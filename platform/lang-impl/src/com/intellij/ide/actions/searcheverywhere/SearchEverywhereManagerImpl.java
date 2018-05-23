// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.ide.actions.SearchEverywhereAction.SEARCH_EVERYWHERE_POPUP;

public class SearchEverywhereManagerImpl implements SearchEverywhereManager {

  private final Project myProject;
  private final List<SearchEverywhereContributor> myShownContributors = new ArrayList<>();
  private final List<SearchEverywhereContributor> myServiceContributors = new ArrayList<>();

  private JBPopup myBalloon;
  private SearchEverywhereUI mySearchEverywhereUI;

  private final SearchHistoryList myHistoryList = new SearchHistoryList();
  private HistoryIterator myHistoryIterator;

  public SearchEverywhereManagerImpl(Project project) {
    myProject = project;
    fillContributors();
  }

  private void fillContributors() {
    // fill shown contributors
    myShownContributors.addAll(SearchEverywhereContributor.getProvidersSorted());

    // fill service contributors
    TopHitSEContributor topHitContributor = new TopHitSEContributor(s -> mySearchEverywhereUI.getSearchField().setText(s));
    myServiceContributors.add(topHitContributor);
  }

  @Override
  public void show(@NotNull String selectedContributorID) {
    if (isShown()) {
      setShownContributor(selectedContributorID);
    }
    else {
      mySearchEverywhereUI = createView(myProject, myServiceContributors, myShownContributors);
      mySearchEverywhereUI.switchToContributor(selectedContributorID);
      myHistoryIterator = myHistoryList.getIterator(selectedContributorID);
      myBalloon = JBPopupFactory.getInstance().createComponentPopupBuilder(mySearchEverywhereUI, mySearchEverywhereUI.getSearchField())
                                .setProject(myProject)
                                .setResizable(false)
                                .setModalContext(false)
                                .setCancelOnClickOutside(true)
                                .setRequestFocus(true)
                                .setCancelKeyEnabled(false)
                                .setCancelCallback(() -> {
                                  saveSearchText();
                                  return true;
                                })
                                .addUserData("SIMPLE_WINDOW")
                                .setResizable(true)
                                .setMovable(true)
                                .createPopup();
      Disposer.register(myBalloon, mySearchEverywhereUI);

      myProject.putUserData(SEARCH_EVERYWHERE_POPUP, myBalloon);
      Disposer.register(myBalloon, () -> myProject.putUserData(SEARCH_EVERYWHERE_POPUP, null));

      RelativePoint showingPoint = calculateShowingPoint();
      if (showingPoint != null) {
        myBalloon.show(showingPoint);
      }
      else {
        myBalloon.showInFocusCenter();
      }
    }
  }

  @Override
  public boolean isShown() {
    return myBalloon != null && !myBalloon.isDisposed();
  }

  @Override
  public String getShownContributorID() {
    checkIsShown();
    return mySearchEverywhereUI.getSelectedContributorID();
  }

  @Override
  public void setShownContributor(@NotNull String contributorID) {
    checkIsShown();
    if (!contributorID.equals(getShownContributorID())) {
      mySearchEverywhereUI.switchToContributor(contributorID);
    }
  }

  @Override
  public boolean isShowNonProjectItems() {
    checkIsShown();
    return mySearchEverywhereUI.isUseNonProjectItems();
  }

  @Override
  public void setShowNonProjectItems(boolean show) {
    checkIsShown();
    mySearchEverywhereUI.setUseNonProjectItems(show);
  }

