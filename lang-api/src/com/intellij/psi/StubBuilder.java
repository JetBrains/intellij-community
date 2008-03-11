/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.psi.stubs.StubElement;

public interface StubBuilder {
  StubElement buildStubTree(PsiFile file);
}