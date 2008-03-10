/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;

public class StubBuilder {
  public static StubElement buildStubElement(StubBasedPsiElement psi) {
    return buildStubTreeFor(psi, null);
  }

  private static StubElement buildStubTreeFor(PsiElement elt, StubElement parentStub) {
    StubElement stub = parentStub;
    if (elt instanceof StubBasedPsiElement) {
      final IStubElementType type = ((StubBasedPsiElement)elt).getElementType();

      //noinspection unchecked
      stub = type.createStub(elt, parentStub);
    }

    final PsiElement[] psiElements = elt.getChildren();
    for (PsiElement child : psiElements) {
      buildStubTreeFor(child, stub);
    }

    return stub;
  }

}