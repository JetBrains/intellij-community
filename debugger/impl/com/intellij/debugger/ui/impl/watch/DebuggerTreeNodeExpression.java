package com.intellij.debugger.ui.impl.watch;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.java.LanguageLevel;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;

/**
 * User: lex
 * Date: Oct 29, 2003
 * Time: 9:24:52 PM
 */
public class DebuggerTreeNodeExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression");

//  private static PsiExpression beautifyExpression(PsiExpression expression) throws IncorrectOperationException {
//    final PsiElementFactory elementFactory = expression.getManager().getElementFactory();
//    final PsiParenthesizedExpression utility = (PsiParenthesizedExpression)elementFactory.createExpressionFromText(
//      "(expr)", expression.getContext());
//    utility.getExpression().replace(expression);
//
//    PsiRecursiveElementVisitor visitor = new PsiRecursiveElementVisitor() {
//      public void visitTypeCastExpression(PsiTypeCastExpression expression) {
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
//      public void visitReferenceExpression(PsiReferenceExpression expression) {
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
      for (int i = 0; i < superMethods.length; i++) {
        if (superMethods[i] == superMethod) {
          return true;
        }
        else if (isSuperMethod(superMethod, superMethods[i])) {
          return true;
        }
      }
      return false;
    }

  public static PsiExpression substituteThis(PsiExpression expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue)
    throws EvaluateException {
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
          if(thisClass == expressionWithThis.getManager().getElementFactory().getArrayClass(languageLevel)) {
            castNeeded = false;
          }
        }
      }
    }

    if (castNeeded) {
      howToEvaluateThis = castToRuntimeType(howToEvaluateThis, howToEvaluateThisValue, howToEvaluateThis.getContext());
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
      return howToEvaluateThis.getManager().getElementFactory().createExpressionFromText(psiExpression.getText(), howToEvaluateThis.getContext());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  public static PsiExpression castToRuntimeType(PsiExpression expression, Value value, PsiElement contextElement) throws EvaluateException {
    if (value instanceof ObjectReference) {
      return castToType(expression, ((ObjectReference)value).referenceType(), contextElement);
    }
    else {
      return expression;
    }
  }

  private static PsiExpression castToType(PsiExpression expression, ReferenceType valueType, PsiElement contextElement) throws EvaluateException{
    if (valueType != null) {
      Project project = expression.getProject();

      String typeName = DebuggerUtilsEx.getQualifiedClassName(valueType.name(), project);
      PsiManager manager = PsiManager.getInstance(project);

      typeName =  normalize(typeName, contextElement, project);

      PsiElementFactory elementFactory = manager.getElementFactory();
      try {
        PsiParenthesizedExpression parenthExpression = (PsiParenthesizedExpression)elementFactory.createExpressionFromText(
          "((" + typeName + ")expression)", null);
        ((PsiTypeCastExpression)parenthExpression.getExpression()).getOperand().replace(expression);
        return parenthExpression;
      }
      catch (IncorrectOperationException e) {
        throw new EvaluateException(DebuggerBundle.message("error.invalid.type.name", typeName), e);
      }
    }
    else {
      return expression;
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

    final PsiManager psiManager = PsiManager.getInstance(project);
    PsiClass aClass = psiManager.findClass(qualifiedName, GlobalSearchScope.allScope(project));
    if (aClass != null) {
      return normalizePsiClass(aClass, contextElement, psiManager.getResolveHelper());
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
      return ((ValueDescriptorImpl)node.getDescriptor()).getTreeEvaluation(node, context);
    }
    else {
      LOG.assertTrue(false, node.getDescriptor() != null ? node.getDescriptor().getClass().getName() : "null");
      return null;
    }
  }

  public static TextWithImports createEvaluationText(final DebuggerTreeNodeImpl node, final DebuggerContextImpl context) throws EvaluateException {
    final EvaluateException[] ex = new EvaluateException[] {null};
    final TextWithImports textWithImports = PsiDocumentManager.getInstance(context.getProject()).commitAndRunReadAction(new Computable<TextWithImports>() {
      public TextWithImports compute() {
        try {
          PsiExpression expressionText = getEvaluationExpression(node, context);
          return new TextWithImportsImpl(expressionText);
        }
        catch (EvaluateException e) {
          ex[0] = e;
        }
        return null;
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    return textWithImports;
  }

  private static class IncorrectOperationRuntimeException extends RuntimeException {
    private IncorrectOperationException myException;

    public IncorrectOperationRuntimeException(IncorrectOperationException exception) {
      myException = exception;
    }

    public IncorrectOperationException getException() { return myException; }
  }
}
