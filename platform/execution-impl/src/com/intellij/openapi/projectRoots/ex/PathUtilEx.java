// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public final class PathUtilEx {

  @Nullable
  public static Sdk getAnyJdk(@NotNull Project project) {
    return chooseJdk(project, Arrays.asList(ModuleManager.getInstance(project).getModules()));
  }

  @Nullable
  public static Sdk chooseJdk(@NotNull Project project, @NotNull Collection<? extends Module> modules) {
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectJdk != null) {
      return projectJdk;
    }
    return chooseJdk(modules);
  }

  @Nullable
  public static Sdk chooseJdk(@NotNull Collection<? extends Module> modules) {
    List<Sdk> jdks = ContainerUtil.mapNotNull(modules, module -> module == null ? null : ModuleRootManager.getInstance(module).getSdk());
    if (jdks.isEmpty()) {
      return null;
    }
    jdks.sort(ComparatorUtil.compareBy(jdk -> StringUtil.notNullize(jdk.getVersionString()), String.CASE_INSENSITIVE_ORDER));
    return jdks.get(jdks.size() - 1);
  }
}
