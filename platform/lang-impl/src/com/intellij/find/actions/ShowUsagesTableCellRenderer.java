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

package com.intellij.find.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsagePresentation;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.NullUsage;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
* @author cdr
*/
class ShowUsagesTableCellRenderer implements TableCellRenderer {
  private final UsageViewImpl myUsageView;

  ShowUsagesTableCellRenderer(UsageViewImpl usageView) {
    myUsageView = usageView;
  }

  public Component getTableCellRendererComponent(JTable list, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    UsageNode usageNode = value instanceof UsageNode ? (UsageNode)value : null;

    Usage usage = usageNode == null ? null : usageNode.getUsage();

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0,0));
    Color fileBgColor = getBackgroundColor(isSelected, usage);
    panel.setBackground(isSelected ? UIUtil.getListSelectionBackground() : fileBgColor == null ? list.getBackground() : fileBgColor);
    panel.setForeground(isSelected ? UIUtil.getListSelectionForeground() : list.getForeground());

    if (usage == null) {
      panel.setLayout(new BorderLayout());
      if (column == 0) {
        panel.add(new JLabel("<html><body><b>" + value + "</b></body></html>", SwingConstants.CENTER));
      }
      return panel;
    }


    SimpleColoredComponent textChunks = new SimpleColoredComponent();
    textChunks.setIpad(new Insets(0,0,0,0));
    textChunks.setBorder(null);

    if (column == 0) {
      GroupNode parent = (GroupNode)usageNode.getParent();
      appendGroupText(parent, panel, fileBgColor);
      if (usage == NullUsage.INSTANCE) {
          textChunks.append("...<");
          textChunks.append("more usages", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          textChunks.append(">...");
      }
    }
    else if (usage != NullUsage.INSTANCE) {
      UsagePresentation presentation = usage.getPresentation();
      TextChunk[] text = presentation.getText();

      if (column == 1) {
        textChunks.setIcon(presentation.getIcon());
        if (text.length != 0) {
          SimpleTextAttributes attributes =
            deriveAttributesWithColor(SimpleTextAttributes.fromTextAttributes(text[0].getAttributes()), fileBgColor);
          textChunks.append(text[0].getText(), attributes);
        }
      }
      else if (column == 2) {
        for (int i = 1; i < text.length; i++) {
          TextChunk textChunk = text[i];
          final SimpleTextAttributes attrs = SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes());
          final Color bg = UIUtil.getListSelectionBackground();
          final Color fg = UIUtil.getListSelectionForeground();
          SimpleTextAttributes attributes = isSelected ?
            new SimpleTextAttributes(bg, fg, fg, attrs.getStyle()) : deriveAttributesWithColor(attrs, fileBgColor);
          textChunks.append(textChunk.getText(), attributes);
        }
      }
      else {
        assert false : column;
      }
    }
    panel.add(textChunks);
    return panel;
  }

  private static SimpleTextAttributes deriveAttributesWithColor(SimpleTextAttributes attributes, Color fileBgColor) {
    if (fileBgColor != null) {
      attributes = attributes.derive(-1,null, fileBgColor,null);
    }
    // Fix problems with black background or white text as foreground
    // See UsageViewTreeCellRenderer#patchAttrs for more details
    Color fgColor = attributes.getFgColor();
    if (fgColor != null){
      final float hsb[] = new float[3];
      Color.RGBtoHSB(fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue(), hsb);
      if (hsb[2] > 0.7f){
        fgColor = null;
      }
      return new SimpleTextAttributes(attributes.getStyle(), fgColor, attributes.getWaveColor());
    }
    return attributes;
  }

  private Color getBackgroundColor(boolean isSelected, Usage usage) {
    Color fileBgColor = null;
    if (isSelected) {
      fileBgColor = UIUtil.getListSelectionBackground();
    }
    else {
      VirtualFile virtualFile = usage instanceof UsageInFile ? ((UsageInFile)usage).getFile() : null;
      if (virtualFile != null) {
        Project project = myUsageView.getProject();
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (psiFile != null && psiFile.isValid()) {
          final Color color = FileColorManager.getInstance(project).getRendererBackground(psiFile);
          if (color != null) fileBgColor = color;
        }
      }
    }
    return fileBgColor;
  }

  private void appendGroupText(final GroupNode node, JPanel panel, Color fileBgColor) {
    UsageGroup group = node == null ? null : node.getGroup();
    if (group == null) return;
    GroupNode parentGroup = (GroupNode)node.getParent();
    appendGroupText(parentGroup, panel, fileBgColor);
    if (node.canNavigateToSource()) {
      SimpleColoredComponent renderer = new SimpleColoredComponent();

      renderer.setIcon(group.getIcon(false));
      SimpleTextAttributes attributes = deriveAttributesWithColor(SimpleTextAttributes.REGULAR_ATTRIBUTES, fileBgColor);
      renderer.append(group.getText(myUsageView), attributes);
      renderer.append(" ", attributes);
      renderer.setIpad(new Insets(0,0,0,0));
      renderer.setBorder(null);
      panel.add(renderer);
    }
  }
}
