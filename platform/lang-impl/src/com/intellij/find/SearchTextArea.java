/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.find;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.editorHeaderActions.Utils;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.InplaceActionButtonLook;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class SearchTextArea extends NonOpaquePanel implements PropertyChangeListener, FocusListener {
  private final JTextArea myTextArea;
  private final JLabel myInfoLabel;

  public SearchTextArea(boolean search) {
    this(new JTextArea(), search);
  }

  public SearchTextArea(@NotNull JTextArea textArea,boolean search) {
    myTextArea = textArea;
    myTextArea.addPropertyChangeListener("background", this);
    myTextArea.addFocusListener(this);
    myTextArea.setBorder(null);
    myTextArea.setOpaque(false);
    JBScrollPane scrollPane = new JBScrollPane(myTextArea,
                                               ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                               ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.getVerticalScrollBar().setBackground(UIUtil.TRANSPARENT_COLOR);
    scrollPane.getViewport().setBorder(null);
    scrollPane.getViewport().setOpaque(false);
    scrollPane.setBorder(JBUI.Borders.emptyRight(2));
    scrollPane.setOpaque(false);
    ShowHistoryAction historyAction = new ShowHistoryAction(search);
    ActionButton button =
      new ActionButton(historyAction, historyAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, new Dimension(JBUI.scale(16), JBUI.scale(16)));
    button.setLook(new InplaceActionButtonLook());

    myInfoLabel = new JBLabel(UIUtil.ComponentStyle.SMALL);
    myInfoLabel.setForeground(JBColor.GRAY);

    setBorder(JBUI.Borders.empty(6, 6, 6, 8));
    setLayout(new MigLayout("flowx, ins 0, gapx " + JBUI.scale(4) + "px"));
    add(button, "ay top, gaptop " + JBUI.scale(2) + "px");
    add(scrollPane, "growx, pushx");
    add(myInfoLabel, "ay top, gaptop " + JBUI.scale(2) + "px, gapright " + JBUI.scale(6) + "px");
  }

  @NotNull
  public JTextArea getTextArea() {
    return myTextArea;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();//myToWrap.getPreferredScrollableViewportSize();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    repaint();
  }

  @Override
  public void focusGained(FocusEvent e) {
    repaint();
  }

  @Override
  public void focusLost(FocusEvent e) {
    repaint();
  }

  public void setInfoText(String info) {
    myInfoLabel.setText(info);
  }

  @Override
  public void paint(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      Rectangle r = new Rectangle(getSize());
      r.x += JBUI.scale(4);
      r.y += JBUI.scale(3);
      r.width -= JBUI.scale(9);
      r.height -= JBUI.scale(6);
      int arcSize = Math.min(25, r.height - 1) - 6;
      g.setColor(myTextArea.getBackground());
      boolean hasFocus = myTextArea.hasFocus();
      g.fillRoundRect(r.x, r.y + 1, r.width, r.height - 2, arcSize, arcSize);
      g.setColor(myTextArea.isEnabled() ? Gray._100 : Gray._83);

      if (hasFocus) {
        DarculaUIUtil.paintSearchFocusRing(g, r, arcSize + 6);
      }
      else {
        g.drawRoundRect(r.x, r.y, r.width, r.height - 1, arcSize, arcSize);
      }
    }
    finally {
      g.dispose();
    }
    super.paint(graphics);
  }

  private class ShowHistoryAction extends DumbAwareAction {
    private final boolean myShowSearchHistory;

    public ShowHistoryAction(boolean search) {
      super((search ? "Search" : "Replace") + " History",
            (search ? "Search" : "Replace") + " history",
            IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/search.png", DarculaTextFieldUI.class, true));

      myShowSearchHistory = search;

      KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK);
      registerCustomShortcutSet(new CustomShortcutSet(new KeyboardShortcut(stroke, null)), myTextArea);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("find.recent.search");
      FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(e.getProject());
      String[] recent = myShowSearchHistory ? findInProjectSettings.getRecentFindStrings()
                                            : findInProjectSettings.getRecentReplaceStrings();
      String title = "Recent " + (myShowSearchHistory ? "Searches" : "Replaces");
      JBList historyList = new JBList((Object[])ArrayUtil.reverseArray(recent));
      Utils.showCompletionPopup(myTextArea, historyList, title, myTextArea, null);
    }
  }
}
