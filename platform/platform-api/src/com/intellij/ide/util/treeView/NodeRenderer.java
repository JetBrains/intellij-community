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
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.List;

public class NodeRenderer extends ColoredTreeCellRenderer {

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    Color color = null;
    NodeDescriptor descriptor = null;
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        descriptor = (NodeDescriptor)userObject;
        color = descriptor.getColor();
        if (expanded) {
          setIcon(descriptor.getOpenIcon());
        }
        else {
          setIcon(descriptor.getClosedIcon());
        }
      }
    }


    if (descriptor instanceof PresentableNodeDescriptor) {
      final PresentableNodeDescriptor node = (PresentableNodeDescriptor)descriptor;
      final PresentationData presentation = node.getPresentation();

      final List<PresentableNodeDescriptor.ColoredFragment> coloredText = presentation.getColoredText();
      if (coloredText.size() == 0) {
        String text = tree.convertValueToText(value.toString(), selected, expanded, leaf, row, hasFocus);
        SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(node, presentation.getForcedTextForeground() != null ? presentation.getForcedTextForeground() : color);
        doAppend(text, simpleTextAttributes, selected);
      }
      else {
        for (PresentableNodeDescriptor.ColoredFragment each : coloredText) {
          doAppend(each.getText(), each.getAttributes(), true);
        }
      }

      final String location = presentation.getLocationString();
      if (location != null && location.length() > 0) {
        doAppend(" (" + location + ")", SimpleTextAttributes.GRAY_ATTRIBUTES, selected);
      }

      setToolTipText(presentation.getTooltip());
    }
    else if (value != null) {
      String text = value.toString();
      if (descriptor != null) {
        text = descriptor.myName;
      }
      text = tree.convertValueToText(text, selected, expanded, leaf, row, hasFocus);
      if (text == null) {
        text = "";
      }
      doAppend(text, selected);
      setToolTipText(null);
    }
  }

  protected void doAppend(@NotNull @Nls String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText, boolean isSelected) {
    append(fragment, attributes, isMainText);
  }
  
  public void doAppend(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, boolean isSelected) {
    append(fragment, attributes);
  }
  
  public void doAppend(String fragment, boolean isSelected) {
    append(fragment);
  }

  protected static SimpleTextAttributes getSimpleTextAttributes(final PresentableNodeDescriptor node, final Color color) {
    SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(node.getPresentation());

    if (color != null) {
      final TextAttributes textAttributes = simpleTextAttributes.toTextAttributes();
      textAttributes.setForegroundColor(color);
      simpleTextAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    }
    return simpleTextAttributes;
  }

  public static SimpleTextAttributes getSimpleTextAttributes(final ItemPresentation presentation) {
    if (presentation instanceof ColoredItemPresentation) {
      final TextAttributesKey textAttributesKey = ((ColoredItemPresentation) presentation).getTextAttributesKey();
      if (textAttributesKey == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
      final TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(textAttributesKey);
      return textAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.fromTextAttributes(textAttributes);
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

}