// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Alexander Lobas
 */
public abstract class SearchResultPanel {
  public final SearchPopupController controller;
  public final int tabIndex;
  public final int backTabIndex;

  protected final PluginsGroupComponent myPanel;
  private JScrollBar myVerticalScrollBar;
  private PluginsGroup myGroup = new PluginsGroup("Search Results");
  private String myQuery;
  private AtomicBoolean myRunQuery;
  private boolean myEmpty = true;

  protected Runnable myPostFillGroupCallback;

  public SearchResultPanel(@Nullable SearchPopupController controller,
                           @NotNull PluginsGroupComponent panel,
                           int tabIndex,
                           int backTabIndex) {
    this.controller = controller;
    myPanel = panel;
    this.tabIndex = tabIndex;
    this.backTabIndex = backTabIndex;

    setEmptyText();

    if (isProgressMode()) {
      loading(false);
    }
  }

  @NotNull
  public PluginsGroupComponent getPanel() {
    return myPanel;
  }

  @NotNull
  public PluginsGroup getGroup() {
    return myGroup;
  }

  @NotNull
  public JComponent createScrollPane() {
    JBScrollPane pane = new JBScrollPane(myPanel);
    pane.setBorder(JBUI.Borders.empty());
    if (isProgressMode()) {
      myVerticalScrollBar = pane.getVerticalScrollBar();
    }
    return pane;
  }

  @NotNull
  public JComponent createVScrollPane() {
    JBScrollPane pane = (JBScrollPane)createScrollPane();
    pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return pane;
  }

  protected void setEmptyText() {
    myPanel.getEmptyText().setText("Nothing found");
  }

  public boolean isEmpty() {
    return myEmpty;
  }

  public void setEmpty() {
    myEmpty = true;
  }

  @NotNull
  public String getQuery() {
    return StringUtil.defaultIfEmpty(myQuery, "");
  }

  public void setQuery(@NotNull String query) {
    assert SwingUtilities.isEventDispatchThread();

    setEmptyText();

    if (query.equals(myQuery)) {
      myEmpty = query.isEmpty();
      return;
    }

    if (myRunQuery != null) {
      myRunQuery.set(false);
      myRunQuery = null;
      loading(false);
    }

    removeGroup();
    myQuery = query;

    if (!(myEmpty = query.isEmpty())) {
      handleQuery(query);
    }
  }

  private void handleQuery(@NotNull String query) {
    if (isProgressMode()) {
      loading(true);

      AtomicBoolean runQuery = myRunQuery = new AtomicBoolean(true);
      PluginsGroup group = myGroup;

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        handleQuery(query, group);

        ApplicationManager.getApplication().invokeLater(() -> {
          assert SwingUtilities.isEventDispatchThread();

          if (!runQuery.get()) {
            return;
          }
          myRunQuery = null;

          loading(false);

          if (!myGroup.descriptors.isEmpty()) {
            myGroup.titleWithCount();
            PluginLogo.startBatchMode();
            myPanel.addLazyGroup(myGroup, myVerticalScrollBar, 100, this::fullRepaint);
            PluginLogo.endBatchMode();
          }

          myPanel.initialSelection(false);
          runPostFillGroupCallback();
          fullRepaint();
        }, ModalityState.any());
      });
    }
    else {
      handleQuery(query, myGroup);

      if (!myGroup.descriptors.isEmpty()) {
        myPanel.addGroup(myGroup);
        myGroup.titleWithCount();
        myPanel.initialSelection(false);
      }

      runPostFillGroupCallback();
      fullRepaint();
    }
  }

  protected abstract void handleQuery(@NotNull String query, @NotNull PluginsGroup result);

  private void runPostFillGroupCallback() {
    if (myPostFillGroupCallback != null) {
      myPostFillGroupCallback.run();
      myPostFillGroupCallback = null;
    }
  }

  private void loading(boolean start) {
    PluginsGroupComponentWithProgress panel = (PluginsGroupComponentWithProgress)myPanel;
    if (start) {
      panel.startLoading();
    }
    else {
      panel.stopLoading();
    }
  }

  public void dispose() {
    if (isProgressMode()) {
      ((PluginsGroupComponentWithProgress)myPanel).dispose();
    }
  }

  private boolean isProgressMode() {
    return myPanel instanceof PluginsGroupComponentWithProgress;
  }

  private void removeGroup() {
    if (myGroup.ui != null) {
      myPanel.removeGroup(myGroup);
      fullRepaint();
    }
    myGroup = new PluginsGroup("Search Results");
  }

  public void fullRepaint() {
    myPanel.doLayout();
    myPanel.revalidate();
    myPanel.repaint();
  }
}