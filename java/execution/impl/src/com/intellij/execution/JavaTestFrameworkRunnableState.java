// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings;
import com.intellij.diagnostic.logging.OutputFileUtil;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.impl.ConsoleBuffer;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.target.*;
import com.intellij.execution.testDiscovery.JavaAutoRunManager;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.autotest.AbstractAutoTestManager;
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMRunnerConsolePropertiesProvider;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;

public abstract class JavaTestFrameworkRunnableState<T extends
  ModuleBasedConfiguration<JavaRunConfigurationModule, Element>
  & CommonJavaRunConfigurationParameters
  & ConfigurationWithCommandLineShortener
  & SMRunnerConsolePropertiesProvider> extends JavaCommandLineState implements RemoteConnectionCreator {
  private static final Logger LOG = Logger.getInstance(JavaTestFrameworkRunnableState.class);

  private static final ExtensionPointName<JUnitPatcher> JUNIT_PATCHER_EP = new ExtensionPointName<>("com.intellij.junitPatcher");
  private static final String JIGSAW_OPTIONS = "Jigsaw Options";

  public static ParamsGroup getJigsawOptions(JavaParameters parameters) {
    return parameters.getVMParametersList().getParamsGroup(JIGSAW_OPTIONS);
  }

  protected ServerSocket myServerSocket;
  protected File myTempFile;
  protected File myWorkingDirsFile = null;

  private RemoteConnectionCreator remoteConnectionCreator;
  private final List<ArgumentFileFilter> myArgumentFileFilters = new ArrayList<>();

  public void setRemoteConnectionCreator(RemoteConnectionCreator remoteConnectionCreator) {
    this.remoteConnectionCreator = remoteConnectionCreator;
  }

  @Nullable
  @Override
  public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
    return remoteConnectionCreator == null ? null : remoteConnectionCreator.createRemoteConnection(environment);
  }

  @Override
  public boolean isPollConnection() {
    return remoteConnectionCreator != null && remoteConnectionCreator.isPollConnection();
  }

  public JavaTestFrameworkRunnableState(ExecutionEnvironment environment) {
    super(environment);
  }

  @NotNull protected abstract String getFrameworkName();

  @NotNull protected abstract String getFrameworkId();

  protected abstract void passTempFile(ParametersList parametersList, String tempFilePath);

  @NotNull protected abstract T getConfiguration();

  @Nullable protected abstract TestSearchScope getScope();

  @NotNull protected abstract String getForkMode();

  @NotNull
  protected OSProcessHandler createHandler(Executor executor) throws ExecutionException {
    appendForkInfo(executor);
    appendRepeatMode();

    EmptyProgressIndicator targetIndicator = new EmptyProgressIndicator();
    TargetEnvironment remoteEnvironment = getEnvironment().getPreparedTargetEnvironment(this, targetIndicator);
    TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
    TargetedCommandLine targetedCommandLine = targetedCommandLineBuilder.build();
    Process process = remoteEnvironment.createProcess(targetedCommandLine, new EmptyProgressIndicator());

    OSProcessHandler processHandler = new KillableColoredProcessHandler.Silent(process,
                                                                               targetedCommandLine.getCommandPresentation(remoteEnvironment),
                                                                               targetedCommandLine.getCharset(),
                                                                               targetedCommandLineBuilder.getFilesToDeleteOnTermination());

    ProcessTerminatedListener.attach(processHandler);
    final SearchForTestsTask searchForTestsTask = createSearchingForTestsTask();
    if (searchForTestsTask != null) {
      searchForTestsTask.attachTaskToProcess(processHandler);
    }
    return processHandler;
  }

  public SearchForTestsTask createSearchingForTestsTask() throws ExecutionException {
    return null;
  }

  protected boolean configureByModule(Module module) {
    return module != null;
  }

  protected boolean isIdBasedTestTree() {
    return false;
  }

  @NotNull
  @Override
  protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request,
                                                                 @Nullable TargetEnvironmentConfiguration configuration)
    throws ExecutionException {
    TargetedCommandLineBuilder commandLineBuilder = super.createTargetedCommandLine(request, configuration);
    File inputFile = InputRedirectAware.getInputFile(getConfiguration());
    if (inputFile != null) {
      commandLineBuilder.setInputFile(request.getDefaultVolume().createUpload(inputFile.getAbsolutePath()));
    }
    Map<String, String> content = commandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_CONTENT);
    if (content != null) {
      content.forEach((key, value) -> myArgumentFileFilters.add(new ArgumentFileFilter(key, value)));
    }
    return commandLineBuilder;
  }

  @NotNull
  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    final RunnerSettings runnerSettings = getRunnerSettings();

    final SMTRunnerConsoleProperties testConsoleProperties = getConfiguration().createTestConsoleProperties(executor);
    testConsoleProperties.setIdBasedTestTree(isIdBasedTestTree());
    testConsoleProperties.setIfUndefined(TestConsoleProperties.HIDE_PASSED_TESTS, false);

    final BaseTestsOutputConsoleView consoleView = SMTestRunnerConnectionUtil.createConsole(getFrameworkName(), testConsoleProperties);
    final SMTestRunnerResultsForm viewer = ((SMTRunnerConsoleView)consoleView).getResultsViewer();
    Disposer.register(getConfiguration().getProject(), consoleView);
    viewer.getTestsRootNode().setExecutionId(getEnvironment().getExecutionId());

    final OSProcessHandler handler = createHandler(executor);

    for (ArgumentFileFilter filter : myArgumentFileFilters) {
      consoleView.addMessageFilter(filter);
    }

    consoleView.attachToProcess(handler);
    final AbstractTestProxy root = viewer.getRoot();
    if (root instanceof TestProxyRoot) {
      ((TestProxyRoot)root).setHandler(handler);
    }
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        if (getConfiguration().isSaveOutputToFile()) {
          final File file = OutputFileUtil.getOutputFile(getConfiguration());
          root.setOutputFilePath(file != null ? file.getAbsolutePath() : null);
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        Runnable runnable = () -> {
          root.flushOutputFile();
          deleteTempFiles();
          clear();
        };
        UIUtil.invokeLaterIfNeeded(runnable);
        handler.removeProcessListener(this);
      }
    });

    AbstractRerunFailedTestsAction rerunFailedTestsAction = testConsoleProperties.createRerunFailedTestsAction(consoleView);
    LOG.assertTrue(rerunFailedTestsAction != null);
    rerunFailedTestsAction.setModelProvider(() -> viewer);

    final DefaultExecutionResult result = new DefaultExecutionResult(consoleView, handler);
    result.setRestartActions(rerunFailedTestsAction, new ToggleAutoTestAction() {
      @Override
      public boolean isDelayApplicable() {
        return false;
      }

      @Override
      public AbstractAutoTestManager getAutoTestManager(Project project) {
        return JavaAutoRunManager.getInstance(project);
      }
    });

    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, runnerSettings);
    return result;
  }

  protected abstract void configureRTClasspath(JavaParameters javaParameters, Module module) throws CantRunException;

  protected Sdk getJdk() {
    Project project = getConfiguration().getProject();
    final Module module = getConfiguration().getConfigurationModule().getModule();

    return module == null ? ProjectRootManager.getInstance(project).getProjectSdk() : ModuleRootManager.getInstance(module).getSdk();
  }
  
  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters javaParameters = new JavaParameters();
    Project project = getConfiguration().getProject();
    final Module module = getConfiguration().getConfigurationModule().getModule();
    javaParameters.setJdk(getJdk());

    final String parameters = getConfiguration().getProgramParameters();
    getConfiguration().setProgramParameters(null);
    try {
      JavaParametersUtil.configureConfiguration(javaParameters, getConfiguration());
    }
    finally {
      getConfiguration().setProgramParameters(parameters);
    }
    configureClasspath(javaParameters);
    javaParameters.getClassPath().addFirst(JavaSdkUtil.getIdeaRtJarPath());

    for (JUnitPatcher patcher : JUNIT_PATCHER_EP.getExtensionList()) {
      patcher.patchJavaParameters(project, module, javaParameters);
    }

    for (RunConfigurationExtension ext : RunConfigurationExtension.EP_NAME.getExtensionList()) {
      ext.updateJavaParameters(getConfiguration(), javaParameters, getRunnerSettings(), getEnvironment().getExecutor());
    }

    if (!StringUtil.isEmptyOrSpaces(parameters)) {
      javaParameters.getProgramParametersList().addAll(getNamedParams(parameters));
    }

    if (ConsoleBuffer.useCycleBuffer()) {
      javaParameters.getVMParametersList().addProperty("idea.test.cyclic.buffer.size", String.valueOf(ConsoleBuffer.getCycleBufferSize()));
    }

    javaParameters.setShortenCommandLine(getConfiguration().getShortenCommandLine(), project);

    return javaParameters;
  }

  protected List<String> getNamedParams(String parameters) {
    return Collections.singletonList("@name" + parameters);
  }

  private ServerSocket myForkSocket = null;

  @Nullable
  public ServerSocket getForkSocket() {
    if (myForkSocket == null && (!Comparing.strEqual(getForkMode(), "none") || forkPerModule()) && getRunnerSettings() != null) {
      try {
        myForkSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return myForkSocket;
  }

  private boolean isExecutorDisabledInForkedMode() {
    final RunnerSettings settings = getRunnerSettings();
    return settings != null && !(settings instanceof GenericDebuggerRunnerSettings);
  }

  public void appendForkInfo(Executor executor) throws ExecutionException {
    final String forkMode = getForkMode();
    if (Comparing.strEqual(forkMode, "none")) {
      if (forkPerModule()) {
        if (isExecutorDisabledInForkedMode()) {
          final String actionName = executor.getActionName();
          throw new CantRunException("'" + actionName + "' is disabled when per-module working directory is configured.<br/>" +
                                     "Please specify single working directory, or change test scope to single module.");
        }
      } else {
        return;
      }
    } else if (isExecutorDisabledInForkedMode()) {
      final String actionName = executor.getActionName();
      throw new CantRunException(actionName + " is disabled in fork mode.<br/>Please change fork mode to &lt;none&gt; to " + StringUtil.toLowerCase(actionName) + ".");
    }

    final JavaParameters javaParameters = getJavaParameters();
    final Sdk jdk = javaParameters.getJdk();
    if (jdk == null) {
      throw new ExecutionException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
    }

    try {
      final File tempFile = FileUtil.createTempFile("command.line", "", true);
      try (PrintWriter writer = new PrintWriter(tempFile, CharsetToolkit.UTF8)) {
        ShortenCommandLine shortenCommandLine = getConfiguration().getShortenCommandLine();
        boolean useDynamicClasspathForForkMode = shortenCommandLine == null
                                                 ? JdkUtil.useDynamicClasspath(getConfiguration().getProject())
                                                 : shortenCommandLine != ShortenCommandLine.NONE;
        if (shortenCommandLine == ShortenCommandLine.ARGS_FILE) {
          //see com.intellij.rt.execution.testFrameworks.ForkedByModuleSplitter.startChildFork
          writer.println(shortenCommandLine);
        }
        else if (useDynamicClasspathForForkMode && forkPerModule()) {
          writer.println("use classpath jar");
        }
        else {
          writer.println("");
        }

        writer.println(((JavaSdkType)jdk.getSdkType()).getVMExecutablePath(jdk));
        for (String vmParameter : javaParameters.getVMParametersList().getList()) {
          writer.println(vmParameter);
        }
      }

      passForkMode(getForkMode(), tempFile, javaParameters);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  protected abstract void passForkMode(String forkMode, File tempFile, JavaParameters parameters) throws ExecutionException;

  protected void collectListeners(JavaParameters javaParameters, StringBuilder buf, String epName, String delimiter) {
    final T configuration = getConfiguration();
    for (final Object listener : Extensions.getRootArea().getExtensionPoint(epName).getExtensionList()) {
      boolean enabled = true;
      for (RunConfigurationExtension ext : RunConfigurationExtension.EP_NAME.getExtensionList()) {
        if (ext.isListenerDisabled(configuration, listener, getRunnerSettings())) {
          enabled = false;
          break;
        }
      }
      if (enabled) {
        if (buf.length() > 0) buf.append(delimiter);
        final Class<?> classListener = listener.getClass();
        buf.append(classListener.getName());
        javaParameters.getClassPath().add(PathUtil.getJarPathForClass(classListener));
      }
    }
  }

  protected void configureClasspath(final JavaParameters javaParameters) throws CantRunException {
    RunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
    final String jreHome = getConfiguration().isAlternativeJrePathEnabled() ? getConfiguration().getAlternativeJrePath() : null;
    final int pathType = JavaParameters.JDK_AND_CLASSES_AND_TESTS;
    Module module = configurationModule.getModule();
    if (configureByModule(module)) {
      JavaParametersUtil.configureModule(configurationModule, javaParameters, pathType, jreHome);
      LOG.assertTrue(module != null);
      if (JavaSdkUtil.isJdkAtLeast(javaParameters.getJdk(), JavaSdkVersion.JDK_1_9)) {
        configureModulePath(javaParameters, module);
      }
    }
    else {
      JavaParametersUtil.configureProject(getConfiguration().getProject(), javaParameters, pathType, jreHome);
    }
    configureRTClasspath(javaParameters, module);
  }

  protected static PsiJavaModule findJavaModule(Module module, boolean inTests) {
    return DumbService.getInstance(module.getProject())
      .computeWithAlternativeResolveEnabled(() -> JavaModuleGraphUtil.findDescriptorByModule(module, inTests));
  }

  private void configureModulePath(JavaParameters javaParameters, @NotNull Module module) {
    if (!useModulePath()) return;
    PsiJavaModule testModule = findJavaModule(module, true);
    if (testModule != null) {
      //adding the test module explicitly as it is unreachable from `idea.rt`
      ParametersList vmParametersList = javaParameters
        .getVMParametersList()
        .addParamsGroup(JIGSAW_OPTIONS)
        .getParametersList();

      vmParametersList.add("--add-modules");
      vmParametersList.add(testModule.getName());
      //setup module path
      PathsList classPath = javaParameters.getClassPath();
      PathsList modulePath = javaParameters.getModulePath();
      modulePath.addAll(classPath.getPathList());
      classPath.clear();
    }
    else {
      PsiJavaModule prodModule = findJavaModule(module, false);
      if (prodModule != null) {
        splitDepsBetweenModuleAndClasspath(javaParameters, module, prodModule);
      }
    }
  }

  /**
   * Put dependencies reachable from module-info located in production sources on the module path
   * leave all other dependencies on the class path as is
   */
  private void splitDepsBetweenModuleAndClasspath(JavaParameters javaParameters, Module module, PsiJavaModule prodModule) {
    CompilerModuleExtension compilerExt = CompilerModuleExtension.getInstance(module);
    if (compilerExt == null) return;

    PathsList modulePath = javaParameters.getModulePath();
    PathsList classPath = javaParameters.getClassPath();

    putDependenciesOnModulePath(modulePath, classPath, prodModule);

    ParametersList vmParametersList = javaParameters.getVMParametersList()
      .addParamsGroup(JIGSAW_OPTIONS)
      .getParametersList();
    String prodModuleName = prodModule.getName();

    //ensure test output is merged to the production module
    VirtualFile testOutput = compilerExt.getCompilerOutputPathForTests();
    if (testOutput != null) {
      vmParametersList.add("--patch-module");
      vmParametersList.add(prodModuleName + "=" + testOutput.getPath());
    }

    //ensure test dependencies missing from production module descriptor are available in tests
    //todo enumerate all test dependencies explicitly
    vmParametersList.add("--add-reads");
    vmParametersList.add(prodModuleName + "=ALL-UNNAMED");

    //open packages with tests to test runner
    List<String> opensOptions = new ArrayList<>();
    collectPackagesToOpen(opensOptions);
    for (String option : opensOptions) {
      if (option.isEmpty()) continue;
      vmParametersList.add("--add-opens");
      vmParametersList.add(prodModuleName + "/" + option + "=ALL-UNNAMED");
    }

    //ensure production module is explicitly added as test starter in `idea-rt` doesn't depend on it
    vmParametersList.add("--add-modules");
    vmParametersList.add(prodModuleName);
  }

  protected void collectPackagesToOpen(List<String> options) { }

  /**
   * called on EDT
   */
  protected static void collectSubPackages(List<String> options, PsiPackage aPackage, GlobalSearchScope globalSearchScope) {
    if (aPackage.getClasses(globalSearchScope).length > 0) {
      options.add(aPackage.getQualifiedName());
    }
    PsiPackage[] subPackages = aPackage.getSubPackages(globalSearchScope);
    for (PsiPackage subPackage : subPackages) {
      collectSubPackages(options, subPackage, globalSearchScope);
    }
  }

  protected static void putDependenciesOnModulePath(PathsList modulePath,
                                                    PathsList classPath,
                                                    PsiJavaModule prodModule) {
    Set<PsiJavaModule> allRequires = JavaModuleGraphUtil.getAllDependencies(prodModule);
    allRequires.add(prodModule);    //put production output on the module path as well
    JarFileSystem jarFS = JarFileSystem.getInstance();
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(prodModule.getProject());
    allRequires.stream()
      .filter(javaModule -> !PsiJavaModule.JAVA_BASE.equals(javaModule.getName()))
      .map(javaModule -> getClasspathEntry(javaModule, fileIndex, jarFS))
      .filter(Objects::nonNull)
      .forEach(file -> putOnModulePath(modulePath, classPath, file));
  }

  private static void putOnModulePath(PathsList modulePath, PathsList classPath, VirtualFile virtualFile) {
    String path = PathUtil.getLocalPath(virtualFile.getPath());
    if (classPath.getPathList().contains(path)) {
      classPath.remove(path);
      modulePath.add(path);
    }
  }

  private static VirtualFile getClasspathEntry(PsiJavaModule javaModule,
                                               ProjectFileIndex fileIndex,
                                               JarFileSystem jarFileSystem) {
    VirtualFile moduleFile = PsiImplUtil.getModuleVirtualFile(javaModule);

    Module moduleDependency = fileIndex.getModuleForFile(moduleFile);
    if (moduleDependency == null) {
      return jarFileSystem.getLocalVirtualFileFor(moduleFile);
    }

    CompilerModuleExtension moduleExtension = CompilerModuleExtension.getInstance(moduleDependency);
    return moduleExtension != null ? moduleExtension.getCompilerOutputPath() : null;
  }

  protected void createServerSocket(JavaParameters javaParameters) {
    try {
      myServerSocket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
      javaParameters.getProgramParametersList().add("-socket" + myServerSocket.getLocalPort());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  protected boolean spansMultipleModules(final String qualifiedName) {
    if (qualifiedName != null) {
      final Project project = getConfiguration().getProject();
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(qualifiedName);
      if (aPackage != null) {
        final TestSearchScope scope = getScope();
        if (scope != null) {
          final SourceScope sourceScope = scope.getSourceScope(getConfiguration());
          if (sourceScope != null) {
            final GlobalSearchScope configurationSearchScope = GlobalSearchScopesCore.projectTestScope(project).intersectWith(
              sourceScope.getGlobalSearchScope());
            final PsiDirectory[] directories = aPackage.getDirectories(configurationSearchScope);
            return Arrays.stream(directories)
                     .map(dir -> ModuleUtilCore.findModuleForFile(dir.getVirtualFile(), project))
                     .filter(Objects::nonNull)
                     .distinct()
                     .count() > 1;
          }
        }
      }
    }
    return false;
  }

  /**
   * Configuration based on a package spanning multiple modules.
   */
  protected boolean forkPerModule() {
    return getScope() != TestSearchScope.SINGLE_MODULE &&
           toChangeWorkingDirectory(getConfiguration().getWorkingDirectory()) &&
           spansMultipleModules(getConfiguration().getPackage());
  }

  private static boolean toChangeWorkingDirectory(final String workingDirectory) {
    //noinspection deprecation
    return PathMacroUtil.DEPRECATED_MODULE_DIR.equals(workingDirectory) ||
           PathMacroUtil.MODULE_WORKING_DIR.equals(workingDirectory) ||
           ProgramParametersConfigurator.MODULE_WORKING_DIR.equals(workingDirectory);
  }

  protected void createTempFiles(JavaParameters javaParameters) {
    try {
      myWorkingDirsFile = FileUtil.createTempFile("idea_working_dirs_" + getFrameworkId(), ".tmp", true);
      javaParameters.getProgramParametersList().add("@w@" + myWorkingDirsFile.getAbsolutePath());

      myTempFile = FileUtil.createTempFile("idea_" + getFrameworkId(), ".tmp", true);
      passTempFile(javaParameters.getProgramParametersList(), myTempFile.getAbsolutePath());
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  protected void writeClassesPerModule(String packageName,
                                       JavaParameters javaParameters,
                                       Map<Module, List<String>> perModule,
                                       @NotNull String filters) throws FileNotFoundException, UnsupportedEncodingException {
    if (perModule != null) {
      final String classpath = getScope() == TestSearchScope.WHOLE_PROJECT
                               ? null : javaParameters.getClassPath().getPathsString();

      String workingDirectory = getConfiguration().getWorkingDirectory();
      //when only classpath should be changed, e.g. for starting tests in IDEA's project when some modules can never appear on the same classpath,
      //like plugin and corresponding IDE register the same components twice
      boolean toChangeWorkingDirectory = toChangeWorkingDirectory(workingDirectory);

      try (PrintWriter wWriter = new PrintWriter(myWorkingDirsFile, CharsetToolkit.UTF8)) {
        wWriter.println(packageName);
        for (Module module : perModule.keySet()) {
          wWriter.println(toChangeWorkingDirectory ? PathMacroUtil.getModuleDir(module.getModuleFilePath()) : workingDirectory);
          wWriter.println(module.getName());

          if (classpath == null) {
            final JavaParameters parameters = new JavaParameters();
            try {
              JavaParametersUtil.configureModule(module, parameters, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                                                 getConfiguration().isAlternativeJrePathEnabled() ? getConfiguration()
                                                   .getAlternativeJrePath() : null);
              if (JavaSdkUtil.isJdkAtLeast(parameters.getJdk(), JavaSdkVersion.JDK_1_9)) {
                configureModulePath(parameters, module);
              }
              configureRTClasspath(parameters, module);
              parameters.getClassPath().add(JavaSdkUtil.getIdeaRtJarPath());
              writeClasspath(wWriter, parameters);
            }
            catch (CantRunException e) {
              writeClasspath(wWriter, javaParameters);
            }
          }
          else {
            writeClasspath(wWriter, javaParameters);
          }

          final List<String> classNames = perModule.get(module);
          wWriter.println(classNames.size());
          for (String className : classNames) {
            wWriter.println(className);
          }
          wWriter.println(filters);
        }
      }
    }
  }

  private static void writeClasspath(PrintWriter wWriter, JavaParameters parameters) {
    wWriter.println(parameters.getClassPath().getPathsString());
    wWriter.println(parameters.getModulePath().getPathsString());
    ParamsGroup paramsGroup = getJigsawOptions(parameters);
    if (paramsGroup == null) {
      wWriter.println(0);
    }
    else {
      List<String> parametersList = paramsGroup.getParametersList().getList();
      wWriter.println(parametersList.size());
      for (String option : parametersList) {
        wWriter.println(option);
      }
    }
  }

  protected void deleteTempFiles() {
    if (myTempFile != null) {
      FileUtil.delete(myTempFile);
    }

    if (myWorkingDirsFile != null) {
      FileUtil.delete(myWorkingDirsFile);
    }
  }

  public void appendRepeatMode() throws ExecutionException { }

  protected boolean useModulePath() {
    return true;
  }
}
