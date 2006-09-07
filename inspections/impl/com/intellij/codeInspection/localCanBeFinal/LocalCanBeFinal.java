package com.intellij.codeInspection.localCanBeFinal;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class LocalCanBeFinal extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal");

  public boolean REPORT_VARIABLES = true;
  public boolean REPORT_PARAMETERS = true;

  private LocalQuickFix myQuickFix;
  @NonNls public static final String SHORT_NAME = "LocalCanBeFinal";

  public LocalCanBeFinal() {
    myQuickFix = new AcceptSuggested();
  }

  public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly) {
    return checkCodeBlock(method.getBody(), manager, isOnTheFly);
  }

  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> allProblems = null;
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] problems = checkCodeBlock(initializer.getBody(), manager, isOnTheFly);
      if (problems != null) {
        if (allProblems == null) {
          allProblems = new ArrayList<ProblemDescriptor>(1);
        }
        allProblems.addAll(Arrays.asList(problems));
      }
    }
    return allProblems == null ? null : allProblems.toArray(new ProblemDescriptor[allProblems.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkCodeBlock(final PsiCodeBlock body, InspectionManager manager, boolean isOnTheFly) {
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
      flow = ControlFlowFactory.getControlFlow(body, policy, false);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }

    int start = flow.getStartOffset(body);
    int end = flow.getEndOffset(body);

    final List<PsiVariable> writtenVariables = new ArrayList<PsiVariable>(Arrays.asList(ControlFlowUtil.getWrittenVariables(flow, start, end, false)));
    
    final HashSet<PsiVariable> ssaVarsSet = new HashSet<PsiVariable>();
    body.accept(new PsiRecursiveElementVisitor() {
      public void visitCodeBlock(PsiCodeBlock block) {
        super.visitCodeBlock(block);
        PsiElement anchor = block;
        if (block.getParent() instanceof PsiSwitchStatement) {
          anchor = block.getParent();
        }
        int from = flow.getStartOffset(anchor);
        int end = flow.getEndOffset(anchor);
        PsiVariable[] ssa = ControlFlowUtil.getSSAVariables(flow, from, end, true);
        HashSet<PsiElement> declared = getDeclaredVariables(block);
        for (PsiVariable psiVariable : ssa) {
          if (declared.contains(psiVariable)) {
            ssaVarsSet.add(psiVariable);
          }
        }
      }

      public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        final PsiParameter param = statement.getIterationParameter();
        final PsiStatement body = statement.getBody();
        int from = flow.getStartOffset(body);
        int end = flow.getEndOffset(body);
        if (!Arrays.asList(ControlFlowUtil.getWrittenVariables(flow, from, end, false)).contains(param)) {
          writtenVariables.remove(param);
          ssaVarsSet.add(param);
        }
      }

      private HashSet<PsiElement> getDeclaredVariables(PsiCodeBlock block) {
        final HashSet<PsiElement> result = new HashSet<PsiElement>();
        PsiElement[] children = block.getChildren();
        for (PsiElement child : children) {
          child.accept(new PsiElementVisitor() {
            public void visitReferenceExpression(PsiReferenceExpression expression) {
              visitReferenceElement(expression);
            }

            public void visitDeclarationStatement(PsiDeclarationStatement statement) {
              PsiElement[] declaredElements = statement.getDeclaredElements();
              for (PsiElement declaredElement : declaredElements) {
                if (declaredElement instanceof PsiVariable) result.add(declaredElement);
              }
            }
          });
        }

        return result;
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
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

    if (result.size() == 0) return null;
    for (Iterator<PsiVariable> iterator = result.iterator(); iterator.hasNext();) {
      final PsiVariable variable = iterator.next();
      if (!variable.isPhysical()){
        iterator.remove();
      }
    }
    ProblemDescriptor[] problems = new ProblemDescriptor[result.size()];
    for (int i = 0; i < problems.length; i++) {
      PsiVariable problemVariable = result.get(i);
      final PsiIdentifier nameIdenitier = problemVariable.getNameIdentifier();
      PsiElement problemElement = nameIdenitier != null ? nameIdenitier : problemVariable;
      if (problemVariable instanceof PsiParameter){
        problems[i] = manager.createProblemDescriptor(problemElement,
                                                      InspectionsBundle.message("inspection.can.be.local.parameter.problem.descriptor"),
                                                      myQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      } else {
        problems[i] = manager.createProblemDescriptor(problemElement,
                                                      InspectionsBundle.message("inspection.can.be.local.variable.problem.descriptor"),
                                                      myQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    }

    return problems;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.local.can.be.final.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  private static class AcceptSuggested implements LocalQuickFix {
    public String getName() {
      return InspectionsBundle.message("inspection.can.be.final.accept.quickfix");
    }

    public void applyFix(Project project, ProblemDescriptor problem) {
      PsiElement nameIdentifier = problem.getPsiElement();
      if (nameIdentifier == null) return;
      PsiVariable psiVariable = (PsiVariable)nameIdentifier.getParent();
      if (psiVariable == null) return;
      try {
        psiVariable.normalizeDeclaration();
        psiVariable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

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
