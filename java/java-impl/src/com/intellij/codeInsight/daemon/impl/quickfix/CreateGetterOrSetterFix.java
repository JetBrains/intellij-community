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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GetterSetterPrototypeProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class CreateGetterOrSetterFix implements IntentionAction, LowPriorityAction {
  private final boolean myCreateGetter;
  private final boolean myCreateSetter;
  private final PsiField myField;
  private final String myPropertyName;

  public CreateGetterOrSetterFix(boolean createGetter, boolean createSetter, @NotNull PsiField field) {
    myCreateGetter = createGetter;
    myCreateSetter = createSetter;
    myField = field;
    myPropertyName = PropertyUtil.suggestPropertyName(field);
  }

  @Override
  @NotNull
  public String getText() {
    @NonNls final String what;
    if (myCreateGetter && myCreateSetter) {
      what = "create.getter.and.setter.for.field";
    }
    else if (myCreateGetter) {
      what = "create.getter.for.field";
    }
    else if (myCreateSetter) {
      what = "create.setter.for.field";
    }
    else {
      what = "";
      assert false;
    }
    return QuickFixBundle.message(what, myField.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.accessor.for.unused.field.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myField.isValid()) return false;

    final PsiClass aClass = myField.getContainingClass();
    if (aClass == null) {
      return false;
    }

    if (myCreateGetter){
      if (isStaticFinal(myField) || PropertyUtil.findPropertyGetter(aClass, myPropertyName, isStatic(myField), false) != null){
        return false;
      }
    }

    if (myCreateSetter){
      if(isFinal(myField) || PropertyUtil.findPropertySetter(aClass, myPropertyName, isStatic(myField), false) != null){
        return false;
      }
    }

    return true;
  }

  private static boolean isFinal(@NotNull PsiField field){
    return field.hasModifierProperty(PsiModifier.FINAL);
  }

  private static boolean isStatic(@NotNull PsiField field){
    return field.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isStaticFinal(@NotNull PsiField field){
    return isStatic(field) && isFinal(field);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myField)) return;
    PsiClass aClass = myField.getContainingClass();
    final List<PsiMethod> methods = new ArrayList<>();
    if (myCreateGetter) {
      Collections.addAll(methods, GetterSetterPrototypeProvider.generateGetterSetters(myField, true));
    }
    if (myCreateSetter) {
      Collections.addAll(methods, GetterSetterPrototypeProvider.generateGetterSetters(myField, false));
    }
    assert aClass != null;
    final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(aClass.getProject());
    for (PsiMethod method : methods) {
      final PsiElement newMember = GenerateMembersUtil.insert(aClass, method, null, true);
      manager.shortenClassReferences(newMember);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
