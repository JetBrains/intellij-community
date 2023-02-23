// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.function.Predicate;

public class GlobalInspectionContextBase extends UserDataHolderBase implements GlobalInspectionContext {
  private static final Logger LOG = Logger.getInstance(GlobalInspectionContextBase.class);
  private static final HashingStrategy<Tools> TOOLS_HASHING_STRATEGY = new HashingStrategy<>() {
    @Override
    public int hashCode(@Nullable Tools object) {
      return object == null ? 0 : object.getShortName().hashCode();
    }

    @Override
    public boolean equals(@Nullable Tools o1, @Nullable Tools o2) {
      return o1 == o2 || (o1 != null && o2 != null && o1.getShortName().equals(o2.getShortName()));
    }
  };

  private RefManager myRefManager;

  private AnalysisScope myCurrentScope;
  @NotNull
  private final Project myProject;
  private final List<JobDescriptor> myJobDescriptors = new ArrayList<>();

  private final StdJobDescriptors myStdJobDescriptors = new StdJobDescriptors();
  @NotNull
  protected ProgressIndicator myProgressIndicator = new EmptyProgressIndicator();

  private InspectionProfileImpl myExternalProfile;
  @NotNull
  private Runnable myRerunAction = EmptyRunnable.getInstance();

  protected final Map<Key<?>, GlobalInspectionContextExtension<?>> myExtensions = new HashMap<>();

  /** null means {@link #initializeTools(List, List, List)} wasn't called yet */
  @Unmodifiable
  private Map<String, Tools> myTools;

  @NonNls public static final String PROBLEMS_TAG_NAME = "problems";
  @NonNls public static final String LOCAL_TOOL_ATTRIBUTE = "is_local_tool";

  public GlobalInspectionContextBase(@NotNull Project project) {
    myProject = project;

    for (InspectionExtensionsFactory factory : InspectionExtensionsFactory.EP_NAME.getExtensionList()) {
      GlobalInspectionContextExtension<?> extension = factory.createGlobalInspectionContextExtension();
      myExtensions.put(extension.getID(), extension);
    }
  }

