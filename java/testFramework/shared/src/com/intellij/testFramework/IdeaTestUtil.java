// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.codehaus.groovy.tools.FileSystemCompiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.junit.Assert;
import org.junit.Assume;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertTrue;

@TestOnly
public final class IdeaTestUtil {
  private static final String MOCK_JDK_DIR_NAME_PREFIX = "mockJDK-";
  private static final String MOCK_JDK_GROUP_ID = "mockjdk-base-java";

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void printDetectedPerformanceTimings() {
    System.out.println(Timings.getStatistics());
  }

  public static void withLevel(final @NotNull Module module, @NotNull LanguageLevel level, final @NotNull Runnable r) {
    final LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(module.getProject());

    final LanguageLevel projectLevel = projectExt.getLanguageLevel();
    final LanguageLevel moduleLevel = LanguageLevelUtil.getCustomLanguageLevel(module);
    final Application application = ApplicationManager.getApplication();
    try {
      application.invokeAndWait(() -> {
        application.runWriteAction(() -> projectExt.setLanguageLevel(level));
      });
      setModuleLanguageLevel(module, level);
      IndexingTestUtil.waitUntilIndexesAreReady(module.getProject());
      r.run();
    }
    finally {
      setModuleLanguageLevel(module, moduleLevel);
      application.invokeAndWait(() -> {
        application.runWriteAction(() -> projectExt.setLanguageLevel(projectLevel));
      });
      IndexingTestUtil.waitUntilIndexesAreReady(module.getProject());
    }
  }

  public static void setProjectLanguageLevel(@NotNull Project project, @NotNull LanguageLevel level, @NotNull Disposable disposable) {
    LanguageLevel oldLevel = setProjectLanguageLevel(project, level);
    Disposer.register(disposable, () -> {
      setProjectLanguageLevel(project, oldLevel);
    });
  }

