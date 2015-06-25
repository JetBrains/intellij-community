/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class ExecutionTestCase extends IdeaTestCase {
  private OutputChecker myChecker;

  private int myTimeout;

  private static AssertionFailedError ourAssertion;
  private static final String CLASSES = "classes";
  private static final String SRC = "src";

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
    ourAssertion = null;
    ensureCompiledAppExists();
    myChecker = initOutputChecker();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          ExecutionTestCase.super.setUp();
        }
        catch (Throwable e) {
          e.printStackTrace();
          assertTrue(false);
        }
      }
    });
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
        PsiTestUtil.setCompilerOutputPath(myModule, VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(getAppClassesPath())), false);
      }
    });
  }

  public void println(@NonNls String s, Key outputType) {
    myChecker.println(s, outputType);
  }

  public void print(String s, Key outputType) {
    myChecker.print(s, outputType);
  }

  @Override
  protected void runBareRunnable(Runnable runnable) throws Throwable {
    runnable.run();
  }

  @Override
  protected void runTest() throws Throwable {
    myChecker.init(getTestName(true));
    super.runTest();
  }

  @Override
  protected void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          ExecutionTestCase.super.tearDown();
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
    if (ourAssertion != null) {
      throw ourAssertion;
    }
    //myChecker.checkValid(getTestProjectJdk());
    //probably some thread is destroyed right now because of log exception
    //wait a little bit
    synchronized (this) {
      wait(300);
    }
  }

  protected JavaParameters createJavaParameters(String mainClass) {
    JavaParameters parameters = new JavaParameters();
    parameters.getClassPath().add(getAppClassesPath());
    parameters.setMainClass(mainClass);
    parameters.setJdk(JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
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

  protected String getAppClassesPath() {
    return getTestAppPath() + File.separator + "classes";
  }

  public void waitProcess(final ProcessHandler processHandler) {
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
    alarm.dispose();
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

//  public static void fail(String message) {
//    ourAssertion = new AssertionFailedError(message);
//  }
//
//  static public void assertTrue(String message, boolean condition) {
//    if (!condition)
//      fail(message);
//  }
//
//  static public void assertTrue(boolean condition) {
//    assertTrue(null, condition);
//  }

  //private static final int CURRENT_VERSION = 6;
  protected int getTestAppVersion() {
    return 6;
  }

  protected void ensureCompiledAppExists() throws Exception {
    final String appPath = getTestAppPath();
    final File classesDir = new File(appPath, CLASSES);
    String VERSION_FILE_NAME = "version-" + getTestAppVersion();
    final File versionFile = new File(classesDir, VERSION_FILE_NAME);
    if (!classesDir.exists() || !versionFile.exists() || !hasCompiledClasses(classesDir)) {
      FileUtil.delete(classesDir);
      classesDir.mkdirs();
      if (compileTinyApp(appPath) != 0) {
        throw new Exception("Failed to compile debugger test application.\nIt must be compiled in order to run debugger tests.\n" + appPath);
      }
      versionFile.createNewFile();
    }
  }

  private int compileTinyApp(String appPath) {
    final List<String> args = new ArrayList<String>();
    args.add("-g");
    args.add("-d");
    args.add(new File(appPath, CLASSES).getPath());
    
    final Class<TestCase> testCaseClass = TestCase.class;
    final String junitLibRoot = PathManager.getResourceRoot(testCaseClass, "/" + testCaseClass.getName().replace('.', '/') + ".class");
    if (junitLibRoot != null) {
      args.add("-cp");
      args.add(junitLibRoot);
    }
    
    final File[] files = new File(appPath, SRC).listFiles(FileFilters.withExtension("java"));
    if (files == null) return 0; // Nothing to compile

    for (File file : files) {
      args.add(file.getPath());
    }
    return com.sun.tools.javac.Main.compile(ArrayUtil.toStringArray(args));
  }

  private boolean hasCompiledClasses(final File classesDir) {
    for (File file : classesDir.listFiles()) {
      if (file.isFile() && file.getName().endsWith(".class")) {
        return true;
      }
    }
    return false;
  }
}
