// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class EditReassignedTextAttributesAction extends BaseElementAtCaretIntentionAction implements LowPriorityAction {
  
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.edit.color.settings");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiVariable variable = getVariable(element);
    if (variable != null) {
      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(getAttributesKey(variable));
      if (attributes != null && !attributes.isEmpty()) {
        if (HighlightControlFlowUtil.isReassigned(variable, new HashMap<>())) {
          setText(JavaBundle.message("intention.name.edit.color.settings",
                                     JavaBundle.message(variable instanceof PsiLocalVariable
                                                        ? "tooltip.reassigned.local.variable"
                                                        : "tooltip.reassigned.parameter")));
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiVariable variable = getVariable(element);
    if (variable != null) {
      Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> descriptor = ColorSettingsPages.getInstance().getAttributeDescriptor(getAttributesKey(variable));
      if (descriptor != null) {
        ColorAndFontOptions.selectOrEditColor(id -> CommonDataKeys.PROJECT.is(id) ? project : null,
                                              descriptor.second.getDisplayName(),
                                              descriptor.first.getDisplayName());
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static PsiVariable getVariable(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiVariable) {
        return (PsiVariable)parent;
      }
      if (parent instanceof PsiReferenceExpression) {
        return ObjectUtils.tryCast(((PsiReferenceExpression)parent).resolve(), PsiVariable.class);
      }
    }
    return null;
  }

  private static TextAttributesKey getAttributesKey(@NotNull PsiVariable element) {
    return element instanceof PsiLocalVariable
           ? JavaHighlightingColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES
           : JavaHighlightingColors.REASSIGNED_PARAMETER_ATTRIBUTES;
  }
}
