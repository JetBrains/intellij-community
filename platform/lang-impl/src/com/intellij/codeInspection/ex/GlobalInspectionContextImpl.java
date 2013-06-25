/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.DefaultInspectionToolPresentation;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.concurrency.JobLauncher;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtilCore;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.content.*;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.TripleFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

public class GlobalInspectionContextImpl extends UserDataHolderBase implements GlobalInspectionContext {
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
  private final NotNullLazyValue<ContentManager> myContentManager;

  private AnalysisScope myCurrentScope;
  private final Project myProject;
  private List<JobDescriptor> myJobDescriptors;
  private InspectionResultsView myView = null;

  private Content myContent = null;


  private final StdJobDescriptors myStdJobDescriptors = new StdJobDescriptors();
  private ProgressIndicator myProgressIndicator;

  private InspectionProfile myExternalProfile = null;

  private final Map<Key, GlobalInspectionContextExtension> myExtensions = new HashMap<Key, GlobalInspectionContextExtension>();

  private final Map<String, Tools> myTools = new THashMap<String, Tools>();

  private AnalysisUIOptions myUIOptions;
  @NonNls public static final String LOCAL_TOOL_ATTRIBUTE = "is_local_tool";

  private boolean myUseProgressIndicatorInTests = false;

  public GlobalInspectionContextImpl(@NotNull Project project, @NotNull NotNullLazyValue<ContentManager> contentManager) {
    myProject = project;

    myUIOptions = AnalysisUIOptions.getInstance(myProject).copy();
    myRefManager = null;
    myCurrentScope = null;
    myContentManager = contentManager;
    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      final GlobalInspectionContextExtension extension = factory.createGlobalInspectionContextExtension();
      myExtensions.put(extension.getID(), extension);
    }
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public <T> T getExtension(final Key<T> key) {
    return (T)myExtensions.get(key);
  }

  public ContentManager getContentManager() {
    return myContentManager.getValue();
  }

  public InspectionProfile getCurrentProfile() {
    if (myExternalProfile != null) return myExternalProfile;
    InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    final InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
    String currentProfile = managerEx.getCurrentProfile();
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
  public boolean isSuppressed(RefEntity entity, String id) {
    return entity instanceof RefElementImpl && ((RefElementImpl)entity).isSuppressed(id);
  }

  @Override
  public boolean shouldCheck(RefEntity entity, GlobalInspectionTool tool) {
    if (entity instanceof RefElementImpl) {
      final RefElementImpl refElement = (RefElementImpl)entity;
      if (refElement.isSuppressed(tool.getShortName())) return false;

      final PsiFile file = refElement.getContainingFile();

      if (file == null) return false;
      final Project project = file.getProject();
      final Tools tools = myTools.get(tool.getShortName());
      if (tools != null) {
        for (ScopeToolState state : tools.getTools()) {
          final NamedScope namedScope = state.getScope(project);
          if (namedScope == null || namedScope.getValue().contains(file, getCurrentProfile().getProfileManager().getScopesManager())) {
            return state.isEnabled() && state.getTool().getTool() == tool;
          }
        }
      }
      return false;
    }
    return true;
  }

  @Override
  public boolean isSuppressed(@NotNull PsiElement element, String id) {
    final RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    if (refManager.isDeclarationsFound()) {
      final RefElement refElement = refManager.getReference(element);
      return refElement instanceof RefElementImpl && ((RefElementImpl)refElement).isSuppressed(id);
    }
    return InspectionManagerEx.isSuppressed(element, id);
  }


  public synchronized void addView(@NotNull InspectionResultsView view, String title) {
    if (myContent != null) return;
    myContentManager.getValue().addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentRemoved(ContentManagerEvent event) {
        if (event.getContent() == myContent) {
          if (myView != null) {
            close(false);
          }
          myContent = null;
        }
      }
    });

    myView = view;
    myContent = ContentFactory.SERVICE.getInstance().createContent(view, title, false);

    myContent.setDisposer(myView);

