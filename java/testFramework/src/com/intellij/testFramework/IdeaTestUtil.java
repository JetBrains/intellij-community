// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.projectRoots.impl.MockSdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.*;
import org.junit.Assert;
import org.junit.Assume;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

@TestOnly
public final class IdeaTestUtil {
  private static final String MOCK_JDK_DIR_NAME_PREFIX = "mockJDK-";

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void printDetectedPerformanceTimings() {
    System.out.println(Timings.getStatistics());
  }

  public static void withLevel(final @NotNull Module module, @NotNull LanguageLevel level, final @NotNull Runnable r) {
    final LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(module.getProject());

    final LanguageLevel projectLevel = projectExt.getLanguageLevel();
    final LanguageLevel moduleLevel = LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
    try {
      projectExt.setLanguageLevel(level);
      setModuleLanguageLevel(module, level);
      r.run();
    }
    finally {
      setModuleLanguageLevel(module, moduleLevel);
      projectExt.setLanguageLevel(projectLevel);
    }
  }

  public static void setModuleLanguageLevel(@NotNull Module module, @Nullable LanguageLevel level) {
    ModuleRootModificationUtil.updateModel(module, (model) -> {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
    });
  }

  public static void setModuleLanguageLevel(@NotNull Module module, @NotNull LanguageLevel level, @NotNull Disposable parentDisposable) {
    LanguageLevel prev = LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
    setModuleLanguageLevel(module, level);
    Disposer.register(parentDisposable, () -> setModuleLanguageLevel(module, prev));
  }

