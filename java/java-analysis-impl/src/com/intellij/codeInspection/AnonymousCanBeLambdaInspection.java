/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

/**
 * User: anna
 */
public class AnonymousCanBeLambdaInspection extends BaseJavaBatchLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + AnonymousCanBeLambdaInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Anonymous type can be replaced with lambda";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2Lambda";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        super.visitAnonymousClass(aClass);
        final PsiElement parent = aClass.getParent();
        final PsiElement lambdaContext = parent != null ? parent.getParent() : null;
        if (lambdaContext != null && 
            (LambdaUtil.isValidLambdaContext(lambdaContext) || !(lambdaContext instanceof PsiExpressionStatement)) &&
            canBeConvertedToLambda(aClass, false)) {
          final PsiElement lBrace = aClass.getLBrace();
          LOG.assertTrue(lBrace != null);
          final TextRange rangeInElement = new TextRange(0, aClass.getStartOffsetInParent() + lBrace.getStartOffsetInParent());
          holder.registerProblem(parent, "Anonymous #ref #loc can be replaced with lambda",
                                 ProblemHighlightType.LIKE_UNUSED_SYMBOL, rangeInElement, new ReplaceWithLambdaFix());
        }
      }
    };
  }

  private static boolean hasRuntimeAnnotations(PsiMethod method) {
    PsiAnnotation[] annotations = method.getModifierList().getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      PsiElement target = ref != null ? ref.resolve() : null;
      if (target instanceof PsiClass) {
        final PsiAnnotation retentionAnno = AnnotationUtil.findAnnotation((PsiClass)target, Retention.class.getName());
        if (retentionAnno != null) {
          PsiAnnotationMemberValue value = retentionAnno.findAttributeValue("value");
          if (value instanceof PsiReferenceExpression) {
            final PsiElement resolved = ((PsiReferenceExpression)value).resolve();
            if (resolved instanceof PsiField && RetentionPolicy.RUNTIME.name().equals(((PsiField)resolved).getName())) {
              final PsiClass containingClass = ((PsiField)resolved).getContainingClass();
              if (containingClass != null && RetentionPolicy.class.getName().equals(containingClass.getQualifiedName())) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  public static boolean hasForbiddenRefsInsideBody(PsiMethod method, PsiAnonymousClass aClass) {
    final ForbiddenRefsChecker checker = new ForbiddenRefsChecker(method, aClass);
    final PsiCodeBlock body = method.getBody();
    LOG.assertTrue(body != null);
    body.accept(checker);
    return checker.hasForbiddenRefs();
  }

  private static PsiType getInferredType(PsiAnonymousClass aClass, PsiMethod method) {
    final PsiExpression expression = (PsiExpression)aClass.getParent();
    final PsiType psiType = PsiTypesUtil.getExpectedTypeByParent(expression);
    if (psiType != null) {
      return psiType;
    }

    PsiExpression topExpr = expression;
    while (topExpr.getParent() instanceof PsiParenthesizedExpression) {
      topExpr = (PsiExpression)topExpr.getParent();
    }

    final PsiElement parent = topExpr.getParent();
    if (parent instanceof PsiExpressionList) {
      PsiExpressionList expressionList = (PsiExpressionList)parent;
      final PsiElement callExpr = expressionList.getParent();
      if (callExpr instanceof PsiCallExpression) {
        PsiExpression[] expressions = expressionList.getExpressions();
        int i = ArrayUtilRt.find(expressions, topExpr);
        if (i < 0) return null;
        final PsiCallExpression copy = (PsiCallExpression)callExpr.copy();
        final PsiExpressionList argumentList = copy.getArgumentList();
        if (argumentList != null) {
          final PsiExpression classArg = argumentList.getExpressions()[i];
          PsiExpression lambda = JavaPsiFacade.getElementFactory(aClass.getProject())
            .createExpressionFromText(ReplaceWithLambdaFix.composeLambdaText(method), expression);
          lambda = (PsiExpression)classArg.replace(lambda);
          ((PsiLambdaExpression)lambda).getBody().replace(method.getBody());
          return LambdaUtil.getFunctionalInterfaceType(lambda, true);
        }
      }
    }
    return null;
  }

  public static boolean canBeConvertedToLambda(PsiAnonymousClass aClass, boolean acceptParameterizedFunctionTypes) {
    if (PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8)) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(aClass.getBaseClassType());
      if (interfaceMethod != null && (acceptParameterizedFunctionTypes || !interfaceMethod.hasTypeParameters())) {
        final PsiMethod[] methods = aClass.getMethods();
        if (methods.length == 1 && 
            aClass.getFields().length == 0 && 
            aClass.getInnerClasses().length == 0 && 
            aClass.getInitializers().length == 0) {
          final PsiMethod method = methods[0];
          return method.getBody() != null &&
                 !hasForbiddenRefsInsideBody(method, aClass) &&
                 !hasRuntimeAnnotations(method) &&
                 !method.hasModifierProperty(PsiModifier.SYNCHRONIZED);
        }
      }
    }
    return false;
  }

  public static PsiExpression replaceAnonymousWithLambda(@NotNull PsiElement anonymousClass, PsiType expectedType) {
    PsiNewExpression newArrayExpression = (PsiNewExpression)JavaPsiFacade.getElementFactory(anonymousClass.getProject())
      .createExpressionFromText("new " + expectedType.getCanonicalText() + "[]{" + anonymousClass.getText() + "}", anonymousClass);
    PsiArrayInitializerExpression initializer = newArrayExpression.getArrayInitializer();
    LOG.assertTrue(initializer != null);
    return replacePsiElementWithLambda(initializer.getInitializers()[0], true, false);
  }

  public static PsiExpression replacePsiElementWithLambda(@NotNull PsiElement element,
                                                          final boolean ignoreEqualsMethod,
                                                          boolean forceIgnoreTypeCast) {
    if (element instanceof PsiNewExpression) {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return null;
      final PsiAnonymousClass anonymousClass = ((PsiNewExpression)element).getAnonymousClass();

      if (anonymousClass == null) return null;

      ChangeContextUtil.encodeContextInfo(anonymousClass, true);
      final String canonicalText = anonymousClass.getBaseClassType().getCanonicalText();

      final PsiMethod method;
      if (ignoreEqualsMethod) {
        final List<PsiMethod> methods = ContainerUtil.filter(anonymousClass.getMethods(), new Condition<PsiMethod>() {
          @Override
          public boolean value(PsiMethod method) {
            return !"equals".equals(method.getName());
          }
        });
        method = methods.get(0);
      } else {
        method = anonymousClass.getMethods()[0];
      }
      if (method == null) return null;

      final PsiCodeBlock body = method.getBody();
      if (body == null) return null;

      final ForbiddenRefsChecker checker = new ForbiddenRefsChecker(method, anonymousClass);
      body.accept(checker);

      final PsiResolveHelper helper = PsiResolveHelper.SERVICE.getInstance(body.getProject());
      final Set<PsiVariable> conflictingLocals = checker.getLocals();
      for (Iterator<PsiVariable> iterator = conflictingLocals.iterator(); iterator.hasNext(); ) {
        PsiVariable local = iterator.next();
        final String localName = local.getName();
        if (localName == null || helper.resolveReferencedVariable(localName, anonymousClass) == null) {
          iterator.remove();
        }
      }

      final Project project = element.getProject();
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

      ReplaceWithLambdaFix
        .giveUniqueNames(project, elementFactory, body, conflictingLocals.toArray(new PsiVariable[conflictingLocals.size()]));

      final String withoutTypesDeclared = ReplaceWithLambdaFix.composeLambdaText(method);

      PsiLambdaExpression lambdaExpression =
        (PsiLambdaExpression)elementFactory.createExpressionFromText(withoutTypesDeclared, anonymousClass);

      PsiElement lambdaBody = lambdaExpression.getBody();
      LOG.assertTrue(lambdaBody != null);
      lambdaBody.replace(body);

      ReplaceWithLambdaFix
        .giveUniqueNames(project, elementFactory, lambdaExpression, lambdaExpression.getParameterList().getParameters());

      final PsiNewExpression newExpression = (PsiNewExpression)anonymousClass.getParent();
      lambdaExpression = (PsiLambdaExpression)newExpression.replace(lambdaExpression);
      final PsiExpression singleExpr = RedundantLambdaCodeBlockInspection.isCodeBlockRedundant(lambdaExpression,
                                                                                               lambdaExpression.getBody());
      if (singleExpr != null) {
        lambdaExpression.getBody().replace(singleExpr);
      }
      ChangeContextUtil.decodeContextInfo(lambdaExpression, null, null);

      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      if (forceIgnoreTypeCast) {
        return (PsiExpression)javaCodeStyleManager.shortenClassReferences(lambdaExpression);
      }

      PsiTypeCastExpression typeCast = (PsiTypeCastExpression)elementFactory
        .createExpressionFromText("(" + canonicalText + ")" + withoutTypesDeclared, lambdaExpression);
      final PsiExpression typeCastOperand = typeCast.getOperand();
      LOG.assertTrue(typeCastOperand instanceof PsiLambdaExpression);
      final PsiElement fromText = ((PsiLambdaExpression)typeCastOperand).getBody();
      LOG.assertTrue(fromText != null);
      lambdaBody = lambdaExpression.getBody();
      LOG.assertTrue(lambdaBody != null);
      fromText.replace(lambdaBody);
      ((PsiLambdaExpression)typeCastOperand).getParameterList().replace(lambdaExpression.getParameterList());
      typeCast = (PsiTypeCastExpression)lambdaExpression.replace(typeCast);
      if (RedundantCastUtil.isCastRedundant(typeCast)) {
        final PsiExpression operand = typeCast.getOperand();
        LOG.assertTrue(operand != null);
        return (PsiExpression)typeCast.replace(operand);
      }
      return (PsiExpression)javaCodeStyleManager.shortenClassReferences(typeCast);
    }
    return null;
  }

  private static class ReplaceWithLambdaFix implements LocalQuickFix, HighPriorityAction {
    @NotNull
    @Override
    public String getName() {
      return "Replace with lambda";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        replacePsiElementWithLambda(element, false, false);
      }
    }

    private static void giveUniqueNames(Project project,
                                        final PsiElementFactory elementFactory,
                                        PsiElement body,
                                        PsiVariable[] parameters) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final Map<PsiVariable, String> names = new HashMap<PsiVariable, String>();
      for (PsiVariable parameter : parameters) {
        String parameterName = parameter.getName();
        final String uniqueVariableName = codeStyleManager.suggestUniqueVariableName(parameterName, parameter.getParent(), false);
        if (!Comparing.equal(parameterName, uniqueVariableName)) {
          names.put(parameter, uniqueVariableName);
        }
      }

      final LinkedHashMap<PsiElement, PsiElement> replacements = new LinkedHashMap<PsiElement, PsiElement>();
      body.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitVariable(PsiVariable variable) {
          super.visitVariable(variable);
          final String newName = names.get(variable);
          if (newName != null) {
            replacements.put(variable.getNameIdentifier(), elementFactory.createIdentifier(newName));
          }
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          final PsiElement resolve = expression.resolve();
          if (resolve instanceof PsiVariable) {
            final String newName = names.get(resolve);
            if (newName != null) {
              replacements.put(expression, elementFactory.createExpressionFromText(newName, expression));
            }
          }
        }
      });

      for (PsiElement psiElement : replacements.keySet()) {
        psiElement.replace(replacements.get(psiElement));
      }
    }

    private static String composeLambdaText(PsiMethod method) {
      final StringBuilder buf = new StringBuilder();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length != 1) {
        buf.append("(");
      }
      buf.append(StringUtil.join(parameters,
                                 new Function<PsiParameter, String>() {
                                   @Override
                                   public String fun(PsiParameter parameter) {
                                     return composeParameter(parameter);
                                   }
                                 }, ","));
      if (parameters.length != 1) {
        buf.append(")");
      }
      buf.append("-> {}");
      return buf.toString();
    }

    private static String composeParameter(PsiParameter parameter) {
      String parameterName = parameter.getName();
      if (parameterName == null) {
        parameterName = "";
      }
      return parameterName;
    }
  }

  public static boolean functionalInterfaceMethodReferenced(PsiMethod psiMethod,
                                                            PsiAnonymousClass anonymClass,
                                                            PsiCallExpression callExpression) {
    if (psiMethod != null && !psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
        return false;
      }

      if (callExpression instanceof PsiMethodCallExpression && 
          ((PsiMethodCallExpression)callExpression).getMethodExpression().isQualified()) {
        return false;
      }

      if (InheritanceUtil.isInheritorOrSelf(anonymClass, containingClass, true) &&
          !InheritanceUtil.hasEnclosingInstanceInScope(containingClass, anonymClass.getParent(), true, true)) {
        return true;
      }
    }
    return false;
  }

  private static class ForbiddenRefsChecker extends JavaRecursiveElementWalkingVisitor {
    private boolean myBodyContainsForbiddenRefs;
    private final Set<PsiVariable> myLocals = ContainerUtilRt.newHashSet(5);

    private final PsiMethod myMethod;
    private final PsiAnonymousClass myAnonymClass;

    private final PsiType myInferredType;

    public ForbiddenRefsChecker(PsiMethod method,
                                PsiAnonymousClass aClass) {
      myMethod = method;
      myAnonymClass = aClass;
      final PsiType inferredType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(getInferredType(aClass, method));
      final PsiClassType baseClassType = aClass.getBaseClassType();
      myInferredType = !baseClassType.equals(inferredType) ? inferredType : null;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression) {
      if (myBodyContainsForbiddenRefs) return;

      super.visitMethodCallExpression(methodCallExpression);
      final PsiMethod psiMethod = methodCallExpression.resolveMethod();
      if (psiMethod == myMethod ||
          functionalInterfaceMethodReferenced(psiMethod, myAnonymClass, methodCallExpression) ||
          psiMethod != null &&
          !methodCallExpression.getMethodExpression().isQualified() &&
          "getClass".equals(psiMethod.getName()) &&
          psiMethod.getParameterList().getParametersCount() == 0) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitThisExpression(PsiThisExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      if (expression.getQualifier() == null) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitSuperExpression(PsiSuperExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      if (expression.getQualifier() == null) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitVariable(PsiVariable variable) {       
      if (myBodyContainsForbiddenRefs) return;

      super.visitVariable(variable);
      if (!(variable instanceof PsiField)) {
        myLocals.add(variable);
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      super.visitReferenceExpression(expression);
      if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
        final PsiField field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          final PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiField && 
              ((PsiField)resolved).getContainingClass() == field.getContainingClass() && 
              expression.getQualifierExpression() == null) {
            final PsiExpression initializer = ((PsiField)resolved).getInitializer();
            if (initializer == null ||
                resolved == field ||
                initializer.getTextOffset() > myAnonymClass.getTextOffset() && !((PsiField)resolved).hasModifierProperty(PsiModifier.STATIC)) {
              myBodyContainsForbiddenRefs = true;
              return;
            }
          }
        } else {
          final PsiMethod method = PsiTreeUtil.getParentOfType(myAnonymClass, PsiMethod.class);
          if (method != null && method.isConstructor()) {
            final PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiField && 
                ((PsiField)resolved).hasModifierProperty(PsiModifier.FINAL) &&
                ((PsiField)resolved).getInitializer() == null &&
                ((PsiField)resolved).getContainingClass() == method.getContainingClass()) {
              try {
                final PsiCodeBlock constructorBody = method.getBody();
                if (constructorBody != null) {
                  final ControlFlow flow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(constructorBody);
                  final int startOffset = flow.getStartOffset(myAnonymClass);
                  final Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, startOffset, false);
                  if (!writtenVariables.contains(resolved)) {
                    myBodyContainsForbiddenRefs = true;
                    return;
                  }
                }
              }
              catch (AnalysisCanceledException e) {
                myBodyContainsForbiddenRefs = true;
                return;
              }
            }
          }
        }
      }

      if (myInferredType != null) {
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiParameter && ((PsiParameter)resolved).getDeclarationScope() == myMethod) {
          if (!(myInferredType instanceof PsiClassType)) {
            myBodyContainsForbiddenRefs = true;
            return;
          }
          final int parameterIndex = myMethod.getParameterList().getParameterIndex((PsiParameter)resolved);
          for (PsiMethod superMethod : myMethod.findDeepestSuperMethods()) {
            final PsiType paramType = superMethod.getParameterList().getParameters()[parameterIndex].getType();
            final PsiClass superClass = superMethod.getContainingClass();
            if (superClass != null) {
              final PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)myInferredType).resolveGenerics();
              final PsiClass classCandidate = classResolveResult.getElement();
              if (classCandidate == null) {
                myBodyContainsForbiddenRefs = true;
                return;
              }
              final PsiSubstitutor inferredSubstitutor = TypeConversionUtil.getClassSubstitutor(superClass, classCandidate, classResolveResult.getSubstitutor());
              final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, myAnonymClass.getBaseClassType());
              if (inferredSubstitutor != null &&
                  !Comparing.equal(inferredSubstitutor.substitute(paramType), substitutor.substitute(paramType))) {
                myBodyContainsForbiddenRefs = true;
                return;
              }
            }
          }
        }
      }
    }

    public boolean hasForbiddenRefs() {
      return myBodyContainsForbiddenRefs;
    }

    public Set<PsiVariable> getLocals() {
      return myLocals;
    }
  }
}
