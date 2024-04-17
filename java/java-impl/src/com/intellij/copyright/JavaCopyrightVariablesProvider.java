// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.copyright;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.maddyhome.idea.copyright.pattern.CopyrightVariablesProvider;
import com.maddyhome.idea.copyright.pattern.FileInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class JavaCopyrightVariablesProvider extends CopyrightVariablesProvider {
  @Override
  public void collectVariables(@NotNull Map<String, Object> context, Project project, Module module, final @NotNull PsiFile file) {
    if (file instanceof PsiClassOwner) {
      final FileInfo info = new FileInfo(file) {
        @Override
        public String getClassName() {
          if (file instanceof PsiJavaFile) {
            return ((PsiJavaFile)file).getClasses()[0].getName();
          }
          else {
            return super.getClassName();
          }
        }

        @Override
        public String getQualifiedClassName() {
          if (file instanceof PsiJavaFile) {
            return ((PsiJavaFile)file).getClasses()[0].getQualifiedName();
          } else {
            return super.getQualifiedClassName();
          }
        }
      };
      context.put("file", info);
    }
  }
}
