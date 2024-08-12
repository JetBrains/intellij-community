// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.find.editorHeaderActions.Utils;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.FieldInplaceActionButtonLook;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.editor.EditorCopyPasteHelper;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.LightEditActionFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.awt.event.InputEvent.*;
import static javax.swing.ScrollPaneConstants.*;

public class SearchTextArea extends JBPanel<SearchTextArea> implements PropertyChangeListener {

  private static final JBColor BACKGROUND_COLOR = JBColor.namedColor("Editor.SearchField.background", UIUtil.getTextFieldBackground());
  public static final String JUST_CLEARED_KEY = "JUST_CLEARED";

  /**
   * @deprecated Use {@link #getNewLineKeystroke()} instead
   */
  @Deprecated(forRemoval = true)
  public static final KeyStroke NEW_LINE_KEYSTROKE
    = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, (SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK) | SHIFT_DOWN_MASK);

  private static final ActionButtonLook FIELD_INPLACE_LOOK = new FieldInplaceActionButtonLook();

  private static final Border EMPTY_SCROLL_BORDER = JBUI.Borders.empty(2, 0, 2, 2);

  private final JTextArea myTextArea;
  private final boolean mySearchMode;
  private final JPanel myIconsPanel = new NonOpaquePanel();
  private final ActionButton myNewLineButton;
  private final ActionButton myClearButton;
  private final NonOpaquePanel myExtraActionsPanel = new NonOpaquePanel();
  private final JBScrollPane myScrollPane;
  private final ActionButton myHistoryPopupButton;
  private boolean myMultilineEnabled = true;
  private boolean myShowNewLineButton = true;

  /**
   * @deprecated infoMode is not used. Use the other constructor.
   */
  @Deprecated(forRemoval = true)
  public SearchTextArea(@NotNull JTextArea textArea, boolean searchMode, @SuppressWarnings("unused") boolean infoMode) {
    this (textArea, searchMode);
  }

  public SearchTextArea(@NotNull JTextArea textArea, boolean searchMode) {
    myTextArea = textArea;
    mySearchMode = searchMode;
    updateFont();

    myTextArea.addPropertyChangeListener("background", this);
    myTextArea.addPropertyChangeListener("font", this);
    LightEditActionFactory.create(event -> myTextArea.transferFocus())
      .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)), myTextArea);
    LightEditActionFactory.create(event -> myTextArea.transferFocusBackward())
      .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, SHIFT_DOWN_MASK)), myTextArea);
    KeymapUtil.reassignAction(myTextArea, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), getNewLineKeystroke(), WHEN_FOCUSED);
    myTextArea.setDocument(new PlainDocument() {
      @Override
      public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
        if (getProperty("filterNewlines") == Boolean.TRUE && str.indexOf('\n') >= 0) {
          str = StringUtil.replace(str, "\n", " ");
        }
        if (!StringUtil.isEmpty(str)) super.insertString(offs, str, a);
      }
    });
    if (Registry.is("ide.find.field.trims.pasted.text", false)) {
      myTextArea.getDocument().putProperty(EditorCopyPasteHelper.TRIM_TEXT_ON_PASTE_KEY, Boolean.TRUE);
    }
    myTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (e.getType() == DocumentEvent.EventType.INSERT) {
          myTextArea.putClientProperty(JUST_CLEARED_KEY, null);
        }
        int rows = Math.min(Registry.get("ide.find.max.rows").asInteger(), myTextArea.getLineCount());
        myTextArea.setRows(Math.max(1, Math.min(25, rows)));
        updateIconsLayout();
      }
    });
    myTextArea.setOpaque(false);
    myScrollPane = new JBScrollPane(myTextArea, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED) {
      @Override
      protected void setupCorners() {
        super.setupCorners();
        super.setBorder(EMPTY_SCROLL_BORDER);
      }

      @Override
      public void updateUI() {
        super.updateUI();
        super.setBorder(EMPTY_SCROLL_BORDER);
      }

      // Disable external updates e.g. from UIUtil.removeScrollBorder
      @Override
      public void setBorder(Border border) {}
    };
    myTextArea.setBorder(new Border() {
      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {}

      @Override
      public Insets getBorderInsets(Component c) {
        if (SystemInfo.isMac) {
          return new JBInsets(3, 0, 2, 0);
        }
        else {
          int bottom = (StringUtil.getLineBreakCount(myTextArea.getText()) > 0) ? 2 : StartupUiUtil.isUnderDarcula() ? 1 : 0;
          int top = myTextArea.getFontMetrics(myTextArea.getFont()).getHeight() <= 16 ? 2 : 1;
          if (JBUIScale.isUsrHiDPI()) {
            bottom = 0;
            top = 2;
          }
          return new JBInsets(top, 0, bottom, 0);
        }
      }

      @Override
      public boolean isBorderOpaque() {
        return false;
      }
    });
    myScrollPane.getViewport().setBorder(null);
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.getHorizontalScrollBar().putClientProperty(JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, Boolean.TRUE);
    myScrollPane.setOpaque(false);

    myHistoryPopupButton = new MyActionButton(new ShowHistoryAction(), false, true);
    myClearButton = new MyActionButton(new ClearAction(), false, false);
    myNewLineButton = new MyActionButton(new NewLineAction(), false, true);

    updateLayout();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    updateFont();
    setBackground(BACKGROUND_COLOR);
  }

  private void updateFont() {
    if (myTextArea != null) {
      if (Registry.is("ide.find.use.editor.font", false)) {
        myTextArea.setFont(EditorUtil.getEditorFont());
      }
      else {
        myTextArea.setFont(UIManager.getFont("TextField.font"));
      }
    }
  }

  protected void updateLayout() {
    JPanel historyButtonWrapper = new NonOpaquePanel(new BorderLayout());
    historyButtonWrapper.setBorder(ExperimentalUI.isNewUI() ? JBUI.Borders.empty(2, 3, 0, 8) : JBUI.Borders.empty(2, 3, 0, 0));
    historyButtonWrapper.add(myHistoryPopupButton, BorderLayout.NORTH);
    JPanel iconsPanelWrapper = new NonOpaquePanel(new BorderLayout());
    iconsPanelWrapper.setBorder(JBUI.Borders.emptyTop(2));
    JPanel p = new NonOpaquePanel(new BorderLayout());
    p.add(myIconsPanel, BorderLayout.NORTH);
    myIconsPanel.setBorder(ExperimentalUI.isNewUI() ? JBUI.Borders.emptyRight(8) : JBUI.Borders.emptyRight(5));
    iconsPanelWrapper.add(p, BorderLayout.WEST);
    iconsPanelWrapper.add(myExtraActionsPanel, BorderLayout.CENTER);

    Border border = getBorder() == null ? JBUI.Borders.empty(JBUI.CurrentTheme.Editor.SearchField.borderInsets()) : getBorder();
    removeAll();
    setLayout(new BorderLayout(JBUIScale.scale(3), 0));
    setBorder(border);

    add(historyButtonWrapper, BorderLayout.WEST);
    add(myScrollPane, BorderLayout.CENTER);
    add(iconsPanelWrapper, BorderLayout.EAST);
    updateIconsLayout();
  }

  private void updateIconsLayout() {
    if (myIconsPanel.getParent() == null) {
      return;
    }

    boolean showClearIcon = !StringUtil.isEmpty(myTextArea.getText());
    boolean showNewLine = myMultilineEnabled && myShowNewLineButton;
    boolean wrongVisibility =
      ((myClearButton.getParent() == null) == showClearIcon) || ((myNewLineButton.getParent() == null) == showNewLine);

    boolean multiline = StringUtil.getLineBreakCount(myTextArea.getText()) > 0;
    if (wrongVisibility) {
      myIconsPanel.removeAll();
      myIconsPanel.setLayout(new BorderLayout());
      myIconsPanel.add(myClearButton, BorderLayout.CENTER);
      if (showNewLine) myIconsPanel.add(myNewLineButton, BorderLayout.EAST);
      resetPreferredSize(myIconsPanel);
      if (!showClearIcon) myIconsPanel.remove(myClearButton);
      myIconsPanel.revalidate();
      myIconsPanel.repaint();
    }
    else {
      resetPreferredSize(myIconsPanel);
    }
    myScrollPane.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setVerticalScrollBarPolicy(multiline ? VERTICAL_SCROLLBAR_AS_NEEDED : VERTICAL_SCROLLBAR_NEVER);
    myScrollPane.getHorizontalScrollBar().setVisible(multiline);
    myScrollPane.revalidate();
    doLayout();
  }

  private void resetPreferredSize(JComponent component) {
    component.setPreferredSize(null);
    component.setPreferredSize(myIconsPanel.getPreferredSize());
  }

  public List<Component> setExtraActions(AnAction... actions) {
    myExtraActionsPanel.removeAll();
    myExtraActionsPanel.setBorder(JBUI.Borders.empty());
    ArrayList<Component> addedButtons = new ArrayList<>();
    if (actions != null && actions.length > 0) {
      JPanel buttonsGrid = new NonOpaquePanel(new GridLayout(1, actions.length, JBUI.scale(4), 0));
      for (AnAction action : actions) {
        if (action instanceof TooltipDescriptionProvider) {
          action.getTemplatePresentation().setDescription(FindBundle.message("find.embedded.buttons.description"));
        }
        ActionButton button = new MyActionButton(action, true, true);
        addedButtons.add(button);
        buttonsGrid.add(button);
      }
      buttonsGrid.setBorder(JBUI.Borders.emptyRight(2));
      myExtraActionsPanel.setLayout(new BorderLayout());
      myExtraActionsPanel.add(buttonsGrid, BorderLayout.NORTH);
      if (!ExperimentalUI.isNewUI()) myExtraActionsPanel.setBorder(new PseudoSeparatorBorder());
    }
    return addedButtons;
  }

  public void updateExtraActions() {
    for (ActionButton button : UIUtil.findComponentsOfType(myExtraActionsPanel, ActionButton.class)) {
      button.update();
    }
  }

  private final KeyAdapter myEnterRedispatcher = new KeyAdapter() {
    @Override
    public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER && SearchTextArea.this.getParent() != null) {
        SearchTextArea.this.getParent().dispatchEvent(e);
      }
    }
  };

  public void setMultilineEnabled(boolean enabled) {
    if (myMultilineEnabled == enabled) return;

    myMultilineEnabled = enabled;
    myTextArea.getDocument().putProperty("filterNewlines", myMultilineEnabled ? null : Boolean.TRUE);
    if (!myMultilineEnabled) {
      myTextArea.getInputMap().put(KeyStroke.getKeyStroke("shift UP"), "selection-begin-line");
      myTextArea.getInputMap().put(KeyStroke.getKeyStroke("shift DOWN"), "selection-end-line");
      myTextArea.addKeyListener(myEnterRedispatcher);
    }
    else {
      myTextArea.getInputMap().put(KeyStroke.getKeyStroke("shift UP"), "selection-up");
      myTextArea.getInputMap().put(KeyStroke.getKeyStroke("shift DOWN"), "selection-down");
      myTextArea.removeKeyListener(myEnterRedispatcher);
    }
    updateIconsLayout();
  }

  public void setShowNewLineButton(boolean show) {
    myShowNewLineButton = show;
    updateIconsLayout();
  }

  public @NotNull JTextArea getTextArea() {
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

  /**
   * @deprecated use this wrapper component with JBTextArea and its getEmptyText() instead
   */
  @Deprecated
  public void setInfoText(@SuppressWarnings("unused") String info) {}

  public static KeyStroke getNewLineKeystroke() {
    return KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, (ClientSystemInfo.isMac() ? META_DOWN_MASK : CTRL_DOWN_MASK) | SHIFT_DOWN_MASK);
  }

  private final class ShowHistoryAction extends DumbAwareAction implements LightEditCompatible {
    ShowHistoryAction() {
      super(FindBundle.message(mySearchMode ? "find.search.history" : "find.replace.history"),
            FindBundle.message(mySearchMode ? "find.search.history" : "find.replace.history"),
            AllIcons.Actions.SearchWithHistory);
      registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("ShowSearchHistory"), myTextArea);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;
      FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(project);
      String[] recent = mySearchMode ? findInProjectSettings.getRecentFindStrings()
                                     : findInProjectSettings.getRecentReplaceStrings();

      Utils.showCompletionPopup(SearchTextArea.this,
                                Arrays.stream(ArrayUtil.reverseArray(recent)).toList(),
                                null,
                                myTextArea,
                                null,
                                FindBundle.message(mySearchMode ? "find.search.history" : "find.replace.history"));
    }
  }

  private final class ClearAction extends DumbAwareAction implements LightEditCompatible {
    ClearAction() {
      super(AllIcons.Actions.Close);
      getTemplatePresentation().setHoveredIcon(AllIcons.Actions.CloseHovered);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myTextArea.putClientProperty(JUST_CLEARED_KEY, !myTextArea.getText().isEmpty());
      myTextArea.setText("");
    }
  }

  private final class NewLineAction extends DumbAwareAction implements LightEditCompatible {
    NewLineAction() {
      super(FindBundle.message("find.new.line"), null, AllIcons.Actions.SearchNewLine);
      setShortcutSet(new CustomShortcutSet(getNewLineKeystroke()));
      getTemplatePresentation().setHoveredIcon(AllIcons.Actions.SearchNewLineHover);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      new DefaultEditorKit.InsertBreakAction().actionPerformed(new ActionEvent(myTextArea, 0, "action"));
    }
  }

  private static final class MyActionButton extends ActionButton {
    MyActionButton(@NotNull AnAction action, boolean focusable, boolean fieldInplaceLook) {
      super(action, action.getTemplatePresentation().clone(), "SearchTextArea", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
      setLook(fieldInplaceLook ? FIELD_INPLACE_LOOK : ActionButtonLook.INPLACE_LOOK);
      setFocusable(focusable);
      updateIcon();
    }

    @Override
    protected DataContext getDataContext() {
      return DataManager.getInstance().getDataContext(this);
    }

    @Override
    public int getPopState() {
      return isSelected() ? SELECTED : super.getPopState();
    }

    @Override
    public Icon getIcon() {
      if (isEnabled() && isSelected()) {
        Icon selectedIcon = myPresentation.getSelectedIcon();
        if (selectedIcon != null) return selectedIcon;
      }
      return super.getIcon();
    }
  }

  private static final class PseudoSeparatorBorder implements Border {
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      g.setColor(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
      g.fillRect(x + JBUI.scale(1), y + 1, 1, JBUI.scale(20));
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return new JBInsets(0, 7, 0, 0);
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
  }
}
