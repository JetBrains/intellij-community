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
package com.intellij.refactoring.util;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author ven
 */
public class InlineUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.util.InlineUtil");

  private InlineUtil() {}

  public static PsiExpression inlineVariable(PsiVariable variable, PsiExpression initializer, PsiJavaCodeReferenceElement ref)
    throws IncorrectOperationException {
    PsiManager manager = initializer.getManager();

    PsiClass thisClass = RefactoringChangeUtil.getThisClass(initializer);
    PsiClass refParent = RefactoringChangeUtil.getThisClass(ref);
    final PsiElement parent = ref.getParent();
    boolean insertCastWhenUnchecked = parent instanceof PsiForeachStatement;
    final PsiType varType = variable.getType();
    initializer = RefactoringUtil.convertInitializerToNormalExpression(initializer, varType);
    if (initializer instanceof PsiPolyadicExpression) {
      final IElementType operationTokenType = ((PsiPolyadicExpression)initializer).getOperationTokenType();
      if ((operationTokenType == JavaTokenType.PLUS || operationTokenType == JavaTokenType.MINUS) &&
          parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType() == JavaTokenType.PLUS) {
        final PsiType type = ((PsiPolyadicExpression)parent).getType();
        if (type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(initializer.getProject());
          initializer = factory.createExpressionFromText("(" + initializer.getText() + ")", initializer);
        }
      }
    }
    solveVariableNameConflicts(initializer, ref, initializer);

    ChangeContextUtil.encodeContextInfo(initializer, false);
    PsiExpression expr = (PsiExpression)replaceDiamondWithInferredTypesIfNeeded(initializer, ref);
    PsiType exprType = expr.getType();
    if (exprType != null && (!varType.equals(exprType) && (varType instanceof PsiPrimitiveType || exprType instanceof PsiPrimitiveType)
                             || !TypeConversionUtil.isAssignable(varType, exprType)
                             || insertCastWhenUnchecked && JavaGenericsUtil.isRawToGeneric(varType, exprType))) {
      boolean matchedTypes = false;
      //try explicit type arguments
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
      if (expr instanceof PsiCallExpression && ((PsiCallExpression)expr).getTypeArguments().length == 0) {
        final JavaResolveResult resolveResult = ((PsiCallExpression)initializer).resolveMethodGenerics();
        final PsiElement resolved = resolveResult.getElement();
        if (resolved instanceof PsiMethod) {
          final PsiTypeParameter[] typeParameters = ((PsiMethod)resolved).getTypeParameters();
          if (typeParameters.length > 0) {
            final PsiCallExpression copy = (PsiCallExpression)expr.copy();
            for (final PsiTypeParameter typeParameter : typeParameters) {
              final PsiType substituted = resolveResult.getSubstitutor().substitute(typeParameter);
              if (substituted == null) break;
              copy.getTypeArgumentList().add(elementFactory.createTypeElement(substituted));
            }
            if (varType.equals(copy.getType())) {
              ((PsiCallExpression)expr).getTypeArgumentList().replace(copy.getTypeArgumentList());
              if (expr instanceof PsiMethodCallExpression) {
                final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expr).getMethodExpression();
                final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
                if (qualifierExpression == null) {
                  final PsiMethod method = (PsiMethod)resolved;
                  final PsiClass containingClass = method.getContainingClass();
                  LOG.assertTrue(containingClass != null);
                  if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                    methodExpression.setQualifierExpression(elementFactory.createReferenceExpression(containingClass));
                  } else {
                    methodExpression.setQualifierExpression(createThisExpression(manager, thisClass, refParent));
                  }
                }
              }
              matchedTypes = true;
            }
          }
        }
      }

      if (!matchedTypes) {
        if (varType instanceof PsiEllipsisType && ((PsiEllipsisType)varType).getComponentType().equals(exprType)) { //convert vararg to array

          final PsiExpressionList argumentList = PsiTreeUtil.getParentOfType(expr, PsiExpressionList.class);
          LOG.assertTrue(argumentList != null);
          final PsiExpression[] arguments = argumentList.getExpressions();

          @NonNls final StringBuilder builder = new StringBuilder("new ");
          builder.append(exprType.getCanonicalText());
          builder.append("[]{");
          builder.append(StringUtil.join(Arrays.asList(arguments), new Function<PsiExpression, String>() {
            @Override
            public String fun(final PsiExpression expr) {
              return expr.getText();
            }
          }, ","));
          builder.append('}');

          expr.replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createExpressionFromText(builder.toString(), argumentList));

        } else {
          //try cast
          PsiTypeCastExpression cast = (PsiTypeCastExpression)elementFactory.createExpressionFromText("(t)a", null);
          PsiTypeElement castTypeElement = cast.getCastType();
          assert castTypeElement != null;
          castTypeElement.replace(variable.getTypeElement());
          final PsiExpression operand = cast.getOperand();
          assert operand != null;
          operand.replace(expr);
          PsiExpression exprCopy = (PsiExpression)expr.copy();
          cast = (PsiTypeCastExpression)expr.replace(cast);
          if (!RedundantCastUtil.isCastRedundant(cast)) {
            expr = cast;
          }
          else {
            PsiElement toReplace = cast;
            while (toReplace.getParent() instanceof PsiParenthesizedExpression) {
              toReplace = toReplace.getParent();
            }
            expr = (PsiExpression)toReplace.replace(exprCopy);
          }
        }
      }
    } else if (exprType instanceof PsiLambdaExpressionType) {
      expr = surroundWithCast(variable, expr, ((PsiLambdaExpressionType)exprType).getExpression());
    } else if (exprType instanceof PsiMethodReferenceType) {
      expr = surroundWithCast(variable, expr, ((PsiMethodReferenceType)exprType).getExpression());
    }

    ChangeContextUtil.clearContextInfo(initializer);

    PsiThisExpression thisAccessExpr = createThisExpression(manager, thisClass, refParent);

    return (PsiExpression)ChangeContextUtil.decodeContextInfo(expr, thisClass, thisAccessExpr);
  }

  private static PsiExpression surroundWithCast(PsiVariable variable, PsiExpression expr, PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiExpressionList) {
      PsiTypeCastExpression cast = (PsiTypeCastExpression)JavaPsiFacade.getElementFactory(expr.getProject()).createExpressionFromText("(t)a", null);
      PsiTypeElement castTypeElement = cast.getCastType();
      assert castTypeElement != null;
      castTypeElement.replace(variable.getTypeElement());
      final PsiExpression operand = cast.getOperand();
      assert operand != null;
      operand.replace(expr);
      expr = (PsiTypeCastExpression)expr.replace(cast);
      if (RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)expr)) {
        expr = (PsiExpression)expr.replace(((PsiTypeCastExpression)expr).getOperand());
      }
    }
    return expr;
  }

  private static PsiThisExpression createThisExpression(PsiManager manager, PsiClass thisClass, PsiClass refParent) {
    PsiThisExpression thisAccessExpr = null;
    if (Comparing.equal(thisClass, refParent))

    {
      thisAccessExpr = RefactoringChangeUtil.createThisExpression(manager, null);
    }

    else

    {
      if (!(thisClass instanceof PsiAnonymousClass)) {
        thisAccessExpr = RefactoringChangeUtil.createThisExpression(manager, thisClass);
      }
    }
    return thisAccessExpr;
  }

  public static void tryToInlineArrayCreationForVarargs(final PsiExpression expr) {
    if (expr instanceof PsiNewExpression && ((PsiNewExpression)expr).getArrayInitializer() != null) {
      if (expr.getParent() instanceof PsiExpressionList) {
        final PsiExpressionList exprList = (PsiExpressionList)expr.getParent();
        if (exprList.getParent() instanceof PsiCall) {
          if (isSafeToInlineVarargsArgument((PsiCall)exprList.getParent())) {
            inlineArrayCreationForVarargs(((PsiNewExpression)expr));
          }
        }
      }
    }
  }

  public static void inlineArrayCreationForVarargs(final PsiNewExpression arrayCreation) {
    PsiExpressionList argumentList = (PsiExpressionList)arrayCreation.getParent();
    if (argumentList == null) return;
    PsiExpression[] args = argumentList.getExpressions();
    PsiArrayInitializerExpression arrayInitializer = arrayCreation.getArrayInitializer();
    try {
      if (arrayInitializer == null) {
        arrayCreation.delete();
        return;
      }

      PsiExpression[] initializers = arrayInitializer.getInitializers();
      if (initializers.length > 0) {
        PsiElement lastInitializerSibling = initializers[initializers.length - 1];
        while (lastInitializerSibling != null) {
          final PsiElement nextSibling = lastInitializerSibling.getNextSibling();
          if (nextSibling == null) {
            break;
          }
          if (nextSibling.getNode().getElementType() == JavaTokenType.RBRACE) break;
          lastInitializerSibling = nextSibling;
        }
        if (lastInitializerSibling instanceof PsiWhiteSpace) {
          lastInitializerSibling = PsiTreeUtil.skipSiblingsBackward(lastInitializerSibling, PsiWhiteSpace.class);
        }
        if (lastInitializerSibling.getNode().getElementType() == JavaTokenType.COMMA) {
          lastInitializerSibling = lastInitializerSibling.getPrevSibling();
        }
        PsiElement firstElement = initializers[0];
        final PsiElement leadingComment = PsiTreeUtil.skipSiblingsBackward(firstElement, PsiWhiteSpace.class);
        if (leadingComment instanceof PsiComment) {
          firstElement = leadingComment;
        }
        argumentList.addRange(firstElement, lastInitializerSibling);
      }
      args[args.length - 1].delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static boolean isSafeToInlineVarargsArgument(PsiCall expression) {
    final JavaResolveResult resolveResult = expression.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (element instanceof PsiMethod && ((PsiMethod)element).isVarArgs()) {
      PsiMethod method = (PsiMethod)element;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList != null) {
        PsiExpression[] args = argumentList.getExpressions();
        if (parameters.length == args.length) {
          PsiExpression lastArg = args[args.length - 1];
          PsiParameter lastParameter = parameters[args.length - 1];
          PsiType lastParamType = lastParameter.getType();
          LOG.assertTrue(lastParamType instanceof PsiEllipsisType);
          if (lastArg instanceof PsiNewExpression) {
            final PsiType lastArgType = lastArg.getType();
            if (lastArgType != null && substitutor.substitute(((PsiEllipsisType)lastParamType).toArrayType()).isAssignableFrom(lastArgType)) {
              PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)lastArg).getArrayInitializer();
              PsiExpression[] initializers = arrayInitializer != null ? arrayInitializer.getInitializers() : PsiExpression.EMPTY_ARRAY;
              if (isSafeToFlatten(expression, method, initializers)) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isSafeToFlatten(PsiCall callExpression, PsiMethod oldRefMethod, PsiExpression[] arrayElements) {
    PsiCall copy = (PsiCall)callExpression.copy();
    PsiExpressionList copyArgumentList = copy.getArgumentList();
    LOG.assertTrue(copyArgumentList != null);
    PsiExpression[] args = copyArgumentList.getExpressions();
    try {
      args[args.length - 1].delete();
      if (arrayElements.length > 0) {
        copyArgumentList.addRange(arrayElements[0], arrayElements[arrayElements.length - 1]);
      }
      return copy.resolveMethod() == oldRefMethod;
    }
    catch (IncorrectOperationException e) {
      return false;
    }
  }

  public static boolean allUsagesAreTailCalls(final PsiMethod method) {
    final List<PsiReference> nonTailCallUsages = Collections.synchronizedList(new ArrayList<PsiReference>());
    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ReferencesSearch.search(method).forEach(new Processor<PsiReference>() {
          @Override
          public boolean process(final PsiReference psiReference) {
            ProgressManager.checkCanceled();
            if (getTailCallType(psiReference) == TailCallType.None) {
              nonTailCallUsages.add(psiReference);
              return false;
            }
            return true;
          }
        });
      }
    }, RefactoringBundle.message("inline.method.checking.tail.calls.progress"), true, method.getProject());
    return result && nonTailCallUsages.isEmpty();
  }

  public static TailCallType getTailCallType(@NotNull final PsiReference psiReference) {
    PsiElement element = psiReference.getElement();
    PsiExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (methodCall == null) return TailCallType.None;
    if (methodCall.getParent() instanceof PsiReturnStatement) return TailCallType.Return;
    if (methodCall.getParent() instanceof PsiExpressionStatement) {
      PsiStatement callStatement = (PsiStatement) methodCall.getParent();
      PsiMethod callerMethod = PsiTreeUtil.getParentOfType(callStatement, PsiMethod.class);
      if (callerMethod != null) {
        final PsiStatement[] psiStatements = callerMethod.getBody().getStatements();
        return psiStatements.length > 0 && callStatement == psiStatements [psiStatements.length-1] ? TailCallType.Simple : TailCallType.None;
      }
    }
    return TailCallType.None;
  }

  public static void substituteTypeParams(PsiElement scope, final PsiSubstitutor substitutor, final PsiElementFactory factory) {
    final Map<PsiElement, PsiElement> replacement = new HashMap<PsiElement, PsiElement>();
    scope.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitTypeElement(PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        PsiType type = typeElement.getType();
        if (type instanceof PsiClassType) {
          JavaResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
          PsiElement resolved = resolveResult.getElement();
          if (resolved instanceof PsiTypeParameter) {
            PsiType newType = resolveResult.getSubstitutor().putAll(substitutor).substitute((PsiTypeParameter)resolved);
            if (newType instanceof PsiCapturedWildcardType) {
              newType = ((PsiCapturedWildcardType)newType).getUpperBound();
            }
            if (newType instanceof PsiWildcardType) {
              newType = ((PsiWildcardType)newType).getBound();
            }
            if (newType == null) {
              newType = PsiType.getJavaLangObject(resolved.getManager(), resolved.getResolveScope());
            }
            try {
              replacement.put(typeElement, factory.createTypeElement(newType));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    });
    for (PsiElement element : replacement.keySet()) {
      if (element.isValid()) {
        element.replace(replacement.get(element));
      }
    }
  }

  private static PsiElement replaceDiamondWithInferredTypesIfNeeded(PsiExpression initializer, PsiElement ref) {
    if (initializer instanceof PsiNewExpression) {
      final PsiDiamondType diamondType = PsiDiamondTypeUtil.getDiamondType((PsiNewExpression)initializer);
      if (diamondType != null) {
        final PsiDiamondType.DiamondInferenceResult inferenceResult = diamondType.resolveInferredTypes();
        if (inferenceResult.getErrorMessage() == null) {
          final PsiElement copy = ref.copy();
          final PsiElement parent = ref.replace(initializer);
          final PsiDiamondType.DiamondInferenceResult result = PsiDiamondTypeImpl.resolveInferredTypes((PsiNewExpression)initializer, parent);
          ref = parent.replace(copy);
          if (!result.equals(inferenceResult)) {
            final String inferredTypeText = StringUtil.join(inferenceResult.getTypes(),
                                                            new Function<PsiType, String>() {
                                                              @Override
                                                              public String fun(PsiType psiType) {
                                                                return psiType.getCanonicalText();
                                                              }
                                                            }, ", ");
            final PsiExpressionList argumentList = ((PsiNewExpression)initializer).getArgumentList();
            if (argumentList != null) {
              final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)initializer).getClassOrAnonymousClassReference();
              LOG.assertTrue(classReference != null);
              final PsiExpression expression = JavaPsiFacade.getElementFactory(initializer.getProject())
                .createExpressionFromText("new " + classReference.getReferenceName() + "<" + inferredTypeText + ">" + argumentList.getText(), initializer);
              return ref.replace(expression);
            }
          }
        }
      }
    }
    return ref != initializer ? ref.replace(initializer) : initializer;
  }

  public static void solveVariableNameConflicts(final PsiElement scope,
                                                final PsiElement placeToInsert,
                                                final PsiElement renameScope) throws IncorrectOperationException {
    if (scope instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)scope;
      String name = var.getName();
      String oldName = name;
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(scope.getProject());
      while (true) {
        String newName = codeStyleManager.suggestUniqueVariableName(name, placeToInsert, true);
        if (newName.equals(name)) break;
        name = newName;
        newName = codeStyleManager.suggestUniqueVariableName(name, var, true);
        if (newName.equals(name)) break;
        name = newName;
      }
      if (!name.equals(oldName)) {
        RefactoringUtil.renameVariableReferences(var, name, new LocalSearchScope(renameScope), true);
        var.getNameIdentifier().replace(JavaPsiFacade.getElementFactory(scope.getProject()).createIdentifier(name));
      }
    }

    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      solveVariableNameConflicts(child, placeToInsert, renameScope);
    }
  }

  public enum TailCallType {
    None, Simple, Return
  }
}