  public static @NotNull Sdk getMockJdk(@NotNull JavaVersion version) {
    int mockJdk = version.feature >= 11 ? 11 :
                  version.feature >= 9 ? 9 :
                  version.feature >= 7 ? version.feature :
                  version.feature >= 5 ? 7 :
                  4;
    String path = getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + (mockJdk < 11 ? "1." : "") + mockJdk).getPath();
    return createMockJdk("java " + version, path);
  }

  public static @NotNull Sdk createMockJdk(@NotNull String name, @NotNull String path) {
    return createMockJdk(name, path, false);
  }

  public static @NotNull Sdk createMockJdk(@NotNull String name, @NotNull String path, boolean isJre) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    if (javaSdk == null) {
      throw new AssertionError("The test uses classes from Java plugin but Java plugin wasn't loaded; make sure that Java plugin " +
                               "classes are included into classpath and that the plugin isn't disabled by using 'idea.load.plugins', " +
                               "'idea.load.plugins.id', 'idea.load.plugins.category' system properties");
    }

    MultiMap<OrderRootType, VirtualFile> roots = MultiMap.create();
    SdkModificator sdkModificator = new SdkModificator() {
      @NotNull
      @Override public String getName() { throw new UnsupportedOperationException(); }
      @Override public void setName(@NotNull String name1) { throw new UnsupportedOperationException(); }
      @Override public String getHomePath() { throw new UnsupportedOperationException(); }
      @Override public void setHomePath(String path1) { throw new UnsupportedOperationException(); }
      @Override public String getVersionString() { throw new UnsupportedOperationException(); }
      @Override public void setVersionString(String versionString) { throw new UnsupportedOperationException(); }
      @Override public SdkAdditionalData getSdkAdditionalData() { throw new UnsupportedOperationException(); }
      @Override public void setSdkAdditionalData(SdkAdditionalData data) { throw new UnsupportedOperationException(); }
      @Override public VirtualFile @NotNull [] getRoots(@NotNull OrderRootType rootType) { return roots.get(rootType).toArray(VirtualFile.EMPTY_ARRAY); }
      @Override public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) { throw new UnsupportedOperationException(); }
      @Override public void removeRoots(@NotNull OrderRootType rootType) { throw new UnsupportedOperationException(); }
      @Override public void removeAllRoots() { throw new UnsupportedOperationException(); }
      @Override public void commitChanges() { throw new UnsupportedOperationException(); }
      @Override public boolean isWritable() { throw new UnsupportedOperationException(); }

      @Override
      public void addRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
        roots.putValue(rootType, root);
      }
    };

    Path jdkHomeFile = Path.of(path);
    JavaSdkImpl.addClasses(jdkHomeFile, sdkModificator, isJre);
    JavaSdkImpl.addSources(jdkHomeFile, sdkModificator);
    JavaSdkImpl.attachJdkAnnotations(sdkModificator);

    return new MockSdk(name, PathUtil.toSystemIndependentName(path), name, roots, () -> JavaSdk.getInstance());
  }

  public static @NotNull Sdk getMockJdk14() {
    return getMockJdk(JavaVersion.compose(4));
  }

  public static @NotNull Sdk getMockJdk16() {
    return getMockJdk(JavaVersion.compose(6));
  }

  public static @NotNull Sdk getMockJdk17() {
    return getMockJdk(JavaVersion.compose(7));
  }

  public static @NotNull Sdk getMockJdk17(@NotNull String name) {
    return createMockJdk(name, getMockJdk17Path().getPath());
  }

  public static @NotNull Sdk getMockJdk18() {
    return getMockJdk(JavaVersion.compose(8));
  }

  public static @NotNull Sdk getMockJdk9() {
    return getMockJdk(JavaVersion.compose(9));
  }
  
  public static @NotNull Sdk getMockJdk11() {
    return getMockJdk(JavaVersion.compose(11));
  }

  public static @NotNull File getMockJdk14Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.4");
  }

  public static @NotNull File getMockJdk17Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.7");
  }

  public static @NotNull File getMockJdk18Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.8");
  }

  public static @NotNull File getMockJdk9Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.9");
  }

  public static String getMockJdkVersion(@NotNull String path) {
    String name = PathUtil.getFileName(path);
    if (name.startsWith(MOCK_JDK_DIR_NAME_PREFIX)) {
      return "java " + StringUtil.trimStart(name, MOCK_JDK_DIR_NAME_PREFIX);
    }
    return null;
  }

  private static @NotNull File getPathForJdkNamed(@NotNull String name) {
    return new File(PathManager.getCommunityHomePath(), "java/" + name);
  }

  public static void addWebJarsToModule(@NotNull Module module) {
    ModuleRootModificationUtil.updateModel(module, IdeaTestUtil::addWebJarsToModule);
  }

  public static void addWebJarsToModule(@NotNull ModifiableRootModel model) {
    MavenDependencyUtil.addFromMaven(model, "javax.servlet.jsp:javax.servlet.jsp-api:2.3.3");
    MavenDependencyUtil.addFromMaven(model, "javax.servlet:javax.servlet-api:3.1.0");
  }

  public static void setTestVersion(@NotNull JavaSdkVersion testVersion, @NotNull Module module, @NotNull Disposable parentDisposable) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    Assert.assertNotNull(sdk);
    String oldVersionString = sdk.getVersionString();

    // hack
    ((SdkModificator)sdk).setVersionString(testVersion.getDescription());

    Assert.assertSame(testVersion, JavaSdk.getInstance().getVersion(sdk));
    Disposer.register(parentDisposable, () -> ((SdkModificator)sdk).setVersionString(oldVersionString));
  }

  public static @NotNull String requireRealJdkHome() {
    String javaHome = SystemProperties.getJavaHome();
    List<String> paths =
      ContainerUtil.packNullables(javaHome, new File(javaHome).getParent(), System.getenv("JDK_16_x64"), System.getenv("JDK_16"));
    for (String path : paths) {
      if (JdkUtil.checkForJdk(path)) {
        return path;
      }
    }
    //noinspection ConstantConditions
    Assume.assumeTrue("Cannot find JDK, checked paths: " + paths, false);
    return null;
  }

  public static @NotNull File findSourceFile(@NotNull String basePath) {
    File testFile = new File(basePath + ".java");
    if (!testFile.exists()) testFile = new File(basePath + ".groovy");
    if (!testFile.exists()) throw new IllegalArgumentException("No test source for " + basePath);
    return testFile;
  }

  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static void compileFile(@NotNull File source, @NotNull File out, String @NotNull ... options) {
    assertTrue("source does not exist: " + source.getPath(), source.isFile());

    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(out.getAbsolutePath());
    ContainerUtil.addAll(args, options);
    args.add(source.getAbsolutePath());

    if (source.getName().endsWith(".groovy")) {
      try {
        org.codehaus.groovy.tools.FileSystemCompiler.commandLineCompile(ArrayUtilRt.toStringArray(args));
      }
      catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    else {
      int result = com.sun.tools.javac.Main.compile(ArrayUtilRt.toStringArray(args));
      if (result != 0) throw new IllegalStateException("javac failed with exit code " + result);
    }
  }
}
