// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class OldReferenceResolver {
  private static final Logger LOG = Logger.getInstance(OldReferenceResolver.class);

  private final PsiCall myContext;
  private final PsiExpression myExpr;
  private final HashMap<PsiExpression, String> myTempVars;
  private final PsiExpression myInstanceRef;
  private final PsiExpression[] myActualArgs;
  private final PsiMethod myMethodToReplaceIn;
  private final Project myProject;
  private final PsiManager myManager;
  private final int myReplaceFieldsWithGetters;
  private final PsiElement myParameterInitializer;

  public OldReferenceResolver(PsiCall context,
                              PsiExpression expr,
                              PsiMethod methodToReplaceIn,
                              int replaceFieldsWithGetters,
                              PsiElement parameterInitializer) throws IncorrectOperationException {
    myContext = context;
    myExpr = expr;
    myReplaceFieldsWithGetters = replaceFieldsWithGetters;
    myParameterInitializer = parameterInitializer;
    myTempVars = new HashMap<>();
    myActualArgs = myContext.getArgumentList().getExpressions();
    myMethodToReplaceIn = methodToReplaceIn;
    myProject = myContext.getProject();
    myManager = myContext.getManager();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    PsiExpression instanceRef;
    if (myContext instanceof PsiMethodCallExpression methodCall) {
      final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
      instanceRef = methodExpression.getQualifierExpression();
      if (instanceRef == null) {
        final PsiClass thisResolveClass = RefactoringUtil.getThisResolveClass(methodExpression);
        if (thisResolveClass != null &&
            !(thisResolveClass instanceof PsiAnonymousClass) &&
            !thisResolveClass.equals(PsiTreeUtil.getParentOfType(methodExpression, PsiClass.class))) {
          //Qualified this needed
          instanceRef = factory.createExpressionFromText(thisResolveClass.getName() + ".this", null);
        }
      }
    }
    else {
      instanceRef = null;
    }
    myInstanceRef = instanceRef;
  }

  public void resolve(boolean varargs) throws IncorrectOperationException {
    resolveOldReferences(myExpr, myParameterInitializer, varargs);

    Set<Map.Entry<PsiExpression, String>> mappingsSet = myTempVars.entrySet();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);

    replaceOldRefWithNew(mappingsSet, factory);
  }

  private static void replaceOldRefWithNew(Set<Map.Entry<PsiExpression, String>> mappingsSet, PsiElementFactory factory) {
    for (Map.Entry<PsiExpression, String> entry : mappingsSet) {
      PsiExpression oldRef = entry.getKey();
      PsiElement newRef = factory.createExpressionFromText(entry.getValue(), null);
      oldRef.replace(newRef);
    }
  }


  private void resolveOldReferences(PsiElement expr, PsiElement oldExpr, boolean varargs) throws IncorrectOperationException {
    if (expr == null || !expr.isValid() || oldExpr == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    PsiElement newExpr = expr;  // references continue being resolved in the children of newExpr

    if (oldExpr instanceof PsiReferenceExpression oldRef) {
      final JavaResolveResult adv = oldRef.advancedResolve(false);
      final PsiElement scope = getClassContainingResolve(adv);
      final PsiElement clss = PsiTreeUtil.getParentOfType(oldExpr, PsiClass.class, PsiFunctionalExpression.class);
      if (clss != null && scope != null ) {

        final PsiElement subj = adv.getElement();


        // Parameters
        if (subj instanceof PsiParameter parameter) {
          PsiParameterList parameterList = myMethodToReplaceIn.getParameterList();

          if (subj.getParent() != parameterList) return;
          int index = parameterList.getParameterIndex(parameter);
          if (index < 0) return;
          if (index < myActualArgs.length) {
            PsiExpression actualArg = myActualArgs[index];
            PsiExpression initializer = actualArg;

            final PsiType parameterType = parameter.getType();
            if (parameter.isVarArgs() && parameterType instanceof PsiEllipsisType) {
              final String varargsJoin = StringUtil.join(ContainerUtil.map2Array(myActualArgs, String.class,
                                                                                 expression -> expression != null ? expression.getText() : "null"), index + 1, myActualArgs.length, ",");
              final String tempVar;
              if (varargs) {
                String newArrayInitializer = "new " + ((PsiEllipsisType)parameterType).toArrayType().getCanonicalText() + " {" + varargsJoin + "}";
                tempVar = getTempVar((PsiExpression)JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(factory.createExpressionFromText(newArrayInitializer, myContext)));
              }
              else {
                tempVar = myActualArgs[myActualArgs.length - 1].getText();
              }
              final Map<PsiExpression, String> map = new HashMap<>();
              if (initializer instanceof PsiReferenceExpression && Comparing.strEqual(parameter.getName(), initializer.getText())) {
                newExpr.replace(factory.createExpressionFromText(tempVar, myContext));
              }
              else {
                initializer = (PsiExpression)initializer.copy();
                initializer.accept(new JavaRecursiveElementWalkingVisitor() {
                  @Override
                  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                    super.visitReferenceExpression(expression);
                    if (Comparing.strEqual(parameter.getName(), expression.getText())) {
                      map.put(expression, tempVar);
                    }
                  }
                });
                replaceOldRefWithNew(map.entrySet(), factory);
                newExpr.replace(factory.createExpressionFromText(getTempVar(actualArg, initializer), null));
              }
              return;
            }

            if (RefactoringUtil.verifySafeCopyExpression(actualArg) == RefactoringUtil.EXPR_COPY_PROHIBITED) {
              newExpr = newExpr.replace(factory.createExpressionFromText(getTempVar(actualArg, initializer), null));
            }
            else {
              newExpr = newExpr.replace(initializer);
            }
          }
        }
        // "naked" field and methods  (should become qualified)
        else if ((subj instanceof PsiField || subj instanceof PsiMethod) && oldRef.getQualifierExpression() == null && PsiTreeUtil.isAncestor(clss, scope, false)) {

          boolean isStatic = subj instanceof PsiField && ((PsiField)subj).hasModifierProperty(PsiModifier.STATIC) ||
                             subj instanceof PsiMethod && ((PsiMethod)subj).hasModifierProperty(PsiModifier.STATIC);

          if (myInstanceRef != null && !isStatic) {
            String name = ((PsiNamedElement)subj).getName();
            PsiReferenceExpression newRef = (PsiReferenceExpression)factory.createExpressionFromText("a." + name, null);
            newRef = (PsiReferenceExpression)CodeStyleManager.getInstance(myProject).reformat(newRef);

            PsiExpression instanceRef = getInstanceRef(factory);

            newRef.getQualifierExpression().replace(instanceRef);
            newRef = (PsiReferenceExpression)newExpr.replace(newRef);
            newExpr = newRef.getReferenceNameElement();
          }
        }

        if (subj instanceof PsiField && PsiTreeUtil.isAncestor(scope, clss, false)) {
          // probably replacing field with a getter
          if (myReplaceFieldsWithGetters != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
            if (myReplaceFieldsWithGetters == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_ALL ||
                myReplaceFieldsWithGetters == IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE &&
                !JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible((PsiMember)subj, newExpr, null)) {
              newExpr = replaceFieldWithGetter(newExpr, (PsiField)subj, oldRef.getQualifierExpression() == null && !((PsiField)subj).hasModifierProperty(PsiModifier.STATIC));
            }
          }
        }
      }
    }
    else if (oldExpr instanceof PsiThisExpression &&
             (((PsiThisExpression)oldExpr).getQualifier() == null ||
              myManager
                .areElementsEquivalent(((PsiThisExpression)oldExpr).getQualifier().resolve(), myMethodToReplaceIn.getContainingClass()))) {
      if (myInstanceRef != null) {
        newExpr.replace(getInstanceRef(factory));
      }
      return;
    }
    else if (oldExpr instanceof PsiSuperExpression && ((PsiSuperExpression)oldExpr).getQualifier() == null) {
      if (myInstanceRef != null) {
        newExpr.replace(getInstanceRef(factory));
      }
      return;
    }

    PsiElement[] oldChildren = oldExpr.getChildren();
    PsiElement[] newChildren = newExpr.getChildren();

    if (oldChildren.length == newChildren.length) {
      for (int i = 0; i < oldChildren.length; i++) {
        resolveOldReferences(newChildren[i], oldChildren[i], varargs);
      }
    }
  }

  private PsiExpression getInstanceRef(PsiElementFactory factory) throws IncorrectOperationException {
    int copyingSafetyLevel = RefactoringUtil.verifySafeCopyExpression(myInstanceRef);

    PsiExpression instanceRef = myInstanceRef;
    if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
      instanceRef = factory.createExpressionFromText(getTempVar(myInstanceRef), null);
    }
    return instanceRef;
  }

  private String getTempVar(PsiExpression expr) throws IncorrectOperationException {
    return getTempVar(expr, expr);
  }

  private String getTempVar(PsiExpression expr, PsiExpression initializer) throws IncorrectOperationException {
    String id = myTempVars.get(expr);
    if (id == null) {
      id = RefactoringUtil.createTempVar(initializer, myContext, true);
      myTempVars.put(expr, id);
    }
    return id;
  }

  private PsiElement replaceFieldWithGetter(PsiElement expr, PsiField psiField, boolean qualify) throws IncorrectOperationException {
    if (RefactoringUtil.isAssignmentLHS(expr)) {
      // todo: warning
      return expr;
    }
    PsiElement newExpr = expr;

    PsiMethod getterPrototype = GenerateMembersUtil.generateGetterPrototype(psiField);

    PsiMethod getter = psiField.getContainingClass().findMethodBySignature(getterPrototype, true);

    if (getter != null) {

      if (JavaPsiFacade.getInstance(psiField.getProject()).getResolveHelper().isAccessible(getter, newExpr, null)) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(newExpr.getProject());
        String id = getter.getName();
        String qualifier = null;
        if (newExpr instanceof PsiReferenceExpression) {
          final PsiExpression instanceRef = getInstanceRef(factory);
          if (qualify && instanceRef != null) {
            qualifier = instanceRef.getText();
          } else {
            final PsiExpression qualifierExpression = ((PsiReferenceExpression)newExpr).getQualifierExpression();
            if (qualifierExpression != null) {
              qualifier = qualifierExpression.getText();
            }
          }
        }
        PsiMethodCallExpression getterCall =
          (PsiMethodCallExpression)factory.createExpressionFromText((qualifier != null ? qualifier + "." : "") + id + "()", null);
        getterCall = (PsiMethodCallExpression)CodeStyleManager.getInstance(myProject).reformat(getterCall);
        if (newExpr.getParent() != null) {
          newExpr = newExpr.replace(getterCall);
        }
        else {
          newExpr = getterCall;
        }
      }
      else {
        // todo: warning
      }
    }

    return newExpr;
  }

  @Nullable
  private static PsiElement getClassContainingResolve(final JavaResolveResult result) {
    final PsiElement elem = result.getElement();
    if (elem != null) {
      if (PsiUtil.isJvmLocalVariable(elem)) {
        return PsiTreeUtil.getParentOfType(elem, PsiClass.class);
      }
      else {
        return result.getCurrentFileResolveScope();
      }
    }
    return null;
  }
}
