// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.MultiPanel;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.TextComponentEmptyText;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.function.Predicate;

@ApiStatus.Internal
public abstract class PluginsTab {
  private final SingleEdtTaskScheduler searchUpdateAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();

  private PluginDetailsPageComponent detailsPage;
  private MultiPanel cardPanel;
  protected PluginSearchTextField searchTextField;
  private SearchResultPanel searchPanel;

  public final LinkListener<Object> searchListener = (__, data) -> {
    String query;
    if (data instanceof String) {
      query = (String)data;
    }
    else if (data instanceof TagComponent) {
      query = SearchQueryParser.getTagQuery(((TagComponent)data).getText());
    }
    else {
      return;
    }

    searchTextField.setTextIgnoreEvents(query);
    IdeFocusManager.getGlobalInstance()
      .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(searchTextField, true));
    searchPanel.setEmpty();
    showSearchPanel(query);
  };

  private final Consumer<PluginsGroupComponent> mySelectionListener = panel -> {
    int key = searchPanel.getPanel() == panel ? 1 : 0;
    if (cardPanel.getKey() == key) {
      detailsPage.showPlugins(panel.getSelection());
    }
  };

  public @NotNull JComponent createPanel() {
    createSearchTextField(100);

    cardPanel = new MultiPanel() {
      @Override
      public void addNotify() {
        super.addNotify();
        EventHandler.addGlobalAction(searchTextField, new CustomShortcutSet(KeyStroke.getKeyStroke("meta alt F")),
                                     () -> IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
                                       () -> IdeFocusManager.getGlobalInstance().requestFocus(searchTextField, true)));
      }

      @Override
      protected JComponent create(Integer key) {
        if (key == 0) {
          return createPluginsPanel(mySelectionListener);
        }
        if (key == 1) {
          return searchPanel.createVScrollPane();
        }
        return super.create(key);
      }
    };

    JPanel listPanel = new JPanel(new BorderLayout());
    listPanel.add(searchTextField, BorderLayout.NORTH);
    listPanel.add(cardPanel);

    OnePixelSplitter splitter = new OnePixelSplitter(false, 0.45f) {
      @Override
      protected Divider createDivider() {
        Divider divider = super.createDivider();
        divider.setBackground(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR);
        return divider;
      }
    };
    splitter.setFirstComponent(listPanel);
    splitter.setSecondComponent(detailsPage = createDetailsPanel(searchListener));

    searchPanel = createSearchPanel(mySelectionListener);

    cardPanel.select(0, true);

    return splitter;
  }

  protected void createSearchTextField(int flyDelay) {
    searchTextField = new PluginSearchTextField() {
      @Override
      protected boolean preprocessEventForTextField(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int id = event.getID();

        if (keyCode == KeyEvent.VK_ENTER || event.getKeyChar() == '\n') {
          if (id == KeyEvent.KEY_PRESSED &&
              (searchPanel.controller == null || !searchPanel.controller.handleEnter(event))) {
            String text = getText();
            if (!text.isEmpty()) {
              if (searchPanel.controller != null) {
                searchPanel.controller.hidePopup();
              }
              showSearchPanel(text);
            }
          }
          return true;
        }
        if ((keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP) && id == KeyEvent.KEY_PRESSED &&
            searchPanel.controller != null && searchPanel.controller.handleUpDown(event)) {
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
          public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
          }

          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            if (searchPanel.controller != null && searchPanel.controller.isPopupShow()) {
              searchPanel.controller.hidePopup();
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
        if (searchPanel.controller != null && !searchPanel.controller.isPopupShow()) {
          showSearchPopup();
        }
      }
    };

    searchTextField.getTextEditor().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (!searchTextField.isSkipDocumentEvents()) {
          searchUpdateAlarm.cancelAndRequest(flyDelay, ModalityState.stateForComponent(searchTextField), this::searchOnTheFly);
        }
      }

      private void searchOnTheFly() {
        String text = searchTextField.getText();
        if (StringUtil.isEmptyOrSpaces(text)) {
          hideSearchPanel();
        }
        else if (searchPanel.controller == null) {
          showSearchPanel(text);
        }
        else {
          searchPanel.controller.handleShowPopup();
        }
      }
    });

    searchTextField.setBorder(JBUI.Borders.customLine(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR));

    JBTextField editor = searchTextField.getTextEditor();
    editor.putClientProperty("JTextField.Search.Gap", JBUIScale.scale(6));
    editor.putClientProperty("JTextField.Search.GapEmptyText", JBUIScale.scale(-1));
    editor.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, (Predicate<JBTextField>)field -> field.getText().isEmpty());
    editor.setBorder(JBUI.Borders.empty(0, 6));
    editor.setOpaque(true);
    editor.setBackground(PluginManagerConfigurable.SEARCH_BG_COLOR);
    editor.getAccessibleContext().setAccessibleName(IdeBundle.message("plugin.manager.search.accessible.name"));

    String text = IdeBundle.message("plugin.manager.options.command");

    StatusText emptyText = searchTextField.getTextEditor().getEmptyText();
    emptyText.appendText(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ListPluginComponent.GRAY_COLOR));
  }

  protected abstract @NotNull PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener);

  protected abstract @NotNull JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener);

  protected abstract void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener);

  protected abstract @NotNull SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener);

  public @Nullable String getSearchQuery() {
    if (searchPanel == null || searchPanel.isEmpty()) {
      return null;
    }
    String query = searchPanel.getQuery();
    return query.isEmpty() ? null : query;
  }

  public void setSearchQuery(@Nullable String query) {
    searchTextField.setTextIgnoreEvents(query);
    if (query == null) {
      hideSearchPanel();
    }
    else {
      showSearchPanel(query);
    }
  }

  public void showSearchPanel(@NotNull String query) {
    if (searchPanel.isEmpty()) {
      cardPanel.select(1, true);
      detailsPage.showPlugin(null);
    }
    searchPanel.setQuery(query);
    searchTextField.addCurrentTextToHistory();
  }

  public void hideSearchPanel() {
    if (!searchPanel.isEmpty()) {
      onSearchReset();
      cardPanel.select(0, true);
      searchPanel.setQuery("");
      updateMainSelection(mySelectionListener);
    }
    if (searchPanel.controller != null) {
      searchPanel.controller.hidePopup();
    }
  }

  protected abstract void onSearchReset();

  private void showSearchPopup() {
    if (searchPanel.controller != null) {
      if (StringUtil.isEmptyOrSpaces(searchTextField.getText())) {
        searchPanel.controller.showAttributesPopup(null, 0);
      }
      else {
        searchPanel.controller.handleShowPopup();
      }
    }
  }

  public void clearSearchPanel(@NotNull String query) {
    hideSearchPanel();
    searchTextField.setTextIgnoreEvents(query);
  }

  public void dispose() {
    searchUpdateAlarm.dispose();
    if (searchTextField != null) {
      searchTextField.disposeUIResources();
    }
  }
}
