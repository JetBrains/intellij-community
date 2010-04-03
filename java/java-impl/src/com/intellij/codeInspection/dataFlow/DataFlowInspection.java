/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
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
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {}


      @Override public void visitField(PsiField field) {
        if (isNullLiteralExpression(field.getInitializer()) && AnnotationUtil.isNotNull(field)) {
          holder.registerProblem(field.getInitializer(), InspectionsBundle.message("dataflow.message.initializing.field.with.null"));
        }
      }

      @Override public void visitMethod(PsiMethod method) {
        analyzeCodeBlock(method.getBody(), holder);
      }

      @Override public void visitClassInitializer(PsiClassInitializer initializer) {
        analyzeCodeBlock(initializer.getBody(), holder);
      }
    };
  }

  private void analyzeCodeBlock(final PsiCodeBlock body, ProblemsHolder holder) {
    if (body == null) return;
    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner(SUGGEST_NULLABLE_ANNOTATIONS);
    final StandardInstructionVisitor visitor = new DataFlowInstructionVisitor();
    final RunnerResult rc = dfaRunner.analyzeMethod(body, visitor);
    if (rc == RunnerResult.OK) {
      if (dfaRunner.problemsDetected(visitor)) {
        createDescription(dfaRunner, holder, visitor);
      }
    }
    else if (rc == RunnerResult.TOO_COMPLEX) {
      if (body.getParent() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)body.getParent();
        final PsiIdentifier name = method.getNameIdentifier();
        if (name != null) { // Might be null for synthetic methods like JSP page.
          holder.registerProblem(name, InspectionsBundle.message("dataflow.too.complex"), ProblemHighlightType.INFO);
        }
      }
    }
  }

  @Nullable
  private static LocalQuickFix[] createNPEFixes(PsiExpression qualifier) {
    if (qualifier != null &&
        !(qualifier instanceof PsiMethodCallExpression) &&
        !(qualifier instanceof PsiLiteralExpression && ((PsiLiteralExpression)qualifier).getValue() == null)) {
      try {
        PsiBinaryExpression binary = (PsiBinaryExpression)JavaPsiFacade.getInstance(qualifier.getProject()).getElementFactory()
          .createExpressionFromText("a != null",
                                                                                                                              null);
        binary.getLOperand().replace(qualifier);
        List<LocalQuickFix> fixes = new SmartList<LocalQuickFix>();

        if (PsiUtil.getLanguageLevel(qualifier).hasAssertKeyword()) {
          fixes.add(new AddAssertStatementFix(binary));
        }
        SurroundWithIfFix ifFix = new SurroundWithIfFix(qualifier);
        if (ifFix.isAvailable()) {
          fixes.add(ifFix);
        }
        return fixes.toArray(new LocalQuickFix[fixes.size()]);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }
    return null;
  }

  private void createDescription(StandardDataFlowRunner runner, ProblemsHolder holder, StandardInstructionVisitor visitor) {
    Pair<Set<Instruction>,Set<Instruction>> constConditions = runner.getConstConditionalExpressions();
    Set<Instruction> trueSet = constConditions.getFirst();
    Set<Instruction> falseSet = constConditions.getSecond();
    Set<Instruction> npeSet = runner.getNPEInstructions();
    Set<Instruction> cceSet = runner.getCCEInstructions();
    Set<Instruction> redundantInstanceofs = StandardDataFlowRunner.getRedundantInstanceofs(runner, visitor);

    ArrayList<Instruction> allProblems = new ArrayList<Instruction>();
    allProblems.addAll(trueSet);
    allProblems.addAll(falseSet);
    allProblems.addAll(npeSet);
    allProblems.addAll(cceSet);
    allProblems.addAll(redundantInstanceofs);

    Collections.sort(allProblems, new Comparator<Instruction>() {
      public int compare(Instruction i1, Instruction i2) {
        return i1.getIndex() - i2.getIndex();
      }
    });

    HashSet<PsiElement> reportedAnchors = new HashSet<PsiElement>();

    for (Instruction instruction : allProblems) {
      if (instruction instanceof MethodCallInstruction) {
        MethodCallInstruction mcInstruction = (MethodCallInstruction)instruction;
        if (mcInstruction.getCallExpression() instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression callExpression = (PsiMethodCallExpression)mcInstruction.getCallExpression();
          LocalQuickFix[] fix = createNPEFixes(callExpression.getMethodExpression().getQualifierExpression());

          holder.registerProblem(callExpression,
                                 InspectionsBundle.message("dataflow.message.npe.method.invocation"),
                                 fix);
        }
      }
      else if (instruction instanceof FieldReferenceInstruction) {
        FieldReferenceInstruction frInstruction = (FieldReferenceInstruction)instruction;
        PsiElement elementToAssert = frInstruction.getElementToAssert();
        PsiExpression expression = frInstruction.getExpression();
        if (expression instanceof PsiArrayAccessExpression) {
          LocalQuickFix[] fix = createNPEFixes((PsiExpression)elementToAssert);
          holder.registerProblem(expression,
                                 InspectionsBundle.message("dataflow.message.npe.array.access"),
                                 fix);
        }
        else {
          LocalQuickFix[] fix = createNPEFixes((PsiExpression)elementToAssert);
          holder.registerProblem(elementToAssert,
                                 InspectionsBundle.message("dataflow.message.npe.field.access"),
                                 fix);
        }
      }
      else if (instruction instanceof TypeCastInstruction) {
        TypeCastInstruction tcInstruction = (TypeCastInstruction)instruction;
        PsiTypeCastExpression typeCast = tcInstruction.getCastExpression();
        holder.registerProblem(typeCast.getCastType(),
                               InspectionsBundle.message("dataflow.message.cce", typeCast.getOperand().getText()));
      }
      else if (instruction instanceof BranchingInstruction) {
        PsiElement psiAnchor = ((BranchingInstruction)instruction).getPsiAnchor();
        if (instruction instanceof InstanceofInstruction && visitor.isInstanceofRedundant((InstanceofInstruction)instruction)) {
          if (visitor.canBeNull((BinopInstruction)instruction)) {
            holder.registerProblem(psiAnchor,
                                   InspectionsBundle.message("dataflow.message.redundant.instanceof"),
                                   new RedundantInstanceofFix());
          }
          else {
            final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(psiAnchor, true);
            holder.registerProblem(psiAnchor,
                                   InspectionsBundle.message("dataflow.message.constant.condition", Boolean.toString(true)),
                                   localQuickFix==null?null:new LocalQuickFix[]{localQuickFix});
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
            holder.registerProblem(psiAnchor, InspectionsBundle.message("dataflow.message.pointless.assignment.expression",
                                                                        Boolean.toString(evaluatesToTrue)));
          }
          else {
            boolean report = !(psiAnchor.getParent() instanceof PsiAssertStatement) || !DONT_REPORT_TRUE_ASSERT_STATEMENTS || !evaluatesToTrue;
            if (report) {
              final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(psiAnchor, evaluatesToTrue);
              holder.registerProblem(psiAnchor, InspectionsBundle.message("dataflow.message.constant.condition",
                                                                          Boolean.toString(evaluatesToTrue)),
                                                localQuickFix == null ? null : new LocalQuickFix[]{localQuickFix});
            }
          }
          reportedAnchors.add(psiAnchor);
        }
      }
    }

    Set<PsiExpression> exprs = runner.getNullableArguments();
    for (PsiExpression expr : exprs) {
      final String text = isNullLiteralExpression(expr)
                          ? InspectionsBundle.message("dataflow.message.passing.null.argument")
                          : InspectionsBundle.message("dataflow.message.passing.nullable.argument");
      LocalQuickFix[] fixes = createNPEFixes(expr);
      holder.registerProblem(expr, text, fixes);
    }

    exprs = runner.getNullableAssignments();
    for (PsiExpression expr : exprs) {
      final String text = isNullLiteralExpression(expr)
                              ? InspectionsBundle.message("dataflow.message.assigning.null")
                              : InspectionsBundle.message("dataflow.message.assigning.nullable");
      holder.registerProblem(expr, text);
    }

    exprs = runner.getUnboxedNullables();
    for (PsiExpression expr : exprs) {
      holder.registerProblem(expr, InspectionsBundle.message("dataflow.message.unboxing"));
    }

    final Set<PsiReturnStatement> statements = runner.getNullableReturns();
    for (PsiReturnStatement statement : statements) {
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
        holder.registerProblem(expr, text, new AnnotateMethodFix(AnnotationUtil.NULLABLE, AnnotationUtil.NOT_NULL));

      }
    }
  }

  private static boolean isCompileConstantInIfCondition(PsiElement element) {
    if (!(element instanceof PsiReferenceExpression)) return false;
    PsiElement resolved = ((PsiReferenceExpression)element).resolve();
    if (!(resolved instanceof PsiField)) return false;
    PsiField field = (PsiField)resolved;

    if (!field.hasModifierProperty(PsiModifier.FINAL)) return false;

    PsiElement parent = element.getParent();
    if (parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationSign().getTokenType() == JavaTokenType.EXCL) {
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
    return new LocalQuickFix() {
      @NotNull public String getName() {
        return fix.getText();
      }

      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement psiElement = descriptor.getPsiElement();
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

      //mySuggestNullables = new JCheckBox("Suggest @Nullable annotation for method possibly return null.\n Requires JDK5.0 and annotations.jar from IDEA distribution");
      mySuggestNullables = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.nullable.quickfix.option", ApplicationNamesInfo.getInstance().getProductName()));
      mySuggestNullables.setSelected(SUGGEST_NULLABLE_ANNOTATIONS);
      mySuggestNullables.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          SUGGEST_NULLABLE_ANNOTATIONS = mySuggestNullables.isSelected();
        }
      });

      myDontReportTrueAsserts = new JCheckBox(
        InspectionsBundle.message("inspection.data.flow.true.asserts.option", ApplicationNamesInfo.getInstance().getProductName()));
      myDontReportTrueAsserts.setSelected(DONT_REPORT_TRUE_ASSERT_STATEMENTS);
      myDontReportTrueAsserts.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          DONT_REPORT_TRUE_ASSERT_STATEMENTS = myDontReportTrueAsserts.isSelected();
        }
      });

      gc.insets = new Insets(0, 0, 15, 0);
      gc.gridy = 0;
      add(mySuggestNullables, gc);

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
      ((StandardDataFlowRunner) runner).onInstructionProducesNPE(instruction);
    }

    protected void onUnboxingNullable(MethodCallInstruction instruction, DataFlowRunner runner) {
      ((StandardDataFlowRunner) runner).onUnboxingNullable(instruction.getContext());
    }

    protected void onPassingNullParameter(DataFlowRunner runner, PsiExpression arg) {
          ((StandardDataFlowRunner) runner).onPassingNullParameter(arg); // Parameters on stack are reverted.
        }
  }
}
