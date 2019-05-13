/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

  @NotNull
  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Nullable
  @Override
  public PsiJavaModule getModuleDeclaration() {
    PsiJavaFileStub stub = (PsiJavaFileStub)getGreenStub();
    return stub != null ? stub.getModule() : PsiTreeUtil.getChildOfType(this, PsiJavaModule.class);
  }

  @Override
  public String toString() {
    return "PsiJavaFile:" + getName();
  }
}