/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A helper class which represents an expression mapped to the functional interface (like lambda, method reference or normal reference)
 *
 * @author Tagir Valeev
 */
abstract class FunctionHelper {
  private static final Logger LOG = Logger.getInstance(FunctionHelper.class);

  private String myResultType;

  FunctionHelper(PsiType resultType) {
    myResultType = resultType.getCanonicalText();
  }

  String getResultType() {
    return myResultType;
  }

  final String getText() {
    return getExpression().getText();
  }

  abstract PsiExpression getExpression();

  /**
   * Try to perform "light" transformation. Works only for single-argument SAM. The function helper decides by itself
   * how to name the SAM argument and returns the assigned name. After this method invocation normal transform cannot be performed.
   *
   * @return SAM argument name or null if function helper refused to perform a transformation.
   * @param type type of the input variable (after generic substitution if applicable)
   */
  String tryLightTransform(PsiType type) {
    return null;
  }

  /**
   * Adapts this function helper converting it to the valid expression and inlining SAM parameters with provided values.
   * Must be called exactly once prior using getExpression() or getText().
   *
   * @param context a context for which transformation should be performed
   * @param argumentValues values (expressions) of the SAM parameters to be inlined into function body
   *                       (the length must be exactly the same as the number of SAM parameters).
   *                       Usually it's just the references to the existing variables, but other expressions
   *                       (e.g. constant literals) could be used as well if necessary.
   */
  abstract void transform(StreamToLoopReplacementContext context, String... argumentValues);

  /**
   * Rename the references of some variable if it's used inside this function
   *
   * @param oldName old variable name
   * @param newName new variable name
   * @param context a context
   */
  void rename(String oldName, String newName, StreamToLoopReplacementContext context) {}

  void registerReusedElements(Consumer<PsiElement> consumer) {}

  @Nullable
  String getParameterName(int index) {
    return null;
  }

  void suggestVariableName(StreamVariable var, int index) {
    String name = getParameterName(index);
    if (name != null) {
      var.addBestNameCandidate(name);
    }
  }

  void suggestOutputNames(StreamVariable var) {}

  List<String> suggestFinalOutputNames(StreamToLoopReplacementContext context, String desiredName, String worstCaseName) {
    List<String> candidates = Arrays.asList(JavaCodeStyleManager.getInstance(context.getProject())
                                              .suggestVariableName(VariableKind.LOCAL_VARIABLE, desiredName,
                                                                   context.createExpression(getText()),
                                                                   context.createType(getResultType())).names);
    if(candidates.isEmpty() && worstCaseName != null) candidates = Collections.singletonList(worstCaseName);
    return candidates;
  }

