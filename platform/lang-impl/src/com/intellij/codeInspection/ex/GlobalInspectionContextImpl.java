// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.analysis.problemsView.toolWindow.ProblemsView;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.actions.CleanupInspectionUtil;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.DefaultInspectionToolPresentation;
import com.intellij.codeInspection.ui.DelegatedInspectionToolPresentation;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.JobLauncherImpl;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.lang.LangBundle;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static com.intellij.codeInspection.ex.InspectListener.InspectionKind.GLOBAL;
import static com.intellij.codeInspection.ex.InspectListener.InspectionKind.GLOBAL_SIMPLE;
import static com.intellij.codeInspection.ex.InspectionEventsKt.reportWhenActivityFinished;
import static com.intellij.codeInspection.ex.InspectionEventsKt.reportWhenInspectionFinished;

public class GlobalInspectionContextImpl extends GlobalInspectionContextEx {
  private static final Logger LOG = Logger.getInstance(GlobalInspectionContextImpl.class);
  @SuppressWarnings("StaticNonFinalField")
  @TestOnly
  public static volatile boolean TESTING_VIEW;
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager
    .getInstance()
    .getNotificationGroup("Inspection Results");

  private final NotNullLazyValue<? extends ContentManager> myContentManager;
  private volatile InspectionResultsView myView;
  private Content myContent;
  private volatile boolean myViewClosed = true;
  private long myInspectionStartedTimestamp;
  private final ConcurrentMap<InspectionToolWrapper<?, ?>, InspectionToolPresentation> myPresentationMap = new ConcurrentHashMap<>();
  private boolean forceInspectAllScope;

  public GlobalInspectionContextImpl(@NotNull Project project, @NotNull NotNullLazyValue<? extends ContentManager> contentManager) {
    super(project);

    myContentManager = contentManager;
  }

  private @NotNull InspectListener getEventPublisher() {
    return getProject().getMessageBus().syncPublisher(GlobalInspectionContextEx.INSPECT_TOPIC);
  }

  private @NotNull ContentManager getContentManager() {
    return myContentManager.getValue();
  }

