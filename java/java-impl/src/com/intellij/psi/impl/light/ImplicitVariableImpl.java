package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public abstract class ImplicitVariableImpl extends LightVariableBase implements ImplicitVariable {

  public ImplicitVariableImpl(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImplicitVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "Implicit variable:" + getName();
  }

  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @NotNull
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }
}
