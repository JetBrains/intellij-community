// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaSdk extends SdkType implements JavaSdkType {
  public static JavaSdk getInstance() {
    return EP_NAME.findExtension(JavaSdk.class);
  }

  public JavaSdk(@NotNull String name) {
    super(name);
  }

  @Override
  public boolean isRelevantForFile(@NotNull Project project, @NotNull VirtualFile file) {
    return PsiManager.getInstance(project).findFile(file) instanceof PsiClassOwner;
  }

  public final @NotNull Sdk createJdk(@NotNull String jdkName, @NotNull String jreHome) {
    return createJdk(jdkName, jreHome, true);
  }

  public abstract @NotNull Sdk createJdk(@NotNull String jdkName, @NotNull String home, boolean isJre);

  public abstract @Nullable JavaSdkVersion getVersion(@NotNull Sdk sdk);

  public abstract boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version);
}
