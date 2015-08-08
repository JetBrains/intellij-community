/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.impl.ParameterClassMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: anna
 * Date: 8/2/12
 */
public class DefineParamsDefaultValueAction extends DelegateWithDefaultParamValueIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
      return false;
    }
    final PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiCodeBlock.class);
    if (!(parent instanceof PsiMethod)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)parent;
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() == 0) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || (containingClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(method))) {
      return false;
    }
    setText("Generate overloaded " + (method.isConstructor() ? "constructor" : "method") + " with default parameter values");
    return true;
  }

  @Nullable
  @Override
  protected PsiParameter[] getParams(PsiElement element) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    assert method != null;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final ParameterClassMember[] members = new ParameterClassMember[parameters.length];
    for (int i = 0; i < members.length; i++) {
      members[i] = new ParameterClassMember(parameters[i]);
    }
    final MemberChooser<ParameterClassMember> chooser =
      new MemberChooser<ParameterClassMember>(members, false, true, element.getProject());
    chooser.selectElements(members);
    chooser.setTitle("Choose Default Value Parameters");
    if (chooser.showAndGet()) {
      final List<ParameterClassMember> elements = chooser.getSelectedElements();
      if (elements != null) {
        PsiParameter[] params = new PsiParameter[elements.size()];
        for (int i = 0; i < params.length; i++) {
          params[i] = elements.get(i).getParameter();
        }
        return params;
      }
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Generate overloaded method with default parameter values";
  }
}
