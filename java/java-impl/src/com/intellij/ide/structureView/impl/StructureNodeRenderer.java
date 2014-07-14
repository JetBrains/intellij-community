/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.FileAppearanceService;
import com.intellij.openapi.roots.ui.ModifiableCellAppearanceEx;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public class StructureNodeRenderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    forNodeDescriptorInTree(value, expanded).customize(this);
  }

  public static CellAppearanceEx forNodeDescriptorInTree(Object node, boolean expanded) {
    NodeDescriptor descriptor = getNodeDescriptor(node);
    if (descriptor == null) return FileAppearanceService.getInstance().empty();
    String name = descriptor.toString();
    Object psiElement = descriptor.getElement();
    ModifiableCellAppearanceEx result;
    if (psiElement instanceof PsiElement && !((PsiElement)psiElement).isValid()) {
      result = CompositeAppearance.single(name);
    }
    else {
      PsiClass psiClass = getContainingClass(psiElement);
      if (isInheritedMember(node, psiClass) && psiClass != null) {
        CompositeAppearance.DequeEnd ending = new CompositeAppearance().getEnding();
        ending.addText(name, applyDeprecation(psiElement, SimpleTextAttributes.DARK_TEXT));
        ending.addComment(psiClass.getName(), applyDeprecation(psiClass, SimpleTextAttributes.GRAY_ATTRIBUTES));
        result = ending.getAppearance();
      }
      else {
        SimpleTextAttributes textAttributes = applyDeprecation(psiElement, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        result = CompositeAppearance.single(name, textAttributes);
      }
    }

    result.setIcon(descriptor.getIcon());
    return result;
  }

  private static boolean isInheritedMember(Object node, PsiClass psiClass) {
    PsiClass treeParentClass = getTreeParentClass(node);
    return treeParentClass != psiClass;
  }

  public static SimpleTextAttributes applyDeprecation(Object value, SimpleTextAttributes nameAttributes) {
    return isDeprecated(value) ? makeStrikeout(nameAttributes) : nameAttributes;
  }

  private static SimpleTextAttributes makeStrikeout(SimpleTextAttributes nameAttributes) {
    return new SimpleTextAttributes(nameAttributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, nameAttributes.getFgColor());
  }

  private static boolean isDeprecated(Object psiElement) {
    return psiElement instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)psiElement).isDeprecated();
  }

  private static PsiClass getContainingClass(Object element) {
    if (element instanceof PsiMember)
      return ((PsiMember) element).getContainingClass();
    return null;
  }

  private static PsiClass getTreeParentClass(Object value) {
    if (!(value instanceof TreeNode))
      return null;
    for (TreeNode treeNode = ((TreeNode) value).getParent(); treeNode != null; treeNode = treeNode.getParent()) {
      Object element = getElement(treeNode);
      if (element instanceof PsiClass)
        return (PsiClass) element;
    }
    return null;
  }

  private static NodeDescriptor getNodeDescriptor(Object value) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        return (NodeDescriptor)userObject;
      }
    }
    return null;
  }

  private static Object getElement(Object node) {
    NodeDescriptor descriptor = getNodeDescriptor(node);
    return descriptor == null ? null : descriptor.getElement();
  }
}
