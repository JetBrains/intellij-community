// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JavadocManager {

  static JavadocManager getInstance(@NotNull Project project) {
    return project.getService(JavadocManager.class);
  }

  JavadocTagInfo @NotNull [] getTagInfos(PsiElement context);

  @Nullable
  JavadocTagInfo getTagInfo(String name);
}