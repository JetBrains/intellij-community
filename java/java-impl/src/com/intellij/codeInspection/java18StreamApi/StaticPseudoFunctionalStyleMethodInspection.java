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

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class StaticPseudoFunctionalStyleMethodInspection extends BaseJavaBatchLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(StaticPseudoFunctionalStyleMethodInspection.class);
  private final StaticPseudoFunctionalStyleMethodOptions myOptions = new StaticPseudoFunctionalStyleMethodOptions();

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    myOptions.readExternal(node);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    myOptions.writeExternal(node);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return myOptions.createPanel();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
          String qName = methodCallExpression.getMethodExpression().getQualifiedName();
          if (qName == null) {
            return;
          }
          qName = StringUtil.getShortName(qName);
          final Collection<StaticPseudoFunctionalStyleMethodOptions.PipelineElement> handlerInfos = myOptions.findElementsByMethodName(qName);
          if (handlerInfos.isEmpty()) {
            return;
          }
          final PsiMethod method = methodCallExpression.resolveMethod();
          if (method == null) {
            return;
          }
          final PsiClass aClass = method.getContainingClass();
          if (aClass == null) {
            return;
          }
          final String classQualifiedName = aClass.getQualifiedName();
          if (classQualifiedName == null) {
            return;
          }
          StaticPseudoFunctionalStyleMethodOptions.PipelineElement suitableHandler = null;
          for (StaticPseudoFunctionalStyleMethodOptions.PipelineElement h : handlerInfos) {
            if (h.getHandlerClass().equals(classQualifiedName)) {
              suitableHandler = h;
              break;
            }
          }
          if (suitableHandler == null) {
            return;
          }
          final int lambdaIndex = validateMethodParameters(methodCallExpression, method, suitableHandler.getStreamApiMethodName() == StreamApiConstants.FAKE_FIND_MATCHED);
          if (lambdaIndex != -1) {
            holder.registerProblem(methodCallExpression.getMethodExpression(), "",
                                   new ReplacePseudoLambdaWithLambda(lambdaIndex, methodCallExpression, method, suitableHandler));
          }
        }
      }
    };
  }

  public static class ReplacePseudoLambdaWithLambda implements LocalQuickFix {
    private final int myLambdaIndex;
    private final SmartPsiElementPointer<PsiMethod> myMethodPointer;
    private final StaticPseudoFunctionalStyleMethodOptions.PipelineElement mySuitableHandler;

    private ReplacePseudoLambdaWithLambda(int lambdaIndex,
                                          @NotNull PsiMethodCallExpression expression,
                                          @NotNull PsiMethod method,
                                          @NotNull StaticPseudoFunctionalStyleMethodOptions.PipelineElement suitableHandler) {
      myLambdaIndex = lambdaIndex;
      myMethodPointer = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(method);
      mySuitableHandler = suitableHandler;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with Java Stream API pipeline";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      LOG.assertTrue(expression != null);
      final PsiExpression[] expressions = expression.getArgumentList().getExpressions();
      PsiExpression lambdaExpression = expressions[myLambdaIndex];
      lambdaExpression = convertClassTypeExpression(lambdaExpression);
      lambdaExpression = convertToJavaLambda(lambdaExpression, mySuitableHandler.getStreamApiMethodName());
      LOG.assertTrue(lambdaExpression != null);

      final PsiExpression collectionExpression = expressions[(1 + myLambdaIndex) % 2];
      final String pipelineHead = createPipelineHeadText(collectionExpression);


      final String lambdaExpressionText;
      final String elementText;
      if (!StreamApiConstants.FAKE_FIND_MATCHED.equals(mySuitableHandler.getStreamApiMethodName())) {
        elementText = mySuitableHandler.getStreamApiMethodName();
        lambdaExpressionText = lambdaExpression.getText();
      }
      else {
        elementText = expressions.length == 3
                      ? String.format(StreamApiConstants.FAKE_FIND_MATCHED_WITH_DEFAULT_PATTERN, lambdaExpression.getText(), expressions[2].getText())
                      : String.format(StreamApiConstants.FAKE_FIND_MATCHED_PATTERN, lambdaExpression.getText());
        lambdaExpressionText = null;
      }
      final String pipelineTail =
        StreamApiConstants.STREAM_STREAM_API_METHODS.getValue().contains(mySuitableHandler.getStreamApiMethodName())
        ? findSuitableTailMethodForCollection(myMethodPointer.getElement())
        : null;

      final PsiElement replaced =
        expression.replace(createPipelineExpression(pipelineHead, elementText, lambdaExpressionText, pipelineTail, project));
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced.getParent());
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

    private static String createPipelineHeadText(PsiExpression collectionExpression) {
      final PsiType type = collectionExpression.getType();
      if (type instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)type).resolve();
        LOG.assertTrue(resolved != null && resolved.getQualifiedName() != null, type);
        return collectionExpression.getText() + ".stream()";
      }
      else if (type instanceof PsiArrayType) {
        return CommonClassNames.JAVA_UTIL_ARRAYS + ".stream(" + collectionExpression.getText() + ")";
      }
      throw new AssertionError("type: " + type + " is unexpected");
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
  }

  private static int validateMethodParameters(final PsiMethodCallExpression methodCallExpression, final PsiMethod method, boolean canThirdParameterExist) {
    final PsiType[] argumentTypes = methodCallExpression.getArgumentList().getExpressionTypes();
    final PsiParameter[] expectedParameters = method.getParameterList().getParameters();

    if (argumentTypes.length != expectedParameters.length) {
      return -1;
    }
    if (expectedParameters.length == 2 || (canThirdParameterExist && expectedParameters.length == 3)) {
      final int collectionOrArrayIndex = findCollectionOrArrayPlacement(expectedParameters);
      if (collectionOrArrayIndex == -1) {
        return -1;
      }
      return (1 + collectionOrArrayIndex) % 2;

    }
    return -1;
  }

  private static int findCollectionOrArrayPlacement(final PsiParameter[] parameters) {
    for (int i = 0, length = parameters.length; i < length; i++) {
      PsiParameter parameter = parameters[i];
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType || type instanceof PsiArrayType) {
        return i;
      }
    }
    return -1;
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
      element.replace(factory.createTypeElementFromText(expectedReturnQName + (genericParameter == null ? "" : genericParameter.getText()), null));
    }
  }

  @Nullable
  private static String findSuitableTailMethodForCollection(PsiMethod lambdaHandler) {
    final PsiType type = lambdaHandler.getReturnType();
    if (type instanceof PsiArrayType) {
      return "toArray(String[]::new)";
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
}