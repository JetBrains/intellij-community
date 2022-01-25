// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind.NullabilityProblem;
import com.intellij.codeInspection.dataFlow.fix.*;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaPolyadicPartAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.bugs.EqualsWithItselfInspection;
import com.siyeh.ig.fixes.EqualsToEqualityFix;
import com.siyeh.ig.numeric.ComparisonToNaNInspection;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.util.ObjectUtils.tryCast;

public abstract class DataFlowInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  static final Logger LOG = Logger.getInstance(DataFlowInspectionBase.class);
  @NonNls private static final String SHORT_NAME = "ConstantConditions";
  public boolean SUGGEST_NULLABLE_ANNOTATIONS;
  public boolean DONT_REPORT_TRUE_ASSERT_STATEMENTS;
  public boolean TREAT_UNKNOWN_MEMBERS_AS_NULLABLE;
  public boolean IGNORE_ASSERT_STATEMENTS;
  public boolean REPORT_CONSTANT_REFERENCE_VALUES = true;
  public boolean REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = true;
  public boolean REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = true;
  public boolean REPORT_UNSOUND_WARNINGS = true;

  @Override
  public JComponent createOptionsPanel() {
    throw new RuntimeException("no UI in headless mode");
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    node.addContent(new Element("option").setAttribute("name", "SUGGEST_NULLABLE_ANNOTATIONS").setAttribute("value", String.valueOf(SUGGEST_NULLABLE_ANNOTATIONS)));
    node.addContent(new Element("option").setAttribute("name", "DONT_REPORT_TRUE_ASSERT_STATEMENTS").setAttribute("value", String.valueOf(DONT_REPORT_TRUE_ASSERT_STATEMENTS)));
    if (IGNORE_ASSERT_STATEMENTS) {
      node.addContent(new Element("option").setAttribute("name", "IGNORE_ASSERT_STATEMENTS").setAttribute("value", "true"));
    }
    if (!REPORT_CONSTANT_REFERENCE_VALUES) {
      node.addContent(new Element("option").setAttribute("name", "REPORT_CONSTANT_REFERENCE_VALUES").setAttribute("value", "false"));
    }
    if (TREAT_UNKNOWN_MEMBERS_AS_NULLABLE) {
      node.addContent(new Element("option").setAttribute("name", "TREAT_UNKNOWN_MEMBERS_AS_NULLABLE").setAttribute("value", "true"));
    }
    if (!REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER) {
      node.addContent(new Element("option").setAttribute("name", "REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER").setAttribute("value", "false"));
    }
    if (!REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL) {
      node.addContent(new Element("option").setAttribute("name", "REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL").setAttribute("value", "false"));
    }
    if (!REPORT_UNSOUND_WARNINGS) {
      node.addContent(new Element("option").setAttribute("name", "REPORT_UNSOUND_WARNINGS").setAttribute("value", "false"));
    }
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        if (aClass instanceof PsiTypeParameter) return;
        if (PsiUtil.isLocalOrAnonymousClass(aClass) && !(aClass instanceof PsiEnumConstantInitializer)) return;

        var runner = new StandardDataFlowRunner(holder.getProject(), ThreeState.fromBoolean(IGNORE_ASSERT_STATEMENTS));
        DataFlowInstructionVisitor visitor =
          analyzeDfaWithNestedClosures(aClass, holder, runner, Collections.singletonList(runner.createMemoryState()));
        List<DfaMemoryState> states = visitor.getEndOfInitializerStates();
        boolean physical = aClass.isPhysical();
        for (PsiMethod method : aClass.getConstructors()) {
          if (physical && !method.isPhysical()) {
            // Constructor could be provided by, e.g. Lombok plugin: ignore it, we won't report any problems inside anyway
            continue;
          }
          List<DfaMemoryState> initialStates;
          PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
          if (JavaPsiConstructorUtil.isChainedConstructorCall(call) || (call == null && DfaUtil.hasImplicitImpureSuperCall(aClass, method))) {
            initialStates = Collections.singletonList(runner.createMemoryState());
          } else {
            initialStates = StreamEx.of(states).map(DfaMemoryState::createCopy).toList();
          }
          analyzeMethod(method, runner, initialStates);
        }
      }

      @Override
      public void visitMethod(PsiMethod method) {
        if (method.isConstructor()) return;
        var runner = new StandardDataFlowRunner(holder.getProject(), ThreeState.fromBoolean(IGNORE_ASSERT_STATEMENTS));
        analyzeMethod(method, runner, Collections.singletonList(runner.createMemoryState()));
      }

      private void analyzeMethod(PsiMethod method, StandardDataFlowRunner runner, List<DfaMemoryState> initialStates) {
        PsiCodeBlock scope = method.getBody();
        if (scope == null) return;
        PsiClass containingClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
        if (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass) && !(containingClass instanceof PsiEnumConstantInitializer)) return;

        analyzeDfaWithNestedClosures(scope, holder, runner, initialStates);
        analyzeNullLiteralMethodArguments(method, holder);
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        super.visitMethodReferenceExpression(expression);
        if (!REPORT_UNSOUND_WARNINGS) return;
        final PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          final PsiType methodReturnType = ((PsiMethod)resolve).getReturnType();
          if (TypeConversionUtil.isPrimitiveWrapper(methodReturnType) && NullableNotNullManager.isNullable((PsiMethod)resolve)) {
            final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(expression);
            if (TypeConversionUtil.isPrimitiveAndNotNull(returnType)) {
              holder.registerProblem(expression, JavaAnalysisBundle.message("dataflow.message.unboxing.method.reference"));
            }
          }
        }
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
        if (BoolUtils.isBooleanLiteral(condition)) {
          LocalQuickFix fix = createSimplifyBooleanExpressionFix(condition, condition.textMatches(PsiKeyword.TRUE));
          holder.registerProblem(condition, JavaAnalysisBundle
            .message("dataflow.message.constant.no.ref", condition.textMatches(PsiKeyword.TRUE) ? 1 : 0), fix);
        }
      }

      @Override
      public void visitDoWhileStatement(PsiDoWhileStatement statement) {
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
        if (condition != null && condition.textMatches(PsiKeyword.FALSE)) {
          holder.registerProblem(condition, JavaAnalysisBundle.message("dataflow.message.constant.no.ref", 0),
                                 createSimplifyBooleanExpressionFix(condition, false));
        }
      }
    };
  }

  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return null;
  }

  private void analyzeNullLiteralMethodArguments(PsiMethod method, ProblemsHolder holder) {
    if (REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER && holder.isOnTheFly()) {
      for (PsiParameter parameter : NullParameterConstraintChecker.checkMethodParameters(method)) {
        PsiIdentifier name = parameter.getNameIdentifier();
        if (name != null) {
          holder.registerProblem(name, JavaAnalysisBundle.message("dataflow.method.fails.with.null.argument"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createNavigateToNullParameterUsagesFix(parameter));
        }
      }
    }
  }

  private DataFlowInstructionVisitor analyzeDfaWithNestedClosures(PsiElement scope,
                                                                  ProblemsHolder holder,
                                                                  StandardDataFlowRunner dfaRunner,
                                                                  Collection<? extends DfaMemoryState> initialStates) {
    DataFlowInstructionVisitor visitor = new DataFlowInstructionVisitor(TREAT_UNKNOWN_MEMBERS_AS_NULLABLE);
    ControlFlow flow = dfaRunner.buildFlow(scope);
    if (flow == null) return visitor;
    visitor.initInstanceOf(flow.getInstructions());
    RunnerResult rc = dfaRunner.analyzeFlow(scope, visitor, initialStates, flow);
    if (rc == RunnerResult.OK) {
      if (dfaRunner.wasForciblyMerged() &&
          (ApplicationManager.getApplication().isUnitTestMode() || Registry.is("ide.dfa.report.imprecise"))) {
        reportAnalysisQualityProblem(holder, scope, "dataflow.not.precise");
      }
      createDescription(holder, visitor, scope, flow.getInstructions());
      dfaRunner.forNestedClosures((closure, states) -> analyzeDfaWithNestedClosures(closure, holder, dfaRunner, states));
    }
    else if (rc == RunnerResult.TOO_COMPLEX) {
      reportAnalysisQualityProblem(holder, scope, "dataflow.too.complex");
    }
    return visitor;
  }

  private static void reportAnalysisQualityProblem(ProblemsHolder holder, PsiElement scope, @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String problemKey) {
    PsiIdentifier name = null;
    String message = null;
    if(scope.getParent() instanceof PsiMethod) {
      name = ((PsiMethod)scope.getParent()).getNameIdentifier();
      message = JavaAnalysisBundle.message(problemKey, "Method <code>#ref</code>");
    } else if(scope instanceof PsiClass) {
      name = ((PsiClass)scope).getNameIdentifier();
      message = JavaAnalysisBundle.message(problemKey, "Class initializer");
    }
    if (name != null) { // Might be null for synthetic methods like JSP page.
      holder.registerProblem(name, message, ProblemHighlightType.WEAK_WARNING);
    }
  }

  protected @NotNull List<LocalQuickFix> createCastFixes(PsiTypeCastExpression castExpression,
                                                         PsiType realType,
                                                         boolean onTheFly,
                                                         boolean alwaysFails) {
    return Collections.emptyList();
  }

  protected @NotNull List<LocalQuickFix> createNPEFixes(@Nullable PsiExpression qualifier, PsiExpression expression, boolean onTheFly) {
    return Collections.emptyList();
  }

  protected @NotNull List<LocalQuickFix> createUnboxingNullableFixes(@NotNull PsiExpression qualifier, PsiExpression expression, boolean onTheFly) {
    return Collections.emptyList();
  }

  protected List<LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef, boolean onTheFly) {
    return Collections.emptyList();
  }

  protected @Nullable LocalQuickFix createUnwrapSwitchLabelFix() {
    return null;
  }

  protected @Nullable LocalQuickFix createIntroduceVariableFix() {
    return null;
  }

  protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment) {
    return null;
  }

  protected LocalQuickFix createReplaceWithTrivialLambdaFix(Object value) {
    return null;
  }

  private void createDescription(ProblemsHolder holder,
                                 final DataFlowInstructionVisitor visitor,
                                 PsiElement scope,
                                 Instruction @NotNull [] instructions) {
    ProblemReporter reporter = new ProblemReporter(holder, scope);

    Map<PsiExpression, ConstantResult> constantExpressions = visitor.getConstantExpressions();
    reportFailingCasts(reporter, visitor, constantExpressions);
    reportUnreachableSwitchBranches(visitor.getSwitchLabelsReachability(), holder);

    reportAlwaysFailingCalls(reporter, visitor);

    List<NullabilityProblem<?>> problems = NullabilityProblemKind.postprocessNullabilityProblems(visitor.problems().toList());
    reportNullabilityProblems(reporter, problems, constantExpressions);
    reportNullableReturns(reporter, problems, constantExpressions, scope);

    reportOptionalOfNullableImprovements(reporter, visitor.getOfNullableCalls());

    reportRedundantInstanceOf(visitor, reporter);

    reportConstants(reporter, visitor);

    reportArrayAccessProblems(holder, visitor);

    reportArrayStoreProblems(holder, visitor);

    if (REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL && visitor.isAlwaysReturnsNotNull(instructions)) {
      reportAlwaysReturnsNotNull(holder, scope);
    }

    reportMutabilityViolations(holder, visitor.getMutabilityViolations(true),
                               JavaAnalysisBundle.message("dataflow.message.immutable.modified"));
    reportMutabilityViolations(holder, visitor.getMutabilityViolations(false),
                               JavaAnalysisBundle.message("dataflow.message.immutable.passed"));

    reportDuplicateAssignments(reporter, visitor);
    reportPointlessSameArguments(reporter, visitor);
  }

  private static void reportRedundantInstanceOf(DataFlowInstructionVisitor visitor, ProblemReporter reporter) {
    visitor.redundantInstanceOfs().forEach(anchor -> {
      PsiExpression expression =
        anchor instanceof JavaExpressionAnchor ? ((JavaExpressionAnchor)anchor).getExpression() :
        anchor instanceof JavaMethodReferenceReturnAnchor ? ((JavaMethodReferenceReturnAnchor)anchor).getMethodReferenceExpression() :
        null;
      if (expression == null || shouldBeSuppressed(expression)) return;
      if (JavaPsiPatternUtil.getExposedPatternVariables(expression).stream()
        .anyMatch(var -> VariableAccessUtils.variableIsUsed(var, var.getDeclarationScope()))) {
        return;
      }
      reporter.registerProblem(expression,
                               JavaAnalysisBundle.message("dataflow.message.redundant.instanceof"),
                               new RedundantInstanceofFix(expression));
    });
  }

  private void reportUnreachableSwitchBranches(Map<PsiCaseLabelElement, ThreeState> labelReachability, ProblemsHolder holder) {
    if (labelReachability.isEmpty()) return;
    Set<PsiSwitchBlock> coveredSwitches = new HashSet<>();

    for (Map.Entry<PsiCaseLabelElement, ThreeState> entry : labelReachability.entrySet()) {
      if (entry.getValue() != ThreeState.YES) continue;
      PsiCaseLabelElement label = entry.getKey();
      PsiSwitchLabelStatementBase labelStatement = Objects.requireNonNull(PsiImplUtil.getSwitchLabel(label));
      PsiSwitchBlock switchBlock = labelStatement.getEnclosingSwitchBlock();
      if (switchBlock == null || !canRemoveUnreachableBranches(labelStatement, switchBlock)) continue;
      if (!canRemoveTheOnlyReachableLabel(label, switchBlock)) continue;
      if (!StreamEx.iterate(labelStatement, Objects::nonNull, l -> PsiTreeUtil.getPrevSiblingOfType(l, PsiSwitchLabelStatementBase.class))
        .skip(1).map(PsiSwitchLabelStatementBase::getCaseLabelElementList)
        .nonNull().flatArray(PsiCaseLabelElementList::getElements)
        .append(StreamEx.iterate(label, Objects::nonNull, l -> PsiTreeUtil.getPrevSiblingOfType(l, PsiCaseLabelElement.class)).skip(1))
        .allMatch(l -> labelReachability.get(l) == ThreeState.NO)) {
        continue;
      }
      coveredSwitches.add(switchBlock);
      LocalQuickFix unwrapFix;
      if (switchBlock instanceof PsiSwitchExpression && !CodeBlockSurrounder.canSurround(((PsiSwitchExpression)switchBlock))) {
        unwrapFix = null;
      }
      else {
        unwrapFix = createUnwrapSwitchLabelFix();
      }
      holder.registerProblem(label, JavaAnalysisBundle.message("dataflow.message.only.switch.label"), unwrapFix);
    }

    for (Map.Entry<PsiCaseLabelElement, ThreeState> entry : labelReachability.entrySet()) {
      if (entry.getValue() != ThreeState.NO) continue;
      PsiCaseLabelElement label = entry.getKey();
      PsiSwitchLabelStatementBase labelStatement = Objects.requireNonNull(PsiImplUtil.getSwitchLabel(label));
      PsiSwitchBlock switchBlock = labelStatement.getEnclosingSwitchBlock();
      if (switchBlock == null || coveredSwitches.contains(switchBlock)) continue;
      // duplicate case label is a compilation error so no need to highlight by the inspection
      Set<PsiElement> suspiciousElements = SwitchBlockHighlightingModel.findSuspiciousLabelElements(switchBlock);
      if (!suspiciousElements.contains(label)) {
        holder.registerProblem(label, JavaAnalysisBundle.message("dataflow.message.unreachable.switch.label"),
                               new DeleteSwitchLabelFix(label));
      }
    }
  }

  private static boolean canRemoveUnreachableBranches(PsiSwitchLabelStatementBase labelStatement, PsiSwitchBlock statement) {
    if (Objects.requireNonNull(labelStatement.getCaseLabelElementList()).getElementCount() != 1) return true;
    List<PsiSwitchLabelStatementBase> allBranches =
      PsiTreeUtil.getChildrenOfTypeAsList(statement.getBody(), PsiSwitchLabelStatementBase.class);
    if (statement instanceof PsiSwitchStatement) {
      // Cannot do anything if we have already single branch, and we cannot restore flow due to non-terminal breaks
      return allBranches.size() != 1 || BreakConverter.from(statement) != null;
    }
    // Expression switch: if we cannot unwrap existing branch and the other one is default case, we cannot kill it either
    return (allBranches.size() <= 2 &&
           !ContainerUtil.and(allBranches, branch -> branch == labelStatement || SwitchUtils.isDefaultLabel(branch))) ||
           (labelStatement instanceof PsiSwitchLabeledRuleStatement &&
            ((PsiSwitchLabeledRuleStatement)labelStatement).getBody() instanceof PsiExpressionStatement);
  }

  private static boolean canRemoveTheOnlyReachableLabel(@NotNull PsiCaseLabelElement label, @NotNull PsiSwitchBlock switchBlock) {
    if (!(label instanceof PsiPattern)) return true;
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return false;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return false;
    if (!JavaPsiPatternUtil.isTotalForType(((PsiPattern)label), selectorType)) return true;
    int branchCount = SwitchUtils.calculateBranchCount(switchBlock);
    // it's a compilation error if switch contains both default and total pattern, so no additional suggestion is needed
    return branchCount > 1;
  }

  private void reportConstants(ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.getConstantExpressionChunks().forEach((anchor, result) -> {
      if (result == ConstantResult.UNKNOWN) return;
      Object value = result.value();
      if (anchor instanceof JavaPolyadicPartAnchor) {
        if (value instanceof Boolean) {
          // report rare cases like a == b == c where "a == b" part is constant
          String message = JavaAnalysisBundle.message("dataflow.message.constant.condition",
                                                      ((Boolean)value).booleanValue() ? 1 : 0);
          reporter.registerProblem(((JavaPolyadicPartAnchor)anchor).getExpression(),
                                   ((JavaPolyadicPartAnchor)anchor).getTextRange(), message);
          // do not add to reported anchors if only part of expression was reported
        }
      }
      else if (anchor instanceof JavaExpressionAnchor) {
        PsiExpression expression = ((JavaExpressionAnchor)anchor).getExpression();
        if (isCondition(expression)) {
          if (value instanceof Boolean) {
            reportConstantBoolean(reporter, expression, (Boolean)value);
          }
        }
        else {
          reportConstantReferenceValue(reporter, expression, result);
        }
      }
      else if (anchor instanceof JavaMethodReferenceReturnAnchor) {
        PsiMethodReferenceExpression methodRef = ((JavaMethodReferenceReturnAnchor)anchor).getMethodReferenceExpression();
        PsiMethod method = tryCast(methodRef.resolve(), PsiMethod.class);
        if (method != null && JavaMethodContractUtil.isPure(method)) {
          List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
          if (contracts.isEmpty() || !contracts.get(0).isTrivial()) {
            reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.constant.method.reference", value),
                                     createReplaceWithTrivialLambdaFix(value));
          }
        }
      }
    });
  }

  private static boolean isCondition(@NotNull PsiExpression expression) {
    PsiType type = expression.getType();
    if (type == null || !PsiType.BOOLEAN.isAssignableFrom(type)) return false;
    if (!(expression instanceof PsiMethodCallExpression) && !(expression instanceof PsiReferenceExpression)) return true;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiStatement) return !(parent instanceof PsiReturnStatement);
    if (parent instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
      return tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR) ||
             tokenType.equals(JavaTokenType.AND) || tokenType.equals(JavaTokenType.OR);
    }
    if (parent instanceof PsiConditionalExpression) {
      return PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), expression, false);
    }
    return PsiUtil.isAccessedForWriting(expression);
  }

  private void reportConstantReferenceValue(ProblemReporter reporter, PsiExpression ref, ConstantResult constant) {
    if (!REPORT_CONSTANT_REFERENCE_VALUES && ref instanceof PsiReferenceExpression) return;
    if (shouldBeSuppressed(ref) || constant == ConstantResult.UNKNOWN) return;
    List<LocalQuickFix> fixes = new SmartList<>();
    String presentableName = constant.toString();
    if (Integer.valueOf(0).equals(constant.value()) && !shouldReportZero(ref)) return;
    if (constant.value() instanceof Boolean) {
      fixes.add(createSimplifyBooleanExpressionFix(ref, (Boolean)constant.value()));
    } else {
      fixes.add(new ReplaceWithConstantValueFix(presentableName, presentableName));
    }
    Object value = constant.value();
    boolean isAssertion = isAssertionEffectively(ref, constant);
    if (isAssertion && DONT_REPORT_TRUE_ASSERT_STATEMENTS) return;
    if (value instanceof Boolean) {
      ContainerUtil.addIfNotNull(fixes, createReplaceWithNullCheckFix(ref, (Boolean)value));
    }
    if (reporter.isOnTheFly()) {
      if (ref instanceof PsiReferenceExpression) {
        fixes.add(new SetInspectionOptionFix(this, "REPORT_CONSTANT_REFERENCE_VALUES",
                                             JavaAnalysisBundle.message("inspection.data.flow.turn.off.constant.references.quickfix"),
                                             false));
      }
      if (isAssertion) {
        fixes.add(new SetInspectionOptionFix(this, "DONT_REPORT_TRUE_ASSERT_STATEMENTS",
                                             JavaAnalysisBundle.message("inspection.data.flow.turn.off.true.asserts.quickfix"), true));
      }
    }
    ContainerUtil.addIfNotNull(fixes, createExplainFix(ref, new TrackingRunner.ValueDfaProblemType(value)));

    ProblemHighlightType type;
    String message;
    if (ref instanceof PsiMethodCallExpression || ref instanceof PsiPolyadicExpression || ref instanceof PsiTypeCastExpression) {
      type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      message = JavaAnalysisBundle.message("dataflow.message.constant.expression", presentableName);
    }
    else {
      type = ProblemHighlightType.WEAK_WARNING;
      message = JavaAnalysisBundle.message("dataflow.message.constant.value", presentableName);
    }
    reporter.registerProblem(ref, message, type, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private static boolean shouldReportZero(PsiExpression ref) {
    if (ref instanceof PsiPolyadicExpression) {
      if (PsiUtil.isConstantExpression(ref)) return false;
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)ref;
      IElementType tokenType = polyadic.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISK)) {
        PsiMethod method = PsiTreeUtil.getParentOfType(ref, PsiMethod.class, true, PsiLambdaExpression.class, PsiClass.class);
        if (MethodUtils.isHashCode(method)) {
          // Standard hashCode template generates int result = 0; result = result * 31 + ...;
          // so annoying warnings might be produced there
          return false;
        }
      }
    }
    else if (ref instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)ref;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (PsiUtil.isConstantExpression(qualifier) &&
          ContainerUtil.and(call.getArgumentList().getExpressions(), PsiUtil::isConstantExpression)) {
        return false;
      }
    }
    else if (ref instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)ref).getOperand();
      return operand != null && TypeConversionUtil.isFloatOrDoubleType(operand.getType());
    }
    else {
      return false;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
    PsiBinaryExpression binOp = tryCast(parent, PsiBinaryExpression.class);
    if (binOp != null && ComparisonUtils.isEqualityComparison(binOp) &&
        (ExpressionUtils.isZero(binOp.getLOperand()) || ExpressionUtils.isZero(binOp.getROperand()))) {
      return false;
    }
    return true;
  }

  private static void reportPointlessSameArguments(ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.pointlessSameArguments().forKeyValue((expr, eq) -> {
      PsiElement name = expr.getReferenceNameElement();
      if (name != null) {
        PsiExpression[] expressions = PsiExpression.EMPTY_ARRAY;
        if (expr.getParent() instanceof PsiMethodCallExpression) {
          expressions = ((PsiMethodCallExpression)expr.getParent()).getArgumentList().getExpressions();
          if (expressions.length == 2 && PsiUtil.isConstantExpression(expressions[0]) && PsiUtil.isConstantExpression(expressions[1]) &&
              !EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expressions[0], expressions[1])) {
            return;
          }
        }
        if (eq.firstArgEqualToResult) {
          String message = eq.argsEqual ? JavaAnalysisBundle.message("dataflow.message.pointless.same.arguments") :
                           JavaAnalysisBundle.message("dataflow.message.pointless.same.argument.and.result", 1);
          LocalQuickFix fix = expressions.length == 2 ? new ReplaceWithArgumentFix(expressions[0], 0) : null;
          reporter.registerProblem(name, message, fix);
        }
        else if (eq.argsEqual) {
          reporter.registerProblem(name, JavaAnalysisBundle.message("dataflow.message.pointless.same.arguments"));
        }
        else if (eq.secondArgEqualToResult) {
          LocalQuickFix fix = expressions.length == 2 ? new ReplaceWithArgumentFix(expressions[1], 1) : null;
          reporter.registerProblem(name, JavaAnalysisBundle.message("dataflow.message.pointless.same.argument.and.result", 2), fix);
        }
      }
    });
  }

  private void reportDuplicateAssignments(ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.sameValueAssignments().forEach(expr -> {
      expr = PsiUtil.skipParenthesizedExprDown(expr);
      if (expr == null) return;
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(expr, PsiAssignmentExpression.class);
      PsiElement context = PsiTreeUtil.getParentOfType(expr, PsiForStatement.class, PsiClassInitializer.class);
      if (context instanceof PsiForStatement && PsiTreeUtil.isAncestor(((PsiForStatement)context).getInitialization(), expr, true)) {
        return;
      }
      if (expr instanceof PsiReferenceExpression) {
        PsiReferenceExpression ref = (PsiReferenceExpression)expr;
        PsiField field = tryCast(ref.resolve(), PsiField.class);
        if (field != null) {
          // Final field assignment: even if redundant according to DFA model (e.g. this.field = null),
          // it's necessary due to language semantics
          if (field.hasModifierProperty(PsiModifier.FINAL)) return;
          if (context instanceof PsiClassInitializer) {
            if (assignment != null) {
              Object constValue = ExpressionUtils.computeConstantExpression(assignment.getRExpression());
              if (constValue == PsiTypesUtil.getDefaultValue(expr.getType())) {
                if ((field.hasModifierProperty(PsiModifier.STATIC) || ExpressionUtil.isEffectivelyUnqualified(ref)) &&
                    field.getContainingClass() == ((PsiClassInitializer)context).getContainingClass()) {
                  return;
                }
              }
            }
          }
        }
      }
      String message = assignment != null && !assignment.getOperationTokenType().equals(JavaTokenType.EQ)
                       ? JavaAnalysisBundle.message("dataflow.message.redundant.update")
                       : JavaAnalysisBundle.message("dataflow.message.redundant.assignment");
      reporter.registerProblem(expr, message, createRemoveAssignmentFix(assignment));
    });
  }

  private void reportMutabilityViolations(ProblemsHolder holder, Set<PsiElement> violations, @InspectionMessage String message) {
    for (PsiElement violation : violations) {
      holder.registerProblem(violation, message, createMutabilityViolationFix(violation, holder.isOnTheFly()));
    }
  }

  protected LocalQuickFix createMutabilityViolationFix(PsiElement violation, boolean onTheFly) {
    return null;
  }

  protected void reportNullabilityProblems(ProblemReporter reporter,
                                           List<NullabilityProblem<?>> problems,
                                           Map<PsiExpression, ConstantResult> expressions) {
    for (NullabilityProblem<?> problem : problems) {
      PsiExpression expression = problem.getDereferencedExpression();
      boolean nullLiteral = ExpressionUtils.isNullLiteral(PsiUtil.skipParenthesizedExprDown(expression));
      if (!REPORT_UNSOUND_WARNINGS) {
        if (expression == null || !nullLiteral && expressions.get(expression) != ConstantResult.NULL) continue;
      }
      // Expression of null type: could be failed LVTI, skip it to avoid confusion
      if (expression != null && !nullLiteral && PsiType.NULL.equals(expression.getType())) continue;
      NullabilityProblemKind.innerClassNPE.ifMyProblem(problem, newExpression -> {
        List<LocalQuickFix> fixes = createNPEFixes(newExpression.getQualifier(), newExpression, reporter.isOnTheFly());
        reporter
          .registerProblem(getElementToHighlight(newExpression), problem.getMessage(expressions), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      });
      NullabilityProblemKind.callMethodRefNPE.ifMyProblem(problem, methodRef ->
        reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.npe.methodref.invocation"),
                                 createMethodReferenceNPEFixes(methodRef, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY)));
      NullabilityProblemKind.callNPE.ifMyProblem(problem, call -> reportCallMayProduceNpe(reporter, problem.getMessage(expressions), call));
      NullabilityProblemKind.passingToNotNullParameter.ifMyProblem(problem, expr -> {
        List<LocalQuickFix> fixes = createNPEFixes(expression, expression, reporter.isOnTheFly());
        reporter.registerProblem(expression, problem.getMessage(expressions), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      });
      NullabilityProblemKind.passingToNotNullMethodRefParameter.ifMyProblem(problem, methodRef -> {
        LocalQuickFix[] fixes = createMethodReferenceNPEFixes(methodRef, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.passing.nullable.argument.methodref"), fixes);
      });
      NullabilityProblemKind.unboxingMethodRefParameter.ifMyProblem(problem, methodRef -> {
        LocalQuickFix[] fixes = createMethodReferenceNPEFixes(methodRef, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.unboxing.nullable.argument.methodref"), fixes);
      });
      NullabilityProblemKind.arrayAccessNPE.ifMyProblem(problem, arrayAccess -> {
        LocalQuickFix[] fixes =
          createNPEFixes(arrayAccess.getArrayExpression(), arrayAccess, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(arrayAccess, problem.getMessage(expressions), fixes);
      });
      NullabilityProblemKind.fieldAccessNPE.ifMyProblem(problem, element -> {
        PsiElement parent = element.getParent();
        PsiExpression fieldAccess = parent instanceof PsiReferenceExpression ? (PsiExpression)parent : element;
        LocalQuickFix[] fix = createNPEFixes(element, fieldAccess, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(element, problem.getMessage(expressions), fix);
      });
      NullabilityProblemKind.unboxingNullable.ifMyProblem(problem, element -> {
        PsiExpression anchor = expression;
        if (anchor instanceof PsiTypeCastExpression && anchor.getType() instanceof PsiPrimitiveType) {
          anchor = Objects.requireNonNull(((PsiTypeCastExpression)anchor).getOperand());
        }
        if (anchor != null) {
          LocalQuickFix[] fixes = createUnboxingNullableFixes(anchor, expression, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
          reporter.registerProblem(anchor, problem.getMessage(expressions), fixes);
        }
      });
      NullabilityProblemKind.nullableFunctionReturn.ifMyProblem(
        problem, expr -> reporter.registerProblem(expression == null ? expr : expression, problem.getMessage(expressions)));
      Consumer<PsiExpression> reportNullability = expr -> reportNullabilityProblem(reporter, problem, expression, expressions);
      NullabilityProblemKind.assigningToNotNull.ifMyProblem(problem, reportNullability);
      NullabilityProblemKind.storingToNotNullArray.ifMyProblem(problem, reportNullability);
      if (SUGGEST_NULLABLE_ANNOTATIONS) {
        NullabilityProblemKind.passingToNonAnnotatedMethodRefParameter.ifMyProblem(
          problem, methodRef -> reporter.registerProblem(methodRef, problem.getMessage(expressions)));
        NullabilityProblemKind.passingToNonAnnotatedParameter.ifMyProblem(
          problem, top -> reportNullableArgumentsPassedToNonAnnotated(reporter, problem.getMessage(expressions), expression, top));
        NullabilityProblemKind.assigningToNonAnnotatedField.ifMyProblem(
          problem, top -> reportNullableAssignedToNonAnnotatedField(reporter, top, expression, problem.getMessage(expressions)));
      }
    }
  }

  private void reportNullabilityProblem(ProblemReporter reporter,
                                        NullabilityProblem<?> problem,
                                        PsiExpression expr,
                                        Map<PsiExpression, ConstantResult> expressions) {
    LocalQuickFix[] fixes = createNPEFixes(expr, expr, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
    reporter.registerProblem(expr, problem.getMessage(expressions), fixes);
  }

  private static void reportArrayAccessProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.outOfBoundsArrayAccesses().forEach(access -> {
      PsiExpression indexExpression = access.getIndexExpression();
      if (indexExpression != null) {
        holder.registerProblem(indexExpression, JavaAnalysisBundle.message("dataflow.message.array.index.out.of.bounds"));
      }
    });
    visitor.negativeArraySizes().forEach(dimExpression -> {
      holder.registerProblem(dimExpression, JavaAnalysisBundle.message("dataflow.message.negative.array.size"));
    });
  }

  private static void reportArrayStoreProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.getArrayStoreProblems().forEach(
      (assignment, types) -> holder.registerProblem(assignment.getOperationSign(), JavaAnalysisBundle
        .message("dataflow.message.arraystore", types.getFirst().getCanonicalText(), types.getSecond().getCanonicalText())));
  }

  private void reportAlwaysReturnsNotNull(ProblemsHolder holder, PsiElement scope) {
    if (!(scope.getParent() instanceof PsiMethod)) return;

    PsiMethod method = (PsiMethod)scope.getParent();
    if (PsiUtil.canBeOverridden(method)) return;

    NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(scope.getProject()).findOwnNullabilityInfo(method);
    if (info == null || info.getNullability() != Nullability.NULLABLE) return;

    PsiAnnotation annotation = info.getAnnotation();
    if (!annotation.isPhysical() || alsoAppliesToInternalSubType(annotation, method)) return;

    PsiJavaCodeReferenceElement annoName = annotation.getNameReferenceElement();
    assert annoName != null;
    String msg = JavaAnalysisBundle
      .message("dataflow.message.return.notnull.from.nullable", NullableStuffInspectionBase.getPresentableAnnoName(annotation),
               method.getName());
    LocalQuickFix[] fixes = {AddAnnotationPsiFix.createAddNotNullFix(method),
      new SetInspectionOptionFix(this, "REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL",
                                 JavaAnalysisBundle
                                   .message(
                                     "inspection.data.flow.turn.off.nullable.returning.notnull.quickfix"),
                                 false)};
    holder.registerProblem(annoName, msg, fixes);
  }

  private static boolean alsoAppliesToInternalSubType(PsiAnnotation annotation, PsiMethod method) {
    return AnnotationTargetUtil.isTypeAnnotation(annotation) && method.getReturnType() instanceof PsiArrayType;
  }

  private void reportAlwaysFailingCalls(ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.alwaysFailingCalls().remove(TestUtils::isExceptionExpected).forEach(call -> {
      String message = getContractMessage(JavaMethodContractUtil.getMethodCallContracts(call));
      LocalQuickFix causeFix = createExplainFix(call, new TrackingRunner.FailingCallDfaProblemType());
      reporter.registerProblem(getElementToHighlight(call), message, causeFix);
    });
  }

  private static @NotNull @InspectionMessage String getContractMessage(List<? extends MethodContract> contracts) {
    if (ContainerUtil.and(contracts, mc -> ContainerUtil.and(mc.getConditions(), ContractValue::isBoundCheckingCondition))) {
      return JavaAnalysisBundle.message("dataflow.message.contract.fail.index");
    }
    return JavaAnalysisBundle.message("dataflow.message.contract.fail");
  }

  private static @NotNull PsiElement getElementToHighlight(@NotNull PsiCall call) {
    PsiJavaCodeReferenceElement ref;
    if (call instanceof PsiNewExpression) {
      ref = ((PsiNewExpression)call).getClassReference();
    }
    else if (call instanceof PsiMethodCallExpression) {
      ref = ((PsiMethodCallExpression)call).getMethodExpression();
    }
    else {
      return call;
    }
    if (ref != null) {
      PsiElement name = ref.getReferenceNameElement();
      return name != null ? name : ref;
    }
    return call;
  }

  private static void reportOptionalOfNullableImprovements(ProblemReporter reporter, Map<PsiElement, ThreeState> nullArgs) {
    nullArgs.forEach((anchor, alwaysPresent) -> {
      if (alwaysPresent == ThreeState.UNSURE) return;
      if (alwaysPresent.toBoolean()) {
        reporter.registerProblem(anchor, JavaAnalysisBundle.message("dataflow.message.passing.non.null.argument.to.optional"),
                                 DfaOptionalSupport.createReplaceOptionalOfNullableWithOfFix(anchor));
      }
      else {
        reporter.registerProblem(anchor, JavaAnalysisBundle.message("dataflow.message.passing.null.argument.to.optional"),
                                 DfaOptionalSupport.createReplaceOptionalOfNullableWithEmptyFix(anchor));
      }
    });
  }

  private void reportNullableArgumentsPassedToNonAnnotated(ProblemReporter reporter,
                                                           @InspectionMessage String message,
                                                           PsiExpression expression,
                                                           PsiExpression top) {
    PsiParameter parameter = MethodCallUtils.getParameterForArgument(top);
    if (parameter != null && BaseIntentionAction.canModify(parameter) && AnnotationUtil.isAnnotatingApplicable(parameter)) {
      List<LocalQuickFix> fixes = createNPEFixes(expression, top, reporter.isOnTheFly());
      fixes.add(AddAnnotationPsiFix.createAddNullableFix(parameter));
      reporter.registerProblem(expression, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private void reportNullableAssignedToNonAnnotatedField(ProblemReporter reporter,
                                                         PsiExpression top,
                                                         PsiExpression expression,
                                                         @InspectionMessage String message) {
    PsiField field = getAssignedField(top);
    if (field != null) {
      List<LocalQuickFix> fixes = createNPEFixes(expression, top, reporter.isOnTheFly());
      fixes.add(AddAnnotationPsiFix.createAddNullableFix(field));
      reporter.registerProblem(expression, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private static @Nullable PsiField getAssignedField(PsiElement assignedValue) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(assignedValue.getParent());
    if (parent instanceof PsiAssignmentExpression) {
      PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      PsiElement target = lExpression instanceof PsiReferenceExpression ? ((PsiReferenceExpression)lExpression).resolve() : null;
      return tryCast(target, PsiField.class);
    }
    return null;
  }

  private void reportCallMayProduceNpe(ProblemReporter reporter, @InspectionMessage String message, PsiMethodCallExpression callExpression) {
    PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    List<LocalQuickFix> fixes = createNPEFixes(methodExpression.getQualifierExpression(), callExpression, reporter.isOnTheFly());
    ContainerUtil.addIfNotNull(fixes, ReplaceWithObjectsEqualsFix.createFix(callExpression, methodExpression));

    PsiElement toHighlight = getElementToHighlight(callExpression);
    reporter.registerProblem(toHighlight, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private void reportFailingCasts(@NotNull ProblemReporter reporter,
                                  @NotNull DataFlowInstructionVisitor visitor,
                                  @NotNull Map<PsiExpression, ConstantResult> constantExpressions) {
    visitor.getFailingCastExpressions().forKeyValue((typeCast, info) -> {
      boolean alwaysFails = info.getFirst();
      PsiType realType = info.getSecond();
      if (!REPORT_UNSOUND_WARNINGS && !alwaysFails) return;
      PsiExpression operand = typeCast.getOperand();
      PsiTypeElement castType = typeCast.getCastType();
      ConstantResult result = constantExpressions.get(PsiUtil.skipParenthesizedExprDown(operand));
      // Skip reporting if cast operand is always null: null can be cast to anything
      if (result == ConstantResult.NULL || ExpressionUtils.isNullLiteral(operand)) return;
      assert castType != null;
      assert operand != null;
      List<LocalQuickFix> fixes = new ArrayList<>(createCastFixes(typeCast, realType, reporter.isOnTheFly(), alwaysFails));
      fixes.add(createExplainFix(typeCast, new TrackingRunner.CastDfaProblemType()));
      String text = PsiExpressionTrimRenderer.render(operand);
      String message = alwaysFails ?
                       JavaAnalysisBundle.message("dataflow.message.cce.always", text) :
                       JavaAnalysisBundle.message("dataflow.message.cce", text);
      reporter.registerProblem(castType, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    });
  }

  private void reportConstantBoolean(ProblemReporter reporter, PsiElement psiAnchor, boolean evaluatesToTrue) {
    while (psiAnchor instanceof PsiParenthesizedExpression) {
      psiAnchor = ((PsiParenthesizedExpression)psiAnchor).getExpression();
    }
    if (psiAnchor == null || shouldBeSuppressed(psiAnchor)) return;
    boolean isAssertion = isAssertionEffectively(psiAnchor, evaluatesToTrue);
    if (DONT_REPORT_TRUE_ASSERT_STATEMENTS && isAssertion) return;

    PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiAnchor.getParent());
    if (parent instanceof PsiAssignmentExpression &&
        PsiTreeUtil.isAncestor(((PsiAssignmentExpression)parent).getLExpression(), psiAnchor, false)) {
      reporter.registerProblem(
        psiAnchor,
        JavaAnalysisBundle.message("dataflow.message.pointless.assignment.expression", Boolean.toString(evaluatesToTrue)),
        createConditionalAssignmentFixes(evaluatesToTrue, (PsiAssignmentExpression)parent, reporter.isOnTheFly())
      );
      return;
    }

    List<LocalQuickFix> fixes = new ArrayList<>();
    if (!isCoveredBySurroundingFix(psiAnchor, evaluatesToTrue)) {
      ContainerUtil.addIfNotNull(fixes, createSimplifyBooleanExpressionFix(psiAnchor, evaluatesToTrue));
      if (isAssertion && reporter.isOnTheFly()) {
        fixes.add(new SetInspectionOptionFix(this, "DONT_REPORT_TRUE_ASSERT_STATEMENTS",
                                             JavaAnalysisBundle.message("inspection.data.flow.turn.off.true.asserts.quickfix"), true));
      }
      ContainerUtil.addIfNotNull(fixes, createReplaceWithNullCheckFix(psiAnchor, evaluatesToTrue));
    }
    if (psiAnchor instanceof PsiExpression) {
      ContainerUtil.addIfNotNull(fixes, createExplainFix(
        (PsiExpression)psiAnchor, new TrackingRunner.ValueDfaProblemType(evaluatesToTrue)));
    }
    String message = JavaAnalysisBundle.message(isAtRHSOfBooleanAnd(psiAnchor) ?
                                               "dataflow.message.constant.condition.when.reached" :
                                               "dataflow.message.constant.condition", evaluatesToTrue ? 1 : 0);
    reporter.registerProblem(psiAnchor, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  protected @Nullable LocalQuickFix createExplainFix(PsiExpression anchor, TrackingRunner.DfaProblemType problemType) {
    return null;
  }

  private static boolean isCoveredBySurroundingFix(PsiElement anchor, boolean evaluatesToTrue) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
      return tokenType.equals(JavaTokenType.ANDAND) && !evaluatesToTrue ||
             tokenType.equals(JavaTokenType.OROR) && evaluatesToTrue;
    }
    return parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
  }

  @Contract("null -> false")
  private static boolean shouldBeSuppressed(PsiElement anchor) {
    if (!(anchor instanceof PsiExpression)) return false;
    // Don't report System.out.println(b = false) or doSomething((Type)null)
    if (anchor instanceof PsiAssignmentExpression ||
        (anchor instanceof PsiTypeCastExpression && !(((PsiTypeCastExpression)anchor).getType() instanceof PsiPrimitiveType))) {
      return true;
    }
    // For conditional the root cause (constant condition or both branches constant) should be already reported for branches
    if (anchor instanceof PsiConditionalExpression) return true;
    PsiExpression expression = (PsiExpression)anchor;
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)expression;
      if ("TRUE".equals(ref.getReferenceName()) || "FALSE".equals(ref.getReferenceName())) {
        PsiElement target = ref.resolve();
        if (target instanceof PsiField) {
          PsiClass containingClass = ((PsiField)target).getContainingClass();
          if (containingClass != null && CommonClassNames.JAVA_LANG_BOOLEAN.equals(containingClass.getQualifiedName())) return true;
        }
      }
    }
    if (expression instanceof PsiBinaryExpression) {
      PsiExpression lOperand = ((PsiBinaryExpression)expression).getLOperand();
      PsiExpression rOperand = ((PsiBinaryExpression)expression).getROperand();
      IElementType tokenType = ((PsiBinaryExpression)expression).getOperationTokenType();
      // Suppress on type mismatch compilation errors
      if (rOperand == null) return true;
      PsiType lType = lOperand.getType();
      PsiType rType = rOperand.getType();
      if (lType == null || rType == null) return true;
      if (!TypeConversionUtil.isBinaryOperatorApplicable(tokenType, lType, rType, false)) return true;
    }
    if (expression instanceof PsiInstanceOfExpression) {
      PsiType type = ((PsiInstanceOfExpression)expression).getOperand().getType();
      if (type == null || !TypeConstraints.instanceOf(type).isResolved()) return true;
      PsiPattern pattern = ((PsiInstanceOfExpression)expression).getPattern();
      if (pattern instanceof PsiTypeTestPattern && ((PsiTypeTestPattern)pattern).getPatternVariable() != null) {
        PsiTypeElement checkType = ((PsiTypeTestPattern)pattern).getCheckType();
        if (checkType != null && checkType.getType().isAssignableFrom(type)) {
          // Reported as compilation error
          return true;
        }
      }
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    // Don't report "x" in "x == null" as will be anyways reported as "always true"
    if (parent instanceof PsiBinaryExpression && ExpressionUtils.getValueComparedWithNull((PsiBinaryExpression)parent) != null) return true;
    // Dereference of null will be covered by other warning
    if (ExpressionUtils.isVoidContext(expression) || isDereferenceContext(expression)) return true;
    // We assume all Void variables as null because you cannot instantiate it without dirty hacks
    // However reporting them as "always null" looks redundant (dereferences or comparisons will be reported though).
    if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, expression.getType())) return true;
    if (isFlagCheck(anchor)) return true;
    if (!isCondition(expression) && expression instanceof PsiMethodCallExpression) {
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts((PsiCallExpression)expression);
      ContractReturnValue value = JavaMethodContractUtil.getNonFailingReturnValue(contracts);
      if (value != null) return true;
      if (!(parent instanceof PsiAssignmentExpression) && !(parent instanceof PsiVariable) &&
          !(parent instanceof PsiReturnStatement)) {
        PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
        if (method == null || !JavaMethodContractUtil.isPure(method)) return true;
      }
    }
    while (expression != null && BoolUtils.isNegation(expression)) {
      expression = BoolUtils.getNegated(expression);
    }
    if (expression == null) return false;
    if (!isCondition(expression) && expression instanceof PsiReferenceExpression) {
      PsiVariable variable = tryCast(((PsiReferenceExpression)expression).resolve(), PsiVariable.class);
      if (variable instanceof PsiField &&
          variable.hasModifierProperty(PsiModifier.STATIC) &&
          ExpressionUtils.isNullLiteral(PsiFieldImpl.getDetachedInitializer(variable))) {
        return true;
      }
      if (variable instanceof PsiLocalVariable && variable.hasInitializer()) {
        boolean effectivelyFinal = variable.hasModifierProperty(PsiModifier.FINAL) ||
                    !VariableAccessUtils.variableIsAssigned(variable, PsiUtil.getVariableCodeBlock(variable, null));
        return effectivelyFinal && PsiUtil.isConstantExpression(variable.getInitializer());
      }
      return false;
    }
    // Avoid double reporting
    return expression instanceof PsiMethodCallExpression && EqualsWithItselfInspection.isEqualsWithItself((PsiMethodCallExpression)expression) ||
           expression instanceof PsiBinaryExpression && ComparisonToNaNInspection.extractNaNFromComparison((PsiBinaryExpression)expression) != null;
  }

  private static boolean isDereferenceContext(PsiExpression ref) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
    return parent instanceof PsiReferenceExpression || parent instanceof PsiArrayAccessExpression
           || parent instanceof PsiSwitchStatement || parent instanceof PsiSynchronizedStatement;
  }

  private static LocalQuickFix createReplaceWithNullCheckFix(PsiElement psiAnchor, boolean evaluatesToTrue) {
    if (evaluatesToTrue) return null;
    if (!(psiAnchor instanceof PsiMethodCallExpression)) return null;
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)psiAnchor;
    if (!MethodCallUtils.isEqualsCall(methodCallExpression)) return null;
    PsiExpression arg = ArrayUtil.getFirstElement(methodCallExpression.getArgumentList().getExpressions());
    if (!ExpressionUtils.isNullLiteral(arg)) return null;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiAnchor.getParent());
    return EqualsToEqualityFix.buildFix(methodCallExpression, parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent));
  }

  protected LocalQuickFix[] createConditionalAssignmentFixes(boolean evaluatesToTrue, PsiAssignmentExpression parent, final boolean onTheFly) {
    return LocalQuickFix.EMPTY_ARRAY;
  }

  private static @Nullable PsiMethod getScopeMethod(PsiElement block) {
    PsiElement parent = block.getParent();
    if (parent instanceof PsiMethod) return (PsiMethod)parent;
    if (parent instanceof PsiLambdaExpression) return LambdaUtil.getFunctionalInterfaceMethod(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
    return null;
  }

  private void reportNullableReturns(ProblemReporter reporter,
                                     List<NullabilityProblem<?>> problems,
                                     Map<PsiExpression, ConstantResult> expressions,
                                     @NotNull PsiElement block) {
    final PsiMethod method = getScopeMethod(block);
    if (method == null) return;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(method);
    if (info == null) info = DfaPsiUtil.getTypeNullabilityInfo(PsiTypesUtil.getMethodReturnType(block));
    PsiAnnotation anno = info == null ? null : info.getAnnotation();
    Nullability nullability = info == null ? Nullability.UNKNOWN : info.getNullability();
    if (nullability == Nullability.NULLABLE) {
      if (!info.isInferred() || DfaPsiUtil.getTypeNullability(method.getReturnType()) == Nullability.NULLABLE) return;
    }
    // In rare cases, inference may produce different result (e.g. if nullable method overrides non-null method)
    if (nullability == Nullability.NOT_NULL && info.isInferred()) return;

    if (nullability != Nullability.NOT_NULL && (!SUGGEST_NULLABLE_ANNOTATIONS || block.getParent() instanceof PsiLambdaExpression)) return;

    PsiType returnType = method.getReturnType();
    // no warnings in void lambdas, where the expression is not returned anyway
    if (block instanceof PsiExpression && block.getParent() instanceof PsiLambdaExpression && PsiType.VOID.equals(returnType)) return;

    // no warnings for Void methods, where only null can be possibly returned
    if (returnType == null || returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID)) return;

    for (NullabilityProblem<PsiExpression> problem : StreamEx.of(problems).map(NullabilityProblemKind.nullableReturn::asMyProblem).nonNull()) {
      final PsiExpression anchor = problem.getAnchor();
      PsiExpression expr = problem.getDereferencedExpression();

      boolean exactlyNull = isNullLiteralExpression(expr) || expressions.get(expr) == ConstantResult.NULL;
      if (!REPORT_UNSOUND_WARNINGS && !exactlyNull) continue;
      if (nullability == Nullability.NOT_NULL) {
        String presentable = NullableStuffInspectionBase.getPresentableAnnoName(anno);
        final String text = exactlyNull
                            ? JavaAnalysisBundle.message("dataflow.message.return.null.from.notnull", presentable)
                            : JavaAnalysisBundle.message("dataflow.message.return.nullable.from.notnull", presentable);
        reporter.registerProblem(expr, text, createNPEFixes(expr, expr, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY));
      }
      else if (AnnotationUtil.isAnnotatingApplicable(anchor)) {
        final String defaultNullable = manager.getDefaultNullable();
        final String presentableNullable = StringUtil.getShortName(defaultNullable);
        final String text = exactlyNull
                            ? JavaAnalysisBundle.message("dataflow.message.return.null.from.notnullable", presentableNullable)
                            : JavaAnalysisBundle.message("dataflow.message.return.nullable.from.notnullable", presentableNullable);
        PsiMethod surroundingMethod = PsiTreeUtil.getParentOfType(anchor, PsiMethod.class, true, PsiLambdaExpression.class);
        final LocalQuickFix fix = surroundingMethod == null ? null :
                                  new AddAnnotationPsiFix(defaultNullable, surroundingMethod,
                                                          ArrayUtilRt.toStringArray(manager.getNotNulls()));
        reporter.registerProblem(expr, text, fix);
      }
    }
  }

  private static boolean isAssertionEffectively(@NotNull PsiElement anchor, ConstantResult result) {
    Object value = result.value();
    if (value instanceof Boolean) {
      return isAssertionEffectively(anchor, (Boolean)value);
    }
    if (value != null) return false;
    return isAssertCallArgument(anchor, ContractValue.nullValue());
  }

  private static boolean isAssertionEffectively(@NotNull PsiElement anchor, boolean evaluatesToTrue) {
    PsiElement parent;
    while (true) {
      parent = anchor.getParent();
      if (parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent)) {
        evaluatesToTrue = !evaluatesToTrue;
        anchor = parent;
        continue;
      }
      if (parent instanceof PsiParenthesizedExpression) {
        anchor = parent;
        continue;
      }
      if (parent instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
          // always true operand makes always true OR-chain and does not affect the result of AND-chain
          // Note that in `assert unknownExpression && trueExpression;` the trueExpression should not be reported
          // because this assertion is essentially the shortened `assert unknownExpression; assert trueExpression;`
          // which is not reported.
          boolean causesShortCircuit = (tokenType.equals(JavaTokenType.OROR) == evaluatesToTrue) &&
                                       ArrayUtil.getLastElement(((PsiPolyadicExpression)parent).getOperands()) != anchor;
          if (!causesShortCircuit) {
            // We still report `assert trueExpression || unknownExpression`, because here `unknownExpression` is never checked
            // which is probably not intended.
            anchor = parent;
            continue;
          }
        }
      }
      break;
    }
    if (parent instanceof PsiAssertStatement) {
      return evaluatesToTrue;
    }
    if (parent instanceof PsiIfStatement && anchor == ((PsiIfStatement)parent).getCondition()) {
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(((PsiIfStatement)parent).getThenBranch());
      if (thenBranch instanceof PsiThrowStatement) {
        return !evaluatesToTrue;
      }
    }
    return isAssertCallArgument(anchor, ContractValue.booleanValue(evaluatesToTrue));
  }

  private static boolean isAssertCallArgument(@NotNull PsiElement anchor, @NotNull ContractValue wantedConstraint) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiExpressionList) {
      int index = ArrayUtil.indexOf(((PsiExpressionList)parent).getExpressions(), anchor);
      if (index >= 0) {
        PsiMethodCallExpression call = tryCast(parent.getParent(), PsiMethodCallExpression.class);
        if (call != null) {
          MethodContract contract = ContainerUtil.getOnlyItem(JavaMethodContractUtil.getMethodCallContracts(call));
          if (contract != null && contract.getReturnValue().isFail()) {
            ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
            if (condition != null) {
              return condition.getArgumentComparedTo(wantedConstraint, false).orElse(-1) == index;
            }
          }
        }
      }
    }
    return false;
  }

  private static boolean isAtRHSOfBooleanAnd(PsiElement expr) {
    PsiElement cur = expr;

    while (cur != null && !(cur instanceof PsiMember)) {
      PsiElement parent = cur.getParent();

      if (parent instanceof PsiBinaryExpression && cur == ((PsiBinaryExpression)parent).getROperand()) {
        return true;
      }

      cur = parent;
    }

    return false;
  }

  private static boolean isFlagCheck(PsiElement element) {
    PsiElement scope = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiVariable.class);
    PsiExpression topExpression = scope instanceof PsiIfStatement ? ((PsiIfStatement)scope).getCondition() :
                                  scope instanceof PsiVariable ? ((PsiVariable)scope).getInitializer() :
                                  null;
    if (!PsiTreeUtil.isAncestor(topExpression, element, false)) return false;

    return StreamEx.<PsiElement>ofTree(topExpression, e -> StreamEx.of(e.getChildren()))
      .anyMatch(DataFlowInspectionBase::isCompileTimeFlagCheck);
  }

  private static boolean isCompileTimeFlagCheck(PsiElement element) {
    if(element instanceof PsiBinaryExpression) {
      PsiBinaryExpression binOp = (PsiBinaryExpression)element;
      if(ComparisonUtils.isComparisonOperation(binOp.getOperationTokenType())) {
        PsiExpression comparedWith = null;
        if(ExpressionUtils.isLiteral(binOp.getROperand())) {
          comparedWith = binOp.getLOperand();
        } else if(ExpressionUtils.isLiteral(binOp.getLOperand())) {
          comparedWith = binOp.getROperand();
        }
        comparedWith = PsiUtil.skipParenthesizedExprDown(comparedWith);
        if (isConstantOfType(comparedWith, PsiType.INT, PsiType.LONG)) {
          // like "if(DEBUG_LEVEL > 2)"
          return true;
        }
        if(comparedWith instanceof PsiBinaryExpression) {
          PsiBinaryExpression subOp = (PsiBinaryExpression)comparedWith;
          if(subOp.getOperationTokenType().equals(JavaTokenType.AND)) {
            PsiExpression left = PsiUtil.skipParenthesizedExprDown(subOp.getLOperand());
            PsiExpression right = PsiUtil.skipParenthesizedExprDown(subOp.getROperand());
            if(isConstantOfType(left, PsiType.INT, PsiType.LONG) ||
               isConstantOfType(right, PsiType.INT, PsiType.LONG)) {
              // like "if((FLAGS & SOME_FLAG) != 0)"
              return true;
            }
          }
        }
      }
    }
    // like "if(DEBUG)"
    return isConstantOfType(element, PsiType.BOOLEAN);
  }

  private static boolean isConstantOfType(PsiElement element, PsiPrimitiveType... types) {
    PsiElement resolved = element instanceof PsiReferenceExpression ? ((PsiReferenceExpression)element).resolve() : null;
    if (!(resolved instanceof PsiField)) return false;
    PsiField field = (PsiField)resolved;
    PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null ||
        !modifierList.hasModifierProperty(PsiModifier.STATIC) ||
        !modifierList.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    if (!ArrayUtil.contains(field.getType(), types)) return false;
    return field.hasInitializer() && PsiUtil.isConstantExpression(field.getInitializer());
  }

  private static boolean isNullLiteralExpression(PsiElement expr) {
    return expr instanceof PsiExpression && ExpressionUtils.isNullLiteral((PsiExpression)expr);
  }

  private @Nullable LocalQuickFix createSimplifyBooleanExpressionFix(PsiElement element, final boolean value) {
    LocalQuickFixOnPsiElement fix = createSimplifyBooleanFix(element, value);
    if (fix == null) return null;
    final String text = fix.getText();
    return new LocalQuickFix() {
      @Override
      public @NotNull String getName() {
        return text;
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement psiElement = descriptor.getPsiElement();
        if (psiElement == null) return;
        final LocalQuickFixOnPsiElement fix = createSimplifyBooleanFix(psiElement, value);
        if (fix == null) return;
        try {
          LOG.assertTrue(psiElement.isValid());
          fix.applyFix();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      @Override
      public @NotNull String getFamilyName() {
        return JavaAnalysisBundle.message("inspection.data.flow.simplify.boolean.expression.quickfix");
      }
    };
  }

  protected static @NotNull LocalQuickFix createSimplifyToAssignmentFix() {
    return new SimplifyToAssignmentFix();
  }

  protected LocalQuickFixOnPsiElement createSimplifyBooleanFix(PsiElement element, boolean value) {
    return null;
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  protected enum ConstantResult {
    TRUE, FALSE, NULL, ZERO, UNKNOWN;

    @Override
    public @NotNull String toString() {
      return this == ZERO ? "0" : StringUtil.toLowerCase(name());
    }

    public Object value() {
      switch (this) {
        case TRUE:
          return Boolean.TRUE;
        case FALSE:
          return Boolean.FALSE;
        case ZERO:
          return 0;
        case NULL:
          return null;
        default:
          throw new UnsupportedOperationException();
      }
    }

    static @NotNull ConstantResult fromDfType(@NotNull DfType dfType) {
      if (dfType == DfTypes.NULL) return NULL;
      if (dfType == DfTypes.TRUE) return TRUE;
      if (dfType == DfTypes.FALSE) return FALSE;
      if (dfType.isConst(0) || dfType.isConst(0L)) return ZERO;
      return UNKNOWN;
    }

    static @NotNull ConstantResult mergeValue(@Nullable ConstantResult state, @NotNull DfaMemoryState memState, @Nullable DfaValue value) {
      if (state == UNKNOWN || value == null) return UNKNOWN;
      ConstantResult nextState = fromDfType(DfaUtil.getUnboxedDfType(memState, value));
      return state == null || state == nextState ? nextState : UNKNOWN;
    }
  }

  /**
   * {@link ProblemsHolder} wrapper to avoid reporting two problems on the same anchor
   */
  protected static class ProblemReporter {
    private final Set<PsiElement> myReportedAnchors = new HashSet<>();
    private final ProblemsHolder myHolder;
    private final PsiElement myScope;

    ProblemReporter(ProblemsHolder holder, PsiElement scope) {
      myHolder = holder;
      myScope = scope;
    }

    public void registerProblem(PsiElement element, @InspectionMessage String message, LocalQuickFix... fixes) {
      if (register(element)) {
        myHolder.registerProblem(element, message, fixes);
      }
    }

    void registerProblem(PsiElement element, @InspectionMessage String message, ProblemHighlightType type, LocalQuickFix... fixes) {
      if (register(element)) {
        myHolder.registerProblem(element, message, type, fixes);
      }
    }

    void registerProblem(PsiElement element, TextRange range, @InspectionMessage String message, LocalQuickFix... fixes) {
      if (range == null) {
        registerProblem(element, message, fixes);
      }
      else {
        myHolder.registerProblem(element, range, message, fixes);
      }
    }

    private boolean register(PsiElement element) {
      // Suppress reporting for inlined simple methods
      if (!PsiTreeUtil.isAncestor(myScope, element, false)) return false;
      if (myScope instanceof PsiClass) {
        PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
        if (member instanceof PsiMethod && !((PsiMethod)member).isConstructor()) return false;
      }
      if (!myReportedAnchors.add(element)) return false;
      if (element instanceof PsiParenthesizedExpression) {
        PsiExpression deparenthesized = PsiUtil.skipParenthesizedExprDown((PsiExpression)element);
        if (deparenthesized != null) {
          myReportedAnchors.add(deparenthesized);
        }
      }
      return true;
    }

    boolean isOnTheFly() {
      return myHolder.isOnTheFly();
    }
  }
}
