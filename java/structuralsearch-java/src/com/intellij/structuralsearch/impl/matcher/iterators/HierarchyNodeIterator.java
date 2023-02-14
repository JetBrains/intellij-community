// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Passes the hierarchy
 */
public class HierarchyNodeIterator extends NodeIterator {
  private int index;
  private final List<PsiElement> remaining = new SmartList<>();
  private boolean objectTaken;
  private boolean firstElementTaken;
  private final boolean acceptClasses;
  private final boolean acceptInterfaces;
  private final boolean acceptFirstElement;

  private void build(PsiElement current, @NotNull Set<PsiElement> visited) {
    if (current == null) return;

    final String text = current instanceof PsiClass ? ((PsiClass)current).getName() : current.getText();

    if (MatchUtils.compareWithNoDifferenceToPackage(text, "Object")) {
      if(objectTaken) return;
      objectTaken = true;
    }

    PsiElement element = current instanceof PsiReference ? ((PsiReference)current).resolve() : current;
    if (element instanceof PsiClass clazz) {
      if (visited.contains(element)) return;

      if (acceptInterfaces || !clazz.isInterface()) visited.add(element);

      if (firstElementTaken || acceptFirstElement) remaining.add(clazz);
      firstElementTaken = true;

      if (clazz instanceof PsiAnonymousClass) {
        build(((PsiAnonymousClass)clazz).getBaseClassReference(),visited);
        return;
      }

      if (acceptClasses) {
        processClasses(clazz.getExtendsList(), visited);

        if (!objectTaken) {
          build(PsiClassImplUtil.getSuperClass(clazz), visited);
        }
      }

      if (acceptInterfaces) {
        processClasses(clazz.getImplementsList(), visited);

        if (!acceptClasses) processClasses(clazz.getExtendsList(), visited);
      }
    } else {
      remaining.add(current);
    }
  }

  private void processClasses(PsiReferenceList referenceList, @NotNull Set<PsiElement> visited) {
    if (referenceList == null) {
      return;
    }
    for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
      build(referenceElement, visited);
    }
  }

  public HierarchyNodeIterator(PsiElement reference, boolean acceptClasses, boolean acceptInterfaces) {
    this(reference, acceptClasses, acceptInterfaces, true);
  }

  private HierarchyNodeIterator(PsiElement reference, boolean acceptClasses, boolean acceptInterfaces, boolean acceptFirstElement) {
    this.acceptClasses = acceptClasses;
    this.acceptInterfaces = acceptInterfaces;
    this.acceptFirstElement = acceptFirstElement;

    build(reference, new HashSet<>());
  }

  @Override
  public boolean hasNext() {
    return index < remaining.size();
  }

  @Override
  public PsiElement current() {
    return remaining.get(index);
  }

  @Override
  public void advance() {
    if (index != remaining.size()) {
      ++index;
    }
  }

  @Override
  public void rewind() {
    if (index > 0) {
      --index;
    }
  }

  @Override
  public void reset() {
    index = 0;
  }
}
