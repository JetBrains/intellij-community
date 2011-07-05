/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.StringRef;

/**
 * @author max
 * @deprecated use {@link JavaLightStubBuilder} (to remove in IDEA 11)
 */
public class JavaFileStubBuilder extends DefaultStubBuilder {
  private static final StubBuilder LIGHT_BUILDER = new JavaLightStubBuilder();

  @Override
  protected StubElement createStubForFile(final PsiFile file) {
    if (file instanceof PsiJavaFile) {
      final PsiJavaFile javaFile = (PsiJavaFile)file;
      return new PsiJavaFileStubImpl(javaFile, StringRef.fromString(javaFile.getPackageName()), false);
    }

    return super.createStubForFile(file);
  }

  @Override
  protected boolean skipChildProcessingWhenBuildingStubs(final PsiElement element, final PsiElement child) {
    final ASTNode node = element.getNode();
    if (node == null) return false;
    final ASTNode childNode = child.getNode();
    if (childNode == null) return false;
    return skipChildProcessingWhenBuildingStubs(node, childNode.getElementType());
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(final ASTNode parent, final IElementType childType) {
    return LIGHT_BUILDER.skipChildProcessingWhenBuildingStubs(parent, childType);
  }
}