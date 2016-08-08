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
package com.intellij.codeInspection.localCanBeFinal;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public class LocalCanBeFinal extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal");

  public boolean REPORT_VARIABLES = true;
  public boolean REPORT_PARAMETERS = true;
  public boolean REPORT_CATCH_PARAMETERS = true;
  public boolean REPORT_FOREACH_PARAMETERS = true;

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
  }

  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> list = checkCodeBlock(method.getBody(), manager, isOnTheFly);
    return list == null ? null : list.toArray(new ProblemDescriptor[list.size()]);
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
    return allProblems == null ? null : allProblems.toArray(new ProblemDescriptor[allProblems.size()]);
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
            result.add(psiVariable);
          }
        }
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

      private HashSet<PsiElement> getDeclaredVariables(PsiCodeBlock block) {
        final HashSet<PsiElement> result = new HashSet<>();
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
                  result.add(declaredElement);
                }
              }
            }
          });
        }

        return result;
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
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
      final PsiIdentifier nameIdentifier = variable.getNameIdentifier();
      PsiElement problemElement = nameIdentifier != null ? nameIdentifier : variable;
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

  private boolean shouldBeIgnored(PsiVariable psiVariable) {
    if (psiVariable.hasModifierProperty(PsiModifier.FINAL)) return true;
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
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.local.can.be.final.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  private static class AcceptSuggested implements LocalQuickFix {
    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.can.be.final.accept.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problem) {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(problem.getPsiElement())) return;
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

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionsBundle.message("inspection.local.can.be.final.option"), "REPORT_VARIABLES");
    panel.addCheckbox(InspectionsBundle.message("inspection.local.can.be.final.option1"), "REPORT_PARAMETERS");
    panel.addCheckbox(InspectionsBundle.message("inspection.local.can.be.final.option2"), "REPORT_CATCH_PARAMETERS");
    panel.addCheckbox(InspectionsBundle.message("inspection.local.can.be.final.option3"), "REPORT_FOREACH_PARAMETERS");
    return panel;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }
}
