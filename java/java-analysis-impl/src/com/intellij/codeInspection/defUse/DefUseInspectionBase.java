/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DefUseInspectionBase extends BaseJavaBatchLocalInspectionTool {
  public boolean REPORT_PREFIX_EXPRESSIONS = false;
  public boolean REPORT_POSTFIX_EXPRESSIONS = true;
  public boolean REPORT_REDUNDANT_INITIALIZER = true;

  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.unused.assignment.display.name");
  @NonNls public static final String SHORT_NAME = "UnusedAssignment";

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitMethod(PsiMethod method) {
        checkCodeBlock(method.getBody(), holder, isOnTheFly);
      }

      @Override public void visitClassInitializer(PsiClassInitializer initializer) {
        checkCodeBlock(initializer.getBody(), holder, isOnTheFly);
      }
    };
  }

  private void checkCodeBlock(final PsiCodeBlock body,
                              final ProblemsHolder holder,
                              final boolean isOnTheFly) {
    if (body == null) return;
    final Set<PsiVariable> usedVariables = new THashSet<PsiVariable>();
    List<DefUseUtil.Info> unusedDefs = DefUseUtil.getUnusedDefs(body, usedVariables);

    if (unusedDefs != null && !unusedDefs.isEmpty()) {
      Collections.sort(unusedDefs, new Comparator<DefUseUtil.Info>() {
        @Override
        public int compare(DefUseUtil.Info o1, DefUseUtil.Info o2) {
          int offset1 = o1.getContext().getTextOffset();
          int offset2 = o2.getContext().getTextOffset();

          if (offset1 == offset2) return 0;
          if (offset1 < offset2) return -1;

          return 1;
        }
      });

      for (DefUseUtil.Info info : unusedDefs) {
        PsiElement context = info.getContext();
        PsiVariable psiVariable = info.getVariable();

        if (context instanceof PsiDeclarationStatement || context instanceof PsiResourceVariable) {
          if (!info.isRead()) {
            if (!isOnTheFly) {
              holder.registerProblem(psiVariable.getNameIdentifier(),
                                     InspectionsBundle.message("inspection.unused.assignment.problem.descriptor1", "<code>#ref</code> #loc"),
                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            }
          }
          else {
            if (REPORT_REDUNDANT_INITIALIZER) {
              List<LocalQuickFix> fixes = ContainerUtil.createMaybeSingletonList(
                isOnTheFlyOrNoSideEffects(isOnTheFly, psiVariable, psiVariable.getInitializer()) ? createRemoveInitializerFix() : null);
              holder.registerProblem(psiVariable.getInitializer(),
                                     InspectionsBundle.message("inspection.unused.assignment.problem.descriptor2",
                                                               "<code>" + psiVariable.getName() + "</code>", "<code>#ref</code> #loc"),
                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                     fixes.toArray(new LocalQuickFix[fixes.size()])
              );
            }
          }
        }
        else if (context instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignment = (PsiAssignmentExpression)context;
          List<LocalQuickFix> fixes = ContainerUtil.createMaybeSingletonList(
            isOnTheFlyOrNoSideEffects(isOnTheFly, psiVariable, assignment.getRExpression()) ? createRemoveAssignmentFix() : null);
          holder.registerProblem(assignment.getLExpression(),
                                 InspectionsBundle.message("inspection.unused.assignment.problem.descriptor3",
                                                           assignment.getRExpression().getText(), "<code>#ref</code>" + " #loc"), 
                                 ProblemHighlightType.LIKE_UNUSED_SYMBOL, fixes.toArray(new LocalQuickFix[fixes.size()])
          );
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

    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitClass(PsiClass aClass) {
      }

      @Override public void visitLocalVariable(PsiLocalVariable variable) {
        if (!usedVariables.contains(variable) && variable.getInitializer() == null && !isOnTheFly) {
          holder.registerProblem(variable.getNameIdentifier(),
                                 InspectionsBundle.message("inspection.unused.assignment.problem.descriptor5", "<code>#ref</code> #loc"),
                                 ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        }
      }
    });
  }

  private static boolean isOnTheFlyOrNoSideEffects(boolean isOnTheFly,
                                                   PsiVariable psiVariable,
                                                   PsiExpression initializer) {
    return isOnTheFly || !RemoveUnusedVariableUtil.checkSideEffects(initializer, psiVariable, new ArrayList<PsiElement>());
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
      myReportInitializer.getModel().addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          REPORT_REDUNDANT_INITIALIZER = myReportInitializer.isSelected();
        }
      });
      gc.insets = new Insets(0, 0, 15, 0);
      gc.gridy = 0;
      add(myReportInitializer, gc);

      myReportPrefix = new JCheckBox(InspectionsBundle.message("inspection.unused.assignment.option"));
      myReportPrefix.setSelected(REPORT_PREFIX_EXPRESSIONS);
      myReportPrefix.getModel().addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          REPORT_PREFIX_EXPRESSIONS = myReportPrefix.isSelected();
        }
      });
      gc.insets = new Insets(0, 0, 0, 0);
      gc.gridy++;
      add(myReportPrefix, gc);

      myReportPostfix = new JCheckBox(InspectionsBundle.message("inspection.unused.assignment.option1"));
      myReportPostfix.setSelected(REPORT_POSTFIX_EXPRESSIONS);
      myReportPostfix.getModel().addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          REPORT_POSTFIX_EXPRESSIONS = myReportPostfix.isSelected();
        }
      });

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
}