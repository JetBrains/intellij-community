package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.psi.impl.source.tree.ChildRole;

/**
 * @author dsl
 */
public abstract class PsiImportStatementBaseImpl extends JavaStubPsiElement<PsiImportStatementStub> implements PsiImportStatementBase{
  public static final PsiImportStatementBaseImpl[] EMPTY_ARRAY = new PsiImportStatementBaseImpl[0];

  protected PsiImportStatementBaseImpl(final PsiImportStatementStub stub) {
    super(stub, stub.isStatic() ? JavaStubElementTypes.IMPORT_STATIC_STATEMENT : JavaStubElementTypes.IMPORT_STATEMENT);
  }

  protected PsiImportStatementBaseImpl(final ASTNode node) {
    super(node);
  }

  public boolean isOnDemand(){
    final PsiImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isOnDemand();
    }

    return calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_ON_DEMAND_DOT) != null;
  }

  public PsiJavaCodeReferenceElement getImportReference() {
    final PsiImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getReference();
    }
    return (PsiJavaCodeReferenceElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_REFERENCE);
  }

  public PsiElement resolve() {
    final PsiJavaCodeReferenceElement reference = getImportReference();
    return reference == null ? null : reference.resolve();
  }
}