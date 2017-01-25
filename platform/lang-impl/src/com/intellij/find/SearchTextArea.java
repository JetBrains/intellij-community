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
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ide.ui.laf.intellij.MacIntelliJIconCache;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.InplaceActionButtonLook;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static javax.swing.ScrollPaneConstants.*;

public class SearchTextArea extends NonOpaquePanel implements PropertyChangeListener, FocusListener {
  private final JTextArea myTextArea;
  private final boolean mySearchMode;
  private final boolean myInfoMode;
  private final JLabel myInfoLabel;
  private JPanel myIconsPanel = null;
  private ActionButton myNewLineButton;
  private ActionButton myClearButton;
  private JBScrollPane myScrollPane;
  private final ActionButton myHistoryPopupButton;
  private final LafHelper myHelper;

  public SearchTextArea(boolean searchMode) {
    this(new JTextArea(), searchMode, false);
  }

  public SearchTextArea(@NotNull JTextArea textArea, boolean searchMode, boolean infoMode) {
    myTextArea = textArea;
    mySearchMode = searchMode;
    myInfoMode = infoMode;
    myTextArea.addPropertyChangeListener("background", this);
    myTextArea.addPropertyChangeListener("font", this);
    myTextArea.addFocusListener(this);
    myTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateIconsLayout();
      }
    });
    myTextArea.setOpaque(false);
    myScrollPane = new JBScrollPane(myTextArea, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED) {
      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.min(d.height, myTextArea.getUI().getPreferredSize(myTextArea).height);
        return d;
      }
    };
    myTextArea.setBorder(new Border() {
      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {

      }

      @Override
      public Insets getBorderInsets(Component c) {
        int bottom = (StringUtil.getLineBreakCount(myTextArea.getText()) > 0) ? 2 : UIUtil.isUnderDarcula() ? 1 : 0;
        int top = myTextArea.getFontMetrics(myTextArea.getFont()).getHeight() <= 16 ? 2 : 1;
        if (JBUI.isHiDPI(c)) bottom = 2;
        if (JBUI.isHiDPI(c)) top = 2;
        return new JBInsets(top, 0, bottom, 0);
      }

      @Override
      public boolean isBorderOpaque() {
        return false;
      }
    });
    myScrollPane.getVerticalScrollBar().setBackground(UIUtil.TRANSPARENT_COLOR);
    myScrollPane.getViewport().setBorder(null);
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setBorder(JBUI.Borders.emptyRight(2));
    myScrollPane.setOpaque(false);

    myInfoLabel = new JBLabel(UIUtil.ComponentStyle.SMALL);
    myInfoLabel.setForeground(JBColor.GRAY);

    myHelper = createHelper();

    myHistoryPopupButton = createButton(new ShowHistoryAction());
    myClearButton = createButton(new ClearAction());
    myNewLineButton = createButton(new NewLineAction());
    myNewLineButton.setVisible(searchMode);
    myIconsPanel = new NonOpaquePanel();

    updateLayout();
  }

  protected void updateLayout() {
    setBorder(myHelper.getBorder());
    setLayout(new MigLayout(myHelper.getLayoutConstraints()));
    removeAll();
    add(myHistoryPopupButton, myHelper.getHistoryButtonConstraints());
    add(myScrollPane, "ay top, growx, pushx");
    //TODO combine icons/info modes
    if (myInfoMode) {
      add(myInfoLabel);
    }
    else {
      add(myIconsPanel, myHelper.getIconsPanelConstraints());
      updateIconsLayout();
    }
  }

  protected boolean isNewLineAvailable() {
    return Registry.is("ide.find.show.add.newline.hint");
  }

  private void updateIconsLayout() {
    if (myIconsPanel.getParent() == null) {
      return;
    }

    boolean showClearIcon = !StringUtil.isEmpty(myTextArea.getText());
    boolean showNewLine = isNewLineAvailable();
    boolean wrongVisibility =
      ((myClearButton.getParent() != null) != showClearIcon) || ((myNewLineButton.getParent() != null) != showNewLine);

    LayoutManager layout = myIconsPanel.getLayout();
    boolean wrongLayout = !(layout instanceof GridLayout);
    boolean multiline = StringUtil.getLineBreakCount(myTextArea.getText()) > 0;
    boolean wrongPositioning = !wrongLayout && (((GridLayout)layout).getRows() > 1) != multiline;
    if (wrongLayout || wrongVisibility || wrongPositioning) {
      myIconsPanel.removeAll();
      int rows = multiline && showClearIcon && showNewLine ? 2 : 1;
      int columns = !multiline && showClearIcon && showNewLine ? 2 : 1;
      myIconsPanel.setLayout(new GridLayout(rows, columns, 8, 8));
      if (!multiline && showNewLine) {
        myIconsPanel.add(myNewLineButton);
      }
      if (showClearIcon) {
        myIconsPanel.add(myClearButton);
      }
      if (multiline && showNewLine) {
        myIconsPanel.add(myNewLineButton);
      }
      myIconsPanel.setBorder(myHelper.getIconsPanelBorder(rows));
      myScrollPane.setHorizontalScrollBarPolicy(multiline ? HORIZONTAL_SCROLLBAR_AS_NEEDED : HORIZONTAL_SCROLLBAR_NEVER);
      myScrollPane.setVerticalScrollBarPolicy(multiline ? VERTICAL_SCROLLBAR_AS_NEEDED : VERTICAL_SCROLLBAR_NEVER);
      myScrollPane.revalidate();
      doLayout();
    }
  }


  @NotNull
  public JTextArea getTextArea() {
    return myTextArea;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if ("background".equals(evt.getPropertyName())) {
      repaint();
    }
    if ("font".equals(evt.getPropertyName())) {
      updateLayout();
    }
  }

  @Override
  public void focusGained(FocusEvent e) {
    myNewLineButton.setVisible(true);
    repaint();
  }

  @Override
  public void focusLost(FocusEvent e) {
    myNewLineButton.setVisible(mySearchMode);
    repaint();
  }

  public void setInfoText(String info) {
    myInfoLabel.setText(info);
  }

  private static Color enabledBorderColor = new JBColor(Gray._196, Gray._100);
  private static Color disabledBorderColor = Gray._83;

  @Override
  public void paint(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      myHelper.paint(g);
    }
    finally {
      g.dispose();
    }
    super.paint(graphics);

    if (UIUtil.isUnderGTKLookAndFeel()) {
      graphics.setColor(myTextArea.getBackground());
      Rectangle bounds = myScrollPane.getViewport().getBounds();
      if (myScrollPane.getVerticalScrollBar().isVisible()) {
        bounds.width -= myScrollPane.getVerticalScrollBar().getWidth();
      }
      bounds = SwingUtilities.convertRectangle(myScrollPane.getViewport()/*myTextArea*/, bounds, this);
      JBInsets.addTo(bounds, new JBInsets(2, 2, -1, -1));
      ((Graphics2D)graphics).draw(bounds);
    }
  }

  private class ShowHistoryAction extends DumbAwareAction {

    public ShowHistoryAction() {
      super((mySearchMode ? "Search" : "Replace") + " History",
            (mySearchMode ? "Search" : "Replace") + " history",
            myHelper.getShowHistoryIcon());

      KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK);
      registerCustomShortcutSet(new CustomShortcutSet(new KeyboardShortcut(stroke, null)), myTextArea);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("find.recent.search");
      FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(e.getProject());
      String[] recent = mySearchMode ? findInProjectSettings.getRecentFindStrings()
                                     : findInProjectSettings.getRecentReplaceStrings();
      String title = "Recent " + (mySearchMode ? "Searches" : "Replaces");
      JBList historyList = new JBList((Object[])ArrayUtil.reverseArray(recent));
      Utils.showCompletionPopup(SearchTextArea.this, historyList, title, myTextArea, null);
    }
  }

  private static ActionButton createButton(AnAction action) {
    Presentation presentation = action.getTemplatePresentation();
    Dimension d = new JBDimension(16, 16);
    ActionButton button = new ActionButton(action, presentation, ActionPlaces.UNKNOWN, d) {
      @Override
      protected DataContext getDataContext() {
        return DataManager.getInstance().getDataContext(this);
      }
    };
    button.setLook(new InplaceActionButtonLook());
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.updateIcon();
    return button;
  }

  private class ClearAction extends DumbAwareAction {
    public ClearAction() {
      super(null, null, myHelper.getClearIcon());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myTextArea.setText("");
    }
  }

  private class NewLineAction extends DumbAwareAction {
    public NewLineAction() {
      super(null, "New line (" + KeymapUtil.getKeystrokeText(SearchReplaceComponent.NEW_LINE_KEYSTROKE) + ")",
            AllIcons.Actions.SearchNewLine);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      new DefaultEditorKit.InsertBreakAction().actionPerformed(new ActionEvent(myTextArea, 0, "action"));
    }
  }

  @NotNull
  private LafHelper createHelper() {
    return SystemInfo.isMac && !UIUtil.isUnderDarcula() ? new MacLafHelper() : new DefaultLafHelper();
  }

  private static abstract class LafHelper {
    abstract Border getBorder();

    abstract String getLayoutConstraints();

    abstract String getHistoryButtonConstraints();

    abstract String getIconsPanelConstraints();

    abstract Border getIconsPanelBorder(int rows);

    abstract Icon getShowHistoryIcon();

    abstract Icon getClearIcon();

    abstract void paint(Graphics2D g);
  }

  private class MacLafHelper extends LafHelper {
    @Override
    Border getBorder() {
      return new EmptyBorder(3 + Math.max(0, JBUI.scale(16) - UIUtil.getLineHeight(myTextArea)) / 2, 6, 3, 4);
    }

    @Override
    String getLayoutConstraints() {
      return "flowx, ins 0, gapx " + JBUI.scale(4);
    }

    @Override
    String getHistoryButtonConstraints() {
      int extraGap = getExtraGap();
      return "ay top, gaptop " + extraGap + ", gapleft" + (JBUI.isHiDPI(myTextArea) ? 4 : 0);
    }

    private int getExtraGap() {
      int height = UIUtil.getLineHeight(myTextArea);
      Insets insets = myTextArea.getInsets();
      return Math.max(JBUI.isHiDPI(myTextArea) ? 0 : 1, (height + insets.top + insets.bottom - JBUI.scale(16)) / 2);
    }


    @Override
    String getIconsPanelConstraints() {
      int extraGap = getExtraGap();
      return "gaptop " + extraGap + ",ay top, gapright " + extraGap / 2;
    }

    @Override
    Border getIconsPanelBorder(int rows) {
      return JBUI.Borders.emptyBottom(rows == 2 ? 3 : 0);
    }

    @Override
    Icon getShowHistoryIcon() {
      return MacIntelliJIconCache.getIcon("searchFieldWithHistory");
    }

    @Override
    Icon getClearIcon() {
      return AllIcons.Actions.Clear;
    }

    @Override
    void paint(Graphics2D g) {
      Rectangle r = new Rectangle(getSize());
      int h = myIconsPanel.getParent() != null ? Math.max(myIconsPanel.getHeight(), myScrollPane.getHeight()) : myScrollPane.getHeight();
      r.height = Math.max(r.height, h + getInsets().top + getInsets().bottom);
      if (r.height % 2 == 1) r.height--;
      g.setColor(myTextArea.isEnabled() ? enabledBorderColor : disabledBorderColor);
      MacIntelliJTextFieldUI.paintAquaSearchFocusRing(g, r, myTextArea);
    }
  }

  private class DefaultLafHelper extends LafHelper {
    @Override
    Border getBorder() {
      return JBUI.Borders.empty(2);
    }

    @Override
    String getLayoutConstraints() {
      return "flowx, ins 2 " + JBUI.scale(4) + " 2 " + (3 + JBUI.scale(1)) + ", gapx " + JBUI.scale(4);
    }

    @Override
    String getHistoryButtonConstraints() {
      return "ay top, gaptop " + JBUI.scale(getIconTopGap());//Double scaling inside but it looks not bad
    }

    private int getIconTopGap() {
      return Math.max(2, (UIUtil.getLineHeight(myTextArea) + myTextArea.getInsets().top + myTextArea.getInsets().bottom - JBUI.scale(16)) / 2);
    }

    @Override
    String getIconsPanelConstraints() {
      return "gaptop " + getIconTopGap() + ",ay top";
    }

    @Override
    Border getIconsPanelBorder(int rows) {
      return JBUI.Borders.empty();
    }

    @Override
    Icon getShowHistoryIcon() {
      Icon searchIcon = UIManager.getIcon("TextField.darcula.searchWithHistory.icon");
      if (searchIcon == null) {
        searchIcon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/searchWithHistory.png", DarculaTextFieldUI.class, true);
      }
      return searchIcon;
    }

    @Override
    Icon getClearIcon() {
      Icon clearIcon = UIManager.getIcon("TextField.darcula.clear.icon");
      if (clearIcon == null) {
        clearIcon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/clear.png", DarculaTextFieldUI.class, true);
      }
      return clearIcon;
    }

    @Override
    void paint(Graphics2D g) {
      Rectangle r = new Rectangle(getSize());
      JBInsets.removeFrom(r, getInsets());
      if (r.height % 2 == 1) r.height++;
      int arcSize = JBUI.scale(26);

      JBInsets.removeFrom(r, new JBInsets(1, 1, 1, 1));
      if (myTextArea.hasFocus()) {
        g.setColor(myTextArea.getBackground());
        RectanglePainter.FILL.paint(g, r.x, r.y, r.width, r.height, arcSize);
        DarculaUIUtil.paintSearchFocusRing(g, r, myTextArea, arcSize);
      }
      else {
        arcSize -= JBUI.scale(5);
        RectanglePainter
          .paint(g, r.x, r.y, r.width, r.height, arcSize, myTextArea.getBackground(), myTextArea.isEnabled() ? Gray._100 : Gray._83);
      }
    }
  }
}
