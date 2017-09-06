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

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInsight.intention.impl.AddNotNullAnnotationFix;
import com.intellij.codeInsight.intention.impl.AddNullableAnnotationFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.fix.RedundantInstanceofFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithConstantValueFix;
import com.intellij.codeInspection.dataFlow.fix.ReplaceWithObjectsEqualsFix;
import com.intellij.codeInspection.dataFlow.fix.SimplifyToAssignmentFix;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Stream;

@SuppressWarnings("ConditionalExpressionWithIdenticalBranches")
public class DataFlowInspectionBase extends BaseJavaBatchLocalInspectionTool {
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
        analyzeCodeBlock(aClass, holder, isOnTheFly);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        analyzeCodeBlock(method.getBody(), holder, isOnTheFly);
        analyzeNullLiteralMethodArguments(method, holder, isOnTheFly);
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

  private void analyzeNullLiteralMethodArguments(PsiMethod method, ProblemsHolder holder, boolean isOnTheFly) {
    if (REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER && isOnTheFly) {
      for (PsiParameter parameter : NullParameterConstraintChecker.checkMethodParameters(method)) {
        PsiIdentifier name = parameter.getNameIdentifier();
        if (name != null) {
          holder.registerProblem(name, InspectionsBundle.message("dataflow.method.fails.with.null.argument"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createNavigateToNullParameterUsagesFix(parameter));
        }
      }
    }
  }

  private void analyzeCodeBlock(@Nullable final PsiElement scope, ProblemsHolder holder, final boolean onTheFly) {
    if (scope == null) return;

    PsiClass containingClass = PsiTreeUtil.getParentOfType(scope, PsiClass.class, false);
    if (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass) && !(containingClass instanceof PsiEnumConstantInitializer)) return;

    final StandardDataFlowRunner dfaRunner =
      new StandardDataFlowRunner(TREAT_UNKNOWN_MEMBERS_AS_NULLABLE, !isInsideConstructorOrInitializer(scope));
    analyzeDfaWithNestedClosures(scope, holder, dfaRunner, Collections.singletonList(dfaRunner.createMemoryState()), onTheFly);
  }

