// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.restart;

import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public abstract class IsolatedIdeTestCase extends UsefulTestCase {
  //protected Path myProjectPath;
  //
  //@Override
  //protected void setUp() throws Exception {
  //  super.setUp();
  //  myProjectPath = TemporaryDirectory.generateTemporaryPath(getName());
  //}
  //
  //@Override
  //protected void tearDown() throws Exception {
  //  new RunAll().append(
  //    () -> super.tearDown(),
  //    () -> FileUtil.delete(new File(PathManager.getSystemPath())),
  //    () -> FileUtil.delete(new File(PathManager.getConfigPath())),
  //    () -> {
  //      if (myProjectPath != null) {
  //        Files.walk(myProjectPath)
  //          .sorted(Comparator.reverseOrder())
  //          .map(Path::toFile)
  //          .filter(File::exists)
  //          .forEach(File::delete);
  //      }
  //    }
  //  ).run();
  //}
  //
  //@Override
  //protected boolean shouldContainTempFiles() {
  //  return false;
  //}
  //
  //public void doTestWithRestart(Class<? extends Consumer<JavaCodeInsightTestFixture>>... runs) {
  //  for (Class<? extends Consumer<JavaCodeInsightTestFixture>> run : runs) {
  //    executeInIsolatedIde(run.getName(), getName(), myProjectPath, "src/");
  //  }
  //}
  //
  //public void executeInIsolatedIde(@NotNull String actionClassName,
  //                                 @NotNull String testName,
  //                                 @NotNull Path projectRoot,
  //                                 @NotNull String sourceContentRoot) {
  //  ClassLoader loader = copyAppClassLoader();
  //
  //  Object isolatedIde;
  //  Class<?> isolatedIdeClass;
  //
  //  try {
  //    isolatedIdeClass = loader.loadClass(IsolatedIde.class.getName());
  //    Constructor<?> constructor = isolatedIdeClass.getDeclaredConstructor(String.class, Path.class, String.class);
  //    constructor.setAccessible(true);
  //    isolatedIde = constructor.newInstance(testName, projectRoot, sourceContentRoot);
  //  }
  //  catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
  //    throw new AssertionError(e);
  //  }
  //
  //  ClassLoader baseContextClassLoader = Thread.currentThread().getContextClassLoader();
  //  try {
  //    Thread.currentThread().setContextClassLoader(loader);
  //    invoke(isolatedIdeClass, isolatedIde, "start");
  //
  //    Method execute = ReflectionUtil.getDeclaredMethod(isolatedIdeClass, "execute", Consumer.class);
  //    try {
  //      Class<?> aClass = loader.loadClass(actionClassName);
  //      Constructor<?> constructor = aClass.getDeclaredConstructors()[0];
  //      constructor.setAccessible(true);
  //      execute.invoke(isolatedIde, constructor.newInstance());
  //    }
  //    catch (IllegalAccessException | InstantiationException | ClassNotFoundException e) {
  //      ExceptionUtil.rethrow(e);
  //    }
  //    catch (InvocationTargetException e) {
  //      ExceptionUtil.rethrow(e.getTargetException());
  //    }
  //  } finally {
  //    try {
  //      invoke(isolatedIdeClass, isolatedIde, "stop");
  //    } finally {
  //      Thread.currentThread().setContextClassLoader(baseContextClassLoader);
  //    }
  //  }
  //}
  //
  //private static Object invoke(Class<?> isolatedIdeClass, Object isolatedIde, String methodName) {
  //  try {
  //    return ReflectionUtil.getDeclaredMethod(isolatedIdeClass, methodName).invoke(isolatedIde);
  //  }
  //  catch (IllegalAccessException | InvocationTargetException e) {
  //    throw new RuntimeException("can't invoke \'" + methodName + "\'", e);
  //  }
  //}
  //
  //@NotNull
  //private static ClassLoader copyAppClassLoader() {
  //  ClassLoader classLoader = UsefulTestCase.class.getClassLoader();
  //  Object result = invoke(classLoader.getClass(), classLoader, "getUrls");
  //  return UrlClassLoader.build().urls((List<URL>)result).parent(classLoader.getParent()).get();
  //}
  //
  //@SuppressWarnings("unused")
  //private static class IsolatedIde {
  //  @NotNull private final String myTestName;
  //  @NotNull private final Path myProjectRoot;
  //  private final String mySourceContentRoot;
  //  private JavaCodeInsightTestFixture myFixture;
  //
  //  IsolatedIde(@NotNull String testName, @NotNull Path projectRoot, @NotNull String sourceContentRoot) {
  //    myTestName = testName;
  //    myProjectRoot = projectRoot;
  //    mySourceContentRoot = sourceContentRoot;
  //  }
  //
  //  void start() {
  //    TestFixtureBuilder<IdeaProjectTestFixture> projectFixture =
  //      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(myTestName, myProjectRoot, true);
  //    IdeaProjectTestFixture ideaTestFixture = projectFixture.getFixture();
  //    myFixture = JavaTestFixtureFactory
  //      .getFixtureFactory().createCodeInsightFixture(ideaTestFixture, new MyTempDirFixture(myProjectRoot.toFile(), mySourceContentRoot));
  //    File srcContentRoot = myProjectRoot.toAbsolutePath().resolve(mySourceContentRoot).toFile();
  //    srcContentRoot.mkdirs();
  //    projectFixture
  //      .addModule(JavaModuleFixtureBuilder.class)
  //      .addSourceContentRoot(srcContentRoot.getAbsolutePath());
  //    ((CodeInsightTestFixtureImpl)myFixture).disableEdtQueueReplacement();
  //    try {
  //      myFixture.setUp();
  //    }
  //    catch (Exception e) {
  //      throw new RuntimeException(e);
  //    }
  //    LanguageLevelProjectExtension.getInstance(myFixture.getProject()).setLanguageLevel(LanguageLevel.JDK_1_6);
  //  }
  //
  //  void execute(@NotNull Consumer<JavaCodeInsightTestFixture> execution) {
  //    execution.accept(myFixture);
  //  }
  //
  //  void stop() {
  //    assertNotNull(myFixture);
  //    try {
  //      myFixture.tearDown();
  //    }
  //    catch (Throwable e) {
  //
  //      throw new RuntimeException(e);
  //    }
  //    finally {
  //      myFixture = null;
  //      WriteAction.run(() -> {
  //        Disposer.dispose(IdeaTestApplication.getInstance());
  //      });
  //    }
  //  }
  //}
  //
  //private static class MyTempDirFixture extends TempDirTestFixtureImpl {
  //  private final File myTempHome;
  //  private final File mySourceContentRoot;
  //
  //  private MyTempDirFixture(File home, String sourceContentRoot) {myTempHome = home;
  //    mySourceContentRoot = new File(myTempHome, sourceContentRoot);
  //    if (!mySourceContentRoot.exists()) {
  //      mySourceContentRoot.mkdirs();
  //    }
  //  }
  //
  //  @Override
  //  protected File getTempHome() {
  //    return myTempHome;
  //  }
  //
  //  @NotNull
  //  @Override
  //  protected File doCreateTempDirectory() {
  //    return mySourceContentRoot;
  //  }
  //
  //  @Override
  //  protected boolean deleteOnTearDown() {
  //    return false;
  //  }
  //}
}
