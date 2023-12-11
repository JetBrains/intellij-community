// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.function.UnaryOperator;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class AnonymousCanBeLambdaInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(AnonymousCanBeLambdaInspection.class);

  public boolean reportNotAnnotatedInterfaces = true;

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
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

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("reportNotAnnotatedInterfaces",
               JavaAnalysisBundle.message("report.when.interface.is.not.annotated.with.functional.interface")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnonymousClass(final @NotNull PsiAnonymousClass aClass) {
        super.visitAnonymousClass(aClass);
        final PsiElement parent = aClass.getParent();
        if (canBeConvertedToLambda(aClass, false, isOnTheFly || reportNotAnnotatedInterfaces, Collections.emptySet())) {
          final PsiElement lBrace = aClass.getLBrace();
          LOG.assertTrue(lBrace != null);
          final TextRange rangeInElement = new TextRange(0, aClass.getStartOffsetInParent() + lBrace.getStartOffsetInParent());
          ProblemHighlightType type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          if (isOnTheFly && !reportNotAnnotatedInterfaces) {
            final PsiClass baseClass = aClass.getBaseClassType().resolve();
            LOG.assertTrue(baseClass != null);
            if (!AnnotationUtil.isAnnotated(baseClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, CHECK_EXTERNAL)) {
              type = ProblemHighlightType.INFORMATION;
            }
          }
          holder.registerProblem(parent, JavaAnalysisBundle.message("anonymous.ref.loc.can.be.replaced.with.lambda"),
                                 type, rangeInElement, new ReplaceWithLambdaFix());
        }
      }
    };
  }

  public static boolean mustKeepAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull Set<String> runtimeAnnotationsToIgnore) {
    PsiModifierList modifierList = listOwner.getModifierList();
    if (modifierList == null) return false;
    PsiAnnotation[] annotations = modifierList.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
      String fqn;
      if (ref != null &&
          ref.resolve() instanceof PsiClass annotationClass &&
          ((fqn = annotationClass.getQualifiedName()) == null ||
          !runtimeAnnotationsToIgnore.contains(fqn))) {
        final PsiAnnotation retentionAnno = AnnotationUtil.findAnnotation(annotationClass, Retention.class.getName());
        // Default retention is CLASS: keep it
        if (retentionAnno == null) return true;
        if (retentionAnno.findAttributeValue("value") instanceof PsiReferenceExpression retentionValue &&
            retentionValue.resolve() instanceof PsiField retentionField &&
            (RetentionPolicy.RUNTIME.name().equals(retentionField.getName()) ||
             RetentionPolicy.CLASS.name().equals(retentionField.getName()))) {
          final PsiClass containingClass = retentionField.getContainingClass();
          if (containingClass != null && RetentionPolicy.class.getName().equals(containingClass.getQualifiedName())) {
            return true;
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

    final PsiCall call = LambdaUtil.treeWalkUp(topExpr);
    if (call != null && call.resolveMethod() != null) {
      Object marker = new Object();
      PsiTreeUtil.mark(aClass, marker);
      PsiCall copyCall = LambdaUtil.copyTopLevelCall(call);
      if (copyCall == null) return null;
      final PsiElement classArg = PsiTreeUtil.releaseMark(copyCall, marker);
      if (classArg instanceof PsiAnonymousClass) {
        PsiExpression lambda = JavaPsiFacade.getElementFactory(aClass.getProject())
          .createExpressionFromText(ReplaceWithLambdaFix.composeLambdaText(method), expression);
        lambda = (PsiExpression)classArg.getParent().replace(lambda);
        ((PsiLambdaExpression)lambda).getBody().replace(method.getBody());
        final PsiType interfaceType;
        if (copyCall.resolveMethod() == null) {
          return PsiTypes.nullType();
        }
        else {
          interfaceType = ((PsiLambdaExpression)lambda).getFunctionalInterfaceType();
        }

        return interfaceType;
      }
    }

    return PsiTypes.nullType();
  }

  public static boolean canBeConvertedToLambda(PsiAnonymousClass aClass,
                                               boolean acceptParameterizedFunctionTypes,
                                               @NotNull Set<String> ignoredRuntimeAnnotations) {
    return canBeConvertedToLambda(aClass, acceptParameterizedFunctionTypes, true, ignoredRuntimeAnnotations);
  }

  public static boolean isLambdaForm(PsiAnonymousClass aClass, Set<String> ignoredRuntimeAnnotations) {
    PsiMethod[] methods = aClass.getMethods();
    if(methods.length != 1) return false;
    PsiMethod method = methods[0];
    return aClass.getFields().length == 0 &&
           aClass.getInnerClasses().length == 0 &&
           aClass.getInitializers().length == 0 &&
           method.getBody() != null &&
           method.getDocComment() == null &&
           !mustKeepAnnotations(method, ignoredRuntimeAnnotations) &&
           !method.hasModifierProperty(PsiModifier.SYNCHRONIZED) &&
           !method.hasModifierProperty(PsiModifier.STRICTFP) &&
           !hasForbiddenRefsInsideBody(method, aClass);
  }

  public static boolean canBeConvertedToLambda(PsiAnonymousClass aClass,
                                               boolean acceptParameterizedFunctionTypes,
                                               boolean reportNotAnnotatedInterfaces,
                                               @NotNull Set<String> ignoredRuntimeAnnotations) {
    if (aClass.getBaseClassType().getAnnotations().length > 0) return false;
    PsiElement parent = aClass.getParent();
    final PsiElement lambdaContext = parent != null ? PsiUtil.skipParenthesizedExprUp(parent.getParent()) : null;
    if (lambdaContext == null || !LambdaUtil.isValidLambdaContext(lambdaContext) && !(lambdaContext instanceof PsiReferenceExpression)) return false;
    return isLambdaForm(aClass, acceptParameterizedFunctionTypes, reportNotAnnotatedInterfaces, ignoredRuntimeAnnotations);
  }

  public static boolean isLambdaForm(PsiAnonymousClass aClass,
                                   boolean acceptParameterizedFunctionTypes,
                                   @NotNull Set<String> ignoredRuntimeAnnotations) {
    return isLambdaForm(aClass, acceptParameterizedFunctionTypes, true, ignoredRuntimeAnnotations);
  }

  public static boolean isLambdaForm(PsiAnonymousClass aClass,
                                     boolean acceptParameterizedFunctionTypes,
                                     boolean reportNotAnnotatedInterfaces,
                                     @NotNull Set<String> ignoredRuntimeAnnotations) {
    if (PsiUtil.getLanguageLevel(aClass).isAtLeast(LanguageLevel.JDK_1_8)) {
      final PsiClassType baseClassType = aClass.getBaseClassType();
      final PsiClassType.ClassResolveResult resolveResult = baseClassType.resolveGenerics();
      final PsiClass baseClass = resolveResult.getElement();
      if (baseClass == null ||
          !reportNotAnnotatedInterfaces && !AnnotationUtil.isAnnotated(baseClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, CHECK_EXTERNAL)) {
        return false;
      }
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod != null && (acceptParameterizedFunctionTypes || !interfaceMethod.hasTypeParameters())) {
        if (isLambdaForm(aClass, ignoredRuntimeAnnotations)) {
          final PsiMethod method = aClass.getMethods()[0];
          return getInferredType(aClass, method) != null;
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
    if (!(element instanceof PsiNewExpression newExpression)) {
      return null;
    }

    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();

    if (anonymousClass == null) return null;

    final PsiMethod method;
    if (ignoreEqualsMethod) {
      final List<PsiMethod> methods = ContainerUtil.filter(anonymousClass.getMethods(), method1 -> !"equals".equals(method1.getName()));
      method = methods.get(0);
    } else {
      method = anonymousClass.getMethods()[0];
    }
    if (method == null || method.getBody() == null) return null;

    return generateLambdaByMethod(anonymousClass, method, lambda -> (PsiLambdaExpression)newExpression.replace(lambda),
                                  forceIgnoreTypeCast);
  }

  /**
   * Try to convert given method of given anonymous class into lambda and replace given element.
   *
   * @param anonymousClass      physical anonymous class containing method
   * @param method              physical method to convert with non-empty body
   * @param replacer            an operator which actually inserts a lambda into the file (possibly removing anonymous class)
   *                            and returns an inserted physical lambda
   * @param forceIgnoreTypeCast if false, type cast might be added if necessary
   * @return newly-generated lambda expression (possibly with typecast)
   */
  @NotNull
  static PsiExpression generateLambdaByMethod(PsiAnonymousClass anonymousClass,
                                              PsiMethod method,
                                              UnaryOperator<PsiLambdaExpression> replacer,
                                              boolean forceIgnoreTypeCast) {
    ChangeContextUtil.encodeContextInfo(anonymousClass, true);
    final String canonicalText = anonymousClass.getBaseClassType().getCanonicalText();

    final PsiCodeBlock body = method.getBody();
    LOG.assertTrue(body != null);

    final Collection<PsiComment> comments = collectCommentsOutsideMethodBody(anonymousClass.getParent(), body);
    final Project project = anonymousClass.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    final String withoutTypesDeclared = ReplaceWithLambdaFix.composeLambdaText(method);

    PsiLambdaExpression lambdaExpression =
      (PsiLambdaExpression)elementFactory.createExpressionFromText(withoutTypesDeclared, anonymousClass);

    PsiElement lambdaBody = lambdaExpression.getBody();
    LOG.assertTrue(lambdaBody != null);
    lambdaBody.replace(body);
    lambdaExpression = replacer.apply(lambdaExpression);

    final Set<PsiVariable> variables = new HashSet<>();
    final Set<String> usedLocalNames = new HashSet<>();

    collectLocalVariablesDefinedInsideLambda(lambdaExpression, variables, usedLocalNames);

    ReplaceWithLambdaFix
      .giveUniqueNames(project, elementFactory, lambdaExpression,
                       usedLocalNames, variables.toArray(new PsiVariable[0]));

    final PsiExpression singleExpr = RedundantLambdaCodeBlockInspection.isCodeBlockRedundant(lambdaExpression.getBody());
    if (singleExpr != null) {
      lambdaExpression.getBody().replace(singleExpr);
    }
    ChangeContextUtil.decodeContextInfo(lambdaExpression, null, null);
    restoreComments(comments, lambdaExpression);

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

  @NotNull
  static Collection<PsiComment> collectCommentsOutsideMethodBody(PsiElement anonymousClass, PsiCodeBlock body) {
    final Collection<PsiComment> psiComments = ContainerUtil.filter(PsiTreeUtil.findChildrenOfType(anonymousClass, PsiComment.class),
    comment -> !PsiTreeUtil.isAncestor(body, comment, false));
    return ContainerUtil.map(psiComments, (comment) -> (PsiComment)comment.copy());
  }

  private static void collectLocalVariablesDefinedInsideLambda(PsiLambdaExpression lambdaExpression,
                                                               final Set<PsiVariable> variables,
                                                               Set<? super String> namesOfVariablesInTheBlock) {
    PsiElement block = PsiUtil.getTopLevelEnclosingCodeBlock(lambdaExpression, null);
    if (block == null) {
      block = lambdaExpression;
    }

    block.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitVariable(@NotNull PsiVariable variable) {
        super.visitVariable(variable);
        if (!(variable instanceof PsiField)) {
          variables.add(variable);
        }
      }
    });

    final PsiResolveHelper helper = PsiResolveHelper.getInstance(lambdaExpression.getProject());
    for (Iterator<PsiVariable> iterator = variables.iterator(); iterator.hasNext(); ) {
      PsiVariable local = iterator.next();
      final String localName = local.getName();
      if (localName == null ||
          shadowingResolve(localName, lambdaExpression, helper) ||
          !PsiTreeUtil.isAncestor(lambdaExpression, local, false)) {
        iterator.remove();
        namesOfVariablesInTheBlock.add(localName);
      }
    }
  }

  private static boolean shadowingResolve(String localName, PsiLambdaExpression lambdaExpression, PsiResolveHelper helper) {
    final PsiVariable variable = helper.resolveReferencedVariable(localName, lambdaExpression);
    return variable == null || variable instanceof PsiField;
  }

  private static class ReplaceWithLambdaFix extends PsiUpdateModCommandQuickFix implements HighPriorityAction {
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("replace.with.lambda");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      replacePsiElementWithLambda(element, false, false);
    }

    private static void giveUniqueNames(Project project,
                                        final PsiElementFactory elementFactory,
                                        PsiElement body,
                                        Set<String> usedLocalNames, PsiVariable[] parameters) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final Map<PsiVariable, String> names = new HashMap<>();
      for (PsiVariable parameter : parameters) {
        String parameterName = parameter.getName();
        String uniqueVariableName = UniqueNameGenerator.generateUniqueName(codeStyleManager.suggestUniqueVariableName(parameterName, parameter.getParent(), false), usedLocalNames);
        if (!Objects.equals(parameterName, uniqueVariableName)) {
          names.put(parameter, uniqueVariableName);
        }
      }

      if (names.isEmpty()) return;

      final Map<PsiElement, PsiElement> replacements = new LinkedHashMap<>();
      body.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitVariable(@NotNull PsiVariable variable) {
          super.visitVariable(variable);
          final String newName = names.get(variable);
          if (newName != null) {
            replacements.put(variable.getNameIdentifier(), elementFactory.createIdentifier(newName));
          }
        }

        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
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
      buf.append(StringUtil.join(parameters, ReplaceWithLambdaFix::composeParameter, ","));
      if (parameters.length != 1) {
        buf.append(")");
      }
      buf.append("-> {}");
      return buf.toString();
    }

    private static String composeParameter(PsiParameter parameter) {
      return parameter.getName();
    }
  }

  public static boolean functionalInterfaceMethodReferenced(PsiMethod psiMethod,
                                                            PsiAnonymousClass anonymousClass,
                                                            PsiCallExpression callExpression) {
    if (psiMethod != null && !psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass != null &&
          CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
        return !(callExpression instanceof PsiMethodCallExpression && ((PsiMethodCallExpression)callExpression).getMethodExpression().isQualified());
      }

      if (callExpression instanceof PsiMethodCallExpression &&
          ((PsiMethodCallExpression)callExpression).getMethodExpression().isQualified()) {
        return false;
      }

      if (InheritanceUtil.isInheritorOrSelf(anonymousClass, containingClass, true) &&
          !InheritanceUtil.hasEnclosingInstanceInScope(containingClass, anonymousClass.getParent(), true, true)) {
        return true;
      }
    }
    return false;
  }

  public static void restoreComments(Collection<? extends PsiComment> comments, PsiElement lambda) {
    PsiElement anchor = PsiTreeUtil.getParentOfType(lambda, PsiStatement.class, PsiField.class);
    if (anchor == null) {
      anchor = lambda;
    }
    for (PsiComment comment : comments) {
      anchor.getParent().addBefore(comment, anchor);
    }
  }

  private static class ForbiddenRefsChecker extends JavaRecursiveElementWalkingVisitor {
    private boolean myBodyContainsForbiddenRefs;

    private final PsiMethod myMethod;
    private final PsiAnonymousClass myAnonymousClass;

    ForbiddenRefsChecker(PsiMethod method, PsiAnonymousClass aClass) {
      myMethod = method;
      myAnonymousClass = aClass;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCallExpression) {
      if (myBodyContainsForbiddenRefs) return;

      super.visitMethodCallExpression(methodCallExpression);
      final PsiMethod psiMethod = methodCallExpression.resolveMethod();
      if (psiMethod == myMethod ||
          functionalInterfaceMethodReferenced(psiMethod, myAnonymousClass, methodCallExpression)) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      if (expression.getQualifier() == null) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      if (expression.getQualifier() == null) {
        myBodyContainsForbiddenRefs = true;
      }
    }

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      if (myBodyContainsForbiddenRefs) return;

      super.visitVariable(variable);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (myBodyContainsForbiddenRefs) return;

      super.visitReferenceExpression(expression);
      if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
        final PsiMember member = PsiTreeUtil.getParentOfType(myAnonymousClass, PsiMember.class);
        if (member instanceof PsiField || member instanceof PsiClassInitializer) {
          final PsiElement resolved = expression.resolve();
          final PsiClass memberContainingClass = member.getContainingClass();
          if (resolved instanceof PsiField &&
              memberContainingClass != null &&
              PsiTreeUtil.isAncestor(((PsiField)resolved).getContainingClass(), memberContainingClass, false) &&
              expression.getQualifierExpression() == null) {
            final PsiExpression initializer = ((PsiField)resolved).getInitializer();
            if (initializer == null ||
                resolved == member ||
                initializer.getTextOffset() > myAnonymousClass.getTextOffset() && ((PsiField)resolved).hasModifierProperty(PsiModifier.STATIC) == member.hasModifierProperty(PsiModifier.STATIC)) {
              myBodyContainsForbiddenRefs = true;
            }
          }
        } else {
          final PsiMethod method = PsiTreeUtil.getParentOfType(myAnonymousClass, PsiMethod.class);
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
                  final int startOffset = flow.getStartOffset(myAnonymousClass);
                  final Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, startOffset, false);
                  if (!writtenVariables.contains(resolved)) {
                    myBodyContainsForbiddenRefs = true;
                  }
                }
              }
              catch (AnalysisCanceledException e) {
                myBodyContainsForbiddenRefs = true;
              }
            }
          }
        }
      }
    }

    public boolean hasForbiddenRefs() {
      return myBodyContainsForbiddenRefs;
    }
  }
}
