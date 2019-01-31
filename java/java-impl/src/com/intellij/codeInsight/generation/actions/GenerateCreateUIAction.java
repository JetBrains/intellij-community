/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.actions;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;

import java.util.HashSet;

/**
 * @author Konstantin Bulenkov
 */
public class GenerateCreateUIAction extends BaseGenerateAction {
  public GenerateCreateUIAction() {
    super(new GenerateCreateUIHandler());
  }

  @Override
  public boolean isValidForClass(PsiClass targetClass) {
    final PsiModifierList list = targetClass.getModifierList();
    return list != null
           && !list.hasModifierProperty(PsiModifier.ABSTRACT)
           && !hasCreateUIMethod(targetClass)
           && isComponentUI(targetClass, new HashSet<>());
  }

  private static boolean hasCreateUIMethod(PsiClass aClass) {
    for (PsiMethod method : aClass.findMethodsByName("createUI", false)) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 1) {
          final PsiType type = parameters[0].getType();
          final PsiClass typeClass = PsiTypesUtil.getPsiClass(type);
          return typeClass != null && "javax.swing.JComponent".equals(typeClass.getQualifiedName());
        }
      }
    }
    return false;
  }

  private static boolean isComponentUI(PsiClass aClass, HashSet<? super PsiClass> classes) {
    while (aClass != null) {
      if (!classes.add(aClass)) return false;
      if ("javax.swing.plaf.ComponentUI".equals(aClass.getQualifiedName())) {
        return true;
      }
      aClass = aClass.getSuperClass();
    }
    return false;
  }
}
