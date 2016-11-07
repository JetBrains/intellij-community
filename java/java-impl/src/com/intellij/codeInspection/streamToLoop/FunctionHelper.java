/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.streamToLoop;

import com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.StreamToLoopReplacementContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * A helper class which represents an expression mapped to the functional interface (like lambda, method reference or normal reference)
 *
 * @author Tagir Valeev
 */
abstract class FunctionHelper {
  private static final Logger LOG = Logger.getInstance(FunctionHelper.class);

  String getText() {
    return getExpression().getText();
  }

  abstract PsiExpression getExpression();

  /**
   * Try to perform "light" transformation. Works only for single-argument SAM. The function helper decides by itself
   * how to name the SAM argument and returns it. After this method invocation normal transform cannot be performed.
   *
   * @return SAM argument name or null if function helper refused to perform a transformation.
   * @param type
   */
  abstract String tryLightTransform(PsiType type);

  /**
   * Perform an adaptation of current function helper to the replacement context with given parameter names.
   * Must be called exactly once prior using getExpression() or getText()
   *
   * @param context a context for which transformation should be performed
   * @param newNames names of the SAM parameters (the length must be exactly the same as number of SAM parameters)
   */
  abstract void transform(StreamToLoopReplacementContext context, String... newNames);

  /**
   * Rename the references of some variable if it's used inside this function
   *
   * @param oldName old variable name
   * @param newName new variable name
   * @param context a context
   */
  abstract void rename(String oldName, String newName, StreamToLoopReplacementContext context);

  abstract void registerUsedNames(Consumer<String> consumer);

  @Nullable
  abstract String getParameterName(int index);

  void suggestVariableName(StreamVariable var, int index) {
    String name = getParameterName(index);
    if (name != null) {
      var.addBestNameCandidate(name);
    }
  }

