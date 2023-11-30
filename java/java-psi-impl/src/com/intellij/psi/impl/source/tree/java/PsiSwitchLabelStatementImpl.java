// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public class PsiSwitchLabelStatementImpl extends PsiSwitchLabelStatementBaseImpl implements PsiSwitchLabelStatement {
  private static final Logger LOG = Logger.getInstance(PsiSwitchLabelStatementImpl.class);

  public PsiSwitchLabelStatementImpl() {
    super(JavaElementType.SWITCH_LABEL_STATEMENT);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.CASE_KEYWORD: return findChildByType(JavaTokenType.CASE_KEYWORD);
      case ChildRole.DEFAULT_KEYWORD: return findChildByType(JavaTokenType.DEFAULT_KEYWORD);
      case ChildRole.CASE_EXPRESSION: return findChildByType(ElementType.EXPRESSION_BIT_SET);
      case ChildRole.COLON: return findChildByType(JavaTokenType.COLON);
      default: return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.CASE_KEYWORD) return ChildRole.CASE_KEYWORD;
    if (i == JavaTokenType.DEFAULT_KEYWORD) return ChildRole.DEFAULT_KEYWORD;
    if (i == JavaTokenType.COLON) return ChildRole.COLON;
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) return ChildRole.CASE_EXPRESSION;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchLabelStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSwitchLabelStatement";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!super.processDeclarations(processor, state, lastParent, place)) return false;

    return !shouldAnalyzePatternVariablesInCaseLabel(place) || processPatternVariables(processor, state, place);
  }

  /**
   * Checks if the pattern variables in the case label list can be analyzed. The processing is allowed in the two following cases:
   * <ol>
   *   <li>The place that is being resolved is a code block. It just wants to build the map of the local and pattern variables in {@link PsiCodeBlockImpl#buildMaps}.</li>
   *   <li>The current {@link PsiSwitchLabelStatement} is the switch label where it's legal to resolve the passed element.
   *      <p>
   *        Read more about scopes of variables in pattern matching for switch statements in
   *        <a href="https://openjdk.org/jeps/406#3--Scope-of-pattern-variable-declarations">the JEP</a>.
   *      </p>
   *   </li>
   * </ol>
   *
   * @param place element that is being resolved
   * @return true when the pattern variables in the case label list can be analyzed.
   */
  private boolean shouldAnalyzePatternVariablesInCaseLabel(@NotNull PsiElement place) {
    if (place instanceof PsiCodeBlock) return true;
    boolean java20plus = PsiUtil.getLanguageLevel(place).isAtLeast(LanguageLevel.JDK_20_PREVIEW);
    final AtomicBoolean thisSwitchLabelIsImmediate = new AtomicBoolean();

    PsiTreeUtil.treeWalkUp(place, getParent(), (currentScope, __) -> {

      PsiSwitchLabelStatementBase immediateSwitchLabel;

      if (currentScope instanceof PsiSwitchLabelStatementBase) {
        immediateSwitchLabel = (PsiSwitchLabelStatementBase)currentScope;
      }
      else {
        immediateSwitchLabel = PsiTreeUtil.getPrevSiblingOfType(currentScope, PsiSwitchLabelStatementBase.class);
      }

      while (immediateSwitchLabel != null && !java20plus &&
             (PsiTreeUtil.getPrevSiblingOfType(immediateSwitchLabel, PsiStatement.class) instanceof PsiSwitchLabelStatementBase &&
              isCaseNull(immediateSwitchLabel))) {
       /*
        Example 1:
          case null:
            System.out.println("null");
          case String s: // illegal fall-through to a pattern in any version of Java
            System.out.println("String " + s);
            break;
        Example 2:
           case null: case String s: // legal in Java 17-19 Preview, illegal in Java 20 Preview
             System.out.println("String " + s);
             break;
        */
        immediateSwitchLabel = PsiTreeUtil.getPrevSiblingOfType(immediateSwitchLabel, PsiSwitchLabelStatementBase.class);
      }

      if (immediateSwitchLabel == this) {
        thisSwitchLabelIsImmediate.set(true);
        return false;
      }
      return true;
    });

    return thisSwitchLabelIsImmediate.get();
  }

  private static boolean isCaseNull(@NotNull PsiSwitchLabelStatementBase switchCaseLabel) {
    if (switchCaseLabel.getCaseLabelElementList() == null) return false;

    final PsiCaseLabelElement[] elements = switchCaseLabel.getCaseLabelElementList().getElements();
    if (elements.length != 1) return false;

    return elements[0].getNode().getFirstChildNode().getElementType() == JavaTokenType.NULL_KEYWORD;
  }
}
