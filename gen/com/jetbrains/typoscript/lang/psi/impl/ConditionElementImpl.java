// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.jetbrains.typoscript.lang.TypoScriptElementTypes.*;
import com.jetbrains.typoscript.lang.psi.TypoScriptCompositeElementImpl;
import com.jetbrains.typoscript.lang.psi.*;

public class ConditionElementImpl extends TypoScriptCompositeElementImpl implements ConditionElement {

  public ConditionElementImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof Visitor) ((Visitor)visitor).visitConditionElement(this);
    else super.accept(visitor);
  }

}
