// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiClassReferenceListStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaClassReferenceListStubFactory implements LightStubElementFactory<PsiClassReferenceListStubImpl, PsiReferenceList> {
  @Override
  public @NotNull PsiClassReferenceListStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    JavaClassReferenceListElementType type = (JavaClassReferenceListElementType)node.getTokenType();
    return new PsiClassReferenceListStubImpl(type, parentStub, getTypeInfos(tree, node));
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

  private static TypeInfo @NotNull [] getTypeInfos(@NotNull LighterAST tree, @NotNull LighterASTNode node) {
    List<LighterASTNode> refs = LightTreeUtil.getChildrenOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
    TypeInfo[] infos = refs.isEmpty() ? TypeInfo.EMPTY_ARRAY : new TypeInfo[refs.size()];
    for (int i = 0; i < refs.size(); i++) {
      LighterASTNode ref = refs.get(i);
      try {
        infos[i] = TypeInfo.fromString(LightTreeUtil.toFilteredString(tree, ref, null));
      }
      catch (IllegalArgumentException e) {
        // Source-AST reference text is malformed — typically the parser left an element with
        // unbalanced angle brackets while recovering from broken code. Walk the AST and
        // collect IDENTIFIER children (skipping the malformed REFERENCE_PARAMETER_LIST) so
        // hierarchy lookups still resolve the qualified class name; dropping the entry would
        // produce a stub/AST disagreement (IDEA-389439).
        infos[i] = extractRefFromAst(tree, ref);
      }
    }
    return infos;
  }

  private static TypeInfo.@NotNull RefTypeInfo extractRefFromAst(@NotNull LighterAST tree, @NotNull LighterASTNode ref) {
    TypeInfo.RefTypeInfo info = null;
    for (LighterASTNode child : tree.getChildren(ref)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE) {
        info = extractRefFromAst(tree, child);
      }
      else if (type == JavaTokenType.IDENTIFIER) {
        info = new TypeInfo.RefTypeInfo(((LighterASTTokenNode)child).getText().toString(), info);
      }
    }
    return info != null ? info : new TypeInfo.RefTypeInfo("");
  }
}