  private static void suggestFromExpression(StreamVariable var, Project project, PsiExpression expression) {
    SuggestedNameInfo info = JavaCodeStyleManager.getInstance(project)
      .suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expression, null, true);
    for (String name : info.names) {
      var.addOtherNameCandidate(name);
    }
  }

  @Contract("null, _ -> null")
  @Nullable
  static FunctionHelper create(PsiExpression expression, int paramCount) {
    if(expression == null) return null;
    PsiType type = expression instanceof PsiFunctionalExpression
                   ? ((PsiFunctionalExpression)expression).getFunctionalInterfaceType()
                   : expression.getType();
    if(!(type instanceof PsiClassType)) return null;
    PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(type);
    if (interfaceMethod == null || interfaceMethod.getParameterList().getParametersCount() != paramCount) return null;
    PsiType returnType = interfaceMethod.getReturnType();
    if (returnType == null) return null;
    returnType = ((PsiClassType)type).resolveGenerics().getSubstitutor().substitute(returnType);
    type = fixType(type, expression.getProject());
    if (expression instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambda = (PsiLambdaExpression)expression;
      PsiParameterList list = lambda.getParameterList();
      if (list.getParametersCount() != paramCount) return null;
      String[] parameters = StreamEx.of(list.getParameters()).map(PsiVariable::getName).toArray(String[]::new);
      PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (body == null) return null;
      return new LambdaFunctionHelper(returnType, body, parameters);
    }
    if (expression instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)expression;
      if (methodRef.resolve() == null) return null;
      String template = tryInlineMethodReference(paramCount, methodRef);
      if (template != null) {
        return new InlinedFunctionHelper(returnType, paramCount, template);
      }
      return new MethodReferenceFunctionHelper(returnType, type, methodRef);
    }
    if (expression instanceof PsiReferenceExpression && ExpressionUtils.isSimpleExpression(expression)) {
      return new SimpleReferenceFunctionHelper(returnType, expression, interfaceMethod.getName());
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (MethodCallUtils.isCallToStaticMethod(call, CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION, "identity", 0)) {
        return paramCount == 1 ? new InlinedFunctionHelper(returnType, 1, "{0}") : null;
      }
      if (MethodCallUtils.isCallToStaticMethod(call, CommonClassNames.JAVA_UTIL_COMPARATOR, "naturalOrder", 0)) {
        return paramCount == 2 ? new InlinedFunctionHelper(returnType, 2, "{0}.compareTo({1})") : null;
      }
      if (MethodCallUtils.isCallToStaticMethod(call, CommonClassNames.JAVA_UTIL_COMPARATOR, "reverseOrder", 0) ||
          MethodCallUtils.isCallToStaticMethod(call, CommonClassNames.JAVA_UTIL_COLLECTIONS, "reverseOrder", 0)) {
        return paramCount == 2 ? new InlinedFunctionHelper(returnType, 2, "{1}.compareTo({0})") : null;
      }
    }
    return new ComplexExpressionFunctionHelper(returnType, type, interfaceMethod.getName(), expression);
  }

  private static PsiType fixType(PsiType type, Project project) {
    if(type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      PsiClass aClass = classType.resolve();
      if (aClass != null && classType.getParameterCount() != 0) {
        PsiType[] parameters = classType.getParameters();
        Arrays.asList(parameters).replaceAll(t -> fixType(t, project));
        return JavaPsiFacade.getElementFactory(project).createType(aClass, parameters);
      }
    }
    else if(type instanceof PsiArrayType) {
      PsiType componentType = ((PsiArrayType)type).getComponentType();
      PsiType fixedType = fixType(componentType, project);
      if(fixedType != componentType) {
        return fixedType.createArrayType();
      }
    }
    else if(type instanceof PsiCapturedWildcardType) {
      return ((PsiCapturedWildcardType)type).getUpperBound();
    }
    return type;
  }

  @Nullable
  private static String tryInlineMethodReference(int paramCount, PsiMethodReferenceExpression methodRef) {
    PsiElement element = methodRef.resolve();
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      String name = method.getName();
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        String className = aClass.getQualifiedName();
        if("java.util.Objects".equals(className) && paramCount == 1) {
          if (name.equals("nonNull")) {
            return "{0}!=null";
          }
          if (name.equals("isNull")) {
            return "{0}==null";
          }
        }
        if (paramCount == 2 && name.equals("sum") && (CommonClassNames.JAVA_LANG_INTEGER.equals(className) ||
                                                      CommonClassNames.JAVA_LANG_LONG.equals(className) ||
                                                      CommonClassNames.JAVA_LANG_DOUBLE.equals(className))) {
          return "{0}+{1}";
        }
        if(CommonClassNames.JAVA_LANG_CLASS.equals(className) && paramCount == 1) {
          PsiExpression qualifier = methodRef.getQualifierExpression();
          if(qualifier instanceof PsiClassObjectAccessExpression) {
            PsiTypeElement type = ((PsiClassObjectAccessExpression)qualifier).getOperand();
            if(name.equals("isInstance")) {
              return "{0} instanceof "+type.getText();
            }
            if(name.equals("cast")) {
              return "("+type.getText()+"){0}";
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Contract(pure = true)
  static FunctionHelper newObjectSupplier(PsiType type, String instanceClassName) {
    return new FunctionHelper(type) {
      PsiExpression myExpression;

      @Override
      PsiExpression getExpression() {
        return myExpression;
      }

      @Override
      void transform(StreamToLoopReplacementContext context, String... argumentValues) {
        LOG.assertTrue(argumentValues.length == 0);
        myExpression = context.createExpression("new "+instanceClassName+"<>()");
      }
    };
  }

  static boolean hasVarReference(PsiExpression expression, String name, StreamToLoopReplacementContext context) {
    PsiLambdaExpression lambda = (PsiLambdaExpression)context.createExpression(name+"->"+expression.getText());
    PsiParameter var = lambda.getParameterList().getParameters()[0];
    PsiElement body = lambda.getBody();
    LOG.assertTrue(body != null);
    return ReferencesSearch.search(var, new LocalSearchScope(body)).findFirst() != null;
  }

  /**
   * Replaces all the references to the variable {@code name} in given expression with {@code replacement}.
   *
   * <p>
   *   If the replacement is a new name to the variable, the caller must take care that this new name was not used before.
   * </p>
   *
   * @param expression an expression to search-and-replace references inside
   * @param name a reference name to replace
   * @param replacement a replacement expression (new name or literal)
   * @param context context
   * @return resulting expression (might be the same as input expression)
   */
  @NotNull
  static PsiExpression replaceVarReference(@NotNull PsiExpression expression,
                                           String name,
                                           String replacement,
                                           StreamToLoopReplacementContext context) {
    if(name.equals(replacement)) return expression;
    PsiLambdaExpression lambda = (PsiLambdaExpression)context.createExpression(name+"->"+expression.getText());
    PsiParameter var = lambda.getParameterList().getParameters()[0];
    PsiElement body = lambda.getBody();
    LOG.assertTrue(body != null);
    PsiExpression replacementExpression = context.createExpression(replacement);
    for (PsiReference ref : ReferencesSearch.search(var, new LocalSearchScope(body)).findAll()) {
      ref.getElement().replace(replacementExpression);
    }
    return (PsiExpression)lambda.getBody();
  }

  private static class MethodReferenceFunctionHelper extends FunctionHelper {
    private final String myType;
    private final String myQualifierType;
    private PsiMethodReferenceExpression myMethodRef;
    private PsiExpression myExpression;

    public MethodReferenceFunctionHelper(PsiType returnType, PsiType functionalInterfaceType, PsiMethodReferenceExpression methodRef) {
      super(returnType);
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
    void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myMethodRef);
    }

    @Override
    void transform(StreamToLoopReplacementContext context, String... argumentValues) {
      PsiMethodReferenceExpression methodRef = fromText(context, myMethodRef.getText());
      PsiExpression qualifier = methodRef.getQualifierExpression();
      if(qualifier != null) {
        String qualifierText = qualifier.getText();
        if(!ExpressionUtils.isSimpleExpression(context.createExpression(qualifierText))) {
          String type = myQualifierType;
          if (type != null) {
            String nameCandidate = "expr";
            PsiType psiType = context.createType(myQualifierType);
            SuggestedNameInfo info =
              JavaCodeStyleManager
                .getInstance(context.getProject()).suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, psiType, true);
            if (info.names.length > 0) {
              nameCandidate = info.names[0];
            }
            String expr = context.declare(nameCandidate, type, qualifierText);
            PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)context
              .createExpression("(" + type + " " + expr + ")->(" + myType + ")" + expr + "::" + myMethodRef.getReferenceName());
            PsiTypeCastExpression castExpr = (PsiTypeCastExpression)lambdaExpression.getBody();
            LOG.assertTrue(castExpr != null);
            methodRef = (PsiMethodReferenceExpression)castExpr.getOperand();
          }
        }
      }
      PsiLambdaExpression lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, true, true);
      LOG.assertTrue(lambda != null);
      PsiElement body = lambda.getBody();
      LOG.assertTrue(body instanceof PsiExpression);
      myExpression = (PsiExpression)body;
      EntryStream.zip(lambda.getParameterList().getParameters(), argumentValues)
        .forKeyValue((param, newName) -> myExpression = replaceVarReference(myExpression, param.getName(), newName, context));
    }

    @Override
    void suggestOutputNames(StreamVariable var) {
      // myMethodRef is physical at this point
      Project project = myMethodRef.getProject();
      PsiTypeCastExpression castExpr = (PsiTypeCastExpression)JavaPsiFacade.getElementFactory(project)
          .createExpressionFromText("(" + myType + ")" + myMethodRef.getText(), myMethodRef);
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)castExpr.getOperand();
      PsiLambdaExpression lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, true, true);
      if(lambda != null) {
        PsiElement body = lambda.getBody();
        if(body instanceof PsiExpression) {
          suggestFromExpression(var, project, (PsiExpression)body);
        }
      }
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
      qualifier = replaceVarReference(qualifier, oldName, newName, context);
      myMethodRef = fromText(context, qualifier.getText()+"::"+myMethodRef.getReferenceName());
    }
  }

  private static class ComplexExpressionFunctionHelper extends FunctionHelper {
    private final String myMethodName;
    private final String myNameCandidate;
    private final String myFnType;
    private PsiExpression myExpression;
    private PsiExpression myFinalExpression;

    private ComplexExpressionFunctionHelper(PsiType type, PsiType functionalInterface, String name, PsiExpression expression) {
      super(type);
      myMethodName = name;
      myExpression = expression;
      myNameCandidate = getNameCandidate(functionalInterface);
      myFnType = functionalInterface.getCanonicalText();
    }

    private String getNameCandidate(PsiType functionalInterface) {
      PsiElement parent = myExpression.getParent();
      if(parent instanceof PsiExpressionList) {
        int idx = ArrayUtil.indexOf(((PsiExpressionList)parent).getExpressions(), myExpression);
        PsiElement gParent = parent.getParent();
        if(gParent instanceof PsiMethodCallExpression && idx >= 0) {
          PsiMethod method = ((PsiMethodCallExpression)gParent).resolveMethod();
          if(method != null) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if(idx < parameters.length) {
              return parameters[idx].getName();
            }
          }
        }
      }
      return functionalInterface.getPresentableText().toLowerCase(Locale.ENGLISH);
    }

    @Override
    PsiExpression getExpression() {
      LOG.assertTrue(myFinalExpression != null);
      return myFinalExpression;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myExpression = replaceVarReference(myExpression, oldName, newName, context);
    }

    @Override
    void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myExpression);
    }

    @Override
    void transform(StreamToLoopReplacementContext context, String... argumentValues) {
      String varName = context.declare(myNameCandidate, myFnType, myExpression.getText());
      myFinalExpression = context.createExpression(varName + "." + myMethodName + "(" + String.join(",", argumentValues) + ")");
    }
  }

  private static class SimpleReferenceFunctionHelper extends FunctionHelper {
    private PsiExpression myReference;
    private final String myName;
    private PsiExpression myExpression;

    public SimpleReferenceFunctionHelper(PsiType returnType, PsiExpression reference, String methodName) {
      super(returnType);
      myReference = reference;
      myName = methodName;
    }

    @Override
    PsiExpression getExpression() {
      LOG.assertTrue(myExpression != null);
      return myExpression;
    }

    @Override
    void transform(StreamToLoopReplacementContext context, String... argumentValues) {
      myExpression = context.createExpression(myReference.getText() + "." + myName + "(" + String.join(",", argumentValues) + ")");
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myReference = replaceVarReference(myReference, oldName, newName, context);
    }

    @Override
    void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myReference);
    }
  }

  private static class InlinedFunctionHelper extends FunctionHelper {
    private final int myArgCount;
    private final String myTemplate;
    private PsiExpression myExpression;

    public InlinedFunctionHelper(PsiType type, int argCount, String template) {
      super(type);
      myArgCount = argCount;
      myTemplate = template;
    }

    @Override
    PsiExpression getExpression() {
      LOG.assertTrue(myExpression != null);
      return myExpression;
    }

    @Override
    void transform(StreamToLoopReplacementContext context, String... argumentValues) {
      LOG.assertTrue(argumentValues.length == myArgCount);
      myExpression = context.createExpression(MessageFormat.format(myTemplate, (Object[])argumentValues));
    }
  }

  private static class LambdaFunctionHelper extends FunctionHelper {
    private String[] myParameters;
    private PsiExpression myBody;

    LambdaFunctionHelper(PsiType returnType, PsiExpression body, String[] parameters) {
      super(returnType);
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

    void transform(StreamToLoopReplacementContext context, String... argumentValues) {
      LOG.assertTrue(argumentValues.length == myParameters.length);
      EntryStream.zip(myParameters, argumentValues).forKeyValue(
        (oldName, newName) -> myBody = replaceVarReference(myBody, oldName, newName, context));
    }

    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      int idx = ArrayUtil.indexOf(myParameters, newName);
      if(idx >= 0) {
        // If new name collides with existing parameter, rename it
        for(int i = 1;; i++) {
          String paramName = newName+'$'+i;
          if (!paramName.equals(oldName) && !StreamEx.of(myParameters).has(paramName) && !hasVarReference(myBody, paramName, context)) {
            myBody = replaceVarReference(myBody, newName, paramName, context);
            myParameters[idx] = paramName;
            break;
          }
        }
      }
      myBody = replaceVarReference(myBody, oldName, newName, context);
    }

    @Override
    void registerReusedElements(Consumer<PsiElement> consumer) {
      consumer.accept(myBody);
    }

    String getParameterName(int index) {
      return myParameters[index];
    }

    @Override
    void suggestOutputNames(StreamVariable var) {
      Project project = myBody.getProject();
      PsiExpression expr = JavaPsiFacade.getElementFactory(project).createExpressionFromText("(" + var.getType() + ")" + getText(), myBody);
      suggestFromExpression(var, project, expr);
    }
  }
}
