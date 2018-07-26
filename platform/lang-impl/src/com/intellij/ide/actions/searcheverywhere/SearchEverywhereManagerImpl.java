// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SearchTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.ide.actions.SearchEverywhereAction.SEARCH_EVERYWHERE_POPUP;

public class SearchEverywhereManagerImpl implements SearchEverywhereManager {

  public static final String ALL_CONTRIBUTORS_GROUP_ID = "SearchEverywhereContributor.All";
  private static final String LOCATION_SETTINGS_KEY = "search.everywhere.popup";

  private final Project myProject;
  private final List<SearchEverywhereContributorFactory<?>> myContributorFactories = SearchEverywhereContributor.getProviders();
  private final Map<String, SearchEverywhereContributorFilter<?>> myContributorFilters = new HashMap<>();

  private JBPopup myBalloon;
  private SearchEverywhereUI mySearchEverywhereUI;
  private Dimension myBalloonFullSize;

  private final SearchHistoryList myHistoryList = new SearchHistoryList();
  private HistoryIterator myHistoryIterator;

  public SearchEverywhereManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void show(@NotNull String selectedContributorID, @Nullable String searchText, @NotNull AnActionEvent initEvent) {
    if (isShown()) {
      throw new IllegalStateException("Method should cannot be called whe popup is shown");
    }

    Project project = initEvent.getProject();
    List<SearchEverywhereContributor> serviceContributors = Arrays.asList(
      new TopHitSEContributor(project, initEvent.getData(PlatformDataKeys.CONTEXT_COMPONENT),
                              s -> mySearchEverywhereUI.getSearchField().setText(s)),
      new RecentFilesSEContributor(project)
    );

    List<SearchEverywhereContributor> contributors = new ArrayList<>();
    Map<String, String> contributorsNames = new LinkedHashMap<>();
    myContributorFactories.forEach(factory -> {
      SearchEverywhereContributor contributor = factory.createContributor(initEvent);
      myContributorFilters.computeIfAbsent(contributor.getSearchProviderId(), s -> factory.createFilter());
      contributors.add(contributor);
      contributorsNames.put(contributor.getSearchProviderId(), contributor.getGroupName());
    });
    Collections.sort(contributors, Comparator.comparingInt(SearchEverywhereContributor::getSortWeight));
    myContributorFilters.computeIfAbsent(ALL_CONTRIBUTORS_GROUP_ID,
                                         s -> {
                                           List<String> ids = contributors.stream()
                                                                          .map(contributor -> contributor.getSearchProviderId())
                                                                          .collect(Collectors.toList());
                                           return new SearchEverywhereContributorFilterImpl<>(ids, id -> contributorsNames.get(id), id -> null);
                                         }
    );

    mySearchEverywhereUI = createView(myProject, serviceContributors, contributors, myContributorFilters);
    mySearchEverywhereUI.switchToContributor(selectedContributorID);

    myHistoryIterator = myHistoryList.getIterator(selectedContributorID);
    if (searchText == null && !ALL_CONTRIBUTORS_GROUP_ID.equals(selectedContributorID)) {
      searchText = myHistoryIterator.prev();
    }

    if (searchText != null && !searchText.isEmpty()) {
      mySearchEverywhereUI.getSearchField().setText(searchText);
      mySearchEverywhereUI.getSearchField().selectAll();
    }

    myBalloon = JBPopupFactory.getInstance().createComponentPopupBuilder(mySearchEverywhereUI, mySearchEverywhereUI.getSearchField())
                              .setProject(myProject)
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
                              .setDimensionServiceKey(project, LOCATION_SETTINGS_KEY, true)
                              .setLocateWithinScreenBounds(false)
                              .createPopup();
    Disposer.register(myBalloon, mySearchEverywhereUI);
    if (project != null) {
      Disposer.register(project, myBalloon);
    }

    Dimension size = mySearchEverywhereUI.getMinimumSize();
    myBalloon.setMinimumSize(withInsets(size));

    myProject.putUserData(SEARCH_EVERYWHERE_POPUP, myBalloon);
    Disposer.register(myBalloon, () -> {
      saveSize();
      myProject.putUserData(SEARCH_EVERYWHERE_POPUP, null);
      mySearchEverywhereUI = null;
      myBalloon = null;
      myBalloonFullSize = null;
    });

    calcPositionAndShow(project, myBalloon);
    if (mySearchEverywhereUI.getViewType() == SearchEverywhereUI.ViewType.SHORT) {
      myBalloonFullSize = DimensionService.getInstance().getSize(LOCATION_SETTINGS_KEY, project);
      myBalloon.pack(false, true);
    }
  }

