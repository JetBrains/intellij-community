/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.SmartHashSet;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class DebuggerTreeNodeExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression");

//  private static PsiExpression beautifyExpression(PsiExpression expression) throws IncorrectOperationException {
//    final PsiElementFactory elementFactory = expression.getManager().getElementFactory();
//    final PsiParenthesizedExpression utility = (PsiParenthesizedExpression)elementFactory.createExpressionFromText(
//      "(expr)", expression.getContext());
//    utility.getExpression().replace(expression);
//
//    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
//      @Override public void visitTypeCastExpression(PsiTypeCastExpression expression) {
//        try {
//          super.visitTypeCastExpression(expression);
//
//          PsiElement parent;
//          PsiElement toBeReplaced = expression;
//          for (parent = expression.getParent();
//               parent instanceof PsiParenthesizedExpression && parent != utility;
//               parent = parent.getParent()) {
//            toBeReplaced = parent;
//          }
//
//          if (parent instanceof PsiReferenceExpression) {
//            PsiReferenceExpression reference = ((PsiReferenceExpression)parent);
//            //((TypeCast)).member
//            PsiElement oldResolved = reference.resolve();
//
//            if (oldResolved != null) {
//              PsiReferenceExpression newReference = ((PsiReferenceExpression)reference.copy());
//              newReference.getQualifierExpression().replace(expression.getOperand());
//              PsiElement newResolved = newReference.resolve();
//
//              if (oldResolved == newResolved) {
//                toBeReplaced.replace(expression.getOperand());
//              }
//              else if (newResolved instanceof PsiMethod && oldResolved instanceof PsiMethod) {
//                if (isSuperMethod((PsiMethod)newResolved, (PsiMethod)oldResolved)) {
//                  toBeReplaced.replace(expression.getOperand());
//                }
//              }
//            }
//          }
//          else {
//            toBeReplaced.replace(expression.getOperand());
//          }
//        }
//        catch (IncorrectOperationException e) {
//          throw new IncorrectOperationRuntimeException(e);
//        }
//      }
//
//      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
//        expression.acceptChildren(this);
//
//        try {
//          JavaResolveResult resolveResult = expression.advancedResolve(false);
//
//          PsiElement oldResolved = resolveResult.getElement();
//
//          if(oldResolved == null) return;
//
//          PsiReferenceExpression newReference;
//          if (expression instanceof PsiMethodCallExpression) {
//            int length = expression.getQualifierExpression().getTextRange().getLength();
//            PsiMethodCallExpression methodCall = (PsiMethodCallExpression)elementFactory.createExpressionFromText(
//              expression.getText().substring(length), expression.getContext());
//            newReference = methodCall.getMethodExpression();
//          }
//          else {
//            newReference =
//            (PsiReferenceExpression)elementFactory.createExpressionFromText(expression.getReferenceName(),
//                                                                            expression.getContext());
//          }
//
//          PsiElement newResolved = newReference.resolve();
//          if (oldResolved == newResolved) {
//            expression.replace(newReference);
//          }
//        }
//        catch (IncorrectOperationException e) {
//          LOG.debug(e);
//        }
//      }
//    };
//
//    try {
//      utility.accept(visitor);
//    }
//    catch (IncorrectOperationRuntimeException e) {
//      throw e.getException();
//    }
//    return utility.getExpression();
//  }

  private static boolean isSuperMethod(PsiMethod superMethod, PsiMethod overridingMethod) {
    PsiMethod[] superMethods = overridingMethod.findSuperMethods();
    for (PsiMethod method : superMethods) {
      if (method == superMethod || isSuperMethod(superMethod, method)) {
        return true;
      }
    }
      return false;
    }

  @Nullable
  public static PsiExpression substituteThis(@Nullable PsiElement expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue)
    throws EvaluateException {
    if (!(expressionWithThis instanceof PsiExpression)) return null;
    PsiExpression result = (PsiExpression)expressionWithThis.copy();

    PsiClass thisClass = PsiTreeUtil.getContextOfType(result, PsiClass.class, true);

    boolean castNeeded = true;

    if (thisClass != null) {
      PsiType type = howToEvaluateThis.getType();
      if(type != null) {
        if(type instanceof PsiClassType) {
          PsiClass psiClass = ((PsiClassType) type).resolve();
          if(psiClass != null && (psiClass == thisClass || psiClass.isInheritor(thisClass, true))) {
            castNeeded = false;
          }
        }
        else if(type instanceof PsiArrayType) {
          LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expressionWithThis);
          if(thisClass == JavaPsiFacade.getInstance(expressionWithThis.getProject()).getElementFactory().getArrayClass(languageLevel)) {
            castNeeded = false;
          }
        }
      }
    }

    if (castNeeded) {
      howToEvaluateThis = castToRuntimeType(howToEvaluateThis, howToEvaluateThisValue);
    }

    ChangeContextUtil.encodeContextInfo(result, false);
    PsiExpression psiExpression;
    try {
      psiExpression = (PsiExpression) ChangeContextUtil.decodeContextInfo(result, thisClass, howToEvaluateThis);
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(
        DebuggerBundle.message("evaluation.error.invalid.this.expression", result.getText(), howToEvaluateThis.getText()), null);
    }

    try {
      PsiExpression res = JavaPsiFacade.getInstance(howToEvaluateThis.getProject()).getElementFactory()
        .createExpressionFromText(psiExpression.getText(), howToEvaluateThis.getContext());
      res.putUserData(ADDITIONAL_IMPORTS_KEY, howToEvaluateThis.getUserData(ADDITIONAL_IMPORTS_KEY));
      return res;
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(e.getMessage(), e);
    }
  }

  public static final Key<Set<String>> ADDITIONAL_IMPORTS_KEY = Key.create("ADDITIONAL_IMPORTS");

  public static PsiExpression castToRuntimeType(PsiExpression expression, Value value) throws EvaluateException {
    if (!(value instanceof ObjectReference)) {
      return expression;
    }
    
    ReferenceType valueType = ((ObjectReference)value).referenceType();
    if (valueType == null) {
      return expression;
    }
    
    Project project = expression.getProject();

    PsiType type = RuntimeTypeEvaluator.getCastableRuntimeType(project, value);
    if (type == null) {
      return expression;
    }

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    String typeName = type.getCanonicalText();
    try {
      PsiParenthesizedExpression parenthExpression = (PsiParenthesizedExpression)elementFactory.createExpressionFromText(
        "((" + typeName + ")expression)", null);
      //noinspection ConstantConditions
      ((PsiTypeCastExpression)parenthExpression.getExpression()).getOperand().replace(expression);
      Set<String> imports = expression.getUserData(ADDITIONAL_IMPORTS_KEY);
      if (imports == null) {
        imports = new SmartHashSet<>();
      }
      imports.add(typeName);
      parenthExpression.putUserData(ADDITIONAL_IMPORTS_KEY, imports);
      return parenthExpression;
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.type.name", typeName), e);
    }
  }

  /**
   * @param qualifiedName the class qualified name to be resolved against the current execution context
   * @return short name if the class could be resolved using short name,
   * otherwise returns qualifiedName
   */
  public static String normalize(final String qualifiedName, PsiElement contextElement, Project project) {
    if (contextElement == null) {
      return qualifiedName;
    }

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass aClass = facade.findClass(qualifiedName, GlobalSearchScope.allScope(project));
    if (aClass != null) {
      return normalizePsiClass(aClass, contextElement, facade.getResolveHelper());
    }
    return qualifiedName;
  }

  private static String normalizePsiClass(PsiClass psiClass, PsiElement contextElement, PsiResolveHelper helper) {
    String name = psiClass.getName();
    PsiClass aClass = helper.resolveReferencedClass(name, contextElement);
    if (psiClass.equals(aClass)) {
      return name;
    }
    PsiClass parentClass = psiClass.getContainingClass();
    if (parentClass != null) {
      return normalizePsiClass(parentClass, contextElement, helper) + "." + name;
    }
    return psiClass.getQualifiedName();
  }

  public static PsiExpression getEvaluationExpression(DebuggerTreeNodeImpl node, DebuggerContextImpl context) throws EvaluateException {
    if(node.getDescriptor() instanceof ValueDescriptorImpl) {
      throw new IllegalStateException("Not supported any more");
      //return ((ValueDescriptorImpl)node.getDescriptor()).getTreeEvaluation(node, context);
    }
    else {
      LOG.error(node.getDescriptor() != null ? node.getDescriptor().getClass().getName() : "null");
      return null;
    }
  }

  public static TextWithImports createEvaluationText(final DebuggerTreeNodeImpl node, final DebuggerContextImpl context) throws EvaluateException {
    final EvaluateException[] ex = new EvaluateException[] {null};
    final TextWithImports textWithImports = PsiDocumentManager.getInstance(context.getProject()).commitAndRunReadAction(
      (Computable<TextWithImports>)() -> {
        try {
          final PsiExpression expressionText = getEvaluationExpression(node, context);
          if (expressionText != null) {
            return new TextWithImportsImpl(expressionText);
          }
        }
        catch (EvaluateException e) {
          ex[0] = e;
        }
        return null;
      });
    if (ex[0] != null) {
      throw ex[0];
    }
    return textWithImports;
  }

  private static class IncorrectOperationRuntimeException extends RuntimeException {
    private final IncorrectOperationException myException;

    public IncorrectOperationRuntimeException(IncorrectOperationException exception) {
      myException = exception;
    }

    public IncorrectOperationException getException() { return myException; }
  }
}
