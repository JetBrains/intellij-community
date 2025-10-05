// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.conversion.ConversionListener;
import com.intellij.conversion.ConversionService;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.diff.tools.util.text.LineOffsetsUtil;
import com.intellij.diff.util.Range;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommandLineInspectionProgressReporter;
import com.intellij.ide.CommandLineInspectionProjectConfigurator;
import com.intellij.ide.impl.PatchProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressRunner;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.ex.RangesBuilder;
import com.intellij.openapi.vfs.*;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import kotlinx.coroutines.future.FutureKt;
import one.util.streamex.StreamEx;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;

import static com.intellij.configurationStore.StoreUtilKt.forPoorJavaClientOnlySaveProjectIndEdtDoNotUseThisMethod;

public class InspectionApplicationBase implements CommandLineInspectionProgressReporter {
  @ApiStatus.Internal
  public static final Logger LOG = Logger.getInstance(InspectionApplicationBase.class);

  public static final String PROJECT_STRUCTURE_DIR = "projectStructure";

  @ApiStatus.Internal
  public InspectionToolCmdlineOptionHelpProvider myHelpProvider;
  @ApiStatus.Internal
  public String myProjectPath;
  @ApiStatus.Internal
  public String myOutPath;
  @ApiStatus.Internal
  public String mySourceDirectory;
  @ApiStatus.Internal
  public String myStubProfile;
  @ApiStatus.Internal
  public String myProfileName;
  @ApiStatus.Internal
  public String myProfilePath;
  @ApiStatus.Internal
  public boolean myRunWithEditorSettings;
  boolean myRunGlobalToolsOnly;
  @ApiStatus.Internal
  public boolean myAnalyzeChanges;
  private boolean myPathProfiling;
  private int myVerboseLevel;
  private final Map<String, List<Range>> diffMap = new ConcurrentHashMap<>();
  private final MultiMap<Pair<String, Integer>, String> originalWarnings = MultiMap.createConcurrent();
  private final AsyncPromise<Void> isMappingLoaded = new AsyncPromise<>();
  @ApiStatus.Internal
  public String myOutputFormat;
  @ApiStatus.Internal
  public InspectionProfileImpl myInspectionProfile;

  String myTargets;
  @ApiStatus.Internal
  public boolean myErrorCodeRequired = true;
  String myScopePattern;

  public void startup() {
    if (myProjectPath == null) {
      reportError("Project to inspect is not defined");
      printHelpAndExit();
    }

    if (isProfileConfigInvalid()) {
      reportError("Profile to inspect with is not defined");
      printHelpAndExit();
    }

    ApplicationManagerEx.getApplicationEx().setSaveAllowed(false);
    try {
      header();
      execute();
    }
    catch (ProcessCanceledException e) {
      reportError(e);
      gracefulExit();
      return;
    }
    catch (Throwable e) {
      LOG.error(e);
      reportError(e);
      gracefulExit();
      return;
    }

    if (myErrorCodeRequired) {
      ApplicationManagerEx.getApplicationEx().exit(true, true);
    }
  }

  @SuppressWarnings("unused")
  public void enablePathProfiling() {
    myPathProfiling = true;
  }

  public void header() { }

  protected boolean isProfileConfigInvalid() {
    return myProfileName == null && myProfilePath == null && myStubProfile == null;
  }