  private void calcPositionAndShow(Project project, JBPopup balloon) {
    Point savedLocation = DimensionService.getInstance().getLocation(LOCATION_SETTINGS_KEY, project);

    if (project != null) {
      balloon.showCenteredInCurrentWindow(project);
    } else {
      balloon.showInFocusCenter();
    }

    //for first show and short mode popup should be shifted to the top screen half
    if (savedLocation == null && mySearchEverywhereUI.getViewType() == SearchEverywhereUI.ViewType.SHORT) {
      Point location = balloon.getLocationOnScreen();
      location.y /= 2;
      balloon.setLocation(location);
    }
  }

  private Dimension withInsets(Dimension size) {
    Insets insets = myBalloon.getContent().getInsets();
    return new Dimension(
      size.width + insets.left + insets.right,
      size.height + insets.top + insets.bottom
    );
  }

  @Override
  public boolean isShown() {
    return mySearchEverywhereUI != null && myBalloon != null && !myBalloon.isDisposed();
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

  private SearchEverywhereUI createView(Project project,
                                        List<SearchEverywhereContributor> serviceContributors,
                                        List<SearchEverywhereContributor> allContributors,
                                        Map<String, SearchEverywhereContributorFilter<?>> contributorFilters) {
    SearchEverywhereUI view = new SearchEverywhereUI(project, serviceContributors, allContributors, contributorFilters);

    view.setSearchFinishedHandler(() -> {
      if (isShown()) {
        myBalloon.cancel();
      }
    });

    view.addViewTypeListener(viewType -> {
      if (!isShown()) {
        return;
      }

      if (viewType == SearchEverywhereUI.ViewType.SHORT) {
        myBalloonFullSize = myBalloon.getSize();
        myBalloon.pack(false, true);
      } else {
        if (myBalloonFullSize == null) {
          myBalloonFullSize = withInsets(mySearchEverywhereUI.getPreferredSize());
        }
        myBalloon.setSize(myBalloonFullSize);
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
    if (!isShown()) {
      return;
    }

    updateHistoryIterator();
    String searchText = mySearchEverywhereUI.getSearchField().getText();
    if (!searchText.isEmpty()) {
      myHistoryList.saveText(searchText, mySearchEverywhereUI.getSelectedContributorID());
    }
  }

  private void saveSize() {
    if (mySearchEverywhereUI.getViewType() == SearchEverywhereUI.ViewType.SHORT) {
      DimensionService.getInstance().setSize(LOCATION_SETTINGS_KEY, myBalloonFullSize, myProject);
    }
  }

  private void showHistoryItem(boolean next) {
    if (!isShown()) {
      return;
    }

    updateHistoryIterator();
    JTextField searchField = mySearchEverywhereUI.getSearchField();
    searchField.setText(next ? myHistoryIterator.next() : myHistoryIterator.prev());
    searchField.selectAll();
  }

  private void updateHistoryIterator() {
    if (!isShown()) {
      return;
    }

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
      String lastHistoryItem = getLastSearchForContributor(contributorID);
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

    private String getLastSearchForContributor(String contributorID) {
      if (historyList.isEmpty()) {
        return null;
      }

      if (ALL_CONTRIBUTORS_GROUP_ID.equals(contributorID)) {
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
      if (ALL_CONTRIBUTORS_GROUP_ID.equals(contributorID)) {
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
