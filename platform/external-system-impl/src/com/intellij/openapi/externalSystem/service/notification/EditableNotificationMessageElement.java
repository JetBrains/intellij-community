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

import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.errorTreeView.*;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 3/24/2014
 */
public class EditableNotificationMessageElement extends NotificationMessageElement implements EditableMessageElement {

  @NotNull private final TreeCellEditor myRightTreeCellEditor;
  @NotNull private final Notification myNotification;
  @NotNull private final Map<String/*url*/, String/*link text to replace*/> disabledLinks;

  public EditableNotificationMessageElement(@NotNull Notification notification,
                                            @NotNull ErrorTreeElementKind kind,
                                            @Nullable GroupingElement parent,
                                            String[] message,
                                            @NotNull Navigatable navigatable,
                                            String exportText, String rendererTextPrefix) {
    super(kind, parent, message, navigatable, exportText, rendererTextPrefix);
    myNotification = notification;
    disabledLinks = ContainerUtil.newHashMap();
    myRightTreeCellEditor = new MyCellEditor();
  }


  public void addDisabledLink(@NotNull String url, @Nullable String text) {
    disabledLinks.put(url, text);
  }

  @NotNull
  @Override
  public TreeCellEditor getRightSelfEditor() {
    return myRightTreeCellEditor;
  }

  @Override
  public boolean startEditingOnMouseMove() {
    return true;
  }

  public static void disableLink(@NotNull HyperlinkEvent event) {
    disableLink(event, null);
  }

  private static void disableLink(@NotNull final HyperlinkEvent event, @Nullable final String linkText) {
    if (event.getSource() instanceof MyJEditorPane) {
      UIUtil.invokeLaterIfNeeded(() -> {
        final MyJEditorPane editorPane = (MyJEditorPane)event.getSource();
        editorPane.myElement.addDisabledLink(event.getDescription(), linkText);
        editorPane.myElement.updateStyle(editorPane, null, null, true, false);
      });
    }
  }

  protected void updateStyle(@NotNull JEditorPane editorPane, @Nullable JTree tree, Object value, boolean selected, boolean hasFocus) {
    super.updateStyle(editorPane, tree, value, selected, hasFocus);

    final HTMLDocument htmlDocument = (HTMLDocument)editorPane.getDocument();
    final Style linkStyle = htmlDocument.getStyleSheet().getStyle(LINK_STYLE);
    StyleConstants.setForeground(linkStyle, IdeTooltipManager.getInstance().getLinkForeground(false));
    StyleConstants.setItalic(linkStyle, true);
    HTMLDocument.Iterator iterator = htmlDocument.getIterator(HTML.Tag.A);
    while (iterator.isValid()) {
      boolean disabledLink = false;
      final AttributeSet attributes = iterator.getAttributes();
      if (attributes instanceof SimpleAttributeSet) {
        final Object attribute = attributes.getAttribute(HTML.Attribute.HREF);
        if (attribute instanceof String && disabledLinks.containsKey(attribute)) {
          disabledLink = true;
          //TODO [Vlad] add support for disabled link text update
          ////final String linkText = disabledLinks.get(attribute);
          //if (linkText != null) {
          //}
          ((SimpleAttributeSet)attributes).removeAttribute(HTML.Attribute.HREF);
        }
        if (attribute == null) {
          disabledLink = true;
        }
      }
      if (!disabledLink) {
        htmlDocument.setCharacterAttributes(
          iterator.getStartOffset(), iterator.getEndOffset() - iterator.getStartOffset(), linkStyle, false);
      }
      iterator.next();
    }
  }

  private static class MyJEditorPane extends JEditorPane {
    @NotNull
    private final EditableNotificationMessageElement myElement;

    public MyJEditorPane(@NotNull EditableNotificationMessageElement element) {
      myElement = element;
    }
  }

  private class MyCellEditor extends AbstractCellEditor implements TreeCellEditor {
    private final JEditorPane editorComponent;
    @Nullable
    private JTree myTree;

    private MyCellEditor() {
      editorComponent = installJep(new MyJEditorPane(EditableNotificationMessageElement.this));

      HyperlinkListener hyperlinkListener = new ActivatedHyperlinkListener();
      editorComponent.addHyperlinkListener(hyperlinkListener);
      editorComponent.addMouseListener(new PopupHandler() {
        @Override
        public void invokePopup(Component comp, int x, int y) {
          if (myTree == null) return;

          final TreePath path = myTree.getLeadSelectionPath();
          if (path == null) {
            return;
          }
          DefaultActionGroup group = new DefaultActionGroup();
          group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
          group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));

          ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.COMPILER_MESSAGES_POPUP, group);
          menu.getComponent().show(comp, x, y);
        }
      });
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row) {
      myTree = tree;
      updateStyle(editorComponent, tree, value, selected, false);
      return editorComponent;
    }

    @Override
    public Object getCellEditorValue() {
      return null;
    }

    private class ActivatedHyperlinkListener implements HyperlinkListener {

      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final NotificationListener notificationListener = myNotification.getListener();
          if (notificationListener != null) {
            notificationListener.hyperlinkUpdate(myNotification, e);
          }
        }
      }
    }
  }
}
