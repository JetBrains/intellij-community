// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ReplaceConstructorWithFactoryProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ReplaceConstructorWithFactoryProcessor.class);
  private final PsiMethod myConstructor;
  private final String myFactoryName;
  private final PsiElementFactory myFactory;
  private final PsiClass myOriginalClass;
  private final PsiClass myTargetClass;
  private final PsiManager myManager;
  private final boolean myIsInner;
  private List<PsiElement> myNonNewConstructorUsages;

  public ReplaceConstructorWithFactoryProcessor(Project project,
                                                PsiMethod originalConstructor,
                                                PsiClass originalClass,
                                                PsiClass targetClass,
                                                @NonNls String factoryName) {
    super(project);
    myOriginalClass = originalClass;
    myConstructor = originalConstructor;
    myTargetClass = targetClass;
    myFactoryName = factoryName;
    myManager = PsiManager.getInstance(project);
    myFactory = JavaPsiFacade.getElementFactory(myManager.getProject());
    myIsInner = isInner(myOriginalClass);
    myNonNewConstructorUsages = Collections.emptyList();
  }

  private boolean isInner(PsiClass originalClass) {
    final boolean result = PsiUtil.isInnerClass(originalClass);
    if (result) {
      LOG.assertTrue(PsiTreeUtil.isAncestor(myTargetClass, originalClass, false));
    }
    return result;
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    if (myConstructor != null) {
      return new ReplaceConstructorWithFactoryViewDescriptor(myConstructor);
    }
    else {
      return new ReplaceConstructorWithFactoryViewDescriptor(myOriginalClass);
    }
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);

    ArrayList<UsageInfo> usages = new ArrayList<>();
    myNonNewConstructorUsages = new ArrayList<>();

    for (PsiReference reference : ReferencesSearch.search(myConstructor == null ? myOriginalClass : myConstructor, projectScope, false)) {
      PsiElement element = reference.getElement();

      if (element.getParent() instanceof PsiNewExpression) {
        usages.add(new UsageInfo(element.getParent()));
      }
      else if ("super".equals(element.getText()) || "this".equals(element.getText())) {
          myNonNewConstructorUsages.add(element);
      }
      else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
        myNonNewConstructorUsages.add(element);
      }
      else if (element instanceof PsiClass) {
        myNonNewConstructorUsages.add(element);
      }
    }

    //if (myConstructor != null && myConstructor.getParameterList().getParametersCount() == 0) {
    //  RefactoringUtil.visitImplicitConstructorUsages(getConstructorContainingClass(), new RefactoringUtil.ImplicitConstructorUsageVisitor() {
    //    @Override public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
    //      myNonNewConstructorUsages.add(constructor);
    //    }
    //
    //    @Override public void visitClassWithoutConstructors(PsiClass aClass) {
    //      myNonNewConstructorUsages.add(aClass);
    //    }
    //  });
    //}

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usages = refUsages.get();

    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final PsiResolveHelper helper = JavaPsiFacade.getInstance(myProject).getResolveHelper();
    final PsiClass constructorContainingClass = getConstructorContainingClass();
    if (!helper.isAccessible(constructorContainingClass, myTargetClass, null)) {
      String message = JavaRefactoringBundle.message("class.0.is.not.accessible.from.target.1",
                                                 RefactoringUIUtil.getDescription(constructorContainingClass, true),
                                                 RefactoringUIUtil.getDescription(myTargetClass, true));
      conflicts.putValue(constructorContainingClass, message);
    }

    HashSet<PsiElement> reportedContainers = new HashSet<>();
    final String targetClassDescription = RefactoringUIUtil.getDescription(myTargetClass, true);
    for (UsageInfo usage : usages) {
      final PsiElement container = ConflictsUtil.getContainer(usage.getElement());
      if (!reportedContainers.contains(container)) {
        reportedContainers.add(container);
        if (!helper.isAccessible(myTargetClass, usage.getElement(), null)) {
          String message = JavaRefactoringBundle.message("target.0.is.not.accessible.from.1",
                                                     targetClassDescription,
                                                     RefactoringUIUtil.getDescription(container, true));
          conflicts.putValue(myTargetClass, message);
        }
      }
    }

    if (myIsInner) {
      for (UsageInfo usage : usages) {
        final PsiField field = PsiTreeUtil.getParentOfType(usage.getElement(), PsiField.class);
        if (field != null) {
          final PsiClass containingClass = field.getContainingClass();

          if (PsiTreeUtil.isAncestor(containingClass, myTargetClass, true)) {
            String message = JavaRefactoringBundle.message("constructor.being.refactored.is.used.in.initializer.of.0",
                                                       RefactoringUIUtil.getDescription(field, true), RefactoringUIUtil.getDescription(
                constructorContainingClass, false));
            conflicts.putValue(field, message);
          }
        }
      }
    }

    final PsiMethod factoryMethod = myTargetClass.findMethodBySignature(createFactoryMethod(), false);
    if (factoryMethod != null) {
      conflicts.putValue(factoryMethod, JavaRefactoringBundle
        .message("replace.constructor.factory.error.factory.method.already.exists", RefactoringUIUtil.getDescription(factoryMethod, false)));
    }

    return showConflicts(conflicts, usages);
  }

  private PsiClass getConstructorContainingClass() {
    if (myConstructor != null) {
      return myConstructor.getContainingClass();
    }
    else {
      return myOriginalClass;
    }
  }

  @Override
  public void performRefactoring(UsageInfo @NotNull [] usages) {

    try {
      PsiReferenceExpression classReferenceExpression =
        myFactory.createReferenceExpression(myTargetClass);
      PsiReferenceExpression qualifiedMethodReference = (PsiReferenceExpression)myFactory.createExpressionFromText("A." + myFactoryName, null);

      PsiMethod factoryMethod = createFactoryMethod();
      final PsiMethod oldFactoryMethod = myTargetClass.findMethodBySignature(factoryMethod, false);
      factoryMethod = oldFactoryMethod != null ? oldFactoryMethod : (PsiMethod)myTargetClass.add(factoryMethod);
      if (myConstructor != null) {
        PsiUtil.setModifierProperty(myConstructor, PsiModifier.PRIVATE, true);
        VisibilityUtil.escalateVisibility(myConstructor, factoryMethod);
        for (PsiElement place : myNonNewConstructorUsages) {
          VisibilityUtil.escalateVisibility(myConstructor, place);
        }
      }

      if (myConstructor == null) {
        PsiMethod constructor = myFactory.createConstructor();
        PsiUtil.setModifierProperty(constructor, PsiModifier.PRIVATE, true);
        constructor = (PsiMethod)getConstructorContainingClass().add(constructor);
        VisibilityUtil.escalateVisibility(constructor, myTargetClass);
      }

      for (UsageInfo usage : usages) {
        PsiNewExpression newExpression = (PsiNewExpression)usage.getElement();
        if (newExpression == null) continue;
        if (oldFactoryMethod != null && PsiTreeUtil.isAncestor(oldFactoryMethod, newExpression, false)) {
          continue;
        }

        VisibilityUtil.escalateVisibility(factoryMethod, newExpression);
        PsiMethodCallExpression factoryCall =
          (PsiMethodCallExpression)myFactory.createExpressionFromText(myFactoryName + "()", newExpression);
        factoryCall.getArgumentList().replace(newExpression.getArgumentList());

        boolean replaceMethodQualifier = false;
        PsiExpression newQualifier = newExpression.getQualifier();

        PsiElement resolvedFactoryMethod = factoryCall.getMethodExpression().resolve();
        if (resolvedFactoryMethod != factoryMethod || newQualifier != null) {
          factoryCall.getMethodExpression().replace(qualifiedMethodReference);
          replaceMethodQualifier = true;
        }

        if (replaceMethodQualifier) {
          if (newQualifier == null) {
            factoryCall.getMethodExpression().getQualifierExpression().replace(classReferenceExpression);
          }
          else {
            factoryCall.getMethodExpression().getQualifierExpression().replace(newQualifier);
          }
        }

        newExpression.replace(factoryCall);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiMethod createFactoryMethod() throws IncorrectOperationException {
    final PsiClass containingClass = getConstructorContainingClass();
    PsiClassType type = myFactory.createType(containingClass, PsiSubstitutor.EMPTY);
    final PsiMethod factoryMethod = myFactory.createMethod(myFactoryName, type);
    if (myConstructor != null) {
      factoryMethod.getParameterList().replace(myConstructor.getParameterList());
      factoryMethod.getThrowsList().replace(myConstructor.getThrowsList());
    }

    Collection<String> names = new HashSet<>();
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(myConstructor != null ? myConstructor : containingClass)) {
      if (!names.contains(typeParameter.getName())) { //Otherwise type parameter is hidden in the constructor
        names.add(typeParameter.getName());
        factoryMethod.getTypeParameterList().addAfter(typeParameter, null);
      }
    }

    PsiReturnStatement returnStatement =
      (PsiReturnStatement)myFactory.createStatementFromText("return new A();", null);
    PsiNewExpression newExpression = (PsiNewExpression)returnStatement.getReturnValue();
    PsiJavaCodeReferenceElement classRef = myFactory.createReferenceElementByType(type);
    newExpression.getClassReference().replace(classRef);
    final PsiExpressionList argumentList = newExpression.getArgumentList();

    PsiParameter[] params = factoryMethod.getParameterList().getParameters();

    for (PsiParameter parameter : params) {
      PsiExpression paramRef = myFactory.createExpressionFromText(parameter.getName(), null);
      argumentList.add(paramRef);
    }
    factoryMethod.getBody().add(returnStatement);

    PsiUtil.setModifierProperty(factoryMethod, getDefaultFactoryVisibility(), true);

    if (!myIsInner) {
      PsiUtil.setModifierProperty(factoryMethod, PsiModifier.STATIC, true);
    }

    return (PsiMethod)CodeStyleManager.getInstance(myProject).reformat(factoryMethod);
  }

  @PsiModifier.ModifierConstant
  private String getDefaultFactoryVisibility() {
    final PsiModifierList modifierList;
    if (myConstructor != null) {
      modifierList = myConstructor.getModifierList();
    }
    else {
      modifierList = myOriginalClass.getModifierList();
    }
    return VisibilityUtil.getVisibilityModifier(modifierList);
  }


  @Override
  @NotNull
  protected String getCommandName() {
    if (myConstructor != null) {
      return JavaRefactoringBundle.message("replace.constructor.0.with.a.factory.method",
                                       DescriptiveNameUtil.getDescriptiveName(myConstructor));
    }
    else {
      return JavaRefactoringBundle.message("replace.default.constructor.of.0.with.a.factory.method",
                                       DescriptiveNameUtil.getDescriptiveName(myOriginalClass));
    }
  }

  public PsiClass getOriginalClass() {
    return getConstructorContainingClass();
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  public PsiMethod getConstructor() {
    return myConstructor;
  }

  public String getFactoryName() {
    return myFactoryName;
  }
}
