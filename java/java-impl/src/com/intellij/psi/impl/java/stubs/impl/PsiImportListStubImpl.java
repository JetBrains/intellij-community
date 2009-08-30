/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiImportList;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportListStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiImportListStubImpl extends StubBase<PsiImportList> implements PsiImportListStub {
  public PsiImportListStubImpl(final StubElement parent) {
    super(parent, JavaStubElementTypes.IMPORT_LIST);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiImportListStub");
    return builder.toString();
  }
}