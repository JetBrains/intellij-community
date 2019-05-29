// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.debugger.impl.OutputChecker;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.ProcessHandler;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public abstract class ExecutionTestCase extends JavaProjectTestCase {
  private OutputChecker myChecker;
  private int myTimeout;
  private static File ourOutputRoot;
  private File myModuleOutputDir;

  public ExecutionTestCase() {
    setTimeout(300000); //30 seconds
  }

  public final void setTimeout(int timeout) {
    myTimeout = timeout;
  }

  protected abstract OutputChecker initOutputChecker();

  protected abstract String getTestAppPath();

  @Override
  protected void setUp() throws Exception {
    if (ourOutputRoot == null) {
      ourOutputRoot = FileUtil.createTempDirectory("ExecutionTestCase", null, true);
    }
    myModuleOutputDir = new File(ourOutputRoot, PathUtil.getFileName(getTestAppPath()));
    myChecker = initOutputChecker();
    EdtTestUtil.runInEdtAndWait(() -> super.setUp());
    if (!myModuleOutputDir.exists()) {
      VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ourOutputRoot);
      assertNotNull(ourOutputRoot.getAbsolutePath(), vDir);
      //we need this to load children to VFS to fire VFileCreatedEvent for the output directory
      vDir.getChildren();

      // JDK added by compilerTester is used after compilation, so, we don't dispose compilerTester after rebuild
      CompilerTester compilerTester = new CompilerTester(myProject, Arrays.asList(ModuleManager.getInstance(myProject).getModules()), getTestRootDisposable());
      List<CompilerMessage> messages = compilerTester.rebuild();
      for (CompilerMessage message : messages) {
        if (message.getCategory() == CompilerMessageCategory.ERROR) {
          FileUtil.delete(myModuleOutputDir);
          fail("Compilation failed: " + message);
        }
      }
    }
  }

  @Override
  protected void setUpModule() {
    super.setUpModule();
    ApplicationManager.getApplication().runWriteAction(() -> {
      final String modulePath = getTestAppPath();
      final String srcPath = modulePath + File.separator + "src";
      VirtualFile moduleDir = LocalFileSystem.getInstance().findFileByPath(modulePath.replace(File.separatorChar, '/'));
      VirtualFile srcDir = LocalFileSystem.getInstance().findFileByPath(srcPath.replace(File.separatorChar, '/'));

      final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
      PsiTestUtil.removeAllRoots(myModule, rootManager.getSdk());
      PsiTestUtil.addContentRoot(myModule, moduleDir);
      PsiTestUtil.addSourceRoot(myModule, srcDir);
      IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_8);
      PsiTestUtil.setCompilerOutputPath(myModule, VfsUtilCore.pathToUrl(myModuleOutputDir.getAbsolutePath()), false);
    });
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
  }

  public void println(@NonNls String s, Key outputType) {
    myChecker.println(s, outputType);
  }

  public void print(String s, Key outputType) {
    myChecker.print(s, outputType);
  }

  @Override
  protected void runBareRunnable(@NotNull ThrowableRunnable<Throwable> runnable) throws Throwable {
    runnable.run();
  }

  @Override
  protected void runTest() throws Throwable {
    myChecker.init(getTestName(true));
    super.runTest();
  }

  @Override
  protected void tearDown() throws Exception {
    myChecker = null;
    EdtTestUtil.runInEdtAndWait(() -> super.tearDown());
    //myChecker.checkValid(getTestProjectJdk());
    //probably some thread is destroyed right now because of log exception
    //wait a little bit
    synchronized (this) {
      wait(300);
    }
  }

  protected JavaParameters createJavaParameters(String mainClass) {
    JavaParameters parameters = new JavaParameters();
    parameters.getClassPath().add(getAppOutputPath());
    parameters.setMainClass(mainClass);
    parameters.setJdk(JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
    parameters.setWorkingDirectory(getTestAppPath());
    return parameters;
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
    return myModuleOutputDir.getAbsolutePath();
  }

  public void waitProcess(@NotNull final ProcessHandler processHandler) {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());

    final boolean[] isRunning = {true};
    alarm.addRequest(() -> {
      boolean b;
      synchronized (isRunning) {
        b = isRunning[0];
      }
      if (b) {
        processHandler.destroyProcess();
        LOG.error("process was running over " + myTimeout / 1000 + " seconds. Interrupted. ");
      }
    }, myTimeout);
    processHandler.waitFor();
    synchronized (isRunning) {
      isRunning[0] = false;
    }
    Disposer.dispose(alarm);
  }

  public void waitFor(Runnable r) {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, getTestRootDisposable());
    final Thread thread = Thread.currentThread();

    final boolean[] isRunning = {true};
    alarm.addRequest(() -> {
      boolean b;
      synchronized (isRunning) {
        b = isRunning[0];
      }
      if (b) {
        thread.interrupt();
        LOG.error("test was running over " + myTimeout / 1000 + " seconds. Interrupted. ");
      }
    }, myTimeout);
    r.run();
    synchronized (isRunning) {
      isRunning[0] = false;
    }
    Thread.interrupted();
  }
}
