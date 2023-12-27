// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * java "decorative" highlighting:
 * - color names, like "reassigned variables"/fields/statics etc.
 * - soft keywords
 * NO COMPILATION ERRORS
 * for other highlighting errors see {@link HighlightVisitorImpl}
 */
final class JavaNamesHighlightVisitor extends JavaElementVisitor implements HighlightVisitor, DumbAware {
  private HighlightInfoHolder myHolder;
  private PsiFile myFile;
  private LanguageLevel myLanguageLevel;
  private boolean shouldHighlightSoftKeywords;

  @NotNull
  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public JavaNamesHighlightVisitor clone() {
    return new JavaNamesHighlightVisitor();
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    // both PsiJavaFile and PsiCodeFragment must match
    return file instanceof PsiImportHolder && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file);
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    element.accept(this);
  }

  @Override
  public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable highlight) {
    try {
      prepare(holder, file);
      highlight.run();
    }
    finally {
      myFile = null;
      myHolder = null;
    }

    return true;
  }

  private void prepare(@NotNull HighlightInfoHolder holder, @NotNull PsiFile file) {
    myHolder = holder;
    myFile = file;
    myLanguageLevel = PsiUtil.getLanguageLevel(file);
    shouldHighlightSoftKeywords = PsiJavaModule.MODULE_INFO_FILE.equals(file.getName()) || myLanguageLevel.isAtLeast(LanguageLevel.JDK_10);
  }

  @Override
  public void visitDocTagValue(@NotNull PsiDocTagValue value) {
    PsiReference reference = computeIfSmartMode(value.getProject(), () -> value.getReference());
    if (reference != null) {
      PsiElement element = computeIfSmartMode(value.getProject(), () -> reference.resolve());
      TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (element instanceof PsiMethod) {
        PsiElement nameElement = ((PsiDocMethodOrFieldRef)value).getNameElement();
        if (nameElement != null) {
          myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)element, nameElement, false, colorsScheme));
        }
      }
      else if (element instanceof PsiParameter) {
        myHolder.add(HighlightNamesUtil.highlightVariableName((PsiVariable)element, value.getNavigationElement(), colorsScheme));
      }
    }
  }


  @Override
  public void visitIdentifier(@NotNull PsiIdentifier identifier) {
    TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable variable) {
      if (variable.getInitializer() == null) {
        PsiElement child = variable.getLastChild();
        if (child instanceof PsiErrorElement && child.getPrevSibling() == identifier) return;
      }
 
      myHolder.add(HighlightNamesUtil.highlightVariableName(variable, identifier, colorsScheme));
    }
    else if (parent instanceof PsiClass aClass) {
      if (!(parent instanceof PsiAnonymousClass) && aClass.getNameIdentifier() == identifier) {
        myHolder.add(HighlightNamesUtil.highlightClassName(aClass, identifier, colorsScheme));
      }
    }
    else if (parent instanceof PsiMethod method) {
      myHolder.add(HighlightNamesUtil.highlightMethodName(method, identifier, true, colorsScheme));
    }

    super.visitIdentifier(identifier);
  }

  @Override
  public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement ref) {
    JavaResolveResult[] results = computeIfSmartMode(ref.getProject(), () -> ref.multiResolve(false));
    if (results == null) {
      results = JavaResolveResult.EMPTY_ARRAY;
    }

    PsiElement referenceNameElement = ref.getReferenceNameElement();
    if (!myHolder.hasErrorResults()) {
      PsiElement resolved = results.length >= 1 ? results[0].getElement() : null;
      if (results.length > 1) {
        for (int i = 1; i < results.length; i++) {
          PsiElement element = results[i].getElement();
          if (resolved instanceof PsiMethod && !(element instanceof PsiMethod) ||
              resolved instanceof PsiVariable && !(element instanceof PsiVariable) ||
              resolved instanceof PsiClass && !(element instanceof PsiClass)) {
            resolved = null;
            break;
          }
        }
      }
      TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (resolved instanceof PsiClass) {
        myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, ref, colorsScheme));
      }
      else {
        if (referenceNameElement != null) {
          if (resolved instanceof PsiVariable) {
            myHolder.add(HighlightNamesUtil.highlightVariableName((PsiVariable)resolved, referenceNameElement, colorsScheme));
          }
          else if (resolved instanceof PsiMethod) {
            myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)resolved, referenceNameElement, false, colorsScheme));
          }
        }
      }
    }
  }

  @Override
  public void visitKeyword(@NotNull PsiKeyword keyword) {
    if (shouldHighlightSoftKeywords &&
        (JavaLexer.isSoftKeyword(keyword.getNode().getChars(), myLanguageLevel) || JavaTokenType.NON_SEALED_KEYWORD == keyword.getTokenType())) {
      myHolder.add(HighlightNamesUtil.highlightKeyword(keyword));
    }
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    doVisitReferenceElement(ref);
  }
  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    doVisitReferenceElement(expression);
  }

  private void doVisitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = HighlightVisitorImpl.resolveOptimised(ref, myFile);
    if (result == null) return;

    PsiElement resolved = result.getElement();

    if (resolved instanceof PsiVariable variable) {
      if (!(variable instanceof PsiField)) {
        PsiElement containingClass = PsiTreeUtil.getNonStrictParentOfType(ref, PsiClass.class, PsiLambdaExpression.class);
        while ((containingClass instanceof PsiAnonymousClass || containingClass instanceof PsiLambdaExpression) &&
               !PsiTreeUtil.isAncestor(containingClass, variable, false)) {
          if (containingClass instanceof PsiLambdaExpression ||
              !PsiTreeUtil.isAncestor(((PsiAnonymousClass)containingClass).getArgumentList(), ref, false)) {
            myHolder.add(HighlightNamesUtil.highlightImplicitAnonymousClassParameter(ref));
            break;
          }
          containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, PsiLambdaExpression.class);
        }
      }

      TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      PsiElement nameElement = ref.getReferenceNameElement();
      if (nameElement != null) {
        myHolder.add(HighlightNamesUtil.highlightVariableName(variable, nameElement, colorsScheme));
      }
    }
    else {
      highlightReferencedMethodOrClassName(ref, resolved);
    }
  }

  private void highlightReferencedMethodOrClassName(@NotNull PsiJavaCodeReferenceElement element, @Nullable PsiElement resolved) {
    PsiElement parent = element.getParent();
    TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethod method = ((PsiMethodCallExpression)parent).resolveMethod();
      PsiElement methodNameElement = element.getReferenceNameElement();
      if (method != null && methodNameElement != null&& !(methodNameElement instanceof PsiKeyword)) {
        myHolder.add(HighlightNamesUtil.highlightMethodName(method, methodNameElement, false, colorsScheme));
      }
    }
    else if (parent instanceof PsiConstructorCall) {
      try {
        PsiMethod method = ((PsiConstructorCall)parent).resolveConstructor();
        PsiMember methodOrClass = method != null ? method : resolved instanceof PsiClass ? (PsiClass)resolved : null;
        if (methodOrClass != null) {
          PsiElement referenceNameElement = element.getReferenceNameElement();
          if(referenceNameElement != null) {
            // exclude type parameters from the highlighted text range
            myHolder.add(HighlightNamesUtil.highlightMethodName(methodOrClass, referenceNameElement, false, colorsScheme));
          }
        }
      }
      catch (IndexNotReadyException ignored) { }
    }
    else if (resolved instanceof PsiPackage) {
      // highlight package (and following dot) as a class
      myHolder.add(HighlightNamesUtil.highlightPackage(resolved, element, colorsScheme));
    }
    else if (resolved instanceof PsiClass) {
      myHolder.add(HighlightNamesUtil.highlightClassName((PsiClass)resolved, element, colorsScheme));
    }
    else if (element.getParent() instanceof PsiAnnotation) {
      myHolder.add(HighlightNamesUtil.highlightClassName(null, element, colorsScheme));
    }
    else if (PsiTreeUtil.skipParentsOfType(element, PsiJavaCodeReferenceElement.class) instanceof PsiAnnotation) {
      myHolder.add(HighlightNamesUtil.highlightPackage(null, element, colorsScheme));
    }
  }

  @Override
  public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
    PsiIdentifier nameId = pair.getNameIdentifier();
    if (nameId != null) {
      myHolder.add(HighlightNamesUtil.highlightAnnotationAttributeName(nameId));
    }
  }

  @Override
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
    JavaResolveResult result;
    JavaResolveResult[] results;
    try {
      results = computeIfSmartMode(expression.getProject(), () -> expression.multiResolve(true));
      result = results != null && results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    }
    catch (IndexNotReadyException e) {
      return;
    }
    PsiElement method = result.getElement();
    if (!(method instanceof PsiJvmMember) || result.isAccessible()) {
      TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (method instanceof PsiMethod && !expression.isConstructor()) {
        PsiElement methodNameElement = expression.getReferenceNameElement();
        if (methodNameElement != null) {
          myHolder.add(HighlightNamesUtil.highlightMethodName((PsiMethod)method, methodNameElement, false, colorsScheme));
        }
      }
    }
  }

  private <T> T computeIfSmartMode(@NotNull Project project, Supplier<T> operation) {
    if (DumbService.isDumb(project)) return null;
    return operation.get();
  }
}
