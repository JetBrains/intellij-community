/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import org.jetbrains.annotations.Nullable;

public interface PsiFileWithStubSupport extends PsiFile {
  @Nullable
  StubTree getStubTree();

  @Nullable
  ASTNode findTreeForStub(StubTree tree, StubElement stub);
}