  public void execute() throws Exception {
    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    reportMessageNoLineBreak(1, InspectionsBundle.message("inspection.application.starting.up",
                                                          appInfo.getFullApplicationName() +
                                                          " (build " +
                                                          appInfo.getBuild().asString() +
                                                          ")"));
    reportMessage(1, InspectionsBundle.message("inspection.done"));

    Disposable disposable = Disposer.newDisposable();
    try {
      run(Paths.get(FileUtil.toCanonicalPath(myProjectPath)), disposable);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  private void printHelpAndExit() {
    myHelpProvider.printHelpAndExit();
  }

  private @NotNull CommandLineInspectionProjectConfigurator.ConfiguratorContext configuratorContext(@NotNull Path projectPath,
                                                                                                    @Nullable AnalysisScope scope) {
    return new CommandLineInspectionProjectConfigurator.ConfiguratorContext() {
      @Override
      public @NotNull ProgressIndicator getProgressIndicator() {
        return new ProgressIndicatorBase();
      }

      @Override
      public @NotNull CommandLineInspectionProgressReporter getLogger() {
        return InspectionApplicationBase.this;
      }

      @Override
      public @NotNull Path getProjectPath() {
        return projectPath;
      }

      @Override
      public @NotNull Predicate<Path> getFilesFilter() {
        return __ -> true;
      }

      @Override
      public @NotNull Predicate<VirtualFile> getVirtualFilesFilter() {
        return __ -> true;
      }
    };
  }

  protected void run(@NotNull Path projectPath, @NotNull Disposable parentDisposable)
    throws IOException, InterruptedException, ExecutionException {
    Project project = openProject(projectPath, parentDisposable);
    if (project == null) return;
    reportMessageNoLineBreak(1, InspectionsBundle.message("inspection.application.initializing.project"));

    if (myInspectionProfile == null) {
      myInspectionProfile = loadInspectionProfile(project);
    }

    AnalysisScope scope = getAnalysisScope(project);
    if (scope == null) return;
    LOG.info("Used scope: " + scope);
    runAnalysisOnScope(projectPath, parentDisposable, project, myInspectionProfile, scope);
  }

  private @Nullable Project openProject(@NotNull Path projectPath, @NotNull Disposable parentDisposable)
    throws InterruptedException, ExecutionException {
    VirtualFile vfsProject = LocalFileSystem.getInstance().refreshAndFindFileByPath(
      FileUtil.toSystemIndependentName(projectPath.toString()));
    if (vfsProject == null) {
      reportError(InspectionsBundle.message("inspection.application.file.cannot.be.found", projectPath));
      printHelpAndExit();
    }

    reportMessageNoLineBreak(1, InspectionsBundle.message("inspection.application.opening.project"));
    ConversionService conversionService = ConversionService.getInstance();
    StringBuilder convertErrorBuffer = new StringBuilder();
    if (conversionService != null &&
        conversionService.blockingConvertSilently(projectPath, createConversionListener(convertErrorBuffer)).openingIsCanceled()) {
      onFailure(convertErrorBuffer.toString());
      return null;
    }

    for (CommandLineInspectionProjectConfigurator configurator : CommandLineInspectionProjectConfigurator.EP_NAME.getExtensionList()) {
      CommandLineInspectionProjectConfigurator.ConfiguratorContext context = configuratorContext(projectPath, null);
      if (configurator.isApplicable(context)) {
        configurator.configureEnvironment(context);
      }
    }

    if (Boolean.getBoolean("log.project.structure.changes")) {
      InspectionsReportConverter reportConverter = ReportConverterUtil.getReportConverter(myOutputFormat);
      if (reportConverter != null) {
        addRootChangesListener(parentDisposable, reportConverter);
      }
    }

    AtomicReference<Project> projectRef = new AtomicReference<>();
    ProgressManager.getInstance().runProcess(
      () -> projectRef.set(ProjectUtil.openOrImport(projectPath)),
      createProgressIndicator()
    );
    Project project = projectRef.get();
    if (project == null) {
      onFailure(InspectionsBundle.message("inspection.application.unable.open.project"));
      return null;
    }
    waitAllStartupActivitiesPassed(project);

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, () -> isMappingLoaded.setResult(null));
    Disposer.register(parentDisposable, () -> closeProject(project));

    ApplicationManager.getApplication().invokeAndWait(() -> VirtualFileManager.getInstance().refreshWithoutFileWatcher(false));

    reportMessage(1, InspectionsBundle.message("inspection.done"));
    return project;
  }

  private @Nullable AnalysisScope getAnalysisScope(@NotNull Project project) throws ExecutionException, InterruptedException {
    SearchScope scope = getSearchScope(project);
    if (scope == null) return null;
    return new AnalysisScope(scope, project);
  }

  private SearchScope getSearchScope(@NotNull Project project) throws ExecutionException, InterruptedException {

    if (myAnalyzeChanges) {
      return getSearchScopeFromChangedFiles(project);
    }

    if (myScopePattern != null) {
      try {
        PackageSet packageSet = PackageSetFactory.getInstance().compile(myScopePattern);
        NamedScope namedScope = new NamedScope("commandLineScope", AllIcons.Ide.LocalScope, packageSet);
        return GlobalSearchScopesCore.filterScope(project, namedScope);
      }
      catch (ParsingException e) {
        LOG.error("Error of scope parsing", e);
        gracefulExit();
        throw new IllegalStateException("unreachable");
      }
    }

    if (mySourceDirectory != null) {
      if (!new File(mySourceDirectory).isAbsolute()) {
        mySourceDirectory = new File(myProjectPath, mySourceDirectory).getPath();
      }
      mySourceDirectory = mySourceDirectory.replace(File.separatorChar, '/');

      VirtualFile vfsDir = LocalFileSystem.getInstance().findFileByPath(mySourceDirectory);
      if (vfsDir == null) {
        reportError(InspectionsBundle.message("inspection.application.directory.cannot.be.found", mySourceDirectory));
        printHelpAndExit();
      }
      return GlobalSearchScopesCore.directoriesScope(project, true, Objects.requireNonNull(vfsDir));
    }

    String scopeName = System.getProperty("idea.analyze.scope");
    NamedScope namedScope = scopeName != null ? NamedScopesHolder.getScope(project, scopeName) : null;
    return namedScope != null ? GlobalSearchScopesCore.filterScope(project, namedScope) : GlobalSearchScope.projectScope(project);
  }

  public @NotNull SearchScope getSearchScopeFromChangedFiles(@NotNull Project project) throws ExecutionException, InterruptedException {
    List<VirtualFile> files = getChangedFiles(project);
    for (VirtualFile file : files) {
      reportMessage(0, "modified file: " + file.getPath());
    }
    return GlobalSearchScope.filesWithoutLibrariesScope(project, files);
  }

  private static void addRootChangesListener(Disposable parentDisposable, InspectionsReportConverter reportConverter) {
    MessageBusConnection applicationBus = ApplicationManager.getApplication().getMessageBus().connect(parentDisposable);
    applicationBus.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        subscribeToRootChanges(project, reportConverter);
      }
    });
  }

  private static void subscribeToRootChanges(Project project, InspectionsReportConverter reportConverter) {
    Path rootLogDir = Paths.get(PathManager.getLogPath()).resolve("projectStructureChanges");
    //noinspection ResultOfMethodCallIgnored
    rootLogDir.toFile().mkdirs();
    AtomicInteger counter = new AtomicInteger(0);
    reportConverter.projectData(project, rootLogDir.resolve("state0"));

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        updateProjectStructure(counter, reportConverter, project, rootLogDir);
      }
    });
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC,
                         (__, __1, __2, __3) -> updateProjectStructure(counter, reportConverter, project, rootLogDir));
  }

  private static void updateProjectStructure(AtomicInteger counter,
                                             InspectionsReportConverter reportConverter,
                                             Project project,
                                             Path rootLogDir) {
    int i = counter.incrementAndGet();
    reportConverter.projectData(project, rootLogDir.resolve("state" + i));
    LOG.info("Project structure update written. Change number " + i);
  }

  public static List<VirtualFile> getChangedFiles(@NotNull Project project) throws ExecutionException, InterruptedException {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    CompletableFuture<List<VirtualFile>> future = new CompletableFuture<>();
    changeListManager.invokeAfterUpdateWithModal(false, null, () -> {
      try {
        List<VirtualFile> files = changeListManager.getAffectedFiles();
        future.complete(files);
      }
      catch (Throwable e) {
        future.completeExceptionally(e);
      }
    });

    return future.get();
  }

  private static void waitAllStartupActivitiesPassed(@NotNull Project project) throws InterruptedException, ExecutionException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    LOG.info("Waiting for startup activities");
    int timeout = Registry.intValue("batch.inspections.startup.activities.timeout", 180);
    try {
      FutureKt.asCompletableFuture(StartupManager.getInstance(project).getAllActivitiesPassedFuture()).get(timeout, TimeUnit.MINUTES);
      waitForInvokeLaterActivities();
      LOG.info("Startup activities finished");
    }
    catch (TimeoutException e) {
      String threads = ThreadDumper.dumpThreadsToString();
      throw new RuntimeException(String.format("Cannot process startup activities in %s minutes. ", timeout) +
                                 "You can try to increase batch.inspections.startup.activities.timeout registry value. " +
                                 "Thread dumps\n: " + threads, e);
    }
  }

  private @NotNull GlobalInspectionContextEx createGlobalInspectionContext(Project project) {
    InspectionManagerBase im = (InspectionManagerBase)InspectionManager.getInstance(project);
    GlobalInspectionContextEx context = (GlobalInspectionContextEx)im.createNewGlobalContext();
    context.setExternalProfile(myInspectionProfile);
    if (myPathProfiling) {
      context.startPathProfiling();
    }
    im.setProfile(myInspectionProfile.getName());
    return context;
  }

  private void runAnalysisOnScope(Path projectPath,
                                  @NotNull Disposable parentDisposable,
                                  Project project,
                                  InspectionProfileImpl inspectionProfile, AnalysisScope scope)
    throws IOException {
    reportMessage(1, InspectionsBundle.message("inspection.done"));

    if (!myRunWithEditorSettings) {
      reportMessage(1, InspectionsBundle.message("inspection.application.chosen.profile.log.message", inspectionProfile.getName()));
    }

    InspectionsReportConverter reportConverter = ReportConverterUtil.getReportConverter(myOutputFormat);
    if (reportConverter == null && myOutputFormat != null && myOutputFormat.endsWith(".xsl")) {
      // xslt converter
      reportConverter = new XSLTReportConverter(myOutputFormat);
    }

    Path resultsDataPath;
    try {
      resultsDataPath = ReportConverterUtil.getResultsDataPath(parentDisposable, reportConverter, myOutPath);
    }
    catch (IOException e) {
      LOG.error(e);
      System.err.println("Cannot create tmp directory.");
      System.exit(1);
      return;
    }

    runAnalysis(project, projectPath, inspectionProfile, scope, reportConverter, resultsDataPath);
  }

  private void configureProject(@NotNull Path projectPath, @NotNull Project project, @NotNull AnalysisScope scope) {

    List<CommandLineInspectionProjectConfigurator> configurators = CommandLineInspectionProjectConfigurator.EP_NAME.getExtensionList();
    List<CommandLineInspectionProjectConfigurator> enabledConfigurators =
      isActivityBasedConfigurationEnabled()
      ? ContainerUtil.filter(configurators, configurator -> configurator.shouldBeInvokedAlongsideActivityTracking())
      : configurators;

    for (CommandLineInspectionProjectConfigurator configurator : enabledConfigurators) {
      CommandLineInspectionProjectConfigurator.ConfiguratorContext context = configuratorContext(projectPath, scope);
      configurator.preConfigureProject(project, context);
    }

    for (CommandLineInspectionProjectConfigurator configurator : enabledConfigurators) {
      CommandLineInspectionProjectConfigurator.ConfiguratorContext context = configuratorContext(projectPath, scope);
      if (configurator.isApplicable(context)) {
        configurator.configureProject(project, context);
      }
    }


    ApplicationManager.getApplication().invokeAndWait(() -> PatchProjectUtil.patchProject(project));

    if (isActivityBasedConfigurationEnabled()) {
      ActivityUtilsKt.configureProjectWithActivities(project, configuratorContext(projectPath, scope));
    }

    waitForInvokeLaterActivities();
  }

  private static void waitForInvokeLaterActivities() {
    for (int i = 0; i < 3; i++) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
      }, ModalityState.any());
    }
  }

  private static boolean isActivityBasedConfigurationEnabled() {
    return Registry.is("ide.inspect.activity.based.inspections.enabled", false);
  }

  private void runAnalysis(Project project,
                           Path projectPath,
                           InspectionProfileImpl inspectionProfile,
                           AnalysisScope scope,
                           InspectionsReportConverter reportConverter,
                           Path resultsDataPath) throws IOException {
    GlobalInspectionContextEx context = createGlobalInspectionContext(project);
    if (myAnalyzeChanges) {
      AnalysisScope baseScope = scope;
      GlobalInspectionContextEx baseContext = createGlobalInspectionContext(project);

      scope = runAnalysisOnCodeWithoutChanges(
        project,
        baseContext,
        () -> runUnderProgress(project, projectPath, baseContext, baseScope, resultsDataPath, new ArrayList<>())
      );
      setupSecondAnalysisHandler(project, context);
    }

    List<Path> inspectionsResults = new ArrayList<>();
    runUnderProgress(project, projectPath, context, scope, resultsDataPath, inspectionsResults);
    Path descriptionsFile = resultsDataPath.resolve(InspectionsResultUtil.DESCRIPTIONS + InspectionsResultUtil.XML_EXTENSION);
    try {
      InspectionsResultUtil.describeInspections(descriptionsFile,
                                                myRunWithEditorSettings ? null : inspectionProfile.getName(),
                                                inspectionProfile);
    }
    catch (XMLStreamException e) {
      throw new IOException(e);
    }
    inspectionsResults.add(descriptionsFile);
    // convert report
    if (reportConverter != null) {
      try {
        List<File> results = ContainerUtil.map(inspectionsResults, Path::toFile);
        reportConverter.convert(resultsDataPath.toString(), myOutPath, context.getTools(),
                                results);
        InspectResultsConsumerEP.runConsumers(context.getTools(), results, project);
        if (myOutPath != null) {
          reportConverter.projectData(project, Paths.get(myOutPath).resolve(PROJECT_STRUCTURE_DIR));
        }
      }
      catch (InspectionsReportConverter.ConversionException e) {
        reportError("\n" + e.getMessage());
        printHelpAndExit();
      }
    }
  }

  public @NotNull AnalysisScope runAnalysisOnCodeWithoutChanges(Project project,
                                                                GlobalInspectionContextEx context,
                                                                Runnable analysisRunner) {
    VirtualFile[] changes = ChangesUtil.getFilesFromChanges(ChangeListManager.getInstance(project).getAllChanges());
    setupFirstAnalysisHandler(context);
    DumbService dumbService = DumbService.getInstance(project);
    while (dumbService.isDumb()) {
      LockSupport.parkNanos(50_000_000);
    }
    if (ProjectLevelVcsManager.getInstance(project).getAllVcsRoots().length == 0) {
      try {
        isMappingLoaded.blockingGet(60000);
      }
      catch (TimeoutException | ExecutionException e) {
        onFailure(InspectionsBundle.message("inspection.application.cannot.initialize.vcs.mapping"));
      }
    }
    runAnalysisAfterShelvingSync(
      project,
      ChangeListManager.getInstance(project).getAffectedFiles(),
      createProgressIndicator(),
      () -> {
        syncProject(project, changes);

        analysisRunner.run();
      }
    );
    syncProject(project, changes);
    // new added files becomes invalid after unshelving, so we need to update our scope
    List<VirtualFile> files = ChangeListManager.getInstance(project).getAffectedFiles();
    if (myVerboseLevel == 3) {
      for (VirtualFile file : files) {
        reportMessage(1, "modified after unshelving: " + file.getPath());
      }
    }
    return new AnalysisScope(project, files);
  }

  private static void syncProject(Project project, VirtualFile[] changes) {
    VfsUtil.markDirtyAndRefresh(false, false, false, changes);
    WriteAction.runAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
  }

  private void setupFirstAnalysisHandler(GlobalInspectionContextEx context) {
    reportMessage(1, "Running first analysis stage...");
    context.setGlobalReportedProblemFilter(
      (entity, description) -> {
        if (!(entity instanceof RefElement)) return false;
        Pair<VirtualFile, Integer> fileAndLine = findFileAndLineByRefElement((RefElement)entity);
        if (fileAndLine == null) return false;
        originalWarnings.putValue(Pair.create(fileAndLine.first.getPath(), fileAndLine.second), description);
        return false;
      }
    );
    context.setReportedProblemFilter(
      (element, descriptors) -> {
        List<ProblemDescriptorBase> problemDescriptors = ContainerUtil.filterIsInstance(descriptors, ProblemDescriptorBase.class);
        if (!problemDescriptors.isEmpty()) {
          ProblemDescriptorBase problemDescriptor = problemDescriptors.get(0);
          VirtualFile file = problemDescriptor.getContainingFile();
          if (file == null) return false;
          int lineNumber = problemDescriptor.getLineNumber();
          for (ProblemDescriptorBase it : problemDescriptors) {
            originalWarnings.putValue(Pair.create(file.getPath(), lineNumber), it.toString());
          }
        }
        return false;
      }
    );
  }

  public void setupSecondAnalysisHandler(Project project, GlobalInspectionContextEx context) {
    reportMessage(1, "Running second analysis stage...");
    printBeforeSecondStageProblems();
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    context.setGlobalReportedProblemFilter(
      (entity, description) -> {
        if (!(entity instanceof RefElement)) return false;
        Pair<VirtualFile, Integer> fileAndLine = findFileAndLineByRefElement((RefElement)entity);
        if (fileAndLine == null) return false;
        return secondAnalysisFilter(changeListManager, description, fileAndLine.first, fileAndLine.second);
      }
    );
    context.setReportedProblemFilter(
      (element, descriptors) -> {
        List<ProblemDescriptorBase> any = ContainerUtil.filterIsInstance(descriptors, ProblemDescriptorBase.class);
        if (!any.isEmpty()) {
          ProblemDescriptorBase problemDescriptor = any.get(0);
          String text = problemDescriptor.toString();
          VirtualFile file = problemDescriptor.getContainingFile();
          if (file == null) return true;
          int line = problemDescriptor.getLineNumber();
          return secondAnalysisFilter(changeListManager, text, file, line);
        }
        return true;
      }
    );
  }

  private static @Nullable Pair<VirtualFile, Integer> findFileAndLineByRefElement(RefElement refElement) {
    PsiElement element = refElement.getPsiElement();
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return null;
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    int line = ReadAction.compute(() -> {
      Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
      return (document == null) ? -1 : document.getLineNumber(element.getTextRange().getStartOffset());
    });
    return Pair.create(virtualFile, line);
  }

  private boolean secondAnalysisFilter(ChangeListManager changeListManager, String text, VirtualFile file, int line) {
    List<Range> ranges = getOrComputeUnchangedRanges(file, changeListManager);
    Range first = ContainerUtil.find(ranges, it -> it.start1 <= line && line < it.end1);
    if (first == null) {
      logNotFiltered(text, file, line, -1);
      return true;
    }
    int position = first.start2 + line - first.start1;
    Collection<String> problems = originalWarnings.get(Pair.create(file.getPath(), position));
    if (problems.stream().anyMatch(it -> Objects.equals(it, text))) {
      return false;
    }
    logNotFiltered(text, file, line, position);
    return true;
  }

  private void logNotFiltered(String text, VirtualFile file, int line, int position) {
    // unused asks shouldReport not only for warnings.
    if (text.contains("unused")) return;
    reportMessage(3, "Not filtered:");
    reportMessage(3, file.getPath() + ":" + (line + 1) + " Original: " + (position + 1));
    reportMessage(3, "\t\t" + text);
  }

  private void printBeforeSecondStageProblems() {
    if (myVerboseLevel == 3) {
      reportMessage(3, "Old warnings:");
      ArrayList<Map.Entry<Pair<String, Integer>, Collection<String>>> entries = new ArrayList<>(originalWarnings.entrySet());
      reportMessage(3, "total size: " + entries.size());
      entries.sort(Comparator.comparing((Map.Entry<Pair<String, Integer>, Collection<String>> o) -> o.getKey().first)
                     .thenComparingInt(o -> o.getKey().second));
      for (Map.Entry<Pair<String, Integer>, Collection<String>> entry : entries) {
        reportMessage(3, entry.getKey().first + ":" + (entry.getKey().second + 1));
        for (String value : entry.getValue()) {
          reportMessage(3, "\t\t" + value);
        }
      }
    }
  }

  private void runUnderProgress(@NotNull Project project,
                                @NotNull Path projectPath,
                                @NotNull GlobalInspectionContextEx context,
                                @NotNull AnalysisScope scope,
                                @NotNull Path resultsDataPath,
                                @NotNull List<? super Path> inspectionsResults) {
    Task.Backgroundable task = new Task.Backgroundable(project, "") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        configureProject(projectPath, project, scope);

        if (!GlobalInspectionContextUtil.canRunInspections(project, false, () -> {
        })) {
          onFailure(InspectionsBundle.message("inspection.application.cannot.configure.project.to.run.inspections"));
        }
        context.launchInspectionsOffline(scope, resultsDataPath, myRunGlobalToolsOnly, inspectionsResults);
        reportMessage(1, "\n" + InspectionsBundle.message("inspection.capitalized.done") + "\n");
        if (!myErrorCodeRequired) {
          closeProject(project);
        }
      }
    };
    new ProgressRunner<>(task)
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .withProgress(createProgressIndicator())
      .sync()
      .submitAndGet();
  }

  private @NotNull ProgressIndicatorBase createProgressIndicator() {
    return new InspectionProgressIndicator();
  }

  private static void runAnalysisAfterShelvingSync(Project project, List<? extends VirtualFile> files,
                                                   ProgressIndicator progressIndicator, Runnable afterShelve) {
    Set<VirtualFile> versionedRoots =
      StreamEx.of(files).map(it -> ProjectLevelVcsManager.getInstance(project).getVcsRootFor(it)).nonNull().toSet();
    String message = VcsBundle.message("searching.for.code.smells.freezing.process");
    VcsPreservingExecutor.executeOperation(project, versionedRoots, message, progressIndicator, afterShelve);
  }

  public void gracefulExit() {
    if (myErrorCodeRequired) {
      System.exit(1);
    }
    else {
      throw new RuntimeException("Failed to proceed");
    }
  }

  private static void closeProject(@NotNull Project project) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (!project.isDisposed()) {
        if (Boolean.getBoolean("inspect.save.project.settings")) {
          forPoorJavaClientOnlySaveProjectIndEdtDoNotUseThisMethod(project, true);
        }
        ProjectManagerEx.getInstanceEx().forceCloseProject(project);
      }
    });
  }

  private @NotNull InspectionProfileImpl loadInspectionProfile(@NotNull Project project) {
    var profileLoader = getInspectionProfileLoader(project);
    InspectionProfileImpl profile = profileLoader.tryLoadProfileByNameOrPath(myProfileName, myProfilePath, "command line",
                                                                             (msg) -> onFailure(msg));
    if (profile != null) return profile;

    if (myStubProfile != null) {
      if (!myRunWithEditorSettings) {
        profile = profileLoader.loadProfileByName(myStubProfile);
        if (profile != null) return profile;

        profile = profileLoader.loadProfileByPath(myStubProfile);
        if (profile != null) return profile;
      }
    }

    profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    reportError("Using default project profile");

    return profile;
  }

  public InspectionProfileManager getProfileManager(@NotNull Project project) {
    return InspectionProjectProfileManager.getInstance(project);
  }

  private @NotNull InspectionProfileLoader<? extends InspectionProfileImpl> getInspectionProfileLoader(@NotNull Project project) {
    return new InspectionProfileLoaderBase<>(project) {
      @Override
      public @Nullable InspectionProfileImpl loadProfileByName(@NotNull String profileName) {
        InspectionProfileManager.getInstance().getProfiles(); //  force init provided profiles
        InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
        InspectionProfileImpl inspectionProfile = profileManager.getProfile(profileName, false);
        if (inspectionProfile == null) {  // check if the IDE profile is used for the project
          for (InspectionProfileImpl profile : profileManager.getProfiles()) {
            if (Comparing.strEqual(profile.getName(), profileName)) {
              inspectionProfile = profile;
              break;
            }
          }
        }

        return inspectionProfile;
      }

      @Override
      public @Nullable InspectionProfileImpl loadProfileByPath(@NotNull String profilePath) {
        InspectionProfileImpl inspectionProfileFromYaml = tryLoadProfileFromYaml(profilePath,
                                                                                 InspectionToolRegistrar.getInstance(),
                                                                                 (BaseInspectionProfileManager)InspectionProjectProfileManager.getInstance(
                                                                                   project));
        if (inspectionProfileFromYaml != null) return inspectionProfileFromYaml;

        try {
          InspectionProfileImpl inspectionProfile = ApplicationInspectionProfileManagerBase.getInstanceBase().loadProfile(profilePath);
          if (inspectionProfile != null) {
            reportMessage(1, "Loaded the '" + inspectionProfile.getName() + "' profile from the file '" + profilePath + "'");
          }
          return inspectionProfile;
        }
        catch (IOException e) {
          throw new InspectionApplicationException("Failed to read inspection profile file '" + profilePath + "': " + e);
        }
        catch (JDOMException e) {
          throw new InspectionApplicationException("Invalid xml structure of inspection profile file '" + profilePath + "': " + e);
        }
      }
    };
  }

  private ConversionListener createConversionListener(StringBuilder errorBuffer) {
    return new ConversionListener() {
      @Override
      public void conversionNeeded() {
        reportMessage(1, InspectionsBundle.message("inspection.application.project.has.older.format.and.will.be.converted"));
      }

      @Override
      public void successfullyConverted(@NotNull Path backupDir) {
        reportMessage(1, InspectionsBundle.message(
          "inspection.application.project.was.successfully.converted.old.project.files.were.saved.to.0",
          backupDir.toString()));
      }

      @Override
      public void error(@NotNull String message) {
        errorBuffer.append(InspectionsBundle.message("inspection.application.cannot.convert.project.0", message))
          .append(System.lineSeparator());
      }

      @Override
      public void cannotWriteToFiles(@NotNull List<? extends Path> readonlyFiles) {
        StringBuilder files = new StringBuilder();
        for (Path file : readonlyFiles) {
          files.append(file.toString()).append("; ");
        }
        errorBuffer.append(InspectionsBundle
                             .message("inspection.application.cannot.convert.the.project.the.following.files.are.read.only.0",
                                      files.toString()))
          .append(System.lineSeparator());
      }
    };
  }

  public static @NotNull String getPrefix(@NotNull String text) {
    int idx = text.indexOf(" in ");
    if (idx == -1) {
      idx = text.indexOf(" of ");
    }

    return idx == -1 ? text : text.substring(0, idx);
  }

  public void setVerboseLevel(int verboseLevel) {
    myVerboseLevel = verboseLevel;
  }

  @SuppressWarnings("SameParameterValue")
  protected void reportMessageNoLineBreak(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.print(message);
    }
  }

  public void reportError(@NotNull Throwable e) {
    reportError(e.getMessage());
  }

  public void onFailure(@NotNull String message) {
    reportError(message);
    gracefulExit();
  }

  @Override
  public void reportError(String message) {
    System.err.println(message);
  }

  @Override
  public void reportMessage(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.println(message);
    }
  }

  private List<Range> getOrComputeUnchangedRanges(@NotNull VirtualFile virtualFile,
                                                  @NotNull ChangeListManager changeListManager) {
    return diffMap.computeIfAbsent(virtualFile.getPath(), key -> computeDiff(virtualFile, changeListManager));
  }

  private static List<Range> computeDiff(@NotNull VirtualFile virtualFile,
                                         @NotNull ChangeListManager changeListManager) {
    try {
      Change change = changeListManager.getChange(virtualFile);
      if (change == null) return Collections.emptyList();
      ContentRevision revision = change.getBeforeRevision();
      if (revision == null) return Collections.emptyList();
      String oldContent = revision.getContent();
      if (oldContent == null) return Collections.emptyList();
      String newContent = VfsUtilCore.loadText(virtualFile);
      return ContainerUtil.newArrayList(
        RangesBuilder.compareLines(newContent, oldContent, LineOffsetsUtil.create(newContent), LineOffsetsUtil.create(oldContent))
          .iterateUnchanged());
    }
    catch (VcsException | IOException e) {
      LOG.error("Couldn't load content", e);
      return Collections.emptyList();
    }
  }

  private class InspectionProgressIndicator extends ProgressIndicatorBase implements ProgressIndicatorWithDelayedPresentation {
    private String lastPrefix = "";
    private int myLastPercent = -1;
    private int nestingLevel;

    private InspectionProgressIndicator() {
      setText("");
    }

    @Override
    public void pushState() {
      super.pushState();
      nestingLevel++;
    }

    @Override
    public void popState() {
      super.popState();
      nestingLevel--;
    }

    @Override
    public void setText(String text) {
      if (Objects.equals(text, getText())) {
        return;
      }
      super.setText(text);
      if (text == null) return;
      switch (myVerboseLevel) {
        case 0 -> {
        }
        case 1 -> {
          String prefix = getPrefix(text);
          if (prefix.equals(lastPrefix)) {
            reportMessageNoLineBreak(1, ".");
          }
          else {
            lastPrefix = prefix;
            reportMessage(1, "");
            reportMessage(1, prefix);
          }
        }
        case 2 -> reportMessage(2, text);
        case 3 -> {
          int percent = (int)(getFraction() * 100);
          if (!isIndeterminate() && getFraction() > 0 && myLastPercent != percent && nestingLevel == 0) {
            // do not print duplicate "processing xx%"
            // do not print nested excessively verbose "Searching for this symbol.... done"
            myLastPercent = percent;
            String msg = getPrefix(text) + " " + percent + "%";
            reportMessage(2, msg);
          }
        }
      }
    }

    @Override
    public void setDelayInMillis(int delayInMillis) {

    }
  }
}
