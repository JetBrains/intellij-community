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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedParameterFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  private final String myName;

  public RemoveUnusedParameterFix(PsiParameter parameter) {
    super(parameter);
    myName = parameter.getName();
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("remove.unused.parameter.text", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unused.parameter.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiParameter myParameter = (PsiParameter)startElement;
    return
      myParameter.isValid()
      && myParameter.getDeclarationScope() instanceof PsiMethod
      && myParameter.getManager().isInProject(myParameter);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiParameter myParameter = (PsiParameter)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(myParameter.getContainingFile())) return;
    removeReferences(myParameter);
  }

  private static void removeReferences(PsiParameter parameter) {
    PsiMethod method = (PsiMethod) parameter.getDeclarationScope();
    ChangeSignatureProcessor processor = new ChangeSignatureProcessor(parameter.getProject(),
                                                                      method,
                                                                      false, null,
                                                                      method.getName(),
                                                                      method.getReturnType(),
                                                                      getNewParametersInfo(method, parameter));
    processor.run();
  }

  public static ParameterInfoImpl[] getNewParametersInfo(PsiMethod method, PsiParameter parameterToRemove) {
    List<ParameterInfoImpl> result = new ArrayList<>();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (!Comparing.equal(parameter, parameterToRemove)) {
        result.add(new ParameterInfoImpl(i, parameter.getName(), parameter.getType()));
      }
    }
    return result.toArray(new ParameterInfoImpl[result.size()]);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
