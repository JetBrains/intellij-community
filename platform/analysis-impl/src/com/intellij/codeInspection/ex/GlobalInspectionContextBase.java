/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GlobalInspectionContextBase extends UserDataHolderBase implements GlobalInspectionContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.GlobalInspectionContextImpl");
  private static final TObjectHashingStrategy<Tools> TOOLS_HASHING_STRATEGY = new TObjectHashingStrategy<Tools>() {
    @Override
    public int computeHashCode(Tools object) {
      return object.getShortName().hashCode();
    }

    @Override
    public boolean equals(Tools o1, Tools o2) {
      return o1.getShortName().equals(o2.getShortName());
    }
  };

  private RefManager myRefManager;

  private AnalysisScope myCurrentScope;
  @NotNull
  private final Project myProject;
  private final List<JobDescriptor> myJobDescriptors = new ArrayList<>();

  private final StdJobDescriptors myStdJobDescriptors = new StdJobDescriptors();
  protected ProgressIndicator myProgressIndicator = new EmptyProgressIndicator();

  private InspectionProfile myExternalProfile;

  protected final Map<Key, GlobalInspectionContextExtension> myExtensions = new HashMap<>();

  protected final Map<String, Tools> myTools = new THashMap<>();

  @NonNls public static final String LOCAL_TOOL_ATTRIBUTE = "is_local_tool";

  public GlobalInspectionContextBase(@NotNull Project project) {
    myProject = project;

    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      final GlobalInspectionContextExtension extension = factory.createGlobalInspectionContextExtension();
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
  public <T> T getExtension(@NotNull final Key<T> key) {
    //noinspection unchecked
    return (T)myExtensions.get(key);
  }

  public InspectionProfile getCurrentProfile() {
    if (myExternalProfile != null) return myExternalProfile;
    String currentProfile = ((InspectionManagerBase)InspectionManager.getInstance(myProject)).getCurrentProfile();
    final InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
    Profile profile = inspectionProfileManager.getProfile(currentProfile, false);
    if (profile == null) {
      profile = InspectionProfileManager.getInstance().getProfile(currentProfile);
      if (profile != null) return (InspectionProfile)profile;

      final String[] availableProfileNames = inspectionProfileManager.getAvailableProfileNames();
      if (availableProfileNames.length == 0) {
        //can't be
        return null;
      }
      profile = inspectionProfileManager.getProfile(availableProfileNames[0]);
    }
    return (InspectionProfile)profile;
  }

  @Override
  public boolean isSuppressed(@NotNull RefEntity entity, @NotNull String id) {
    return entity instanceof RefElementImpl && ((RefElementImpl)entity).isSuppressed(id);
  }

  @Override
  public boolean shouldCheck(@NotNull RefEntity entity, @NotNull GlobalInspectionTool tool) {
    return !(entity instanceof RefElementImpl) || isToCheckMember((RefElementImpl)entity, tool);
  }

  @Override
  public boolean isSuppressed(@NotNull PsiElement element, @NotNull String id) {
    final RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    if (refManager.isDeclarationsFound()) {
      final RefElement refElement = refManager.getReference(element);
      return refElement instanceof RefElementImpl && ((RefElementImpl)refElement).isSuppressed(id);
    }
    return SuppressionUtil.isSuppressed(element, id);
  }


  void cleanupTools() {
    myProgressIndicator.cancel();
    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.cleanup();
    }

    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = state.getTool();
        toolWrapper.cleanup(myProject);
      }
    }
    myTools.clear();

    EntryPointsManager entryPointsManager = EntryPointsManager.getInstance(getProject());
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

  public void setCurrentScope(@NotNull AnalysisScope currentScope) {
    myCurrentScope = currentScope;
  }

  public void doInspections(@NotNull final AnalysisScope scope) {
    if (!GlobalInspectionContextUtil.canRunInspections(myProject, true)) return;

    cleanup();

    ApplicationManager.getApplication().invokeLater(() -> {
      myCurrentScope = scope;
      launchInspections(scope);
    }, ApplicationManager.getApplication().getDisposed());
  }


  @Override
  @NotNull
  public RefManager getRefManager() {
    if (myRefManager == null) {
      myRefManager = ApplicationManager.getApplication().runReadAction(new Computable<RefManagerImpl>() {
        @Override
        public RefManagerImpl compute() {
          return new RefManagerImpl(myProject, myCurrentScope, GlobalInspectionContextBase.this);
        }
      });
    }
    return myRefManager;
  }

  public boolean isToCheckMember(@NotNull RefElement owner, @NotNull InspectionProfileEntry tool) {
    return isToCheckFile(((RefElementImpl)owner).getContainingFile(), tool) && !((RefElementImpl)owner).isSuppressed(tool.getShortName(), tool.getAlternativeID());
  }

  public boolean isToCheckFile(PsiFile file, @NotNull InspectionProfileEntry tool) {
    final Tools tools = myTools.get(tool.getShortName());
    if (tools != null && file != null) {
      for (ScopeToolState state : tools.getTools()) {
        final NamedScope namedScope = state.getScope(file.getProject());
        if (namedScope == null || namedScope.getValue().contains(file, getCurrentProfile().getProfileManager().getScopesManager())) {
          return state.isEnabled() && state.getTool().getTool() == tool;
        }
      }
    }
    return false;
  }

  protected void launchInspections(@NotNull final AnalysisScope scope) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    LOG.info("Code inspection started");
    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), InspectionsBundle.message("inspection.progress.title"), true,
                                                              createOption()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        performInspectionsWithProgress(scope, false, false);
      }

      @Override
      public void onSuccess() {
        notifyInspectionsFinished(scope);
      }
    });
  }

  @NotNull
  protected PerformInBackgroundOption createOption() {
    return new PerformInBackgroundOption(){
      @Override
      public boolean shouldStartInBackground() {
        return true;
      }

      @Override
      public void processSentToBackground() {

      }
    };
  }

  protected void notifyInspectionsFinished(AnalysisScope scope) {
  }

  public void performInspectionsWithProgress(@NotNull final AnalysisScope scope, final boolean runGlobalToolsOnly, final boolean isOfflineInspections) {
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (myProgressIndicator == null) {
      throw new IllegalStateException("Inspections must be run under progress");
    }
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    //init manager in read action
    RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    try {
      psiManager.startBatchFilesProcessingMode();
      refManager.inspectionReadActionStarted();
      getStdJobDescriptors().BUILD_GRAPH.setTotalAmount(scope.getFileCount());
      getStdJobDescriptors().LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
      getStdJobDescriptors().FIND_EXTERNAL_USAGES.setTotalAmount(0);
      //to override current progress in order to hide useless messages/%
      ProgressManager.getInstance().executeProcessUnderProgress(() -> runTools(scope, runGlobalToolsOnly, isOfflineInspections), ProgressWrapper.wrap(myProgressIndicator));
    }
    catch (ProcessCanceledException e) {
      cleanup();
      throw e;
    }
    catch (IndexNotReadyException e) {
      cleanup();
      DumbService.getInstance(myProject).showDumbModeNotification("Usage search is not available until indices are ready");
      throw new ProcessCanceledException();
    }
    catch (Throwable e) {
      LOG.error(e);
    }
    finally {
      refManager.inspectionReadActionFinished();
      psiManager.finishBatchFilesProcessingMode();
    }
  }

  protected void runTools(@NotNull AnalysisScope scope, boolean runGlobalToolsOnly, boolean isOfflineInspections) {
  }


  public void initializeTools(@NotNull List<Tools> outGlobalTools,
                              @NotNull List<Tools> outLocalTools,
                              @NotNull List<Tools> outGlobalSimpleTools) {
    final List<Tools> usedTools = getUsedTools();
    for (Tools currentTools : usedTools) {
      final String shortName = currentTools.getShortName();
      myTools.put(shortName, currentTools);
      InspectionToolWrapper toolWrapper = currentTools.getTool();
      classifyTool(outGlobalTools, outLocalTools, outGlobalSimpleTools, currentTools, toolWrapper);

      for (ScopeToolState state : currentTools.getTools()) {
        state.getTool().initialize(this);
      }

      JobDescriptor[] jobDescriptors = toolWrapper.getJobDescriptors(this);
      for (JobDescriptor jobDescriptor : jobDescriptors) {
        appendJobDescriptor(jobDescriptor);
      }
    }
    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.performPreRunActivities(outGlobalTools, outLocalTools, this);
    }
  }

  @NotNull
  protected List<Tools> getUsedTools() {
    InspectionProfileImpl profile = (InspectionProfileImpl)getCurrentProfile();
    List<Tools> tools = profile.getAllEnabledInspectionTools(myProject);
    Set<InspectionToolWrapper> dependentTools = new LinkedHashSet<>();
    for (Tools tool : tools) {
      profile.collectDependentInspections(tool.getTool(), dependentTools, getProject());
    }

    if (dependentTools.isEmpty()) {
      return tools;
    }
    Set<Tools> set = new THashSet<>(tools, TOOLS_HASHING_STRATEGY);
    set.addAll(ContainerUtil.map(dependentTools, toolWrapper -> new ToolsImpl(toolWrapper, toolWrapper.getDefaultLevel(), true, true)));
    return new ArrayList<>(set);
  }

  private static void classifyTool(@NotNull List<Tools> outGlobalTools,
                                   @NotNull List<Tools> outLocalTools,
                                   @NotNull List<Tools> outGlobalSimpleTools,
                                   @NotNull Tools currentTools,
                                   @NotNull InspectionToolWrapper toolWrapper) {
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
    return myTools;
  }

  private void appendJobDescriptor(@NotNull JobDescriptor job) {
    if (!myJobDescriptors.contains(job)) {
      myJobDescriptors.add(job);
      job.setDoneAmount(0);
    }
  }

  public void codeCleanup(@NotNull AnalysisScope scope,
                          @NotNull InspectionProfile profile,
                          @Nullable String commandName,
                          @Nullable Runnable postRunnable,
                          final boolean modal) {}

  public static void codeCleanup(@NotNull Project project, @NotNull AnalysisScope scope, @Nullable Runnable runnable) {
    GlobalInspectionContextBase globalContext = (GlobalInspectionContextBase)InspectionManager.getInstance(project).createNewGlobalContext(false);
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    globalContext.codeCleanup(scope, profile, null, runnable, false);
  }

  public static void cleanupElements(@NotNull final Project project, @Nullable final Runnable runnable, @NotNull PsiElement... scope) {
    final List<SmartPsiElementPointer<PsiElement>> elements = new ArrayList<>();
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);
    for (PsiElement element : scope) {
      elements.add(manager.createSmartPsiElementPointer(element));
    }

    cleanupElements(project, runnable, elements);
  }

  public static void cleanupElements(@NotNull final Project project,
                                     @Nullable final Runnable runnable,
                                     final List<SmartPsiElementPointer<PsiElement>> elements) {
    Runnable cleanupRunnable = () -> {
      final List<PsiElement> psiElements = new ArrayList<>();
      for (SmartPsiElementPointer<PsiElement> element : elements) {
        PsiElement psiElement = element.getElement();
        if (psiElement != null && psiElement.isPhysical()) {
          psiElements.add(psiElement);
        }
      }
      if (psiElements.isEmpty()) {
        return;
      }
      GlobalInspectionContextBase globalContext = (GlobalInspectionContextBase)InspectionManager.getInstance(project).createNewGlobalContext(false);
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
      AnalysisScope analysisScope = new AnalysisScope(new LocalSearchScope(psiElements.toArray(new PsiElement[psiElements.size()])), project);
      globalContext.codeCleanup(analysisScope, profile, null, runnable, true);
    };

    Application application = ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed() && !application.isUnitTestMode()) {
      application.invokeLater(cleanupRunnable);
    }
    else {
      cleanupRunnable.run();
    }
  }

  public void close(boolean noSuspisiousCodeFound) {
    cleanup();
  }

  @Override
  public void cleanup() {
    cleanupTools();
  }

  @Override
  public void incrementJobDoneAmount(@NotNull JobDescriptor job, @NotNull String message) {
    if (myProgressIndicator == null) return;

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

  public void setExternalProfile(InspectionProfile profile) {
    myExternalProfile = profile;
  }

  @Override
  @NotNull
  public StdJobDescriptors getStdJobDescriptors() {
    return myStdJobDescriptors;
  }
}
