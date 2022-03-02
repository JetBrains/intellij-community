// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.HighlightUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Pavel.Dolgov
 */
public final class DuplicateExpressionsInspection extends LocalInspectionTool {
  public int complexityThreshold = 70;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);

        if (expression instanceof PsiParenthesizedExpression) {
          return;
        }
        visitExpressionImpl(expression);
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);

        if (expression.getParent() instanceof PsiCallExpression) {
          return;
        }
        visitExpressionImpl(expression);
      }

      public void visitExpressionImpl(PsiExpression expression) {
        if (ComplexityCalculator.isDefinitelySimple(expression) ||
            SideEffectCalculator.isDefinitelyWithSideEffect(expression) ||
            expression instanceof PsiLambdaExpression ||
            ExpressionUtils.isVoidContext(expression)) {
          return;
        }
        DuplicateExpressionsContext context = DuplicateExpressionsContext.getOrCreateContext(expression, session);
        if (context == null || context.mayHaveSideEffect(expression)) {
          return;
        }
        if (context.getComplexity(expression) > complexityThreshold) {
          context.addOccurrence(expression);
        }
      }

      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);

        PsiCodeBlock body = method.getBody();
        if (body != null) {
          registerProblemsForExpressions(body, session);
        }
      }

      @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
        super.visitClassInitializer(initializer);

        registerProblemsForExpressions(initializer.getBody(), session);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);

        PsiElement body = expression.getBody();
        if (body instanceof PsiCodeBlock) {
          registerProblemsForExpressions((PsiCodeBlock)body, session);
        }
      }

      public void registerProblemsForExpressions(@NotNull PsiCodeBlock body, @NotNull UserDataHolder session) {
        DuplicateExpressionsContext context = DuplicateExpressionsContext.getContext(body, session);
        if (context == null) return;

        Set<PsiExpression> processed = new HashSet<>();
        context.forEach((pattern, occurrences) -> {
          if (!processed.contains(pattern)) {
            processed.addAll(occurrences);
            registerProblems(occurrences, body);
          }
        });
      }

      public void registerProblems(@NotNull List<? extends PsiExpression> occurrences, @NotNull PsiCodeBlock body) {
        if (occurrences.size() > 1 && areSafeToExtract(occurrences, body)) {
          Map<PsiExpression, List<PsiVariable>> reusableVariables = collectReusableVariables(occurrences);
          for (PsiExpression occurrence : occurrences) {
            List<LocalQuickFix> fixes = new ArrayList<>();
            List<PsiVariable> variables = reusableVariables.get(occurrence);
            if (variables != null) {
              for (PsiVariable variable : variables) {
                fixes.add(new ReuseVariableFix(occurrence, variable));
              }
            }
            PsiVariable variable = findVariableByInitializer(occurrence);
            if (variable != null && canReplaceOtherOccurrences(occurrence, occurrences, variable)) {
              fixes.add(new ReplaceOtherOccurrencesFix(occurrence, variable));
            }
            else if (isOnTheFly) {
              fixes.add(new IntroduceVariableFix(occurrence));
            }
            holder.registerProblem(occurrence, JavaBundle.message("inspection.duplicate.expressions.message"),
                                   fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
          }
        }
      }
    };
  }

  private static boolean areSafeToExtract(@NotNull List<? extends PsiExpression> occurrences, @NotNull PsiCodeBlock body) {
    if (occurrences.isEmpty()) return false;
    Project project = occurrences.get(0).getProject();
    Set<PsiVariable> variables = collectVariablesSafeToExtract(occurrences);
    if (variables == null) return false;
    if (variables.isEmpty()) return true;

    PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(occurrences.toArray(PsiExpression.EMPTY_ARRAY), null);
    if (anchor == null) return false;
    PsiElement anchorParent = anchor.getParent();
    if (anchorParent == null) return false;
    try {
      ControlFlow flow = ControlFlowFactory.getInstance(project).getControlFlow(body, new LocalsControlFlowPolicy(body));
      int startOffset = flow.getSize();
      int endOffset = 0;

      for (PsiExpression occurrence : occurrences) {
        PsiElement occurrenceSurroundings = PsiTreeUtil.findFirstParent(occurrence, false, e -> e.getParent() == anchorParent);
        if (occurrenceSurroundings == null) return false;
        startOffset = Math.min(startOffset, flow.getStartOffset(occurrenceSurroundings));
        endOffset = Math.max(endOffset, flow.getEndOffset(occurrenceSurroundings));
      }
      return ControlFlowUtil.areVariablesUnmodifiedAtLocations(flow, startOffset, endOffset, variables, occurrences);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  @Nullable
  private static Set<PsiVariable> collectVariablesSafeToExtract(@NotNull List<? extends PsiExpression> occurrences) {
    Set<PsiVariable> variables = new HashSet<>();
    Ref<Boolean> refFailed = new Ref<>(Boolean.FALSE);
    JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);

        PsiElement resolved = reference.resolve();
        if (PsiUtil.isJvmLocalVariable(resolved)) {
          variables.add((PsiVariable)resolved);
        }
        else if (resolved instanceof PsiVariable && !((PsiVariable)resolved).hasModifierProperty(PsiModifier.FINAL)) {
          refFailed.set(Boolean.TRUE);
          stopWalking();
        }
      }
    };

    for (PsiExpression occurrence : occurrences) {
      occurrence.accept(visitor);
      if (refFailed.get()) {
        return null;
      }
    }

    return variables;
  }

  @NotNull
  private static Map<PsiExpression, List<PsiVariable>> collectReusableVariables(@NotNull List<? extends PsiExpression> occurrences) {
    if (occurrences.size() <= 1) {
      return Collections.emptyMap();
    }
    Map<PsiVariable, PsiExpression> initializers = new HashMap<>();
    for (PsiExpression occurrence : occurrences) {
      PsiVariable variable = findVariableByInitializer(occurrence);
      if (variable != null) {
        initializers.put(variable, occurrence);
      }
    }
    if (initializers.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<PsiExpression, List<PsiVariable>> result = new HashMap<>();
    initializers.forEach((variable, initializer) -> {
      for (PsiExpression occurrence : occurrences) {
        if (occurrence != initializer && canReplaceWith(occurrence, variable)) {
          result.computeIfAbsent(occurrence, unused -> new ArrayList<>()).add(variable);
        }
      }
    });
    return result;
  }

  private static boolean canReplaceOtherOccurrences(@NotNull PsiExpression originalOccurrence,
                                                    @NotNull List<? extends PsiExpression> occurrences,
                                                    @NotNull PsiVariable variable) {
    return occurrences.stream().anyMatch(occurrence -> occurrence != originalOccurrence && canReplaceWith(occurrence, variable));
  }

  private static boolean canReplaceWith(@NotNull PsiExpression occurrence, @NotNull PsiVariable variable) {
    String variableName = variable.getName();
    if (variableName == null) {
      return false;
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
    PsiExpression refExpr;
    try {
      refExpr = factory.createExpressionFromText(variableName, occurrence);
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    return refExpr instanceof PsiReferenceExpression && ((PsiReferenceExpression)refExpr).resolve() == variable;
  }

  @Nullable
  private static PsiVariable findVariableByInitializer(@NotNull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;
      if (PsiTreeUtil.isAncestor(variable.getInitializer(), expression, false)) {
        return variable;
      }
    }
    return null;
  }

  @Override
  public @NotNull JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(
      JavaBundle.message("inspection.duplicate.expressions.complexity.threshold"), this, "complexityThreshold", 3);
  }

  private static final class IntroduceVariableFix implements LocalQuickFix {
    private final String myExpressionText;

    private IntroduceVariableFix(@NotNull PsiExpression expression) {myExpressionText = expression.getText();}

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.duplicate.expressions.introduce.variable.fix.family.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.duplicate.expressions.introduce.variable.fix.name", myExpressionText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpression expression = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiExpression.class);
      if (expression == null) return;
      PsiCodeBlock codeBlock = DuplicateExpressionsContext.findNearestBody(expression);
      if (codeBlock == null) return;
      DuplicateExpressionCollector expressionCollector = new DuplicateExpressionCollector(expression);
      codeBlock.accept(expressionCollector);
      PsiExpression[] occurrences = expressionCollector.getOccurrences();
      if (occurrences.length < 2) return;
      PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(occurrences, codeBlock);
      if (anchor == null) return;
      PsiElement parent = anchor.getParent();
      if (parent == null) return;
      PsiExpression canonicalExpression = expressionCollector.myPattern;
      PsiDeclarationStatement declaration = createDeclaration(codeBlock, canonicalExpression, anchor);
      if (declaration == null) return;
      declaration = addDeclaration(anchor, parent, declaration);
      PsiVariable declared = (PsiVariable)declaration.getDeclaredElements()[0];
      String pathVarName = Objects.requireNonNull(declared.getName());
      PsiReference[] refs = new PsiReference[occurrences.length];
      for (int i = 0; i < occurrences.length; i++) {
        PsiExpression occurrence = occurrences[i];
        refs[i] = (PsiReference)new CommentTracker().replaceAndRestoreComments(occurrence, pathVarName);
      }
      HighlightUtils.showRenameTemplate(codeBlock, declared, refs);
    }

    @NotNull
    private static PsiDeclarationStatement addDeclaration(PsiElement anchor, @NotNull PsiElement parent, PsiDeclarationStatement declaration) {
      declaration = (PsiDeclarationStatement)parent.addBefore(declaration, anchor);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(parent.getProject());
      declaration = (PsiDeclarationStatement)codeStyleManager.shortenClassReferences(declaration);
      return declaration;
    }

    @Nullable
    private static PsiDeclarationStatement createDeclaration(@NotNull PsiCodeBlock block,
                                                             @NotNull PsiExpression initializer,
                                                             @NotNull PsiElement anchor) {
      PsiType varType = initializer.getType();
      if (varType == null) return null;
      String varName = getSuggestedName(varType, initializer, anchor);
      if (varName == null) return null;
      PsiElementFactory elementFactory = PsiElementFactory.getInstance(block.getProject());
      return elementFactory.createVariableDeclarationStatement(varName, varType, initializer);
    }

    private static @Nullable String getSuggestedName(@NotNull PsiType type,
                                                     @NotNull PsiExpression initializer,
                                                     @NotNull PsiElement anchor) {
      SuggestedNameInfo nameInfo = CommonJavaRefactoringUtil.getSuggestedName(type, initializer, anchor);
      String[] names = nameInfo.names;
      return names.length == 0 ? null : names[0];
    }

    private static class DuplicateExpressionCollector extends JavaRecursiveElementWalkingVisitor {

      private static final ExpressionHashingStrategy HASHING_STRATEGY = new ExpressionHashingStrategy();
      private final List<PsiExpression> myOccurrences = new ArrayList<>();
      private final PsiExpression myPattern;
      private final CanonicalExpressionProvider myCanonicalExpressionProvider = new CanonicalExpressionProvider();

      private DuplicateExpressionCollector(PsiExpression pattern) {
        myPattern = myCanonicalExpressionProvider.getCanonicalExpression(pattern);
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        PsiExpression canonicalExpression = myCanonicalExpressionProvider.getCanonicalExpression(expression);
        if (HASHING_STRATEGY.equals(canonicalExpression, myPattern)) myOccurrences.add(expression);
      }

      public PsiExpression @NotNull [] getOccurrences() {
        return myOccurrences.toArray(PsiExpression.EMPTY_ARRAY);
      }
    }
  }

  private static final class ReuseVariableFix implements LocalQuickFix {
    private final String myExpressionText;
    private final String myVariableName;

    private ReuseVariableFix(@NotNull PsiExpression expression, @NotNull PsiVariable variable) {
      myExpressionText = expression.getText();
      myVariableName = variable.getName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.duplicate.expressions.reuse.variable.fix.family.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.duplicate.expressions.reuse.variable.fix.name", myVariableName, myExpressionText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiExpression) {
        new CommentTracker().replaceAndRestoreComments(element, myVariableName);
      }
    }
  }

  private static final class ReplaceOtherOccurrencesFix implements LocalQuickFix {
    private final String myExpressionText;
    private final String myVariableName;

    private ReplaceOtherOccurrencesFix(@NotNull PsiExpression expression, @NotNull PsiVariable variable) {
      myExpressionText = expression.getText();
      myVariableName = variable.getName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.duplicate.expressions.replace.other.occurrences.fix.family.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.duplicate.expressions.replace.other.occurrences.fix.name", myVariableName, myExpressionText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiExpression) {
        List<PsiExpression> occurrences = collectReplaceableOccurrences((PsiExpression)element);
        for (PsiExpression occurrence : occurrences) {
          new CommentTracker().replaceAndRestoreComments(occurrence, myVariableName);
        }
      }
    }

    @NotNull
    private static List<PsiExpression> collectReplaceableOccurrences(@NotNull PsiExpression originalExpr) {
      PsiVariable variable = findVariableByInitializer(originalExpr);
      PsiCodeBlock nearestBody = DuplicateExpressionsContext.findNearestBody(originalExpr);
      if (variable != null && nearestBody != null) {
        List<PsiExpression> replaceableOccurrences = new ArrayList<>();
        nearestBody.accept(new JavaRecursiveElementWalkingVisitor() {
          final ExpressionHashingStrategy hashingStrategy = new ExpressionHashingStrategy();

          @Override
          public void visitExpression(PsiExpression occurrence) {
            super.visitExpression(occurrence);

            if (occurrence != originalExpr &&
                hashingStrategy.equals(occurrence, originalExpr) &&
                canReplaceWith(occurrence, variable)) {
              replaceableOccurrences.add(occurrence);
            }
          }
        });
        return replaceableOccurrences;
      }
      return Collections.emptyList();
    }
  }
}
