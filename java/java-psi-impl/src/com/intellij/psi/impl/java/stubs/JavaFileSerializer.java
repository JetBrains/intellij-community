// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaFileSerializer implements ObjectStubSerializer<PsiJavaFileStub, StubElement> {
  @Override
  public @NotNull String getExternalId() {
    return "java.FILE";
  }

  @Override
  public void serialize(@NotNull PsiJavaFileStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeBoolean(stub.isCompiled());
    LanguageLevel level = stub.getLanguageLevel();
    dataStream.writeByte(level != null ? level.ordinal() : -1);
  }

  @Override
  public @NotNull PsiJavaFileStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    boolean compiled = dataStream.readBoolean();
    int level = dataStream.readByte();
    return new PsiJavaFileStubImpl(null, level >= 0 ? LanguageLevel.getEntries().get(level) : null, compiled);
  }

  @Override
  public void indexStub(@NotNull PsiJavaFileStub stub, @NotNull IndexSink sink) { }

}
