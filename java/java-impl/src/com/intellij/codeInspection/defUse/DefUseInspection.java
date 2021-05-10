// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.defUse;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class DefUseInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean REPORT_PREFIX_EXPRESSIONS;
  public boolean REPORT_POSTFIX_EXPRESSIONS = true;
  public boolean REPORT_REDUNDANT_INITIALIZER = true;

  public static final String SHORT_NAME = "UnusedAssignment";

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        checkCodeBlock(method.getBody(), holder);
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        checkCodeBlock(initializer.getBody(), holder);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        PsiElement body = expression.getBody();
        if (body instanceof PsiCodeBlock) {
          checkCodeBlock((PsiCodeBlock)body, holder);
        }
      }

      @Override
      public void visitField(PsiField field) {
        checkField(field, holder);
      }
    };
  }

  private void checkCodeBlock(final PsiCodeBlock body,
                              final ProblemsHolder holder) {
    if (body == null) return;
    final Set<PsiVariable> usedVariables = new HashSet<>();
    List<DefUseUtil.Info> unusedDefs = DefUseUtil.getUnusedDefs(body, usedVariables);

    if (unusedDefs != null && !unusedDefs.isEmpty()) {
      unusedDefs.sort(Comparator.comparingInt(o -> o.getContext().getTextOffset()));

      for (DefUseUtil.Info info : unusedDefs) {
        PsiElement context = info.getContext();
        PsiVariable psiVariable = info.getVariable();

        if (context instanceof PsiDeclarationStatement || context instanceof PsiResourceVariable) {
          if (info.isRead() && REPORT_REDUNDANT_INITIALIZER) {
            PsiTypeElement typeElement = psiVariable.getTypeElement();
            if (typeElement == null || !typeElement.isInferredType()) {
              reportInitializerProblem(psiVariable, holder);
            }
          }
        }
        else if (context instanceof PsiAssignmentExpression) {
          PsiElement parent = PsiUtil.skipParenthesizedExprUp(context.getParent());
          if (parent == psiVariable) continue; // int x = x = 5; -- compilation error and reported as reassigned var
          if (parent instanceof PsiAssignmentExpression && EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(
            ((PsiAssignmentExpression)parent).getLExpression(), ((PsiAssignmentExpression)context).getLExpression())) {
            // x = x = 5; reported by "Variable is assigned to itself"
            continue;
          }
          reportAssignmentProblem(psiVariable, (PsiAssignmentExpression)context, holder);
        }
        else {
          if (context instanceof PsiPrefixExpression && REPORT_PREFIX_EXPRESSIONS ||
              context instanceof PsiPostfixExpression && REPORT_POSTFIX_EXPRESSIONS) {
            holder.registerProblem(context,
                                   JavaBundle.message("inspection.unused.assignment.problem.descriptor4", "<code>#ref</code> #loc"));
          }
        }
      }
    }

    processFieldsViaDfa(body, holder);
  }

  private void processFieldsViaDfa(PsiCodeBlock body, ProblemsHolder holder) {
    DfaValueFactory factory = new DfaValueFactory(holder.getProject());
    var flow = ControlFlowAnalyzer.buildFlow(body, factory, true);
    if (flow != null) {
      Set<AssignInstruction> variables = new OverwrittenFieldAnalyzer(flow).getOverwrittenFields();
      for (AssignInstruction instruction : variables) {
        DfaValue value = instruction.getAssignedValue();
        if (!(value instanceof DfaVariableValue)) continue;
        PsiField field = ObjectUtils.tryCast(((DfaVariableValue)value).getPsiVariable(), PsiField.class);
        if (field == null) continue;
        PsiExpression lExpression = instruction.getLExpression();
        PsiExpression expression = instruction.getRExpression();
        if (lExpression == null) continue;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(lExpression.getParent());
        if (parent instanceof PsiPrefixExpression && REPORT_PREFIX_EXPRESSIONS ||
            parent instanceof PsiPostfixExpression && REPORT_POSTFIX_EXPRESSIONS) {
          holder.registerProblem(parent, JavaBundle.message("inspection.unused.assignment.problem.descriptor4", "<code>#ref</code> #loc"));
        }
        else if (parent instanceof PsiAssignmentExpression) {
          if (expression instanceof PsiArrayInitializerExpression ||
              expression instanceof PsiNewExpression && ((PsiNewExpression)expression).getArrayInitializer() != null) {
            // Due to implementation quirk, array initializers are reassigned in CFG, so false warnings appear there
            continue;
          }
          reportAssignmentProblem(field, (PsiAssignmentExpression)parent, holder);
        }
      }
    }
  }

  private static void reportInitializerProblem(PsiVariable psiVariable, ProblemsHolder holder) {
    List<LocalQuickFix> fixes = ContainerUtil.createMaybeSingletonList(
      isOnTheFlyOrNoSideEffects(holder.isOnTheFly(), psiVariable, psiVariable.getInitializer()) ? new RemoveInitializerFix() : null);
    holder.registerProblem(ObjectUtils.notNull(psiVariable.getInitializer(), psiVariable),
                           JavaBundle.message("inspection.unused.assignment.problem.descriptor2",
                                              "<code>" + psiVariable.getName() + "</code>", "<code>#ref</code> #loc"),
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                           fixes.toArray(LocalQuickFix.EMPTY_ARRAY)
    );
  }

  private static void reportAssignmentProblem(PsiVariable psiVariable,
                                              PsiAssignmentExpression assignment,
                                              ProblemsHolder holder) {
    List<LocalQuickFix> fixes = ContainerUtil.createMaybeSingletonList(
      isOnTheFlyOrNoSideEffects(holder.isOnTheFly(), psiVariable, assignment.getRExpression()) ? new RemoveAssignmentFix() : null);
    holder.registerProblem(assignment.getLExpression(),
                           JavaBundle.message("inspection.unused.assignment.problem.descriptor3",
                                              Objects.requireNonNull(assignment.getRExpression()).getText(), "<code>#ref</code>" + " #loc"),
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes.toArray(LocalQuickFix.EMPTY_ARRAY)
    );
  }

  private void checkField(@NotNull PsiField field, @NotNull ProblemsHolder holder) {
    if (field.hasModifierProperty(PsiModifier.FINAL)) return;
    final PsiClass psiClass = field.getContainingClass();
    if (psiClass == null) return;
    final PsiClassInitializer[] classInitializers = psiClass.getInitializers();
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiMethod[] constructors = !isStatic ? psiClass.getConstructors() : PsiMethod.EMPTY_ARRAY;
    final boolean fieldHasInitializer = field.hasInitializer();
    final int maxPossibleWritesCount = classInitializers.length + (constructors.length != 0 ? 1 : 0) + (fieldHasInitializer ? 1 : 0);
    if (maxPossibleWritesCount <= 1) return;

    final PsiClassInitializer initializerBeforeField = PsiTreeUtil.getPrevSiblingOfType(field, PsiClassInitializer.class);
    final List<FieldWrite> fieldWrites = new ArrayList<>(); // class initializers and field initializer in the program order

    if (fieldHasInitializer && initializerBeforeField == null) {
      fieldWrites.add(FieldWrite.createInitializer());
    }
    for (PsiClassInitializer classInitializer : classInitializers) {
      if (classInitializer.hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        final List<PsiAssignmentExpression> assignments = collectAssignments(field, classInitializer);
        if (!assignments.isEmpty()) {
          boolean isDefinitely = HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, classInitializer.getBody());
          if (isDefinitely) {
            try {
              ControlFlow flow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(classInitializer.getBody());
              if (ControlFlowUtil.getReadBeforeWrite(flow)
                                 .stream()
                                 .anyMatch(read -> (isStatic || ExpressionUtil.isEffectivelyUnqualified(read)) &&
                                                   read.isReferenceTo(field))) {
                isDefinitely = false;
              }
            }
            catch (AnalysisCanceledException e) {
              // ignore
            }
          }
          fieldWrites.add(FieldWrite.createAssignments(isDefinitely, assignments));
        }
      }
      if (fieldHasInitializer && initializerBeforeField == classInitializer) {
        fieldWrites.add(FieldWrite.createInitializer());
      }
    }
    Collections.reverse(fieldWrites);

    boolean wasDefinitelyAssigned = isAssignedInAllConstructors(field, constructors);
    for (final FieldWrite fieldWrite : fieldWrites) {
      if (wasDefinitelyAssigned) {
        if (fieldWrite.isInitializer()) {
          if (REPORT_REDUNDANT_INITIALIZER) {
            reportInitializerProblem(field, holder);
          }
        }
        else {
          for (PsiAssignmentExpression assignment : fieldWrite.getAssignments()) {
            reportAssignmentProblem(field, assignment, holder);
          }
        }
      }
      else if (fieldWrite.isDefinitely()) {
        wasDefinitelyAssigned = true;
      }
    }
  }

  private static boolean isAssignedInAllConstructors(@NotNull PsiField field, PsiMethod @NotNull [] constructors) {
    if (constructors.length == 0 || field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    for (PsiMethod constructor : constructors) {
      if (!JavaHighlightUtil.getChainedConstructors(constructor).isEmpty()) continue;
      final PsiCodeBlock body = constructor.getBody();
      if (body == null || !HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, body)) {
        return false;
      }
      try {
        ControlFlow flow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(body);
        if (ControlFlowUtil.getReadBeforeWrite(flow).stream()
                           .anyMatch(read -> ExpressionUtil.isEffectivelyUnqualified(read) && read.isReferenceTo(field))) {
          return false;
        }
      }
      catch (AnalysisCanceledException e) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static List<PsiAssignmentExpression> collectAssignments(@NotNull PsiField field, @NotNull PsiClassInitializer classInitializer) {
    final List<PsiAssignmentExpression> assignmentExpressions = new ArrayList<>();
    classInitializer.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        final PsiExpression lExpression = expression.getLExpression();
        if (lExpression instanceof PsiJavaReference && ((PsiJavaReference)lExpression).isReferenceTo(field)) {
          final PsiExpression rExpression = expression.getRExpression();
          if (rExpression != null) {
            assignmentExpressions.add(expression);
          }
        }
        super.visitAssignmentExpression(expression);
      }
    });
    return assignmentExpressions;
  }

  private static boolean isOnTheFlyOrNoSideEffects(boolean isOnTheFly,
                                                   PsiVariable psiVariable,
                                                   PsiExpression initializer) {
    return isOnTheFly || !RemoveUnusedVariableUtil.checkSideEffects(initializer, psiVariable, new ArrayList<>());
  }


  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private final class OptionsPanel extends InspectionOptionsPanel {
    private final JCheckBox myReportPrefix;
    private final JCheckBox myReportPostfix;
    private final JCheckBox myReportInitializer;

    private OptionsPanel() {

      myReportInitializer = new JCheckBox(JavaBundle.message("inspection.unused.assignment.option2"));
      myReportInitializer.setSelected(REPORT_REDUNDANT_INITIALIZER);
      myReportInitializer.getModel().addItemListener(e -> REPORT_REDUNDANT_INITIALIZER = myReportInitializer.isSelected());
      add(myReportInitializer);

      myReportPrefix = new JCheckBox(JavaBundle.message("inspection.unused.assignment.option"));
      myReportPrefix.setSelected(REPORT_PREFIX_EXPRESSIONS);
      myReportPrefix.getModel().addItemListener(e -> REPORT_PREFIX_EXPRESSIONS = myReportPrefix.isSelected());
      add(myReportPrefix);

      myReportPostfix = new JCheckBox(JavaBundle.message("inspection.unused.assignment.option1"));
      myReportPostfix.setSelected(REPORT_POSTFIX_EXPRESSIONS);
      myReportPostfix.getModel().addItemListener(e -> REPORT_POSTFIX_EXPRESSIONS = myReportPostfix.isSelected());
      add(myReportPostfix);
    }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.probable.bugs");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }


  private static final class FieldWrite {
    final boolean myDefinitely;
    final List<PsiAssignmentExpression> myAssignments;

    private FieldWrite(boolean definitely, List<PsiAssignmentExpression> assignments) {
      myDefinitely = definitely;
      myAssignments = assignments;
    }

    public boolean isDefinitely() {
      return myDefinitely;
    }

    public boolean isInitializer() {
      return myAssignments == null;
    }

    public List<PsiAssignmentExpression> getAssignments() {
      return myAssignments != null ? myAssignments : Collections.emptyList();
    }

    @NotNull
    public static FieldWrite createInitializer() {
      return new FieldWrite(true, null);
    }

    @NotNull
    public static FieldWrite createAssignments(boolean definitely, @NotNull List<PsiAssignmentExpression> assignmentExpressions) {
      return new FieldWrite(definitely, assignmentExpressions);
    }
  }
}