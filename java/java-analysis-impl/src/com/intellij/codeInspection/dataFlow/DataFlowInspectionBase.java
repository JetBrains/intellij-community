// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.impl.AddNotNullAnnotationFix;
import com.intellij.codeInsight.intention.impl.AddNullableAnnotationFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind.NullabilityProblem;
import com.intellij.codeInspection.dataFlow.fix.RedundantInstanceofFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithConstantValueFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithObjectsEqualsFix;
import com.intellij.codeInspection.dataFlow.fix.SimplifyToAssignmentFix;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TestUtils;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

@SuppressWarnings("ConditionalExpressionWithIdenticalBranches")
public class DataFlowInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowInspection");
  @NonNls private static final String SHORT_NAME = "ConstantConditions";
  public boolean SUGGEST_NULLABLE_ANNOTATIONS;
  public boolean DONT_REPORT_TRUE_ASSERT_STATEMENTS;
  public boolean TREAT_UNKNOWN_MEMBERS_AS_NULLABLE;
  public boolean IGNORE_ASSERT_STATEMENTS;
  public boolean REPORT_CONSTANT_REFERENCE_VALUES = true;
  public boolean REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = true;
  public boolean REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = true;
  public boolean REPORT_UNCHECKED_OPTIONALS = true;

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
    if (!REPORT_UNCHECKED_OPTIONALS) {
      node.addContent(new Element("option").setAttribute("name", "REPORT_UNCHECKED_OPTIONALS").setAttribute("value", "false"));
    }
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        if (aClass instanceof PsiTypeParameter) return;
        analyzeCodeBlock(aClass, holder);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        analyzeCodeBlock(method.getBody(), holder);
        analyzeNullLiteralMethodArguments(method, holder);
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        super.visitMethodReferenceExpression(expression);
        final PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          final PsiType methodReturnType = ((PsiMethod)resolve).getReturnType();
          if (TypeConversionUtil.isPrimitiveWrapper(methodReturnType) && NullableNotNullManager.isNullable((PsiMethod)resolve)) {
            final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(expression);
            if (TypeConversionUtil.isPrimitiveAndNotNull(returnType)) {
              holder.registerProblem(expression, InspectionsBundle.message("dataflow.message.unboxing.method.reference"));
            }
          }
        }
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
        if (BranchingInstruction.isBoolConst(condition)) {
          LocalQuickFix fix = createSimplifyBooleanExpressionFix(condition, condition.textMatches(PsiKeyword.TRUE));
          holder.registerProblem(condition, "Condition is always " + condition.getText(), fix);
        }
      }

      @Override
      public void visitWhileStatement(PsiWhileStatement statement) {
        checkLoopCondition(statement.getCondition());
      }

      @Override
      public void visitDoWhileStatement(PsiDoWhileStatement statement) {
        checkLoopCondition(statement.getCondition());
      }

      @Override
      public void visitForStatement(PsiForStatement statement) {
        checkLoopCondition(statement.getCondition());
      }

      private void checkLoopCondition(PsiExpression condition) {
        condition = PsiUtil.skipParenthesizedExprDown(condition);
        if (condition != null && condition.textMatches(PsiKeyword.FALSE)) {
          holder.registerProblem(condition, "Condition is always false", createSimplifyBooleanExpressionFix(condition, false));
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
          holder.registerProblem(name, InspectionsBundle.message("dataflow.method.fails.with.null.argument"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createNavigateToNullParameterUsagesFix(parameter));
        }
      }
    }
  }

  private void analyzeCodeBlock(@Nullable final PsiElement scope, ProblemsHolder holder) {
    if (scope == null) return;

    PsiClass containingClass = PsiTreeUtil.getNonStrictParentOfType(scope, PsiClass.class);
    if (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass) && !(containingClass instanceof PsiEnumConstantInitializer)) return;

    final StandardDataFlowRunner dfaRunner =
      new StandardDataFlowRunner(TREAT_UNKNOWN_MEMBERS_AS_NULLABLE, !DfaUtil.isInsideConstructorOrInitializer(scope));
    analyzeDfaWithNestedClosures(scope, holder, dfaRunner, Collections.singletonList(dfaRunner.createMemoryState()));
  }

  private void analyzeDfaWithNestedClosures(PsiElement scope,
                                            ProblemsHolder holder,
                                            StandardDataFlowRunner dfaRunner,
                                            Collection<? extends DfaMemoryState> initialStates) {
    final DataFlowInstructionVisitor visitor = new DataFlowInstructionVisitor();
    final RunnerResult rc = dfaRunner.analyzeMethod(scope, visitor, IGNORE_ASSERT_STATEMENTS, initialStates);
    if (rc == RunnerResult.OK) {
      createDescription(dfaRunner, holder, visitor, scope);
      dfaRunner.forNestedClosures((closure, states) -> analyzeDfaWithNestedClosures(closure, holder, dfaRunner, states));
    }
    else if (rc == RunnerResult.TOO_COMPLEX) {
      PsiIdentifier name = null;
      String message = null;
      if(scope.getParent() instanceof PsiMethod) {
        name = ((PsiMethod)scope.getParent()).getNameIdentifier();
        message = InspectionsBundle.message("dataflow.too.complex");
      } else if(scope instanceof PsiClass) {
        name = ((PsiClass)scope).getNameIdentifier();
        message = InspectionsBundle.message("dataflow.too.complex.class");
      }
      if (name != null) { // Might be null for synthetic methods like JSP page.
        holder.registerProblem(name, message, ProblemHighlightType.WEAK_WARNING);
      }
    }
  }

  @NotNull
  protected List<LocalQuickFix> createNPEFixes(PsiExpression qualifier, PsiExpression expression, boolean onTheFly) {
    return Collections.emptyList();
  }

  protected List<LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef) {
    return Collections.emptyList();
  }

  @Nullable
  protected LocalQuickFix createIntroduceVariableFix(PsiExpression expression) {
    return null;
  }

  protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment) {
    return null;
  }

  protected LocalQuickFix createReplaceWithTrivialLambdaFix(Object value) {
    return null;
  }

  private void createDescription(StandardDataFlowRunner runner,
                                 ProblemsHolder holder,
                                 final DataFlowInstructionVisitor visitor,
                                 PsiElement scope) {
    Pair<Set<Instruction>, Set<Instruction>> constConditions = runner.getConstConditionalExpressions();
    Set<Instruction> trueSet = constConditions.getFirst();
    Set<Instruction> falseSet = constConditions.getSecond();

    ArrayList<Instruction> allProblems = new ArrayList<>();
    allProblems.addAll(trueSet);
    allProblems.addAll(falseSet);
    allProblems.addAll(visitor.getClassCastExceptionInstructions());
    allProblems.addAll(ContainerUtil.filter(runner.getInstructions(), instruction1 -> instruction1 instanceof InstanceofInstruction && visitor.isInstanceofRedundant((InstanceofInstruction)instruction1)));

    HashSet<PsiElement> reportedAnchors = new HashSet<>();

    for (Instruction instruction : allProblems) {
      if (instruction instanceof TypeCastInstruction &&
               reportedAnchors.add(((TypeCastInstruction)instruction).getCastExpression().getCastType())) {
        reportCastMayFail(holder, (TypeCastInstruction)instruction);
      }
      else if (instruction instanceof BranchingInstruction) {
        handleBranchingInstruction(holder, visitor, trueSet, falseSet, reportedAnchors, (BranchingInstruction)instruction);
      }
    }

    reportAlwaysFailingCalls(holder, visitor, reportedAnchors);

    reportConstantPushes(runner, holder, reportedAnchors);

    reportNullabilityProblems(holder, visitor, reportedAnchors);
    reportNullableReturns(visitor, holder, reportedAnchors, scope);
    if (SUGGEST_NULLABLE_ANNOTATIONS) {
      reportNullableArgumentsPassedToNonAnnotated(visitor, holder, reportedAnchors);
    }

    reportOptionalOfNullableImprovements(holder, reportedAnchors, visitor.getOfNullableCalls());

    reportUncheckedOptionalGet(holder, visitor.getOptionalCalls(), visitor.getOptionalQualifiers());

    visitor.getBooleanCalls().forEach((call, state) -> {
      if (state != ThreeState.UNSURE && reportedAnchors.add(call)) {
        reportConstantCondition(holder, call, state.toBoolean());
      }
    });

    reportMethodReferenceProblems(holder, visitor);

    reportArrayAccessProblems(holder, visitor);

    reportArrayStoreProblems(holder, visitor);

    if (REPORT_CONSTANT_REFERENCE_VALUES) {
      reportConstantReferenceValues(holder, visitor, reportedAnchors);
    }

    if (REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL && visitor.isAlwaysReturnsNotNull(runner.getInstructions())) {
      reportAlwaysReturnsNotNull(holder, scope);
    }

    reportMutabilityViolations(holder, reportedAnchors, visitor.getMutabilityViolations(true),
                               InspectionsBundle.message("dataflow.message.immutable.modified"));
    reportMutabilityViolations(holder, reportedAnchors, visitor.getMutabilityViolations(false),
                               InspectionsBundle.message("dataflow.message.immutable.passed"));

    reportDuplicateAssignments(holder, reportedAnchors, visitor);
  }

  private void reportDuplicateAssignments(ProblemsHolder holder,
                                          HashSet<PsiElement> reportedAnchors,
                                          DataFlowInstructionVisitor visitor) {
    visitor.sameValueAssignments().forEach(expr -> {
      if(!reportedAnchors.add(expr)) return;
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(expr, PsiAssignmentExpression.class);
      PsiElement context = PsiTreeUtil.getParentOfType(expr, PsiForStatement.class, PsiClassInitializer.class);
      if (context instanceof PsiForStatement && PsiTreeUtil.isAncestor(((PsiForStatement)context).getInitialization(), expr, true)) {
        return;
      }
      if (context instanceof PsiClassInitializer && expr instanceof PsiReferenceExpression) {
        if (assignment != null) {
          Object constValue = ExpressionUtils.computeConstantExpression(assignment.getRExpression());
          if (constValue == PsiTypesUtil.getDefaultValue(expr.getType())) {
            PsiReferenceExpression ref = (PsiReferenceExpression)expr;
            PsiElement target = ref.resolve();
            if (target instanceof PsiField &&
                (((PsiField)target).hasModifierProperty(PsiModifier.STATIC) || ExpressionUtil.isEffectivelyUnqualified(ref)) &&
                ((PsiField)target).getContainingClass() == ((PsiClassInitializer)context).getContainingClass()) {
              return;
            }
          }
        }
      }
      holder.registerProblem(expr, InspectionsBundle.message("dataflow.message.redundant.assignment"), createRemoveAssignmentFix(assignment));
    });
  }

  private static void reportMutabilityViolations(ProblemsHolder holder,
                                                 Set<PsiElement> reportedAnchors,
                                                 Set<PsiElement> violations,
                                                 String message) {
    for (PsiElement violation : violations) {
      if (reportedAnchors.add(violation)) {
        holder.registerProblem(violation, message);
      }
    }
  }

  private void reportNullabilityProblems(ProblemsHolder holder,
                                         DataFlowInstructionVisitor visitor,
                                         HashSet<PsiElement> reportedAnchors) {
    visitor.problems().forEach(problem -> {
      if (NullabilityProblemKind.passingNullableArgumentToNonAnnotatedParameter.isMyProblem(problem) ||
          NullabilityProblemKind.nullableReturn.isMyProblem(problem)) {
        // these two kinds are still reported separately
        return;
      }
      if (!reportedAnchors.add(problem.getAnchor())) return;
      NullabilityProblemKind.innerClassNPE.ifMyProblem(problem, newExpression -> {
        List<LocalQuickFix> fixes = createNPEFixes(newExpression.getQualifier(), newExpression, holder.isOnTheFly());
        holder.registerProblem(getElementToHighlight(newExpression), problem.getMessage(), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      });
      NullabilityProblemKind.callMethodRefNPE.ifMyProblem(problem, methodRef ->
        holder.registerProblem(methodRef, InspectionsBundle.message("dataflow.message.npe.methodref.invocation"),
                               createMethodReferenceNPEFixes(methodRef).toArray(LocalQuickFix.EMPTY_ARRAY)));
      NullabilityProblemKind.callNPE.ifMyProblem(problem, call -> reportCallMayProduceNpe(holder, call));
      NullabilityProblemKind.passingNullableToNotNullParameter.ifMyProblem(problem, expr -> reportNullableArgument(holder, expr));
      NullabilityProblemKind.arrayAccessNPE.ifMyProblem(problem, expression -> {
        LocalQuickFix[] fix =
          createNPEFixes(expression.getArrayExpression(), expression, holder.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
        holder.registerProblem(expression, problem.getMessage(), fix);
      });
      NullabilityProblemKind.fieldAccessNPE.ifMyProblem(problem, element -> {
        PsiElement parent = element.getParent();
        PsiExpression fieldAccess = parent instanceof PsiReferenceExpression ? (PsiExpression)parent : element;
        LocalQuickFix[] fix = createNPEFixes(element, fieldAccess, holder.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
        holder.registerProblem(element, problem.getMessage(), fix);
      });
      NullabilityProblemKind.unboxingNullable.ifMyProblem(problem, element -> holder.registerProblem(element, problem.getMessage()));
      NullabilityProblemKind.nullableFunctionReturn.ifMyProblem(problem, expr -> holder.registerProblem(expr, problem.getMessage()));
      NullabilityProblemKind.assigningToNotNull.ifMyProblem(problem, expr -> reportNullabilityProblem(holder, problem, expr));
      NullabilityProblemKind.storingToNotNullArray.ifMyProblem(problem, expr -> reportNullabilityProblem(holder, problem, expr));
    });
  }

  private void reportNullabilityProblem(ProblemsHolder holder, NullabilityProblem<?> problem, PsiExpression expr) {
    holder.registerProblem(expr, problem.getMessage(), createNPEFixes(expr, expr, holder.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private static void reportArrayAccessProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.outOfBoundsArrayAccesses().forEach(access -> {
      PsiExpression indexExpression = access.getIndexExpression();
      if (indexExpression != null) {
        holder.registerProblem(indexExpression, InspectionsBundle.message("dataflow.message.array.index.out.of.bounds"));
      }
    });
  }

  private static void reportArrayStoreProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.getArrayStoreProblems().forEach(
      (assignment, types) -> holder.registerProblem(assignment.getOperationSign(), InspectionsBundle
        .message("dataflow.message.arraystore", types.getFirst().getCanonicalText(), types.getSecond().getCanonicalText())));
  }

  private void reportMethodReferenceProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.getMethodReferenceResults().forEach((methodRef, dfaValue) -> {
      if (dfaValue instanceof DfaConstValue) {
        Object value = ((DfaConstValue)dfaValue).getValue();
        if(value instanceof Boolean) {
          holder.registerProblem(methodRef, InspectionsBundle.message("dataflow.message.constant.method.reference", value),
                                 createReplaceWithTrivialLambdaFix(value));
        }
      }
    });
  }

  private void reportUncheckedOptionalGet(ProblemsHolder holder,
                                          Map<PsiMethodCallExpression, ThreeState> calls,
                                          List<PsiExpression> qualifiers) {
    if (!REPORT_UNCHECKED_OPTIONALS) return;
    for (Map.Entry<PsiMethodCallExpression, ThreeState> entry : calls.entrySet()) {
      ThreeState state = entry.getValue();
      if (state != ThreeState.UNSURE) continue;
      PsiMethodCallExpression call = entry.getKey();
      PsiMethod method = call.resolveMethod();
      if (method == null) continue;
      PsiClass optionalClass = method.getContainingClass();
      if (optionalClass == null) continue;
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
      if (qualifier instanceof PsiMethodCallExpression &&
          qualifiers.stream().anyMatch(q -> PsiEquivalenceUtil.areElementsEquivalent(q, qualifier))) {
        // Conservatively do not report methodCall().get() cases if methodCall().isPresent() was found in the same method
        // without deep correspondence analysis
        continue;
      }
      LocalQuickFix fix = holder.isOnTheFly() ? new SetInspectionOptionFix(this, "REPORT_UNCHECKED_OPTIONALS", InspectionsBundle
        .message("inspection.data.flow.turn.off.unchecked.optional.get.quickfix"), false) : null;
      holder.registerProblem(getElementToHighlight(call),
                             InspectionsBundle.message("dataflow.message.optional.get.without.is.present", optionalClass.getName()),
                             fix);
    }
  }

  private void reportAlwaysReturnsNotNull(ProblemsHolder holder, PsiElement scope) {
    if (!(scope.getParent() instanceof PsiMethod)) return;

    PsiMethod method = (PsiMethod)scope.getParent();
    if (PsiUtil.canBeOverridden(method)) return;

    PsiAnnotation nullableAnno = NullableNotNullManager.getInstance(scope.getProject()).getNullableAnnotation(method, false);
    if (nullableAnno == null || !nullableAnno.isPhysical()) return;

    PsiJavaCodeReferenceElement annoName = nullableAnno.getNameReferenceElement();
    assert annoName != null;
    String msg = "@" + NullableStuffInspectionBase.getPresentableAnnoName(nullableAnno) +
                 " method '" + method.getName() + "' always returns a non-null value";
    LocalQuickFix[] fixes = {new AddNotNullAnnotationFix(method)};
    if (holder.isOnTheFly()) {
      fixes = ArrayUtil.append(fixes, new SetInspectionOptionFix(this, "REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL",
                                                                 InspectionsBundle
                                                                   .message(
                                                                     "inspection.data.flow.turn.off.nullable.returning.notnull.quickfix"),
                                                                 false));
    }
    holder.registerProblem(annoName, msg, fixes);
  }

  private static void reportAlwaysFailingCalls(ProblemsHolder holder,
                                               DataFlowInstructionVisitor visitor,
                                               HashSet<PsiElement> reportedAnchors) {
    visitor.getAlwaysFailingCalls().forEach((call, contracts) -> {
      if (TestUtils.isExceptionExpected(call)) return;
      PsiMethod method = call.resolveMethod();
      if (method != null && reportedAnchors.add(call)) {
        holder.registerProblem(getElementToHighlight(call), getContractMessage(contracts));
      }
    });
  }

  @NotNull
  private static String getContractMessage(List<MethodContract> contracts) {
    if (contracts.stream().allMatch(mc -> mc.getConditions().stream().allMatch(cv -> cv.isBoundCheckingCondition()))) {
      return InspectionsBundle.message("dataflow.message.contract.fail.index");
    }
    return InspectionsBundle.message("dataflow.message.contract.fail");
  }

  @NotNull private static PsiElement getElementToHighlight(@NotNull PsiCall call) {
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

  private void reportConstantPushes(StandardDataFlowRunner runner,
                                    ProblemsHolder holder,
                                    Set<PsiElement> reportedAnchors) {
    for (Instruction instruction : runner.getInstructions()) {
      if (instruction instanceof PushInstruction) {
        PsiExpression place = ((PushInstruction)instruction).getPlace();
        DfaValue value = ((PushInstruction)instruction).getValue();
        Object constant = value instanceof DfaConstValue ? ((DfaConstValue)value).getValue() : null;
        if (place instanceof PsiPolyadicExpression && constant instanceof Boolean && !isFlagCheck(place) && reportedAnchors.add(place)) {
          reportConstantCondition(holder, place, (Boolean)constant);
        }
      }
    }
  }

  private static void reportOptionalOfNullableImprovements(ProblemsHolder holder,
                                                           Set<PsiElement> reportedAnchors,
                                                           Map<MethodCallInstruction, ThreeState> nullArgs) {
    nullArgs.forEach((call, nullArg) -> {
      PsiElement arg = call.getArgumentAnchor(0);
      if (reportedAnchors.add(arg)) {
        switch (nullArg) {
          case YES:
            holder.registerProblem(arg, "Passing <code>null</code> argument to <code>Optional</code>",
                                   DfaOptionalSupport.createReplaceOptionalOfNullableWithEmptyFix(arg));
            break;
          case NO:
            holder.registerProblem(arg, "Passing a non-null argument to <code>Optional</code>",
                                   DfaOptionalSupport.createReplaceOptionalOfNullableWithOfFix(arg));
            break;
          default:
        }
      }
    });
  }

  private void reportConstantReferenceValues(ProblemsHolder holder, DataFlowInstructionVisitor visitor, Set<PsiElement> reportedAnchors) {
    for (Pair<PsiReferenceExpression, DfaConstValue> pair : visitor.getConstantReferenceValues()) {
      PsiReferenceExpression ref = pair.first;
      if (ref.getParent() instanceof PsiReferenceExpression || !reportedAnchors.add(ref)) {
        continue;
      }

      final Object value = pair.second.getValue();
      PsiVariable constant = pair.second.getConstant();
      final String presentableName = constant != null ? constant.getName() : String.valueOf(value);
      final String exprText = String.valueOf(value);
      if (presentableName == null || exprText == null) {
        continue;
      }

      LocalQuickFix[] fixes = {new ReplaceWithConstantValueFix(presentableName, exprText)};
      if (holder.isOnTheFly()) {
        fixes = ArrayUtil.append(fixes, new SetInspectionOptionFix(this, "REPORT_CONSTANT_REFERENCE_VALUES",
                                                                   InspectionsBundle
                                                                     .message("inspection.data.flow.turn.off.constant.references.quickfix"),
                                                                   false));
      }

      holder.registerProblem(ref, "Value <code>#ref</code> #loc is always '" + presentableName + "'", fixes);
    }
  }

  private void reportNullableArgumentsPassedToNonAnnotated(DataFlowInstructionVisitor visitor, ProblemsHolder holder, Set<PsiElement> reportedAnchors) {
    for (PsiElement expr : visitor.problems()
      .map(NullabilityProblemKind.passingNullableArgumentToNonAnnotatedParameter::asMyProblem).nonNull()
      .map(NullabilityProblem::getAnchor)) {
      if (reportedAnchors.contains(expr)) continue;

      if (expr.getParent() instanceof PsiMethodReferenceExpression) {
        holder.registerProblem(expr.getParent(), "Method reference argument might be null but passed to non annotated parameter");
        continue;
      }

      final String text = isNullLiteralExpression(expr)
                          ? "Passing <code>null</code> argument to non annotated parameter"
                          : "Argument <code>#ref</code> #loc might be null but passed to non annotated parameter";
      List<LocalQuickFix> fixes = createNPEFixes((PsiExpression)expr, (PsiExpression)expr, holder.isOnTheFly());
      final PsiElement parent = expr.getParent();
      if (parent instanceof PsiExpressionList) {
        final int idx = ArrayUtilRt.find(((PsiExpressionList)parent).getExpressions(), expr);
        if (idx > -1) {
          final PsiElement gParent = parent.getParent();
          if (gParent instanceof PsiCallExpression) {
            final PsiMethod psiMethod = ((PsiCallExpression)gParent).resolveMethod();
            if (psiMethod != null && psiMethod.getManager().isInProject(psiMethod) && AnnotationUtil.isAnnotatingApplicable(psiMethod)) {
              final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
              if (idx < parameters.length) {
                fixes.add(new AddNullableAnnotationFix(parameters[idx]));
                holder.registerProblem(expr, text, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
                reportedAnchors.add(expr);
              }
            }
          }
        }
      }

    }
  }

  private void reportCallMayProduceNpe(ProblemsHolder holder, PsiMethodCallExpression callExpression) {
    PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    List<LocalQuickFix> fixes = createNPEFixes(methodExpression.getQualifierExpression(), callExpression, holder.isOnTheFly());
    ContainerUtil.addIfNotNull(fixes, ReplaceWithObjectsEqualsFix.createFix(callExpression, methodExpression));

    PsiElement toHighlight = getElementToHighlight(callExpression);
    holder.registerProblem(toHighlight,
                           InspectionsBundle.message("dataflow.message.npe.method.invocation"),
                           fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private static void reportCastMayFail(ProblemsHolder holder, TypeCastInstruction instruction) {
    PsiTypeCastExpression typeCast = instruction.getCastExpression();
    PsiExpression operand = typeCast.getOperand();
    PsiTypeElement castType = typeCast.getCastType();
    assert castType != null;
    assert operand != null;
    holder.registerProblem(castType, InspectionsBundle.message("dataflow.message.cce", operand.getText()));
  }

  private void handleBranchingInstruction(ProblemsHolder holder,
                                          StandardInstructionVisitor visitor,
                                          Set<Instruction> trueSet,
                                          Set<Instruction> falseSet,
                                          HashSet<PsiElement> reportedAnchors,
                                          BranchingInstruction instruction) {
    PsiElement psiAnchor = instruction.getPsiAnchor();
    if (instruction instanceof InstanceofInstruction && visitor.isInstanceofRedundant((InstanceofInstruction)instruction)) {
      if (visitor.canBeNull((BinopInstruction)instruction)) {
        holder.registerProblem(psiAnchor,
                               InspectionsBundle.message("dataflow.message.redundant.instanceof"),
                               new RedundantInstanceofFix());
      }
      else {
        final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(psiAnchor, true);
        String message = InspectionsBundle.message(isAtRHSOfBooleanAnd(psiAnchor)
                                                   ? "dataflow.message.constant.condition.when.reached"
                                                   : "dataflow.message.constant.condition", Boolean.toString(true));
        holder.registerProblem(psiAnchor, message, localQuickFix);
      }
    }
    else if (psiAnchor instanceof PsiSwitchLabelStatement) {
      if (falseSet.contains(instruction)) {
        holder.registerProblem(psiAnchor,
                               InspectionsBundle.message("dataflow.message.unreachable.switch.label"));
      }
    }
    else if (psiAnchor != null && !reportedAnchors.contains(psiAnchor) && !isFlagCheck(psiAnchor)) {
      boolean evaluatesToTrue = trueSet.contains(instruction);
      final PsiElement parent = psiAnchor.getParent();
      if (parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getLExpression() == psiAnchor) {
        holder.registerProblem(
          psiAnchor,
          InspectionsBundle.message("dataflow.message.pointless.assignment.expression", Boolean.toString(evaluatesToTrue)),
          createConditionalAssignmentFixes(evaluatesToTrue, (PsiAssignmentExpression)parent, holder.isOnTheFly())
        );
      }
      else {
        reportConstantCondition(holder, psiAnchor, evaluatesToTrue);
      }
      reportedAnchors.add(psiAnchor);
    }
  }

  private void reportConstantCondition(ProblemsHolder holder, PsiElement psiAnchor, boolean evaluatesToTrue) {
    if (psiAnchor.getParent() instanceof PsiForeachStatement) {
      // highlighted for-each iterated value means evaluatesToTrue == "collection is always empty"
      if (!evaluatesToTrue) {
        // loop on always non-empty collection -- nothing to report
        return;
      }
      boolean array = psiAnchor instanceof PsiExpression && ((PsiExpression)psiAnchor).getType() instanceof PsiArrayType;
      holder.registerProblem(psiAnchor, array ?
                                        InspectionsBundle.message("dataflow.message.loop.on.empty.array") :
                                        InspectionsBundle.message("dataflow.message.loop.on.empty.collection"));
    }
    else if (psiAnchor instanceof PsiMethodReferenceExpression) {
      holder.registerProblem(psiAnchor, InspectionsBundle.message("dataflow.message.constant.method.reference", evaluatesToTrue),
                             createReplaceWithTrivialLambdaFix(evaluatesToTrue));
    }
    else {
      boolean isAssertion = isAssertionEffectively(psiAnchor, evaluatesToTrue);
      if (!DONT_REPORT_TRUE_ASSERT_STATEMENTS || !isAssertion) {
        List<LocalQuickFix> fixes = new ArrayList<>();
        ContainerUtil.addIfNotNull(fixes, createSimplifyBooleanExpressionFix(psiAnchor, evaluatesToTrue));
        if (isAssertion && holder.isOnTheFly()) {
          fixes.add(new SetInspectionOptionFix(this, "DONT_REPORT_TRUE_ASSERT_STATEMENTS",
                                               InspectionsBundle.message("inspection.data.flow.turn.off.true.asserts.quickfix"), true));
        }
        String message = InspectionsBundle.message(isAtRHSOfBooleanAnd(psiAnchor) ?
                                                   "dataflow.message.constant.condition.when.reached" :
                                                   "dataflow.message.constant.condition", Boolean.toString(evaluatesToTrue));
        holder.registerProblem(psiAnchor, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    }
  }

  protected LocalQuickFix[] createConditionalAssignmentFixes(boolean evaluatesToTrue, PsiAssignmentExpression parent, final boolean onTheFly) {
    return LocalQuickFix.EMPTY_ARRAY;
  }

  private void reportNullableArgument(ProblemsHolder holder, PsiElement expr) {
    if (expr.getParent() instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)expr.getParent();
      holder.registerProblem(methodRef, InspectionsBundle.message("dataflow.message.passing.nullable.argument.methodref"),
                             createMethodReferenceNPEFixes(methodRef).toArray(LocalQuickFix.EMPTY_ARRAY));
    }
    else {
      final String text = isNullLiteralExpression(expr)
               ? InspectionsBundle.message("dataflow.message.passing.null.argument")
               : InspectionsBundle.message("dataflow.message.passing.nullable.argument");
      List<LocalQuickFix> fixes = createNPEFixes((PsiExpression)expr, (PsiExpression)expr, holder.isOnTheFly());
      holder.registerProblem(expr, text, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  @Nullable
  private static PsiMethod getScopeMethod(PsiElement block) {
    PsiElement parent = block.getParent();
    if (parent instanceof PsiMethod) return (PsiMethod)parent;
    if (parent instanceof PsiLambdaExpression) return LambdaUtil.getFunctionalInterfaceMethod(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
    return null;
  }

  private void reportNullableReturns(DataFlowInstructionVisitor visitor,
                                     ProblemsHolder holder,
                                     Set<PsiElement> reportedAnchors,
                                     @NotNull PsiElement block) {
    final PsiMethod method = getScopeMethod(block);
    if (method == null || NullableStuffInspectionBase.isNullableNotInferred(method, true)) return;

    PsiAnnotation notNullAnno = NullableNotNullManager.getInstance(method.getProject()).getNotNullAnnotation(method, true);
    if (notNullAnno == null && (!SUGGEST_NULLABLE_ANNOTATIONS || block.getParent() instanceof PsiLambdaExpression)) return;

    PsiType returnType = method.getReturnType();
    // no warnings in void lambdas, where the expression is not returned anyway
    if (block instanceof PsiExpression && block.getParent() instanceof PsiLambdaExpression && PsiType.VOID.equals(returnType)) return;

    // no warnings for Void methods, where only null can be possibly returned
    if (returnType == null || returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID)) return;

    for (NullabilityProblem<PsiExpression> problem : visitor.problems().map(NullabilityProblemKind.nullableReturn::asMyProblem).nonNull()) {
      final PsiExpression expr = problem.getAnchor();
      if (!reportedAnchors.add(expr)) continue;

      if (notNullAnno != null) {
        String presentable = NullableStuffInspectionBase.getPresentableAnnoName(notNullAnno);
        final String text = isNullLiteralExpression(expr)
                            ? InspectionsBundle.message("dataflow.message.return.null.from.notnull", presentable)
                            : InspectionsBundle.message("dataflow.message.return.nullable.from.notnull", presentable);
        holder.registerProblem(expr, text);
      }
      else if (AnnotationUtil.isAnnotatingApplicable(expr)) {
        final NullableNotNullManager manager = NullableNotNullManager.getInstance(expr.getProject());
        final String defaultNullable = manager.getDefaultNullable();
        final String presentableNullable = StringUtil.getShortName(defaultNullable);
        final String text = isNullLiteralExpression(expr)
                            ? InspectionsBundle.message("dataflow.message.return.null.from.notnullable", presentableNullable)
                            : InspectionsBundle.message("dataflow.message.return.nullable.from.notnullable", presentableNullable);
        final LocalQuickFix[] fixes =
          PsiTreeUtil.getParentOfType(expr, PsiMethod.class, PsiLambdaExpression.class) instanceof PsiLambdaExpression
          ? LocalQuickFix.EMPTY_ARRAY
          : new LocalQuickFix[]{ new AnnotateMethodFix(defaultNullable, ArrayUtil.toStringArray(manager.getNotNulls()))};
        holder.registerProblem(expr, text, fixes);
      }
    }
  }

  private static boolean isAssertionEffectively(PsiElement psiAnchor, boolean evaluatesToTrue) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiAnchor.getParent());
    if (parent instanceof PsiAssertStatement) {
      return evaluatesToTrue;
    }
    if (parent instanceof PsiIfStatement && psiAnchor == ((PsiIfStatement)parent).getCondition()) {
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(((PsiIfStatement)parent).getThenBranch());
      if (thenBranch instanceof PsiThrowStatement) {
        return !evaluatesToTrue;
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
    PsiVariable field = (PsiVariable)resolved;
    return field.hasModifierProperty(PsiModifier.STATIC) && PsiUtil.isCompileTimeConstant(field) &&
           ArrayUtil.contains(field.getType(), types);
  }

  private static boolean isNullLiteralExpression(PsiElement expr) {
    return expr instanceof PsiExpression && ExpressionUtils.isNullLiteral((PsiExpression)expr);
  }

  @Nullable
  private LocalQuickFix createSimplifyBooleanExpressionFix(PsiElement element, final boolean value) {
    LocalQuickFixOnPsiElement fix = createSimplifyBooleanFix(element, value);
    if (fix == null) return null;
    final String text = fix.getText();
    return new LocalQuickFix() {
      @Override
      @NotNull
      public String getName() {
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
      @NotNull
      public String getFamilyName() {
        return InspectionsBundle.message("inspection.data.flow.simplify.boolean.expression.quickfix");
      }
    };
  }

  @NotNull
  protected static LocalQuickFix createSimplifyToAssignmentFix() {
    return new SimplifyToAssignmentFix();
  }

  protected LocalQuickFixOnPsiElement createSimplifyBooleanFix(PsiElement element, boolean value) {
    return null;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.data.flow.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }
}
