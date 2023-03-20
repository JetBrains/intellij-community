// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import org.jetbrains.annotations.NotNull;

public abstract class CreateFieldFromParameterActionBase extends BaseIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!BaseIntentionAction.canModify(file)) return false;
    PsiParameter parameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (parameter == null || !isAvailable(parameter)) {
      return false;
    }
    setText(JavaBundle.message("intention.create.field.from.parameter.text", parameter.getName()));

    return true;
  }

  protected abstract boolean isAvailable(@NotNull PsiParameter parameter);

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.create.field.from.parameter.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiParameter parameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (parameter == null || !FileModificationService.getInstance().prepareFileForWrite(file)) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    processParameter(editor, parameter, !ApplicationManager.getApplication().isHeadlessEnvironment());
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiParameter parameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (parameter == null) return IntentionPreviewInfo.EMPTY;
    processParameter(editor, parameter, false);
    return IntentionPreviewInfo.DIFF;
  }

  private void processParameter(@NotNull Editor editor, @NotNull PsiParameter parameter, boolean isInteractive) {
    Project project = parameter.getProject();
    PsiType type = getSubstitutedType(parameter);
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String parameterName = parameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    PsiClass targetClass = method.getContainingClass();
    if (targetClass == null) return;

    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    SuggestedNameInfo uniqueNameInfo = styleManager.suggestUniqueVariableName(suggestedNameInfo, targetClass, true);

    boolean isFinal = !isMethodStatic && method.isConstructor();

    PsiVariable variable = IntentionPreviewUtils.writeAndCompute(
      () -> createField(project, targetClass, method, parameter, type, uniqueNameInfo.names[0], isMethodStatic, isFinal));

    if (!isInteractive) return;

    PsiIdentifier identifier = variable.getNameIdentifier();
    if (identifier == null) return;
    final int textOffset = identifier.getTextOffset();
    editor.getCaretModel().moveToOffset(textOffset);
    new MemberInplaceRenamer(variable, null, editor) {
      @Override
      protected boolean shouldSelectAll() {
        return true;
      }

      @Override
      protected void moveOffsetAfter(boolean success) {
        super.moveOffsetAfter(success);
        editor.getCaretModel().moveToOffset(parameter.getTextRange().getEndOffset());
      }
    }.performInplaceRename();
  }

  protected abstract PsiType getSubstitutedType(@NotNull PsiParameter parameter);

  protected abstract PsiVariable createField(@NotNull Project project,
                                             @NotNull PsiClass targetClass,
                                             @NotNull PsiMethod method,
                                             @NotNull PsiParameter myParameter,
                                             PsiType type,
                                             @NotNull String fieldName,
                                             boolean methodStatic,
                                             boolean isFinal);

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
