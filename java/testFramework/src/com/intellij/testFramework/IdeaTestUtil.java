// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;
import org.junit.Assume;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@TestOnly
public class IdeaTestUtil extends PlatformTestUtil {
  private static final String MOCK_JDK_DIR_NAME_PREFIX = "mockJDK-";

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void printDetectedPerformanceTimings() {
    System.out.println(Timings.getStatistics());
  }

  public static void withLevel(@NotNull final Module module, @NotNull LanguageLevel level, @NotNull final Runnable r) {
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

  @NotNull
  public static Sdk getMockJdk(@NotNull JavaVersion version) {
    int mockJdk = version.feature >= 11 ? 11 :
                  version.feature >= 9 ? 9 :
                  version.feature >= 7 ? version.feature :
                  version.feature >= 5 ? 7 :
                  4;
    String path = getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + (mockJdk < 11 ? "1." : "") + mockJdk).getPath();
    return createMockJdk("java " + version, path);
  }

  @NotNull
  private static Sdk createMockJdk(@NotNull String name, @NotNull String path) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    if (javaSdk == null) {
      throw new AssertionError("The test uses classes from Java plugin but Java plugin wasn't loaded; make sure that Java plugin " +
                               "classes are included into classpath and that the plugin isn't disabled by using 'idea.load.plugins', 'idea.load.plugins.id', 'idea.load.plugins.category' system properties");
    }
    return ((JavaSdkImpl)javaSdk).createMockJdk(name, path, false);
  }

  @NotNull
  public static Sdk getMockJdk14() {
    return getMockJdk(JavaVersion.compose(4));
  }

  @NotNull
  public static Sdk getMockJdk17() {
    return getMockJdk(JavaVersion.compose(7));
  }

  @NotNull
  public static Sdk getMockJdk17(@NotNull String name) {
    return createMockJdk(name, getMockJdk17Path().getPath());
  }

  @NotNull
  public static Sdk getMockJdk18() {
    return getMockJdk(JavaVersion.compose(8));
  }

  @NotNull
  public static Sdk getMockJdk9() {
    return getMockJdk(JavaVersion.compose(9));
  }

  @NotNull
  public static File getMockJdk14Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.4");
  }

  @NotNull
  public static File getMockJdk17Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.7");
  }

  @NotNull
  public static File getMockJdk18Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.8");
  }

  @NotNull
  public static File getMockJdk9Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.9");
  }

  public static String getMockJdkVersion(@NotNull String path) {
    String name = PathUtil.getFileName(path);
    if (name.startsWith(MOCK_JDK_DIR_NAME_PREFIX)) {
      return "java " + StringUtil.trimStart(name, MOCK_JDK_DIR_NAME_PREFIX);
    }
    return null;
  }

  @NotNull
  private static File getPathForJdkNamed(@NotNull String name) {
    return new File(PathManager.getCommunityHomePath(), "java/" + name);
  }

  @NotNull
  public static Sdk getWebMockJdk17() {
    Sdk jdk = getMockJdk17();
    jdk=addWebJarsTo(jdk);
    return jdk;
  }

  @NotNull
  @Contract(pure=true)
  public static Sdk addWebJarsTo(@NotNull Sdk jdk) {
    try {
      jdk = (Sdk)jdk.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    SdkModificator sdkModificator = jdk.getSdkModificator();
    sdkModificator.addRoot(findJar("lib/jsp-api.jar"), OrderRootType.CLASSES);
    sdkModificator.addRoot(findJar("lib/servlet-api.jar"), OrderRootType.CLASSES);
    sdkModificator.commitChanges();
    return jdk;
  }

  @NotNull
  private static VirtualFile findJar(@NotNull String name) {
    String path = PathManager.getHomePath() + '/' + name;
    VirtualFile file = VfsTestUtil.findFileByCaseSensitivePath(path);
    VirtualFile jar = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    assert jar != null : "no .jar for: " + path;
    return jar;
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

  @NotNull
  public static String requireRealJdkHome() {
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

  @NotNull
  public static File findSourceFile(@NotNull String basePath) {
    File testFile = new File(basePath + ".java");
    if (!testFile.exists()) testFile = new File(basePath + ".groovy");
    if (!testFile.exists()) throw new IllegalArgumentException("No test source for " + basePath);
    return testFile;
  }

  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static void compileFile(@NotNull File source, @NotNull File out, @NotNull String... options) {
    Assert.assertTrue("source does not exist: " + source.getPath(), source.isFile());

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