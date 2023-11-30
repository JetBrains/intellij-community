// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DeferFinalAssignmentFix extends PsiUpdateModCommandAction<PsiVariable> {
  private static final Logger LOG = Logger.getInstance(DeferFinalAssignmentFix.class);

  private final PsiReferenceExpression expression;

  public DeferFinalAssignmentFix(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression) {
    super(variable);
    this.expression = expression;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("defer.final.assignment.with.temp.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiVariable variable) {
    boolean available = expression.isValid() && !(variable instanceof PsiParameter) && !(variable instanceof ImplicitVariable);
    return available ? Presentation.of(QuickFixBundle.message("defer.final.assignment.with.temp.text", variable.getName())) : null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiVariable variable, @NotNull ModPsiUpdater updater) {
    if (variable instanceof PsiField field) {
      PsiCodeBlock codeBlock = getEnclosingCodeBlock(field, updater.getWritable(expression));
      if (codeBlock == null) return;
      deferVariable(codeBlock, variable, null, updater);
    }
    else {
      PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
      deferVariable(outerCodeBlock, variable, variable.getParent(), updater);
    }
  }

  private static PsiCodeBlock getEnclosingCodeBlock(PsiField field, PsiElement element) {
    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return null;
    PsiMethod[] constructors = aClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      if (PsiTreeUtil.isAncestor(body, element, true)) return body;
    }

    //maybe inside class initializer ?
    PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      PsiCodeBlock body = initializer.getBody();
      if (PsiTreeUtil.isAncestor(body, element, true)) return body;
    }
    return null;
  }

  private static void deferVariable(@Nullable PsiElement outerCodeBlock, 
                                    @NotNull PsiVariable variable, 
                                    @Nullable PsiElement tempDeclarationAnchor,
                                    @NotNull ModPsiUpdater updater) throws IncorrectOperationException {
    if (outerCodeBlock == null) return;
    List<PsiReferenceExpression> outerReferences = new ArrayList<>();
    collectReferences(outerCodeBlock, variable, outerReferences);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
    Project project = variable.getProject();
    List<String> names =
      new VariableNameGenerator(outerCodeBlock, VariableKind.LOCAL_VARIABLE).byName(variable.getName() + "1").byType(variable.getType())
        .generateAll(true);
    String tempName = names.get(0);
    PsiDeclarationStatement tempVariableDeclaration = factory.createVariableDeclarationStatement(tempName, variable.getType(), null);

    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(project).getControlFlow(outerCodeBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    }
    catch (AnalysisCanceledException e) {
      return;
    }
    int minOffset = 0;
    boolean writeReferenceOccurred = false;
    PsiReferenceExpression writeReference = null;
    for (int i = outerReferences.size()-1; i>=0; i--) {
      PsiReferenceExpression reference = outerReferences.get(i);
      if (!writeReferenceOccurred && !PsiUtil.isAccessedForWriting(reference)) {
        // trailing read references need not be converted to temp var references
        outerReferences.remove(i);
        continue;
      }
      writeReferenceOccurred = true;
      writeReference = reference;
      PsiElement element = PsiUtil.getEnclosingStatement(reference);
      int endOffset = element == null ? -1 : controlFlow.getEndOffset(element);
      minOffset = Math.max(minOffset, endOffset);
    }
    LOG.assertTrue(writeReference != null);
    PsiStatement finalAssignment = factory.createStatementFromText(writeReference.getText()+" = "+tempName+";", outerCodeBlock);
    if (!insertToDefinitelyReachedPlace(outerCodeBlock, finalAssignment, controlFlow, minOffset, outerReferences)) return;

    tempVariableDeclaration = (PsiDeclarationStatement)outerCodeBlock.addAfter(tempVariableDeclaration, tempDeclarationAnchor);

    replaceReferences(outerReferences, factory.createExpressionFromText(tempName, outerCodeBlock));
    updater.rename(((PsiVariable)tempVariableDeclaration.getDeclaredElements()[0]), names);
  }


  private static boolean insertToDefinitelyReachedPlace(PsiElement codeBlock,
                                                        PsiStatement finalAssignment,
                                                        ControlFlow controlFlow,
                                                        int minOffset,
                                                        @NotNull List<? extends PsiElement> references) throws IncorrectOperationException {
    int offset = ControlFlowUtil.getMinDefinitelyReachedOffset(controlFlow, minOffset, references);
    if (offset == controlFlow.getSize()) {
      codeBlock.add(finalAssignment);
      return true;
    }
    PsiElement element = null; //controlFlow.getEndOffset(codeBlock) == offset ? getEnclosingStatement(controlFlow.getElement(offset)) : null;
    while (offset < controlFlow.getSize()) {
      element = controlFlow.getElement(offset);
      while (element != null) {
        if (element.getParent() == codeBlock) break;
        element = element.getParent();
      }
      if (element == null) return false;
      int startOffset = controlFlow.getStartOffset(element);
      if (startOffset != -1 && startOffset >= minOffset && element instanceof PsiStatement) break;
      offset++;
    }
    if (!(offset < controlFlow.getSize())) return false;
    // inside loop
    if (ControlFlowUtil.isInstructionReachable(controlFlow, offset, offset)) return false;
    codeBlock.addBefore(finalAssignment, element);
    return true;
  }

  private static void replaceReferences(List<PsiReferenceExpression> references, PsiElement newExpression) throws IncorrectOperationException {
    for (PsiReferenceExpression reference : references) {
      reference.replace(newExpression);
    }
  }

  private static void collectReferences(PsiElement context, final PsiVariable variable, final List<? super PsiReferenceExpression> references) {
    context.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }
}
