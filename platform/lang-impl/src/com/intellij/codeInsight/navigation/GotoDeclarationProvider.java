// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.navigation.NavigationTarget;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

public interface GotoDeclarationProvider {

  ExtensionPointName<GotoDeclarationProvider> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.gotoDeclaration");

  void collectTargets(@NotNull Project project,
                      @NotNull Editor editor,
                      @NotNull PsiFile file,
                      @NotNull Consumer<? super NavigationTarget> consumer);

  @NotNull
  static Collection<NavigationTarget> collectAllTargets(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    LinkedHashSet<NavigationTarget> result = new LinkedHashSet<>();
    for (GotoDeclarationProvider extension : EP_NAME.getExtensions()) {
      extension.collectTargets(project, editor, file, result::add);
    }
    return result;
  }
}
