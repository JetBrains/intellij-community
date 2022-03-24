// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.localCanBeFinal;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class LocalCanBeFinal extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  public boolean REPORT_VARIABLES = true;
  public boolean REPORT_PARAMETERS = true;
  public boolean REPORT_CATCH_PARAMETERS = true;
  public boolean REPORT_FOREACH_PARAMETERS = true;
  public boolean REPORT_IMPLICIT_FINALS = true;

  private final LocalQuickFix myQuickFix;
  @NonNls public static final String SHORT_NAME = "LocalCanBeFinal";

  public LocalCanBeFinal() {
    myQuickFix = new AcceptSuggested();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    node.addContent(new Element("option").setAttribute("name", "REPORT_VARIABLES").setAttribute("value", String.valueOf(REPORT_VARIABLES)));
    node.addContent(new Element("option").setAttribute("name", "REPORT_PARAMETERS").setAttribute("value", String.valueOf(REPORT_PARAMETERS)));
    if (!REPORT_CATCH_PARAMETERS) {
      node.addContent(new Element("option").setAttribute("name", "REPORT_CATCH_PARAMETERS").setAttribute("value", "false"));
    }
    if (!REPORT_FOREACH_PARAMETERS) {
      node.addContent(new Element("option").setAttribute("name", "REPORT_FOREACH_PARAMETERS").setAttribute("value", "false"));
    }
    if (!REPORT_IMPLICIT_FINALS) {
      node.addContent(new Element("option").setAttribute("name", "REPORT_IMPLICIT_FINALS").setAttribute("value", "false"));
    }
  }

  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> list = checkCodeBlock(method.getBody(), manager, isOnTheFly);
    return list == null ? null : list.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> allProblems = null;
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      final List<ProblemDescriptor> problems = checkCodeBlock(initializer.getBody(), manager, isOnTheFly);
      if (problems != null) {
        if (allProblems == null) {
          allProblems = new ArrayList<>(1);
        }
        allProblems.addAll(problems);
      }
    }
    return allProblems == null ? null : allProblems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Nullable
  private List<ProblemDescriptor> checkCodeBlock(final PsiCodeBlock body, final InspectionManager manager, final boolean onTheFly) {
    if (body == null) return null;
    final ControlFlow flow;
    try {
      ControlFlowPolicy policy = new ControlFlowPolicy() {
        @Override
        public PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
          if (refExpr.isQualified()) return null;

          PsiElement refElement = refExpr.resolve();
          if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
            if (!isVariableDeclaredInMethod((PsiVariable)refElement)) return null;
            return (PsiVariable)refElement;
          }

          return null;
        }

        @Override
        public boolean isParameterAccepted(@NotNull PsiParameter psiParameter) {
          return isVariableDeclaredInMethod(psiParameter);
        }

        @Override
        public boolean isLocalVariableAccepted(@NotNull PsiLocalVariable psiVariable) {
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

    final Collection<PsiVariable> writtenVariables = ControlFlowUtil.getWrittenVariables(flow, start, end, false);

    final List<ProblemDescriptor> problems = new ArrayList<>();
    final HashSet<PsiVariable> result = new HashSet<>();
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitCodeBlock(PsiCodeBlock block) {
        if (block.getParent() instanceof PsiLambdaExpression && block != body) {
          final List<ProblemDescriptor> descriptors = checkCodeBlock(block, manager, onTheFly);
          if (descriptors != null) {
            problems.addAll(descriptors);
          }
          return;
        }
        super.visitCodeBlock(block);
        Set<PsiVariable> declared = getDeclaredVariables(block);
        if (declared.isEmpty()) return;
        PsiElement anchor = block;
        if (block.getParent() instanceof PsiSwitchBlock) {
          anchor = block.getParent();

          //special case: switch legs
          Set<PsiReferenceExpression> writeRefs = 
            SyntaxTraverser.psiTraverser().withRoot(block)
              .filter(PsiReferenceExpression.class)
              .filter(ref -> PsiUtil.isOnAssignmentLeftHand(ref)).toSet();

          for (PsiReferenceExpression ref : writeRefs) {
            PsiElement resolve = ref.resolve();
            if (resolve instanceof PsiVariable && declared.contains(resolve) && ((PsiVariable)resolve).hasInitializer()) {
              declared.remove(resolve);
            }
          }
        }

        int from = flow.getStartOffset(anchor);
        int end = flow.getEndOffset(anchor);
        List<PsiVariable> ssa = ControlFlowUtil.getSSAVariables(flow, from, end, true);
       
        for (PsiVariable psiVariable : ssa) {
          if (declared.contains(psiVariable)) {
            result.add(psiVariable);
          }
        }
      }

      @Override
      public void visitResourceVariable(PsiResourceVariable variable) {
        if (PsiTreeUtil.getParentOfType(variable, PsiClass.class) != PsiTreeUtil.getParentOfType(body, PsiClass.class)) {
          return;
        }
        result.add(variable);
      }

      @Override
      public void visitCatchSection(PsiCatchSection section) {
        super.visitCatchSection(section);
        if (!REPORT_CATCH_PARAMETERS) return;
        final PsiParameter parameter = section.getParameter();
        if (PsiTreeUtil.getParentOfType(parameter, PsiClass.class) != PsiTreeUtil.getParentOfType(body, PsiClass.class)) {
          return;
        }
        final PsiCodeBlock catchBlock = section.getCatchBlock();
        if (catchBlock == null) return;
        final int from = flow.getStartOffset(catchBlock);
        final int end = flow.getEndOffset(catchBlock);
        if (!ControlFlowUtil.getWrittenVariables(flow, from, end, false).contains(parameter)) {
          writtenVariables.remove(parameter);
          result.add(parameter);
        }
      }

      @Override public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        if (!REPORT_FOREACH_PARAMETERS) return;
        final PsiParameter param = statement.getIterationParameter();
        if (PsiTreeUtil.getParentOfType(param, PsiClass.class) != PsiTreeUtil.getParentOfType(body, PsiClass.class)) {
          return;
        }
        final PsiStatement body = statement.getBody();
        if (body == null) return;
        int from = flow.getStartOffset(body);
        int end = flow.getEndOffset(body);
        if (!ControlFlowUtil.getWrittenVariables(flow, from, end, false).contains(param)) {
          writtenVariables.remove(param);
          result.add(param);
        }
      }

      private Set<PsiVariable> getDeclaredVariables(PsiCodeBlock block) {
        final HashSet<PsiVariable> result = new HashSet<>();
        PsiElement[] children = block.getChildren();
        for (PsiElement child : children) {
          child.accept(new JavaElementVisitor() {
            @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
              visitReferenceElement(expression);
            }

            @Override public void visitDeclarationStatement(PsiDeclarationStatement statement) {
              PsiElement[] declaredElements = statement.getDeclaredElements();
              for (PsiElement declaredElement : declaredElements) {
                if (declaredElement instanceof PsiVariable) result.add((PsiVariable)declaredElement);
              }
            }

            @Override
            public void visitForStatement(PsiForStatement statement) {
              super.visitForStatement(statement);
              final PsiStatement initialization = statement.getInitialization();
              if (!(initialization instanceof PsiDeclarationStatement)) {
                return;
              }
              final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)initialization;
              final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
              for (final PsiElement declaredElement : declaredElements) {
                if (declaredElement instanceof PsiVariable) {
                  result.add((PsiVariable)declaredElement);
                }
              }
            }
          });
        }

        return result;
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.getParent() instanceof PsiMethodCallExpression) {
          super.visitReferenceExpression(expression);
        }
      }
    });

    if (body.getParent() instanceof PsiMethod && REPORT_PARAMETERS) {
      final PsiMethod method = (PsiMethod)body.getParent();
      if (!(method instanceof SyntheticElement)) { // e.g. JspHolderMethod
        Collections.addAll(result, method.getParameterList().getParameters());
      }
    }

    for (Iterator<PsiVariable> iterator = result.iterator(); iterator.hasNext(); ) {
      final PsiVariable variable = iterator.next();
      if (shouldBeIgnored(variable)) {
        iterator.remove();
        continue;
      }
      final PsiElement parent = variable.getParent();
      if (!(parent instanceof PsiDeclarationStatement)) {
        continue;
      }
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)parent;
      final PsiElement[] elements = declarationStatement.getDeclaredElements();
      final PsiElement grandParent = parent.getParent();
      if (elements.length > 1 && grandParent instanceof PsiForStatement) {
        iterator.remove(); // do not report when more than 1 variable declared in for loop
      }
    }

    for (PsiVariable writtenVariable : writtenVariables) {
      if (writtenVariable instanceof PsiParameter) {
        result.remove(writtenVariable);
      }
    }

    if (result.isEmpty() && problems.isEmpty()) return null;

    for (PsiVariable variable : result) {
      if (!variable.isPhysical()) continue;
      final PsiIdentifier nameIdentifier = variable.getNameIdentifier();
      PsiElement problemElement = nameIdentifier != null ? nameIdentifier : variable;
      if (variable instanceof PsiParameter && !(((PsiParameter)variable).getDeclarationScope() instanceof PsiForeachStatement)) {
        problems.add(manager.createProblemDescriptor(problemElement,
                                                     JavaAnalysisBundle.message("inspection.can.be.local.parameter.problem.descriptor"),
                                                     myQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly));
      }
      else {
        problems.add(manager.createProblemDescriptor(problemElement,
                                                     JavaAnalysisBundle.message("inspection.can.be.local.variable.problem.descriptor"),
                                                     myQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, onTheFly));
      }
    }

    return problems;
  }

  private boolean shouldBeIgnored(PsiVariable psiVariable) {
    PsiModifierList modifierList = psiVariable.getModifierList();
    if (modifierList == null) return true;
    if (modifierList.hasExplicitModifier(PsiModifier.FINAL)) return true;
    if (!REPORT_IMPLICIT_FINALS && modifierList.hasModifierProperty(PsiModifier.FINAL)) return true;
    if (psiVariable instanceof PsiLocalVariable) {
      return !REPORT_VARIABLES;
    }
    if (psiVariable instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)psiVariable;
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiCatchSection) {
        return !REPORT_CATCH_PARAMETERS;
      }
      else if (declarationScope instanceof PsiForeachStatement) {
        return !REPORT_FOREACH_PARAMETERS;
      }
      return !REPORT_PARAMETERS;
    }
    return true;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.code.style.issues");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private static class AcceptSuggested implements LocalQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.can.be.final.accept.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problem) {
      PsiElement nameIdentifier = problem.getPsiElement();
      if (nameIdentifier == null) return;
      PsiVariable psiVariable = PsiTreeUtil.getParentOfType(nameIdentifier, PsiVariable.class, false);
      if (psiVariable == null) return;
      psiVariable.normalizeDeclaration();
      PsiUtil.setModifierProperty(psiVariable, PsiModifier.FINAL, true);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(JavaAnalysisBundle.message("inspection.local.can.be.final.option"), "REPORT_VARIABLES");
    panel.addCheckbox(JavaAnalysisBundle.message("inspection.local.can.be.final.option1"), "REPORT_PARAMETERS");
    panel.addCheckbox(JavaAnalysisBundle.message("inspection.local.can.be.final.option2"), "REPORT_CATCH_PARAMETERS");
    panel.addCheckbox(JavaAnalysisBundle.message("inspection.local.can.be.final.option3"), "REPORT_FOREACH_PARAMETERS");
    panel.addCheckbox(JavaAnalysisBundle.message("inspection.local.can.be.final.option4"), "REPORT_IMPLICIT_FINALS");
    return panel;
  }
}
