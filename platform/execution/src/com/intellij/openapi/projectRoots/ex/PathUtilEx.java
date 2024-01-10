// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public final class PathUtilEx {

  @Nullable
  public static Sdk getAnyJdk(@NotNull Project project) {
    return chooseJdk(project, Arrays.asList(ModuleManager.getInstance(project).getModules()));
  }

  /**
   * @deprecated the meaning of this method is unclear, choose a JDK using explicit criteria instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  @Nullable
  private static Sdk chooseJdk(@NotNull Project project, @NotNull Collection<? extends Module> modules) {
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectJdk != null && projectJdk.getSdkType() instanceof JavaSdkType) {
      return projectJdk;
    }
    List<Sdk> jdks = ContainerUtil.mapNotNull(modules, module -> {
      if (module == null) return null;
      Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) return null;
      return sdk;
    });
    if (jdks.isEmpty()) {
      return null;
    }
    return jdks.stream().max(Comparator.comparing(jdk -> JavaVersion.tryParse(jdk.getVersionString()), Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
  }
}
