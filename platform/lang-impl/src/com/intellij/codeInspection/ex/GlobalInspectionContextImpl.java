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
import com.intellij.codeInspection.ui.InspectionResultsView;
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
import com.intellij.openapi.project.ProjectUtil;
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
  private boolean RUN_GLOBAL_TOOLS_ONLY = false;

  private final Map<String, Tools> myTools = new THashMap<String, Tools>();

  private AnalysisUIOptions myUIOptions;
  @NonNls static final String LOCAL_TOOL_ATTRIBUTE = "is_local_tool";

  private boolean myUseProgressIndicatorInTests = false;

  public GlobalInspectionContextImpl(Project project, NotNullLazyValue<ContentManager> contentManager) {
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
    Profile profile = inspectionProfileManager.getProfile(managerEx.getCurrentProfile(), false);
    if (profile == null) {
      profile = InspectionProfileManager.getInstance().getProfile(managerEx.getCurrentProfile());
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
            return state.isEnabled() && ((InspectionToolWrapper)state.getTool()).getTool() == tool;
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

  protected void addView(@NotNull InspectionResultsView view) {
    addView(view, view.getCurrentProfileName() == null
                  ? InspectionsBundle.message("inspection.results.title")
                  : InspectionsBundle.message("inspection.results.for.profile.toolwindow.title", view.getCurrentProfileName()));

  }

  private void cleanup() {
    myProgressIndicator = null;

    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.cleanup();
    }

    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = (InspectionToolWrapper)state.getTool();
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

  public void setCurrentScope(AnalysisScope currentScope) {
    myCurrentScope = currentScope;
  }

  public void doInspections(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager) {
    if (!InspectionManagerEx.canRunInspections(myProject, true)) return;

    cleanup();
    if (myContent != null) {
      getContentManager().removeContent(myContent, true);
    }

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        myCurrentScope = scope;
        launchInspections(scope, manager);
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
                                       final InspectionManager manager,
                                       @NotNull final List<File> inspectionsResults) {
    cleanup();

    myCurrentScope = scope;

    InspectionTool.setOutputPath(outputPath);
    final boolean oldToolsSettings = RUN_GLOBAL_TOOLS_ONLY;
    RUN_GLOBAL_TOOLS_ONLY = runGlobalToolsOnly;
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          performInspectionsWithProgress(scope, manager);
          @NonNls final String ext = ".xml";
          final Map<Element, Tools> globalTools = new HashMap<Element, Tools>();
          for (Map.Entry<String,Tools> stringSetEntry : myTools.entrySet()) {
            final Tools sameTools = stringSetEntry.getValue();
            boolean hasProblems = false;
            String toolName = stringSetEntry.getKey();
            if (sameTools != null) {
              for (ScopeToolState toolDescr : sameTools.getTools()) {
                InspectionToolWrapper toolWrapper = (InspectionToolWrapper)toolDescr.getTool();
                if (toolWrapper instanceof LocalInspectionToolWrapper) {
                  hasProblems = new File(outputPath, toolName + ext).exists();
                }
                else if (toolWrapper.getTool() instanceof InspectionTool) {
                  InspectionTool tool = (InspectionTool)toolWrapper.getTool();
                  tool.updateContent();
                  if (tool.hasReportedProblems()) {
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
                    InspectionToolWrapper toolWrapper = (InspectionToolWrapper)state.getTool();
                    toolWrapper.exportResults(element, refEntity);
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
      InspectionTool.setOutputPath(null);
      RUN_GLOBAL_TOOLS_ONLY = oldToolsSettings;
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
            InspectionToolWrapper toolWrapper = (InspectionToolWrapper)state.getTool();
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
        InspectionToolWrapper toolWrapper = (InspectionToolWrapper)state.getTool();
        ignoreElementRecursively(toolWrapper, refElement);
      }
    }
  }

  public InspectionResultsView getView() {
    return myView;
  }

  private static void ignoreElementRecursively(@NotNull InspectionToolWrapper toolWrapper, final RefEntity refElement) {
    if (refElement != null) {
      InspectionProfileEntry tool = toolWrapper.getTool();
      if (tool instanceof InspectionTool) {
        ((InspectionTool)tool).ignoreCurrentElement(refElement);
      }
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

  private void launchInspections(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager) {
    myUIOptions = AnalysisUIOptions.getInstance(myProject).copy();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    LOG.info("Code inspection started");
    myView = new InspectionResultsView(myProject, getCurrentProfile(), scope, this, new InspectionRVContentProviderImpl(myProject));
    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), InspectionsBundle.message("inspection.progress.title"), true,
                                                              new PerformAnalysisInBackgroundOption(myProject)) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        performInspectionsWithProgress(scope, manager);
      }

      @Override
      public void onSuccess() {
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
    });
  }

  public void performInspectionsWithProgress(@NotNull final AnalysisScope scope, @NotNull final InspectionManager manager) {
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
            runTools(scope, manager);
          }
        }, ProgressWrapper.wrap(myProgressIndicator));
    }
    catch (ProcessCanceledException e) {
      cleanup((InspectionManagerEx)manager);
      throw e;
    }
    catch (IndexNotReadyException e) {
      cleanup((InspectionManagerEx)manager);
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

  private void runTools(@NotNull AnalysisScope scope, @NotNull final InspectionManager manager) {
    List<Tools> globalTools = new ArrayList<Tools>();
    final List<Tools> localTools = new ArrayList<Tools>();
    final List<Tools> globalSimpleTools = new ArrayList<Tools>();
    List<Tools> specialTools = new ArrayList<Tools>();
    initializeTools(globalTools, localTools, globalSimpleTools, specialTools);
    final List<InspectionToolWrapper> needRepeatSearchRequest = new ArrayList<InspectionToolWrapper>();
    ((RefManagerImpl)getRefManager()).initializeAnnotators();
    // run special tools first
    for (Tools tools : specialTools) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = (InspectionToolWrapper)state.getTool();
        InspectionTool tool = (InspectionTool)toolWrapper.getTool();
        try {
          if (tool.isGraphNeeded()) {
            ((RefManagerImpl)getRefManager()).findAllDeclarations();
          }
          tool.runInspection(scope, manager);
          if (tool.queryExternalUsagesRequests(manager)) {
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

    for (Tools tools : globalTools) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper toolWrapper = (InspectionToolWrapper)state.getTool();
        GlobalInspectionTool tool = (GlobalInspectionTool)toolWrapper.getTool();
        try {
          if (tool.isGraphNeeded()) {
            ((RefManagerImpl)getRefManager()).findAllDeclarations();
          }
          tool.runInspection(scope, manager, this, toolWrapper);
          if (tool.queryExternalUsagesRequests(manager,this, toolWrapper)) {
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
    if (RUN_GLOBAL_TOOLS_ONLY) return;

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final Set<VirtualFile> localScopeFiles = scope.toSearchScope() instanceof LocalSearchScope ? new THashSet<VirtualFile>() : null;
    for (Tools tools : globalSimpleTools) {
      GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
      GlobalSimpleInspectionTool tool = (GlobalSimpleInspectionTool)toolWrapper.getTool();
      tool.inspectionStarted(manager, this, toolWrapper);
    }

    final Map<String, DescriptorProviderInspection> map = getInspectionWrappersMap(localTools);
    scope.accept(new PsiElementVisitor() {
      @Override
      public void visitFile(final PsiFile file) {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          incrementJobDoneAmount(getStdJobDescriptors().LOCAL_ANALYSIS, ProjectUtil.calcRelativeToProjectPath(virtualFile, myProject));
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
          pass.doInspectInBatch((InspectionManagerEx)manager, lTools);

          JobLauncher.getInstance().invokeConcurrentlyUnderProgress(globalSimpleTools, myProgressIndicator, false, new Processor<Tools>() {
            @Override
            public boolean process(Tools tools) {
              GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
              GlobalSimpleInspectionTool tool = (GlobalSimpleInspectionTool)toolWrapper.getTool();
              ProblemsHolder problemsHolder = new ProblemsHolder(manager, file, false);
              ProblemDescriptionsProcessor problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, map);
              tool.checkFile(file, manager, problemsHolder, GlobalInspectionContextImpl.this, problemDescriptionProcessor);
              LocalInspectionToolWrapper.addProblemDescriptors(problemsHolder.getResults(), false, GlobalInspectionContextImpl.this, null,
                                                               CONVERT, toolWrapper);
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
      tool.inspectionFinished(manager, this, problemDescriptionProcessor);
    }
  }

  @NotNull
  private static ProblemDescriptionsProcessor getProblemDescriptionProcessor(@NotNull final GlobalInspectionToolWrapper toolWrapper,
                                                                             @NotNull final Map<String, DescriptorProviderInspection> wrappersMap) {
    return new GlobalInspectionToolWrapper(toolWrapper.getTool()) {
      @Override
      public void addProblemElement(RefEntity refEntity, @NotNull CommonProblemDescriptor... commonProblemDescriptors) {
        for (CommonProblemDescriptor problemDescriptor : commonProblemDescriptors) {
          if (problemDescriptor instanceof ProblemDescriptor) {
            ProblemGroup problemGroup = ((ProblemDescriptor)problemDescriptor).getProblemGroup();

            if (problemGroup != null) {
              DescriptorProviderInspection dummyWrapper = wrappersMap.get(problemGroup.getProblemName());

              if (dummyWrapper != null) { // Else it's switched off
                dummyWrapper.addProblemElement(refEntity, problemDescriptor);
              }
            }
            else {
              toolWrapper.addProblemElement(refEntity, problemDescriptor);
            }
          }
        }
      }
    };
  }

  @NotNull
  private static Map<String, DescriptorProviderInspection> getInspectionWrappersMap(@NotNull List<Tools> tools) {
    Map<String, DescriptorProviderInspection> name2Inspection = new HashMap<String, DescriptorProviderInspection>(tools.size());
    for (Tools tool : tools) {
      InspectionProfileEntry profileEntry = tool.getTool();
      if (profileEntry instanceof DescriptorProviderInspection) {
        name2Inspection.put(profileEntry.getShortName(), (DescriptorProviderInspection)profileEntry);
      }
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
                              @NotNull List<Tools> outGlobalSimpleTools,
                              @NotNull List<Tools> outSpecialTools
                              ) {
    myJobDescriptors = new ArrayList<JobDescriptor>();
    final List<Tools> usedTools = getUsedTools();
    for (Tools currentTools : usedTools) {
      final String shortName = currentTools.getShortName();
      myTools.put(shortName, currentTools);
      InspectionToolWrapper toolWrapper1 = (InspectionToolWrapper)currentTools.getTool();
      classifyTool(outGlobalTools, outLocalTools, outGlobalSimpleTools, outSpecialTools, currentTools, toolWrapper1);

      for (ScopeToolState state : currentTools.getTools()) {
        InspectionToolWrapper toolWrapper = (InspectionToolWrapper)state.getTool();
        toolWrapper.initialize(this);
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
      profile.collectDependentInspections((InspectionToolWrapper)tool.getTool(), dependentTools);
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

  private void classifyTool(@NotNull List<Tools> outGlobalTools,
                            @NotNull List<Tools> outLocalTools,
                            @NotNull List<Tools> outGlobalSimpleTools,
                            @NotNull List<Tools> outSpecialTools,
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
      else if (toolWrapper.getTool() instanceof InspectionTool) {
        outSpecialTools.add(currentTools);
      }
      else {
        throw new RuntimeException("unknown global tool " + toolWrapper);
      }
    }
    else if (toolWrapper.getTool() instanceof InspectionTool) {
      outSpecialTools.add(currentTools);
    }
    else {
      throw new RuntimeException("unknown tool " + toolWrapper);
    }
    JobDescriptor[] jobDescriptors = toolWrapper.getJobDescriptors(this);
    for (JobDescriptor jobDescriptor : jobDescriptors) {
      appendJobDescriptor(jobDescriptor);
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
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    cleanup(managerEx);
    AnalysisUIOptions.getInstance(myProject).save(myUIOptions);
    if (myContent != null) {
      final ContentManager contentManager = getContentManager();
      if (contentManager != null) {  //null for tests
        contentManager.removeContent(myContent, true);
      }
    }
    myView = null;
  }

  public void cleanup(final InspectionManagerEx managerEx) {
    managerEx.closeRunningContext(this);
    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper tool = (InspectionToolWrapper)state.getTool();
        tool.finalCleanup();
      }
    }
    cleanup();
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
}