  public void addView(@NotNull InspectionResultsView view,
                      @NotNull @NlsContexts.TabTitle String title,
                      boolean isOffline) {
    LOG.assertTrue(myContent == null, "GlobalInspectionContext is busy under other view now");
    myView = view;
    if (!isOffline) {
      myView.setUpdating(true);
    }
    myContent = ContentFactory.SERVICE.getInstance().createContent(view, title, false);
    myContent.setHelpId(InspectionResultsView.HELP_ID);
    myContent.setDisposer(myView);
    Disposer.register(myContent, () -> {
      if (myView != null) {
        close(false);
      }
      myContent = null;
    });
    RefManagerImpl.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionRemoved(@NotNull RefGraphAnnotator graphAnnotator, @NotNull PluginDescriptor pluginDescriptor) {
        ((RefManagerImpl)getRefManager()).unregisterAnnotator(graphAnnotator);
      }
    }, myContent);

    ContentManager contentManager = getContentManager();
    contentManager.addContent(myContent);
    contentManager.setSelectedContent(myContent);

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(getProject());
    ToolWindow toolWindow = toolWindowManager.getToolWindow(ProblemsView.ID);
    if (toolWindow == null) { // TODO: compatibility mode for Rider where there's no problems view; remove in 2021.2
      //noinspection deprecation
      toolWindow = toolWindowManager.getToolWindow(ToolWindowId.INSPECTION);
    }
    if (toolWindow != null)
      toolWindow.activate(null);
  }

  public void addView(@NotNull InspectionResultsView view) {
    addView(view, InspectionsBundle.message(view.isSingleInspectionRun() ?
                                            "inspection.results.for.inspection.toolwindow.title" :
                                            "inspection.results.for.profile.toolwindow.title",
                                            view.getCurrentProfileName(), getCurrentScope().getShortenName()), false);

  }

  @Override
  public void doInspections(@NotNull AnalysisScope scope) {
    if (myContent != null) {
      getContentManager().removeContent(myContent, true);
    }
    super.doInspections(scope);
  }

  public void resolveElement(@NotNull InspectionProfileEntry tool, @NotNull PsiElement element) {
    RefElement refElement = getRefManager().getReference(element);
    if (refElement == null) return;
    Tools tools = getTools().get(tool.getShortName());
    if (tools != null){
      for (ScopeToolState state : tools.getTools()) {
        InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
        InspectionToolResultExporter presentation = getPresentationOrNull(toolWrapper);
        if (presentation != null) {
          resolveElementRecursively(presentation, refElement);
        }
      }
    }
  }

  public InspectionResultsView getView() {
    return myView;
  }

  public boolean isForceInspectAllScope() {
    return forceInspectAllScope;
  }

  public void setForceInspectAllScope(boolean forceInspectAllScope) {
    this.forceInspectAllScope = forceInspectAllScope;
  }

  private static void resolveElementRecursively(@NotNull InspectionToolResultExporter presentation, @NotNull RefEntity refElement) {
    presentation.suppressProblem(refElement);
    List<RefEntity> children = refElement.getChildren();
    for (RefEntity child : children) {
      resolveElementRecursively(presentation, child);
    }
  }

  public @NotNull AnalysisUIOptions getUIOptions() {
    return AnalysisUIOptions.getInstance(getProject());
  }

  public void setSplitterProportion(float proportion) {
    getUIOptions().SPLITTER_PROPORTION = proportion;
  }

  public @NotNull ToggleAction createToggleAutoscrollAction() {
    return getUIOptions().getAutoScrollToSourceHandler().createToggleAction();
  }

  @Override
  protected void launchInspections(@NotNull AnalysisScope scope) {
    myViewClosed = false;
    super.launchInspections(scope);
  }

  @Override
  protected @NotNull PerformInBackgroundOption createOption() {
    return new PerformAnalysisInBackgroundOption(getProject());
  }

  @Override
  protected void notifyInspectionsFinished(@NotNull AnalysisScope scope) {
    //noinspection TestOnlyProblems
    if (ApplicationManager.getApplication().isUnitTestMode() && !TESTING_VIEW) return;
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    long elapsed = System.currentTimeMillis() - myInspectionStartedTimestamp;
    LOG.info("Code inspection finished. Took " + elapsed + " ms");
    if (SystemProperties.getBooleanProperty("idea.is.integration.test", false)) {
      String logPath = PathManager.getLogPath();
      Path perfMetrics = Paths.get(logPath).resolve("performance-metrics").resolve("inspectionMetrics.json");
      try {
        FileUtil.writeToFile(perfMetrics.toFile(), "{\n\t\"inspection_execution_time\" : " + elapsed + "\n}");
      }
      catch (IOException ex) {
        LOG.info("Could not create json file " + perfMetrics + " with the performance metrics.");
      }
    }
    if (getProject().isDisposed()) return;

    InspectionResultsView oldView = myView;
    InspectionResultsView newView = oldView == null ? new InspectionResultsView(this, new InspectionRVContentProviderImpl()) : null;
    ReadAction
      .nonBlocking(() -> (oldView == null ? newView : oldView).hasProblems())
      .finishOnUiThread(ModalityState.any(), hasProblems -> {
        if (!hasProblems) {
          showNoProblemsNotification(scope, newView);
        }
        else if (newView != null && !newView.isDisposed() && getCurrentScope() != null) {
          addView(newView);
          newView.update();
        }
        if (myView != null) {
          myView.setUpdating(false);
        }
      })
      .expireWith(getProject())
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void showNoProblemsNotification(@NotNull AnalysisScope scope, InspectionResultsView newView) {
    int totalFiles = getStdJobDescriptors().BUILD_GRAPH.getTotalAmount(); // do not use invalidated scope

    var notification = NOTIFICATION_GROUP.createNotification(InspectionsBundle.message("inspection.no.problems.message",
                                                                                       totalFiles,
                                                                                       scope.getShortenName()), MessageType.INFO);
    if (!scope.isIncludeTestSource()) addRepeatWithTestsAction(scope, notification, () -> doInspections(scope));
    notification.notify(getProject());

    close(true);
    if (newView != null) {
      Disposer.dispose(newView);
    }
  }

  @Override
  protected void runTools(@NotNull AnalysisScope scope, boolean runGlobalToolsOnly, boolean isOfflineInspections) {
    myInspectionStartedTimestamp = System.currentTimeMillis();
    ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progressIndicator == null) {
      throw new IncorrectOperationException("Must be run under progress");
    }
    if (!isOfflineInspections && ApplicationManager.getApplication().isDispatchThread()) {
      throw new IncorrectOperationException("Must not start inspections from within EDT");
    }
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throw new IncorrectOperationException("Must not start inspections from within write action");
    }
    // in offline inspection application we don't care about global read action
    if (!isOfflineInspections && ApplicationManager.getApplication().isReadAccessAllowed()) {
      throw new IncorrectOperationException("Must not start inspections from within global read action");
    }
    InspectionManager inspectionManager = InspectionManager.getInstance(getProject());
    ((RefManagerImpl)getRefManager()).initializeAnnotators();
    List<Tools> globalTools = new ArrayList<>();
    List<Tools> localTools = new ArrayList<>();
    List<Tools> globalSimpleTools = new ArrayList<>();
    initializeTools(globalTools, localTools, globalSimpleTools);
    appendPairedInspectionsForUnfairTools(globalTools, globalSimpleTools, localTools);
    runGlobalTools(scope, inspectionManager, globalTools, isOfflineInspections);

    if (runGlobalToolsOnly || localTools.isEmpty() && globalSimpleTools.isEmpty()) return;

    SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
    Set<VirtualFile> localScopeFiles = searchScope instanceof LocalSearchScope ? new HashSet<>() : null;
    for (Tools tools : globalSimpleTools) {
      GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
      GlobalSimpleInspectionTool tool = (GlobalSimpleInspectionTool)toolWrapper.getTool();
      tool.inspectionStarted(inspectionManager, this, getPresentation(toolWrapper));
    }

    boolean headlessEnvironment = ApplicationManager.getApplication().isHeadlessEnvironment();
    boolean inspectInjectedPsi = Registry.is("idea.batch.inspections.inspect.injected.psi", true);

    Map<String, InspectionToolWrapper<?, ?>> map = getInspectionWrappersMap(localTools);

    BlockingQueue<VirtualFile> filesToInspect = new ArrayBlockingQueue<>(1000);
    // use original progress indicator here since we don't want it to cancel on write action start
    ProgressIndicator iteratingIndicator = new SensitiveProgressWrapper(progressIndicator);
    Future<?> future = startIterateScopeInBackground(scope, iteratingIndicator, headlessEnvironment, localScopeFiles, filesToInspect);

    PsiManager psiManager = PsiManager.getInstance(getProject());
    Processor<VirtualFile> processor = virtualFile -> {
      ProgressManager.checkCanceled();
      Boolean readActionSuccess = DumbService.getInstance(getProject()).tryRunReadActionInSmartMode(() -> {
        long start = getPathProfile() == null ? 0 : System.currentTimeMillis();
        PsiFile file = virtualFile.isValid() ? psiManager.findFile(virtualFile) : null;
        if (file == null) {
          return true;
        }
        if (!scope.contains(virtualFile)) {
          LOG.info(file.getName() + "; scope: " + scope + "; " + virtualFile);
          return true;
        }
        boolean includeDoNotShow = includeDoNotShow(getCurrentProfile());
        List<GlobalInspectionToolWrapper> globalSimpleWrappers = getWrappersFromTools(globalSimpleTools, file, includeDoNotShow,
                                                                       wrapper -> !(wrapper.getTool() instanceof ExternalAnnotatorBatchInspection));
        List<LocalInspectionToolWrapper> localToolWrappers = getWrappersFromTools(localTools, file, includeDoNotShow,
                                                                      wrapper -> !(wrapper.getTool() instanceof ExternalAnnotatorBatchInspection));
        inspectFile(file, getEffectiveRange(searchScope, file), inspectionManager, map,
                    globalSimpleWrappers,
                    localToolWrappers,
                    inspectInjectedPsi && scope.isAnalyzeInjectedCode());
        if (start != 0) {
          updateProfile(virtualFile, System.currentTimeMillis() - start);
        }
        return true;
      }, LangBundle.message("popup.content.inspect.code.not.available.until.indices.are.ready"));
      if (readActionSuccess == null || !readActionSuccess) {
        throw new ProcessCanceledException();
      }

      PsiFile file = ReadAction.compute(() -> psiManager.findFile(virtualFile));
      if (file == null) {
        return true;
      }
      getEventPublisher().fileAnalyzed(file, getProject());

      boolean includeDoNotShow = includeDoNotShow(getCurrentProfile());
      List<InspectionToolWrapper<?, ?>> externalAnnotatable = ContainerUtil.concat(
        getWrappersFromTools(localTools, file, includeDoNotShow, wrapper -> wrapper.getTool() instanceof ExternalAnnotatorBatchInspection),
        getWrappersFromTools(globalSimpleTools, file, includeDoNotShow, wrapper -> wrapper.getTool() instanceof ExternalAnnotatorBatchInspection));
      externalAnnotatable.forEach(wrapper -> {
        ProblemDescriptor[] descriptors = ((ExternalAnnotatorBatchInspection)wrapper.getTool()).checkFile(file, this, inspectionManager);
        InspectionToolResultExporter toolPresentation = getPresentation(wrapper);
        ReadAction.run(() -> BatchModeDescriptorsUtil
          .addProblemDescriptors(Arrays.asList(descriptors), false, this, null, toolPresentation, CONVERT));
      });

      return true;
    };
    try {
      Queue<VirtualFile> filesFailedToInspect = new LinkedBlockingQueue<>();
      while (true) {
        Disposable disposable = Disposer.newDisposable();
        ProgressIndicator wrapper = new DaemonProgressIndicator();
        dependentIndicators.add(wrapper);
        try {
          // avoid "attach listener"/"write action" race
          ReadAction.run(() -> {
            wrapper.start();
            ProgressIndicatorUtils.forceWriteActionPriority(wrapper, disposable);
            // there is a chance we are racing with write action, in which case just registered listener might not be called, retry.
            if (ApplicationManagerEx.getApplicationEx().isWriteActionPending()) {
              throw new ProcessCanceledException();
            }
          });
          // use wrapper here to cancel early when write action start but do not affect the original indicator
          ((JobLauncherImpl)JobLauncher.getInstance()).processQueue(filesToInspect, filesFailedToInspect, wrapper, TOMBSTONE, processor);
          break;
        }
        catch (ProcessCanceledException e) {
          progressIndicator.checkCanceled();
          // PCE may be thrown from inside wrapper when write action started.
          // Go on with the write and then resume processing the rest of the queue.
          assert isOfflineInspections || !ApplicationManager.getApplication().isReadAccessAllowed()
            : "Must be outside read action. PCE=\n" + ExceptionUtil.getThrowableText(e);
          assert isOfflineInspections || !ApplicationManager.getApplication().isDispatchThread()
            : "Must be outside EDT. PCE=\n" + ExceptionUtil.getThrowableText(e);

          // wait for write action to complete
          ApplicationManager.getApplication().runReadAction(EmptyRunnable.getInstance());
        }
        finally {
          dependentIndicators.remove(wrapper);
          Disposer.dispose(disposable);
        }
      }
    }
    finally {
      iteratingIndicator.cancel(); // tell file scanning thread to stop
      filesToInspect.clear(); // let file scanning thread a chance to put TOMBSTONE and complete
      try {
        future.get(30, TimeUnit.SECONDS);
      }
      catch (Exception e) {
        LOG.error("Thread dump: \n"+ThreadDumper.dumpThreadsToString(), e);
      }
    }

    ProgressManager.checkCanceled();

    for (Tools tools : globalSimpleTools) {
      GlobalInspectionToolWrapper toolWrapper = (GlobalInspectionToolWrapper)tools.getTool();
      GlobalSimpleInspectionTool tool = (GlobalSimpleInspectionTool)toolWrapper.getTool();
      ProblemDescriptionsProcessor problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, map);
      tool.inspectionFinished(inspectionManager, this, problemDescriptionProcessor);

    }

    addProblemsToView(globalSimpleTools);
  }

  private static @NotNull TextRange getEffectiveRange(@NotNull SearchScope searchScope, @NotNull PsiFile file) {
    if (searchScope instanceof LocalSearchScope) {
      List<PsiElement> scopeFileElements = ContainerUtil.filter(((LocalSearchScope)searchScope).getScope(), e -> e.getContainingFile() == file);
      if (!scopeFileElements.isEmpty()) {
        int start = -1;
        int end = -1;
        for (PsiElement scopeElement : scopeFileElements) {
          TextRange elementRange = scopeElement.getTextRange();
          start = start == -1 ? elementRange.getStartOffset() : Math.min(elementRange.getStartOffset(), start);
          end = end == -1 ? elementRange.getEndOffset() : Math.max(elementRange.getEndOffset(), end);
        }
        return new TextRange(start, end);
      }
    }
    return new TextRange(0, file.getTextLength());
  }

  // indicators which should be canceled once the main indicator myProgressIndicator is
  private final List<ProgressIndicator> dependentIndicators = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  protected void canceled() {
    super.canceled();
    dependentIndicators.forEach(ProgressIndicator::cancel);
  }

  private void inspectFile(@NotNull PsiFile file,
                           @NotNull TextRange restrictRange,
                           @NotNull InspectionManager inspectionManager,
                           @NotNull Map<String, InspectionToolWrapper<?, ?>> wrappersMap,
                           @NotNull List<? extends GlobalInspectionToolWrapper> globalSimpleTools,
                           @NotNull List<? extends LocalInspectionToolWrapper> localTools,
                           boolean inspectInjectedPsi) {
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    if (document == null) return;

    InspectionProfileWrapper.runWithCustomInspectionWrapper(file, p -> new InspectionProfileWrapper(getCurrentProfile()), () -> {
      try {
        Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
          InspectionEngine.inspectEx(localTools, file, restrictRange, restrictRange, false, inspectInjectedPsi, true,
                                     myProgressIndicator,
                                     (wrapper, descriptor) -> true);
        for (Map.Entry<LocalInspectionToolWrapper, List<ProblemDescriptor>> entry : map.entrySet()) {
          LocalInspectionToolWrapper toolWrapper = entry.getKey();
          List<ProblemDescriptor> descriptors = entry.getValue();
          InspectionToolPresentation toolPresentation = getPresentation(toolWrapper);
          BatchModeDescriptorsUtil.addProblemDescriptors(descriptors, toolPresentation, true, this, toolWrapper.getTool());
        }

        assertUnderDaemonProgress();

        JobLauncher.getInstance()
          .invokeConcurrentlyUnderProgress(globalSimpleTools, ProgressIndicatorProvider.getGlobalProgressIndicator(), toolWrapper -> {
            GlobalSimpleInspectionTool tool = (GlobalSimpleInspectionTool)toolWrapper.getTool();
            ProblemsHolder holder = new ProblemsHolder(inspectionManager, file, false);
            ProblemDescriptionsProcessor problemDescriptionProcessor = getProblemDescriptionProcessor(toolWrapper, wrappersMap);
            reportWhenInspectionFinished(
              getEventPublisher(),
              toolWrapper,
              GLOBAL_SIMPLE,
              file,
              getProject(),
              () -> {
                tool.checkFile(file, inspectionManager, holder, this, problemDescriptionProcessor);
                return holder.getResultCount();
              });
            InspectionToolResultExporter toolPresentation = getPresentation(toolWrapper);
            BatchModeDescriptorsUtil.addProblemDescriptors(holder.getResults(), false, this, null, toolPresentation, CONVERT);
            return true;
          });
        VirtualFile virtualFile = file.getVirtualFile();
        String displayUrl = ProjectUtilCore.displayUrlRelativeToProject(virtualFile, virtualFile.getPresentableUrl(), getProject(), true, false);
        incrementJobDoneAmount(getStdJobDescriptors().LOCAL_ANALYSIS, displayUrl);
      }
      catch (ProcessCanceledException | IndexNotReadyException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error("In file: " + file.getViewProvider().getVirtualFile().getPath(), e);
      }
      finally {
        InjectedLanguageManager.getInstance(getProject()).dropFileCaches(file);
      }
    });
  }

  protected boolean includeDoNotShow(InspectionProfile profile) {
    return profile.getSingleTool() != null;
  }

  private static final VirtualFile TOMBSTONE = new LightVirtualFile("TOMBSTONE");

  private @NotNull Future<?> startIterateScopeInBackground(@NotNull AnalysisScope scope,
                                                           @NotNull ProgressIndicator progressIndicator,
                                                           boolean headlessEnvironment,
                                                           @Nullable Collection<? super VirtualFile> localScopeFiles,
                                                           @NotNull BlockingQueue<? super VirtualFile> outFilesToInspect) {
    Task.Backgroundable task = new Task.Backgroundable(getProject(), InspectionsBundle.message("scanning.files.to.inspect.progress.text")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          Project project = scope.getProject();
          FileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
          scope.accept(file -> {
            ProgressManager.checkCanceled();
            if (!forceInspectAllScope &&
                (ProjectUtil.isProjectOrWorkspaceFile(file) || !ReadAction.compute(() -> fileIndex.isInContent(file)))) return true;

            PsiFile psiFile = ReadAction.compute(() -> {
              if (project.isDisposed()) throw new ProcessCanceledException();
              PsiFile psi = PsiManager.getInstance(project).findFile(file);
              Document document = psi == null ? null : shouldProcess(psi, headlessEnvironment, localScopeFiles);
              if (document != null) {
                return psi;
              }
              return null;
            });
            // do not inspect binary files
            if (psiFile != null) {
              try {
                if (ApplicationManager.getApplication().isReadAccessAllowed()) {
                  throw new IllegalStateException("Must not have read action");
                }
                outFilesToInspect.put(file);
              }
              catch (InterruptedException e) {
                LOG.error(e);
              }
            }
            ProgressManager.checkCanceled();
            return true;
          });
        }
        catch (ProcessCanceledException e) {
          // ignore, but put tombstone
        }
        finally {
          try {
            outFilesToInspect.put(TOMBSTONE);
          }
          catch (InterruptedException e) {
            LOG.error(e);
          }
        }
      }
    };
    return ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  private Document shouldProcess(@NotNull PsiFile file, boolean headlessEnvironment, @Nullable Collection<? super VirtualFile> localScopeFiles) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return null;
    if (isBinary(file)) return null; //do not inspect binary files

    if (myViewClosed && !headlessEnvironment) {
      throw new ProcessCanceledException();
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Running local inspections on " + virtualFile.getPath());
    }

    if (SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile)) return null;
    if (localScopeFiles != null && !localScopeFiles.add(virtualFile)) return null;
    if (!ProblemHighlightFilter.shouldProcessFileInBatch(file)) return null;

    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  private void runGlobalTools(@NotNull AnalysisScope scope,
                              @NotNull InspectionManager inspectionManager,
                              @NotNull List<? extends Tools> globalTools,
                              boolean isOfflineInspections) {
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed() || isOfflineInspections, "Must not run under read action, too unresponsive");
    buildRefGraphIfNeeded(globalTools);

    List<InspectionToolWrapper<?, ?>> needRepeatSearchRequest = new ArrayList<>();
    SearchScope initialSearchScope = ReadAction.compute(scope::toSearchScope);
    boolean canBeExternalUsages = !(scope.getScopeType() == AnalysisScope.PROJECT && scope.isIncludeTestSource());
    InspectListener eventPublisher = getEventPublisher();
    for (Tools tools : globalTools) {
      for (ScopeToolState state : tools.getTools()) {
        if (!state.isEnabled()) continue;
        NamedScope stateScope = state.getScope(getProject());
        if (stateScope == null) continue;

        AnalysisScope scopeForState = new AnalysisScope(GlobalSearchScopesCore.filterScope(getProject(), stateScope)
                                                          .intersectWith(initialSearchScope), getProject());
        InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
        GlobalInspectionTool tool = (GlobalInspectionTool)toolWrapper.getTool();
        InspectionToolResultExporter toolPresentation = getPresentation(toolWrapper);
        try {
          ThrowableRunnable<RuntimeException> runnable = () -> {
            reportWhenInspectionFinished(
              eventPublisher,
              toolWrapper,
              GLOBAL,
              null,
              getProject(),
              () -> {
                tool.runInspection(scopeForState, inspectionManager, this, toolPresentation);
                return toolPresentation.getProblemDescriptors().size();
              });

            //skip phase when we are sure that scope already contains everything, unused declaration though needs to proceed with its suspicious code
            if ((canBeExternalUsages || tool.getAdditionalJobs(this) != null) &&
                tool.queryExternalUsagesRequests(inspectionManager, this, toolPresentation)) {
              needRepeatSearchRequest.add(toolWrapper);
            }
          };
          if (tool.isReadActionNeeded()) {
            ReadAction.run(runnable);
          }
          else {
            runnable.run();
          }
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
    reportWhenActivityFinished(
      eventPublisher,
      InspectListener.ActivityKind.GLOBAL_POST_RUN_ACTIVITIES,
      getProject(),
      () -> processPostRunActivities(needRepeatSearchRequest));
    addProblemsToView(globalTools);
  }

  private void buildRefGraphIfNeeded(List<? extends Tools> globalTools) {
    for (Tools tools : globalTools) {
      for (ScopeToolState state : tools.getTools()) {
        if (!state.isEnabled()) continue;
        NamedScope stateScope = state.getScope(getProject());
        if (stateScope == null) continue;

        InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
        GlobalInspectionTool tool = (GlobalInspectionTool)toolWrapper.getTool();
        if (tool.isGraphNeeded()) {
          try {
            reportWhenActivityFinished(
              getEventPublisher(),
              InspectListener.ActivityKind.REFERENCE_SEARCH,
              getProject(),
              () -> ((RefManagerImpl)getRefManager()).findAllDeclarations());
          }
          catch (Throwable e) {
            getStdJobDescriptors().BUILD_GRAPH.setDoneAmount(0);
            throw e;
          }
          return;
        }
      }
    }
  }

  // todo move the logic to appropriate place if it's needed
  @SuppressWarnings("unused")
  private static void logGraph(RefManager refManager) {
    List<RefElement> roots = new ArrayList<>();
    MultiMap<RefElement, RefElement> entitiesWithParents = new MultiMap<>();
    refManager.iterate(new RefVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity elem) {
        if (elem instanceof RefElement) {
          RefElement refElement = (RefElement)elem;
          entitiesWithParents.put(refElement, refElement.getInReferences());
          if (refElement.getInReferences().isEmpty() || refElement.getInReferences().contains(refElement)) {
            roots.add(refElement);
          }
        }
      }
    });
    StringBuilder result = new StringBuilder("\n");
    for (RefElement root : roots) {
      List<List<RefElement>> paths = new ArrayList<>();
      paths.add(new ArrayList<>());
      traverse(root, paths, 0);
      String rootText = String.format("%s %s (owner: %s, reachable: %s, entry: %s):", root.getClass().getSimpleName(), root.getName(),
                                      root.getOwner(), root.isReachable(), root.isEntry());
      result.append(rootText).append("\n");
      for (List<RefElement> path : paths) {
        if (path.size() <= 1) continue;
        StringJoiner pathJoiner = new StringJoiner(" --> ");
        for (int i = 1; i < path.size(); i++) {
          RefElement element = path.get(i);
          String elementText = String.format("%s %s (owner: %s, reachable: %s)", element.getClass().getSimpleName(), element.getName(),
                                             element.getOwner(), element.isReachable());
          pathJoiner.add(elementText);
        }
        result.append(" --> ").append(pathJoiner).append("\n");
      }
    }
    LOG.warn(result.toString());
  }

  private static void traverse(RefElement element, List<List<RefElement>> paths, Integer depth) {
    int lastPathsIndex = paths.size() - 1;
    List<RefElement> path = paths.get(lastPathsIndex);
    if (path.contains(element)) {
      return;
    }
    path.add(element);
    List<RefElement> outRefs = new ArrayList<>(element.getOutReferences());
    for (int i = 0; i < outRefs.size(); i++) {
      RefElement outRef = outRefs.get(i);
      if (i > 0) {
        List<RefElement> pathCopy = new ArrayList<>(path.subList(0, depth + 1));
        paths.add(pathCopy);
      }
      depth++;
      traverse(outRef, paths, depth);
      depth--;
    }
  }

  private void processPostRunActivities(List<InspectionToolWrapper<?, ?>> needRepeatSearchRequest) {
    for (GlobalInspectionContextExtension<?> extension : myExtensions.values()) {
      try {
        extension.performPostRunActivities(needRepeatSearchRequest, this);
      }
      catch (ProcessCanceledException | IndexNotReadyException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  public ActionCallback initializeViewIfNeeded() {
    if (myView != null) {
      return ActionCallback.DONE;
    }
    ActionCallback callback = new ActionCallback();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (getCurrentScope() == null) {
        callback.setDone();
        return;
      }
      InspectionResultsView view = getView();
      if (view == null) {
        view = new InspectionResultsView(this, new InspectionRVContentProviderImpl());
        addView(view);
      }
      callback.setDone();
    });
    return callback;
  }

  private void appendPairedInspectionsForUnfairTools(@NotNull List<? super Tools> globalTools,
                                                     @NotNull List<? super Tools> globalSimpleTools,
                                                     @NotNull List<Tools> localTools) {
    Tools[] lArray = localTools.toArray(new Tools[0]);
    for (Tools tool : lArray) {
      LocalInspectionToolWrapper toolWrapper = (LocalInspectionToolWrapper)tool.getTool();
      LocalInspectionTool localTool = toolWrapper.getTool();
      if (localTool instanceof PairedUnfairLocalInspectionTool) {
        String batchShortName = ((PairedUnfairLocalInspectionTool)localTool).getInspectionForBatchShortName();
        InspectionProfile currentProfile = getCurrentProfile();
        InspectionToolWrapper<?, ?> batchInspection;
        InspectionToolWrapper<?, ?> pairedWrapper = currentProfile.getInspectionTool(batchShortName, getProject());
        batchInspection = pairedWrapper != null ? pairedWrapper.createCopy() : null;
        if (batchInspection != null && !getTools().containsKey(batchShortName)) {
          // add to existing inspections to run
          InspectionProfileEntry batchTool = batchInspection.getTool();
          ScopeToolState defaultState = tool.getDefaultState();
          ToolsImpl newTool = new ToolsImpl(batchInspection, defaultState.getLevel(), true, defaultState.isEnabled());
          for (ScopeToolState state : tool.getTools()) {
            NamedScope scope = state.getScope(getProject());
            if (scope != null) {
              newTool.addTool(scope, batchInspection, state.isEnabled(), state.getLevel());
            }
          }
          if (batchTool instanceof LocalInspectionTool) localTools.add(newTool);
          else if (batchTool instanceof GlobalSimpleInspectionTool) globalSimpleTools.add(newTool);
          else if (batchTool instanceof GlobalInspectionTool) globalTools.add(newTool);
          else throw new AssertionError(batchTool);
          getTools().put(batchShortName, newTool);
          batchInspection.initialize(this);
        }
      }
    }
  }

  public @NotNull <T extends InspectionToolWrapper<?, ?>> List<T> getWrappersFromTools(@NotNull List<? extends Tools> localTools,
                                                                                       @NotNull PsiFile file,
                                                                                       boolean includeDoNotShow,
                                                                                       @NotNull Predicate<? super T> filter) {
    return ContainerUtil.mapNotNull(localTools, tool -> {
      //noinspection unchecked
      T unwrapped = (T)tool.getEnabledTool(file, includeDoNotShow);
      if (unwrapped == null) return null;
      return filter.test(unwrapped) ? unwrapped : null;
    });
  }

  private @NotNull ProblemDescriptionsProcessor getProblemDescriptionProcessor(@NotNull GlobalInspectionToolWrapper toolWrapper,
                                                                               @NotNull Map<String, InspectionToolWrapper<?, ?>> wrappersMap) {
    return new ProblemDescriptionsProcessor() {
      @Override
      public void addProblemElement(@Nullable RefEntity refEntity, CommonProblemDescriptor @NotNull ... commonProblemDescriptors) {
        for (CommonProblemDescriptor problemDescriptor : commonProblemDescriptors) {
          if (!(problemDescriptor instanceof ProblemDescriptor)) {
            continue;
          }
          if (SuppressionUtil.inspectionResultSuppressed(((ProblemDescriptor)problemDescriptor).getPsiElement(), toolWrapper.getTool())) {
            continue;
          }
          ProblemGroup problemGroup = ((ProblemDescriptor)problemDescriptor).getProblemGroup();

          InspectionToolWrapper<?, ?> targetWrapper = problemGroup == null ? toolWrapper : wrappersMap.get(problemGroup.getProblemName());
          if (targetWrapper != null) { // Else it's switched off
            InspectionToolResultExporter toolPresentation = getPresentation(targetWrapper);
            toolPresentation.addProblemElement(refEntity, problemDescriptor);
          }
        }
      }
    };
  }

  private static @NotNull Map<String, InspectionToolWrapper<?, ?>> getInspectionWrappersMap(@NotNull List<? extends Tools> tools) {
    Map<String, InspectionToolWrapper<?, ?>> name2Inspection = new HashMap<>(tools.size());
    for (Tools tool : tools) {
      InspectionToolWrapper<?, ?> toolWrapper = tool.getTool();
      name2Inspection.put(toolWrapper.getShortName(), toolWrapper);
    }

    return name2Inspection;
  }

  private static final TripleFunction<LocalInspectionTool,PsiElement,GlobalInspectionContext,RefElement> CONVERT =
    (tool, elt, context) -> {
      PsiNamedElement problemElement = PsiTreeUtil.getNonStrictParentOfType(elt, PsiFile.class);

      RefElement refElement = context.getRefManager().getReference(problemElement);
      if (refElement == null && problemElement != null) {  // no need to lose collected results
        refElement = GlobalInspectionContextUtil.retrieveRefElement(elt, context);
      }
      return refElement;
    };


  @Override
  public void close(boolean noSuspiciousCodeFound) {
    if (!noSuspiciousCodeFound) {
      if (myView.isRerun()) {
        myViewClosed = true;
        myView = null;
      }
      if (myView == null) {
        return;
      }
    }
    if (myContent != null) {
      ContentManager contentManager = getContentManager();
      contentManager.removeContent(myContent, true);
    }
    myViewClosed = true;
    myView = null;
    ((InspectionManagerEx)InspectionManager.getInstance(getProject())).closeRunningContext(this);
    myPresentationMap.clear();
    super.close(noSuspiciousCodeFound);
  }

  @Override
  public void cleanup() {
    if (myView != null) {
      myView.setUpdating(false);
    }
    else {
      myPresentationMap.clear();
      super.cleanup();
    }
  }

  public void refreshViews() {
    if (myView != null) {
      myView.getTree().getInspectionTreeModel().reload();
    }
  }

  private @Nullable InspectionToolResultExporter getPresentationOrNull(@NotNull InspectionToolWrapper<?, ?> toolWrapper) {
    return myPresentationMap.get(toolWrapper);
  }

  @Override
  public void codeCleanup(@NotNull AnalysisScope scope,
                          @NotNull InspectionProfile profile,
                          @Nullable String commandName,
                          @Nullable Runnable postRunnable,
                          boolean modal,
                          @NotNull Predicate<? super ProblemDescriptor> shouldApplyFix) {
    String title = LangBundle.message("progress.title.inspect.code");
    if (LOG.isDebugEnabled()) {
      LOG.debug("Starting code cleanup");
    }

    Task task = modal ? new Task.Modal(getProject(), title, true) {
      private CleanupProblems problems;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        problems = findProblems(scope, profile, indicator, shouldApplyFix);
      }

      @Override
      public void onSuccess() {
        applyFixes(scope, problems, commandName, postRunnable, profile, true, shouldApplyFix);
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: an exception during findProblems");
        }
        super.onThrowable(error);
      }

      @Override
      public void onCancel() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: cancelled");
        }
      }

      @Override
      public void onFinished() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: finished searching for problems");
        }
      }
    } : new Task.Backgroundable(getProject(), title, true) {
      private CleanupProblems problems;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        problems = findProblems(scope, profile, indicator, shouldApplyFix);
      }

      @Override
      public void onSuccess() {
        applyFixes(scope, problems, commandName, postRunnable, profile, false, shouldApplyFix);
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: an exception during findProblems");
        }
        super.onThrowable(error);
      }

      @Override
      public void onCancel() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: cancelled");
        }
      }

      @Override
      public void onFinished() {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Code cleanup: finished searching for problems");
        }
      }
    };
    ProgressManager.getInstance().run(task);
  }

  public @NotNull CleanupProblems findProblems(@NotNull AnalysisScope scope,
                                               @NotNull InspectionProfile profile,
                                               @NotNull ProgressIndicator progressIndicator,
                                               @NotNull Predicate<? super ProblemDescriptor> shouldApplyFix) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Code cleanup: searching for problems in code");
    }

    setCurrentScope(scope);
    int fileCount = scope.getFileCount();
    progressIndicator.setIndeterminate(false);
    SearchScope searchScope = ReadAction.compute(scope::toSearchScope);
    TextRange range;
    if (searchScope instanceof LocalSearchScope) {
      PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      range = elements.length == 1 ? ReadAction.compute(elements[0]::getTextRange) : null;
    }
    else {
      range = null;
    }
    Iterable<Tools> inspectionTools = ContainerUtil.filter(profile.getAllEnabledInspectionTools(getProject()), tools -> {
      assert tools != null;
      return tools.getTool().isCleanupTool();
    });
    boolean includeDoNotShow = includeDoNotShow(profile);
    List<ProblemDescriptor> descriptors = new ArrayList<>();
    Set<PsiFile> files = new HashSet<>();
    ((RefManagerImpl)getRefManager()).runInsideInspectionReadAction(() -> {
      scope.accept(new PsiElementVisitor() {
        private int myCount;
        @Override
        public void visitFile(@NotNull PsiFile file) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Code cleanup: searching for problems in file " + file);
          }

          progressIndicator.setText(AbstractLayoutCodeProcessor.getPresentablePath(getProject(), file));
          progressIndicator.setFraction((double)++myCount / fileCount);

          if (isBinary(file)) return;
          List<LocalInspectionToolWrapper> lTools = new ArrayList<>();
          for (Tools tools : inspectionTools) {
            InspectionToolWrapper<?, ?> tool = tools.getEnabledTool(file, includeDoNotShow);
            if (tool instanceof GlobalInspectionToolWrapper) {
              tool = ((GlobalInspectionToolWrapper)tool).getSharedLocalInspectionToolWrapper();
            }
            if (tool != null) {
              lTools.add((LocalInspectionToolWrapper)tool);
              tool.initialize(GlobalInspectionContextImpl.this);
            }
          }

          if (!lTools.isEmpty()) {
            InspectionProfileWrapper.runWithCustomInspectionWrapper(file, p -> new InspectionProfileWrapper(profile,
                                                                                                            ((InspectionProfileImpl)p).getProfileManager()), () -> {
              try {
                TextRange restrictRange = range == null ? file.getTextRange() : range;
                ApplicationManager.getApplication().runReadAction(() -> {
                   Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
                     InspectionEngine.inspectEx(lTools, file, restrictRange, restrictRange, false, true, true,
                                                myProgressIndicator,
                                                (wrapper, descriptor) -> true);
                  for (Map.Entry<LocalInspectionToolWrapper, List<ProblemDescriptor>> entry : map.entrySet()) {
                    LocalInspectionToolWrapper toolWrapper = entry.getKey();
                    List<ProblemDescriptor> descriptors = entry.getValue();
                    InspectionToolPresentation toolPresentation = getPresentation(toolWrapper);
                    BatchModeDescriptorsUtil.addProblemDescriptors(descriptors, toolPresentation, true, GlobalInspectionContextImpl.this, toolWrapper.getTool());
                  }
                });

                Set<ProblemDescriptor> localDescriptors = new TreeSet<>(CommonProblemDescriptor.DESCRIPTOR_COMPARATOR);
                for (LocalInspectionToolWrapper tool : lTools) {
                  InspectionToolResultExporter toolPresentation = getPresentation(tool);
                  for (CommonProblemDescriptor descriptor : toolPresentation.getProblemDescriptors()) {
                    if (descriptor instanceof ProblemDescriptor) {
                      localDescriptors.add((ProblemDescriptor)descriptor);
                    }
                  }
                }

                if (searchScope instanceof LocalSearchScope) {
                  for (Iterator<ProblemDescriptor> iterator = localDescriptors.iterator(); iterator.hasNext(); ) {
                    ProblemDescriptor descriptor = iterator.next();
                    TextRange infoRange = descriptor instanceof ProblemDescriptorBase ? ((ProblemDescriptorBase)descriptor).getTextRange() : null;
                    if (infoRange != null && !((LocalSearchScope)searchScope).containsRange(file, infoRange)) {
                      iterator.remove();
                    }
                  }
                }
                if (!localDescriptors.isEmpty()) {
                  for (ProblemDescriptor descriptor : localDescriptors) {
                    if (shouldApplyFix.test(descriptor)) {
                      descriptors.add(descriptor);
                    }
                  }
                  files.add(file);
                }
              }
              finally {
                myPresentationMap.clear();
              }
            });
          }
        }
      });
    });

    return new CleanupProblems(files, descriptors, searchScope instanceof GlobalSearchScope);
  }

  private void applyFixes(@NotNull AnalysisScope scope,
                          @NotNull CleanupProblems problems,
                          @Nullable String commandName,
                          @Nullable Runnable postRunnable,
                          @NotNull InspectionProfile profile,
                          boolean modal,
                          @NotNull Predicate<? super ProblemDescriptor> shouldApplyFix) {
    if (problems.getFiles().isEmpty()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("No problems found during code inspection, nothing to cleanup");
      }

      if (commandName != null) {
        var notification = NOTIFICATION_GROUP.createNotification(InspectionsBundle.message("inspection.no.problems.message",
                                                                                           scope.getFileCount(),
                                                                                           scope.getDisplayName()), MessageType.INFO);
        if (!scope.isIncludeTestSource()) addRepeatWithTestsAction(scope, notification, () -> codeCleanup(scope, profile, commandName, postRunnable, modal, shouldApplyFix));
        notification.notify(getProject());
      }
      if (postRunnable != null) {
        postRunnable.run();
      }
      return;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Applying fixes");
    }

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(problems.getFiles())) return;
    CleanupInspectionUtil.getInstance().applyFixesNoSort(
      getProject(), LangBundle.message("code.cleanup"), problems.getProblemDescriptors(), null, false, problems.isGlobalScope());
    if (postRunnable != null) {
      postRunnable.run();
    }
  }

  private static void addRepeatWithTestsAction(@NotNull AnalysisScope scope, @NotNull Notification notification, @NotNull Runnable analysisRepeater) {
    notification.addAction(new NotificationAction(InspectionsBundle.message("inspection.no.problems.repeat.with.tests")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        scope.setIncludeTestSource(true);
        scope.invalidate();
        analysisRepeater.run();
      }
    });
  }

  private static boolean isBinary(@NotNull PsiFile file) {
    return file instanceof PsiBinaryFile || file.getFileType().isBinary();
  }

  public boolean isViewClosed() {
    return myViewClosed;
  }

  private void addProblemsToView(List<? extends Tools> tools) {
    //noinspection TestOnlyProblems
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && !TESTING_VIEW) {
      return;
    }
    if (myView == null && !InspectionResultsView.hasProblems(tools, this, new InspectionRVContentProviderImpl())) {
      return;
    }
    initializeViewIfNeeded().doWhenDone(() -> myView.addTools(tools));
  }

  @Override
  public @NotNull InspectionToolPresentation getPresentation(@NotNull InspectionToolWrapper<?, ?> toolWrapper) {
    return myPresentationMap.computeIfAbsent(toolWrapper, __ -> {
      String presentationClass = toolWrapper.myEP == null ? null : toolWrapper.myEP.presentation;
      if (StringUtil.isEmpty(presentationClass)) {
        if (myProblemConsumer !=  null) {
          return new DelegatedInspectionToolPresentation(toolWrapper, this, myProblemConsumer);
        }
        presentationClass = DefaultInspectionToolPresentation.class.getName();
      }
      try {
        InspectionEP extension = toolWrapper.getExtension();
        ClassLoader extensionClassLoader = extension != null ? extension.getPluginDescriptor().getPluginClassLoader() : null;
        Constructor<?> constructor = Class.forName(presentationClass,
                                                   true,
                                                   extensionClassLoader != null ? extensionClassLoader : getClass().getClassLoader())
          .getConstructor(InspectionToolWrapper.class, GlobalInspectionContextImpl.class);
        constructor.setAccessible(true);
        return (InspectionToolPresentation)constructor.newInstance(toolWrapper, this);
      }
      catch (Exception e) {
        LOG.error(e);
        throw new RuntimeException(e);
      }
    });
  }
}
