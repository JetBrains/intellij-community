// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public class ChangeNotesPanel {
  private final JPanel myPanel = new OpaquePanel(new BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR);
  private final JLabel myTitle = new JLabel(IdeBundle.message("label.plugin.change.notes"), AllIcons.General.ArrowRight, SwingConstants.LEFT) {
    @Override
    public void setBounds(int x, int y, int width, int height) {
      super.setBounds(x, y, Math.min(width, getPreferredSize().width), height);
    }
  };
  private final JEditorPane myEditorPane = PluginDetailsPageComponent.createDescriptionComponent(null);
  private final JEditorPane myDescriptionPane;
  private @NlsContexts.DialogMessage String myText;

  public ChangeNotesPanel(@NotNull JPanel parent, @Nullable Object constraints, @NotNull JEditorPane descriptionPane) {
    myDescriptionPane = descriptionPane;
    parent.add(myPanel, constraints);

    myPanel.add(myTitle, BorderLayout.NORTH);
    myPanel.add(myEditorPane);

    myEditorPane.setBorder(JBUI.Borders.emptyLeft(20));

    myTitle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myTitle.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        setDecorateState(!myEditorPane.isVisible());
      }
    });

    setDecorateState(false);
  }

  public void show(@Nullable @NlsContexts.DialogMessage String text) {
    if (text == null) {
      myPanel.setVisible(false);
    }
    else {
      if (!text.equals(myText)) {
        myText = text;
        myEditorPane.setText(XmlStringUtil.wrapInHtml(text));
        if (myEditorPane.getCaret() != null) {
          myEditorPane.setCaretPosition(0);
        }

        ApplicationManager.getApplication().invokeLater(() -> {
          myTitle.setBorder(JBUI.Borders.empty(getBorder(myDescriptionPane, true), 0, getBorder(myEditorPane, false), 0));
          fullRepaint();
        });

        setDecorateState(false);
      }
      myPanel.setVisible(true);
    }
  }

  private void setDecorateState(boolean show) {
    myTitle.setIcon(show ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
    myEditorPane.setVisible(show);
    fullRepaint();
  }

  private void fullRepaint() {
    myPanel.doLayout();
    myPanel.revalidate();
    myPanel.repaint();
  }

  private static int getBorder(@NotNull JEditorPane editorPane, boolean top) {
    try {
      Rectangle bounds = editorPane.getBounds();
      if (bounds.width <= 0 || bounds.height <= 0) {
        editorPane.setSize(editorPane.getPreferredSize());
      }

      Rectangle r = editorPane.modelToView(top ? editorPane.getDocument().getLength() - 1 : 3);
      if (r != null) {
        if (top) {
          if (r.y + r.height == editorPane.getHeight()) {
            return 10;
          }
        }
        else if (r.y == 0) {
          return 10;
        }
      }
    }
    catch (BadLocationException ignore) {
    }
    return 0;
  }
}