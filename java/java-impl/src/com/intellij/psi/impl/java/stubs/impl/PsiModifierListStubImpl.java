/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiModifierListStubImpl extends StubBase<PsiModifierList> implements PsiModifierListStub {
  private final int myMask;

  public PsiModifierListStubImpl(final StubElement parent, final int mask) {
    super(parent, JavaStubElementTypes.MODIFIER_LIST);
    myMask = mask;
  }

  public int getModifiersMask() {
    return myMask;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.
        append("PsiModifierListStub[").
        append("mask=").append(getModifiersMask()).
        append("]");
    return builder.toString();

  }
}