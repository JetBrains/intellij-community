// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class FunctionalExpressionElementType<T extends PsiFunctionalExpression> extends JavaStubElementType<FunctionalExpressionStub<T>,T> {
  public FunctionalExpressionElementType(@NotNull String debugName, @NotNull IElementType parentElementType) {
    super(debugName, parentElementType);
  }

  @Override
  public void serialize(@NotNull FunctionalExpressionStub<T> stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPresentableText());
  }

  @Override
  public @NotNull FunctionalExpressionStub<T> deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new FunctionalExpressionStub<>(parentStub, this, dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull FunctionalExpressionStub<T> stub, @NotNull IndexSink sink) {
  }

  @Override
  public @NotNull FunctionalExpressionStub<T> createStub(@NotNull LighterAST tree, @NotNull LighterASTNode funExpr, @NotNull StubElement<?> parentStub) {
    return new FunctionalExpressionStub<>(parentStub, this, getPresentableText(tree, funExpr));
  }

  protected abstract @NotNull String getPresentableText(@NotNull LighterAST tree, @NotNull LighterASTNode funExpr);
}
