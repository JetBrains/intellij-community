/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
class PseudoLambdaReplaceTemplate {
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
      ContainerUtil.map2Array(Arrays.asList(parameters), new PsiType[parameters.length], new Function<PsiParameter, PsiType>() {
        @Override
        public PsiType fun(PsiParameter parameter) {
          return parameter.getType();
        }
      });
    final PsiType returnType = method.getReturnType();

    if (returnType instanceof PsiClassType) {
      final PsiClass resolvedReturnTypeClass = ((PsiClassType)returnType).resolve();
      if (!InheritanceUtil.isInheritor(resolvedReturnTypeClass, CommonClassNames.JAVA_LANG_ITERABLE)) {
        return null;
      }
    } else if (!(returnType instanceof PsiArrayType)) {
      return null;
    }
    return validate(parameterTypes, returnType, method);
  }

  @Nullable
  public ValidationInfo validate(final PsiMethodCallExpression expression) {
    final PsiType[] argumentTypes = expression.getArgumentList().getExpressionTypes();
    final PsiType methodReturnType = expression.getType();
    if (methodReturnType == null) {
      return null;
    }

    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return null;
    }
    final PsiParameter[] expectedParameters = method.getParameterList().getParameters();

    if (argumentTypes.length != expectedParameters.length) {
      return null;
    }
    return validate(argumentTypes, methodReturnType, expression);
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

  private ValidationInfo validate(final PsiType[] arguments, final PsiType methodReturnType, final PsiElement context) {
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
      if (isFunction(type, methodReturnType, context)) {
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

  private boolean isFunction(PsiType type, PsiType baseMethodReturnType, PsiElement context) {
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
      return isSuitableLambdaRole(returnType, baseMethodReturnType, context);
    } else if (type instanceof PsiLambdaExpressionType) {
      final PsiLambdaExpression expression = ((PsiLambdaExpressionType)type).getExpression();
      final PsiType psiType = LambdaUtil.getFunctionalInterfaceReturnType(expression.getFunctionalInterfaceType());
      return isSuitableLambdaRole(psiType, baseMethodReturnType, context);
    } else if (isSuitableFunctionalType(type, baseMethodReturnType, context)) {
      return true;
    }
    return isJavaLangClassType(type) && myLambdaRole == LambdaRole.PREDICATE;
  }

  private boolean isSuitableLambdaRole(final PsiType lambdaReturnType,
                                       final PsiType baseMethodReturnType,
                                       final PsiElement context) {
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
      if (baseMethodReturnType instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)baseMethodReturnType).resolveGenerics();
        final Map<PsiTypeParameter, PsiType> substitutionMap = resolveResult.getSubstitutor().getSubstitutionMap();
        if (substitutionMap.size() != 1) {
          return false;
        }
        final PsiType iterableParametrizedType = ContainerUtil.getFirstItem(substitutionMap.values());
        if (!lambdaReturnType.equals(iterableParametrizedType)) {
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

  private boolean isSuitableFunctionalType(final PsiType type, final PsiType baseMethodReturnType, final PsiElement context) {
    if (type instanceof PsiClassType) {
      PsiClass targetClass = ((PsiClassType)type).resolve();
      if (targetClass != null) {
        if (targetClass instanceof PsiAnonymousClass) {
          targetClass = ((PsiAnonymousClass)targetClass).getBaseClassType().resolve();
          if (targetClass == null) {
            return false;
          }
        }
        if (!LambdaUtil.isFunctionalClass(targetClass)) {
          return false;
        }
        PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(targetClass);
        final PsiMethod[] methods = ((PsiClassType)type).resolve().findMethodsByName(method.getName(), false);
        if (methods.length != 1) {
          return false;
        }
        method = methods[0];
        if (method == null) {
          return false;
        }
        final PsiType psiType;
        if (type instanceof PsiClassReferenceType) {
          final PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType)type).getReference();
          final PsiClass resolvedClass = ((PsiClassReferenceType)type).resolve();
          final PsiTypeParameter[] typeParameters = resolvedClass.getTypeParameters();
          final PsiType[] substitutedTypeParameters = reference.getTypeParameters();

          LOG.assertTrue(typeParameters.length == substitutedTypeParameters.length);
          final Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
          for (int i = 0; i < typeParameters.length; i++) {
            PsiTypeParameter parameter = typeParameters[i];
            PsiType t = substitutedTypeParameters[i];
            substitutionMap.put(parameter, t);
          }
          PsiSubstitutor substitutor = PsiSubstitutorImpl.createSubstitutor(substitutionMap);
          psiType = substitutor.substitute(method.getReturnType());
        } else {
          psiType = method.getReturnType();
        }
        return isSuitableLambdaRole(psiType, baseMethodReturnType, context);
      }
      return false;
    } else {
      return false;
    }
  }

  @NotNull
  public PsiExpression convertToStream(final PsiMethodCallExpression expression, PsiMethod method, boolean force) {
    LOG.assertTrue(expression != null);
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
    PsiExpression lambdaExpression = expressions[lambdaIndex];
    if (!force) {
      lambdaExpression = convertClassTypeExpression(lambdaExpression);
      lambdaExpression = convertToJavaLambda(lambdaExpression, myStreamApiMethodName);
    }
    LOG.assertTrue(lambdaExpression != null);

    final PsiExpression iterableExpression = expressions[validationInfo.getIterablePosition()];
    final String pipelineHead = createPipelineHeadText(iterableExpression, lambdaExpression, force);


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

  private static void convertNewExpression(PsiMethod containingMethod, PsiNewExpression newExpression, PsiClass expectedReturnClass) {
    final String expectedReturnQName = expectedReturnClass.getQualifiedName();
    LOG.assertTrue(expectedReturnQName != null);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(newExpression.getProject());
    PsiAnonymousClass anonymousClass = PsiTreeUtil.findChildOfType(newExpression, PsiAnonymousClass.class);
    LOG.assertTrue(anonymousClass != null);
    PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.findChildOfType(anonymousClass, PsiJavaCodeReferenceElement.class);
    LOG.assertTrue(referenceElement != null);
    final PsiReferenceParameterList parameterList = PsiTreeUtil.findChildOfType(referenceElement, PsiReferenceParameterList.class);
    final PsiJavaCodeReferenceElement newCodeReferenceElement = factory.createReferenceFromText(expectedReturnClass.getQualifiedName()
                                                                                                +
                                                                                                (parameterList == null
                                                                                                 ? ""
                                                                                                 : parameterList.getText()), null);
    referenceElement.replace(newCodeReferenceElement);
    final List<PsiMethod> methods = ContainerUtil.filter(anonymousClass.getMethods(), new Condition<PsiMethod>() {
      @Override
      public boolean value(PsiMethod method) {
        return !"equals".equals(method.getName());
      }
    });
    LOG.assertTrue(methods.size() == 1, methods);
    final PsiMethod method = methods.get(0);
    method.setName(expectedReturnClass.getMethods()[0].getName());
    final PsiTypeElement element = containingMethod.getReturnTypeElement();
    if (element != null) {
      final PsiReferenceParameterList genericParameter = PsiTreeUtil.findChildOfType(element, PsiReferenceParameterList.class);
      element.replace(
        factory.createTypeElementFromText(expectedReturnQName + (genericParameter == null ? "" : genericParameter.getText()), null));
    }
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

  private static PsiExpression convertToJavaLambda(PsiExpression expression, String streamApiMethodName) {
    if (streamApiMethodName.equals(StreamApiConstants.FAKE_FIND_MATCHED)) {
      streamApiMethodName = StreamApiConstants.FILTER;
    }
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
      final PsiClass lambdaClass = ((PsiClassType)type).resolve();
      if (lambdaClass == null) {
        return null;
      }
      final String methodName = lambdaClass.getMethods()[0].getName();
      if (tryConvertPseudoLambdaToStreamApi(method, resolveStreamApiLambdaClass(expression.getProject(), streamApiMethodName))) {
        return expression;
      }
      else {
        return JavaPsiFacade.getElementFactory(expression.getProject())
          .createExpressionFromText(expression.getText() + "::" + methodName, null);
      }
    }
    return AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(expression, true);
  }

  @NotNull
  private static PsiClass resolveStreamApiLambdaClass(Project project, String streamApiMethodName) {
    final PsiClass javaUtilStream = JavaPsiFacade.getInstance(project)
      .findClass(StreamApiConstants.JAVA_UTIL_STREAM_STREAM, GlobalSearchScope.notScope(GlobalSearchScope.projectScope(project)));
    LOG.assertTrue(javaUtilStream != null);
    final PsiMethod[] methods = javaUtilStream.findMethodsByName(streamApiMethodName, false);
    LOG.assertTrue(methods.length == 1);
    final PsiMethod method = methods[0];
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    LOG.assertTrue(parameters.length == 1);
    final PsiType type = parameters[0].getType();
    LOG.assertTrue(type instanceof PsiClassType);
    final PsiClass resolved = ((PsiClassType)type).resolve();
    LOG.assertTrue(resolved != null);
    return resolved;
  }

  private static boolean tryConvertPseudoLambdaToStreamApi(final @NotNull PsiMethod method, final @NotNull PsiClass expectedReturnClass) {
    final PsiType currentReturnType = method.getReturnType();
    if (!(currentReturnType instanceof PsiClassType)) {
      LOG.error("pseudo-lambda return type must be class " + currentReturnType);
      return true;
    }
    final PsiClass resolvedCurrentReturnType = ((PsiClassType)currentReturnType).resolve();
    if (expectedReturnClass.getManager().areElementsEquivalent(expectedReturnClass, resolvedCurrentReturnType)) {
      return true;
    }
    final PsiCodeBlock body = method.getBody();
    Collection<PsiReturnStatement> returnStatements = PsiTreeUtil.findChildrenOfType(body, PsiReturnStatement.class);
    returnStatements = ContainerUtil.filter(returnStatements, new Condition<PsiReturnStatement>() {
      @Override
      public boolean value(PsiReturnStatement statement) {
        return PsiTreeUtil.getParentOfType(statement, PsiMethod.class) == method;
      }
    });
    if (returnStatements.size() != 1) {
      return false;
    }
    final PsiReturnStatement returnStatement = ContainerUtil.getFirstItem(returnStatements);
    assert returnStatement != null;
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue instanceof PsiNewExpression) {
      convertNewExpression(method, (PsiNewExpression)returnValue, expectedReturnClass);
      return true;
    }
    else {
      return false;
    }
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

  private static String createPipelineHeadText(PsiExpression collectionExpression, PsiExpression lambdaExpression, boolean force) {
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
        collectionExpression = AddTypeArgumentsFix.addTypeArguments(copiedExpression, currentType);
      }
    }
    final PsiType type = collectionExpression.getType();
    if (type instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)type).resolve();
      LOG.assertTrue(resolved != null && resolved.getQualifiedName() != null, type);
      return collectionExpression.getText() + ".stream()";
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
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass != null) {
        return InheritanceUtil.isInheritor(resolvedClass, CommonClassNames.JAVA_LANG_ITERABLE);
      }
    }
    else if (type instanceof PsiArrayType) {
      return true;
    }
    return false;
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
