// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateClassFromNewFix extends CreateFromUsageBaseFix {
  private final SmartPsiElementPointer<PsiNewExpression> myNewExpression;

  public CreateClassFromNewFix(PsiNewExpression newExpression) {
    myNewExpression = SmartPointerManager.getInstance(newExpression.getProject()).createSmartPsiElementPointer(newExpression);
  }

  protected PsiNewExpression getNewExpression() {
    return myNewExpression.getElement();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) {
    PsiNewExpression newExpression = getNewExpression();
    if (newExpression == null) {
      return;
    }

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(newExpression);
    PsiClass psiClass = CreateFromUsageUtils.createClass(referenceElement, getKind(), null);
    if (psiClass != null) {
      WriteAction.run(() -> setupClassFromNewExpression(psiClass, newExpression));
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiNewExpression element = myNewExpression.getElement();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    PsiJavaCodeReferenceElement classReference = getReferenceElement(element);
    if (classReference == null) return IntentionPreviewInfo.EMPTY;
    PsiClass aClass = (PsiClass)psiFile.add(getKind().create(JavaPsiFacade.getElementFactory(project), classReference.getReferenceName()));
    setupClassFromNewExpression(aClass, element);
    setupGenericParameters(aClass, classReference);
    CodeStyleManager.getInstance(project).reformat(aClass);
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, "", aClass.getText());
  }

  @NotNull
  CreateClassKind getKind() {
    return CreateClassKind.CLASS;
  }

  protected void setupClassFromNewExpression(final @NotNull PsiClass aClass, final @NotNull PsiNewExpression newExpression) {
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference != null && aClass.isPhysical()) {
      classReference.bindToElement(aClass);
    }
    setupInheritance(newExpression, aClass);

    PsiExpressionList argList = newExpression.getArgumentList();
    final Project project = aClass.getProject();
    if (argList != null && !argList.isEmpty()) {
      TemplateBuilderImpl templateBuilder = createConstructorTemplate(aClass, newExpression, argList);

      if (aClass.isPhysical()) {
        getReferenceElement(newExpression).bindToElement(aClass);
      }
      CreateFromUsageBaseFix.startTemplate(project, aClass, templateBuilder.buildTemplate(), getText());
    }
    else {
      CodeInsightUtil.positionCursor(project, aClass.getContainingFile(), ObjectUtils.notNull(aClass.getNameIdentifier(), aClass));
    }
  }

  @NotNull
  TemplateBuilderImpl createConstructorTemplate(PsiClass aClass, PsiNewExpression newExpression, PsiExpressionList argList) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(newExpression.getProject());
    PsiMethod constructor = elementFactory.createConstructor();
    constructor = (PsiMethod)aClass.add(constructor);

    TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(aClass);
    CreateFromUsageUtils.setupMethodParameters(constructor, templateBuilder, argList, getTargetSubstitutor(newExpression));

    setupSuperCall(aClass, constructor, templateBuilder);
    return templateBuilder;
  }

  public static @Nullable PsiMethod setupSuperCall(PsiClass targetClass, PsiMethod constructor, TemplateBuilderImpl templateBuilder)
    throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getProject());
    PsiMethod supConstructor = null;
    PsiClass superClass = targetClass.getSuperClass();
    if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName()) &&
        !"java.lang.Enum".equals(superClass.getQualifiedName())) {
      PsiMethod[] constructors = superClass.getConstructors();
      boolean hasDefaultConstructor = false;

      for (PsiMethod superConstructor : constructors) {
        if (superConstructor.getParameterList().isEmpty()) {
          hasDefaultConstructor = true;
          supConstructor = null;
          break;
        }
        else {
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
        return supConstructor;
      }
    }

    templateBuilder.setEndVariableAfter(constructor.getBody().getLBrace());
    return null;
  }

  private static void setupInheritance(PsiNewExpression element, PsiClass targetClass) throws IncorrectOperationException {
    if (element.getParent() instanceof PsiReferenceExpression) return;

    ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(element, false);

    for (ExpectedTypeInfo expectedType : expectedTypes) {
      PsiType type = expectedType.getType();
      if (!(type instanceof PsiClassType classType)) continue;
      PsiClass aClass = classType.resolve();
      if (aClass == null) continue;
      if (aClass.equals(targetClass) || aClass.hasModifierProperty(PsiModifier.FINAL)) continue;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());

      if (aClass.isInterface()) {
        PsiReferenceList implementsList = targetClass.getImplementsList();
        assert implementsList != null : targetClass;
        implementsList.add(factory.createReferenceElementByType(classType));
      }
      else {
        PsiReferenceList extendsList = targetClass.getExtendsList();
        assert extendsList != null : targetClass;
        if (extendsList.getReferencedTypes().length == 0 && !CommonClassNames.JAVA_LANG_OBJECT.equals(classType.getCanonicalText())) {
          extendsList.add(factory.createReferenceElementByType(classType));
        }
      }
    }
  }

  private static PsiFile getTargetFile(PsiElement element) {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement((PsiNewExpression)element);

    PsiElement q = referenceElement.getQualifier();
    if (q instanceof PsiJavaCodeReferenceElement qualifier && qualifier.resolve() instanceof PsiClass psiClass) {
      return psiClass.getContainingFile();
    }

    return null;
  }

  @Override
  protected PsiElement getElement() {
    final PsiNewExpression expression = getNewExpression();
    if (expression == null ||
        (!expression.getManager().isInProject(expression) && !ScratchUtil.isScratch(PsiUtilCore.getVirtualFile(expression)))) {
      return null;
    }
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(expression);
    if (referenceElement == null) return null;
    if (referenceElement.getReferenceNameElement() instanceof PsiIdentifier) return expression;

    return null;
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return false;
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(element, PsiJavaCodeReferenceElement.class);
    return ref != null && ref.resolve() != null;
  }

  @Override
  protected boolean isAvailableImpl(int offset) {
    PsiNewExpression expression = getNewExpression();
    if (rejectContainer(expression)) {
      return false;
    }

    PsiFile targetFile = getTargetFile(expression);
    if (targetFile != null && !targetFile.getManager().isInProject(targetFile)) {
      return false;
    }

    PsiElement nameElement = getNameElement(expression);
    if (CreateFromUsageUtils.shouldShowTag(offset, nameElement, expression)) {
      String varName = nameElement.getText();
      setText(getText(varName));
      return true;
    }

    return false;
  }

  protected boolean rejectContainer(PsiNewExpression expression) {
    if (expression.getQualifier() != null) {
      return true;
    }
    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    if (classReference != null && classReference.isQualified()) {
      PsiJavaCodeReferenceElement containerReference = ObjectUtils.tryCast(classReference.getQualifier(), PsiJavaCodeReferenceElement.class);
      if (containerReference != null) {
        PsiElement targetClass = containerReference.resolve();
        return !(targetClass instanceof PsiClass) || !InheritanceUtil.hasEnclosingInstanceInScope((PsiClass)targetClass, expression, true, true);
      }
      return true;
    }
    return false;
  }

  protected @IntentionName String getText(final String varName) {
    return CommonQuickFixBundle.message("fix.create.title.x", getKind().getDescriptionAccusative(), varName);
  }

  protected static PsiJavaCodeReferenceElement getReferenceElement(PsiNewExpression expression) {
    return expression.getClassOrAnonymousClassReference();
  }

  private static PsiElement getNameElement(PsiNewExpression targetElement) {
    PsiJavaCodeReferenceElement referenceElement = getReferenceElement(targetElement);
    return referenceElement != null ? referenceElement.getReferenceNameElement() : null;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.class.from.new.family");
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return false;
  }
}
