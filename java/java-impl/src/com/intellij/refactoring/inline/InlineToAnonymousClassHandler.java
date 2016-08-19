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
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author yole
 */
public class InlineToAnonymousClassHandler extends JavaInlineActionHandler {
  static final ElementPattern ourCatchClausePattern = PlatformPatterns.psiElement(PsiTypeElement.class).withParent(PlatformPatterns.psiElement(PsiParameter.class).withParent(
  PlatformPatterns.psiElement(PsiCatchSection.class)));
  static final ElementPattern ourThrowsClausePattern = PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(PsiReferenceList.class).withFirstChild(
    PlatformPatterns.psiElement().withText(PsiKeyword.THROWS)));

  @Override
  public boolean isEnabledOnElement(PsiElement element) {
    return element instanceof PsiMethod || element instanceof PsiClass;
  }

  @Override
  public boolean canInlineElement(final PsiElement element) {
    if (element.getLanguage() != StdLanguages.JAVA) return false;
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (method.isConstructor() && !InlineMethodHandler.isChainingConstructor(method)) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return false;
        return findClassInheritors(containingClass);
      }
    }
    if (!(element instanceof PsiClass)) return false;
    if (element instanceof PsiAnonymousClass) return false;
    return findClassInheritors((PsiClass)element);
  }

  private static boolean findClassInheritors(final PsiClass element) {
    final Collection<PsiElement> inheritors = new ArrayList<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> {
      final PsiClass inheritor = ClassInheritorsSearch.search(element).findFirst();
      if (inheritor != null) {
        inheritors.add(inheritor);
      } else {
        final PsiFunctionalExpression functionalExpression = FunctionalExpressionSearch.search(element).findFirst();
        if (functionalExpression != null) {
          inheritors.add(functionalExpression);
        }
      }
    }), "Searching for class \"" + element.getQualifiedName() + "\" inheritors ...", true, element.getProject())) return false;
    return inheritors.isEmpty();
  }

  @Override
  public boolean canInlineElementInEditor(PsiElement element, Editor editor) {
    if (canInlineElement(element)) {
      PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
      if (!InlineMethodHandler.isThisReference(reference)) {
        if (element instanceof PsiMethod && reference != null) {
          final PsiElement referenceElement = reference.getElement();
          return referenceElement != null && !PsiTreeUtil.isAncestor(((PsiMethod)element).getContainingClass(), referenceElement, false);
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void inlineElement(final Project project, final Editor editor, final PsiElement psiElement) {
    final PsiClass psiClass = psiElement instanceof PsiMethod ? ((PsiMethod) psiElement).getContainingClass() : (PsiClass) psiElement;
    PsiCall callToInline = findCallToInline(editor);

    final PsiClassType superType = InlineToAnonymousClassProcessor.getSuperType(psiClass);
    if (superType == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, "java.lang.Object is not found", RefactoringBundle.message("inline.to.anonymous.refactoring"), null);
      return;
    }

    final Ref<String> errorMessage = new Ref<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> errorMessage.set(getCannotInlineMessage(psiClass))), "Check if inline is possible...", true, project)) return;
    if (errorMessage.get() != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, errorMessage.get(), RefactoringBundle.message("inline.to.anonymous.refactoring"), null);
      return;
    }

    new InlineToAnonymousClassDialog(project, psiClass, callToInline, canBeInvokedOnReference(callToInline, superType)).show();
  }

  public static boolean canBeInvokedOnReference(PsiCall callToInline, PsiType superType) {
    if (callToInline != null) {
      final PsiElement parent = callToInline.getParent();
      if (parent instanceof PsiExpressionStatement || parent instanceof PsiSynchronizedStatement) {
        return true;
      }
      else if (parent instanceof PsiReferenceExpression) {
        return true;
      }
      else if (parent instanceof PsiExpressionList) {
        final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(parent, PsiMethodCallExpression.class);
        if (methodCallExpression != null) {
          int paramIdx = ArrayUtil.find(methodCallExpression.getArgumentList().getExpressions(), callToInline);
          if (paramIdx != -1) {
            final JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
            final PsiElement resolvedMethod = resolveResult.getElement();
            if (resolvedMethod instanceof PsiMethod) {
              PsiType paramType;
              final PsiParameter[] parameters = ((PsiMethod)resolvedMethod).getParameterList().getParameters();
              if (paramIdx >= parameters.length) {
                final PsiParameter varargParameter = parameters[parameters.length - 1];
                paramType = varargParameter.getType();
              }
              else {
                paramType = parameters[paramIdx].getType();
              }
              if (paramType instanceof PsiEllipsisType) {
                paramType = ((PsiEllipsisType)paramType).getComponentType();
              }
              paramType = resolveResult.getSubstitutor().substitute(paramType);

              final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)callToInline).getClassOrAnonymousClassReference();
              if (classReference != null) {
                superType = classReference.advancedResolve(false).getSubstitutor().substitute(superType);
                if (TypeConversionUtil.isAssignable(paramType, superType)) {
                  return true;
                }
              }
            }
          }
        }
      }
    }
    return false;
  }


  @Nullable
  public static PsiCall findCallToInline(final Editor editor) {
    PsiCall callToInline = null;
    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor) : null;
    if (reference != null) {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiJavaCodeReferenceElement) {
        callToInline = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)element);
      }
    }
    return callToInline;
  }

  @Nullable
  public static String getCannotInlineMessage(final PsiClass psiClass) {
    if (psiClass instanceof PsiTypeParameter) {
      return "Type parameters cannot be inlined";
    }
    if (psiClass.isAnnotationType()) {
      return "Annotation types cannot be inlined";
    }
    if (psiClass.isInterface()) {
      return "Interfaces cannot be inlined";
    }
    if (psiClass.isEnum()) {
      return "Enums cannot be inlined";
    }
    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return RefactoringBundle.message("inline.to.anonymous.no.abstract");
    }
    if (!psiClass.getManager().isInProject(psiClass)) {
      return "Library classes cannot be inlined";
    }

    PsiClassType[] classTypes = psiClass.getExtendsListTypes();
    for(PsiClassType classType: classTypes) {
      PsiClass superClass = classType.resolve();
      if (superClass == null) {
        return "Class cannot be inlined because its superclass cannot be resolved";
      }
    }

    final PsiClassType[] interfaces = psiClass.getImplementsListTypes();
    if (interfaces.length > 1) {
      return RefactoringBundle.message("inline.to.anonymous.no.multiple.interfaces");
    }
    if (interfaces.length == 1) {
      if (interfaces [0].resolve() == null) {
        return "Class cannot be inlined because an interface implemented by it cannot be resolved";
      }
      final PsiClass superClass = psiClass.getSuperClass();
      if (superClass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        PsiClassType interfaceType = interfaces[0];
        if (!isRedundantImplements(superClass, interfaceType)) {
          return RefactoringBundle.message("inline.to.anonymous.no.superclass.and.interface");
        }
      }
    }

    final PsiMethod[] methods = psiClass.getMethods();
    for(PsiMethod method: methods) {
      if (method.isConstructor()) {
        if (PsiUtil.findReturnStatements(method).length > 0) {
          return "Class cannot be inlined because its constructor contains 'return' statements";
        }
      }
      else if (method.findSuperMethods().length == 0) {
        if (!ReferencesSearch.search(method).forEach(new AllowedUsagesProcessor(psiClass))) {
          return "Class cannot be inlined because there are usages of its methods not inherited from its superclass or interface";
        }
      }
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return "Class cannot be inlined because it has static methods";
      }
    }

    final PsiClass[] innerClasses = psiClass.getInnerClasses();
    for(PsiClass innerClass: innerClasses) {
      PsiModifierList classModifiers = innerClass.getModifierList();
      if (classModifiers.hasModifierProperty(PsiModifier.STATIC)) {
        return "Class cannot be inlined because it has static inner classes";
      }
      if (!ReferencesSearch.search(innerClass).forEach(new AllowedUsagesProcessor(psiClass))) {
        return "Class cannot be inlined because it has usages of its inner classes";
      }
    }

    final PsiField[] fields = psiClass.getFields();
    for(PsiField field: fields) {
      final PsiModifierList fieldModifiers = field.getModifierList();
      if (fieldModifiers != null && fieldModifiers.hasModifierProperty(PsiModifier.STATIC)) {
        if (!fieldModifiers.hasModifierProperty(PsiModifier.FINAL)) {
          return "Class cannot be inlined because it has static non-final fields";
        }
        Object initValue = null;
        final PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          initValue = JavaPsiFacade.getInstance(psiClass.getProject()).getConstantEvaluationHelper().computeConstantExpression(initializer);
        }
        if (initValue == null) {
          return "Class cannot be inlined because it has static fields with non-constant initializers";
        }
      }
      if (!ReferencesSearch.search(field).forEach(new AllowedUsagesProcessor(psiClass))) {
        return "Class cannot be inlined because it has usages of fields not inherited from its superclass";
      }
    }

    final PsiClassInitializer[] initializers = psiClass.getInitializers();
    for(PsiClassInitializer initializer: initializers) {
      final PsiModifierList modifiers = initializer.getModifierList();
      if (modifiers != null && modifiers.hasModifierProperty(PsiModifier.STATIC)) {
        return "Class cannot be inlined because it has static initializers";
      }
    }

    return getCannotInlineDueToUsagesMessage(psiClass);
  }

  static boolean isRedundantImplements(@NotNull final PsiClass superClass, final PsiClassType interfaceType) {
    boolean redundantImplements = false;
    PsiClassType[] superClassInterfaces = superClass.getImplementsListTypes();
    for(PsiClassType superClassInterface: superClassInterfaces) {
      if (superClassInterface.equals(interfaceType)) {
        redundantImplements = true;
        break;
      }
    }
    return redundantImplements;
  }

  @Nullable
  private static String getCannotInlineDueToUsagesMessage(final PsiClass aClass) {
    boolean hasUsages = false;
    for(PsiReference reference : ReferencesSearch.search(aClass)) {
      final PsiElement element = reference.getElement();
      if (element == null) continue;
      if (!PsiTreeUtil.isAncestor(aClass, element, false)) {
        hasUsages = true;
      }
      final PsiElement parentElement = element.getParent();
      if (parentElement != null) {
        final PsiElement grandPa = parentElement.getParent();
        if (grandPa instanceof PsiClassObjectAccessExpression) {
          return "Class cannot be inlined because it has usages of its class literal";
        }
        if (ourCatchClausePattern.accepts(parentElement)) {
          return "Class cannot be inlined because it is used in a 'catch' clause";
        }
      }
      if (ourThrowsClausePattern.accepts(element)) {
        return "Class cannot be inlined because it is used in a 'throws' clause";
      }
      if (parentElement instanceof PsiThisExpression) {
        return "Class cannot be inlined because it is used as a 'this' qualifier";
      }
      if (parentElement instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)parentElement;
        final PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length == 0) {
          PsiExpressionList newArgumentList = newExpression.getArgumentList();
          if (newArgumentList != null && newArgumentList.getExpressions().length > 0) {
            return "Class cannot be inlined because a call to its constructor is unresolved";
          }
        }
        else {
          final JavaResolveResult resolveResult = newExpression.resolveMethodGenerics();
          if (!resolveResult.isValidResult()) {
            return "Class cannot be inlined because a call to its constructor is unresolved";
          }
        }
      }
    }
    if (!hasUsages) {
      return RefactoringBundle.message("class.is.never.used");
    }
    return null;
  }

  private static class AllowedUsagesProcessor implements Processor<PsiReference> {
    private final PsiElement myPsiElement;

    public AllowedUsagesProcessor(final PsiElement psiElement) {
      myPsiElement = psiElement;
    }

    @Override
    public boolean process(final PsiReference psiReference) {
      if (PsiTreeUtil.isAncestor(myPsiElement, psiReference.getElement(), false)) {
        return true;
      }
      PsiElement element = psiReference.getElement();
      if (element instanceof PsiReferenceExpression) {
        PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
        while (qualifier instanceof PsiParenthesizedExpression) {
          qualifier = ((PsiParenthesizedExpression) qualifier).getExpression();
        }
        if (qualifier instanceof PsiNewExpression) {
          PsiNewExpression newExpr = (PsiNewExpression) qualifier;
          PsiJavaCodeReferenceElement classRef = newExpr.getClassReference();
          if (classRef != null && myPsiElement.equals(classRef.resolve())) {
            return true;
          }
        }
      }
      return false;
    }
  }
}