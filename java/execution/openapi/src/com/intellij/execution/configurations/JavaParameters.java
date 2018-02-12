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
package com.intellij.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.util.PathsList;
import com.intellij.util.text.VersionComparatorUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class JavaParameters extends SimpleJavaParameters {
  private static final Logger LOG = Logger.getInstance(JavaParameters.class);
  private static final String JAVA_LIBRARY_PATH_PROPERTY = "java.library.path";
  public static final DataKey<JavaParameters> JAVA_PARAMETERS = DataKey.create("javaParameters");

  public String getJdkPath() throws CantRunException {
    final Sdk jdk = getJdk();
    if (jdk == null) {
      throw new CantRunException(ExecutionBundle.message("no.jdk.specified..error.message"));
    }

    final VirtualFile jdkHome = jdk.getHomeDirectory();
    if (jdkHome == null) {
      throw new CantRunException(ExecutionBundle.message("home.directory.not.specified.for.jdk.error.message"));
    }
    return jdkHome.getPresentableUrl();
  }

  public static final int JDK_ONLY = 0x1;
  public static final int CLASSES_ONLY = 0x2;
  public static final int TESTS_ONLY = 0x4;
  public static final int INCLUDE_PROVIDED = 0x8;
  public static final int JDK_AND_CLASSES = JDK_ONLY | CLASSES_ONLY;
  public static final int JDK_AND_CLASSES_AND_TESTS = JDK_ONLY | CLASSES_ONLY | TESTS_ONLY;
  public static final int CLASSES_AND_TESTS = CLASSES_ONLY | TESTS_ONLY;
  public static final int JDK_AND_CLASSES_AND_PROVIDED = JDK_ONLY | CLASSES_ONLY | INCLUDE_PROVIDED;

  public void configureByModule(final Module module,
                                @MagicConstant(valuesFromClass = JavaParameters.class) final int classPathType,
                                final Sdk jdk) throws CantRunException {
    if ((classPathType & JDK_ONLY) != 0) {
      if (jdk == null) {
        throw CantRunException.noJdkConfigured();
      }
      setJdk(jdk);
    }

    if ((classPathType & CLASSES_ONLY) == 0) {
      return;
    }

    setDefaultCharset(module.getProject());
    configureEnumerator(OrderEnumerator.orderEntries(module).recursively(), classPathType, jdk).collectPaths(getClassPath());
    configureJavaLibraryPath(OrderEnumerator.orderEntries(module).recursively());
  }

  private void configureJavaLibraryPath(OrderEnumerator enumerator) {
    PathsList pathsList = new PathsList();
    enumerator.runtimeOnly().withoutSdk().roots(NativeLibraryOrderRootType.getInstance()).collectPaths(pathsList);
    if (!pathsList.getPathList().isEmpty()) {
      ParametersList vmParameters = getVMParametersList();
      if (vmParameters.hasProperty(JAVA_LIBRARY_PATH_PROPERTY)) {
        LOG.info(JAVA_LIBRARY_PATH_PROPERTY + " property is already specified, " +
                 "native library paths from dependencies (" + pathsList.getPathsString() + ") won't be added");
      }
      else {
        vmParameters.addProperty(JAVA_LIBRARY_PATH_PROPERTY, pathsList.getPathsString());
      }
    }
  }

  public void setDefaultCharset(final Project project) {
    Charset encoding = EncodingProjectManager.getInstance(project).getDefaultCharset();
    setCharset(encoding);
  }

  public void configureByModule(final Module module,
                                @MagicConstant(valuesFromClass = JavaParameters.class) final int classPathType) throws CantRunException {
    configureByModule(module, classPathType, getValidJdkToRunModule(module, (classPathType & TESTS_ONLY) == 0));
  }

  /** @deprecated use {@link #getValidJdkToRunModule(Module, boolean)} instead */
  public static Sdk getModuleJdk(final Module module) throws CantRunException {
    return getValidJdkToRunModule(module, false);
  }

  @NotNull
  public static Sdk getValidJdkToRunModule(final Module module, boolean productionOnly) throws CantRunException {
    Sdk jdk = getJdkToRunModule(module, productionOnly);
    if (jdk == null) {
      throw CantRunException.noJdkForModule(module);
    }
    final VirtualFile homeDirectory = jdk.getHomeDirectory();
    if (homeDirectory == null || !homeDirectory.isValid()) {
      throw CantRunException.jdkMisconfigured(jdk, module);
    }
    return jdk;
  }

  @Nullable
  public static Sdk getJdkToRunModule(Module module, boolean productionOnly) {
    final Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
    if (moduleSdk == null) {
      return null;
    }

    final Set<Sdk> sdksFromDependencies = new LinkedHashSet<>();
    OrderEnumerator enumerator = OrderEnumerator.orderEntries(module).runtimeOnly().recursively();
    if (productionOnly) {
      enumerator = enumerator.productionOnly();
    }
    enumerator.forEachModule(module1 -> {
      Sdk sdk = ModuleRootManager.getInstance(module1).getSdk();
      if (sdk != null && sdk.getSdkType().equals(moduleSdk.getSdkType())) {
        sdksFromDependencies.add(sdk);
      }
      return true;
    });
    return findLatestVersion(moduleSdk, sdksFromDependencies);
  }

  @NotNull
  private static Sdk findLatestVersion(@NotNull Sdk mainSdk, @NotNull Set<Sdk> sdks) {
    Sdk result = mainSdk;
    for (Sdk sdk : sdks) {
      if (VersionComparatorUtil.compare(result.getVersionString(), sdk.getVersionString()) < 0) {
        result = sdk;
      }
    }
    return result;
  }

  public void configureByProject(Project project,
                                 @MagicConstant(valuesFromClass = JavaParameters.class) int classPathType,
                                 Sdk jdk) throws CantRunException {
    if ((classPathType & JDK_ONLY) != 0) {
      if (jdk == null) {
        throw CantRunException.noJdkConfigured();
      }
      setJdk(jdk);
    }

    if ((classPathType & CLASSES_ONLY) == 0) {
      return;
    }
    setDefaultCharset(project);
    configureEnumerator(OrderEnumerator.orderEntries(project).runtimeOnly(), classPathType, jdk).collectPaths(getClassPath());
    configureJavaLibraryPath(OrderEnumerator.orderEntries(project));
  }

  private static OrderRootsEnumerator configureEnumerator(OrderEnumerator enumerator, int classPathType, Sdk jdk) {
    if ((classPathType & INCLUDE_PROVIDED) == 0) {
      enumerator = enumerator.runtimeOnly();
    }
    if ((classPathType & JDK_ONLY) == 0) {
      enumerator = enumerator.withoutSdk();
    }
    if ((classPathType & TESTS_ONLY) == 0) {
      enumerator = enumerator.productionOnly();
    }
    OrderRootsEnumerator rootsEnumerator = enumerator.classes();
    if ((classPathType & JDK_ONLY) != 0) {
      rootsEnumerator = rootsEnumerator.usingCustomRootProvider(
        e -> e instanceof JdkOrderEntry ? jdkRoots(jdk) : e.getFiles(OrderRootType.CLASSES));
    }
    return rootsEnumerator;
  }

  private static VirtualFile[] jdkRoots(Sdk jdk) {
    return Arrays.stream(jdk.getRootProvider().getFiles(OrderRootType.CLASSES))
      .filter(f -> !JrtFileSystem.isModuleRoot(f))
      .toArray(VirtualFile[]::new);
  }
}