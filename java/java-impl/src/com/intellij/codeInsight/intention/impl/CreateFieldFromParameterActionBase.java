/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
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
import com.intellij.util.ArrayUtil;
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
    final PsiParameter parameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (parameter == null || !isAvailable(parameter)) {
      return false;
    }
    setText(CodeInsightBundle.message("intention.create.field.from.parameter.text", parameter.getName()));

    return true;
  }

  protected abstract boolean isAvailable(PsiParameter parameter);

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.create.field.from.parameter.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiParameter myParameter = FieldFromParameterUtils.findParameterAtCursor(file, editor);
    if (myParameter == null || !FileModificationService.getInstance().prepareFileForWrite(file)) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    try {
      processParameter(project, myParameter, !ApplicationManager.getApplication().isHeadlessEnvironment());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void processParameter(final @NotNull Project project,
                                final @NotNull PsiParameter myParameter,
                                final boolean isInteractive) {
    final PsiType type = getSubstitutedType(myParameter);
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    final String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    String fieldNameToCalc;
    boolean isFinalToCalc;
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    final PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

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
      names = ArrayUtil.toStringArray(namesList);

      final CreateFieldFromParameterDialog dialog = new CreateFieldFromParameterDialog(
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

    final boolean isFinal = isFinalToCalc;
    final String fieldName = fieldNameToCalc;
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        performRefactoring(project, targetClass, method, myParameter, type, fieldName, isMethodStatic, isFinal);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  protected abstract PsiType getSubstitutedType(PsiParameter parameter);

  protected abstract void performRefactoring(Project project,
                                    PsiClass targetClass,
                                    PsiMethod method,
                                    PsiParameter myParameter,
                                    PsiType type,
                                    String fieldName,
                                    boolean methodStatic,
                                    boolean isFinal);

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
