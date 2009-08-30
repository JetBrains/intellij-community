/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.impl.java.stubs.impl.PsiClassInitializerStubImpl;
import com.intellij.psi.impl.source.PsiClassInitializerImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.IOException;

public class JavaClassInitializerElementType extends JavaStubElementType<PsiClassInitializerStub, PsiClassInitializer> {
  public JavaClassInitializerElementType() {
    super("CLASS_INITIALIZER");
  }

  public PsiClassInitializer createPsi(final PsiClassInitializerStub stub) {
    assert !isCompiled(stub);
    return new PsiClassInitializerImpl(stub);
  }

  public PsiClassInitializer createPsi(final ASTNode node) {
    return new PsiClassInitializerImpl(node);
  }

  public PsiClassInitializerStub createStub(final PsiClassInitializer psi, final StubElement parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  public void serialize(final PsiClassInitializerStub stub, final StubOutputStream dataStream)
      throws IOException {
  }

  public PsiClassInitializerStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  public void indexStub(final PsiClassInitializerStub stub, final IndexSink sink) {
  }
}