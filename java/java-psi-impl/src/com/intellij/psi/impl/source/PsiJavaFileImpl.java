// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl {
  public PsiJavaFileImpl(FileViewProvider file) {
    super(JavaParserDefinition.JAVA_FILE, JavaParserDefinition.JAVA_FILE, file);
  }

  @Override
  public @NotNull FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public @Nullable PsiJavaModule getModuleDeclaration() {
    return withGreenStubOrAst(
      PsiJavaFileStub.class,
      stub -> stub.getModule(),
      ast -> PsiTreeUtil.getChildOfType(this, PsiJavaModule.class)
    );
  }

  @Override
  public String toString() {
    return "PsiJavaFile:" + getName();
  }
}