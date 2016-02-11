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

public abstract class ExecutionTestCase extends IdeaTestCase {
  private OutputChecker myChecker;
  private int myTimeout;
  private static File ourOutputRoot;
  private File myModuleOutputDir;
  private CompilerTester myCompilerTester;

  public ExecutionTestCase() {
    setTimeout(300000); //30 seconds
  }

  public void setTimeout(int timeout) {
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
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        ExecutionTestCase.super.setUp();
      }
    });
    if (!myModuleOutputDir.exists()) {
      VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ourOutputRoot);
      assertNotNull(ourOutputRoot.getAbsolutePath(), vDir);
      vDir.getChildren();//we need this to load children to VFS to fire VFileCreatedEvent for the output directory

      myCompilerTester = new CompilerTester(myProject, Arrays.asList(ModuleManager.getInstance(myProject).getModules()));
      List<CompilerMessage> messages = myCompilerTester.rebuild();
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
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final String modulePath = getTestAppPath();
        final String srcPath = modulePath + File.separator + "src";
        VirtualFile moduleDir = LocalFileSystem.getInstance().findFileByPath(modulePath.replace(File.separatorChar, '/'));
        VirtualFile srcDir = LocalFileSystem.getInstance().findFileByPath(srcPath.replace(File.separatorChar, '/'));

        final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
        PsiTestUtil.removeAllRoots(myModule, rootManager.getSdk());
        PsiTestUtil.addContentRoot(myModule, moduleDir);
        PsiTestUtil.addSourceRoot(myModule, srcDir);
        IdeaTestUtil.setModuleLanguageLevel(myModule, LanguageLevel.JDK_1_8);
        PsiTestUtil.setCompilerOutputPath(myModule, VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(myModuleOutputDir.getAbsolutePath())), false);
      }
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
  protected void runBareRunnable(ThrowableRunnable<Throwable> runnable) throws Throwable {
    runnable.run();
  }

  @Override
  protected void runTest() throws Throwable {
    myChecker.init(getTestName(true));
    super.runTest();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myCompilerTester != null) {
      myCompilerTester.tearDown();
    }
    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Throwable {
        ExecutionTestCase.super.tearDown();
      }
    });
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
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, getTestRootDisposable());

    final boolean[] isRunning = {true};
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        boolean b;
        synchronized (isRunning) {
          b = isRunning[0];
        }
        if (b) {
          processHandler.destroyProcess();
          LOG.error("process was running over " + myTimeout / 1000 + " seconds. Interrupted. ");
        }
      }
    }, myTimeout);
    processHandler.waitFor();
    synchronized (isRunning) {
      isRunning[0] = false;
    }
    Disposer.dispose(alarm);
  }

  public void waitFor(Runnable r) {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, getTestRootDisposable());
    final Thread thread = Thread.currentThread();

    final boolean[] isRunning = {true};
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        boolean b;
        synchronized (isRunning) {
          b = isRunning[0];
        }
        if (b) {
          thread.interrupt();
          LOG.error("test was running over " + myTimeout / 1000 + " seconds. Interrupted. ");
        }
      }
    }, myTimeout);
    r.run();
    synchronized (isRunning) {
      isRunning[0] = false;
    }
    Thread.interrupted();
  }
}
