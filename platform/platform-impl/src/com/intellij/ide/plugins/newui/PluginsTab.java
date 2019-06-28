// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.MultiPanel;
import com.intellij.ide.plugins.PluginManagerConfigurableNew;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.BooleanFunction;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public abstract class PluginsTab {
  private final Alarm mySearchUpdateAlarm = new Alarm();

  private PluginDetailsPageComponent myDetailsPage;
  private MultiPanel myCardPanel;
  protected PluginSearchTextField mySearchTextField;
  private SearchResultPanel mySearchPanel;

  public final LinkListener<Object> mySearchListener = (__, data) -> {
    String query;
    if (data instanceof String) {
      query = (String)data;
    }
    else if (data instanceof TagComponent) {
      query = "/" + SearchQueryParser.getTagQuery(((TagComponent)data).getText());
    }
    else {
      return;
    }

    mySearchTextField.setTextIgnoreEvents(query);
    IdeFocusManager.getGlobalInstance()
      .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(mySearchTextField, true));
    mySearchPanel.setEmpty();
    showSearchPanel(query);
  };

  private final Consumer<PluginsGroupComponent> mySelectionListener = panel -> {
    List<CellPluginComponent> selection = panel.getSelection();
    int size = selection.size();
    myDetailsPage.showPlugin(size == 1 ? selection.get(0) : null, size > 1);
  };

  @NotNull
  public JComponent createPanel() {
    createSearchTextField(100);

    myCardPanel = new MultiPanel() {
      @Override
      public void addNotify() {
        super.addNotify();
        EventHandler.addGlobalAction(mySearchTextField, new CustomShortcutSet(KeyStroke.getKeyStroke("meta alt F")),
                                     () -> IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
                                       () -> IdeFocusManager.getGlobalInstance().requestFocus(mySearchTextField, true)));
      }

      @Override
      protected JComponent create(Integer key) {
        if (key == 0) {
          return createPluginsPanel(mySelectionListener);
        }
        if (key == 1) {
          return mySearchPanel.createVScrollPane();
        }
        return super.create(key);
      }
    };

    JPanel listPanel = new JPanel(new BorderLayout());
    listPanel.add(mySearchTextField, BorderLayout.NORTH);
    listPanel.add(myCardPanel);

    OnePixelSplitter splitter = new OnePixelSplitter(false, 0.45f) {
      @Override
      protected Divider createDivider() {
        Divider divider = super.createDivider();
        divider.setBackground(PluginManagerConfigurableNew.SEARCH_FIELD_BORDER_COLOR);
        return divider;
      }
    };
    splitter.setFirstComponent(listPanel);
    splitter.setSecondComponent(myDetailsPage = createDetailsPanel(mySearchListener));

    mySearchPanel = createSearchPanel(mySelectionListener);

    myCardPanel.select(0, true);

    return splitter;
  }

  protected void createSearchTextField(int flyDelay) {
    mySearchTextField = new PluginSearchTextField() {
      @Override
      protected boolean preprocessEventForTextField(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int id = event.getID();

        if (keyCode == KeyEvent.VK_ENTER || event.getKeyChar() == '\n') {
          if (id == KeyEvent.KEY_PRESSED &&
              (mySearchPanel.controller == null || !mySearchPanel.controller.handleEnter(event))) {
            String text = getText();
            if (!text.isEmpty()) {
              if (mySearchPanel.controller != null) {
                mySearchPanel.controller.hidePopup();
              }
              showSearchPanel(text);
            }
          }
          return true;
        }
        if ((keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP) && id == KeyEvent.KEY_PRESSED &&
            mySearchPanel.controller != null && mySearchPanel.controller.handleUpDown(event)) {
          return true;
        }
        return super.preprocessEventForTextField(event);
      }

      @Override
      protected boolean toClearTextOnEscape() {
        new AnAction() {
          {
            setEnabledInModalContext(true);
          }

          @Override
          public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!getText().isEmpty());
          }

          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            if (mySearchPanel.controller != null && mySearchPanel.controller.isPopupShow()) {
              mySearchPanel.controller.hidePopup();
            }
            else {
              setText("");
            }
          }
        }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, this);
        return false;
      }

      @Override
      protected void onFieldCleared() {
        hideSearchPanel();
      }

      @Override
      protected void showCompletionPopup() {
        if (mySearchPanel.controller != null && !mySearchPanel.controller.isPopupShow()) {
          showSearchPopup();
        }
      }
    };

    mySearchTextField.getTextEditor().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (!mySearchTextField.isSkipDocumentEvents()) {
          mySearchUpdateAlarm.cancelAllRequests();
          mySearchUpdateAlarm.addRequest(this::searchOnTheFly, flyDelay, ModalityState.stateForComponent(mySearchTextField));
        }
      }

      private void searchOnTheFly() {
        String text = mySearchTextField.getText();
        if (StringUtil.isEmptyOrSpaces(text)) {
          hideSearchPanel();
        }
        else if (mySearchPanel.controller == null) {
          showSearchPanel(text);
        }
        else {
          mySearchPanel.controller.handleShowPopup();
        }
      }
    });

    mySearchTextField.setBorder(JBUI.Borders.customLine(PluginManagerConfigurableNew.SEARCH_FIELD_BORDER_COLOR));

    JBTextField editor = mySearchTextField.getTextEditor();
    editor.putClientProperty("JTextField.Search.Gap", JBUIScale.scale(6));
    editor.putClientProperty("JTextField.Search.GapEmptyText", JBUIScale.scale(-1));
    editor.putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>)field -> field.getText().isEmpty());
    editor.setBorder(JBUI.Borders.empty(0, 6));
    editor.setOpaque(true);
    editor.setBackground(PluginManagerConfigurableNew.SEARCH_BG_COLOR);

    String text = "Type / to see options";

    StatusText emptyText = mySearchTextField.getTextEditor().getEmptyText();
    emptyText.appendText(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, CellPluginComponent.GRAY_COLOR));
  }

  @NotNull
  protected abstract PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener);

  @NotNull
  protected abstract JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener);

  protected abstract void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener);

  @NotNull
  protected abstract SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener);

  @Nullable
  public String getSearchQuery() {
    if (mySearchPanel == null || mySearchPanel.isEmpty()) {
      return null;
    }
    String query = mySearchPanel.getQuery();
    return query.isEmpty() ? null : query;
  }

  public void setSearchQuery(@Nullable String query) {
    mySearchTextField.setTextIgnoreEvents(query);
    mySearchTextField.requestFocus();
    if (query == null) {
      hideSearchPanel();
    }
    else {
      showSearchPanel(query);
    }
  }

  public void showSearchPanel(@NotNull String query) {
    if (mySearchPanel.isEmpty()) {
      myCardPanel.select(1, true);
      myDetailsPage.showPlugin(null, false);
    }
    mySearchPanel.setQuery(query);
    mySearchTextField.addCurrentTextToHistory();
  }

  public void hideSearchPanel() {
    if (!mySearchPanel.isEmpty()) {
      myCardPanel.select(0, true);
      mySearchPanel.setQuery("");
      updateMainSelection(mySelectionListener);
    }
    if (mySearchPanel.controller != null) {
      mySearchPanel.controller.hidePopup();
    }
  }

  private void showSearchPopup() {
    if (mySearchPanel.controller != null) {
      if (StringUtil.isEmptyOrSpaces(mySearchTextField.getText())) {
        mySearchPanel.controller.showAttributesPopup(null, 0);
      }
      else {
        mySearchPanel.controller.handleShowPopup();
      }
    }
  }

  public void clearSearchPanel(@NotNull String query) {
    hideSearchPanel();
    mySearchTextField.setTextIgnoreEvents(query);
  }

  public void dispose() {
    Disposer.dispose(mySearchUpdateAlarm);
  }
}