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
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.ex.AddAssertStatementFix;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ex.SurroundWithIfFix;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;

public class DataFlowInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowInspection");
  private static final @NonNls String SHORT_NAME = "ConstantConditions";
  public boolean SUGGEST_NULLABLE_ANNOTATIONS = false;

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {}


      public void visitField(PsiField field) {
        if (isNullLiteralExpression(field.getInitializer()) && AnnotationUtil.isNotNull(field)) {
          holder.registerProblem(field.getInitializer(), InspectionsBundle.message("dataflow.message.initializing.field.with.null"));
        }
      }

      public void visitMethod(PsiMethod method) {
        analyzeCodeBlock(method.getBody(), holder);
      }

      public void visitClassInitializer(PsiClassInitializer initializer) {
        analyzeCodeBlock(initializer.getBody(), holder);
      }
    };
  }

  private void analyzeCodeBlock(final PsiCodeBlock body, ProblemsHolder holder) {
    if (body == null) return;
    DataFlowRunner dfaRunner = new DataFlowRunner(SUGGEST_NULLABLE_ANNOTATIONS);
    if (dfaRunner.analyzeMethod(body)) {
      if (dfaRunner.problemsDetected()) {
        createDescription(dfaRunner, holder);
      }
    }
  }

  private static @Nullable LocalQuickFix[] createNPEFixes(PsiExpression qualifier) {
    if (qualifier != null &&
        !(qualifier instanceof PsiMethodCallExpression) &&
        !(qualifier instanceof PsiLiteralExpression && ((PsiLiteralExpression)qualifier).getValue() == null)) {
      try {
        PsiBinaryExpression binary = (PsiBinaryExpression)qualifier.getManager().getElementFactory().createExpressionFromText("a != null",
                                                                                                                              null);
        binary.getLOperand().replace(qualifier);
        if (PsiUtil.getLanguageLevel(qualifier).hasAssertKeyword()) {
          return new LocalQuickFix[]{
            new AddAssertStatementFix(binary),
            new SurroundWithIfFix(qualifier)
          };
        } else {
          return new LocalQuickFix[]{new SurroundWithIfFix(qualifier)};
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }
    return null;
  }

  private static void createDescription(DataFlowRunner runner, ProblemsHolder holder) {
    Pair<Set<Instruction>,Set<Instruction>> constConditions = runner.getConstConditionalExpressions();
    Set<Instruction> trueSet = constConditions.getFirst();
    Set<Instruction> falseSet = constConditions.getSecond();
    Set<Instruction> npeSet = runner.getNPEInstructions();
    Set<Instruction> cceSet = runner.getCCEInstructions();
    Set<Instruction> redundantInstanceofs = runner.getRedundantInstanceofs();

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
          holder.registerProblem(expression,
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
        if (instruction instanceof BinopInstruction && ((BinopInstruction)instruction).isInstanceofRedundant()) {
          if (((BinopInstruction)instruction).canBeNull()) {
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
          if (onTheLeftSideOfConditionalAssignemnt(psiAnchor)) {
            holder.registerProblem(psiAnchor, InspectionsBundle.message("dataflow.message.pointless.assignment.expression",
                                                                        Boolean.toString(trueSet.contains(instruction))));
          }
          else {
            final LocalQuickFix localQuickFix = createSimplifyBooleanExpressionFix(psiAnchor, trueSet.contains(instruction));
            holder.registerProblem(psiAnchor, InspectionsBundle.message("dataflow.message.constant.condition",
                                                                        Boolean.toString(trueSet.contains(instruction))),
                                              localQuickFix == null ? null : new LocalQuickFix[]{localQuickFix});
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
        holder.registerProblem(expr, text, new AnnotateMethodFix(AnnotationUtil.NULLABLE));

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
      return literalExpression.getType() == PsiType.NULL;
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

  private static @Nullable LocalQuickFix createSimplifyBooleanExpressionFix(PsiElement element, final boolean value) {
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

      public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
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

    public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiInstanceOfExpression) {
        try {
          final PsiExpression compareToNull = psiElement.getManager().getElementFactory().
            createExpressionFromText(((PsiInstanceOfExpression)psiElement).getOperand().getText() + " != null",
                                     psiElement.getParent());
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
      gc.insets = new Insets(0, 0, 15, 0);
      gc.gridy = 0;
      add(mySuggestNullables, gc);
    }
  }
}