  private RelativePoint calculateShowingPoint() {
    final Window window = myProject != null
                          ? WindowManager.getInstance().suggestParentWindow(myProject)
                          : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    Component parent = UIUtil.findUltimateParent(window);
    if (parent == null) {
      return null;
    }

    int height = UISettings.getInstance().getShowMainToolbar() ? 135 : 115;
    if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
      height -= 20;
    }
    return new RelativePoint(parent, new Point((parent.getSize().width - mySearchEverywhereUI.getPreferredSize().width) / 2, height));
  }

  private SearchEverywhereUI createView(Project project,
                                        List<SearchEverywhereContributor> serviceContributors,
                                        List<SearchEverywhereContributor> allContributors) {
    SearchEverywhereUI view = new SearchEverywhereUI(project, serviceContributors, allContributors);
    view.addPropertyChangeListener("preferredSize", evt -> {
      if (isShown()) {
        myBalloon.pack(true, true);
      }
    });

    view.setSearchFinishedHandler(() -> {
      if (isShown()) {
        myBalloon.cancel();
      }
    });

    DumbAwareAction.create(__ -> showHistoryItem(true))
                   .registerCustomShortcutSet(SearchTextField.SHOW_HISTORY_SHORTCUT, view);

    DumbAwareAction.create(__ -> showHistoryItem(false))
                   .registerCustomShortcutSet(SearchTextField.ALT_SHOW_HISTORY_SHORTCUT, view);

    return view;
  }

  private void checkIsShown() {
    if (!isShown()) {
      throw new IllegalStateException("Method should be called only when search popup is shown");
    }
  }

  private void saveSearchText() {
    updateHistoryIterator();
    String searchText = mySearchEverywhereUI.getSearchField().getText();
    if (!searchText.isEmpty()) {
      myHistoryList.saveText(searchText, mySearchEverywhereUI.getSelectedContributorID());
    }
  }

  private void showHistoryItem(boolean next) {
    updateHistoryIterator();
    JTextField searchField = mySearchEverywhereUI.getSearchField();
    searchField.setText(next ? myHistoryIterator.next() : myHistoryIterator.prev());
    searchField.selectAll();
  }

  private void updateHistoryIterator() {
    String selectedContributorID = mySearchEverywhereUI.getSelectedContributorID();
    if (myHistoryIterator == null || !myHistoryIterator.getContributorID().equals(selectedContributorID)) {
      myHistoryIterator = myHistoryList.getIterator(selectedContributorID);
    }
  }

  private static class SearchHistoryList {

    private final static int HISTORY_LIMIT = 50;

    private static class HistoryItem {
      private final String searchText;
      private final String contributorID;

      public HistoryItem(String searchText, String contributorID) {
        this.searchText = searchText;
        this.contributorID = contributorID;
      }

      public String getSearchText() {
        return searchText;
      }

      public String getContributorID() {
        return contributorID;
      }
    }

    private final List<HistoryItem> historyList = new ArrayList<>();

    public HistoryIterator getIterator(String contributorID) {
      List<String> list = getHistoryForContributor(contributorID);
      return new HistoryIterator(contributorID, list);
    }

    public void saveText(String text, String contributorID) {
      String lastHistoryItem = lastSearchForContributor(contributorID);
      if (text.equals(lastHistoryItem)) {
        return;
      }

      historyList.add(new HistoryItem(text, contributorID));

      List<String> list = filteredHistory(item -> item.getContributorID().equals(contributorID));
      if (list.size() > HISTORY_LIMIT) {
        historyList.stream()
                   .filter(item -> item.getContributorID().equals(contributorID))
                   .findFirst()
                   .ifPresent(historyList::remove);
      }
    }

    private String lastSearchForContributor(String contributorID) {
      if (historyList.isEmpty()) {
        return null;
      }

      if (SearchEverywhereContributor.ALL_CONTRIBUTORS_GROUP_ID.equals(contributorID)) {
        return historyList.get(historyList.size() - 1).getSearchText();
      } else {
        return Lists.reverse(historyList)
                    .stream()
                    .filter(item -> item.getContributorID().equals(contributorID))
                    .findFirst()
                    .map(item -> item.getSearchText())
                    .orElse(null);
      }
    }

    private List<String> getHistoryForContributor(String contributorID) {
      if (SearchEverywhereContributor.ALL_CONTRIBUTORS_GROUP_ID.equals(contributorID)) {
        List<String> res = filteredHistory(item -> true);
        int size = res.size();
        return size > HISTORY_LIMIT ? res.subList(size - HISTORY_LIMIT, size) : res;
      } else {
        return filteredHistory(item -> item.getContributorID().equals(contributorID));
      }
    }

    @NotNull
    private List<String> filteredHistory(Predicate<HistoryItem> predicate) {
      return historyList.stream()
                        .filter(predicate)
                        .map(item -> item.getSearchText())
                        .collect(Collectors.toList());
    }
  }

  private static class HistoryIterator {

    private final String contributorID;
    private final List<String> list;
    private int index;

    public HistoryIterator(String id, List<String> list) {
      contributorID = id;
      this.list = list;
      index = -1;
    }

    public String getContributorID() {
      return contributorID;
    }

    public String next() {
      if (list.isEmpty()) {
        return "";
      }

      index += 1;
      if (index >= list.size()) {
        index = 0;
      }
      return list.get(index);
    }

    public String prev() {
      if (list.isEmpty()) {
        return "";
      }

      index -= 1;
      if (index < 0) {
        index = list.size() - 1;
      }
      return list.get(index);
    }
  }
}
