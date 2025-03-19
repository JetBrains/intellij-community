// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class JdkPathMacro extends Macro implements PathMacro {
  @Override
  public @NotNull String getName() {
    return "JDKPath";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.jdk.path");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    final Sdk anyJdk = PathUtilEx.getAnyJdk(project);
    return sdkPath(anyJdk);
  }

  static @Nullable String sdkPath(@Nullable Sdk anyJdk) {
    if (anyJdk == null) {
      return null;
    }
    String jdkHomePath = PathUtil.getLocalPath(anyJdk.getHomeDirectory());
    if (jdkHomePath != null) {
      jdkHomePath = jdkHomePath.replace('/', File.separatorChar);
    }
    return jdkHomePath;
  }
}
