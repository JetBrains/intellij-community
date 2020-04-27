// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;
import java.util.List;

public class CodeFragment {
  public final List<PsiElement> elements;

  private CodeFragment(List<PsiElement> elements) {
    this.elements = elements;
  }

  public static CodeFragment of(List<PsiElement> elements) {
    checkRange(elements);
    return new CodeFragment(elements);
  }

  public PsiElement getFirstElement() {
    return elements.get(0);
  }

  public PsiElement getLastElement() {
    return elements.get(elements.size() - 1);
  }

  public PsiElement getCommonParent() {
    return getFirstElement().getParent();
  }

  public Project getProject() {
    return getFirstElement().getProject();
  }

  public PsiFile getContainingFile() {
    return getFirstElement().getContainingFile();
  }

  public TextRange getTextRange() {
    return new TextRange(getFirstElement().getTextRange().getStartOffset(), getLastElement().getTextRange().getEndOffset());
  }

  private static void checkRange(List<PsiElement> elements) {
    if (elements.isEmpty()) throw new IllegalArgumentException();
    final PsiElement parent = elements.get(0).getParent();
    final boolean areNotSiblings = elements.stream().anyMatch(element -> element.getParent() != parent);
    if (parent == null || areNotSiblings) throw new IllegalArgumentException();
  }

  public static CodeFragment copyAsCodeBlockOf(CodeFragment codeFragment) {
    final PsiElement parent = codeFragment.getCommonParent();
    if (!(parent instanceof PsiCodeBlock)) throw new IllegalArgumentException("Code fragment is not inside of code block");
    final PsiCodeBlock block = PsiElementFactory.getInstance(codeFragment.getProject()).createCodeBlock();
    block.addRange(codeFragment.getFirstElement(), codeFragment.getLastElement());
    return of(Arrays.asList(block.getStatements()));
  }

  @SuppressWarnings("unchecked")
  public static <T extends PsiElement> T findSameElementInCopy(CodeFragment source, CodeFragment copy, T element) {
    final int sourceStartOffset = source.getTextRange().getStartOffset();
    final int copyStartOffset = copy.getTextRange().getStartOffset();
    final TextRange range = element.getTextRange().shiftRight(copyStartOffset - sourceStartOffset);
    return (T) CodeInsightUtilCore.findElementInRange(copy.getContainingFile(), range.getStartOffset(), range.getEndOffset(), element.getClass(),
                                           JavaLanguage.INSTANCE);
  }

  public static <T extends PsiElement> List<T> findSameElementsInCopy(CodeFragment source, CodeFragment copy, List<T> elements) {
    return ContainerUtil.map(elements, element -> findSameElementInCopy(source, copy, element));
  }

  public static <T extends PsiElement> List<List<T>> findSameGroupsInCopy(CodeFragment source, CodeFragment copy, List<List<T>> groups) {
    return ContainerUtil.map(groups, group -> findSameElementsInCopy(source, copy, group));
  }
}
