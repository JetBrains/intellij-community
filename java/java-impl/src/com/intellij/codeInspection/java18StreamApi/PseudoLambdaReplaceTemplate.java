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
package com.intellij.codeInspection.java18StreamApi;

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class PseudoLambdaReplaceTemplate {
  private final static Logger LOG = Logger.getInstance(PseudoLambdaReplaceTemplate.class);

  public enum LambdaRole {
    PREDICATE,
    FUNCTION
  }

  public static class ValidationInfo {
    private final int myLambdaPosition;
    private final int myIterablePosition;
    private final int myDefaultValuePosition;

    public ValidationInfo(int lambdaPosition, int iterablePosition, int defaultValuePosition) {
      myLambdaPosition = lambdaPosition;
      myIterablePosition = iterablePosition;
      myDefaultValuePosition = defaultValuePosition;
    }

    public int getLambdaPosition() {
      return myLambdaPosition;
    }

    public int getIterablePosition() {
      return myIterablePosition;
    }

    public int getDefaultValuePosition() {
      return myDefaultValuePosition;
    }
  }

  static final PseudoLambdaReplaceTemplate MAP =
    new PseudoLambdaReplaceTemplate(StreamApiConstants.MAP, LambdaRole.FUNCTION, false);
  static final PseudoLambdaReplaceTemplate FILTER =
    new PseudoLambdaReplaceTemplate(StreamApiConstants.FILTER, LambdaRole.PREDICATE, false);
  static final PseudoLambdaReplaceTemplate FIND =
    new PseudoLambdaReplaceTemplate(StreamApiConstants.FAKE_FIND_MATCHED, LambdaRole.PREDICATE, true);
  static final PseudoLambdaReplaceTemplate ALL_MATCH =
    new PseudoLambdaReplaceTemplate(StreamApiConstants.ALL_MATCH, LambdaRole.PREDICATE, false);
  static final PseudoLambdaReplaceTemplate ANY_MATCH =
    new PseudoLambdaReplaceTemplate(StreamApiConstants.ANY_MATCH, LambdaRole.PREDICATE, false);

  private final String myStreamApiMethodName;
  private final LambdaRole myLambdaRole;
  private final boolean myAcceptDefaultValue;

  public PseudoLambdaReplaceTemplate(String method,
                                     LambdaRole type,
                                     boolean acceptDefaultValue) {
    myStreamApiMethodName = method;
    myLambdaRole = type;
    myAcceptDefaultValue = acceptDefaultValue;
  }

  public static List<PseudoLambdaReplaceTemplate> getAllTemplates() {
    return ContainerUtil.newArrayList(MAP, FILTER, FIND, ALL_MATCH, ANY_MATCH);
  }

  public ValidationInfo validate(final PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiType[] parameterTypes =
      ContainerUtil.map2Array(Arrays.asList(parameters), new PsiType[parameters.length], parameter -> parameter.getType());
    final PsiType returnType = method.getReturnType();

    if (StreamApiConstants.FAKE_FIND_MATCHED.equals(myStreamApiMethodName)) {
      if (!PsiType.BOOLEAN.equals(returnType)) {
        return null;
      }
    } else {
      final PsiClass stream =
        JavaPsiFacade.getInstance(method.getProject()).findClass(StreamApiConstants.JAVA_UTIL_STREAM_STREAM, method.getResolveScope());
      if (stream == null) {
        return null;
      }
      final PsiMethod[] methods = stream.findMethodsByName(myStreamApiMethodName, false);
      LOG.assertTrue(methods.length != 0);
      PsiMethod representative = methods[0];
      final PsiType expectedReturnType = representative.getReturnType();
      if (expectedReturnType instanceof PsiClassType) {
        final PsiClass resolvedClass = ((PsiClassType)expectedReturnType).resolve();
        if (resolvedClass == null) {
          return null;
        } else {
          if (StreamApiConstants.JAVA_UTIL_STREAM_STREAM.equals(resolvedClass.getQualifiedName())) {
            if (!(returnType instanceof PsiArrayType)) {
              if (!(returnType instanceof PsiClassType)) {
                return null;
              }
              final PsiClass methodReturnType = ((PsiClassType)returnType).resolve();
              if (methodReturnType == null ||
                  !InheritanceUtil.isInheritor(methodReturnType, CommonClassNames.JAVA_LANG_ITERABLE)) {
                return null;
              }
            }
          }
        }
      }
      else if (PsiType.BOOLEAN.equals(expectedReturnType)) {
        if (!PsiType.BOOLEAN.equals(returnType)) {
          return null;
        }
      }
    }

    return validate(parameterTypes, returnType, null, method);
  }

  @Nullable
  public ValidationInfo validate(final PsiMethodCallExpression expression) {
    final PsiType[] argumentTypes = expression.getArgumentList().getExpressionTypes();
    final PsiType methodReturnType = expression.getType();
    if (methodReturnType == null) {
      return null;
    }

    final JavaResolveResult result = expression.getMethodExpression().advancedResolve(false);
    final PsiElement element = result.getElement();
    if (!(element instanceof PsiMethod)) {
      return null;
    }
    final PsiParameter[] expectedParameters = ((PsiMethod)element).getParameterList().getParameters();

    if (argumentTypes.length != expectedParameters.length) {
      return null;
    }
    
    final PsiSubstitutor methodSubstitutor = result.getSubstitutor();
    return validate(argumentTypes, methodReturnType, methodSubstitutor, expression);
  }

  public String getStreamApiMethodName() {
    return myStreamApiMethodName;
  }

  public LambdaRole getLambdaRole() {
    return myLambdaRole;
  }

  public boolean isAcceptDefaultValue() {
    return myAcceptDefaultValue;
  }

  private ValidationInfo validate(final PsiType[] arguments,
                                  final PsiType methodReturnType,
                                  final @Nullable PsiSubstitutor methodSubstitutor,
                                  final PsiElement context) {
    int lambdaPosition = -1;
    int defaultValuePosition = -1;
    int iterablePosition = -1;

    if (!myAcceptDefaultValue) {
      if (arguments.length != 2) {
        return null;
      }
    } else {
      if (arguments.length != 2 && arguments.length != 3) {
        return null;
      }
    }

    for (int i = 0; i < arguments.length; i++) {
      PsiType type = arguments[i];
      if (type == null) {
        return null;
      }
      if (isFunction(type, methodReturnType, methodSubstitutor, context)) {
        if (lambdaPosition == -1) {
          lambdaPosition = i;
          continue;
        }
        else {
          return null;
        }
      }
      if (isIterableOrArray(type)) {
        if (iterablePosition == -1) {
          iterablePosition = i;
          continue;
        }
        else {
          return null;
        }
      }
      if (myAcceptDefaultValue && methodReturnType.isAssignableFrom(type)) {
        if (defaultValuePosition == -1) {
          defaultValuePosition = i;
        }
        else {
          return null;
        }
      }
    }

    if (lambdaPosition == -1 || iterablePosition == -1) {
      return null;
    }
    if (myAcceptDefaultValue) {
      if (defaultValuePosition == -1 && arguments.length == 3) {
        return null;
      }
    }
    return new ValidationInfo(lambdaPosition, iterablePosition, defaultValuePosition);
  }

  private boolean isFunction(PsiType type, PsiType baseMethodReturnType, PsiSubstitutor methodSubstitutor, PsiElement context) {
    if (type instanceof PsiMethodReferenceType) {
      final PsiMethodReferenceExpression expression = ((PsiMethodReferenceType)type).getExpression();
      final PsiMethod resolvedMethod = (PsiMethod)expression.resolve();
      if (resolvedMethod == null) {
        return false;
      }
      final PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
      if ((parameters.length != 1 && myLambdaRole == LambdaRole.FUNCTION) || (parameters.length != 0 && myLambdaRole == LambdaRole.PREDICATE)) {
        return false;
      }
      final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(expression);
      return isSuitableLambdaRole(returnType, baseMethodReturnType, methodSubstitutor, context);
    } else if (type instanceof PsiLambdaExpressionType) {
      final PsiLambdaExpression expression = ((PsiLambdaExpressionType)type).getExpression();
      final PsiType psiType = LambdaUtil.getFunctionalInterfaceReturnType(expression.getFunctionalInterfaceType());
      return isSuitableLambdaRole(psiType, baseMethodReturnType, methodSubstitutor, context);
    } else if (isSuitableFunctionalType(type, baseMethodReturnType, methodSubstitutor, context)) {
      return true;
    }
    return isJavaLangClassType(type) && myLambdaRole == LambdaRole.PREDICATE;
  }

  private boolean isSuitableLambdaRole(PsiType lambdaReturnType,
                                       PsiType baseMethodReturnType,
                                       PsiSubstitutor methodSubstitutor,
                                       PsiElement context) {
    if (lambdaReturnType == null) {
      return false;
    }
    if (myLambdaRole == LambdaRole.PREDICATE) {
      final PsiClassType boxedBoolean = PsiType.BOOLEAN.getBoxedType(context);
      if (!(PsiType.BOOLEAN.equals(lambdaReturnType) || (boxedBoolean != null && boxedBoolean.equals(lambdaReturnType)))) {
        return false;
      }
    }
    else {
      LOG.assertTrue(myLambdaRole == LambdaRole.FUNCTION);
      if (methodSubstitutor != null) {
        lambdaReturnType = methodSubstitutor.substitute(lambdaReturnType);
      }
      if (baseMethodReturnType instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)baseMethodReturnType).resolveGenerics();
        final Map<PsiTypeParameter, PsiType> substitutionMap = resolveResult.getSubstitutor().getSubstitutionMap();
        if (substitutionMap.size() != 1) {
          return false;
        }
        final PsiType iterableParametrizedType = ObjectUtils.notNull(ContainerUtil.getFirstItem(substitutionMap.values()));
        if (!TypeConversionUtil.isAssignable(lambdaReturnType, iterableParametrizedType)) {
          return false;
        }
      }
      else if (baseMethodReturnType instanceof PsiArrayType) {
        if (!lambdaReturnType.equals(((PsiArrayType)baseMethodReturnType).getComponentType())) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean isJavaLangClassType(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(resolvedClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  private boolean isSuitableFunctionalType(final PsiType type,
                                           final PsiType baseMethodReturnType,
                                           final @Nullable PsiSubstitutor methodSubstitutor,
                                           final PsiElement context) {
    if (type instanceof PsiClassType) {
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass != null) {
        if (resolvedClass instanceof PsiAnonymousClass) {
          final PsiClass baseClass = ((PsiAnonymousClass)resolvedClass).getBaseClassType().resolve();
          if (baseClass == null) {
            return false;
          }
          if (!LambdaUtil.isFunctionalClass(baseClass)) {
            return false;
          }
          PsiMethod superMethod = LambdaUtil.getFunctionalInterfaceMethod(baseClass);
          if (superMethod == null) {
            return false;
          }
          final PsiMethod[] methods = resolvedClass.findMethodsByName(superMethod.getName(), false);
          PsiMethod method = null;
          for (PsiMethod m : methods) {
            if (PsiSuperMethodUtil.isSuperMethod(m, superMethod)) {
              method = m;
            }
          }
          if (method == null) {
            return false;
          }
          final PsiType psiType = methodSubstitutor == null ? method.getReturnType() : methodSubstitutor.substitute(method.getReturnType());
          return isSuitableLambdaRole(psiType, baseMethodReturnType, methodSubstitutor, context);
        } else {
          if (!LambdaUtil.isFunctionalClass(resolvedClass)) {
            return false;
          }
          return isSuitableLambdaRole(LambdaUtil.getFunctionalInterfaceReturnType(type), baseMethodReturnType, methodSubstitutor, context);
        }
        }
      return false;
    } else {
      return false;
    }
  }

  @NotNull
  public PsiExpression convertToStream(@NotNull final PsiMethodCallExpression expression, @Nullable PsiMethod method, boolean force) {
    if (method == null) {
      method = expression.resolveMethod();
      if (method == null) {
        return expression;
      }
    }
    final ValidationInfo validationInfo = force ? validate(method) : validate(expression);
    if (validationInfo == null) {
      return expression;
    }
    final Project project = expression.getProject();
    int lambdaIndex = validationInfo.getLambdaPosition();

    final PsiExpression[] expressions = expression.getArgumentList().getExpressions();
    final PsiExpression iterableExpression = expressions[validationInfo.getIterablePosition()];
    final String pipelineHead = createPipelineHeadText(iterableExpression, force);
    if (pipelineHead == null) {
      return expression;
    }

    PsiExpression lambdaExpression = expressions[lambdaIndex];
    if (!force) {
      lambdaExpression = convertClassTypeExpression(lambdaExpression);
      lambdaExpression = convertToJavaLambda(lambdaExpression);
    }
    if (lambdaExpression == null) {
      return expression;
    }

    final String lambdaExpressionText;
    final String elementText;
    if (!StreamApiConstants.FAKE_FIND_MATCHED.equals(myStreamApiMethodName)) {
      elementText = myStreamApiMethodName;
      lambdaExpressionText = lambdaExpression.getText();
    }
    else {
      elementText = validationInfo.getDefaultValuePosition() != -1
                    ? String.format(StreamApiConstants.FAKE_FIND_MATCHED_WITH_DEFAULT_PATTERN, lambdaExpression.getText(),
                                    expressions[validationInfo.getDefaultValuePosition()].getText())
                    : String.format(StreamApiConstants.FAKE_FIND_MATCHED_PATTERN, lambdaExpression.getText());
      lambdaExpressionText = null;
    }
    final String pipelineTail =
      StreamApiConstants.STREAM_STREAM_API_METHODS.getValue().contains(myStreamApiMethodName)
      ? findSuitableTailMethodForCollection(method)
      : null;

    final PsiElement replaced =
      expression.replace(createPipelineExpression(pipelineHead, elementText, lambdaExpressionText, pipelineTail, project));
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced.getParent());
    return (PsiExpression)replaced;
  }

  private static PsiExpression createPipelineExpression(String pipelineHead,
                                                        String elementText,
                                                        String lambdaExpression,
                                                        String pipelineTail,
                                                        Project project) {
    final StringBuilder sb = new StringBuilder();
    sb.append(pipelineHead).append(".").append(elementText);
    if (lambdaExpression != null) {
      sb.append("(").append(lambdaExpression).append(")");
    }
    if (pipelineTail != null) {
      sb.append(".").append(pipelineTail);
    }
    return JavaPsiFacade.getElementFactory(project).createExpressionFromText(sb.toString(), null);
  }

  @Nullable
  private static String findSuitableTailMethodForCollection(PsiMethod lambdaHandler) {
    final PsiType type = lambdaHandler.getReturnType();
    if (type instanceof PsiArrayType) {
      final PsiType arrayComponentType = ((PsiArrayType)type).getComponentType();
      return "toArray(" + arrayComponentType.getCanonicalText() + "[]::new)";
    }
    else if (type instanceof PsiClassType) {
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass == null) {
        return null;
      }
      final String qName = resolvedClass.getQualifiedName();
      if (qName == null) {
        return null;
      }
      if (qName.equals(CommonClassNames.JAVA_UTIL_LIST)
          || qName.equals(CommonClassNames.JAVA_UTIL_COLLECTION)
          || qName.equals(CommonClassNames.JAVA_LANG_ITERABLE)) {
        return "collect(" + StreamApiConstants.JAVA_UTIL_STREAM_COLLECTORS + ".toList())";
      }
      else if (qName.equals(CommonClassNames.JAVA_UTIL_SET)) {
        return "collect(" + StreamApiConstants.JAVA_UTIL_STREAM_COLLECTORS + ".toSet())";
      }
      else if (qName.equals(CommonClassNames.JAVA_UTIL_ITERATOR)) {
        return "iterator()";
      }
    }
    return null;
  }

  private static PsiExpression convertToJavaLambda(PsiExpression expression) {
    if (expression instanceof PsiMethodReferenceExpression) {
      return expression;
    }
    if (expression instanceof PsiLambdaExpression) {
      return expression;
    }
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
      if (method == null) {
        return null;
      }
      final PsiType type = method.getReturnType();
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      final PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      final PsiClass lambdaClass = result.getElement();
      if (lambdaClass == null) {
        return null;
      }
      final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(lambdaClass);
      LOG.assertTrue(functionalInterfaceMethod != null);
      final String methodName = functionalInterfaceMethod.getName();
      return JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(expression.getText() + "::" + methodName, null);
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiType expressionType = expression.getType();
      final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(expressionType);
      LOG.assertTrue(method != null);
      return JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(expression.getText() + "::" + method.getName(), null);
    }

    final PsiType psiType = expression.getType();
    if (psiType != null && !LambdaUtil.notInferredType(psiType)) {
      return AnonymousCanBeLambdaInspection.replaceAnonymousWithLambda(expression, psiType);
    }
    return null;
  }

  @NotNull
  private static PsiExpression convertClassTypeExpression(PsiExpression expression) {
    final PsiType type = expression.getType();
    if (type instanceof PsiClassType) {
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(resolvedClass.getQualifiedName())) {
        return JavaPsiFacade.getElementFactory(expression.getProject())
          .createExpressionFromText("(" + expression.getText() + ")::isInstance", null);
      }
    }
    return expression;
  }

  public static PsiExpression replaceTypeParameters(PsiExpression collectionExpression) {
    if (collectionExpression instanceof PsiNewExpression) {
      final PsiDiamondType.DiamondInferenceResult diamondResolveResult =
        PsiDiamondTypeImpl.resolveInferredTypesNoCheck((PsiNewExpression)collectionExpression, collectionExpression);
      if (!diamondResolveResult.getInferredTypes().isEmpty()) {
        collectionExpression = PsiDiamondTypeUtil.expandTopLevelDiamondsInside(collectionExpression);
      }
    }
    else if (collectionExpression instanceof PsiMethodCallExpression) {
      final PsiType currentType = collectionExpression.getType();
      if (currentType == null) {
        return null;
      }
      final PsiExpression copiedExpression = (PsiExpression) collectionExpression.copy();
      final PsiType newType = copiedExpression.getType();
      if (!currentType.equals(newType)) {
        final PsiExpression newExpression = AddTypeArgumentsFix.addTypeArguments(copiedExpression, currentType);
        return newExpression == null ? collectionExpression : newExpression;
      }
    }
    return collectionExpression;
  }

  private static String createPipelineHeadText(PsiExpression collectionExpression, boolean force) {
    collectionExpression = replaceTypeParameters(collectionExpression);
    if (collectionExpression == null) return null;
    final PsiType type = collectionExpression.getType();
    if (type instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)type).resolve();
      LOG.assertTrue(resolved != null && resolved.getQualifiedName() != null, type);
      if (InheritanceUtil.isInheritor(resolved, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        return collectionExpression.getText() + ".stream()";
      } else {
        return "java.util.stream.StreamSupport.stream(" + collectionExpression.getText() + ".spliterator(), false)";
      }
    }
    else if (type instanceof PsiArrayType) {
      return CommonClassNames.JAVA_UTIL_ARRAYS + ".stream(" + collectionExpression.getText() + ")";
    } else if (force) {
      return collectionExpression.getText() + ".stream()";
    }
    throw new AssertionError("type: " + type + " is unexpected for expression: " + collectionExpression.getText());
  }

  private static boolean isIterableOrArray(final PsiType type) {
    if (type instanceof PsiClassType) {
      return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE);
    }
    return type instanceof PsiArrayType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PseudoLambdaReplaceTemplate template = (PseudoLambdaReplaceTemplate)o;

    if (myAcceptDefaultValue != template.myAcceptDefaultValue) return false;
    if (!myStreamApiMethodName.equals(template.myStreamApiMethodName)) return false;
    if (myLambdaRole != template.myLambdaRole) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myStreamApiMethodName.hashCode();
    result = 31 * result + myLambdaRole.hashCode();
    result = 31 * result + (myAcceptDefaultValue ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "PseudoLambdaReplaceTemplate{" +
           "myStreamApiMethodName='" + myStreamApiMethodName + '\'' +
           '}';
  }
}
