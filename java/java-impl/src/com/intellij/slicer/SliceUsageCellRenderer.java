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
package com.intellij.slicer;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.TextChunk;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * @author cdr
 */
public class SliceUsageCellRenderer extends ColoredTreeCellRenderer {
  private static final EditorColorsScheme ourColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
  public static final SimpleTextAttributes ourInvalidAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.INVALID_PREFIX));

  public SliceUsageCellRenderer() {
    setOpaque(false);
  }

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    assert value instanceof DefaultMutableTreeNode;
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
    Object userObject = treeNode.getUserObject();
    if (userObject == null) return;
    if (userObject instanceof MyColoredTreeCellRenderer) {
      MyColoredTreeCellRenderer node = (MyColoredTreeCellRenderer)userObject;
      node.customizeCellRenderer(this, tree, value, selected, expanded, leaf, row, hasFocus);
      if (node instanceof SliceNode) {
        setToolTipText(((SliceNode)node).getPresentation().getTooltip());
      }
    }
    else {
      append(userObject.toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  public void customizeCellRendererFor(SliceUsage sliceUsage) {
    boolean isForcedLeaf = sliceUsage instanceof SliceDereferenceUsage;

    TextChunk[] text = sliceUsage.getPresentation().getText();
    for (TextChunk textChunk : text) {
      SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes());
      if (isForcedLeaf) {
        attributes = attributes.derive(attributes.getStyle(), Color.LIGHT_GRAY, attributes.getBgColor(), attributes.getWaveColor());
        //attributes = attributes.derive(SimpleTextAttributes.STYLE_UNDERLINE, attributes.getBgColor(), attributes.getBgColor(), attributes.getWaveColor());
      }
      append(textChunk.getText(), attributes);
    }

    PsiElement element = sliceUsage.getElement();
    PsiMethod method;
    PsiClass aClass;
    while (true) {
      method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      aClass = method == null ? PsiTreeUtil.getParentOfType(element, PsiClass.class) : method.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        element = aClass;
      }
      else {
        break;
      }
    }
    String location = method != null
                      ? PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
                                                                                 PsiFormatUtil.SHOW_PARAMETERS |
                                                                                 PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                                   PsiFormatUtil.SHOW_TYPE, 2)
                      : aClass != null ? PsiFormatUtil.formatClass(aClass, PsiFormatUtil.SHOW_NAME) : null;
    if (location != null) {
      SimpleTextAttributes attributes = SimpleTextAttributes.GRAY_ATTRIBUTES;
      append(" in " + location, attributes);
    }
  }
}

