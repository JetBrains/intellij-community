// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind.NullabilityProblem;
import com.intellij.codeInspection.dataFlow.fix.RedundantInstanceofFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithArgumentFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithObjectsEqualsFix;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.util.ObjectUtils.tryCast;

public abstract class DataFlowInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  private static final @NonNls String SHORT_NAME = "DataFlowIssue";
  public boolean SUGGEST_NULLABLE_ANNOTATIONS;
  public boolean TREAT_UNKNOWN_MEMBERS_AS_NULLABLE;
  public boolean IGNORE_ASSERT_STATEMENTS;
  public boolean REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = true;
  public boolean REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = true;
  public boolean REPORT_UNSOUND_WARNINGS = true;

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    node.addContent(new Element("option").setAttribute("name", "SUGGEST_NULLABLE_ANNOTATIONS").setAttribute("value", String.valueOf(SUGGEST_NULLABLE_ANNOTATIONS)));
    // Preserved for serialization compatibility
    node.addContent(new Element("option").setAttribute("name", "DONT_REPORT_TRUE_ASSERT_STATEMENTS").setAttribute("value", "false"));
    if (IGNORE_ASSERT_STATEMENTS) {
      node.addContent(new Element("option").setAttribute("name", "IGNORE_ASSERT_STATEMENTS").setAttribute("value", "true"));
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
      public void visitClass(@NotNull PsiClass aClass) {
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
            initialStates = ContainerUtil.map(states, DfaMemoryState::createCopy);
          }
          analyzeMethod(method, runner, initialStates);
        }
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
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
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
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

  protected @NotNull List<@NotNull LocalQuickFix> createCastFixes(PsiTypeCastExpression castExpression,
                                                                  PsiType realType,
                                                                  boolean onTheFly,
                                                                  boolean alwaysFails) {
    return Collections.emptyList();
  }

  protected @NotNull List<@NotNull LocalQuickFix> createNPEFixes(@Nullable PsiExpression qualifier,
                                                                 PsiExpression expression,
                                                                 boolean onTheFly,
                                                                 boolean alwaysNull) {
    return Collections.emptyList();
  }

  protected @NotNull List<@NotNull LocalQuickFix> createUnboxingNullableFixes(@NotNull PsiExpression qualifier, PsiElement anchor, boolean onTheFly) {
    return Collections.emptyList();
  }

  protected @NotNull List<@NotNull LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef, boolean onTheFly) {
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

  private void createDescription(ProblemsHolder holder,
                                 final DataFlowInstructionVisitor visitor,
                                 PsiElement scope,
                                 Instruction @NotNull [] instructions) {
    ProblemReporter reporter = new ProblemReporter(holder, scope);

    reportFailingCasts(reporter, visitor);
    reportUnreachableSwitchBranches(visitor.getSwitchLabelsReachability(), holder);

    reportAlwaysFailingCalls(reporter, visitor);

    List<NullabilityProblem<?>> problems = NullabilityProblemKind.postprocessNullabilityProblems(visitor.problems().toList());
    reportNullabilityProblems(reporter, problems);
    reportNullableReturns(reporter, problems, scope);

    reportRedundantInstanceOf(visitor, reporter);

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
    reportStreamConsumed(holder, visitor);
  }

  private static void reportRedundantInstanceOf(DataFlowInstructionVisitor visitor, ProblemReporter reporter) {
    visitor.redundantInstanceOfs().forEach(anchor -> {
      PsiExpression expression =
        anchor instanceof JavaExpressionAnchor ? ((JavaExpressionAnchor)anchor).getExpression() :
        anchor instanceof JavaMethodReferenceReturnAnchor ? ((JavaMethodReferenceReturnAnchor)anchor).getMethodReferenceExpression() :
        null;
      if (expression == null || shouldBeSuppressed(expression)) return;
      if (ContainerUtil.exists(JavaPsiPatternUtil.getExposedPatternVariables(expression),
                               var -> VariableAccessUtils.variableIsUsed(var, var.getDeclarationScope()))) {
        return;
      }
      ModCommandAction action = new RedundantInstanceofFix(expression);
      reporter.registerProblem(expression,
                               JavaAnalysisBundle.message("dataflow.message.redundant.instanceof"),
                               LocalQuickFix.from(action));
    });
  }

  private void reportUnreachableSwitchBranches(Map<PsiCaseLabelElement, ThreeState> labelReachability, ProblemsHolder holder) {
    if (labelReachability.isEmpty()) return;
    Set<PsiSwitchBlock> coveredSwitches = new HashSet<>();
    Map<PsiCaseLabelElement, PsiSwitchBlock> unreachableLabels = new HashMap<>();

    for (Map.Entry<PsiCaseLabelElement, ThreeState> entry : labelReachability.entrySet()) {
      if (entry.getValue() != ThreeState.YES) continue;
      PsiCaseLabelElement label = entry.getKey();
      PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(label);
      if (labelStatement == null) continue; // could be a guard
      PsiExpression guardExpression = labelStatement.getGuardExpression();
      if (guardExpression != null) {
        ThreeState guardReachability = labelReachability.get(guardExpression);
        if (guardReachability != ThreeState.YES) continue;
      }
      PsiSwitchBlock switchBlock = labelStatement.getEnclosingSwitchBlock();
      if (switchBlock == null) continue;
      if (!canRemoveTheOnlyReachableLabel(label, switchBlock)) continue;
      if (!StreamEx.iterate(labelStatement, Objects::nonNull, l -> PsiTreeUtil.getPrevSiblingOfType(l, PsiSwitchLabelStatementBase.class))
        .skip(1).map(PsiSwitchLabelStatementBase::getCaseLabelElementList)
        .nonNull().flatArray(PsiCaseLabelElementList::getElements)
        .append(StreamEx.iterate(label, Objects::nonNull, l -> PsiTreeUtil.getPrevSiblingOfType(l, PsiCaseLabelElement.class)).skip(1))
        .allMatch(l -> labelReachability.get(l) == ThreeState.NO)) {

        // Add all labels after always-reachable one as unreachable
        StreamEx.iterate(labelStatement, Objects::nonNull, l -> PsiTreeUtil.getNextSiblingOfType(l, PsiSwitchLabelStatementBase.class))
          .remove(SwitchUtils::isDefaultLabel)
          .skip(1).map(PsiSwitchLabelStatementBase::getCaseLabelElementList)
          .nonNull().flatArray(PsiCaseLabelElementList::getElements)
          .append(StreamEx.iterate(label, Objects::nonNull, l -> PsiTreeUtil.getNextSiblingOfType(l, PsiCaseLabelElement.class)).skip(1))
          .forEach(l -> unreachableLabels.put(l, switchBlock));
        continue;
      }
      coveredSwitches.add(switchBlock);
      LocalQuickFix unwrapFix;
      if ((switchBlock instanceof PsiSwitchExpression && !CodeBlockSurrounder.canSurround(((PsiSwitchExpression)switchBlock))) ||
          SwitchUtils.findRemovableUnreachableBranches(label, switchBlock).isEmpty()) {
        unwrapFix = null;
      }
      else {
        unwrapFix = createUnwrapSwitchLabelFix();
      }
      holder.registerProblem(label, JavaAnalysisBundle.message("dataflow.message.only.switch.label"),
                             LocalQuickFix.notNullElements(unwrapFix));
    }

    for (Map.Entry<PsiCaseLabelElement, ThreeState> entry : labelReachability.entrySet()) {
      if (entry.getValue() != ThreeState.NO) continue;
      PsiCaseLabelElement label = entry.getKey();
      PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(label);
      if (labelStatement == null) continue;
      PsiSwitchBlock switchBlock = labelStatement.getEnclosingSwitchBlock();
      if (switchBlock == null || coveredSwitches.contains(switchBlock)) continue;
      unreachableLabels.put(label, switchBlock);
    }
    unreachableLabels.forEach((label, switchBlock) -> {
      if (isThrowing(label)) return;
      // duplicate case label is a compilation error so no need to highlight by the inspection
      Set<PsiElement> suspiciousElements = findSuspiciousLabelElements(switchBlock);
      if (!suspiciousElements.contains(label)) {
        holder.problem(label, JavaAnalysisBundle.message("dataflow.message.unreachable.switch.label"))
            .maybeFix(createDeleteLabelFix(label)).register();
      }
    });
  }

  protected @Nullable LocalQuickFix createDeleteLabelFix(PsiCaseLabelElement label) {
    return null;
  }

  private static boolean isThrowing(PsiCaseLabelElement label) {
    PsiCaseLabelElementList caseLabelList = tryCast(label.getParent(), PsiCaseLabelElementList.class);
    if (caseLabelList == null) return false;
    PsiSwitchLabelStatementBase labelStatement = tryCast(caseLabelList.getParent(), PsiSwitchLabelStatementBase.class);
    if (labelStatement == null) return false;
    if (labelStatement instanceof PsiSwitchLabeledRuleStatement) {
      return ControlFlowUtils.stripBraces(((PsiSwitchLabeledRuleStatement)labelStatement).getBody()) instanceof PsiThrowStatement;
    }
    if (labelStatement instanceof PsiSwitchLabelStatement) {
      PsiElement cur = labelStatement;
      while(true) {
        PsiElement next = cur.getNextSibling();
        if (!(next instanceof PsiComment) && !(next instanceof PsiWhiteSpace) && !(next instanceof PsiSwitchLabelStatement)) {
          return next instanceof PsiThrowStatement;
        }
        cur = next;
      }
    }
    return false;
  }

  private static boolean canRemoveTheOnlyReachableLabel(@NotNull PsiCaseLabelElement label, @NotNull PsiSwitchBlock switchBlock) {
    if (!(label instanceof PsiPattern)) return true;
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return false;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return false;
    if (!JavaPsiPatternUtil.isUnconditionalForType(label, selectorType)) return true;
    int branchCount = SwitchUtils.calculateBranchCount(switchBlock);
    // it's a compilation error if switch contains both default and an unconditional pattern, so no additional suggestion is needed
    return branchCount > 1;
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
        if (eq.firstArgEqualToResult()) {
          String message = eq.argsEqual() ? JavaAnalysisBundle.message("dataflow.message.pointless.same.arguments") :
                           JavaAnalysisBundle.message("dataflow.message.pointless.same.argument.and.result", 1);
          LocalQuickFix fix = expressions.length == 2 ? new ReplaceWithArgumentFix(expressions[0], 0) : null;
          reporter.registerProblem(name, message, LocalQuickFix.notNullElements(fix));
        }
        else if (eq.argsEqual()) {
          reporter.registerProblem(name, JavaAnalysisBundle.message("dataflow.message.pointless.same.arguments"));
        }
        else if (eq.secondArgEqualToResult()) {
          LocalQuickFix fix = expressions.length == 2 ? new ReplaceWithArgumentFix(expressions[1], 1) : null;
          reporter.registerProblem(name, JavaAnalysisBundle.message("dataflow.message.pointless.same.argument.and.result", 2),
                                   LocalQuickFix.notNullElements(fix));
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
      if (expr instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiField field) {
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
      DfType value = CommonDataflow.getDfType(expr, IGNORE_ASSERT_STATEMENTS);
      // reported by ConstantValueInspection
      if (value == DfTypes.TRUE || value == DfTypes.FALSE) return;
      String message = assignment != null && !assignment.getOperationTokenType().equals(JavaTokenType.EQ)
                       ? JavaAnalysisBundle.message("dataflow.message.redundant.update")
                       : JavaAnalysisBundle.message("dataflow.message.redundant.assignment");
      reporter.registerProblem(expr, message, LocalQuickFix.notNullElements(createRemoveAssignmentFix(assignment)));
    });
  }

  private void reportMutabilityViolations(ProblemsHolder holder, Set<PsiElement> violations, @InspectionMessage String message) {
    for (PsiElement violation : violations) {
      holder.registerProblem(violation, message, LocalQuickFix.notNullElements(createMutabilityViolationFix(violation)));
    }
  }

  protected LocalQuickFix createMutabilityViolationFix(PsiElement violation) {
    return null;
  }

  protected void reportNullabilityProblems(ProblemReporter reporter, List<NullabilityProblem<?>> problems) {
    for (NullabilityProblem<?> problem : problems) {
      PsiExpression expression = problem.getDereferencedExpression();
      boolean nullLiteral = ExpressionUtils.isNullLiteral(expression);
      if (!REPORT_UNSOUND_WARNINGS) {
        if (expression == null || !nullLiteral && CommonDataflow.getDfType(expression, IGNORE_ASSERT_STATEMENTS) != DfTypes.NULL) continue;
      }
      // Expression of null type: could be failed LVTI, skip it to avoid confusion
      if (expression != null && !nullLiteral && PsiTypes.nullType().equals(expression.getType())) continue;
      boolean alwaysNull = problem.isAlwaysNull(IGNORE_ASSERT_STATEMENTS);
      NullabilityProblemKind.innerClassNPE.ifMyProblem(problem, newExpression -> {
        List<LocalQuickFix> fixes = createNPEFixes(newExpression.getQualifier(), newExpression, reporter.isOnTheFly(), alwaysNull);
        reporter
          .registerProblem(getElementToHighlight(newExpression), problem.getMessage(IGNORE_ASSERT_STATEMENTS),
                           fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      });
      NullabilityProblemKind.callMethodRefNPE.ifMyProblem(problem, methodRef ->
        reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.npe.methodref.invocation"),
                                 createMethodReferenceNPEFixes(methodRef, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY)));
      NullabilityProblemKind.callNPE.ifMyProblem(problem, call ->
        reportCallMayProduceNpe(reporter, problem.getMessage(IGNORE_ASSERT_STATEMENTS), call, alwaysNull));
      NullabilityProblemKind.passingToNotNullParameter.ifMyProblem(problem, expr -> {
        List<LocalQuickFix> fixes = createNPEFixes(expression, expression, reporter.isOnTheFly(), alwaysNull);
        reporter.registerProblem(expression, problem.getMessage(IGNORE_ASSERT_STATEMENTS), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
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
        LocalQuickFix[] fixes = createNPEFixes(arrayAccess.getArrayExpression(), arrayAccess, reporter.isOnTheFly(),
                                               alwaysNull).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(arrayAccess, problem.getMessage(IGNORE_ASSERT_STATEMENTS), fixes);
      });
      NullabilityProblemKind.templateNPE.ifMyProblem(problem, template -> {
        PsiExpression processor = template.getProcessor();
        LocalQuickFix[] fixes = createNPEFixes(processor, template, reporter.isOnTheFly(),
                                               alwaysNull).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(processor, problem.getMessage(IGNORE_ASSERT_STATEMENTS), fixes);
      });
      NullabilityProblemKind.fieldAccessNPE.ifMyProblem(problem, element -> {
        PsiElement parent = element.getParent();
        PsiExpression fieldAccess = parent instanceof PsiReferenceExpression ? (PsiExpression)parent : element;
        LocalQuickFix[] fix = createNPEFixes(element, fieldAccess, reporter.isOnTheFly(), alwaysNull).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(element, problem.getMessage(IGNORE_ASSERT_STATEMENTS), fix);
      });
      NullabilityProblemKind.unboxingNullable.ifMyProblem(problem, element -> {
        PsiExpression anchor = expression;
        if (anchor instanceof PsiTypeCastExpression && anchor.getType() instanceof PsiPrimitiveType) {
          anchor = Objects.requireNonNull(((PsiTypeCastExpression)anchor).getOperand());
        }
        if (anchor != null) {
          LocalQuickFix[] fixes = createUnboxingNullableFixes(anchor, element, reporter.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
          reporter.registerProblem(anchor, problem.getMessage(IGNORE_ASSERT_STATEMENTS), fixes);
        }
      });
      NullabilityProblemKind.nullableFunctionReturn.ifMyProblem(
        problem, expr -> reporter.registerProblem(expression == null ? expr : expression, problem.getMessage(IGNORE_ASSERT_STATEMENTS)));
      Consumer<PsiExpression> reportNullability = expr -> reportNullabilityProblem(reporter, problem, expression);
      NullabilityProblemKind.assigningToNotNull.ifMyProblem(problem, reportNullability);
      NullabilityProblemKind.storingToNotNullArray.ifMyProblem(problem, reportNullability);
      if (SUGGEST_NULLABLE_ANNOTATIONS) {
        NullabilityProblemKind.passingToNonAnnotatedMethodRefParameter.ifMyProblem(
          problem, methodRef -> reportNullableArgumentPassedToNonAnnotatedMethodRef(reporter, problem, methodRef));
        NullabilityProblemKind.passingToNonAnnotatedParameter.ifMyProblem(
          problem,
          top -> reportNullableArgumentsPassedToNonAnnotated(reporter, problem.getMessage(IGNORE_ASSERT_STATEMENTS), expression, top,
                                                             alwaysNull));
        NullabilityProblemKind.assigningToNonAnnotatedField.ifMyProblem(
          problem, top -> reportNullableAssignedToNonAnnotatedField(reporter, top, expression, problem.getMessage(IGNORE_ASSERT_STATEMENTS),
                                                                    alwaysNull));
      }
    }
  }

  private void reportNullabilityProblem(ProblemReporter reporter,
                                        NullabilityProblem<?> problem,
                                        PsiExpression expr) {
    LocalQuickFix[] fixes = createNPEFixes(expr, expr, reporter.isOnTheFly(), problem.isAlwaysNull(IGNORE_ASSERT_STATEMENTS))
      .toArray(LocalQuickFix.EMPTY_ARRAY);
    reporter.registerProblem(expr, problem.getMessage(IGNORE_ASSERT_STATEMENTS), fixes);
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

  private void reportStreamConsumed(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.streamConsumed().forKeyValue((psiElement, alwaysFails) -> {
      if (!REPORT_UNSOUND_WARNINGS && !alwaysFails) return;
      holder.registerProblem(psiElement, JavaAnalysisBundle.message(alwaysFails ? "dataflow.message.stream.consumed.always" :
                                                                    "dataflow.message.stream.consumed"));
    });
  }

  private static void reportArrayStoreProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.getArrayStoreProblems().forEach(
      (assignment, types) -> holder.registerProblem(assignment.getOperationSign(), JavaAnalysisBundle
        .message("dataflow.message.arraystore", types.getFirst().getCanonicalText(), types.getSecond().getCanonicalText())));
  }

  private void reportAlwaysReturnsNotNull(ProblemsHolder holder, PsiElement scope) {
    if (!(scope.getParent() instanceof PsiMethod method) || PsiUtil.canBeOverridden(method)) return;

    NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(scope.getProject()).findOwnNullabilityInfo(method);
    if (info == null || info.getNullability() != Nullability.NULLABLE) return;
    if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, method.getReturnType())) return;

    PsiAnnotation annotation = info.getAnnotation();
    if (!annotation.isPhysical() || alsoAppliesToInternalSubType(annotation, method)) return;

    PsiJavaCodeReferenceElement annoName = annotation.getNameReferenceElement();
    assert annoName != null;
    String msg = JavaAnalysisBundle
      .message("dataflow.message.return.notnull.from.nullable", NullableStuffInspectionBase.getPresentableAnnoName(annotation),
               method.getName());
    holder.problem(annoName, msg)
      .maybeFix(AddAnnotationModCommandAction.createAddNotNullFix(method))
      .fix(new UpdateInspectionOptionFix(this, "REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL",
                                         JavaAnalysisBundle.message(
                                           "inspection.data.flow.turn.off.nullable.returning.notnull.quickfix"),
                                         false))
      .register();
  }

  private static boolean alsoAppliesToInternalSubType(PsiAnnotation annotation, PsiMethod method) {
    return AnnotationTargetUtil.isTypeAnnotation(annotation) && method.getReturnType() instanceof PsiArrayType;
  }

  private void reportAlwaysFailingCalls(ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.alwaysFailingCalls().remove(TestUtils::isExceptionExpected).forEach(anchor -> {
      List<? extends MethodContract> contracts = DataFlowInstructionVisitor.getContracts(anchor);
      if (contracts != null && contracts.isEmpty()) {
        PsiMethod method = anchor instanceof PsiCallExpression call ? call.resolveMethod() :
                           anchor instanceof PsiMethodReferenceExpression methodRef ? tryCast(methodRef.resolve(), PsiMethod.class) : null;
        contracts = DfaUtil.addRangeContracts(method, List.of());
      }
      if (contracts == null) return;
      String message = getContractMessage(contracts);
      LocalQuickFix causeFix = createExplainFix(anchor, new TrackingRunner.FailingCallDfaProblemType());
      reporter.registerProblem(getElementToHighlight(anchor), message, LocalQuickFix.notNullElements(causeFix));
    });
  }

  private static @NotNull @InspectionMessage String getContractMessage(List<? extends MethodContract> contracts) {
    if (contracts.isEmpty()) {
      return JavaAnalysisBundle.message("dataflow.message.fail");
    }
    if (ContainerUtil.and(contracts, mc -> ContainerUtil.and(mc.getConditions(), ContractValue::isBoundCheckingCondition))) {
      return JavaAnalysisBundle.message("dataflow.message.contract.fail.index");
    }
    return JavaAnalysisBundle.message("dataflow.message.contract.fail");
  }

  private static @NotNull PsiElement getElementToHighlight(@NotNull PsiElement element) {
    PsiJavaCodeReferenceElement ref;
    if (element instanceof PsiNewExpression newExpression) {
      ref = newExpression.getClassReference();
    }
    else if (element instanceof PsiMethodCallExpression callExpression) {
      ref = callExpression.getMethodExpression();
    }
    else {
      return element;
    }
    if (ref != null) {
      PsiElement name = ref.getReferenceNameElement();
      return name != null ? name : ref;
    }
    return element;
  }

  private void reportNullableArgumentPassedToNonAnnotatedMethodRef(@NotNull ProblemReporter reporter,
                                                                   @NotNull NullabilityProblem<?> problem,
                                                                   @NotNull PsiMethodReferenceExpression methodRef) {
    PsiMethod target = tryCast(methodRef.resolve(), PsiMethod.class);
    if (target == null) return;
    PsiParameter[] parameters = target.getParameterList().getParameters();
    if (parameters.length == 0) return;
    PsiParameter parameter = parameters[0];
    if (!BaseIntentionAction.canModify(parameter) || !AnnotationUtil.isAnnotatingApplicable(parameter)) return;
    reporter.registerProblem(methodRef, problem.getMessage(IGNORE_ASSERT_STATEMENTS),
                             LocalQuickFix.notNullElements(
                               parameters.length == 1
                               ? LocalQuickFix.from(AddAnnotationModCommandAction.createAddNullableFix(parameter))
                               : null));
  }

  private void reportNullableArgumentsPassedToNonAnnotated(ProblemReporter reporter,
                                                           @InspectionMessage String message,
                                                           PsiExpression expression,
                                                           PsiExpression top, boolean alwaysNull) {
    PsiParameter parameter = MethodCallUtils.getParameterForArgument(top);
    if (parameter == null) return;
    PsiModifierListOwner target = Objects.requireNonNullElse(JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter(parameter), parameter);
    if (BaseIntentionAction.canModify(target) && AnnotationUtil.isAnnotatingApplicable(target)) {
      List<LocalQuickFix> fixes = createNPEFixes(expression, top, reporter.isOnTheFly(), alwaysNull);
      fixes.add(LocalQuickFix.from(AddAnnotationModCommandAction.createAddNullableFix(target)));
      reporter.registerProblem(expression, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private void reportNullableAssignedToNonAnnotatedField(ProblemReporter reporter,
                                                         PsiExpression top,
                                                         PsiExpression expression,
                                                         @InspectionMessage String message,
                                                         boolean alwaysNull) {
    PsiField field = getAssignedField(top);
    if (field != null) {
      List<LocalQuickFix> fixes = createNPEFixes(expression, top, reporter.isOnTheFly(), alwaysNull);
      fixes.add(LocalQuickFix.from(AddAnnotationModCommandAction.createAddNullableFix(field)));
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

  private void reportCallMayProduceNpe(ProblemReporter reporter, @InspectionMessage String message, PsiMethodCallExpression callExpression,
                                       boolean alwaysNull) {
    PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    List<LocalQuickFix> fixes = createNPEFixes(methodExpression.getQualifierExpression(), callExpression, reporter.isOnTheFly(), alwaysNull);
    if (!alwaysNull) {
      ContainerUtil.addIfNotNull(fixes, ReplaceWithObjectsEqualsFix.createFix(callExpression, methodExpression));
    }

    PsiElement toHighlight = getElementToHighlight(callExpression);
    reporter.registerProblem(toHighlight, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private void reportFailingCasts(@NotNull ProblemReporter reporter, @NotNull DataFlowInstructionVisitor visitor) {
    visitor.getFailingCastExpressions().forKeyValue((typeCast, info) -> {
      boolean alwaysFails = info.getFirst();
      PsiType realType = info.getSecond();
      if (!REPORT_UNSOUND_WARNINGS && !alwaysFails) return;
      PsiExpression operand = typeCast.getOperand();
      PsiTypeElement castType = typeCast.getCastType();
      if (ExpressionUtils.isNullLiteral(operand) || DfTypes.NULL.equals(CommonDataflow.getDfType(operand, IGNORE_ASSERT_STATEMENTS))) {
        // Skip reporting if cast operand is always null: null can be cast to anything
        return;
      }
      assert castType != null;
      assert operand != null;
      List<LocalQuickFix> fixes = new ArrayList<>(createCastFixes(typeCast, realType, reporter.isOnTheFly(), alwaysFails));
      ContainerUtil.addIfNotNull(fixes, createExplainFix(typeCast, new TrackingRunner.CastDfaProblemType()));
      String text = PsiExpressionTrimRenderer.render(operand);
      String message = alwaysFails ?
                       JavaAnalysisBundle.message("dataflow.message.cce.always", text) :
                       JavaAnalysisBundle.message("dataflow.message.cce", text);
      reporter.registerProblem(castType, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    });
  }

  protected @Nullable LocalQuickFix createExplainFix(PsiExpression anchor, TrackingRunner.DfaProblemType problemType) {
    return null;
  }

  private static boolean shouldBeSuppressed(@NotNull PsiExpression anchor) {
    if (anchor instanceof PsiInstanceOfExpression) {
      PsiType type = ((PsiInstanceOfExpression)anchor).getOperand().getType();
      if (type == null || !TypeConstraints.instanceOf(type).isResolved()) return true;
      // 5.20.2 Removed restriction on pattern instanceof for unconditional patterns (JEP 427)
      if (PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, anchor)) return false;
      PsiPattern pattern = ((PsiInstanceOfExpression)anchor).getPattern();
      if (pattern instanceof PsiTypeTestPattern && ((PsiTypeTestPattern)pattern).getPatternVariable() != null) {
        PsiTypeElement checkType = ((PsiTypeTestPattern)pattern).getCheckType();
        // Reported as compilation error
        return checkType != null && checkType.getType().isAssignableFrom(type);
      }
    }
    return false;
  }

  private static @Nullable PsiMethod getScopeMethod(PsiElement block) {
    PsiElement parent = block.getParent();
    if (parent instanceof PsiMethod) return (PsiMethod)parent;
    if (parent instanceof PsiLambdaExpression) return LambdaUtil.getFunctionalInterfaceMethod(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
    return null;
  }

  private void reportNullableReturns(ProblemReporter reporter,
                                     List<NullabilityProblem<?>> problems,
                                     @NotNull PsiElement block) {
    final PsiMethod method = getScopeMethod(block);
    if (method == null) return;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(method);
    if (info == null || info.isInferred()) info = DfaPsiUtil.getTypeNullabilityInfo(PsiTypesUtil.getMethodReturnType(block));
    PsiAnnotation anno = info == null ? null : info.getAnnotation();
    Nullability nullability = info == null ? Nullability.UNKNOWN : info.getNullability();
    PsiType returnType = method.getReturnType();
    if (nullability == Nullability.NULLABLE) {
      if (!info.isInferred() || DfaPsiUtil.getTypeNullability(returnType) == Nullability.NULLABLE) return;
    }
    // In rare cases, inference may produce different result (e.g. if nullable method overrides non-null method)
    if (nullability == Nullability.NOT_NULL && info.isInferred()) return;

    if (nullability != Nullability.NOT_NULL && (!SUGGEST_NULLABLE_ANNOTATIONS || block.getParent() instanceof PsiLambdaExpression)) return;

    // no warnings in void lambdas, where the expression is not returned anyway
    if (block instanceof PsiExpression && block.getParent() instanceof PsiLambdaExpression && PsiTypes.voidType().equals(returnType)) return;

    // no warnings for Void methods, where only null can be possibly returned
    if (returnType == null || returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID)) return;

    for (NullabilityProblem<PsiExpression> problem : StreamEx.of(problems).map(NullabilityProblemKind.nullableReturn::asMyProblem).nonNull()) {
      final PsiExpression anchor = problem.getAnchor();
      PsiExpression expr = problem.getDereferencedExpression();

      boolean exactlyNull = problem.isAlwaysNull(IGNORE_ASSERT_STATEMENTS);
      if (!REPORT_UNSOUND_WARNINGS && !exactlyNull) continue;
      if (nullability == Nullability.NOT_NULL) {
        String presentable = NullableStuffInspectionBase.getPresentableAnnoName(anno);
        final String text = exactlyNull
                            ? JavaAnalysisBundle.message("dataflow.message.return.null.from.notnull", presentable)
                            : JavaAnalysisBundle.message("dataflow.message.return.nullable.from.notnull", presentable);
        List<LocalQuickFix> fixes = createNPEFixes(expr, expr, reporter.isOnTheFly(), exactlyNull);
        PsiMethod surroundingMethod = PsiTreeUtil.getParentOfType(anchor, PsiMethod.class, true, PsiLambdaExpression.class);
        if (surroundingMethod != null) {
          LocalQuickFix fix = LocalQuickFix.from(AddAnnotationModCommandAction.createAddNullableFix(surroundingMethod));
          fixes = StreamEx.of(fixes).append(fix).toList();
        }
        reporter.registerProblem(expr, text, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
      else if (AnnotationUtil.isAnnotatingApplicable(anchor)) {
        final String defaultNullable = manager.getDefaultNullable();
        final String presentableNullable = StringUtil.getShortName(defaultNullable);
        final String text = exactlyNull
                            ? JavaAnalysisBundle.message("dataflow.message.return.null.from.notnullable", presentableNullable)
                            : JavaAnalysisBundle.message("dataflow.message.return.nullable.from.notnullable", presentableNullable);
        PsiMethod surroundingMethod = PsiTreeUtil.getParentOfType(anchor, PsiMethod.class, true, PsiLambdaExpression.class);
        final LocalQuickFix fix = surroundingMethod == null ? null :
                                  LocalQuickFix.from(AddAnnotationModCommandAction.createAddNullableFix(surroundingMethod));
        reporter.registerProblem(expr, text, LocalQuickFix.notNullElements(fix));
      }
    }
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  /**
   * @param switchBlock switch statement/expression to check
   * @return a set of label elements that are duplicates. If a switch block contains patterns,
   * then dominated label elements will be also included in the result set.
   */
  private static @NotNull Set<PsiElement> findSuspiciousLabelElements(@NotNull PsiSwitchBlock switchBlock) {
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return Collections.emptySet();
    PsiType selectorType = selector.getType();
    if (selectorType == null) return Collections.emptySet();
    List<PsiCaseLabelElement> labelElements =
      ContainerUtil.filterIsInstance(JavaPsiSwitchUtil.getSwitchBranches(switchBlock), PsiCaseLabelElement.class);
    if (labelElements.isEmpty()) return Collections.emptySet();
    MultiMap<Object, PsiElement> duplicateCandidates = JavaPsiSwitchUtil.getValuesAndLabels(switchBlock);

    Set<PsiElement> result = new SmartHashSet<>();

    for (Map.Entry<Object, Collection<PsiElement>> entry : duplicateCandidates.entrySet()) {
      if (entry.getValue().size() <= 1) continue;
      result.addAll(entry.getValue());
    }

    // Find only one unconditional pattern, but not all, because if there are
    // multiple unconditional patterns, they will all be found as duplicates
    PsiCaseLabelElement unconditionalPattern = ContainerUtil.find(
      labelElements, element -> JavaPsiPatternUtil.isUnconditionalForType(element, selectorType));
    PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
    if (unconditionalPattern != null && defaultElement != null) {
      result.add(unconditionalPattern);
      result.add(defaultElement);
    }

    return StreamEx.ofKeys(JavaPsiSwitchUtil.findDominatedLabels(switchBlock), value -> value instanceof PsiPattern)
      .into(result);
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

    public void registerProblem(PsiElement element, @InspectionMessage String message, @NotNull LocalQuickFix @NotNull ... fixes) {
      if (register(element)) {
        myHolder.registerProblem(element, message, fixes);
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
