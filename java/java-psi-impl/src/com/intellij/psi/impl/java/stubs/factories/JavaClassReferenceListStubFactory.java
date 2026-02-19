// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiClassReferenceListStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaClassReferenceListStubFactory implements LightStubElementFactory<PsiClassReferenceListStubImpl, PsiReferenceList> {
  @Override
  public @NotNull PsiClassReferenceListStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    JavaClassReferenceListElementType type = (JavaClassReferenceListElementType)node.getTokenType();
    return new PsiClassReferenceListStubImpl(type, parentStub, getTexts(tree, node));
  }

  @Override
  public PsiReferenceList createPsi(@NotNull PsiClassReferenceListStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createClassReferenceList(stub);
  }
  
  @Override
  public @NotNull PsiClassReferenceListStubImpl createStub(@NotNull PsiReferenceList psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
  
  private static String @NotNull [] getTexts(@NotNull LighterAST tree, @NotNull LighterASTNode node) {
    List<LighterASTNode> refs = LightTreeUtil.getChildrenOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
    String[] texts = ArrayUtil.newStringArray(refs.size());
    for (int i = 0; i < refs.size(); i++) {
      texts[i] = LightTreeUtil.toFilteredString(tree, refs.get(i), null);
    }
    return texts;
  }
}