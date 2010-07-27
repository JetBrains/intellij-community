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
package com.intellij.codeInspection.localCanBeFinal;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * @author max
 */
public class LocalCanBeFinal extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal");

  public boolean REPORT_VARIABLES = true;
  public boolean REPORT_PARAMETERS = true;

  private final LocalQuickFix myQuickFix;
  @NonNls public static final String SHORT_NAME = "LocalCanBeFinal";

  public LocalCanBeFinal() {
    myQuickFix = new AcceptSuggested();
  }

  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> list = checkCodeBlock(method.getBody(), manager, isOnTheFly);
    return list == null ? null : list.toArray(new ProblemDescriptor[list.size()]);
  }

  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> allProblems = null;
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      final List<ProblemDescriptor> problems = checkCodeBlock(initializer.getBody(), manager, isOnTheFly);
      if (problems != null) {
        if (allProblems == null) {
          allProblems = new ArrayList<ProblemDescriptor>(1);
        }
        allProblems.addAll(problems);
      }
    }
    return allProblems == null ? null : allProblems.toArray(new ProblemDescriptor[allProblems.size()]);
  }

  @Nullable
  private List<ProblemDescriptor> checkCodeBlock(final PsiCodeBlock body, InspectionManager manager, boolean onTheFly) {
    if (body == null) return null;
    final ControlFlow flow;
    try {
      ControlFlowPolicy policy = new ControlFlowPolicy() {
        public PsiVariable getUsedVariable(PsiReferenceExpression refExpr) {
          if (refExpr.isQualified()) return null;

          PsiElement refElement = refExpr.resolve();
          if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
            if (!isVariableDeclaredInMethod((PsiVariable)refElement)) return null;
            return (PsiVariable)refElement;
          }

          return null;
        }

        public boolean isParameterAccepted(PsiParameter psiParameter) {
          return isVariableDeclaredInMethod(psiParameter);
        }

        public boolean isLocalVariableAccepted(PsiLocalVariable psiVariable) {
          return isVariableDeclaredInMethod(psiVariable);
        }

        private boolean isVariableDeclaredInMethod(PsiVariable psiVariable) {
          return PsiTreeUtil.getParentOfType(psiVariable, PsiClass.class) == PsiTreeUtil.getParentOfType(body, PsiClass.class);
        }
      };
      flow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, policy, false);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }

    int start = flow.getStartOffset(body);
    int end = flow.getEndOffset(body);

    final List<PsiVariable> writtenVariables = new ArrayList<PsiVariable>(ControlFlowUtil.getWrittenVariables(flow, start, end, false));
    
    final HashSet<PsiVariable> ssaVarsSet = new HashSet<PsiVariable>();
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitCodeBlock(PsiCodeBlock block) {
        super.visitCodeBlock(block);
        PsiElement anchor = block;
        if (block.getParent() instanceof PsiSwitchStatement) {
          anchor = block.getParent();
        }
        int from = flow.getStartOffset(anchor);
        int end = flow.getEndOffset(anchor);
        List<PsiVariable> ssa = ControlFlowUtil.getSSAVariables(flow, from, end, true);
        HashSet<PsiElement> declared = getDeclaredVariables(block);
        for (PsiVariable psiVariable : ssa) {
          if (declared.contains(psiVariable)) {
            ssaVarsSet.add(psiVariable);
          }
        }
      }

      @Override public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        final PsiParameter param = statement.getIterationParameter();
        final PsiStatement body = statement.getBody();
        int from = flow.getStartOffset(body);
        int end = flow.getEndOffset(body);
        if (!ControlFlowUtil.getWrittenVariables(flow, from, end, false).contains(param)) {
          writtenVariables.remove(param);
          ssaVarsSet.add(param);
        }
      }

      private HashSet<PsiElement> getDeclaredVariables(PsiCodeBlock block) {
        final HashSet<PsiElement> result = new HashSet<PsiElement>();
        PsiElement[] children = block.getChildren();
        for (PsiElement child : children) {
          child.accept(new JavaElementVisitor() {
            @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
              visitReferenceElement(expression);
            }

            @Override public void visitDeclarationStatement(PsiDeclarationStatement statement) {
              PsiElement[] declaredElements = statement.getDeclaredElements();
              for (PsiElement declaredElement : declaredElements) {
                if (declaredElement instanceof PsiVariable) result.add(declaredElement);
              }
            }
          });
        }

        return result;
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      }
    });

    ArrayList<PsiVariable> result = new ArrayList<PsiVariable>(ssaVarsSet);

    if (body.getParent() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)body.getParent();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (!result.contains(parameter)) result.add(parameter);
      }
    }

    PsiVariable[] psiVariables = result.toArray(new PsiVariable[result.size()]);
    for (PsiVariable psiVariable : psiVariables) {
      if (!isReportParameters() && psiVariable instanceof PsiParameter || !isReportVariables() && psiVariable instanceof PsiLocalVariable ||
          psiVariable.hasModifierProperty(PsiModifier.FINAL)) {
        result.remove(psiVariable);
      }

      if (psiVariable instanceof PsiLocalVariable) {
        PsiDeclarationStatement decl = (PsiDeclarationStatement)psiVariable.getParent();
        if (decl != null && decl.getParent() instanceof PsiForStatement) {
          result.remove(psiVariable);
        }
      }
    }

    for (PsiVariable writtenVariable : writtenVariables) {
      if (writtenVariable instanceof PsiParameter) {
        result.remove(writtenVariable);
      }
    }

    if (result.isEmpty()) return null;
    for (Iterator<PsiVariable> iterator = result.iterator(); iterator.hasNext();) {
      final PsiVariable variable = iterator.next();
      if (!variable.isPhysical()){
        iterator.remove();
      }
    }
    List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>(result.size());
    for (PsiVariable variable : result) {
      final PsiIdentifier nameIdenitier = variable.getNameIdentifier();
      PsiElement problemElement = nameIdenitier != null ? nameIdenitier : variable;
      if (variable instanceof PsiParameter && !(((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement)) {
        problems.add(manager.createProblemDescriptor(problemElement,
                                                     InspectionsBundle.message("inspection.can.be.local.parameter.problem.descriptor"),
                                                     myQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly));
      }
      else {
        problems.add(manager.createProblemDescriptor(problemElement,
                                                     InspectionsBundle.message("inspection.can.be.local.variable.problem.descriptor"),
                                                     myQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly));
      }
    }

    return problems;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.local.can.be.final.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private static class AcceptSuggested implements LocalQuickFix {
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.can.be.final.accept.quickfix");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problem) {
      if (!CodeInsightUtilBase.preparePsiElementForWrite(problem.getPsiElement())) return;
      PsiElement nameIdentifier = problem.getPsiElement();
      if (nameIdentifier == null) return;
      PsiVariable psiVariable = PsiTreeUtil.getParentOfType(nameIdentifier, PsiVariable.class, false);
      if (psiVariable == null) return;
      try {
        psiVariable.normalizeDeclaration();
        PsiUtil.setModifierProperty(psiVariable, PsiModifier.FINAL, true);
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

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private boolean isReportVariables() {
    return REPORT_VARIABLES;
  }

  private boolean isReportParameters() {
    return REPORT_PARAMETERS;
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myReportVariablesCheckbox;
    private final JCheckBox myReportParametersCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());

      GridBagConstraints gc = new GridBagConstraints();
      gc.weighty = 0;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;


      myReportVariablesCheckbox = new JCheckBox(InspectionsBundle.message("inspection.local.can.be.final.option"));
      myReportVariablesCheckbox.setSelected(REPORT_VARIABLES);
      myReportVariablesCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          REPORT_VARIABLES = myReportVariablesCheckbox.isSelected();
        }
      });
      gc.gridy = 0;
      add(myReportVariablesCheckbox, gc);

      myReportParametersCheckbox = new JCheckBox(InspectionsBundle.message("inspection.local.can.be.final.option1"));
      myReportParametersCheckbox.setSelected(REPORT_PARAMETERS);
      myReportParametersCheckbox.getModel().addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          REPORT_PARAMETERS = myReportParametersCheckbox.isSelected();
        }
      });

      gc.weighty = 1;
      gc.gridy++;
      add(myReportParametersCheckbox, gc);
    }
  }

  public boolean isEnabledByDefault() {
    return false;
  }
}
