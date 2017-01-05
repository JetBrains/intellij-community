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
package com.intellij.psi.impl.source;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl {
  public PsiJavaFileImpl(FileViewProvider file) {
    super(JavaStubElementTypes.JAVA_FILE, JavaStubElementTypes.JAVA_FILE, file);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Nullable
  @Override
  public PsiJavaModule getModuleDeclaration() {
    PsiJavaFileStub stub = (PsiJavaFileStub)getGreenStub();
    if (stub != null) {
      return stub.getModule();
    }

    PsiElement element = getFirstChild();
    if (element instanceof PsiWhiteSpace || element instanceof PsiComment) {
      element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class, PsiComment.class);
    }
    return element instanceof PsiJavaModule ? (PsiJavaModule)element : null;
  }

  @Override
  public String toString() {
    return "PsiJavaFile:" + getName();
  }
}