/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsagePresentation;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
* @author cdr
*/
class ShowUsagesTableCellRenderer implements TableCellRenderer {
  private final UsageViewImpl myUsageView;
  @NotNull private final AtomicInteger myOutOfScopeUsages;
  @NotNull private final SearchScope mySearchScope;

  ShowUsagesTableCellRenderer(@NotNull UsageViewImpl usageView, @NotNull AtomicInteger outOfScopeUsages, @NotNull SearchScope searchScope) {
    myUsageView = usageView;
    myOutOfScopeUsages = outOfScopeUsages;
    mySearchScope = searchScope;
  }

  @Override
  public Component getTableCellRendererComponent(JTable list, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    UsageNode usageNode = value instanceof UsageNode ? (UsageNode)value : null;
    Usage usage = usageNode == null ? null : usageNode.getUsage();

    Color fileBgColor = getBackgroundColor(isSelected, usage);
    Color bg = UIUtil.getListSelectionBackground();
    Color fg = UIUtil.getListSelectionForeground();
    Color panelBackground = isSelected ? bg : fileBgColor == null ? list.getBackground() : fileBgColor;
    Color panelForeground = isSelected ? fg : list.getForeground();

    SimpleColoredComponent textChunks = new SimpleColoredComponent();
    if (usageNode == null || usageNode instanceof ShowUsagesAction.StringNode) {
      textChunks.append(ObjectUtils.notNull(value, "").toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      return textComponentSpanningWholeRow(textChunks, panelBackground, panelForeground, column, list);
    }
    if (usage == ShowUsagesAction.MORE_USAGES_SEPARATOR) {
      textChunks.append("...<");
      textChunks.append("more usages", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      textChunks.append(">...");
      return textComponentSpanningWholeRow(textChunks, panelBackground, panelForeground, column, list);
    }
    else if (usage == ShowUsagesAction.USAGES_OUTSIDE_SCOPE_SEPARATOR) {
      textChunks.append("...<");
      textChunks.append(UsageViewManagerImpl.outOfScopeMessage(myOutOfScopeUsages.get(), mySearchScope), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      textChunks.append(">...");
      return textComponentSpanningWholeRow(textChunks, panelBackground, panelForeground, column, list);
    }

    boolean lineNumberColumn = column == 1;
    JPanel panel = new JPanel(new FlowLayout(lineNumberColumn ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0) {
      @Override
      public void layoutContainer(Container container) {
        super.layoutContainer(container);
        for (Component component : container.getComponents()) { // align inner components
          Rectangle b = component.getBounds();
          Insets insets = container.getInsets();
          component.setBounds(b.x, b.y, b.width, container.getSize().height - insets.top - insets.bottom);
        }
      }
    });
    panel.setFont(null);
    panel.setBackground(panelBackground);
    panel.setForeground(panelForeground);

    if (column == 0) {
      appendGroupText(list, (GroupNode)usageNode.getParent(), panel, fileBgColor, isSelected);
      return panel;
    }
    else if (usage != ShowUsagesAction.MORE_USAGES_SEPARATOR && usage != ShowUsagesAction.USAGES_OUTSIDE_SCOPE_SEPARATOR) {
      UsagePresentation presentation = usage.getPresentation();
      TextChunk[] text = presentation.getText();

      if (lineNumberColumn) { // line number
        if (text.length != 0) {
          TextChunk chunk = text[0];
          textChunks.append(chunk.getText(), getAttributes(isSelected, fileBgColor, bg, fg, chunk));
        }
      }
      else if (column == 2) {
        Icon icon = presentation.getIcon();
        textChunks.setIcon(icon == null ? EmptyIcon.ICON_16 : icon);
        textChunks.append("").appendTextPadding(16 + 5);
        for (int i = 1; i < text.length; i++) {
          TextChunk chunk = text[i];
          textChunks.append(chunk.getText(), getAttributes(isSelected, fileBgColor, bg, fg, chunk));
        }
      }
      else {
        assert false : column;
      }
    }
    SpeedSearchUtil.applySpeedSearchHighlighting(list, textChunks, false, isSelected);
    panel.add(textChunks);
    return panel;
  }

  @NotNull
  public static SimpleTextAttributes getAttributes(boolean isSelected, Color fileBgColor, Color bg, Color fg, TextChunk chunk) {
    SimpleTextAttributes background = chunk.getSimpleAttributesIgnoreBackground();
    return isSelected
           ? new SimpleTextAttributes(bg, fg, null, background.getStyle())
           : deriveAttributesWithColor(background, fileBgColor);
  }

  @NotNull
  private static Component textComponentSpanningWholeRow(@NotNull SimpleColoredComponent chunks,
                                                         Color panelBackground,
                                                         Color panelForeground,
                                                         final int column,
                                                         @NotNull final JTable table) {
    final SimpleColoredComponent component = new SimpleColoredComponent() {
      @Override
      protected void doPaint(Graphics2D g) {
        int offset = 0;
        int i = 0;
        final TableColumnModel columnModel = table.getColumnModel();
        while (i < column) {
          offset += columnModel.getColumn(i).getWidth();
          i++;
        }
        g.translate(-offset, 0);

        //if (column == columnModel.getColumnCount()-1) {
        //}
        setSize(getWidth()+offset, getHeight()); // should increase the column width so that selection background will be visible even after offset translation

        super.doPaint(g);

        g.translate(+offset, 0);
      }

      @NotNull
      @Override
      public Dimension getPreferredSize() {
        //return super.getPreferredSize();
        return column == table.getColumnModel().getColumnCount()-1 ? super.getPreferredSize() : new Dimension(0,0);
        // it should span the whole row, so we can't return any specific value here,
        // because otherwise it would be used in the "max width" calculation in com.intellij.find.actions.ShowUsagesAction.calcMaxWidth
      }
    };

    component.setBackground(panelBackground);
    component.setForeground(panelForeground);

    for (SimpleColoredComponent.ColoredIterator iterator = chunks.iterator(); iterator.hasNext(); ) {
      iterator.next();
      String fragment = iterator.getFragment();
      SimpleTextAttributes attributes = iterator.getTextAttributes();
      attributes = attributes.derive(attributes.getStyle(), panelForeground, panelBackground, attributes.getWaveColor());
      component.append(fragment, attributes);
    }

    return component;
  }

  private static SimpleTextAttributes deriveAttributesWithColor(SimpleTextAttributes attributes, Color fileBgColor) {
    if (fileBgColor != null) {
      attributes = attributes.derive(-1,null, fileBgColor,null);
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

  private void appendGroupText(JTable table, final GroupNode node, JPanel panel, Color fileBgColor, boolean isSelected) {
    UsageGroup group = node == null ? null : node.getGroup();
    if (group == null) return;
    GroupNode parentGroup = (GroupNode)node.getParent();
    appendGroupText(table, parentGroup, panel, fileBgColor, isSelected);
    if (node.canNavigateToSource()) {
      SimpleColoredComponent renderer = new SimpleColoredComponent();
      renderer.setIcon(group.getIcon(false));
      SimpleTextAttributes attributes = deriveAttributesWithColor(SimpleTextAttributes.REGULAR_ATTRIBUTES, fileBgColor);
      renderer.append(group.getText(myUsageView), attributes);
      renderer.setBorder(null);
      SpeedSearchUtil.applySpeedSearchHighlighting(table, renderer, false, isSelected);
      panel.add(renderer);
    }
  }
}