  private static boolean isInsideConstructorOrInitializer(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClass) return true;
      element = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClassInitializer.class);
      if (element instanceof PsiClassInitializer) return true;
      if (element instanceof PsiMethod) {
        if (((PsiMethod)element).isConstructor()) return true;
        
        final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
        return !InheritanceUtil.processSupers(containingClass, true,
                                              psiClass -> !canCallMethodsInConstructors(psiClass, psiClass != containingClass));
        
      }
    }
    return false;
  }

  private static boolean canCallMethodsInConstructors(PsiClass aClass, boolean virtual) {
    for (PsiMethod constructor : aClass.getConstructors()) {
      if (!constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return true;

      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;

      for (PsiMethodCallExpression call : SyntaxTraverser.psiTraverser().withRoot(body).filter(PsiMethodCallExpression.class)) {
        PsiReferenceExpression methodExpression = call.getMethodExpression();
        if (methodExpression.textMatches(PsiKeyword.THIS) || methodExpression.textMatches(PsiKeyword.SUPER)) continue;
        if (!virtual) return true;
        
        PsiMethod target = call.resolveMethod();
        if (target != null && PsiUtil.canBeOverriden(target)) return true;
      }
    }

    return false;
  }

  private void analyzeDfaWithNestedClosures(PsiElement scope,
                                            ProblemsHolder holder,
                                            StandardDataFlowRunner dfaRunner,
                                            Collection<DfaMemoryState> initialStates, final boolean onTheFly) {
    final DataFlowInstructionVisitor visitor = new DataFlowInstructionVisitor();
    final RunnerResult rc = dfaRunner.analyzeMethod(scope, visitor, IGNORE_ASSERT_STATEMENTS, initialStates);
    if (rc == RunnerResult.OK) {
      createDescription(dfaRunner, holder, visitor, onTheFly, scope);

      MultiMap<PsiElement,DfaMemoryState> nestedClosures = dfaRunner.getNestedClosures();
      for (PsiElement closure : nestedClosures.keySet()) {
        analyzeDfaWithNestedClosures(closure, holder, dfaRunner, nestedClosures.get(closure), onTheFly);
      }
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

  protected LocalQuickFix createReplaceWithTrivialLambdaFix(Object value) {
    return null;
  }

  private void createDescription(StandardDataFlowRunner runner, ProblemsHolder holder, final DataFlowInstructionVisitor visitor, final boolean onTheFly, PsiElement scope) {
    Pair<Set<Instruction>, Set<Instruction>> constConditions = runner.getConstConditionalExpressions();
    Set<Instruction> trueSet = constConditions.getFirst();
    Set<Instruction> falseSet = constConditions.getSecond();

    ArrayList<Instruction> allProblems = new ArrayList<>();
    allProblems.addAll(trueSet);
    allProblems.addAll(falseSet);
    allProblems.addAll(visitor.myCCEInstructions);
    allProblems.addAll(ContainerUtil.filter(runner.getInstructions(), instruction1 -> instruction1 instanceof InstanceofInstruction && visitor.isInstanceofRedundant((InstanceofInstruction)instruction1)));

    HashSet<PsiElement> reportedAnchors = new HashSet<>();
    for (PsiElement element : visitor.getProblems(NullabilityProblem.callNPE)) {
      if (reportedAnchors.add(element)) {
        if (element instanceof PsiMethodReferenceExpression) {
          holder.registerProblem(element, InspectionsBundle.message("dataflow.message.npe.methodref.invocation"),
                                 createMethodReferenceNPEFixes((PsiMethodReferenceExpression)element).toArray(LocalQuickFix.EMPTY_ARRAY));
        }
        else {
          reportCallMayProduceNpe(holder, (PsiMethodCallExpression)element, holder.isOnTheFly());
        }
      }
    }
    for (PsiElement element : visitor.getProblems(NullabilityProblem.fieldAccessNPE)) {
      if (reportedAnchors.add(element)) {
        PsiElement parent = element.getParent();
        PsiElement fieldAccess = parent instanceof PsiArrayAccessExpression || parent instanceof PsiReferenceExpression ? parent : element;
        reportFieldAccessMayProduceNpe(holder, element, (PsiExpression)fieldAccess);
      }
    }

    for (Instruction instruction : allProblems) {
      if (instruction instanceof TypeCastInstruction &&
               reportedAnchors.add(((TypeCastInstruction)instruction).getCastExpression().getCastType())) {
        reportCastMayFail(holder, (TypeCastInstruction)instruction);
      }
      else if (instruction instanceof BranchingInstruction) {
        handleBranchingInstruction(holder, visitor, trueSet, falseSet, reportedAnchors, (BranchingInstruction)instruction, onTheFly);
      }
    }

    reportAlwaysFailingCalls(holder, visitor, reportedAnchors);

    reportConstantPushes(runner, holder, visitor, reportedAnchors);

    reportNullableFunctions(visitor, holder, reportedAnchors);
    reportNullableArguments(visitor, holder, reportedAnchors);
    reportNullableAssignments(visitor, holder, reportedAnchors, onTheFly);
    reportUnboxedNullables(visitor, holder, reportedAnchors);
    reportNullableReturns(visitor, holder, reportedAnchors, scope);
    if (SUGGEST_NULLABLE_ANNOTATIONS) {
      reportNullableArgumentsPassedToNonAnnotated(visitor, holder, reportedAnchors);
    }

    reportOptionalOfNullableImprovements(holder, reportedAnchors, visitor.getOfNullableCalls());

    reportUncheckedOptionalGet(holder, visitor.getOptionalCalls(), visitor.getOptionalQualifiers());

    visitor.getBooleanCalls().forEach((call, state) -> {
      if (state != ThreeState.UNSURE && reportedAnchors.add(call)) {
        reportConstantCondition(holder, visitor, call, state.toBoolean());
      }
    });

    reportMethodReferenceProblems(holder, visitor);

    reportArrayAccessProblems(holder, visitor);

    if (REPORT_CONSTANT_REFERENCE_VALUES) {
      reportConstantReferenceValues(holder, visitor, reportedAnchors);
    }

    if (REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL && visitor.isAlwaysReturnsNotNull(runner.getInstructions())) {
      reportAlwaysReturnsNotNull(holder, scope);
    }
  }

  private static void reportArrayAccessProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.outOfBoundsArrayAccesses().forEach(access -> {
      PsiExpression indexExpression = access.getIndexExpression();
      if (indexExpression != null) {
        holder.registerProblem(indexExpression, InspectionsBundle.message("dataflow.message.array.index.out.of.bounds"));
      }
    });
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
      holder.registerProblem(getElementToHighlight(call),
                             InspectionsBundle.message("dataflow.message.optional.get.without.is.present", optionalClass.getName()));
    }
  }

  private static void reportAlwaysReturnsNotNull(ProblemsHolder holder, PsiElement scope) {
    if (!(scope.getParent() instanceof PsiMethod)) return;

    PsiMethod method = (PsiMethod)scope.getParent();
    if (PsiUtil.canBeOverriden(method)) return;

    PsiAnnotation nullableAnno = NullableNotNullManager.getInstance(scope.getProject()).getNullableAnnotation(method, false);
    if (nullableAnno == null || !nullableAnno.isPhysical()) return;

    PsiJavaCodeReferenceElement annoName = nullableAnno.getNameReferenceElement();
    assert annoName != null;
    String msg = "@" + NullableStuffInspectionBase.getPresentableAnnoName(nullableAnno) +
                 " method '" + method.getName() + "' always returns a non-null value";
    holder.registerProblem(annoName, msg, new AddNotNullAnnotationFix(method));
  }

  private static void reportAlwaysFailingCalls(ProblemsHolder holder,
                                               DataFlowInstructionVisitor visitor,
                                               HashSet<PsiElement> reportedAnchors) {
    if (ProjectFileIndex.SERVICE.getInstance(holder.getProject()).isInTestSourceContent(holder.getFile().getViewProvider().getVirtualFile())) {
      return;
    }

    for (PsiCall call : visitor.getAlwaysFailingCalls()) {
      PsiMethod method = call.resolveMethod();
      if (method != null && reportedAnchors.add(call)) {
        holder.registerProblem(getElementToHighlight(call), "The call to '#ref' always fails, according to its method contracts");
      }
    }
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
                                    DataFlowInstructionVisitor visitor,
                                    Set<PsiElement> reportedAnchors) {
    for (Instruction instruction : runner.getInstructions()) {
      if (instruction instanceof PushInstruction) {
        PsiExpression place = ((PushInstruction)instruction).getPlace();
        DfaValue value = ((PushInstruction)instruction).getValue();
        Object constant = value instanceof DfaConstValue ? ((DfaConstValue)value).getValue() : null;
        if (place instanceof PsiPolyadicExpression && constant instanceof Boolean && !isFlagCheck(place) && reportedAnchors.add(place)) {
          reportConstantCondition(holder, visitor, place, (Boolean)constant);
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

  private static void reportConstantReferenceValues(ProblemsHolder holder, StandardInstructionVisitor visitor, Set<PsiElement> reportedAnchors) {
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

      holder.registerProblem(ref, "Value <code>#ref</code> #loc is always '" + presentableName + "'",
                             new ReplaceWithConstantValueFix(presentableName, exprText));
    }
  }

  private void reportNullableArgumentsPassedToNonAnnotated(DataFlowInstructionVisitor visitor, ProblemsHolder holder, Set<PsiElement> reportedAnchors) {
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter)) {
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

  private void reportCallMayProduceNpe(ProblemsHolder holder, PsiMethodCallExpression callExpression, boolean onTheFly) {
    PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    List<LocalQuickFix> fixes = createNPEFixes(methodExpression.getQualifierExpression(), callExpression, onTheFly);
    ContainerUtil.addIfNotNull(fixes, ReplaceWithObjectsEqualsFix.createFix(callExpression, methodExpression));

    PsiElement toHighlight = getElementToHighlight(callExpression);
    holder.registerProblem(toHighlight,
                           InspectionsBundle.message("dataflow.message.npe.method.invocation"),
                           fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private void reportFieldAccessMayProduceNpe(ProblemsHolder holder, PsiElement elementToAssert, @NotNull PsiExpression expression) {
    LocalQuickFix[] fix = createNPEFixes((PsiExpression)elementToAssert, expression, holder.isOnTheFly()).toArray(LocalQuickFix.EMPTY_ARRAY);
    if (expression instanceof PsiArrayAccessExpression) {
      holder.registerProblem(expression,
                             InspectionsBundle.message("dataflow.message.npe.array.access"),
                             fix);
    }
    else {
      assert elementToAssert != null;
      //noinspection ConditionalExpressionWithIdenticalBranches
      holder.registerProblem(elementToAssert,
                             expression.textMatches("null")
                             ? InspectionsBundle.message("dataflow.message.npe.field.access.sure")
                             : InspectionsBundle.message("dataflow.message.npe.field.access"),
                             fix);
    }
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
                                          Set<Instruction> falseSet, HashSet<PsiElement> reportedAnchors, BranchingInstruction instruction, final boolean onTheFly) {
    PsiElement psiAnchor = instruction.getPsiAnchor();
    if (instruction instanceof InstanceofInstruction && visitor.isInstanceofRedundant((InstanceofInstruction)instruction)) {
      if (visitor.canBeNull((BinopInstruction)instruction)) {
        holder.registerProblem(psiAnchor,
                               InspectionsBundle.message("dataflow.message.redundant.instanceof"),
                               new RedundantInstanceofFix());
      }
      else {
        final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(psiAnchor, true);
        holder.registerProblem(psiAnchor,
                               InspectionsBundle.message(isAtRHSOfBooleanAnd(psiAnchor)
                                                         ? "dataflow.message.constant.condition.when.reached" : "dataflow.message.constant.condition", Boolean.toString(true)),
                               localQuickFix == null ? null : new LocalQuickFix[]{localQuickFix});
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
          createConditionalAssignmentFixes(evaluatesToTrue, (PsiAssignmentExpression)parent, onTheFly)
        );
      }
      else {
        reportConstantCondition(holder, visitor, psiAnchor, evaluatesToTrue);
      }
      reportedAnchors.add(psiAnchor);
    }
  }

  private void reportConstantCondition(ProblemsHolder holder,
                                       StandardInstructionVisitor visitor,
                                       PsiElement psiAnchor,
                                       boolean evaluatesToTrue) {
    if (!skipReportingConstantCondition(visitor, psiAnchor, evaluatesToTrue)) {
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
      else {
        final LocalQuickFix fix = createSimplifyBooleanExpressionFix(psiAnchor, evaluatesToTrue);
        String message = InspectionsBundle.message(isAtRHSOfBooleanAnd(psiAnchor) ?
                                                   "dataflow.message.constant.condition.when.reached" :
                                                   "dataflow.message.constant.condition", Boolean.toString(evaluatesToTrue));
        holder.registerProblem(psiAnchor, message, fix == null ? null : new LocalQuickFix[]{fix});
      }
    }
  }

  protected LocalQuickFix[] createConditionalAssignmentFixes(boolean evaluatesToTrue, PsiAssignmentExpression parent, final boolean onTheFly) {
    return LocalQuickFix.EMPTY_ARRAY;
  }

  private boolean skipReportingConstantCondition(StandardInstructionVisitor visitor, PsiElement psiAnchor, boolean evaluatesToTrue) {
    return DONT_REPORT_TRUE_ASSERT_STATEMENTS && isAssertionEffectively(psiAnchor, evaluatesToTrue) ||
           visitor.silenceConstantCondition(psiAnchor);
  }

  private static void reportNullableFunctions(DataFlowInstructionVisitor visitor, ProblemsHolder holder, Set<PsiElement> reportedAnchors) {
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.nullableFunctionReturn)) {
      if (!reportedAnchors.add(expr)) continue;
      holder.registerProblem(expr, InspectionsBundle.message("dataflow.message.return.nullable.from.notnull.function"));
    }
  }

  private void reportNullableArguments(DataFlowInstructionVisitor visitor, ProblemsHolder holder, Set<PsiElement> reportedAnchors) {
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.passingNullableToNotNullParameter)) {
      if (!reportedAnchors.add(expr)) continue;

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
  }

  private void reportNullableAssignments(DataFlowInstructionVisitor visitor,
                                         ProblemsHolder holder,
                                         Set<PsiElement> reportedAnchors,
                                         boolean onTheFly) {
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.assigningToNotNull)) {
      if (!reportedAnchors.add(expr)) continue;
      assert expr instanceof PsiExpression;

      final String text = isNullLiteralExpression(expr)
                          ? InspectionsBundle.message("dataflow.message.assigning.null")
                          : InspectionsBundle.message("dataflow.message.assigning.nullable");
      holder.registerProblem(expr, text,
                             createNPEFixes((PsiExpression)expr, (PsiExpression)expr, onTheFly).toArray(LocalQuickFix.EMPTY_ARRAY));
    }
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.storingToNotNullArray)) {
      if (!reportedAnchors.add(expr)) continue;
      assert expr instanceof PsiExpression;

      final String text = isNullLiteralExpression(expr)
                          ? InspectionsBundle.message("dataflow.message.storing.array.null")
                          : InspectionsBundle.message("dataflow.message.storing.array.nullable");
      holder.registerProblem(expr, text,
                             createNPEFixes((PsiExpression)expr, (PsiExpression)expr, onTheFly).toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private static void reportUnboxedNullables(DataFlowInstructionVisitor visitor, ProblemsHolder holder, Set<PsiElement> reportedAnchors) {
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.unboxingNullable)) {
      if (!reportedAnchors.add(expr)) continue;
      holder.registerProblem(expr, InspectionsBundle.message("dataflow.message.unboxing"));
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

    for (PsiElement statement : visitor.getProblems(NullabilityProblem.nullableReturn)) {
      assert statement instanceof PsiExpression;
      final PsiExpression expr = (PsiExpression)statement;
      if (!reportedAnchors.add(expr)) continue;

      if (notNullAnno != null) {
        String presentable = NullableStuffInspectionBase.getPresentableAnnoName(notNullAnno);
        final String text = isNullLiteralExpression(expr)
                            ? InspectionsBundle.message("dataflow.message.return.null.from.notnull", presentable)
                            : InspectionsBundle.message("dataflow.message.return.nullable.from.notnull", presentable);
        holder.registerProblem(expr, text);
      }
      else if (AnnotationUtil.isAnnotatingApplicable(statement)) {
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
    PsiElement parent = psiAnchor.getParent();
    if (parent instanceof PsiAssertStatement) {
      return evaluatesToTrue;
    }
    if (parent instanceof PsiIfStatement && psiAnchor == ((PsiIfStatement)parent).getCondition()) {
      PsiStatement thenBranch = ((PsiIfStatement)parent).getThenBranch();
      if (thenBranch instanceof PsiThrowStatement) {
        return !evaluatesToTrue;
      }
      if (thenBranch instanceof PsiBlockStatement) {
        PsiStatement[] statements = ((PsiBlockStatement)thenBranch).getCodeBlock().getStatements();
        if (statements.length == 1 && statements[0] instanceof PsiThrowStatement) {
          return !evaluatesToTrue;
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
    PsiVariable field = (PsiVariable)resolved;
    return field.hasModifierProperty(PsiModifier.STATIC) && PsiUtil.isCompileTimeConstant(field) &&
           ArrayUtil.contains(field.getType(), types);
  }

  private static boolean isNullLiteralExpression(PsiElement expr) {
    if (expr instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expr;
      return PsiType.NULL.equals(literalExpression.getType());
    }
    return false;
  }

  @Nullable
  private static LocalQuickFix createSimplifyBooleanExpressionFix(PsiElement element, final boolean value) {
    SimplifyBooleanExpressionFix fix = createIntention(element, value);
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
        final SimplifyBooleanExpressionFix fix = createIntention(psiElement, value);
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

  private static SimplifyBooleanExpressionFix createIntention(PsiElement element, boolean value) {
    if (!(element instanceof PsiExpression)) return null;
    if (PsiTreeUtil.findChildOfType(element, PsiAssignmentExpression.class) != null) return null;

    final PsiExpression expression = (PsiExpression)element;
    while (element.getParent() instanceof PsiExpression) {
      element = element.getParent();
    }
    final SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expression, value);
    // simplify intention already active
    if (!fix.isAvailable() ||
        SimplifyBooleanExpressionFix.canBeSimplified((PsiExpression)element)) {
      return null;
    }
    return fix;
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

  private static class DataFlowInstructionVisitor extends StandardInstructionVisitor {
    private final MultiMap<NullabilityProblem, PsiElement> myProblems = new MultiMap<>();
    private final Map<Pair<NullabilityProblem, PsiElement>, StateInfo> myStateInfos = ContainerUtil.newHashMap();
    private final Set<Instruction> myCCEInstructions = ContainerUtil.newHashSet();
    private final Map<MethodCallInstruction, Boolean> myFailingCalls = new HashMap<>();
    private final Map<PsiMethodCallExpression, ThreeState> myOptionalCalls = new HashMap<>();
    private final Map<PsiMethodCallExpression, ThreeState> myBooleanCalls = new HashMap<>();
    private final Map<MethodCallInstruction, ThreeState> myOfNullableCalls = new HashMap<>();
    private final Map<PsiMethodReferenceExpression, DfaValue> myMethodReferenceResults = new HashMap<>();
    private final Map<PsiArrayAccessExpression, ThreeState> myOutOfBoundsArrayAccesses = new HashMap<>();
    private final List<PsiExpression> myOptionalQualifiers = new ArrayList<>();
    private boolean myAlwaysReturnsNotNull = true;

    @Override
    protected void onInstructionProducesCCE(TypeCastInstruction instruction) {
      myCCEInstructions.add(instruction);
    }

    Collection<PsiElement> getProblems(final NullabilityProblem kind) {
      return ContainerUtil.filter(myProblems.get(kind), psiElement -> {
        StateInfo info = myStateInfos.get(Pair.create(kind, psiElement));
        // non-ephemeral NPE should be reported
        // ephemeral NPE should also be reported if only ephemeral states have reached a particular problematic instruction
        //  (e.g. if it's inside "if (var == null)" check after contract method invocation
        return info.normalNpe || info.ephemeralNpe && !info.normalOk;
      });
    }

    Map<PsiMethodCallExpression, ThreeState> getOptionalCalls() {
      return myOptionalCalls;
    }

    Map<MethodCallInstruction, ThreeState> getOfNullableCalls() {
      return myOfNullableCalls;
    }

    Map<PsiMethodCallExpression, ThreeState> getBooleanCalls() {
      return myBooleanCalls;
    }

    Map<PsiMethodReferenceExpression, DfaValue> getMethodReferenceResults() {
      return myMethodReferenceResults;
    }

    Stream<PsiArrayAccessExpression> outOfBoundsArrayAccesses() {
      return StreamEx.ofKeys(myOutOfBoundsArrayAccesses, ThreeState.YES::equals);
    }

    List<PsiExpression> getOptionalQualifiers() {
      return myOptionalQualifiers;
    }

    Collection<PsiCall> getAlwaysFailingCalls() {
      return StreamEx.ofKeys(myFailingCalls, v -> v).map(MethodCallInstruction::getCallExpression).toList();
    }

    boolean isAlwaysReturnsNotNull(Instruction[] instructions) {
      return myAlwaysReturnsNotNull &&
             ContainerUtil.exists(instructions, i -> i instanceof ReturnInstruction && ((ReturnInstruction)i).getAnchor() instanceof PsiReturnStatement);
    }

    @Override
    public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction,
                                                 DataFlowRunner runner,
                                                 DfaMemoryState memState) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(instruction.getCallExpression(), PsiMethodCallExpression.class);
      if (call != null) {
        String methodName = call.getMethodExpression().getReferenceName();
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
        if (qualifier != null && TypeUtils.isOptional(qualifier.getType())) {
          if ("isPresent".equals(methodName) && qualifier instanceof PsiMethodCallExpression) {
            myOptionalQualifiers.add(qualifier);
          }
          else if (DfaOptionalSupport.isOptionalGetMethodName(methodName)) {
            Boolean fact = memState.getValueFact(DfaFactType.OPTIONAL_PRESENCE, memState.peek());
            ThreeState state = fact == null ? ThreeState.UNSURE : ThreeState.fromBoolean(fact);
            myOptionalCalls.merge(call, state, ThreeState::merge);
          }
        }
      }
      if (instruction.matches(DfaOptionalSupport.OPTIONAL_OF_NULLABLE)) {
        DfaValue arg = memState.peek();
        ThreeState nullArg = memState.isNull(arg) ? ThreeState.YES : memState.isNotNull(arg) ? ThreeState.NO : ThreeState.UNSURE;
        myOfNullableCalls.merge(instruction, nullArg, ThreeState::merge);
      }
      DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
      if (hasNonTrivialFailingContracts(instruction)) {
        DfaConstValue fail = runner.getFactory().getConstFactory().getContractFail();
        boolean allFail = Arrays.stream(states).allMatch(s -> s.getMemoryState().peek() == fail);
        myFailingCalls.merge(instruction, allFail, Boolean::logicalAnd);
      }
      handleBooleanCalls(instruction, states);
      return states;
    }

    void handleBooleanCalls(MethodCallInstruction instruction, DfaInstructionState[] states) {
      if (!hasNonTrivialBooleanContracts(instruction)) return;
      PsiMethod method = instruction.getTargetMethod();
      if (method == null || !ControlFlowAnalyzer.isPure(method)) return;
      PsiMethodCallExpression call = ObjectUtils.tryCast(instruction.getCallExpression(), PsiMethodCallExpression.class);
      if (call == null || myBooleanCalls.get(call) == ThreeState.UNSURE) return;
      PsiElement parent = call.getParent();
      if (parent instanceof PsiExpressionStatement) return;
      if (parent instanceof PsiLambdaExpression &&
          PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent))) {
        return;
      }
      for (DfaInstructionState s : states) {
        DfaValue val = s.getMemoryState().peek();
        ThreeState state = ThreeState.UNSURE;
        if (val instanceof DfaConstValue) {
          Object value = ((DfaConstValue)val).getValue();
          if (value instanceof Boolean) {
            state = ThreeState.fromBoolean((Boolean)value);
          }
        }
        myBooleanCalls.merge(call, state, ThreeState::merge);
      }
    }

    @Override
    protected void processArrayAccess(PsiArrayAccessExpression expression, boolean alwaysOutOfBounds) {
      myOutOfBoundsArrayAccesses.merge(expression, ThreeState.fromBoolean(alwaysOutOfBounds), ThreeState::merge);
    }

    @Override
    protected void processMethodReferenceResult(PsiMethodReferenceExpression methodRef,
                                                List<? extends MethodContract> contracts,
                                                DfaValue res) {
      if(contracts.isEmpty() || !contracts.get(0).isTrivial()) {
        // Do not track if method reference may have different results
        myMethodReferenceResults.merge(methodRef, res, (a, b) -> a == b ? a : DfaUnknownValue.getInstance());
      }
    }

    private static boolean hasNonTrivialFailingContracts(MethodCallInstruction instruction) {
      List<MethodContract> contracts = instruction.getContracts();
      return !contracts.isEmpty() && contracts.stream().anyMatch(
        contract -> contract.getReturnValue() == MethodContract.ValueConstraint.THROW_EXCEPTION && !contract.isTrivial());
    }

    private static boolean hasNonTrivialBooleanContracts(MethodCallInstruction instruction) {
      if (CustomMethodHandlers.find(instruction) != null) return true;
      List<MethodContract> contracts = instruction.getContracts();
      return !contracts.isEmpty() && contracts.stream().anyMatch(
        contract -> (contract.getReturnValue() == MethodContract.ValueConstraint.FALSE_VALUE ||
                     contract.getReturnValue() == MethodContract.ValueConstraint.TRUE_VALUE)
                    && !contract.isTrivial());
    }

    @Override
    protected boolean checkNotNullable(DfaMemoryState state, DfaValue value, NullabilityProblem problem, PsiElement anchor) {
      if (problem == NullabilityProblem.nullableReturn && !state.isNotNull(value)) {
        myAlwaysReturnsNotNull = false;
      }

      boolean ok = super.checkNotNullable(state, value, problem, anchor);
      if (!ok && anchor != null) {
        myProblems.putValue(problem, anchor);
      }
      Pair<NullabilityProblem, PsiElement> key = Pair.create(problem, anchor);
      StateInfo info = myStateInfos.computeIfAbsent(key, k -> new StateInfo());
      if (state.isEphemeral() && !ok) {
        info.ephemeralNpe = true;
      } else if (!state.isEphemeral()) {
        if (ok) info.normalOk = true;
        else info.normalNpe = true;
      }
      return ok;
    }
    
    private static class StateInfo {
      boolean ephemeralNpe;
      boolean normalNpe;
      boolean normalOk;
    }
  }
}
