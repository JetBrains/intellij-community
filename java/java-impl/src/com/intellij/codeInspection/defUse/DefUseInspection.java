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
package com.intellij.codeInspection.defUse;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix;
import com.intellij.codeInsight.daemon.impl.quickfix.SideEffectWarningDialog;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DefUseInspection extends BaseLocalInspectionTool {
  public boolean REPORT_PREFIX_EXPRESSIONS = false;
  public boolean REPORT_POSTFIX_EXPRESSIONS = true;
  public boolean REPORT_REDUNDANT_INITIALIZER = true;

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defUse.DefUseInspection");

  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.unused.assignment.display.name");
  @NonNls public static final String SHORT_NAME = "UnusedAssignment";

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {}

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
              holder.registerProblem(psiVariable.getInitializer(),
                                     InspectionsBundle.message("inspection.unused.assignment.problem.descriptor2",
                                                               "<code>" + psiVariable.getName() + "</code>", "<code>#ref</code> #loc"),
                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                     new RemoveInitializerFix());
            }
          }
        }
        else if (context instanceof PsiAssignmentExpression &&
                 ((PsiAssignmentExpression)context).getOperationTokenType() == JavaTokenType.EQ) {
          final PsiAssignmentExpression assignment = (PsiAssignmentExpression)context;
          holder.registerProblem(assignment.getRExpression(),
                                 InspectionsBundle.message("inspection.unused.assignment.problem.descriptor3",
                                                           "<code>#ref</code>", assignment.getLExpression().getText() + " #loc"));
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

      @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression lExpression = expression.getLExpression();
        PsiExpression rExpression = expression.getRExpression();

        if (lExpression instanceof PsiReferenceExpression && rExpression instanceof PsiReferenceExpression) {
          PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
          PsiReferenceExpression rRef = (PsiReferenceExpression)rExpression;

          if (lRef.resolve() != rRef.resolve()) return;
          PsiExpression lQualifier = lRef.getQualifierExpression();
          PsiExpression rQualifier = rRef.getQualifierExpression();

          if ((lQualifier == null && rQualifier == null ||
               lQualifier instanceof PsiThisExpression && rQualifier instanceof PsiThisExpression ||
               lQualifier instanceof PsiThisExpression && rQualifier == null ||
               lQualifier == null && rQualifier instanceof PsiThisExpression) && !isOnTheFly) {
            holder.registerProblem(expression,
                                   InspectionsBundle.message("inspection.unused.assignment.problem.descriptor6", "<code>#ref</code>"));
          }
        }
      }
    });
  }

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
        public void stateChanged(ChangeEvent e) {
          REPORT_POSTFIX_EXPRESSIONS = myReportPostfix.isSelected();
        }
      });

      gc.weighty = 1;
      gc.gridy++;
      add(myReportPostfix, gc);
    }
  }


  private static class RemoveInitializerFix implements LocalQuickFix {

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.unused.assignment.remove.quickfix");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiInitializer = descriptor.getPsiElement();
      if (!(psiInitializer instanceof PsiExpression)) return;
      if (!(psiInitializer.getParent() instanceof PsiVariable)) return;
      if (!CodeInsightUtilBase.prepareFileForWrite(psiInitializer.getContainingFile())) return;

      final PsiVariable variable = (PsiVariable)psiInitializer.getParent();
      final PsiDeclarationStatement declaration = (PsiDeclarationStatement)variable.getParent();
      final List<PsiElement> sideEffects = new ArrayList<PsiElement>();
      boolean hasSideEffects = RemoveUnusedVariableFix.checkSideEffects(psiInitializer, variable, sideEffects);
      int res = SideEffectWarningDialog.DELETE_ALL;
      if (hasSideEffects) {
        hasSideEffects = PsiUtil.isStatement(psiInitializer);
        res = RemoveUnusedVariableFix.showSideEffectsWarning(sideEffects, variable,
                                                             FileEditorManager.getInstance(project).getSelectedTextEditor(),
                                                             hasSideEffects, sideEffects.get(0).getText(),
                                                             variable.getTypeElement().getText() + " " + variable.getName() + ";<br>" + PsiExpressionTrimRenderer.render((PsiExpression)psiInitializer));
      }
      try {
        if (res == SideEffectWarningDialog.DELETE_ALL) {
          psiInitializer.delete();
        }
        else if (res == SideEffectWarningDialog.MAKE_STATEMENT) {
          final PsiElementFactory factory = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory();
          final PsiStatement statementFromText = factory.createStatementFromText(psiInitializer.getText() + ";", null);
          declaration.getParent().addAfter(statementFromText, declaration);
          psiInitializer.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }
}
