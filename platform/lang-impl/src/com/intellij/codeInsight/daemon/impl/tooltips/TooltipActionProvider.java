// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.tooltips;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.TooltipAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Provides actions for error tooltips
 *
 * @see com.intellij.codeInsight.daemon.impl.DaemonTooltipActionProvider
 */
public interface TooltipActionProvider {
  ExtensionPointName<TooltipActionProvider> EP_NAME = ExtensionPointName.create("com.intellij.daemon.tooltipActionProvider");

  String SHOW_FIXES_KEY = "tooltips.show.actions.in.key";
  boolean SHOW_FIXES_DEFAULT_VALUE = true;

  @Nullable
  TooltipAction getTooltipAction(@NotNull HighlightInfo info, @NotNull Editor editor, @NotNull PsiFile psiFile);


  @Nullable
  static TooltipAction calcTooltipAction(@NotNull HighlightInfo info, @NotNull Project project, @NotNull Editor editor) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;

    return EP_NAME.getExtensionList().stream()
      .map(extension -> extension.getTooltipAction(info, editor, file))
      .filter(Objects::nonNull).findFirst().orElse(null);
  }

  static boolean isShowActions() {
    return PropertiesComponent.getInstance().getBoolean(SHOW_FIXES_KEY, SHOW_FIXES_DEFAULT_VALUE);
  }

  static void setShowActions(boolean newValue) {
    PropertiesComponent.getInstance().setValue(SHOW_FIXES_KEY, newValue, SHOW_FIXES_DEFAULT_VALUE);
  }
}
