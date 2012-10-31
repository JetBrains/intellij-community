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
import com.intellij.util.Processor;
import com.intellij.util.TripleFunction;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GlobalInspectionContextImpl extends UserDataHolderBase implements GlobalInspectionContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.GlobalInspectionContextImpl");
  private static final TObjectHashingStrategy<ToolsImpl> TOOLS_HASHING_STRATEGY = new TObjectHashingStrategy<ToolsImpl>() {
    @Override
    public int computeHashCode(ToolsImpl object) {
      return object.getShortName().hashCode();
    }

    @Override
    public boolean equals(ToolsImpl o1, ToolsImpl o2) {
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


  private ProgressIndicator myProgressIndicator;
  public final JobDescriptor BUILD_GRAPH = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor"));
  public final JobDescriptor[] BUILD_GRAPH_ONLY = {BUILD_GRAPH};
  public final JobDescriptor FIND_EXTERNAL_USAGES = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor1"));
  private final JobDescriptor LOCAL_ANALYSIS = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor2"));
  public final JobDescriptor[] LOCAL_ANALYSIS_ARRAY = {LOCAL_ANALYSIS};

  private InspectionProfile myExternalProfile = null;

  private final Map<Key, GlobalInspectionContextExtension> myExtensions = new HashMap<Key, GlobalInspectionContextExtension>();
  private boolean RUN_GLOBAL_TOOLS_ONLY = false;

  private final Map<String, Tools> myTools = new THashMap<String, Tools>();

  private AnalysisUIOptions myUIOptions;
  @NonNls static final String LOCAL_TOOL_ATTRIBUTE = "is_local_tool";

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
      if (availableProfileNames == null || availableProfileNames.length == 0) {
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
  public boolean isSuppressed(PsiElement element, String id) {
    final RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    if (refManager.isDeclarationsFound()) {
      final RefElement refElement = refManager.getReference(element);
      return refElement instanceof RefElementImpl && ((RefElementImpl)refElement).isSuppressed(id);
    }
    return InspectionManagerEx.isSuppressed(element, id);
  }


  public synchronized void addView(InspectionResultsView view, String title) {
    if (myContent != null) return;
    myContentManager.getValue().addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentRemoved(ContentManagerEvent event) {
        if (event.getContent() == myContent){
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

  protected void addView(InspectionResultsView view) {
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
        ((InspectionTool)state.getTool()).cleanup();
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
                final InspectionTool tool = (InspectionTool)toolDescr.getTool();
                if (tool instanceof LocalInspectionToolWrapper) {
                  hasProblems = new File(outputPath, toolName + ext).exists();
                }
                else {
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
            public void visitElement(final RefEntity refEntity) {
              for (Element element : globalTools.keySet()) {
                final Tools tools = globalTools.get(element);
                for (ScopeToolState state : tools.getTools()) {
                  try {
                    ((InspectionTool)state.getTool()).exportResults(element, refEntity);
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
              JDOMUtil.writeDocument(doc, file, "\n");
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


  public boolean isToCheckMember(@NotNull RefElement owner, InspectionProfileEntry tool) {
    return isToCheckFile(((RefElementImpl)owner).getContainingFile(), tool) && !((RefElementImpl)owner).isSuppressed(tool.getShortName());
  }

  public boolean isToCheckFile(PsiFile file, final InspectionProfileEntry tool) {
    final Tools tools = myTools.get(tool.getShortName());
    if (tools != null && file != null) {
      for (ScopeToolState state : tools.getTools()) {
        final NamedScope namedScope = state.getScope(file.getProject());
        if (namedScope == null || namedScope.getValue().contains(file, getCurrentProfile().getProfileManager().getScopesManager())) {
          if (state.isEnabled()) {
            final InspectionProfileEntry entry = state.getTool();
            if (entry instanceof InspectionToolWrapper && ((InspectionToolWrapper)entry).getTool() == tool) return true;
            if (entry == tool) {
              return true;
            }
          }
          return false;
        }
      }
    }
    return false;
  }

  public void ignoreElement(final InspectionTool tool, final PsiElement element) {
    final RefElement refElement = getRefManager().getReference(element);
    final Tools tools = myTools.get(tool.getShortName());
    if (tools != null){
      for (ScopeToolState state : tools.getTools()) {
        ignoreElementRecursively((InspectionTool)state.getTool(), refElement);
      }
    }
  }

  public InspectionResultsView getView() {
    return myView;
  }

  private static void ignoreElementRecursively(final InspectionTool tool, final RefEntity refElement) {
    if (refElement != null) {
      tool.ignoreCurrentElement(refElement);
      final List<RefEntity> children = refElement.getChildren();
      if (children != null) {
        for (RefEntity child : children) {
          ignoreElementRecursively(tool, child);
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

  private void launchInspections(final AnalysisScope scope, final InspectionManager manager) {
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
    myProgressIndicator = ApplicationManager.getApplication().isUnitTestMode() ? new EmptyProgressIndicator() : ProgressManager.getInstance().getProgressIndicator();
    //init manager in read action
    RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    try {
      psiManager.startBatchFilesProcessingMode();
      refManager.inspectionReadActionStarted();
      BUILD_GRAPH.setTotalAmount(scope.getFileCount());
      LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
      FIND_EXTERNAL_USAGES.setTotalAmount(0);
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

  private void runTools(@NotNull AnalysisScope scope, @NotNull final InspectionManager manager) {
    final List<Tools> globalTools = new ArrayList<Tools>();
    final List<Tools> localTools = new ArrayList<Tools>();
    final List<Tools> globalSimpleTools = new ArrayList<Tools>();
    initializeTools(globalTools, localTools, globalSimpleTools);
    final List<InspectionProfileEntry> needRepeatSearchRequest = new ArrayList<InspectionProfileEntry>();
    ((RefManagerImpl)getRefManager()).initializeAnnotators();
    for (Tools tools : globalTools) {
      for (ScopeToolState state : tools.getTools()) {
        final InspectionTool tool = (InspectionTool)state.getTool();
        try {
          if (tool.isGraphNeeded()) {
            ((RefManagerImpl)getRefManager()).findAllDeclarations();
          }
          tool.runInspection(scope, manager);
          if (tool.queryExternalUsagesRequests(manager)) {
            needRepeatSearchRequest.add(tool);
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
          incrementJobDoneAmount(LOCAL_ANALYSIS, ProjectUtil.calcRelativeToProjectPath(virtualFile, myProject));
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
              GlobalInspectionToolWrapper problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, map);
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
      GlobalInspectionToolWrapper problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, map);
      tool.inspectionFinished(manager, this, problemDescriptionProcessor);
    }
  }

  private static GlobalInspectionToolWrapper getProblemDescriptionProcessor(@NotNull final GlobalInspectionToolWrapper toolWrapper,
                                                                            final Map<String, DescriptorProviderInspection> wrappersMap) {

    return new GlobalInspectionToolWrapper(toolWrapper.getTool()) {
      @Override
      public void addProblemElement(RefEntity refEntity, CommonProblemDescriptor... commonProblemDescriptors) {
        for (CommonProblemDescriptor problemDescriptor : commonProblemDescriptors) {
          if (problemDescriptor instanceof ProblemDescriptor) {
            String problemGroup = ((ProblemDescriptor)problemDescriptor).getProblemGroup();

            if (problemGroup != null) {
              DescriptorProviderInspection dummyWrapper = wrappersMap.get(problemGroup);

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
  private static Map<String, DescriptorProviderInspection> getInspectionWrappersMap(List<Tools> tools) {

    Map<String, DescriptorProviderInspection> toolWrappers = new HashMap<String, DescriptorProviderInspection>(tools.size());
    for (Tools tool : tools) {
      InspectionProfileEntry profileEntry = tool.getTool();
      if (profileEntry instanceof DescriptorProviderInspection) {
        toolWrappers.put(profileEntry.getShortName(), (DescriptorProviderInspection)profileEntry);
      }
    }

    return toolWrappers;
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
    final List<ToolsImpl> usedTools = getUsedTools();
    for (Tools currentTools : usedTools) {
      final String shortName = currentTools.getShortName();
      myTools.put(shortName, currentTools);
      final InspectionTool tool = (InspectionTool)currentTools.getTool();
      classifyTool(outGlobalTools, outLocalTools, outGlobalSimpleTools, currentTools, tool);

      for (ScopeToolState state : currentTools.getTools()) {
        ((InspectionTool)state.getTool()).initialize(this);
      }
    }
    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.performPreRunActivities(outGlobalTools, outLocalTools, this);
    }
  }

  protected List<ToolsImpl> getUsedTools() {
    InspectionProfileImpl profile = new InspectionProfileImpl((InspectionProfileImpl)getCurrentProfile());
    List<ToolsImpl> tools = profile.getAllEnabledInspectionTools(myProject);
    THashSet<ToolsImpl> set = null;
    for (ToolsImpl tool : tools) {
      String id = tool.getTool().getMainToolId();
      if (id != null) {
        InspectionProfileEntry mainTool = profile.getInspectionTool(id);
        LOG.assertTrue(mainTool != null, "Can't find main tool: " + id);
        if (set == null) {
          set = new THashSet<ToolsImpl>(tools, TOOLS_HASHING_STRATEGY);
        }
        set.add(new ToolsImpl(mainTool, mainTool.getDefaultLevel(), true));
      }
    }
    return set == null ? tools : new ArrayList<ToolsImpl>(set);
  }

  private void classifyTool(List<Tools> outGlobalTools,
                            List<Tools> outLocalTools,
                            List<Tools> outGlobalSimpleTools,
                            Tools currentTools,
                            InspectionTool tool) {
    if (tool instanceof LocalInspectionToolWrapper) {
      outLocalTools.add(currentTools);
    }
    else if (tool instanceof GlobalInspectionToolWrapper && ((GlobalInspectionToolWrapper)tool).getTool() instanceof GlobalSimpleInspectionTool) {
      outGlobalSimpleTools.add(currentTools);
    }
    else {
      outGlobalTools.add(currentTools);
    }
    JobDescriptor[] jobDescriptors = tool.getJobDescriptors(this);
    for (JobDescriptor jobDescriptor : jobDescriptors) {
      appendJobDescriptor(jobDescriptor);
    }
  }

  public Map<String, Tools> getTools() {
    return myTools;
  }

  private void appendJobDescriptor(@NotNull JobDescriptor job) {
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
        ((InspectionTool)state.getTool()).finalCleanup();
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
    float totalProgress = 0;
    int liveDescriptors = 0;
    for (JobDescriptor jobDescriptor : myJobDescriptors) {
      totalProgress += jobDescriptor.getProgress();
      liveDescriptors += jobDescriptor.getTotalAmount() == 0 ? 0 : 1;
    }

    return totalProgress / liveDescriptors;
  }

  public void setExternalProfile(InspectionProfile profile) {
    myExternalProfile = profile;
  }
}
