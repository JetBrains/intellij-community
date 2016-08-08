/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MethodParameterFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnFix");

  private final PsiType myParameterType;
  private final int myIndex;
  private final boolean myFixWholeHierarchy;
  private final String myName;

  public MethodParameterFix(PsiMethod method, PsiType type, int index, boolean fixWholeHierarchy) {
    super(method);
    myParameterType = type;
    myIndex = index;
    myFixWholeHierarchy = fixWholeHierarchy;
    myName = method.getName();
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("fix.parameter.type.text",
                                  myName,
                                  myParameterType.getCanonicalText() );
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.parameter.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;
    return myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myParameterType != null
        && !TypeConversionUtil.isNullType(myParameterType)
        && myMethod.getReturnType() != null
        && !Comparing.equal(myParameterType, myMethod.getReturnType());
  }

  @Override
  public void invoke(@NotNull final Project project,
                     @NotNull final PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiMethod myMethod = (PsiMethod)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(myMethod.getContainingFile())) return;
    try {
      PsiMethod method = myMethod;
      if (myFixWholeHierarchy) {
        method = myMethod.findDeepestSuperMethod();
        if (method == null) method = myMethod;
      }

      final PsiMethod finalMethod = method;
      ChangeSignatureProcessor processor = new ChangeSignatureProcessor(project,
                                                                        finalMethod,
                                                                        false, null,
                                                                        finalMethod.getName(),
                                                                        finalMethod.getReturnType(),
                                                                        getNewParametersInfo(finalMethod));

      processor.run();


      UndoUtil.markPsiFileForUndo(file);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private ParameterInfoImpl[] getNewParametersInfo(PsiMethod method) throws IncorrectOperationException {
    List<ParameterInfoImpl> result = new ArrayList<>();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(method.getProject());
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, myParameterType);
    PsiParameter newParameter = factory.createParameter(nameInfo.names[0], myParameterType);
    if (method.getContainingClass().isInterface()) {
      PsiUtil.setModifierProperty(newParameter, PsiModifier.FINAL, false);
      }

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (i == myIndex) {
        newParameter.setName(parameter.getName());
        parameter = newParameter;
      }
      result.add(new ParameterInfoImpl(i, parameter.getName(), parameter.getType()));
    }
    if (parameters.length == myIndex) {
      result.add(new ParameterInfoImpl(-1, newParameter.getName(), newParameter.getType()));
    }
    return result.toArray(new ParameterInfoImpl[result.size()]);
  }
}
