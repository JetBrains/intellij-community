// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BigPopupUI<T extends AbstractListModel<Object>> extends BorderLayoutPanel implements Disposable {
  protected final Project myProject;
  protected JTextField mySearchField;
  protected JPanel suggestionsPanel;

  protected final JBList<Object> myResultsList = new JBList<>();

  protected JBPopup myHint;

  protected Runnable searchFinishedHandler = () -> {
  };

  protected final List<SearchEverywhereUI.ViewTypeListener> myViewTypeListeners = new ArrayList<>();
  //todo
  protected SearchEverywhereUI.ViewType myViewType = SearchEverywhereUI.ViewType.SHORT;

  protected T myListModel; //todo using in different threads? #UX-1

  public BigPopupUI(Project project) {
    myProject = project;
  }

  protected abstract void initSearchActions();

  @NotNull
  protected abstract T createListModel();

  @NotNull
  protected abstract ListCellRenderer createCellRenderer();

  @NotNull
  protected abstract JPanel createTopLeftPanel();

  @NotNull
  protected abstract JPanel createSettingsPanel();

  @NotNull
  protected abstract JTextField createSearchField();

  public void init() {
    withBackground(JBUI.CurrentTheme.SearchEverywhere.dialogBackground());
    JPanel contributorsPanel = createTopLeftPanel();
    JPanel settingsPanel = createSettingsPanel();
    mySearchField = createSearchField();
    suggestionsPanel = createSuggestionsPanel();

    myListModel = createListModel();
    myListModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        updateViewType(SearchEverywhereUI.ViewType.FULL);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        if (myResultsList.isEmpty() && getSearchPattern().isEmpty()) {
          updateViewType(SearchEverywhereUI.ViewType.SHORT);
        }
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        SearchEverywhereUI.ViewType viewType =
          myResultsList.isEmpty() && getSearchPattern().isEmpty() ? SearchEverywhereUI.ViewType.SHORT : SearchEverywhereUI.ViewType.FULL;
        updateViewType(viewType);
      }
    });
    myResultsList.setModel(myListModel);
    myResultsList.setFocusable(false);
    myResultsList.setCellRenderer(createCellRenderer());

    if (Registry.is("new.search.everywhere.use.editor.font")) {
      Font editorFont = EditorUtil.getEditorFont();
      myResultsList.setFont(editorFont);
    }

    installScrollingActions();

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.setOpaque(false);
    topPanel.add(contributorsPanel, BorderLayout.WEST);
    topPanel.add(settingsPanel, BorderLayout.EAST);
    topPanel.add(mySearchField, BorderLayout.SOUTH);

    WindowMoveListener moveListener = new WindowMoveListener(this);
    topPanel.addMouseListener(moveListener);
    topPanel.addMouseMotionListener(moveListener);

    addToTop(topPanel);
    addToCenter(suggestionsPanel);

    initSearchActions();
  }

  @NotNull
  protected String getSearchPattern() {
    return Optional.ofNullable(mySearchField)
      .map(JTextComponent::getText)
      .orElse("");
  }

  protected void updateViewType(@NotNull SearchEverywhereUI.ViewType viewType) {
    if (myViewType != viewType) {
      myViewType = viewType;
      myViewTypeListeners.forEach(listener -> listener.suggestionsShown(viewType));
    }
  }

  private JPanel createSuggestionsPanel() {
    JPanel pnl = new JPanel(new BorderLayout());
    pnl.setOpaque(false);
    //todo
    pnl.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.SearchEverywhere.searchFieldBorderColor(), 1, 0, 0, 0));

    JScrollPane resultsScroll = new JBScrollPane(myResultsList);
    resultsScroll.setBorder(null);
    resultsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    //todo
    resultsScroll.setPreferredSize(JBUI.size(670, JBUI.CurrentTheme.SearchEverywhere.maxListHeight()));
    pnl.add(resultsScroll, BorderLayout.CENTER);

    //todo
    String hint = IdeBundle.message("searcheverywhere.history.shortcuts.hint",
                                    KeymapUtil.getKeystrokeText(SearchTextField.ALT_SHOW_HISTORY_KEYSTROKE),
                                    KeymapUtil.getKeystrokeText(SearchTextField.SHOW_HISTORY_KEYSTROKE));
    JLabel hintLabel = HintUtil.createAdComponent(hint, JBUI.Borders.emptyLeft(8), SwingConstants.LEFT);
    hintLabel.setOpaque(false);
    hintLabel.setForeground(JBColor.GRAY);
    Dimension size = hintLabel.getPreferredSize();
    size.height = JBUI.scale(17);
    hintLabel.setPreferredSize(size);
    pnl.add(hintLabel, BorderLayout.SOUTH);

    return pnl;
  }

  public void installScrollingActions() {
    ScrollingUtil.installActions(myResultsList, getSearchField());
  }

  @NotNull
  public JTextField getSearchField() {
    return mySearchField;
  }

  @Override
  public Dimension getMinimumSize() {
    return calcPrefSize(SearchEverywhereUI.ViewType.SHORT);
  }

  @Override
  public Dimension getPreferredSize() {
    return calcPrefSize(myViewType);
  }

  private Dimension calcPrefSize(SearchEverywhereUI.ViewType viewType) {
    Dimension size = super.getPreferredSize();
    if (viewType == SearchEverywhereUI.ViewType.SHORT) {
      size.height -= suggestionsPanel.getPreferredSize().height;
    }
    return size;
  }

  public void setSearchFinishedHandler(@NotNull Runnable searchFinishedHandler) {
    this.searchFinishedHandler = searchFinishedHandler;
  }
}