// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

/**
 * Represents a [HierarchyNodeDescriptor] that stores references and element to which references are resolved.
 */
interface ReferenceAwareNodeDescriptor {
  /**
   * References related to the node.
   */
  val references: List<PsiReference>

  /**
   *  [PsiElement] that should be used to find the children of the node.
   *  @see com.intellij.ide.hierarchy.HierarchyTreeStructure.buildChildren
   */
  val enclosingElement: PsiElement?

  /**
   * Computes the representation of the [enclosingElement] in `Call Hierarchy` view.
   * It doesn't take into account prefix like package name of the {@code enclosingElement} or usages count.
   */
  fun getPresentation(): @NlsSafe String?
}
