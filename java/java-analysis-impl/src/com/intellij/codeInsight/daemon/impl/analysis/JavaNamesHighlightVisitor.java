// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.JavaHighlightInfoTypes;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

// java highlighting: color names like "reassigned variables"/fields/statics etc.
// NO ERRORS, only INFORMATION (maybe with custom text attributes)
// for highlighting error see HighlightVisitorImpl
class JavaNamesHighlightVisitor extends JavaElementVisitor implements HighlightVisitor {
  private HighlightInfoHolder myHolder;
  private PsiFile myFile;

  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new HashMap<>();

  private enum ReassignedState {
    DUNNO, INSIDE_FILE, REASSIGNED
  }
  private final Map<PsiParameter, ReassignedState> myReassignedParameters = new HashMap<>();

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
      myFinalVarProblems.clear();
      myReassignedParameters.clear();
      myFile = null;
      myHolder = null;
    }

    return true;
  }

  private void prepare(@NotNull HighlightInfoHolder holder, @NotNull PsiFile file) {
    myHolder = holder;
    myFile = file;
  }

  @Override
  public void visitDocTagValue(PsiDocTagValue value) {
    PsiReference reference = value.getReference();
    if (reference != null) {
      PsiElement element = reference.resolve();
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
  public void visitIdentifier(PsiIdentifier identifier) {
    TextAttributesScheme colorsScheme = myHolder.getColorsScheme();

    PsiElement parent = identifier.getParent();
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;

      if (variable.getInitializer() == null) {
        PsiElement child = variable.getLastChild();
        if (child instanceof PsiErrorElement && child.getPrevSibling() == identifier) return;
      }

      boolean isMethodParameter = variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() instanceof PsiMethod;
      if (isMethodParameter) {
        myReassignedParameters.put((PsiParameter)variable, ReassignedState.INSIDE_FILE); // mark param as present in current file
      }
      else {
        // method params are highlighted in visitMethod since we should make sure the method body was visited before
        if (HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems)) {
          myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, identifier));
        }
        else {
          myHolder.add(HighlightNamesUtil.highlightVariableName(variable, identifier, colorsScheme));
        }
      }
    }
    else if (parent instanceof PsiClass) {
      PsiClass aClass = (PsiClass)parent;

      if (!(parent instanceof PsiAnonymousClass) && aClass.getNameIdentifier() == identifier) {
        myHolder.add(HighlightNamesUtil.highlightClassName(aClass, identifier, colorsScheme));
      }
    }
    else if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      myHolder.add(HighlightNamesUtil.highlightMethodName(method, identifier, true, colorsScheme));
    }

    super.visitIdentifier(identifier);
  }

  @Override
  public void visitImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement ref) {
    JavaResolveResult[] results = ref.multiResolve(false);

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
  public void visitMethod(PsiMethod method) {
    super.visitMethod(method);

    // method params are highlighted in visitMethod since we should make sure the method body was visited before
    PsiParameter[] parameters = method.getParameterList().getParameters();

    for (PsiParameter parameter : parameters) {
      ReassignedState info = myReassignedParameters.getOrDefault(parameter, ReassignedState.DUNNO);
      if (info == ReassignedState.DUNNO) continue; // out of this file

      PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
      if (nameIdentifier != null) {
        if (info == ReassignedState.REASSIGNED) { // reassigned
          myHolder.add(HighlightNamesUtil.highlightReassignedVariable(parameter, nameIdentifier));
        }
        else {
          myHolder.add(HighlightNamesUtil.highlightVariableName(parameter, nameIdentifier, myHolder.getColorsScheme()));
        }
      }
    }
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement ref) {
    doVisitReferenceElement(ref);
  }
  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    doVisitReferenceElement(expression);
  }

  private boolean isReassigned(@NotNull PsiVariable variable) {
    try {
      boolean reassigned;
      if (variable instanceof PsiParameter) {
        reassigned = myReassignedParameters.get(variable) == ReassignedState.REASSIGNED;
      }
      else  {
        reassigned = HighlightControlFlowUtil.isReassigned(variable, myFinalVarProblems);
      }

      return reassigned;
    }
    catch (IndexNotReadyException e) {
      return false;
    }
  }

  private void doVisitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result = HighlightVisitorImpl.resolveOptimised(ref, myFile);
    if (result == null) return;

    PsiElement resolved = result.getElement();

    if (resolved instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)resolved;

      if (!(variable instanceof PsiField)) {
        PsiElement containingClass = PsiTreeUtil.getNonStrictParentOfType(ref, PsiClass.class, PsiLambdaExpression.class);
        while ((containingClass instanceof PsiAnonymousClass || containingClass instanceof PsiLambdaExpression) &&
               !PsiTreeUtil.isAncestor(containingClass, variable, false)) {
          if (containingClass instanceof PsiLambdaExpression ||
              !PsiTreeUtil.isAncestor(((PsiAnonymousClass)containingClass).getArgumentList(), ref, false)) {
            myHolder.add(HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.IMPLICIT_ANONYMOUS_CLASS_PARAMETER).range(ref).create());
            break;
          }
          containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class, PsiLambdaExpression.class);
        }
      }

      if (variable instanceof PsiParameter && ref instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)ref)) {
        myReassignedParameters.put((PsiParameter)variable, ReassignedState.REASSIGNED);
      }

      TextAttributesScheme colorsScheme = myHolder.getColorsScheme();
      if (!variable.hasModifierProperty(PsiModifier.FINAL) && isReassigned(variable)) {
        myHolder.add(HighlightNamesUtil.highlightReassignedVariable(variable, ref));
      }
      else {
        PsiElement nameElement = ref.getReferenceNameElement();
        if (nameElement != null) {
          myHolder.add(HighlightNamesUtil.highlightVariableName(variable, nameElement, colorsScheme));
        }
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
  }

  @Override
  public void visitNameValuePair(PsiNameValuePair pair) {
    PsiIdentifier nameId = pair.getNameIdentifier();
    if (nameId != null) {
      myHolder.add(HighlightInfo.newHighlightInfo(JavaHighlightInfoTypes.ANNOTATION_ATTRIBUTE_NAME).range(nameId).create());
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    JavaResolveResult result;
    JavaResolveResult[] results;
    try {
      results = expression.multiResolve(true);
      result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
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
}
