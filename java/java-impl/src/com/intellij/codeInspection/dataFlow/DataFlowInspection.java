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
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.NullableNotNullDialog;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInsight.intention.impl.AddNullableAnnotationFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class DataFlowInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowInspection");
  @NonNls private static final String SHORT_NAME = "ConstantConditions";
  public boolean SUGGEST_NULLABLE_ANNOTATIONS = false;
  public boolean DONT_REPORT_TRUE_ASSERT_STATEMENTS = false;

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(PsiField field) {
        analyzeCodeBlock(field, holder);
      }

      @Override
      public void visitMethod(PsiMethod method) {
        analyzeCodeBlock(method.getBody(), holder);
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        analyzeCodeBlock(initializer.getBody(), holder);
      }
    };
  }

  private void analyzeCodeBlock(@Nullable final PsiElement scope, ProblemsHolder holder) {
    if (scope == null) return;
    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner(SUGGEST_NULLABLE_ANNOTATIONS);
    final StandardInstructionVisitor visitor = new DataFlowInstructionVisitor();
    final RunnerResult rc = dfaRunner.analyzeMethod(scope, visitor);
    if (rc == RunnerResult.OK) {
      if (dfaRunner.problemsDetected(visitor)) {
        createDescription(dfaRunner, holder, visitor);
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
  private static LocalQuickFix[] createNPEFixes(PsiExpression qualifier, PsiExpression expression) {
    if (qualifier == null || expression == null) return null;
    if (qualifier instanceof PsiMethodCallExpression) return null;
    if (qualifier instanceof PsiLiteralExpression && ((PsiLiteralExpression)qualifier).getValue() == null) return null;

    try {
      final List<LocalQuickFix> fixes = new SmartList<LocalQuickFix>();

      if (PsiUtil.getLanguageLevel(qualifier).isAtLeast(LanguageLevel.JDK_1_4)) {
        final Project project = qualifier.getProject();
        final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        final PsiBinaryExpression binary = (PsiBinaryExpression)elementFactory.createExpressionFromText("a != null", null);
        binary.getLOperand().replace(qualifier);
        fixes.add(new AddAssertStatementFix(binary));
      }

      if (SurroundWithIfFix.isAvailable(qualifier)) {
        fixes.add(new SurroundWithIfFix(qualifier));
      }
      if (ReplaceWithTernaryOperatorFix.isAvailable(qualifier, expression)) {
        fixes.add(new ReplaceWithTernaryOperatorFix(qualifier));
      }
      return fixes.toArray(new LocalQuickFix[fixes.size()]);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private void createDescription(StandardDataFlowRunner runner, ProblemsHolder holder, StandardInstructionVisitor visitor) {
    Pair<Set<Instruction>, Set<Instruction>> constConditions = runner.getConstConditionalExpressions();
    Set<Instruction> trueSet = constConditions.getFirst();
    Set<Instruction> falseSet = constConditions.getSecond();

    ArrayList<Instruction> allProblems = new ArrayList<Instruction>();
    allProblems.addAll(trueSet);
    allProblems.addAll(falseSet);
    allProblems.addAll(runner.getNPEInstructions());
    allProblems.addAll(runner.getCCEInstructions());
    allProblems.addAll(StandardDataFlowRunner.getRedundantInstanceofs(runner, visitor));

    Collections.sort(allProblems, new Comparator<Instruction>() {
      public int compare(Instruction i1, Instruction i2) {
        return i1.getIndex() - i2.getIndex();
      }
    });

    HashSet<PsiElement> reportedAnchors = new HashSet<PsiElement>();

    for (Instruction instruction : allProblems) {
      if (instruction instanceof MethodCallInstruction) {
        reportCallMayProduceNpe(holder, (MethodCallInstruction)instruction);
      }
      else if (instruction instanceof FieldReferenceInstruction) {
        reportFieldAccessMayProduceNpe(holder, (FieldReferenceInstruction)instruction);
      }
      else if (instruction instanceof TypeCastInstruction) {
        reportCastMayFail(holder, (TypeCastInstruction)instruction);
      }
      else if (instruction instanceof BranchingInstruction) {
        handleBranchingInstruction(holder, visitor, trueSet, falseSet, reportedAnchors, (BranchingInstruction)instruction);
      }
    }

    reportNullableArguments(runner, holder);
    reportNullableAssignments(runner, holder);
    reportUnboxedNullables(runner, holder);
    reportNullableReturns(runner, holder);
    reportNullableArgumentsPassedToNonAnnotated(runner, holder);
  }

  private static void reportNullableArgumentsPassedToNonAnnotated(StandardDataFlowRunner runner, ProblemsHolder holder) {
    Set<PsiExpression> exprs = runner.getNullableArgumentsPassedToNonAnnotatedParam();
    for (PsiExpression expr : exprs) {
      final String text = isNullLiteralExpression(expr)
                          ? "Passing <code>null</code> argument to non annotated parameter"
                          : "Argument <code>#ref</code> #loc might be null but passed to non annotated parameter";
      LocalQuickFix[] fixes = createNPEFixes(expr, expr);
      final PsiElement parent = expr.getParent();
      if (parent instanceof PsiExpressionList) {
        final int idx = ArrayUtil.find(((PsiExpressionList)parent).getExpressions(), expr);
        if (idx > -1) {
          final PsiElement gParent = parent.getParent();
          if (gParent instanceof PsiCallExpression) {
            final PsiMethod psiMethod = ((PsiCallExpression)gParent).resolveMethod();
            if (psiMethod != null && psiMethod.getManager().isInProject(psiMethod)) {
              final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
              if (idx < parameters.length) {
                final AddNullableAnnotationFix addNullableAnnotationFix = new AddNullableAnnotationFix(parameters[idx]);
                fixes = fixes == null ? new LocalQuickFix[]{addNullableAnnotationFix} : ArrayUtil.append(fixes, addNullableAnnotationFix);
                holder.registerProblem(expr, text, fixes);
              }
            }
          }
        }
      }
      
    }
  }

  private static void reportCallMayProduceNpe(ProblemsHolder holder, MethodCallInstruction mcInstruction) {
    if (mcInstruction.getCallExpression() instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression callExpression = (PsiMethodCallExpression)mcInstruction.getCallExpression();
      LocalQuickFix[] fix = createNPEFixes(callExpression.getMethodExpression().getQualifierExpression(), callExpression);

      holder.registerProblem(callExpression,
                             InspectionsBundle.message("dataflow.message.npe.method.invocation"),
                             fix);
    }
  }

  private static void reportFieldAccessMayProduceNpe(ProblemsHolder holder, FieldReferenceInstruction frInstruction) {
    PsiElement elementToAssert = frInstruction.getElementToAssert();
    PsiExpression expression = frInstruction.getExpression();
    if (expression instanceof PsiArrayAccessExpression) {
      LocalQuickFix[] fix = createNPEFixes((PsiExpression)elementToAssert, expression);
      holder.registerProblem(expression,
                             InspectionsBundle.message("dataflow.message.npe.array.access"),
                             fix);
    }
    else {
      LocalQuickFix[] fix = createNPEFixes((PsiExpression)elementToAssert, expression);
      holder.registerProblem(elementToAssert,
                             InspectionsBundle.message("dataflow.message.npe.field.access"),
                             fix);
    }
  }

  private static void reportCastMayFail(ProblemsHolder holder, TypeCastInstruction instruction) {
    PsiTypeCastExpression typeCast = instruction.getCastExpression();
    holder.registerProblem(typeCast.getCastType(),
                           InspectionsBundle.message("dataflow.message.cce", typeCast.getOperand().getText()));
  }

  private void handleBranchingInstruction(ProblemsHolder holder,
                                          StandardInstructionVisitor visitor,
                                          Set<Instruction> trueSet,
                                          Set<Instruction> falseSet, HashSet<PsiElement> reportedAnchors, BranchingInstruction instruction) {
    PsiElement psiAnchor = instruction.getPsiAnchor();
    boolean underBinary = isAtRHSOfBooleanAnd(psiAnchor);
    if (instruction instanceof InstanceofInstruction && visitor.isInstanceofRedundant((InstanceofInstruction)instruction)) {
      if (visitor.canBeNull((BinopInstruction)instruction)) {
        holder.registerProblem(psiAnchor,
                               InspectionsBundle.message("dataflow.message.redundant.instanceof"),
                               new RedundantInstanceofFix());
      }
      else {
        final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(psiAnchor, true);
        holder.registerProblem(psiAnchor,
                               InspectionsBundle.message(underBinary ? "dataflow.message.constant.condition.when.reached" : "dataflow.message.constant.condition", Boolean.toString(true)),
                               localQuickFix == null ? null : new LocalQuickFix[]{localQuickFix});
      }
    }
    else if (psiAnchor instanceof PsiSwitchLabelStatement) {
      if (falseSet.contains(instruction)) {
        holder.registerProblem(psiAnchor,
                               InspectionsBundle.message("dataflow.message.unreachable.switch.label"));
      }
    }
    else if (psiAnchor != null && !reportedAnchors.contains(psiAnchor) && !isCompileConstantInIfCondition(psiAnchor)) {
      boolean evaluatesToTrue = trueSet.contains(instruction);
      if (onTheLeftSideOfConditionalAssignemnt(psiAnchor)) {
        holder.registerProblem(
          psiAnchor,
          InspectionsBundle.message("dataflow.message.pointless.assignment.expression", Boolean.toString(evaluatesToTrue)),
          createSimplifyToAssignmentFix()
        );
      }
      else if (!skipReportingConstantCondition(visitor, psiAnchor, evaluatesToTrue)) {
        final LocalQuickFix fix = createSimplifyBooleanExpressionFix(psiAnchor, evaluatesToTrue);
        String message = InspectionsBundle.message(underBinary ?
                                                   "dataflow.message.constant.condition.when.reached" :
                                                   "dataflow.message.constant.condition", Boolean.toString(evaluatesToTrue));
        holder.registerProblem(psiAnchor, message, fix == null ? null : new LocalQuickFix[]{fix});
      }
      reportedAnchors.add(psiAnchor);
    }
  }

  private boolean skipReportingConstantCondition(StandardInstructionVisitor visitor, PsiElement psiAnchor, boolean evaluatesToTrue) {
    return DONT_REPORT_TRUE_ASSERT_STATEMENTS && isAssertionEffectively(psiAnchor, evaluatesToTrue) ||
           visitor.silenceConstantCondition(psiAnchor);
  }

  private static void reportNullableArguments(StandardDataFlowRunner runner, ProblemsHolder holder) {
    Set<PsiExpression> exprs = runner.getNullableArguments();
    for (PsiExpression expr : exprs) {
      final String text = isNullLiteralExpression(expr)
                          ? InspectionsBundle.message("dataflow.message.passing.null.argument")
                          : InspectionsBundle.message("dataflow.message.passing.nullable.argument");
      LocalQuickFix[] fixes = createNPEFixes(expr, expr);
      holder.registerProblem(expr, text, fixes);
    }
  }

  private static void reportNullableAssignments(StandardDataFlowRunner runner, ProblemsHolder holder) {
    for (PsiExpression expr : runner.getNullableAssignments()) {
      final String text = isNullLiteralExpression(expr)
                          ? InspectionsBundle.message("dataflow.message.assigning.null")
                          : InspectionsBundle.message("dataflow.message.assigning.nullable");
      holder.registerProblem(expr, text);
    }
  }

  private static void reportUnboxedNullables(StandardDataFlowRunner runner, ProblemsHolder holder) {
    for (PsiExpression expr : runner.getUnboxedNullables()) {
      holder.registerProblem(expr, InspectionsBundle.message("dataflow.message.unboxing"));
    }
  }

  private static void reportNullableReturns(StandardDataFlowRunner runner, ProblemsHolder holder) {
    for (PsiReturnStatement statement : runner.getNullableReturns()) {
      final PsiExpression expr = statement.getReturnValue();
      if (runner.isInNotNullMethod()) {
        final String text = isNullLiteralExpression(expr)
                            ? InspectionsBundle.message("dataflow.message.return.null.from.notnull")
                            : InspectionsBundle.message("dataflow.message.return.nullable.from.notnull");
        holder.registerProblem(expr, text);
      }
      else if (AnnotationUtil.isAnnotatingApplicable(statement)) {
        final String text = isNullLiteralExpression(expr)
                            ? InspectionsBundle.message("dataflow.message.return.null.from.notnullable")
                            : InspectionsBundle.message("dataflow.message.return.nullable.from.notnullable");
        final NullableNotNullManager manager = NullableNotNullManager.getInstance(expr.getProject());
        holder.registerProblem(expr, text, new AnnotateMethodFix(manager.getDefaultNullable(), ArrayUtil.toStringArray(manager.getNotNulls())));
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

  private static boolean isCompileConstantInIfCondition(PsiElement element) {
    if (!(element instanceof PsiReferenceExpression)) return false;
    PsiElement resolved = ((PsiReferenceExpression)element).resolve();
    if (!(resolved instanceof PsiField)) return false;
    PsiField field = (PsiField)resolved;

    if (!field.hasModifierProperty(PsiModifier.FINAL)) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;

    PsiElement parent = element.getParent();
    if (parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationTokenType() == JavaTokenType.EXCL) {
      element = parent;
      parent = parent.getParent();
    }
    return parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getCondition() == element;
  }

  private static boolean isNullLiteralExpression(PsiExpression expr) {
    if (expr instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expr;
      return PsiType.NULL.equals(literalExpression.getType());
    }
    return false;
  }

  private static boolean onTheLeftSideOfConditionalAssignemnt(final PsiElement psiAnchor) {
    final PsiElement parent = psiAnchor.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression expression = (PsiAssignmentExpression)parent;
      if (expression.getLExpression() == psiAnchor) return true;
    }
    return false;
  }

  @Nullable
  private static LocalQuickFix createSimplifyBooleanExpressionFix(PsiElement element, final boolean value) {
    SimplifyBooleanExpressionFix fix = createIntention(element, value);
    if (fix == null) return null;
    final String text = fix.getText();
    return new LocalQuickFix() {
      @NotNull
      public String getName() {
        return text;
      }

      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement psiElement = descriptor.getPsiElement();
        if (psiElement == null) return;
        final SimplifyBooleanExpressionFix fix = createIntention(psiElement, value);
        if (fix == null) return;
        try {
          LOG.assertTrue(psiElement.isValid());
          fix.invoke(project, null, psiElement.getContainingFile());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      @NotNull
      public String getFamilyName() {
        return InspectionsBundle.message("inspection.data.flow.simplify.boolean.expression.quickfix");
      }
    };
  }

  @NotNull
  private static LocalQuickFix createSimplifyToAssignmentFix() {
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
    final PsiExpression expression = (PsiExpression)element;
    while (element.getParent() instanceof PsiExpression) {
      element = element.getParent();
    }
    final SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expression, value);
    // simplify intention already active
    if (!fix.isAvailable(element.getProject(), null, element.getContainingFile()) ||
        SimplifyBooleanExpressionFix.canBeSimplified((PsiExpression)element)) {
      return null;
    }
    return fix;
  }

  private static class RedundantInstanceofFix implements LocalQuickFix {
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.data.flow.redundant.instanceof.quickfix");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (!CodeInsightUtilBase.preparePsiElementForWrite(descriptor.getPsiElement())) return;
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

    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }


  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.data.flow.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox mySuggestNullables;
    private final JCheckBox myDontReportTrueAsserts;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      mySuggestNullables = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.nullable.quickfix.option"));
      mySuggestNullables.setSelected(SUGGEST_NULLABLE_ANNOTATIONS);
      mySuggestNullables.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          SUGGEST_NULLABLE_ANNOTATIONS = mySuggestNullables.isSelected();
        }
      });

      myDontReportTrueAsserts = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.true.asserts.option"));
      myDontReportTrueAsserts.setSelected(DONT_REPORT_TRUE_ASSERT_STATEMENTS);
      myDontReportTrueAsserts.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          DONT_REPORT_TRUE_ASSERT_STATEMENTS = myDontReportTrueAsserts.isSelected();
        }
      });

      gc.insets = new Insets(0, 0, 0, 0);
      gc.gridy = 0;
      add(mySuggestNullables, gc);

      final JButton configureAnnotations = new JButton(InspectionsBundle.message("configure.annotations.option"));
      configureAnnotations.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(OptionsPanel.this));
          if (project == null) project = ProjectManager.getInstance().getDefaultProject();
          final NullableNotNullDialog dialog = new NullableNotNullDialog(project);
          dialog.show();
        }
      });
      gc.gridy++;
      gc.fill = GridBagConstraints.NONE;
      gc.insets.left = 20;
      gc.insets.bottom = 15;
      add(configureAnnotations, gc);

      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weighty = 1;
      gc.insets.left = 0;
      gc.gridy++;
      add(myDontReportTrueAsserts, gc);
    }
  }

  private static class DataFlowInstructionVisitor extends StandardInstructionVisitor {

    protected void onAssigningToNotNullableVariable(AssignInstruction instruction, DataFlowRunner runner) {
      ((StandardDataFlowRunner)runner).onAssigningToNotNullableVariable(instruction.getRExpression());
    }

    protected void onNullableReturn(CheckReturnValueInstruction instruction, DataFlowRunner runner) {
      ((StandardDataFlowRunner)runner).onNullableReturn(instruction.getReturn());
    }

    protected void onInstructionProducesNPE(FieldReferenceInstruction instruction, DataFlowRunner runner) {
      ((StandardDataFlowRunner)runner).onInstructionProducesNPE(instruction);
    }

    protected void onInstructionProducesCCE(TypeCastInstruction instruction, DataFlowRunner runner) {
      ((StandardDataFlowRunner)runner).onInstructionProducesCCE(instruction);
    }

    protected void onInstructionProducesNPE(MethodCallInstruction instruction, DataFlowRunner runner) {
      ((StandardDataFlowRunner)runner).onInstructionProducesNPE(instruction);
    }

    protected void onUnboxingNullable(MethodCallInstruction instruction, DataFlowRunner runner) {
      ((StandardDataFlowRunner)runner).onUnboxingNullable(instruction.getContext());
    }

    protected void onPassingNullParameter(DataFlowRunner runner, PsiExpression arg) {
      ((StandardDataFlowRunner)runner).onPassingNullParameter(arg); // Parameters on stack are reverted.
    }

    @Override
    protected void onPassingNullParameterToNonAnnotated(DataFlowRunner runner, PsiExpression arg) {
      ((StandardDataFlowRunner)runner).onPassingNullParameterToNonAnnotated(arg);
    }
  }
}
