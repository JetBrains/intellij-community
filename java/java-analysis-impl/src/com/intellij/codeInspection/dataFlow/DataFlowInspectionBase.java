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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInsight.intention.impl.AddNullableAnnotationFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class DataFlowInspectionBase extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowInspection");
  @NonNls private static final String SHORT_NAME = "ConstantConditions";
  public boolean SUGGEST_NULLABLE_ANNOTATIONS;
  public boolean DONT_REPORT_TRUE_ASSERT_STATEMENTS;
  public boolean TREAT_UNKNOWN_MEMBERS_AS_NULLABLE;
  public boolean IGNORE_ASSERT_STATEMENTS;
  public boolean REPORT_CONSTANT_REFERENCE_VALUES = true;

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
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(PsiField field) {
        analyzeCodeBlock(field, holder, isOnTheFly);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        analyzeCodeBlock(method.getBody(), holder, isOnTheFly);
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        analyzeCodeBlock(initializer.getBody(), holder, isOnTheFly);
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
        PsiExpression condition = statement.getCondition();
        if (BranchingInstruction.isBoolConst(condition)) {
          LocalQuickFix fix = createSimplifyBooleanExpressionFix(condition, condition.textMatches(PsiKeyword.TRUE));
          holder.registerProblem(condition, "Condition is always " + condition.getText(), fix);
        }
      }

    };
  }

  private void analyzeCodeBlock(@Nullable final PsiElement scope, ProblemsHolder holder, final boolean onTheFly) {
    if (scope == null) return;

    PsiClass containingClass = PsiTreeUtil.getParentOfType(scope, PsiClass.class);
    if (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass) && !(containingClass instanceof PsiEnumConstantInitializer)) return;

    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner(TREAT_UNKNOWN_MEMBERS_AS_NULLABLE, !isInsideConstructorOrInitializer(
      scope)) {
      @Override
      protected boolean shouldCheckTimeLimit() {
        if (!onTheFly) return false;
        return super.shouldCheckTimeLimit();
      }
    };
    analyzeDfaWithNestedClosures(scope, holder, dfaRunner, Collections.singletonList(dfaRunner.createMemoryState()), onTheFly);
  }

  private static boolean isInsideConstructorOrInitializer(PsiElement element) {
    while (element != null) {
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
      if (scope.getParent() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)scope.getParent();
        final PsiIdentifier name = method.getNameIdentifier();
        if (name != null) { // Might be null for synthetic methods like JSP page.
          holder.registerProblem(name, InspectionsBundle.message("dataflow.too.complex"), ProblemHighlightType.WEAK_WARNING);
        }
      }
    }
  }

  @Nullable
  private LocalQuickFix[] createNPEFixes(PsiExpression qualifier, PsiExpression expression, boolean onTheFly) {
    if (qualifier == null || expression == null) return null;
    if (qualifier instanceof PsiMethodCallExpression) return null;

    try {
      final List<LocalQuickFix> fixes = new SmartList<>();

      if (isVolatileFieldReference(qualifier)) {
        ContainerUtil.addIfNotNull(fixes, createIntroduceVariableFix(qualifier));
      }
      else if (!(qualifier instanceof PsiLiteralExpression && ((PsiLiteralExpression)qualifier).getValue() == null))  {
        if (PsiUtil.getLanguageLevel(qualifier).isAtLeast(LanguageLevel.JDK_1_4)) {
          final Project project = qualifier.getProject();
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
          final PsiBinaryExpression binary = (PsiBinaryExpression)elementFactory.createExpressionFromText("a != null", null);
          binary.getLOperand().replace(qualifier);
          ContainerUtil.addIfNotNull(fixes, createAssertFix(binary, expression));
        }

        addSurroundWithIfFix(qualifier, fixes, onTheFly);

        if (ReplaceWithTernaryOperatorFix.isAvailable(qualifier, expression)) {
          fixes.add(new ReplaceWithTernaryOperatorFix(qualifier));
        }
      }

      ContainerUtil.addIfNotNull(fixes, DfaOptionalSupport.registerReplaceOptionalOfWithOfNullableFix(qualifier));
      return fixes.isEmpty() ? null : fixes.toArray(new LocalQuickFix[fixes.size()]);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @Nullable
  protected LocalQuickFix createIntroduceVariableFix(PsiExpression expression) {
    return null;
  }

  private static boolean isVolatileFieldReference(PsiExpression qualifier) {
    PsiElement target = qualifier instanceof PsiReferenceExpression ? ((PsiReferenceExpression)qualifier).resolve() : null;
    return target instanceof PsiField && ((PsiField)target).hasModifierProperty(PsiModifier.VOLATILE);
  }

  protected LocalQuickFix createAssertFix(PsiBinaryExpression binary, PsiExpression expression) {
    return null;
  }

  protected void addSurroundWithIfFix(PsiExpression qualifier, List<LocalQuickFix> fixes, boolean onTheFly) {
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
        reportCallMayProduceNpe(holder, (PsiMethodCallExpression)element, holder.isOnTheFly());
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

    reportConstantPushes(runner, holder, visitor, reportedAnchors);

    reportNullableArguments(visitor, holder, reportedAnchors);
    reportNullableAssignments(visitor, holder, reportedAnchors);
    reportUnboxedNullables(visitor, holder, reportedAnchors);
    reportNullableReturns(visitor, holder, reportedAnchors, scope);
    if (SUGGEST_NULLABLE_ANNOTATIONS) {
      reportNullableArgumentsPassedToNonAnnotated(visitor, holder, reportedAnchors);
    }

    reportOptionalOfNullableImprovements(holder, reportedAnchors, runner.getInstructions());


    if (REPORT_CONSTANT_REFERENCE_VALUES) {
      reportConstantReferenceValues(holder, visitor, reportedAnchors);
    }
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
        if (place instanceof PsiPolyadicExpression && constant instanceof Boolean && reportedAnchors.add(place)) {
          reportConstantCondition(holder, visitor, place, (Boolean)constant);
        }
      }
    }
  }

  private static void reportOptionalOfNullableImprovements(ProblemsHolder holder, Set<PsiElement> reportedAnchors, Instruction[] instructions) {
    for (Instruction instruction : instructions) {
      if (instruction instanceof MethodCallInstruction) {
        final PsiExpression[] args = ((MethodCallInstruction)instruction).getArgs();
        if (args.length != 1) continue;

        final PsiExpression expr = args[0];

        if (((MethodCallInstruction)instruction).isOptionalAlwaysNullProblem()) {
          if (!reportedAnchors.add(expr)) continue;
          holder.registerProblem(expr, "Passing <code>null</code> argument to <code>Optional</code>",
                                 DfaOptionalSupport.createReplaceOptionalOfNullableWithEmptyFix(expr));
        }
        else if (((MethodCallInstruction)instruction).isOptionalAlwaysNotNullProblem()) {
          if (!reportedAnchors.add(expr)) continue;
          holder.registerProblem(expr, "Passing a non-null argument to <code>Optional</code>",
                                 DfaOptionalSupport.createReplaceOptionalOfNullableWithOfFix());
        }
        
      }
    }
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

      holder.registerProblem(ref, "Value <code>#ref</code> #loc is always '" + presentableName + "'", new LocalQuickFix() {
        @NotNull
        @Override
        public String getName() {
          return "Replace with '" + presentableName + "'";
        }

        @NotNull
        @Override
        public String getFamilyName() {
          return "Replace with constant value";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
          if (!FileModificationService.getInstance().preparePsiElementsForWrite(descriptor.getPsiElement())) {
            return;
          }

          PsiElement problemElement = descriptor.getPsiElement();
          if (problemElement == null) return;

          PsiMethodCallExpression call = problemElement.getParent() instanceof PsiExpressionList &&
                                         problemElement.getParent().getParent() instanceof PsiMethodCallExpression ?
                                         (PsiMethodCallExpression)problemElement.getParent().getParent() :
                                         null;
          PsiMethod targetMethod = call == null ? null : call.resolveMethod();

          JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
          problemElement.replace(facade.getElementFactory().createExpressionFromText(exprText, null));

          if (targetMethod != null) {
            ExtractMethodUtil.addCastsToEnsureResolveTarget(targetMethod, call);
          }
        }
      });
    }
  }

  private void reportNullableArgumentsPassedToNonAnnotated(DataFlowInstructionVisitor visitor, ProblemsHolder holder, Set<PsiElement> reportedAnchors) {
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter)) {
      if (reportedAnchors.contains(expr)) continue;

      final String text = isNullLiteralExpression(expr)
                          ? "Passing <code>null</code> argument to non annotated parameter"
                          : "Argument <code>#ref</code> #loc might be null but passed to non annotated parameter";
      LocalQuickFix[] fixes = createNPEFixes((PsiExpression)expr, (PsiExpression)expr, holder.isOnTheFly());
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
                final AddNullableAnnotationFix addNullableAnnotationFix = new AddNullableAnnotationFix(parameters[idx]);
                fixes = fixes == null ? new LocalQuickFix[]{addNullableAnnotationFix} : ArrayUtil.append(fixes, addNullableAnnotationFix);
                holder.registerProblem(expr, text, fixes);
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
    LocalQuickFix[] fix = createNPEFixes(methodExpression.getQualifierExpression(), callExpression, onTheFly);

    PsiElement toHighlight = methodExpression.getReferenceNameElement();
    if (toHighlight == null) toHighlight = methodExpression;
    holder.registerProblem(toHighlight,
                           InspectionsBundle.message("dataflow.message.npe.method.invocation"),
                           fix);
  }

  private void reportFieldAccessMayProduceNpe(ProblemsHolder holder, PsiElement elementToAssert, PsiExpression expression) {
    if (expression instanceof PsiArrayAccessExpression) {
      LocalQuickFix[] fix = createNPEFixes((PsiExpression)elementToAssert, expression, holder.isOnTheFly());
      holder.registerProblem(expression,
                             InspectionsBundle.message("dataflow.message.npe.array.access"),
                             fix);
    }
    else {
      LocalQuickFix[] fix = createNPEFixes((PsiExpression)elementToAssert, expression, holder.isOnTheFly());
      assert elementToAssert != null;
      holder.registerProblem(elementToAssert,
                             InspectionsBundle.message("dataflow.message.npe.field.access"),
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
      final LocalQuickFix fix = createSimplifyBooleanExpressionFix(psiAnchor, evaluatesToTrue);
      String message = InspectionsBundle.message(isAtRHSOfBooleanAnd(psiAnchor) ?
                                                 "dataflow.message.constant.condition.when.reached" :
                                                 "dataflow.message.constant.condition", Boolean.toString(evaluatesToTrue));
      holder.registerProblem(psiAnchor, message, fix == null ? null : new LocalQuickFix[]{fix});
    }
  }

  protected LocalQuickFix[] createConditionalAssignmentFixes(boolean evaluatesToTrue, PsiAssignmentExpression parent, final boolean onTheFly) {
    return LocalQuickFix.EMPTY_ARRAY;
  }

  private boolean skipReportingConstantCondition(StandardInstructionVisitor visitor, PsiElement psiAnchor, boolean evaluatesToTrue) {
    return DONT_REPORT_TRUE_ASSERT_STATEMENTS && isAssertionEffectively(psiAnchor, evaluatesToTrue) ||
           visitor.silenceConstantCondition(psiAnchor);
  }

  private void reportNullableArguments(DataFlowInstructionVisitor visitor, ProblemsHolder holder, Set<PsiElement> reportedAnchors) {
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.passingNullableToNotNullParameter)) {
      if (!reportedAnchors.add(expr)) continue;

      final String text = isNullLiteralExpression(expr)
                          ? InspectionsBundle.message("dataflow.message.passing.null.argument")
                          : InspectionsBundle.message("dataflow.message.passing.nullable.argument");
      LocalQuickFix[] fixes = createNPEFixes((PsiExpression)expr, (PsiExpression)expr, holder.isOnTheFly());
      holder.registerProblem(expr, text, fixes);
    }
  }

  private static void reportNullableAssignments(DataFlowInstructionVisitor visitor, ProblemsHolder holder, Set<PsiElement> reportedAnchors) {
    for (PsiElement expr : visitor.getProblems(NullabilityProblem.assigningToNotNull)) {
      if (!reportedAnchors.add(expr)) continue;

      final String text = isNullLiteralExpression(expr)
                          ? InspectionsBundle.message("dataflow.message.assigning.null")
                          : InspectionsBundle.message("dataflow.message.assigning.nullable");
      holder.registerProblem(expr, text);
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

    boolean notNullRequired = NullableNotNullManager.isNotNull(method);
    if (!notNullRequired && !SUGGEST_NULLABLE_ANNOTATIONS) return;

    PsiType returnType = method.getReturnType();
    // no warnings in void lambdas, where the expression is not returned anyway
    if (block instanceof PsiExpression && block.getParent() instanceof PsiLambdaExpression && PsiType.VOID.equals(returnType)) return;
    
    // no warnings for Void methods, where only null can be possibly returned
    if (returnType == null || returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID)) return;

    for (PsiElement statement : visitor.getProblems(NullabilityProblem.nullableReturn)) {
      assert statement instanceof PsiExpression; 
      final PsiExpression expr = (PsiExpression)statement;
      if (!reportedAnchors.add(expr)) continue;

      if (notNullRequired) {
        final String text = isNullLiteralExpression(expr)
                            ? InspectionsBundle.message("dataflow.message.return.null.from.notnull")
                            : InspectionsBundle.message("dataflow.message.return.nullable.from.notnull");
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
          : new LocalQuickFix[]{ new AnnotateMethodFix(defaultNullable, ArrayUtil.toStringArray(manager.getNotNulls())) {
            @Override
            public int shouldAnnotateBaseMethod(PsiMethod method, PsiMethod superMethod, Project project) {
              return 1;
            }
          }};
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
    PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    if (!(statement instanceof PsiIfStatement)) return false;

    PsiExpression condition = ((PsiIfStatement)statement).getCondition();
    if (!PsiTreeUtil.isAncestor(condition, element, false)) return false;

    if (isCompileTimeFlagReference(condition)) return true;

    Collection<PsiReferenceExpression> refs = PsiTreeUtil.findChildrenOfType(condition, PsiReferenceExpression.class);
    return ContainerUtil.or(refs, DataFlowInspectionBase::isCompileTimeFlagReference);
  }

  private static boolean isCompileTimeFlagReference(PsiElement element) {
    PsiElement resolved = element instanceof PsiReferenceExpression ? ((PsiReferenceExpression)element).resolve() : null;
    if (!(resolved instanceof PsiField)) return false;
    PsiField field = (PsiField)resolved;
    return field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.STATIC) &&
           PsiType.BOOLEAN.equals(field.getType());
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
    return new LocalQuickFix() {
      @NotNull
      @Override
      public String getName() {
        return InspectionsBundle.message("inspection.data.flow.simplify.to.assignment.quickfix.name");
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return InspectionsBundle.message("inspection.data.flow.simplify.boolean.expression.quickfix");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement psiElement = descriptor.getPsiElement();
        if (psiElement == null) return;

        final PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(psiElement, PsiAssignmentExpression.class);
        if (assignmentExpression == null) {
          return;
        }

        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final String lExpressionText = assignmentExpression.getLExpression().getText();
        final PsiExpression rExpression = assignmentExpression.getRExpression();
        final String rExpressionText = rExpression != null ? rExpression.getText() : "";
        assignmentExpression.replace(factory.createExpressionFromText(lExpressionText + " = " + rExpressionText, psiElement));
      }
    };
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

  private static class RedundantInstanceofFix implements LocalQuickFix {
    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.data.flow.redundant.instanceof.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement())) return;
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiInstanceOfExpression) {
        try {
          final PsiExpression compareToNull = JavaPsiFacade.getInstance(psiElement.getProject()).getElementFactory().
            createExpressionFromText(((PsiInstanceOfExpression)psiElement).getOperand().getText() + " != null", psiElement.getParent());
          psiElement.replace(compareToNull);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
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

    @Override
    protected boolean checkNotNullable(DfaMemoryState state, DfaValue value, NullabilityProblem problem, PsiElement anchor) {
      boolean ok = super.checkNotNullable(state, value, problem, anchor);
      if (!ok && anchor != null) {
        myProblems.putValue(problem, anchor);
      }
      Pair<NullabilityProblem, PsiElement> key = Pair.create(problem, anchor);
      StateInfo info = myStateInfos.get(key);
      if (info == null) {
        myStateInfos.put(key, info = new StateInfo());
      }
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
