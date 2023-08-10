// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper provides a possibility to normalize the declaration for the variables
 * with C style arrays declaration.
 * The normalization is applied only if array dimensions are equals and annotations
 * for these dimensions are the same as well.
 *
 * Note that the normalizer is tested only with chain of local variables and fields.
 */
final class SingleDeclarationNormalizer {
  private final List<PsiVariable> myVariables;

  SingleDeclarationNormalizer(@NotNull List<PsiVariable> variables) {
    myVariables = variables;
  }

  boolean normalize() {
    if (!possibleSingleDeclaration() || !sameAnnotationsForAllDimensions()) return false;

    PsiVariable firstVar = myVariables.get(0);
    JavaSharedImplUtil.normalizeBrackets(firstVar);

    PsiVariable nextVar = firstVar;
    for (int i = 1; i < myVariables.size(); i++) {
      nextVar = PsiTreeUtil.getNextSiblingOfType(nextVar, PsiVariable.class);
      assert nextVar != null;
      PsiElement nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(nextVar.getNameIdentifier());
      while (nextSibling != null) {
        PsiElement currSibling = nextSibling;
        if (currSibling instanceof PsiJavaToken) {
          final IElementType tokenType = ((PsiJavaToken)currSibling).getTokenType();
          if (tokenType == JavaTokenType.EQ || tokenType == JavaTokenType.SEMICOLON) break;
        }
        nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(nextSibling);
        currSibling.delete();
      }
    }
    return true;
  }

  private boolean possibleSingleDeclaration() {
    if (myVariables.size() < 2) return false;
    int firstVarArrayDimensions = myVariables.get(0).getType().getArrayDimensions();

    for (int i = 1; i < myVariables.size(); i++) {
      PsiVariable nextVar = myVariables.get(i);
      if (firstVarArrayDimensions != nextVar.getType().getArrayDimensions()) return false;
    }
    return true;
  }

  private boolean sameAnnotationsForAllDimensions() {
    PsiVariable firstVar = myVariables.get(0);
    assert firstVar.getNameIdentifier() != null;
    Map<Integer, Set<String>> firstFieldAnnotations = getFieldAnnotations(firstVar.getNameIdentifier());

    PsiVariable nextVar = firstVar;
    for (int i = 1; i < myVariables.size(); i++) {
      nextVar = PsiTreeUtil.getNextSiblingOfType(nextVar, PsiVariable.class);
      assert nextVar != null;
      assert nextVar.getNameIdentifier() != null;
      Map<Integer, Set<String>> nextFieldAnnotations = getFieldAnnotations(nextVar.getNameIdentifier());
      if (firstFieldAnnotations.size() != nextFieldAnnotations.size()) return false;
      if (ContainerUtil.intersection(firstFieldAnnotations, nextFieldAnnotations).size() != firstFieldAnnotations.size()) return false;
    }
    return true;
  }

  @NotNull
  private static Map<Integer, Set<String>> getFieldAnnotations(@NotNull PsiElement startElement) {
    Int2ObjectMap<Set<String>> result = new Int2ObjectOpenHashMap<>();
    int dimensionCounter = 0;
    PsiElement nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(startElement);
    while (nextSibling != null) {
      if (nextSibling instanceof PsiAnnotation) {
        Set<String> annotations = result.computeIfAbsent(dimensionCounter, v -> new HashSet<>());
        annotations.add(((PsiAnnotation)nextSibling).getQualifiedName());
      }
      else if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.RBRACKET)) {
        dimensionCounter++;
      }
      nextSibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(nextSibling);
    }
    return result;
  }
}
