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
package com.intellij.codeInspection.defUse;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefUseInspectionBase extends BaseJavaBatchLocalInspectionTool {
  public boolean REPORT_PREFIX_EXPRESSIONS;
  public boolean REPORT_POSTFIX_EXPRESSIONS = true;
  public boolean REPORT_REDUNDANT_INITIALIZER = true;

  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.unused.assignment.display.name");
  public static final String SHORT_NAME = "UnusedAssignment";

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        checkCodeBlock(method.getBody(), holder, isOnTheFly);
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        checkCodeBlock(initializer.getBody(), holder, isOnTheFly);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        PsiElement body = expression.getBody();
        if (body instanceof PsiCodeBlock) {
          checkCodeBlock((PsiCodeBlock)body, holder, isOnTheFly);
        }
      }

      @Override
      public void visitField(PsiField field) {
        checkField(field, holder, isOnTheFly);
      }
    };
  }

  private void checkCodeBlock(final PsiCodeBlock body,
                              final ProblemsHolder holder,
                              final boolean isOnTheFly) {
    if (body == null) return;
    final Set<PsiVariable> usedVariables = new THashSet<>();
    List<DefUseUtil.Info> unusedDefs = DefUseUtil.getUnusedDefs(body, usedVariables);

    if (unusedDefs != null && !unusedDefs.isEmpty()) {
      Collections.sort(unusedDefs, (o1, o2) -> {
        int offset1 = o1.getContext().getTextOffset();
        int offset2 = o2.getContext().getTextOffset();

        if (offset1 == offset2) return 0;
        if (offset1 < offset2) return -1;

        return 1;
      });

      for (DefUseUtil.Info info : unusedDefs) {
        PsiElement context = info.getContext();
        PsiVariable psiVariable = info.getVariable();

        if (context instanceof PsiDeclarationStatement || context instanceof PsiResourceVariable) {
          if (info.isRead() && REPORT_REDUNDANT_INITIALIZER) {
            reportInitializerProblem(psiVariable, holder, isOnTheFly);
          }
        }
        else if (context instanceof PsiAssignmentExpression) {
          reportAssignmentProblem(psiVariable, (PsiAssignmentExpression)context, holder, isOnTheFly);
        }
        else {
          if (context instanceof PsiPrefixExpression && REPORT_PREFIX_EXPRESSIONS ||
              context instanceof PsiPostfixExpression && REPORT_POSTFIX_EXPRESSIONS) {
            holder.registerProblem(context,
                                   InspectionsBundle.message("inspection.unused.assignment.problem.descriptor4", "<code>#ref</code> #loc"));
          }
        }
      }
    }
  }

  private void reportInitializerProblem(PsiVariable psiVariable, ProblemsHolder holder, boolean isOnTheFly) {
    List<LocalQuickFix> fixes = ContainerUtil.createMaybeSingletonList(
      isOnTheFlyOrNoSideEffects(isOnTheFly, psiVariable, psiVariable.getInitializer()) ? createRemoveInitializerFix() : null);
    holder.registerProblem(ObjectUtils.notNull(psiVariable.getInitializer(), psiVariable),
                           InspectionsBundle.message("inspection.unused.assignment.problem.descriptor2",
                                                     "<code>" + psiVariable.getName() + "</code>", "<code>#ref</code> #loc"),
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                           fixes.toArray(LocalQuickFix.EMPTY_ARRAY)
    );
  }

  private void reportAssignmentProblem(PsiVariable psiVariable,
                                       PsiAssignmentExpression assignment,
                                       ProblemsHolder holder,
                                       boolean isOnTheFly) {
    List<LocalQuickFix> fixes = ContainerUtil.createMaybeSingletonList(
      isOnTheFlyOrNoSideEffects(isOnTheFly, psiVariable, assignment.getRExpression()) ? createRemoveAssignmentFix() : null);
    holder.registerProblem(assignment.getLExpression(),
                           InspectionsBundle.message("inspection.unused.assignment.problem.descriptor3",
                                                     ObjectUtils.assertNotNull(assignment.getRExpression()).getText(), "<code>#ref</code>" + " #loc"),
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes.toArray(LocalQuickFix.EMPTY_ARRAY)
    );
  }

  private void checkField(@NotNull PsiField field, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (field.hasModifierProperty(PsiModifier.FINAL)) return;
    final PsiClass psiClass = field.getContainingClass();
    if (psiClass == null) return;
    final PsiClassInitializer[] classInitializers = psiClass.getInitializers();
    if (classInitializers.length == 0) return;

    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final boolean fieldHasInitializer = field.hasInitializer();
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
          fieldWrites.add(FieldWrite.createAssignments(isDefinitely, assignments));
        }
      }
      if (fieldHasInitializer && initializerBeforeField == classInitializer) {
        fieldWrites.add(FieldWrite.createInitializer());
      }
    }
    Collections.reverse(fieldWrites);

    boolean wasDefinitelyAssigned = false;
    for (final FieldWrite fieldWrite : fieldWrites) {
      if (wasDefinitelyAssigned) {
        if (fieldWrite.isInitializer()) {
          reportInitializerProblem(field, holder, isOnTheFly);
        }
        else {
          for (PsiAssignmentExpression assignment : fieldWrite.getAssignments()) {
            reportAssignmentProblem(field, assignment, holder, isOnTheFly);
          }
        }
      }
      else if (fieldWrite.isDefinitely()) {
        wasDefinitelyAssigned = true;
      }
    }
  }

  @NotNull
  private static List<PsiAssignmentExpression> collectAssignments(@NotNull PsiField field, @NotNull PsiClassInitializer classInitializer) {
    final List<PsiAssignmentExpression> assignmentExpressions = new ArrayList<>();
    classInitializer.accept(new JavaRecursiveElementVisitor() {
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

  protected LocalQuickFix createRemoveInitializerFix() {
    return null;
  }

  protected LocalQuickFix createRemoveAssignmentFix() {
    return null;
  }

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myReportPrefix;
    private final JCheckBox myReportPostfix;
    private final JCheckBox myReportInitializer;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myReportInitializer = new JCheckBox(InspectionsBundle.message("inspection.unused.assignment.option2"));
      myReportInitializer.setSelected(REPORT_REDUNDANT_INITIALIZER);
      myReportInitializer.getModel().addItemListener(e -> REPORT_REDUNDANT_INITIALIZER = myReportInitializer.isSelected());
      gc.insets = JBUI.insetsBottom(15);
      gc.gridy = 0;
      add(myReportInitializer, gc);

      myReportPrefix = new JCheckBox(InspectionsBundle.message("inspection.unused.assignment.option"));
      myReportPrefix.setSelected(REPORT_PREFIX_EXPRESSIONS);
      myReportPrefix.getModel().addItemListener(e -> REPORT_PREFIX_EXPRESSIONS = myReportPrefix.isSelected());
      gc.insets = JBUI.emptyInsets();
      gc.gridy++;
      add(myReportPrefix, gc);

      myReportPostfix = new JCheckBox(InspectionsBundle.message("inspection.unused.assignment.option1"));
      myReportPostfix.setSelected(REPORT_POSTFIX_EXPRESSIONS);
      myReportPostfix.getModel().addItemListener(e -> REPORT_POSTFIX_EXPRESSIONS = myReportPostfix.isSelected());
      gc.weighty = 1;
      gc.gridy++;
      add(myReportPostfix, gc);
    }
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
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


  private static class FieldWrite {
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