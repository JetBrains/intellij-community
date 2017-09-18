/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.beanProperties;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class CreateJavaBeanPropertyFix implements LocalQuickFix, IntentionAction {

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.beanProperties.CreateJavaBeanPropertyFix");
  protected final String myPropertyName;
  @NotNull protected final SmartPsiElementPointer<PsiClass> myPsiClass;
  @NotNull protected final PsiType myType;
  private final boolean myGetter;
  private final boolean mySetter;
  private final boolean myField;

  public CreateJavaBeanPropertyFix(@NotNull PsiClass psiClass, @NotNull String propertyName,
                                   @NotNull PsiType propertyType,
                                   boolean getterRequired,
                                   boolean setterRequired,
                                   boolean fieldRequired) {
    myPropertyName = propertyName;
    myPsiClass = SmartPointerManager.getInstance(psiClass.getProject()).createSmartPsiElementPointer(psiClass);
    myType = propertyType;
    myGetter = getterRequired;
    mySetter = setterRequired;
    myField = fieldRequired;
  }

  @Override
  @NotNull
  public String getName() {
    if (myGetter && mySetter && myField) return QuickFixBundle.message("create.readable.writable.property.with.field", myPropertyName);
    if (myField && myGetter) return QuickFixBundle.message("create.readable.property.with.field", myPropertyName);
    if (myField && mySetter) return QuickFixBundle.message("create.writable.property.with.field", myPropertyName);
    if (!myField && myGetter) return QuickFixBundle.message("create.readable.property", myPropertyName);
    if (!myField && mySetter) return QuickFixBundle.message("create.writable.property", myPropertyName);
    return QuickFixBundle.message("create.readable.writable.property.with.field", myPropertyName);
  }

  protected void doFix() throws IncorrectOperationException {
    if (myField) createField();
    if (mySetter) createSetter(myField);
    if (myGetter) createGetter(myField);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    applyFix(project);
  }

  private void applyFix(final Project project) {
    new WriteCommandAction.Simple(project, getName(), myPsiClass.getContainingFile()) {
      @Override
      protected void run() throws Throwable {
        try {
          doFix();
        }
        catch (IncorrectOperationException e) {
          LOG.error("Cannot create property", e);
        }
      }
    }.execute();
  }

  @Override
  @NotNull
  public String getText() {
    return getName();
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    applyFix(project);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private String getFieldName() {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myPsiClass.getProject());
    return styleManager.suggestVariableName(VariableKind.FIELD, myPropertyName, null, myType).names[0];
  }

  private void createSetter(final boolean createField) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final String methodName = PropertyUtilBase.suggestSetterName(myPropertyName);
    final String typeName = myType.getCanonicalText();

    @NonNls final String text;
    PsiClass psiClass = myPsiClass.getElement();
    if (psiClass == null) return;
    boolean isInterface = psiClass.isInterface();
    if (isInterface) {
      text = "public void " + methodName + "(" + typeName + " " + myPropertyName + ");";
    }
    else if (createField) {
      @NonNls String fieldName = getFieldName();
      if (fieldName.equals(myPropertyName)) {
        fieldName = "this." + fieldName;
      }
      text = "public void " + methodName + "(" + typeName + " " + myPropertyName + ") {" + fieldName + "=" + myPropertyName + ";}";
    }
    else {
      text = "public void " + methodName + "(" + typeName + " " + myPropertyName + ") {}";
    }
    final PsiMethod method = elementFactory.createMethodFromText(text, null);
    final PsiMethod psiElement = (PsiMethod)psiClass.add(method);
    if (!isInterface && !createField) {
      CreateFromUsageUtils.setupMethodBody(psiElement, psiClass);
    }
  }

  private void createGetter(final boolean createField) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final String methodName = PropertyUtilBase.suggestGetterName(myPropertyName, myType);
    final String typeName = myType.getCanonicalText();
    @NonNls final String text;
    PsiClass psiClass = myPsiClass.getElement();
    if (psiClass == null) return;
    boolean isInterface = psiClass.isInterface();
    if (createField) {
      final String fieldName = getFieldName();
      text = "public " + typeName + " " + methodName + "() { return " + fieldName + "; }";
    }
    else {
      if (isInterface) {
        text = typeName + " " + methodName + "();";
      }
      else {
        text = "public " + typeName + " " + methodName + "() { return null; }";
      }
    }
    final PsiMethod method = elementFactory.createMethodFromText(text, null);
    final PsiMethod psiElement = (PsiMethod)psiClass.add(method);
    if (!createField && !isInterface) {
      CreateFromUsageUtils.setupMethodBody(psiElement);
    }
  }

  private void createField() throws IncorrectOperationException {
    final String fieldName = getFieldName();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final PsiField psiField = elementFactory.createField(fieldName, myType);
    PsiClass psiClass = myPsiClass.getElement();
    if (psiClass == null) return;
    psiClass.add(psiField);
  }
}
