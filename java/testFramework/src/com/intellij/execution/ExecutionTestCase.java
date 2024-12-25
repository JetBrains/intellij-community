// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.debugger.impl.OutputChecker;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.*;
import com.intellij.util.Alarm;
import com.intellij.util.PathUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides a framework for compiling Java code, for running or debugging it, and for capturing the output.
 * <p>
 * The output is recorded using {@link #print(String, Key)} and {@link #println(String, Key)},
 * and at the end of the test, it can be validated against prerecorded output files, as described in {@link OutputChecker}.
 * This validation needs to be triggered explicitly using {@link #getChecker()} and {@link OutputChecker#checkValid(Sdk)};
 * by default, the output is discarded.
 */
public abstract class ExecutionTestCase extends JavaProjectTestCase {
  private OutputChecker myChecker;
  private int myTimeoutMillis = 300_000;
  private static Path ourOutputRoot;
  private Path myModuleOutputDir;

  protected static final String SOURCES_DIRECTORY_NAME = "src";

  public final void setTimeout(int timeoutMillis) {
    myTimeoutMillis = timeoutMillis;
  }

  protected abstract OutputChecker initOutputChecker();

  protected abstract String getTestAppPath();

  protected boolean areLogErrorsIgnored() {
    return false;
  }

  @Override
  protected void setUp() throws Exception {
    setupTempDir();

    if (ourOutputRoot == null) {
      ourOutputRoot = getTempDir().newPath();
    }

    myChecker = initOutputChecker();
    EdtTestUtil.runInEdtAndWait(() -> super.setUp());
    myModuleOutputDir = getModuleOutputDir();
    if (!Files.exists(myModuleOutputDir)) {
      Files.createDirectories(myModuleOutputDir);
      if (FileUtil.isAncestor(ourOutputRoot.toFile(), myModuleOutputDir.toFile(), false)) {
        VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ourOutputRoot);
        assertNotNull(ourOutputRoot.toString(), vDir);
      }

      compileProject();
    }
  }

  protected void compileProject() throws Exception {
    // JDK added by compilerTester is used after compilation, so, we don't dispose compilerTester after rebuild
    CompilerTester compilerTester = new CompilerTester(myProject, Arrays.asList(ModuleManager.getInstance(myProject).getModules()),
                                                       getTestRootDisposable(), overrideCompileJdkAndOutput());
    List<CompilerMessage> messages = compilerTester.rebuild();
    for (CompilerMessage message : messages) {
      if (message.getCategory() == CompilerMessageCategory.ERROR) {
        FileUtil.delete(myModuleOutputDir);
        fail("Compilation failed: " + message + " " + message.getVirtualFile());
      }
    }
  }

  protected @NotNull Path getModuleOutputDir() {
    return ourOutputRoot.resolve(PathUtil.getFileName(getTestAppPath()));
  }

  protected boolean overrideCompileJdkAndOutput() {
    return true;
  }

  @Override
  protected void setUpModule() {
    super.setUpModule();
    ApplicationManager.getApplication().runWriteAction(() -> {
      setupModuleRoots();

      IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_8);

      Path outputDir = getModuleOutputDir();
      PsiTestUtil.setCompilerOutputPath(myModule, VfsUtilCore.pathToUrl(outputDir.toString()), false);
    });
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
  }

  /** Adds the message to a buffer that can later be validated using {@link #getChecker()}. */
  protected final void systemPrintln(@NotNull @NonNls String msg) {
    println(msg, ProcessOutputType.SYSTEM);
  }

  public void println(@NonNls String s, Key outputType) {
    myChecker.println(s, outputType);
  }

  public void print(String s, Key outputType) {
    myChecker.print(s, outputType);
  }

  @SuppressWarnings("CallToPrintStackTrace")
  @Override
  protected void runBareRunnable(@NotNull ThrowableRunnable<Throwable> runnable) throws Throwable {
    runnable.run();
    int errorLoggingHappened = TestLoggerFactory.getRethrowErrorNumber();
    if (errorLoggingHappened != 0 && !areLogErrorsIgnored()) {
      assertEquals("No ignored errors should happen during execution tests", 0, errorLoggingHappened);
    }
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    myChecker.init(getTestName(true));
    super.runTestRunnable(testRunnable);
  }

  @Override
  protected void tearDown() throws Exception {
    myChecker = null;
    EdtTestUtil.runInEdtAndWait(() -> super.tearDown());
  }

  protected JavaParameters createJavaParameters(String mainClass) {
    JavaParameters parameters = new JavaParameters();
    parameters.getClassPath().add(getAppOutputPath());
    parameters.setMainClass(mainClass);
    parameters.setJdk(JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
    parameters.setWorkingDirectory(getTestAppPath());
    return parameters;
  }

  @RequiresWriteLock
  protected void setupModuleRoots() {
    final String modulePath = getTestAppPath();
    final String srcPath = getSrcPath(modulePath);
    VirtualFile moduleDir = LocalFileSystem.getInstance().findFileByPath(modulePath.replace(File.separatorChar, '/'));
    VirtualFile srcDir = LocalFileSystem.getInstance().findFileByPath(srcPath.replace(File.separatorChar, '/'));

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    PsiTestUtil.removeAllRoots(myModule, rootManager.getSdk());
    PsiTestUtil.addContentRoot(myModule, moduleDir);
    PsiTestUtil.addSourceRoot(myModule, srcDir);
  }

  protected OutputChecker getChecker() {
    return myChecker;
  }

  protected String getAppDataPath() {
    return getTestAppPath() + File.separator + "data";
  }

  protected String getAppOptionsPath() {
    return getTestAppPath() + File.separator + "config" + File.separator + "options";
  }

  protected String getAppOutputPath() {
    return getModuleOutputDir().toString();
  }

  protected @NotNull String getSrcPath(String modulePath) {
    return modulePath + File.separator + SOURCES_DIRECTORY_NAME;
  }

  public void waitProcess(@NotNull ProcessHandler processHandler) {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());

    AtomicBoolean isRunning = new AtomicBoolean(true);
    alarm.addRequest(() -> {
      if (isRunning.get()) {
        processHandler.destroyProcess();
        LOG.error("process was running over " + myTimeoutMillis / 1000 + " seconds. Interrupted. ");
      }
    }, myTimeoutMillis);
    processHandler.waitFor();
    isRunning.set(false);
    Disposer.dispose(alarm);
  }

  public void waitFor(Runnable r) {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
    Thread thread = Thread.currentThread();
    AtomicBoolean isRunning = new AtomicBoolean(true);
    alarm.addRequest(() -> {
      if (isRunning.get()) {
        thread.interrupt();
        LOG.error("test was running over " + myTimeoutMillis / 1000 + " seconds. Interrupted. ");
      }
    }, myTimeoutMillis);
    r.run();
    isRunning.set(false);
    Thread.interrupted();
    Disposer.dispose(alarm);
  }
}
