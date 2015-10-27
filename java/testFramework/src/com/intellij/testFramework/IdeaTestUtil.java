/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.List;

import static org.junit.Assume.assumeTrue;

public class IdeaTestUtil extends PlatformTestUtil {
  private static final String MOCK_JDK_DIR_NAME_PREFIX = "mockJDK-";

  public static void main(String[] args) {
    printDetectedPerformanceTimings();
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr"})
  public static void printDetectedPerformanceTimings() {
    System.out.println(Timings.getStatistics());
  }

  public static void withLevel(final Module module, final LanguageLevel level, final Runnable r) {
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

  public static void setModuleLanguageLevel(Module module, final LanguageLevel level) {
    final LanguageLevelModuleExtensionImpl
      modifiable = (LanguageLevelModuleExtensionImpl)LanguageLevelModuleExtensionImpl.getInstance(module).getModifiableModel(true);
    modifiable.setLanguageLevel(level);
    modifiable.commit();
  }

  public static Sdk getMockJdk17() {
    return getMockJdk17("java 1.7");
  }

  public static Sdk getMockJdk17(@NotNull String name) {
    return JavaSdk.getInstance().createJdk(name, getMockJdk17Path().getPath(), false);
  }

  public static Sdk getMockJdk18() {
    return JavaSdk.getInstance().createJdk("java 1.8", getMockJdk18Path().getPath(), false);
  }

  public static Sdk getMockJdk14() {
    return JavaSdk.getInstance().createJdk("java 1.4", getMockJdk14Path().getPath(), false);
  }

  public static File getMockJdk14Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.4");
  }

  public static File getMockJdk17Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.7");
  }

  public static File getMockJdk18Path() {
    return getPathForJdkNamed(MOCK_JDK_DIR_NAME_PREFIX + "1.8");
  }

  public static String getMockJdkVersion(String path) {
    String name = PathUtil.getFileName(path);
    if (name.startsWith(MOCK_JDK_DIR_NAME_PREFIX)) {
      return "java " + StringUtil.trimStart(name, MOCK_JDK_DIR_NAME_PREFIX);
    }
    return null;
  }

  private static File getPathForJdkNamed(String name) {
    File mockJdkCEPath = new File(PathManager.getHomePath(), "java/" + name);
    return mockJdkCEPath.exists() ? mockJdkCEPath : new File(PathManager.getHomePath(), "community/java/" + name);
  }

  public static Sdk getWebMockJdk17() {
    Sdk jdk = getMockJdk17();
    addWebJarsTo(jdk);
    return jdk;
  }

  public static void addWebJarsTo(@NotNull Sdk jdk) {
    SdkModificator sdkModificator = jdk.getSdkModificator();
    sdkModificator.addRoot(findJar("lib/jsp-api.jar"), OrderRootType.CLASSES);
    sdkModificator.addRoot(findJar("lib/servlet-api.jar"), OrderRootType.CLASSES);
    sdkModificator.commitChanges();
  }

  private static VirtualFile findJar(String name) {
    String path = PathManager.getHomePath() + '/' + name;
    VirtualFile file = VfsTestUtil.findFileByCaseSensitivePath(path);
    VirtualFile jar = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    assert jar != null : "no .jar for: " + path;
    return jar;
  }

  @TestOnly
  public static void setTestVersion(@NotNull final JavaSdkVersion testVersion, @NotNull Module module, @NotNull Disposable parentDisposable) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final Sdk sdk = rootManager.getSdk();
    final String oldVersionString = sdk.getVersionString();
    ((ProjectJdkImpl)sdk).setVersionString(testVersion.getDescription());
    assert JavaSdk.getInstance().getVersion(sdk) == testVersion;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        ((ProjectJdkImpl)sdk).setVersionString(oldVersionString);
      }
    });
  }


  @NotNull
  public static String requireRealJdkHome() {
    String javaHome = SystemProperties.getJavaHome();
    List<String> paths =
      ContainerUtil.packNullables(javaHome, new File(javaHome).getParent(), System.getenv("JDK_16_x64"), System.getenv("JDK_16"));
    for (String path : paths) {
      if (JdkUtil.checkForJdk(new File(path))) {
        return path;
      }
    }
    assumeTrue("Cannot find JDK, checked paths: " + paths, false);
    return null;
  }
}
