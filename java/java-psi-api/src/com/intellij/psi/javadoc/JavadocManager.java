// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.javadoc;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JavadocManager {
  final class SERVICE {
    private SERVICE() { }

    public static JavadocManager getInstance(@NotNull Project project) {
      return ServiceManager.getService(project, JavadocManager.class);
    }
  }

  JavadocTagInfo @NotNull [] getTagInfos(PsiElement context);

  @Nullable
  JavadocTagInfo getTagInfo(String name);
}