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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
public class CreateClassFromNewFix extends CreateFromUsageBaseFix {
  private final SmartPsiElementPointer myNewExpression;

  public CreateClassFromNewFix(PsiNewExpression newExpression) {
    myNewExpression = SmartPointerManager.getInstance(newExpression.getProject()).createSmartPsiElementPointer(newExpression);
  }

  protected PsiNewExpression getNewExpression() {
    return (PsiNewExpression)myNewExpression.getElement();
  }

  protected void invokeImpl(PsiClass targetClass) {
    assert ApplicationManager.getApplication().isWriteAccessAllowed();

    final PsiNewExpression newExpression = getNewExpression();

    final PsiJavaCodeReferenceElement referenceElement = getReferenceElement(newExpression);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final PsiClass[] psiClass = new PsiClass[1];
        CommandProcessor.getInstance().executeCommand(newExpression.getProject(), new Runnable() {
          public void run() {
            psiClass[0] = CreateFromUsageUtils.createClass(referenceElement, CreateClassKind.CLASS, null);
          }
        }, getText(), getText());
        new WriteCommandAction(newExpression.getProject(), getText(), getText()) {
          @Override
          protected void run(Result result) throws Throwable {
            setupClassFromNewExpression(psiClass[0], newExpression);
          }
        }.execute();
      }
    });
  }

  protected void setupClassFromNewExpression(final PsiClass psiClass, final PsiNewExpression newExpression) {
    assert ApplicationManager.getApplication().isWriteAccessAllowed();

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(newExpression.getProject()).getElementFactory();
    PsiClass aClass = psiClass;
    if (aClass == null) return;

    final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference != null) {
      classReference.bindToElement(aClass);
    }
    setupInheritance(newExpression, aClass);

    PsiExpressionList argList = newExpression.getArgumentList();
    Project project = aClass.getProject();
    if (argList != null && argList.getExpressions().length > 0) {
      PsiMethod constructor = elementFactory.createConstructor();
      constructor = (PsiMethod) aClass.add(constructor);

      TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(aClass);
      CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, argList, getTargetSubstitutor(newExpression));

      setupSuperCall(aClass, constructor, templateBuilder);

      getReferenceElement(newExpression).bindToElement(aClass);
      aClass = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(aClass);
      Template template = templateBuilder.buildTemplate();
      template.setToReformat(true);

      Editor editor = positionCursor(project, aClass.getContainingFile(), aClass);
      TextRange textRange = aClass.getTextRange();
      editor.getDocument().deleteString(textRange.getStartOffset(), textRange.getEndOffset());

      startTemplate(editor, template, project, null, getText());
    }
    else {
      positionCursor(project, aClass.getContainingFile(), aClass);
    }
  }

  @Nullable
  public static PsiMethod setupSuperCall(PsiClass targetClass, PsiMethod constructor, TemplateBuilderImpl templateBuilder)
    throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(targetClass.getProject()).getElementFactory();
    PsiMethod supConstructor = null;
    PsiClass superClass = targetClass.getSuperClass();
    if (superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName()) &&
          !"java.lang.Enum".equals(superClass.getQualifiedName())) {
      PsiMethod[] constructors = superClass.getConstructors();
      boolean hasDefaultConstructor = false;

      for (PsiMethod superConstructor : constructors) {
        if (superConstructor.getParameterList().getParametersCount() == 0) {
          hasDefaultConstructor = true;
          supConstructor = null;
          break;
        } else {
          supConstructor = superConstructor;
        }
      }

      if (!hasDefaultConstructor) {
        PsiExpressionStatement statement =
          (PsiExpressionStatement)elementFactory.createStatementFromText("super();", constructor);
        statement = (PsiExpressionStatement)constructor.getBody().add(statement);

        PsiMethodCallExpression call = (PsiMethodCallExpression)statement.getExpression();
        PsiExpressionList argumentList = call.getArgumentList();
        templateBuilder.setEndVariableAfter(argumentList.getFirstChild());
      }
    }

    templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
    return supConstructor;
  }

  private static void setupInheritance(PsiNewExpression element, PsiClass targetClass) throws IncorrectOperationException {
    if (element.getParent() instanceof PsiReferenceExpression) return;

    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getInstance(element.getProject()).getExpectedTypes(element, false);

    for (ExpectedTypeInfo expectedType : expectedTypes) {
      PsiType type = expectedType.getType();
      if (!(type instanceof PsiClassType)) continue;
      final PsiClassType classType = (PsiClassType)type;
      PsiClass aClass = classType.resolve();
      if (aClass == null) continue;
      if (aClass.equals(targetClass) || aClass.hasModifierProperty(PsiModifier.FINAL)) continue;
      PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();

      if (aClass.isInterface()) {
        PsiReferenceList implementsList = targetClass.getImplementsList();
        implementsList.add(factory.createReferenceElementByType(classType));
      }
      else {
        PsiReferenceList extendsList = targetClass.getExtendsList();
        if (extendsList.getReferencedTypes().length == 0 && !"java.lang.Object".equals(classType.getCanonicalText())) {
          extendsList.add(factory.createReferenceElementByType(classType));
        }
      }
    }
  }


  private static PsiFile getTargetFile(PsiElement element) {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)element);

    PsiElement q = referenceElement.getQualifier();
    if (q instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement)q;
      PsiElement psiElement = qualifier.resolve();
      if (psiElement instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)psiElement;
        return psiClass.getContainingFile();
      }
    }

    return null;
  }

  protected PsiElement getElement() {
    final PsiNewExpression expression = getNewExpression();
    if (expression == null || !expression.getManager().isInProject(expression)) return null;
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(expression);
    if (referenceElement == null) return null;
    if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) return expression;

    return null;
  }

  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  protected boolean isValidElement(PsiElement element) {
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(element, PsiJavaCodeReferenceElement.class);
    return ref != null && ref.resolve() != null;
  }

  protected boolean isAvailableImpl(int offset) {
    PsiElement nameElement = getNameElement(getNewExpression());

    PsiFile targetFile = getTargetFile(getNewExpression());
    if (targetFile != null && !targetFile.getManager().isInProject(targetFile)) {
      return false;
    }

    if (CreateFromUsageUtils.shouldShowTag(offset, nameElement, getNewExpression())) {
      String varName = nameElement.getText();
      setText(getText(varName));
      return true;
    }

    return false;
  }

  protected String getText(final String varName) {
    return QuickFixBundle.message("create.class.from.new.text", varName);
  }

  protected static PsiJavaCodeReferenceElement getReferenceElement(PsiNewExpression expression) {
    return expression.getClassOrAnonymousClassReference();
  }

  private static PsiElement getNameElement(PsiNewExpression targetElement) {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(targetElement);
    if (referenceElement == null) return null;
    return referenceElement.getReferenceNameElement();
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.class.from.new.family");
  }
}