  @Contract("null, _ -> null")
  @Nullable
  static FunctionHelper create(PsiExpression expression, int paramCount) {
    if (expression instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambda = (PsiLambdaExpression)expression;
      PsiType functionalInterfaceType = lambda.getFunctionalInterfaceType();
      if(functionalInterfaceType == null) return null;
      PsiParameterList list = lambda.getParameterList();
      if (list.getParametersCount() != paramCount) return null;
      String[] parameters = StreamEx.of(list.getParameters()).map(PsiVariable::getName).toArray(String[]::new);
      PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (body == null) return null;
      return new LambdaFunctionHelper(body, parameters);
    }
    if (expression instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)expression;
      if (methodRef.resolve() == null) return null;
      PsiType functionalInterfaceType = methodRef.getFunctionalInterfaceType();
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
      if (interfaceMethod == null) return null;
      if (interfaceMethod.getParameterList().getParametersCount() != paramCount) return null;
      return new MethodReferenceFunctionHelper(functionalInterfaceType, methodRef);
    }
    if (expression instanceof PsiReferenceExpression && ExpressionUtils.isSimpleExpression(expression)) {
      PsiType functionalInterfaceType = expression.getType();
      PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
      if (interfaceMethod == null || interfaceMethod.getParameterList().getParametersCount() != paramCount) return null;
      return new SimpleReferenceFunctionHelper(expression, interfaceMethod.getName());
    }
    return null;
  }

  /**
   * Renames references to the variable oldName in given expression into newName
   * @param expression an expression to search-and-replace references inside
   * @param oldName old name
   * @param newName new name
   * @param context context
   * @return resulting expression (might be the same as input expression) or null if expression already had references to newName,
   *   so rename may merge two variables
   */
  @NotNull
  static PsiExpression renameVarReference(@NotNull PsiExpression expression,
                                          String oldName,
                                          String newName,
                                          StreamToLoopReplacementContext context) {
    if(oldName.equals(newName)) return expression;
    PsiLambdaExpression lambda = (PsiLambdaExpression)context.createExpression("("+oldName+","+newName+")-> "+expression.getText());
    PsiParameter[] parameters = lambda.getParameterList().getParameters();
    PsiParameter oldVar = parameters[0];
    PsiParameter newVar = parameters[1];
    PsiElement body = lambda.getBody();
    LOG.assertTrue(body != null);
    if(ReferencesSearch.search(newVar, new LocalSearchScope(body)).findFirst() != null) {
      throw new IllegalStateException("Reference with name "+newVar+" already exists in "+lambda.getText());
    }
    for (PsiReference ref : ReferencesSearch.search(oldVar, new LocalSearchScope(body)).findAll()) {
      ref.handleElementRename(newName);
    }
    return (PsiExpression)lambda.getBody();
  }

  static void processUsedNames(PsiElement start, Consumer<String> action) {
    start.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitVariable(PsiVariable variable) {
        super.visitVariable(variable);
        action.accept(variable.getName());
      }
    });
  }

  private static class MethodReferenceFunctionHelper extends FunctionHelper {
    private final String myType;
    private final String myQualifierType;
    private PsiMethodReferenceExpression myMethodRef;
    private PsiExpression myExpression;

    public MethodReferenceFunctionHelper(PsiType functionalInterfaceType, PsiMethodReferenceExpression methodRef) {
      myMethodRef = methodRef;
      myType = functionalInterfaceType.getCanonicalText();
      PsiExpression qualifier = methodRef.getQualifierExpression();
      PsiType type = qualifier == null ? null : qualifier.getType();
      myQualifierType = type == null ? null : type.getCanonicalText();
    }

    @Override
    String tryLightTransform(PsiType type) {
      if(myMethodRef.isConstructor()) return null;
      type = GenericsUtil.getVariableTypeByExpressionType(type);
      if(type == null) return null;
      PsiElement element = myMethodRef.resolve();
      if(!(element instanceof PsiMethod)) return null;
      PsiMethod method = (PsiMethod)element;
      String var = "x";
      PsiLambdaExpression lambda;
      PsiClass aClass = method.getContainingClass();
      if(aClass == null) return null;
      if(method.getModifierList().hasExplicitModifier(PsiModifier.STATIC)) {
        if(method.getParameterList().getParametersCount() != 1) return null;
        lambda = (PsiLambdaExpression)JavaPsiFacade.getElementFactory(myMethodRef.getProject())
          .createExpressionFromText("(" + type.getCanonicalText() + " " + var + ")->" +
                                    aClass.getQualifiedName() + "." + method.getName() + "(" + var + ")", myMethodRef);
      } else {
        lambda =
          (PsiLambdaExpression)JavaPsiFacade.getElementFactory(myMethodRef.getProject()).createExpressionFromText(
            "(" + type.getCanonicalText() + " " + var + ")->" + var + "." + myMethodRef.getReferenceName() + "()", myMethodRef);
      }
      myExpression = (PsiExpression)lambda.getBody();
      return var;
    }

    @Override
    PsiExpression getExpression() {
      LOG.assertTrue(myExpression != null);
      return myExpression;
    }

    @Override
    void registerUsedNames(Consumer<String> consumer) {
      processUsedNames(myMethodRef, consumer);
    }

    @Override
    void transform(StreamToLoopReplacementContext context, String... newNames) {
      PsiMethodReferenceExpression methodRef = fromText(context, myMethodRef.getText());
      PsiExpression qualifier = methodRef.getQualifierExpression();
      if(qualifier != null && !ExpressionUtils.isSimpleExpression(qualifier)) {
        String type = myQualifierType;
        if(type != null) {
          String nameCandidate = "expr";
          PsiType psiType = context.createType(myQualifierType);
          SuggestedNameInfo info =
            JavaCodeStyleManager
              .getInstance(context.getProject()).suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, psiType, true);
          if(info.names.length > 0) {
            nameCandidate = info.names[0];
          }
          String expr = context.declare(nameCandidate, type, qualifier.getText());
          PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)context
            .createExpression("(" + type + " " + expr + ")->(" + myType + ")" + expr + "::" + myMethodRef.getReferenceName());
          PsiTypeCastExpression castExpr = (PsiTypeCastExpression)lambdaExpression.getBody();
          LOG.assertTrue(castExpr != null);
          methodRef = (PsiMethodReferenceExpression)castExpr.getOperand();
        }
      }
      PsiLambdaExpression lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, true, true);
      LOG.assertTrue(lambda != null);
      PsiElement body = lambda.getBody();
      LOG.assertTrue(body instanceof PsiExpression);
      myExpression = (PsiExpression)body;
      EntryStream.zip(lambda.getParameterList().getParameters(), newNames)
        .forKeyValue((param, newName) -> myExpression = renameVarReference(myExpression, param.getName(), newName, context));
    }

    @NotNull
    private PsiMethodReferenceExpression fromText(StreamToLoopReplacementContext context, String text) {
      PsiTypeCastExpression castExpr = (PsiTypeCastExpression)context.createExpression("(" + myType + ")" + text);
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)castExpr.getOperand();
      LOG.assertTrue(methodRef != null);
      return methodRef;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      if(oldName.equals(newName)) return;
      PsiExpression qualifier = myMethodRef.getQualifierExpression();
      if(qualifier == null) return;
      qualifier = renameVarReference(qualifier, oldName, newName, context);
      myMethodRef = fromText(context, qualifier.getText()+"::"+myMethodRef.getReferenceName());
    }

    @Override
    @Nullable
    String getParameterName(int index) {
      return null;
    }
  }

  private static class SimpleReferenceFunctionHelper extends FunctionHelper {
    private PsiExpression myReference;
    private final String myName;
    private PsiExpression myExpression;

    public SimpleReferenceFunctionHelper(PsiExpression reference, String methodName) {
      myReference = reference;
      myName = methodName;
    }

    @Override
    String tryLightTransform(PsiType type) {
      return null;
    }

    @Override
    PsiExpression getExpression() {
      LOG.assertTrue(myExpression != null);
      return myExpression;
    }

    @Override
    void transform(StreamToLoopReplacementContext context, String... newNames) {
      myExpression = context.createExpression(myReference.getText() + "." + myName + "(" + String.join(",", newNames) + ")");
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myReference = renameVarReference(myReference, oldName, newName, context);
    }

    @Override
    void registerUsedNames(Consumer<String> consumer) {
      processUsedNames(myReference, consumer);
    }

    @Nullable
    @Override
    String getParameterName(int index) {
      return null;
    }
  }

  private static class LambdaFunctionHelper extends FunctionHelper {
    private String[] myParameters;
    private PsiExpression myBody;

    LambdaFunctionHelper(PsiExpression body, String[] parameters) {
      myParameters = parameters;
      myBody = body;
    }

    @Override
    String tryLightTransform(PsiType type) {
      LOG.assertTrue(myParameters.length == 1);
      return myParameters[0];
    }

    PsiExpression getExpression() {
      return myBody;
    }

    void transform(StreamToLoopReplacementContext context, String... newNames) {
      LOG.assertTrue(newNames.length == myParameters.length);
      EntryStream.zip(myParameters, newNames).forKeyValue(
        (oldName, newName) -> myBody = renameVarReference(myBody, oldName, newName, context));
    }

    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      OptionalLong idx = StreamEx.of(myParameters).indexOf(newName);
      if(idx.isPresent()) {
        for(int i = 1;; i++) {
          String paramName = newName+'$'+i;
          if (!paramName.equals(oldName) &&
              !StreamEx.of(myParameters).has(paramName)) {
            try {
              myBody = renameVarReference(myBody, newName, paramName, context);
              myParameters[(int)idx.getAsLong()] = paramName;
              break;
            }
            catch(IllegalStateException ise) {
              // something is really wrong if we already have references to all newName$1, newName$2, ... newName$50
              // or probably IllegalStateException was thrown by something else: at least we don't stuck in endless loop
              if(i > 50) throw ise;
            }
          }
        }
      }
      myBody = renameVarReference(myBody, oldName, newName, context);
    }

    @Override
    void registerUsedNames(Consumer<String> consumer) {
      processUsedNames(myBody, consumer);
    }

    String getParameterName(int index) {
      return myParameters[index];
    }
  }
}