  public AnalysisScope getCurrentScope() {
    return myCurrentScope;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public <T> T getExtension(@NotNull Key<T> key) {
    //noinspection unchecked
    return (T)myExtensions.get(key);
  }

  @NotNull
  public InspectionProfileImpl getCurrentProfile() {
    if (myExternalProfile != null) {
      return myExternalProfile;
    }

    String currentProfile = ((InspectionManagerBase)InspectionManager.getInstance(myProject)).getCurrentProfile();
    InspectionProfileImpl profile = ProjectInspectionProfileManager.getInstance(myProject).getProfile(currentProfile, false);
    return profile == null ? InspectionProfileManager.getInstance().getProfile(currentProfile) : profile;
  }

  @Override
  public boolean shouldCheck(@NotNull RefEntity entity, @NotNull GlobalInspectionTool tool) {
    return !(entity instanceof RefElementImpl) || isToCheckMember((RefElementImpl)entity, tool);
  }

  @Override
  public boolean isSuppressed(@NotNull PsiElement element, @NotNull String id) {
    RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    if (refManager.isDeclarationsFound()) {
      RefElement refElement = refManager.getReference(element);
      return refElement instanceof RefElementImpl && ((RefElementImpl)refElement).isSuppressed(id);
    }
    return SuppressionUtil.isSuppressed(element, id);
  }


  void cleanupTools() {
    myProgressIndicator.cancel();
    for (GlobalInspectionContextExtension<?> extension : myExtensions.values()) {
      extension.cleanup();
    }

    // allow to call cleanupTools() even when initializeTools() wasn't called: suspicious but this is how launchInspections() works - by calling cleanupTools() first
    if (myTools != null) {
      for (Tools tools : myTools.values()) {
        for (ScopeToolState state : tools.getTools()) {
          InspectionToolWrapper<?,?> toolWrapper = state.getTool();
          toolWrapper.cleanup(myProject);
        }
      }
      myTools = null;
    }

    EntryPointsManager entryPointsManager = getProject().isDisposed() ? null : EntryPointsManager.getInstance(getProject());
    if (entryPointsManager != null) {
      entryPointsManager.cleanup();
    }

    if (myRefManager != null) {
      ((RefManagerImpl)myRefManager).cleanup();
      myRefManager = null;
      if (myCurrentScope != null){
        myCurrentScope.invalidate();
        myCurrentScope = null;
      }
    }
    myJobDescriptors.clear();
  }

  boolean areToolsInitialized() {
    return myTools != null;
  }

  public void setCurrentScope(@NotNull AnalysisScope currentScope) {
    myCurrentScope = currentScope;
  }

  public void setRerunAction(@NotNull Runnable action) {
    myRerunAction = action;
  }

  public void doInspections(@NotNull AnalysisScope scope) {
    if (!GlobalInspectionContextUtil.canRunInspections(myProject, true, myRerunAction)) return;

    cleanup();

    ApplicationManager.getApplication().invokeLater(() -> {
      myCurrentScope = scope;
      launchInspections(scope);
    }, myProject.getDisposed());
  }


  @Override
  @NotNull
  public RefManager getRefManager() {
    RefManager refManager = myRefManager;
    if (refManager == null) {
      myRefManager = refManager = DumbService.getInstance(myProject).runReadActionInSmartMode(() -> new RefManagerImpl(myProject, myCurrentScope, this));
    }
    return refManager;
  }

  public boolean isToCheckMember(@NotNull RefElement owner, @NotNull InspectionProfileEntry tool) {
    return isToCheckFile(((RefElementImpl)owner).getContainingFile(), tool) && !((RefElementImpl)owner).isSuppressed(tool.getShortName(), tool.getAlternativeID());
  }

  public boolean isToCheckFile(PsiFile file, @NotNull InspectionProfileEntry tool) {
    Tools tools = getTools().get(tool.getShortName());
    if (tools != null && file != null) {
      for (ScopeToolState state : tools.getTools()) {
        NamedScope namedScope = state.getScope(file.getProject());
        if (namedScope == null || namedScope.getValue().contains(file, getCurrentProfile().getProfileManager().getScopesManager())) {
          return state.isEnabled() && state.getTool().getTool() == tool;
        }
      }
    }
    return false;
  }

  protected void launchInspections(@NotNull AnalysisScope scope) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    LOG.info("Code inspection started");
    InspectionProfileImpl profile = getCurrentProfile();
    String title = profile.getSingleTool() == null
                   ? AnalysisBundle.message("inspection.progress.profile.title", profile.getName())
                   : AnalysisBundle.message("inspection.progress.single.inspection.title", profile.getName());
    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), title, true, createOption()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        performInspectionsWithProgress(scope, false, false);
      }

      @Override
      public void onSuccess() {
        notifyInspectionsFinished(scope);
      }

      @Override
      public void onCancel() {
        // execute cleanup in EDT because of myTools
        cleanup();
      }
    });
  }

  @NotNull
  protected PerformInBackgroundOption createOption() {
    return PerformInBackgroundOption.ALWAYS_BACKGROUND;
  }

  protected void notifyInspectionsFinished(@NotNull AnalysisScope scope) {
  }

  public void performInspectionsWithProgress(@NotNull AnalysisScope scope, boolean runGlobalToolsOnly, boolean isOfflineInspections) {
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (!(myProgressIndicator instanceof ProgressIndicatorEx) || !(myProgressIndicator instanceof ProgressIndicatorWithDelayedPresentation)) {
      throw new IllegalStateException("Inspections must be run under ProgressWindow but got: "+myProgressIndicator);
    }
    myProgressIndicator.setIndeterminate(false);
    ((ProgressIndicatorEx)myProgressIndicator).addStateDelegate(new AbstractProgressIndicatorExBase(){
      @Override
      public void cancel() {
        super.cancel();
        canceled();
      }
    });
    PsiManager psiManager = PsiManager.getInstance(myProject);
    //init manager in read action
    ((RefManagerImpl)getRefManager()).runInsideInspectionReadAction(() ->
      psiManager.runInBatchFilesMode(() -> {
        try {
          getStdJobDescriptors().BUILD_GRAPH.setTotalAmount(scope.getFileCount());
          getStdJobDescriptors().LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
          getStdJobDescriptors().FIND_EXTERNAL_USAGES.setTotalAmount(0);

          runTools(scope, runGlobalToolsOnly, isOfflineInspections);
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
        return  null;
      })
    );
  }

  protected void canceled() {

  }

  protected void runTools(@NotNull AnalysisScope scope, boolean runGlobalToolsOnly, boolean isOfflineInspections) {
  }

  public void initializeTools(@NotNull List<Tools> outGlobalTools,
                              @NotNull List<Tools> outLocalTools,
                              @NotNull List<? super Tools> outGlobalSimpleTools) {
    List<Tools> usedTools = getUsedTools();
    Map<String, Tools> tools = new HashMap<>(usedTools.size());
    for (Tools currentTools : usedTools) {
      String shortName = currentTools.getShortName();
      tools.put(shortName, currentTools);
      InspectionToolWrapper<?,?> toolWrapper = currentTools.getTool();
      classifyTool(outGlobalTools, outLocalTools, outGlobalSimpleTools, currentTools, toolWrapper);

      for (ScopeToolState state : currentTools.getTools()) {
        state.getTool().initialize(this);
      }

      JobDescriptor[] jobDescriptors = toolWrapper.getJobDescriptors(this);
      for (JobDescriptor jobDescriptor : jobDescriptors) {
        appendJobDescriptor(jobDescriptor);
      }
    }
    myTools = Collections.unmodifiableMap(tools);
    for (GlobalInspectionContextExtension<?> extension : myExtensions.values()) {
      extension.performPreRunActivities(outGlobalTools, outLocalTools, this);
    }
  }

  @NotNull
  public List<Tools> getUsedTools() {
    InspectionProfileImpl profile = getCurrentProfile();
    List<Tools> tools = profile.getAllEnabledInspectionTools(myProject);
    Set<InspectionToolWrapper<?, ?>> dependentTools = new LinkedHashSet<>();
    for (Tools tool : tools) {
      profile.collectDependentInspections(tool.getTool(), dependentTools, getProject());
    }

    if (dependentTools.isEmpty()) {
      return tools;
    }
    Set<Tools> set = CollectionFactory.createCustomHashingStrategySet(TOOLS_HASHING_STRATEGY);
    set.addAll(tools);
    set.addAll(ContainerUtil.map(dependentTools, toolWrapper -> new ToolsImpl(toolWrapper, toolWrapper.getDefaultLevel(), true, true)));
    return new ArrayList<>(set);
  }

  protected void classifyTool(@NotNull List<? super Tools> outGlobalTools,
                                   @NotNull List<? super Tools> outLocalTools,
                                   @NotNull List<? super Tools> outGlobalSimpleTools,
                                   @NotNull Tools currentTools,
                                   @NotNull InspectionToolWrapper<?,?> toolWrapper) {
    if (toolWrapper instanceof LocalInspectionToolWrapper) {
      outLocalTools.add(currentTools);
    }
    else if (toolWrapper instanceof GlobalInspectionToolWrapper) {
      if (toolWrapper.getTool() instanceof GlobalSimpleInspectionTool) {
        outGlobalSimpleTools.add(currentTools);
      }
      else if (toolWrapper.getTool() instanceof GlobalInspectionTool) {
        outGlobalTools.add(currentTools);
      }
      else {
        throw new RuntimeException("unknown global tool " + toolWrapper);
      }
    }
    else {
      throw new RuntimeException("unknown tool " + toolWrapper);
    }
  }

  @NotNull
  public Map<String, Tools> getTools() {
    Map<String, Tools> tools = myTools;
    if (tools == null) {
      throw new IllegalStateException("Tools are not initialized. Please call initializeTools() before use");
    }
    return tools;
  }

  protected void appendJobDescriptor(@NotNull JobDescriptor job) {
    if (!myJobDescriptors.contains(job)) {
      myJobDescriptors.add(job);
      job.setDoneAmount(0);
    }
  }

  public void codeCleanup(@NotNull AnalysisScope scope,
                          @NotNull InspectionProfile profile,
                          @Nullable String commandName,
                          @Nullable Runnable postRunnable,
                          boolean modal,
                          @NotNull Predicate<? super ProblemDescriptor> shouldApplyFix) {}

  public void codeCleanup(@NotNull AnalysisScope scope,
                          @NotNull InspectionProfile profile,
                          @Nullable String commandName,
                          @Nullable Runnable postRunnable,
                          boolean modal) {
    codeCleanup(scope, profile, commandName, postRunnable, modal, Predicates.alwaysTrue());
  }

  public static void cleanupElements(@NotNull Project project, @Nullable Runnable runnable, PsiElement @NotNull ... scope) {
    cleanupElements(project, runnable, Predicates.alwaysTrue(), scope);
  }

  public static void cleanupElements(@NotNull Project project, @Nullable Runnable runnable, Predicate<? super ProblemDescriptor> shouldApplyFix, PsiElement @NotNull ... scope) {
    List<SmartPsiElementPointer<PsiElement>> elements = new ArrayList<>();
    SmartPointerManager manager = SmartPointerManager.getInstance(project);
    for (PsiElement element : scope) {
      elements.add(manager.createSmartPsiElementPointer(element));
    }

    cleanupElements(project, runnable, elements, shouldApplyFix);
  }

  public static void cleanupElements(@NotNull Project project,
                                     @Nullable Runnable runnable,
                                     List<? extends SmartPsiElementPointer<PsiElement>> elements) {
    cleanupElements(project, runnable, elements, Predicates.alwaysTrue());
  }

  private static void cleanupElements(@NotNull Project project,
                                      @Nullable Runnable runnable,
                                      List<? extends SmartPsiElementPointer<PsiElement>> elements,
                                      @NotNull Predicate<? super ProblemDescriptor> shouldApplyFix) {
    ApplicationManager.getApplication().invokeLater(() -> {
      List<PsiElement> psiElements = new ArrayList<>();
      for (SmartPsiElementPointer<PsiElement> element : elements) {
        PsiElement psiElement = element.getElement();
        if (psiElement != null && psiElement.isPhysical()) {
          psiElements.add(psiElement);
        }
      }
      if (psiElements.isEmpty()) {
        return;
      }
      GlobalInspectionContextBase globalContext = (GlobalInspectionContextBase)InspectionManager.getInstance(project).createNewGlobalContext();
      InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
      AnalysisScope analysisScope = new AnalysisScope(new LocalSearchScope(psiElements.toArray(PsiElement.EMPTY_ARRAY)), project);
      globalContext.codeCleanup(analysisScope, profile, null, runnable, true, shouldApplyFix);
    });
  }

  public void close(boolean noSuspiciousCodeFound) {
    cleanup();
  }

  @Override
  public void cleanup() {
    cleanupTools();
  }

  @Override
  public void incrementJobDoneAmount(@NotNull JobDescriptor job, @NotNull @NlsContexts.ProgressText String message) {
    ProgressManager.checkCanceled();

    int old = job.getDoneAmount();
    job.setDoneAmount(old + 1);

    float totalProgress = getTotalProgress();

    myProgressIndicator.setFraction(totalProgress);
    myProgressIndicator.setText(job.getDisplayName() + " " + message);
  }

  private float getTotalProgress() {
    int totalDone = 0;
    int totalTotal = 0;
    for (JobDescriptor jobDescriptor : myJobDescriptors) {
      totalDone += jobDescriptor.getDoneAmount();
      totalTotal += jobDescriptor.getTotalAmount();
    }
    return totalTotal == 0 ? 1 : 1.0f * totalDone / totalTotal;
  }

  public void setExternalProfile(InspectionProfileImpl profile) {
    myExternalProfile = profile;
  }

  @Override
  @NotNull
  public StdJobDescriptors getStdJobDescriptors() {
    return myStdJobDescriptors;
  }

  @NotNull
  public static DaemonProgressIndicator assertUnderDaemonProgress() {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    ProgressIndicator original = indicator == null ? null : ProgressWrapper.unwrapAll(indicator);
    if (!(original instanceof DaemonProgressIndicator)) {
      throw new IllegalStateException("must be run under DaemonProgressIndicator, but got: " + (original == null ? "null" : ": " +original.getClass()) + ": "+ original);
    }
    return (DaemonProgressIndicator)original;
  }
}
