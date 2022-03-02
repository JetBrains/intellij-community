// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class CreateFieldFromParameterActionBase extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(CreateFieldFromParameterActionBase.class);

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
    PsiParameter myParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (myParameter == null || !FileModificationService.getInstance().prepareFileForWrite(file)) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    try {
      processParameter(project, myParameter, !ApplicationManager.getApplication().isHeadlessEnvironment());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void processParameter(@NotNull Project project,
                                @NotNull PsiParameter myParameter,
                                boolean isInteractive) {
    PsiType type = getSubstitutedType(myParameter);
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    String fieldNameToCalc;
    boolean isFinalToCalc;
    PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    if (targetClass == null) return;
    PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    String[] names = suggestedNameInfo.names;

    if (isInteractive) {
      List<String> namesList = new ArrayList<>();
      ContainerUtil.addAll(namesList, names);
      String defaultName = styleManager.propertyNameToVariableName(propertyName, kind);
      if (namesList.contains(defaultName)) {
        Collections.swap(namesList, 0, namesList.indexOf(defaultName));
      }
      else {
        namesList.add(0, defaultName);
      }
      names = ArrayUtilRt.toStringArray(namesList);

      CreateFieldFromParameterDialog dialog = new CreateFieldFromParameterDialog(
        project,
        names,
        targetClass,
        method.isConstructor(),
        type
      );
      if (!dialog.showAndGet()) {
        return;
      }
      fieldNameToCalc = dialog.getEnteredName();
      isFinalToCalc = dialog.isDeclareFinal();

      suggestedNameInfo.nameChosen(fieldNameToCalc);
    }
    else {
      isFinalToCalc = !isMethodStatic && method.isConstructor();
      fieldNameToCalc = names[0];
    }

    boolean isFinal = isFinalToCalc;
    String fieldName = fieldNameToCalc;
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        performRefactoring(project, targetClass, method, myParameter, type, fieldName, isMethodStatic, isFinal);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  protected abstract PsiType getSubstitutedType(@NotNull PsiParameter parameter);

  protected abstract void performRefactoring(@NotNull Project project,
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
