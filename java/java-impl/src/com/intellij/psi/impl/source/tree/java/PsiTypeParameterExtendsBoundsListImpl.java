package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PsiTypeParameterExtendsBoundsListImpl extends JavaStubPsiElement<PsiClassReferenceListStub> implements PsiReferenceList {
  public PsiTypeParameterExtendsBoundsListImpl(final PsiClassReferenceListStub stub, final IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public PsiTypeParameterExtendsBoundsListImpl(final ASTNode node) {
    super(node);
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return calcTreeElement().getChildrenAsPsiElements(Constants.JAVA_CODE_REFERENCE_BIT_SET,
                                                      Constants.PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    final PsiClassReferenceListStub stub = getStub();
    if (stub != null) return stub.getReferencedTypes();

    return createTypes(getReferenceElements());
  }

  public Role getRole() {
    return Role.EXTENDS_BOUNDS_LIST;
  }

  private PsiClassType[] createTypes(final PsiJavaCodeReferenceElement[] refs) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiClassType[] types = new PsiClassType[refs.length];
    for (int i = 0; i < refs.length; i++) {
      types[i] = factory.createType(refs[i]);
    }
    return types;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiElement(EXTENDS_BOUND_LIST)";
  }
}
