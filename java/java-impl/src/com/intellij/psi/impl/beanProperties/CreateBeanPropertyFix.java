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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateBeanPropertyFix implements LocalQuickFix, IntentionAction {

  private final static Logger LOG = Logger.getInstance("#com.intellij.psi.impl.beanProperties.CreateBeanPropertyFix");

  protected final String myPropertyName;
  @NotNull protected final PsiClass myPsiClass;
  @NotNull protected final PsiType myType;

  public static LocalQuickFix[] createFixes(String propertyName, @NotNull PsiClass psiClass, @Nullable PsiType type, final boolean createSetter) {
    return (LocalQuickFix[])create(propertyName, psiClass, type, createSetter);
  }

  public static IntentionAction[] createActions(String propertyName, @NotNull PsiClass psiClass, @Nullable PsiType type, final boolean createSetter) {
    return (IntentionAction[])create(propertyName, psiClass, type, createSetter);
  }

  private static Object[] create(final String propertyName, final PsiClass psiClass, PsiType type, final boolean createSetter) {
    if (type == null) {
      final Project project = psiClass.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass aClass = facade.findClass("java.lang.String", GlobalSearchScope.allScope(project));
      if (aClass == null) {
        return new CreateBeanPropertyFix[0];
      }
      type = facade.getElementFactory().createType(aClass);
    }
    if (psiClass.isInterface()) {
      return new CreateBeanPropertyFix[] {
        new CreateBeanPropertyFix(propertyName, psiClass, type) {
          @Override
          protected void doFix() throws IncorrectOperationException {
            createSetter(false);
          }

          @NotNull
          public String getName() {
            return QuickFixBundle.message("create.writable.property", myPropertyName);
          }
        }
      };
    }
    return new CreateBeanPropertyFix[] {
        new CreateBeanPropertyFix(propertyName, psiClass, type) {

          @NotNull
          public String getName() {
            return QuickFixBundle.message("create.readable.writable.property.with.field", myPropertyName);
          }

          protected void doFix() throws IncorrectOperationException {
            createField();
            createSetter(true);
            createGetter(true);
          }
        },
        new CreateBeanPropertyFix(propertyName, psiClass, type) {
          protected void doFix() throws IncorrectOperationException {
            if (createSetter) {
              createSetter(false);
            }
            else {
              createGetter(false);
            }
          }

          @NotNull
          public String getName() {
            return QuickFixBundle.message(createSetter ? "create.writable.property" : "create.readable.property", myPropertyName);
          }
        },
        new CreateBeanPropertyFix(propertyName, psiClass, type) {
          protected void doFix() throws IncorrectOperationException {
            createField();
            if (createSetter) {
              createSetter(true);
            }
            else {
              createGetter(true);
            }
          }

          @NotNull
          public String getName() {
            return QuickFixBundle.message(createSetter ? "create.writable.property.with.field" : "create.readable.property.with.field", myPropertyName);
          }
        }

    };
  }

  protected CreateBeanPropertyFix(String propertyName, @NotNull PsiClass psiClass, @NotNull PsiType type) {
    myPropertyName = propertyName;
    myPsiClass = psiClass;
    myType = type;
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    applyFix(project);
  }

  private void applyFix(final Project project) {
    new WriteCommandAction.Simple(project, getName(), myPsiClass.getContainingFile()) {
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

  @NotNull
  public String getText() {
    return getName();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    applyFix(project);
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected abstract void doFix() throws IncorrectOperationException;

  private String getFieldName() {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myPsiClass.getProject());
    return styleManager.suggestVariableName(VariableKind.FIELD, myPropertyName, null, myType).names[0];
  }

  protected PsiElement createSetter(final boolean createField) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final String methodName = PropertyUtil.suggestSetterName(myPropertyName);
    final String typeName = myType.getCanonicalText();

    @NonNls final String text;
    boolean isInterface = myPsiClass.isInterface();
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
    final PsiMethod psiElement = (PsiMethod)myPsiClass.add(method);
    if (!isInterface && !createField) {
      CreateFromUsageUtils.setupMethodBody(psiElement);
    }
    return psiElement;
  }

  protected PsiElement createGetter(final boolean createField) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final String methodName = PropertyUtil.suggestGetterName(myPropertyName, myType);
    final String typeName = myType.getCanonicalText();
    @NonNls final String text;
    if (createField) {
      final String fieldName = getFieldName();
      text = "public " + typeName + " " + methodName + "() { return " + fieldName + "; }";
    } else {
      text = "public " + typeName + " " + methodName + "() { return null; }";
    }
    final PsiMethod method = elementFactory.createMethodFromText(text, null);
    final PsiMethod psiElement = (PsiMethod)myPsiClass.add(method);
    if (!createField) {
      CreateFromUsageUtils.setupMethodBody(psiElement);
    }
    return psiElement;
  }

  protected PsiElement createField() throws IncorrectOperationException {
    final String fieldName = getFieldName();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
    final PsiField psiField = elementFactory.createField(fieldName, myType);
    return myPsiClass.add(psiField);
  }
}
