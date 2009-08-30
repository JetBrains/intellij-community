package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class PsiTypeParameterListImpl extends JavaStubPsiElement<PsiTypeParameterListStub> implements PsiTypeParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl");

  public PsiTypeParameterListImpl(final PsiTypeParameterListStub stub) {
    super(stub, JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  public PsiTypeParameterListImpl(final ASTNode node) {
    super(node);
  }

  public PsiTypeParameter[] getTypeParameters() {
    return getStubOrPsiChildren(JavaStubElementTypes.TYPE_PARAMETER, PsiTypeParameter.ARRAY_FACTORY);
  }

  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    LOG.assertTrue(typeParameter.getParent() == this);
    return PsiImplUtil.getTypeParameterIndex(typeParameter, this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    final PsiTypeParameter[] parameters = getTypeParameters();
    for (final PsiTypeParameter parameter : parameters) {
      if (!processor.execute(parameter, state)) return false;
    }
    return true;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiTypeParameterList";
  }
}