    ContentManager contentManager = getContentManager();
    contentManager.addContent(myContent);
    contentManager.setSelectedContent(myContent);

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.INSPECTION).activate(null);
  }

  public void addView(@NotNull InspectionResultsView view) {
    addView(view, view.getCurrentProfileName() == null
                  ? InspectionsBundle.message("inspection.results.title")
                  : InspectionsBundle.message("inspection.results.for.profile.toolwindow.title", view.getCurrentProfileName()));

  }

  private void cleanupTools() {
    myProgressIndicator = null;

    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.cleanup();
    }

    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = state.getTool();
        toolWrapper.cleanup();
      }
    }
    myTools.clear();

    //EntryPointsManager.getInstance(getProject()).cleanup();

    if (myRefManager != null) {
      ((RefManagerImpl)myRefManager).cleanup();
      myRefManager = null;
      if (myCurrentScope != null){
        myCurrentScope.invalidate();
        myCurrentScope = null;
      }
    }
  }

  public void setCurrentScope(@NotNull AnalysisScope currentScope) {
    myCurrentScope = currentScope;
  }

  public void doInspections(@NotNull final AnalysisScope scope) {
    if (!InspectionManagerEx.canRunInspections(myProject, true)) return;

    cleanupTools();
    if (myContent != null) {
      getContentManager().removeContent(myContent, true);
    }

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        myCurrentScope = scope;
        launchInspections(scope);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }


  @Override
  @NotNull
  public RefManager getRefManager() {
    if (myRefManager == null) {
      myRefManager = ApplicationManager.getApplication().runReadAction(new Computable<RefManagerImpl>() {
        @Override
        public RefManagerImpl compute() {
          return new RefManagerImpl(myProject, myCurrentScope, GlobalInspectionContextImpl.this);
        }
      });
    }
    return myRefManager;
  }

  public void launchInspectionsOffline(final AnalysisScope scope,
                                       @Nullable final String outputPath,
                                       final boolean runGlobalToolsOnly,
                                       @NotNull final List<File> inspectionsResults) {
    cleanupTools();
    myCurrentScope = scope;

    DefaultInspectionToolPresentation.setOutputPath(outputPath);
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          performInspectionsWithProgress(scope, runGlobalToolsOnly);
          @NonNls final String ext = ".xml";
          final Map<Element, Tools> globalTools = new HashMap<Element, Tools>();
          for (Map.Entry<String,Tools> stringSetEntry : myTools.entrySet()) {
            final Tools sameTools = stringSetEntry.getValue();
            boolean hasProblems = false;
            String toolName = stringSetEntry.getKey();
            if (sameTools != null) {
              for (ScopeToolState toolDescr : sameTools.getTools()) {
                InspectionToolWrapper toolWrapper = toolDescr.getTool();
                if (toolWrapper instanceof LocalInspectionToolWrapper) {
                  hasProblems = new File(outputPath, toolName + ext).exists();
                }
                else {
                  InspectionToolPresentation presentation = getPresentation(toolWrapper);
                  presentation.updateContent();
                  if (presentation.hasReportedProblems()) {
                    final Element root = new Element(InspectionsBundle.message("inspection.problems"));
                    globalTools.put(root, sameTools);
                    LOG.assertTrue(!hasProblems, toolName);
                    break;
                  }
                }
              }
            }
            if (!hasProblems) continue;
            try {
              new File(outputPath).mkdirs();
              final File file = new File(outputPath, toolName + ext);
              inspectionsResults.add(file);
              FileUtil.writeToFile(file, ("</" + InspectionsBundle.message("inspection.problems") + ">").getBytes("UTF-8"), true);
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }

          getRefManager().iterate(new RefVisitor() {
            @Override
            public void visitElement(@NotNull final RefEntity refEntity) {
              for (Element element : globalTools.keySet()) {
                final Tools tools = globalTools.get(element);
                for (ScopeToolState state : tools.getTools()) {
                  try {
                    InspectionToolWrapper toolWrapper = state.getTool();
                    InspectionToolPresentation presentation = getPresentation(toolWrapper);
                    presentation.exportResults(element, refEntity);
                  }
                  catch (Exception e) {
                    LOG.error("Problem when exporting: " + refEntity.getExternalName(), e);
                  }
                }
              }
            }
          });

          for (Element element : globalTools.keySet()) {
            final String toolName = globalTools.get(element).getShortName();
            element.setAttribute(LOCAL_TOOL_ATTRIBUTE, Boolean.toString(false));
            final Document doc = new Document(element);
            PathMacroManager.getInstance(getProject()).collapsePaths(doc.getRootElement());
            try {
              new File(outputPath).mkdirs();
              final File file = new File(outputPath, toolName + ext);
              inspectionsResults.add(file);

              OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
              try {
                JDOMUtil.writeDocument(doc, writer, "\n");
              }
              finally {
                writer.close();
              }
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
        }
      });
    }
    finally {
      DefaultInspectionToolPresentation.setOutputPath(null);
    }
  }


  public boolean isToCheckMember(@NotNull RefElement owner, @NotNull InspectionProfileEntry tool) {
    return isToCheckFile(((RefElementImpl)owner).getContainingFile(), tool) && !((RefElementImpl)owner).isSuppressed(tool.getShortName());
  }

  public boolean isToCheckFile(PsiFile file, @NotNull InspectionProfileEntry tool) {
    final Tools tools = myTools.get(tool.getShortName());
    if (tools != null && file != null) {
      for (ScopeToolState state : tools.getTools()) {
        final NamedScope namedScope = state.getScope(file.getProject());
        if (namedScope == null || namedScope.getValue().contains(file, getCurrentProfile().getProfileManager().getScopesManager())) {
          if (state.isEnabled()) {
            InspectionToolWrapper toolWrapper = state.getTool();
            if (toolWrapper.getTool() == tool) return true;
          }
          return false;
        }
      }
    }
    return false;
  }

  public void ignoreElement(@NotNull InspectionProfileEntry tool, final PsiElement element) {
    final RefElement refElement = getRefManager().getReference(element);
    final Tools tools = myTools.get(tool.getShortName());
    if (tools != null){
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = state.getTool();
        ignoreElementRecursively(toolWrapper, refElement);
      }
    }
  }

  public InspectionResultsView getView() {
    return myView;
  }

  private void ignoreElementRecursively(@NotNull InspectionToolWrapper toolWrapper, final RefEntity refElement) {
    if (refElement != null) {
      InspectionToolPresentation presentation = getPresentation(toolWrapper);
      presentation.ignoreCurrentElement(refElement);
      final List<RefEntity> children = refElement.getChildren();
      if (children != null) {
        for (RefEntity child : children) {
          ignoreElementRecursively(toolWrapper, child);
        }
      }
    }
  }

  public AnalysisUIOptions getUIOptions() {
    return myUIOptions;
  }

  public void setSplitterProportion(final float proportion) {
    myUIOptions.SPLITTER_PROPORTION = proportion;
  }

  public ToggleAction createToggleAutoscrollAction() {
    return myUIOptions.getAutoScrollToSourceHandler().createToggleAction();
  }

  private void launchInspections(@NotNull final AnalysisScope scope) {
    myUIOptions = AnalysisUIOptions.getInstance(myProject).copy();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    LOG.info("Code inspection started");
    myView = new InspectionResultsView(myProject, getCurrentProfile(), scope, this, new InspectionRVContentProviderImpl(myProject));
    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), InspectionsBundle.message("inspection.progress.title"), true,
                                                              new PerformAnalysisInBackgroundOption(myProject)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        performInspectionsWithProgress(scope, false);
      }

      @Override
      public void onSuccess() {
        notifyInspectionsFinished();
      }
    });
  }

  private void notifyInspectionsFinished() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        LOG.info("Code inspection finished");

        if (myView != null) {
          if (!myView.update() && !getUIOptions().SHOW_ONLY_DIFF) {
            NotificationGroup.toolWindowGroup("Inspection Results", ToolWindowId.INSPECTION, true)
              .createNotification(InspectionsBundle.message("inspection.no.problems.message"), MessageType.INFO).notify(myProject);
            close(true);
          }
          else {
            addView(myView);
          }
        }
      }
    });
  }

  private void performInspectionsWithProgress(@NotNull final AnalysisScope scope, final boolean runGlobalToolsOnly) {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    myProgressIndicator = getProgressIndicator();
    //init manager in read action
    RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    try {
      psiManager.startBatchFilesProcessingMode();
      refManager.inspectionReadActionStarted();
      getStdJobDescriptors().BUILD_GRAPH.setTotalAmount(scope.getFileCount());
      getStdJobDescriptors().LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
      getStdJobDescriptors().FIND_EXTERNAL_USAGES.setTotalAmount(0);
      //to override current progress in order to hide useless messages/%
      ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable() {
          @Override
          public void run() {
            runTools(scope, runGlobalToolsOnly);
          }
        }, ProgressWrapper.wrap(myProgressIndicator));
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
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      refManager.inspectionReadActionFinished();
      psiManager.finishBatchFilesProcessingMode();
    }
  }

  private ProgressIndicator getProgressIndicator() {
    return ApplicationManager.getApplication().isUnitTestMode() && !myUseProgressIndicatorInTests
           ? new EmptyProgressIndicator() : ProgressManager.getInstance().getProgressIndicator();
  }

  @TestOnly
  public void setUseProgressIndicatorInTests(boolean useProgressIndicatorInTests) {
    myUseProgressIndicatorInTests = useProgressIndicatorInTests;
  }

  private void runTools(@NotNull AnalysisScope scope, boolean runGlobalToolsOnly) {
    final InspectionManagerEx inspectionManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    List<Tools> globalTools = new ArrayList<Tools>();
    final List<Tools> localTools = new ArrayList<Tools>();
    final List<Tools> globalSimpleTools = new ArrayList<Tools>();
    initializeTools(globalTools, localTools, globalSimpleTools);
    final List<InspectionToolWrapper> needRepeatSearchRequest = new ArrayList<InspectionToolWrapper>();
    ((RefManagerImpl)getRefManager()).initializeAnnotators();

    for (Tools tools : globalTools) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = state.getTool();
        GlobalInspectionTool tool = (GlobalInspectionTool)toolWrapper.getTool();
        InspectionToolPresentation toolPresentation = getPresentation(toolWrapper);
        try {
          if (tool.isGraphNeeded()) {
            ((RefManagerImpl)getRefManager()).findAllDeclarations();
          }
          tool.runInspection(scope, inspectionManager, this, toolPresentation);
          if (tool.queryExternalUsagesRequests(inspectionManager, this, toolPresentation)) {
            needRepeatSearchRequest.add(toolWrapper);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (IndexNotReadyException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      try {
        extension.performPostRunActivities(needRepeatSearchRequest, this);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (IndexNotReadyException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    if (runGlobalToolsOnly) return;

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final Set<VirtualFile> localScopeFiles = scope.toSearchScope() instanceof LocalSearchScope ? new THashSet<VirtualFile>() : null;
    for (Tools tools : globalSimpleTools) {
      GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
      GlobalSimpleInspectionTool tool = (GlobalSimpleInspectionTool)toolWrapper.getTool();
      tool.inspectionStarted(inspectionManager, this, getPresentation(toolWrapper));
    }

    final Map<String, InspectionToolWrapper> map = getInspectionWrappersMap(localTools);
    scope.accept(new PsiElementVisitor() {
      @Override
      public void visitFile(final PsiFile file) {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          incrementJobDoneAmount(getStdJobDescriptors().LOCAL_ANALYSIS, ProjectUtilCore.displayUrlRelativeToProject(virtualFile, virtualFile
            .getPresentableUrl(), myProject, true, false));
          if (SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile)) return;
          if (localScopeFiles != null && !localScopeFiles.add(virtualFile)) return;
        }

        final FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
        final com.intellij.openapi.editor.Document document = viewProvider == null ? null : viewProvider.getDocument();
        if (document == null || virtualFile.getFileType().isBinary()) return; //do not inspect binary files
        final LocalInspectionsPass pass = new LocalInspectionsPass(file, document, 0,
                                                                   file.getTextLength(), LocalInspectionsPass.EMPTY_PRIORITY_RANGE, true);
        try {
          final List<LocalInspectionToolWrapper> lTools = new ArrayList<LocalInspectionToolWrapper>();
          for (Tools tool : localTools) {
            final LocalInspectionToolWrapper enabledTool = (LocalInspectionToolWrapper)tool.getEnabledTool(file);
            if (enabledTool != null) {
              lTools.add(enabledTool);
            }
          }
          pass.doInspectInBatch(inspectionManager, lTools);

          JobLauncher.getInstance().invokeConcurrentlyUnderProgress(globalSimpleTools, myProgressIndicator, false, new Processor<Tools>() {
            @Override
            public boolean process(Tools tools) {
              GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
              GlobalSimpleInspectionTool tool = (GlobalSimpleInspectionTool)toolWrapper.getTool();
              ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, file, false);
              ProblemDescriptionsProcessor problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, map);
              tool.checkFile(file, inspectionManager, problemsHolder, GlobalInspectionContextImpl.this, problemDescriptionProcessor);
              InspectionToolPresentation toolPresentation = getPresentation(toolWrapper);
              LocalDescriptorsUtil.addProblemDescriptors(problemsHolder.getResults(), false, GlobalInspectionContextImpl.this, null,
                                                         CONVERT, toolPresentation);
              return true;
            }
          });
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (IndexNotReadyException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error("In file: " + file, e);
        }
        catch (AssertionError e) {
          LOG.error("In file: " + file, e);
        }
        finally {
          InjectedLanguageManager.getInstance(myProject).dropFileCaches(file);
        }
      }
    });
    for (Tools tools : globalSimpleTools) {
      GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
      GlobalSimpleInspectionTool tool = (GlobalSimpleInspectionTool)toolWrapper.getTool();
      ProblemDescriptionsProcessor problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, map);
      tool.inspectionFinished(inspectionManager, this, problemDescriptionProcessor);
    }
  }

  @NotNull
  private ProblemDescriptionsProcessor getProblemDescriptionProcessor(@NotNull final GlobalInspectionToolWrapper toolWrapper,
                                                                      @NotNull final Map<String, InspectionToolWrapper> wrappersMap) {
    return new ProblemDescriptionsProcessor() {
      @Nullable
      @Override
      public CommonProblemDescriptor[] getDescriptions(@NotNull RefEntity refEntity) {
        return new CommonProblemDescriptor[0];
      }

      @Override
      public void ignoreElement(@NotNull RefEntity refEntity) {

      }

      @Override
      public void addProblemElement(@Nullable RefEntity refEntity, @NotNull CommonProblemDescriptor... commonProblemDescriptors) {
        for (CommonProblemDescriptor problemDescriptor : commonProblemDescriptors) {
          if (!(problemDescriptor instanceof ProblemDescriptor)) {
            continue;
          }
          ProblemGroup problemGroup = ((ProblemDescriptor)problemDescriptor).getProblemGroup();

          InspectionToolWrapper targetWrapper = problemGroup == null ? toolWrapper : wrappersMap.get(problemGroup.getProblemName());
          if (targetWrapper != null) { // Else it's switched off
            InspectionToolPresentation toolPresentation = getPresentation(targetWrapper);
            toolPresentation.addProblemElement(refEntity, problemDescriptor);
          }
        }
      }

      @Override
      public RefEntity getElement(@NotNull CommonProblemDescriptor descriptor) {
        return null;
      }
    };
  }

  @NotNull
  private static Map<String, InspectionToolWrapper> getInspectionWrappersMap(@NotNull List<Tools> tools) {
    Map<String, InspectionToolWrapper> name2Inspection = new HashMap<String, InspectionToolWrapper>(tools.size());
    for (Tools tool : tools) {
      InspectionToolWrapper toolWrapper = tool.getTool();
      name2Inspection.put(toolWrapper.getShortName(), toolWrapper);
    }

    return name2Inspection;
  }

  private static final TripleFunction<LocalInspectionTool,PsiElement,GlobalInspectionContext,RefElement> CONVERT =
    new TripleFunction<LocalInspectionTool, PsiElement, GlobalInspectionContext, RefElement>() {
      @Override
      public RefElement fun(LocalInspectionTool tool,
                            PsiElement elt,
                            GlobalInspectionContext context) {
        final PsiNamedElement problemElement = PsiTreeUtil.getNonStrictParentOfType(elt, PsiFile.class);

        RefElement refElement = context.getRefManager().getReference(problemElement);
        if (refElement == null && problemElement != null) {  // no need to lose collected results
          refElement = GlobalInspectionUtil.retrieveRefElement(elt, context);
        }
        return refElement;
      }
    };


  public void initializeTools(@NotNull List<Tools> outGlobalTools,
                              @NotNull List<Tools> outLocalTools,
                              @NotNull List<Tools> outGlobalSimpleTools) {
    myJobDescriptors = new ArrayList<JobDescriptor>();
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

  protected List<Tools> getUsedTools() {
    InspectionProfileImpl profile = new InspectionProfileImpl((InspectionProfileImpl)getCurrentProfile());
    List<Tools> tools = profile.getAllEnabledInspectionTools(myProject);
    Set<InspectionToolWrapper> dependentTools = new LinkedHashSet<InspectionToolWrapper>();
    for (Tools tool : tools) {
      profile.collectDependentInspections(tool.getTool(), dependentTools, getProject());
    }

    if (dependentTools.isEmpty()) {
      return tools;
    }
    Set<Tools> set = new THashSet<Tools>(tools, TOOLS_HASHING_STRATEGY);
    set.addAll(ContainerUtil.map(dependentTools, new Function<InspectionToolWrapper, ToolsImpl>() {
      @Override
      public ToolsImpl fun(InspectionToolWrapper toolWrapper) {
        return new ToolsImpl(toolWrapper, toolWrapper.getDefaultLevel(), true, true);
      }
    }));
    return new ArrayList<Tools>(set);
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

  public Map<String, Tools> getTools() {
    return myTools;
  }

  public void appendJobDescriptor(@NotNull JobDescriptor job) {
    if (!myJobDescriptors.contains(job)) {
      myJobDescriptors.add(job);
      job.setDoneAmount(0);
    }
  }

  public void close(boolean noSuspisiousCodeFound) {
    if (!noSuspisiousCodeFound && (myView == null || myView.isRerun())) return;
    cleanup();
    AnalysisUIOptions.getInstance(myProject).save(myUIOptions);
    if (myContent != null) {
      final ContentManager contentManager = getContentManager();
      if (contentManager != null) {  //null for tests
        contentManager.removeContent(myContent, true);
      }
    }
    myView = null;
  }

  public void cleanup() {
    ((InspectionManagerEx)InspectionManager.getInstance(getProject())).closeRunningContext(this);
    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = state.getTool();
        getPresentation(toolWrapper).finalCleanup();
      }
    }
    cleanupTools();
  }

  public void refreshViews() {
    if (myView != null) {
      myView.updateView(false);
    }
  }

  @Override
  public void incrementJobDoneAmount(JobDescriptor job, String message) {
    if (myProgressIndicator == null) return;

    ProgressManager.checkCanceled();

    int old = job.getDoneAmount();
    job.setDoneAmount(old + 1);

    float totalProgress = getTotalProgress();

    myProgressIndicator.setFraction(totalProgress);
    myProgressIndicator.setText(job.getDisplayName() + " " + message);
  }

  private float getTotalProgress() {
    float totalDone = 0;
    int totalTotal = 0;
    for (JobDescriptor jobDescriptor : myJobDescriptors) {
      totalDone += jobDescriptor.getDoneAmount();
      totalTotal += jobDescriptor.getTotalAmount();
    }
    return totalTotal == 0 ? 1 : totalDone / totalTotal;
  }

  public void setExternalProfile(InspectionProfile profile) {
    myExternalProfile = profile;
  }

  @Override
  @NotNull
  public StdJobDescriptors getStdJobDescriptors() {
    return myStdJobDescriptors;
  }

  private final Map<InspectionToolWrapper, InspectionToolPresentation> myPresentationMap = new THashMap<InspectionToolWrapper, InspectionToolPresentation>();
  @NotNull
  public InspectionToolPresentation getPresentation(@NotNull InspectionToolWrapper toolWrapper) {
    InspectionToolPresentation presentation = myPresentationMap.get(toolWrapper);
    if (presentation == null) {
      InspectionProfileEntry tool = toolWrapper.getTool();
      if (tool instanceof InspectionPresentationProvider) {
        presentation = ((InspectionPresentationProvider)tool).createPresentation(toolWrapper);
      }
      else {
        presentation = new DefaultInspectionToolPresentation(toolWrapper);
      }
      presentation.initialize(this);
      myPresentationMap.put(toolWrapper, presentation);
    }
    return presentation;
  }
}
