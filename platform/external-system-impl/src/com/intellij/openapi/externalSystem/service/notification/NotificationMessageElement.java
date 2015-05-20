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
package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;

/**
 * @author Vladislav.Soroka
 * @since 3/24/2014
 */
public class NotificationMessageElement extends NavigatableMessageElement {
  public static final String MSG_STYLE = "messageStyle";
  public static final String LINK_STYLE = "linkStyle";

  @NotNull private final CustomizeColoredTreeCellRenderer myLeftTreeCellRenderer;
  @NotNull private final CustomizeColoredTreeCellRenderer myRightTreeCellRenderer;

  public NotificationMessageElement(@NotNull final ErrorTreeElementKind kind,
                                    @Nullable GroupingElement parent,
                                    String[] message,
                                    @NotNull Navigatable navigatable,
                                    String exportText,
                                    String rendererTextPrefix) {
    super(kind, parent, message, navigatable, exportText, rendererTextPrefix);
    myLeftTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
      public void customizeCellRenderer(SimpleColoredComponent renderer,
                                        JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        renderer.setIcon(getIcon(kind));
        renderer.setFont(tree.getFont());
        renderer.append(NewErrorTreeRenderer.calcPrefix(NotificationMessageElement.this));
      }

      @NotNull
      private Icon getIcon(@NotNull ErrorTreeElementKind kind) {
        Icon icon = AllIcons.General.Mdot_empty;
        switch (kind) {
          case INFO:
            icon = AllIcons.General.Information;
            break;
          case ERROR:
            icon = AllIcons.General.Error;
            break;
          case WARNING:
            icon = AllIcons.General.Warning;
            break;
          case NOTE:
            icon = AllIcons.General.Tip;
            break;
          case GENERIC:
            icon = AllIcons.General.Mdot_empty;
            break;
        }
        return icon;
      }
    };

    myRightTreeCellRenderer = new MyCustomizeColoredTreeCellRendererReplacement();
  }

  @Nullable
  @Override
  public CustomizeColoredTreeCellRenderer getRightSelfRenderer() {
    return myRightTreeCellRenderer;
  }

  @Nullable
  @Override
  public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
    return myLeftTreeCellRenderer;
  }

  protected JEditorPane installJep(@NotNull JEditorPane myEditorPane) {
    String message = StringUtil.join(this.getText(), "<br>");
    myEditorPane.setEditable(false);
    myEditorPane.setOpaque(false);
    myEditorPane.setEditorKit(UIUtil.getHTMLEditorKit());
    myEditorPane.setHighlighter(null);

    final StyleSheet styleSheet = ((HTMLDocument)myEditorPane.getDocument()).getStyleSheet();
    final Style style = styleSheet.addStyle(MSG_STYLE, null);
    styleSheet.addStyle(LINK_STYLE, style);
    myEditorPane.setText(message);

    return myEditorPane;
  }

  protected void updateStyle(@NotNull JEditorPane editorPane, @Nullable JTree tree, Object value, boolean selected, boolean hasFocus) {
    final HTMLDocument htmlDocument = (HTMLDocument)editorPane.getDocument();
    final Style style = htmlDocument.getStyleSheet().getStyle(MSG_STYLE);
    if (value instanceof LoadingNode) {
      StyleConstants.setForeground(style, JBColor.GRAY);
    }
    else {
      if (selected) {
        StyleConstants.setForeground(style, hasFocus ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeTextForeground());
      }
      else {
        StyleConstants.setForeground(style, UIUtil.getTreeTextForeground());
      }
    }

    if (UIUtil.isUnderGTKLookAndFeel() ||
        UIUtil.isUnderNimbusLookAndFeel() && selected && hasFocus ||
        tree != null && tree.getUI() instanceof WideSelectionTreeUI && ((WideSelectionTreeUI)tree.getUI()).isWideSelection()) {
      editorPane.setOpaque(false);
    }
    else {
      editorPane.setOpaque(selected && hasFocus);
    }

    htmlDocument.setCharacterAttributes(0, htmlDocument.getLength(), style, false);
  }

  private class MyCustomizeColoredTreeCellRendererReplacement extends CustomizeColoredTreeCellRendererReplacement {
    @NotNull
    private final JEditorPane myEditorPane;

    private MyCustomizeColoredTreeCellRendererReplacement() {
      myEditorPane = installJep(new JEditorPane());
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      updateStyle(myEditorPane, tree, value, selected, hasFocus);
      return myEditorPane;
    }
  }
}
