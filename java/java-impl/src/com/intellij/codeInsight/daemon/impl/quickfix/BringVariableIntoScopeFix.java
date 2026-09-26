// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiResourceVariable;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BringVariableIntoScopeFix implements ModCommandAction {
  private static final Logger LOG = Logger.getInstance(BringVariableIntoScopeFix.class);
  private final @NotNull PsiReferenceExpression myUnresolvedReference;
  private final @NotNull PsiLocalVariable myOutOfScopeVariable;

  private BringVariableIntoScopeFix(@NotNull PsiReferenceExpression unresolvedReference, @NotNull PsiLocalVariable variable) {
    myUnresolvedReference = unresolvedReference;
    myOutOfScopeVariable = variable;
  }

  public PsiLocalVariable getVariable() {
    return myOutOfScopeVariable;
  }

  public static @Nullable BringVariableIntoScopeFix fromReference(PsiReferenceExpression unresolvedReference) {
    if (unresolvedReference.isQualified()) return null;
    final String referenceName = unresolvedReference.getReferenceName();
    if (referenceName == null) return null;

    PsiElement container = getContainer(unresolvedReference);
    if (container == null) return null;

    class Visitor extends JavaRecursiveElementWalkingVisitor {
      int variableCount = 0;
      PsiLocalVariable myOutOfScopeVariable;

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {}

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        //Don't look inside expressions
      }

      @Override
      public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
        if (referenceName.equals(variable.getName())) {
          myOutOfScopeVariable = variable;
          variableCount++;
          if (variableCount > 1) {
            stopWalking();
          }
        }
      }
    }
    Visitor visitor = new Visitor();
    container.accept(visitor);

    if (visitor.variableCount != 1 || visitor.myOutOfScopeVariable instanceof PsiResourceVariable) return null;
    // E.g., reference in annotation
    if (PsiTreeUtil.isAncestor(visitor.myOutOfScopeVariable, unresolvedReference, true)) return null;
    return new BringVariableIntoScopeFix(unresolvedReference, visitor.myOutOfScopeVariable);
  }

  public static @Nullable PsiElement getContainer(PsiElement unresolvedReference) {
    PsiElement container = PsiTreeUtil.getParentOfType(unresolvedReference, PsiCodeBlock.class, PsiClass.class);
    if (!(container instanceof PsiCodeBlock)) return null;
    while (container.getParent() instanceof PsiStatement || container.getParent() instanceof PsiCatchSection) {
      container = container.getParent();
    }
    return container;
  }

  private @IntentionName @NotNull String getText() {
    PsiLocalVariable variable = myOutOfScopeVariable;

    String varText = !variable.isValid()
                     ? "" : PsiFormatUtil.formatVariable(variable, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
    return QuickFixBundle.message("bring.variable.to.scope.text", varText);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("bring.variable.to.scope.family");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (!(myUnresolvedReference.isValid() && BaseIntentionAction.canModify(myUnresolvedReference) && myOutOfScopeVariable.isValid())) {
      return null;
    }
    return Presentation.of(getText()).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.psiUpdate(myOutOfScopeVariable, (outOfScopeVariable, updater) -> {
      PsiReferenceExpression reference = updater.getWritable(myUnresolvedReference);
      invoke(outOfScopeVariable, reference);
    });
  }

  private static void invoke(@NotNull PsiLocalVariable outOfScopeVariable, @NotNull PsiReferenceExpression reference) {
    PsiFile psiFile = outOfScopeVariable.getContainingFile();
    Project project = psiFile.getProject();
    outOfScopeVariable.normalizeDeclaration();
    PsiUtil.setModifierProperty(outOfScopeVariable, PsiModifier.FINAL, false);
    PsiElement commonParent = PsiTreeUtil.findCommonParent(outOfScopeVariable, reference);
    LOG.assertTrue(commonParent != null);
    PsiElement child = outOfScopeVariable.getTextRange().getStartOffset() < reference.getTextRange().getStartOffset() ?
                       outOfScopeVariable : reference;

    while(child.getParent() != commonParent) child = child.getParent();
    PsiDeclarationStatement newDeclaration = (PsiDeclarationStatement)JavaPsiFacade.getElementFactory(project)
      .createStatementFromText("int i = 0", null);
    PsiVariable variable = (PsiVariable)newDeclaration.getDeclaredElements()[0].replace(outOfScopeVariable);
    if (variable.getInitializer() != null) {
      PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null && typeElement.isInferredType()) {
        PsiTypesUtil.replaceWithExplicitType(typeElement);
      }
      variable.getInitializer().delete();
    }

    while(!(child instanceof PsiStatement) || !(child.getParent() instanceof PsiCodeBlock)) {
      child = child.getParent();
      commonParent = commonParent.getParent();
    }
    LOG.assertTrue(commonParent != null);
    PsiDeclarationStatement added = (PsiDeclarationStatement)commonParent.addBefore(newDeclaration, child);
    final PsiElement[] declaredElements = added.getDeclaredElements();
    LOG.assertTrue(declaredElements.length > 0, added.getText());
    PsiLocalVariable addedVar = (PsiLocalVariable)declaredElements[0];
    assert addedVar != null : added;
    CodeStyleManager.getInstance(project).reformat(commonParent);

    //Leave initializer assignment
    PsiExpression initializer = outOfScopeVariable.getInitializer();
    if (initializer != null) {
      PsiExpressionStatement assignment = (PsiExpressionStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(
        outOfScopeVariable.getName() + "= e;", null);
      Objects.requireNonNull(((PsiAssignmentExpression)assignment.getExpression()).getRExpression()).replace(initializer);
      assignment = (PsiExpressionStatement)CodeStyleManager.getInstance(project).reformat(assignment);
      PsiDeclarationStatement declStatement = PsiTreeUtil.getParentOfType(outOfScopeVariable, PsiDeclarationStatement.class);
      LOG.assertTrue(declStatement != null);
      PsiElement parent = declStatement.getParent();
      if (parent instanceof PsiForStatement) {
        declStatement.replace(assignment);
      }
      else {
        parent.addAfter(assignment, declStatement);
      }
    }

    if (outOfScopeVariable.isValid()) {
      outOfScopeVariable.delete();
    }

    if (!ControlFlowUtil.isInitializedBeforeUsage(reference, addedVar, new HashMap<>(), false)) {
      initialize(addedVar);
    }
  }

  private static void initialize(final PsiLocalVariable variable) throws IncorrectOperationException {
    PsiType type = variable.getType();
    String init = PsiTypesUtil.getDefaultValueOfType(type);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
    PsiExpression initializer = factory.createExpressionFromText(init, variable);
    variable.setInitializer(initializer);
  }

  /**
   * @param position PSI position to find the inner scope variables
   * @return list of variables declared in the scopes nested to the position scope
   */
  public static @NotNull List<PsiLocalVariable> findInnerScopeVariables(PsiElement position) {
    PsiElement container = getContainer(position);
    Map<String, Optional<PsiLocalVariable>> variableMap = Map.of();
    if (container != null) {
      variableMap = EntryStream.ofTree(container, (depth, element) -> depth > 2 ? null : StreamEx.of(element.getChildren()))
        .values()
        .select(PsiCodeBlock.class)
        .flatArray(PsiCodeBlock::getStatements)
        .select(PsiDeclarationStatement.class)
        .flatArray(PsiDeclarationStatement::getDeclaredElements)
        .select(PsiLocalVariable.class)
        .remove(var -> PsiTreeUtil.isAncestor(var, position, true))
        .toMap(PsiLocalVariable::getName, Optional::of, (v1, v2) -> Optional.empty());
      PsiResolveHelper helper = JavaPsiFacade.getInstance(container.getProject()).getResolveHelper();
      variableMap.values().removeAll(Collections.singleton(Optional.<PsiLocalVariable>empty()));
      variableMap.keySet().removeIf(name -> helper.resolveReferencedVariable(name, position) != null);
      int offset = position.getTextRange().getStartOffset();
      variableMap.values().removeIf(v -> v.orElseThrow().getTextRange().getStartOffset() > offset);
    }
    List<PsiLocalVariable> list = ContainerUtil.map(variableMap.values(), Optional::get);
    return list;
  }

  /**
   * @param variable variable to describe its location
   * @return the human-readable description of variable's declaration place, useful for variable brought into the scope. 
   */
  public static @Nls @NotNull String getVariableDeclarationPlace(PsiLocalVariable variable) {
    String place = JavaBundle.message("completion.inner.scope");
    PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    PsiElement statement = block == null ? null : block.getParent();
    if (statement instanceof PsiTryStatement) {
      place = ((PsiTryStatement)statement).getFinallyBlock() == block ? JavaKeywords.TRY + "-" + JavaKeywords.FINALLY : JavaKeywords.TRY;
    }
    else if (statement instanceof PsiCatchSection) {
      place = JavaKeywords.CATCH;
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      place = JavaKeywords.SYNCHRONIZED;
    }
    else if (statement instanceof PsiBlockStatement) {
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiWhileStatement) {
        place = JavaKeywords.WHILE;
      }
      else if (parent instanceof PsiIfStatement ifStatement) {
        place = ifStatement.getThenBranch() == statement ? JavaKeywords.IF + "-then" : JavaKeywords.IF + "-" + JavaKeywords.ELSE;
      }
    }
    return place;
  }
}
