/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class FunctionalExpressionElementType<T extends PsiFunctionalExpression> extends JavaStubElementType<FunctionalExpressionStub<T>,T> {
  public FunctionalExpressionElementType(String debugName) {
    super(debugName);
  }

  @Override
  public void serialize(@NotNull FunctionalExpressionStub<T> stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPresentableText());
  }

  @NotNull
  @Override
  public FunctionalExpressionStub<T> deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new FunctionalExpressionStub<>(parentStub, this, dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull FunctionalExpressionStub<T> stub, @NotNull IndexSink sink) {
  }

  @Override
  public FunctionalExpressionStub<T> createStub(LighterAST tree, LighterASTNode funExpr, StubElement parentStub) {
    return new FunctionalExpressionStub<>(parentStub, this, getPresentableText(tree, funExpr));
  }

  @NotNull
  protected abstract String getPresentableText(@NotNull LighterAST tree, @NotNull LighterASTNode funExpr);
}