  public static LanguageLevel setProjectLanguageLevel(@NotNull Project project, @NotNull LanguageLevel level) {
    LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(project);
    LanguageLevel oldLevel = projectExt.getLanguageLevel();
    Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> {
      application.runWriteAction(() -> projectExt.setLanguageLevel(level));
    });
    IndexingTestUtil.waitUntilIndexesAreReady(project);
    return oldLevel;
  }

  public static LanguageLevel setModuleLanguageLevel(@NotNull Module module, @Nullable LanguageLevel level) {
    LanguageLevel prev = LanguageLevelUtil.getCustomLanguageLevel(module);
    ModuleRootModificationUtil.updateModel(module, model -> model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level));
    IndexingTestUtil.waitUntilIndexesAreReady(module.getProject());
    return prev;
  }

  public static void setModuleLanguageLevel(@NotNull Module module, @NotNull LanguageLevel level, @NotNull Disposable parentDisposable) {
    LanguageLevel prev = setModuleLanguageLevel(module, level);
    Disposer.register(parentDisposable, () -> {
      setModuleLanguageLevel(module, prev);
    });
  }

  public static @NotNull Sdk getMockJdk(@NotNull JavaVersion version) {
    int mockJdk = version.feature >= 21 ? 21 :
                  version.feature >= 11 ? 11 :
                  version.feature >= 9 ? 9 :
                  version.feature >= 7 ? version.feature :
                  version.feature >= 5 ? 7 :
                  4;
    String sdkName = getMockJdkName(version);
    if (mockJdk > 9) {
      return createMockJdkFromRepository(sdkName, mockJdk);
    }
    String path = getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1." + mockJdk).getPath();
    return createMockJdk(sdkName, path);
  }

  public static @NotNull String getMockJdkName(@NotNull JavaVersion version) {
    return "java " + version;
  }

  private static Sdk createMockJdkFromRepository(String name, int version) {
    List<RemoteRepositoryDescription> repos = MavenDependencyUtil.getRemoteRepositoryDescriptions();
    String coordinates = "org.jetbrains.mockjdk:"+ MOCK_JDK_GROUP_ID + ":" + version + ".0";
    RepositoryLibraryProperties libraryProperties = new RepositoryLibraryProperties(coordinates, false);
    Collection<OrderRoot> roots =
      JarRepositoryManager.loadDependenciesModal(ProjectManager.getInstance().getDefaultProject(), libraryProperties, false, false, null,
                                                 repos);
    if (roots.isEmpty()) {
      throw new IllegalStateException("MockJDK artifact not found: " + coordinates);
    }
    if (roots.size() != 1) {
      throw new IllegalStateException("Unexpected number of roots: " + coordinates + ": " + roots);
    }
    VirtualFile file = roots.iterator().next().getFile();
    String canonicalPath = file.getCanonicalPath();
    if (canonicalPath == null) {
      throw new IllegalStateException("No canonical path found for " + file);
    }
    return createMockJdk(name, canonicalPath);
  }

  /**
   * @param path Mock JDK path
   * @return Sdk created from the known legacy filesystem path to a mockJDK, which doesn't exist anymore,
   * because the corresponding SDK is created from the artifacts repository now. Can be used as a
   * bridge for older code which identifies SDKs by file system path. Returns null, if the path
   * is not recognized as a legacy SDK path.
   */
  public static @Nullable Sdk createMockJdkFromLegacyPath(@NotNull String path) {
    if (Path.of(path).getFileName().toString().equals("mockJDK-11")) {
      return getMockJdk(JavaVersion.compose(11));
    }
    return null;
  }

  public static @NotNull Sdk createMockJdk(
    @NotNull String name,
    @NotNull String path,
    Consumer<SdkModificator> sdkModificatorCustomizer
  ) {
    Sdk fromLegacyPath = createMockJdkFromLegacyPath(path);
    if (fromLegacyPath != null) {
      return fromLegacyPath;
    }
    JavaSdk javaSdk = JavaSdk.getInstance();
    if (javaSdk == null) {
      throw new AssertionError("The test uses classes from Java plugin but Java plugin wasn't loaded; make sure that Java plugin " +
                               "classes are included into classpath and that the plugin isn't disabled " +
                               "by 'idea.load.plugins' or 'idea.load.plugins.id' system properties");
    }
    Sdk sdk = ProjectJdkTable.getInstance().createSdk(name, JavaSdk.getInstance());
    SdkModificator sdkModificator = sdk.getSdkModificator();

    String sdkPath;
    if (path.endsWith(".jar!/")) {
      sdkPath = PathUtil.getParentPath(path);
      sdkModificator.addRoot("jar://"+path, OrderRootType.CLASSES);
    } else {
      sdkPath = PathUtil.toSystemIndependentName(path);
      File[] jars = new File(path, "jre/lib").listFiles(f -> f.getName().endsWith(".jar"));
      if (jars != null) {
        for (File jar : jars) {
          sdkModificator.addRoot("jar://"+PathUtil.toSystemIndependentName(jar.getPath())+"!/", OrderRootType.CLASSES);
        }
      }
    }
    sdkModificator.setHomePath(sdkPath);
    sdkModificator.setVersionString(name);
    if (sdkModificatorCustomizer != null) {
      sdkModificatorCustomizer.accept(sdkModificator);
    }

    // only Mock JDKs 1.4/1.7 have src.zip
    if (path.endsWith("mockJDK-1.7") || path.endsWith("mockJDK-1.4")) {
      if (new File(path, "src.zip").exists()) {
        sdkModificator.addRoot("jar://"+PathUtil.toSystemIndependentName(path)+"/src.zip!/", OrderRootType.SOURCES);
      }
    }

    JavaSdkImpl.attachJdkAnnotations(sdkModificator);
    Application application = ApplicationManager.getApplication();
    Runnable runnable = () -> sdkModificator.commitChanges();
    if (application.isDispatchThread()) {
      application.runWriteAction(runnable);
    } else {
      application.invokeAndWait(() -> application.runWriteAction(runnable));
    }
    return sdk;
  }

  public static @NotNull Sdk createMockJdk(@NotNull String name, @NotNull String path) {
    return createMockJdk(name, path, null);
  }

  /// It's JDK 1.4, not 14.
  public static @NotNull Sdk getMockJdk14() {
    return getMockJdk(JavaVersion.compose(4));
  }

  /// It's JDK 1.6, not 16.
  public static @NotNull Sdk getMockJdk16() {
    return getMockJdk(JavaVersion.compose(6));
  }

  /// It's JDK 1.7, not 17.
  public static @NotNull Sdk getMockJdk17() {
    return getMockJdk(JavaVersion.compose(7));
  }

  /// It's JDK 1.7, not 17.
  public static @NotNull Sdk getMockJdk17(@NotNull String name) {
    return createMockJdk(name, getMockJdk17Path().getPath());
  }

  /// It's JDK 1.8, not 18.
  public static @NotNull Sdk getMockJdk18() {
    return getMockJdk(JavaVersion.compose(8));
  }

  public static @NotNull Sdk getMockJdk9() {
    return getMockJdk(JavaVersion.compose(9));
  }
  
  public static @NotNull Sdk getMockJdk11() {
    return getMockJdk(JavaVersion.compose(11));
  }

  public static @NotNull Sdk getMockJdk21() {
    return getMockJdk(JavaVersion.compose(21));
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

  public static String getMockJdkVersion(@NotNull String path) {
    String name = PathUtil.getFileName(path);
    return name.startsWith(MOCK_JDK_DIR_NAME_PREFIX) ? "java " + StringUtil.trimStart(name, MOCK_JDK_DIR_NAME_PREFIX) : null;
  }

  private static @NotNull File getPathForJdkNamed(@NotNull String name) {
    // Bazel-provided test dependencies, from runfiles tree
    if (BazelTestUtil.isUnderBazelTest()) {
      return BazelTestUtil.findRunfilesDirectoryUnderCommunityOrUltimate("java/" + name).toFile();
    }

    return new File(PlatformTestUtil.getCommunityPath(), "java/" + name);
  }

  public static void addWebJarsToModule(@NotNull Module module) {
    ModuleRootModificationUtil.updateModel(module, IdeaTestUtil::addWebJarsToModule);
    IndexingTestUtil.waitUntilIndexesAreReady(module.getProject());
  }

  public static void addWebJarsToModule(@NotNull ModifiableRootModel model) {
    MavenDependencyUtil.addFromMaven(model, "javax.servlet.jsp:javax.servlet.jsp-api:2.3.3");
    MavenDependencyUtil.addFromMaven(model, "javax.servlet:javax.servlet-api:3.1.0");
    IndexingTestUtil.waitUntilIndexesAreReady(model.getProject());
  }

  public static void setTestVersion(@NotNull JavaSdkVersion testVersion, @NotNull Module module, @NotNull Disposable parentDisposable) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    Assert.assertNotNull(sdk);
    String oldVersionString = sdk.getVersionString();

    setSdkVersion(sdk, testVersion.getDescription());
    IndexingTestUtil.waitUntilIndexesAreReady(module.getProject());

    Assert.assertSame(testVersion, JavaSdk.getInstance().getVersion(sdk));
    Disposer.register(parentDisposable, () -> {
      setSdkVersion(sdk, oldVersionString);
      IndexingTestUtil.waitUntilIndexesAreReady(module.getProject());
    });
  }

  private static void setSdkVersion(@NotNull Sdk sdk, @Nullable String sdkVersion) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setVersionString(sdkVersion);
    ApplicationManager.getApplication().runWriteAction(() -> {
      sdkModificator.commitChanges();
    });
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
        FileSystemCompiler.commandLineCompile(ArrayUtilRt.toStringArray(args));
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
