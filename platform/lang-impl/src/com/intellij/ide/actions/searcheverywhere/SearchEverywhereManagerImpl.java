// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.SearchEverywhereConfiguration;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collector;
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
    Component contextComponent = initEvent.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    List<SearchEverywhereContributor> serviceContributors = Arrays.asList(
      new TopHitSEContributor(project, contextComponent,
                              s -> mySearchEverywhereUI.getSearchField().setText(s)),
      new RecentFilesSEContributor(project, GotoActionBase.getPsiContext(initEvent)),
      new RunConfigurationsSEContributor(project, contextComponent, () ->  mySearchEverywhereUI.getSearchField().getText())
    );

    List<SearchEverywhereContributor> contributors = new ArrayList<>(serviceContributors);
    myContributorFactories.forEach(factory -> {
      SearchEverywhereContributor contributor = factory.createContributor(initEvent);
      myContributorFilters.computeIfAbsent(contributor.getSearchProviderId(), s -> factory.createFilter(initEvent));
      contributors.add(contributor);
    });
    Collections.sort(contributors, Comparator.comparingInt(SearchEverywhereContributor::getSortWeight));
    Map<String, String> contributorsNames = contributors.stream().collect(Collectors.toMap(c -> c.getSearchProviderId(), c -> c.getGroupName()));

    myContributorFilters.computeIfAbsent(ALL_CONTRIBUTORS_GROUP_ID,
                                         s -> {
                                           List<String> ids = ContainerUtil.map(contributors, c -> c.getSearchProviderId());
                                           return new PersistentSearchEverywhereContributorFilter<>(ids,
                                                                                                    SearchEverywhereConfiguration.getInstance(project),
                                                                                                    id -> contributorsNames.get(id), id -> null);
                                         }
    );

    mySearchEverywhereUI = createView(myProject, contributors, myContributorFilters);
    mySearchEverywhereUI.switchToContributor(selectedContributorID);

    myHistoryIterator = myHistoryList.getIterator(selectedContributorID);
    //history could be suppressed by user for some reasons (creating promo video, conference demo etc.)
    boolean suppressHistory = "true".equals(System.getProperty("idea.searchEverywhere.noHistory", "false"));
    if (searchText == null && !suppressHistory) {
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
    JBInsets.addTo(size, myBalloon.getContent().getInsets());
    myBalloon.setMinimumSize(size);

    myProject.putUserData(SEARCH_EVERYWHERE_POPUP, myBalloon);
    Disposer.register(myBalloon, () -> {
      saveSize();
      myProject.putUserData(SEARCH_EVERYWHERE_POPUP, null);
      mySearchEverywhereUI = null;
      myBalloon = null;
      myBalloonFullSize = null;
    });

    if (mySearchEverywhereUI.getViewType() == SearchEverywhereUI.ViewType.SHORT) {
      myBalloonFullSize = DimensionService.getInstance().getSize(LOCATION_SETTINGS_KEY, project);
      Dimension prefSize = mySearchEverywhereUI.getPreferredSize();
      myBalloon.setSize(prefSize);
    }
    calcPositionAndShow(project, myBalloon);
  }

  private void calcPositionAndShow(Project project, JBPopup balloon) {
    Point savedLocation = DimensionService.getInstance().getLocation(LOCATION_SETTINGS_KEY, project);

    //for first show and short mode popup should be shifted to the top screen half
    if (savedLocation == null && mySearchEverywhereUI.getViewType() == SearchEverywhereUI.ViewType.SHORT) {
      Window window = project != null
                      ? WindowManager.getInstance().suggestParentWindow(project)
                      : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      Component parent = UIUtil.findUltimateParent(window);

      if (parent != null) {
        JComponent content = balloon.getContent();
        Dimension balloonSize = content.getPreferredSize();

        Point screenPoint = new Point((parent.getSize().width - balloonSize.width) / 2, parent.getHeight() / 4 - balloonSize.height / 2);
        SwingUtilities.convertPointToScreen(screenPoint, parent);

        Rectangle screenRectangle = ScreenUtil.getScreenRectangle(screenPoint);
        Insets insets = content.getInsets();
        int bottomEdge = screenPoint.y + mySearchEverywhereUI.getExpandedSize().height + insets.bottom + insets.top;
        int shift = bottomEdge - (int) screenRectangle.getMaxY();
        if (shift > 0) {
          screenPoint.y = Integer.max(screenPoint.y - shift, screenRectangle.y);
        }

        RelativePoint showPoint = new RelativePoint(screenPoint);
        balloon.show(showPoint);
        return;
      }
    }

    if (project != null) {
      balloon.showCenteredInCurrentWindow(project);
    } else {
      balloon.showInFocusCenter();
    }
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
                                        List<SearchEverywhereContributor> contributors,
                                        Map<String, SearchEverywhereContributorFilter<?>> contributorFilters) {
    SearchEverywhereUI view = new SearchEverywhereUI(project, contributors, contributorFilters);

    view.setSearchFinishedHandler(() -> {
      if (isShown()) {
        myBalloon.cancel();
      }
    });

    view.addViewTypeListener(viewType -> {
      if (!isShown()) {
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        Dimension minSize = mySearchEverywhereUI.getMinimumSize();
        JBInsets.addTo(minSize, myBalloon.getContent().getInsets());
        myBalloon.setMinimumSize(minSize);

        if (viewType == SearchEverywhereUI.ViewType.SHORT) {
          myBalloonFullSize = myBalloon.getSize();
          JBInsets.removeFrom(myBalloonFullSize, myBalloon.getContent().getInsets());
          myBalloon.pack(false, true);
        } else {
          if (myBalloonFullSize == null) {
            myBalloonFullSize = mySearchEverywhereUI.getPreferredSize();
            JBInsets.addTo(myBalloonFullSize, myBalloon.getContent().getInsets());
          }
          myBalloonFullSize.height = Integer.max(myBalloonFullSize.height, minSize.height);
          myBalloonFullSize.width = Integer.max(myBalloonFullSize.width, minSize.width);
          myBalloon.setSize(myBalloonFullSize);
        }
      });
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

      HistoryItem(String searchText, String contributorID) {
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

    public void saveText(@NotNull String text, @NotNull String contributorID) {
      historyList.stream()
        .filter(item -> text.equals(item.getSearchText()) && contributorID.equals(item.getContributorID()))
        .findFirst()
        .ifPresent(historyList::remove);

      historyList.add(new HistoryItem(text, contributorID));

      List<String> list = filteredHistory(item -> item.getContributorID().equals(contributorID));
      if (list.size() > HISTORY_LIMIT) {
        historyList.stream()
                   .filter(item -> item.getContributorID().equals(contributorID))
                   .findFirst()
                   .ifPresent(historyList::remove);
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
        .collect(distinctCollector);
    }

    private final static Collector<String, List<String>, List<String>> distinctCollector = Collector.of(
      () -> new ArrayList<>(),
      (lst, str) -> {
        lst.remove(str);
        lst.add(str);
      },
      (lst1, lst2) -> {
        lst1.removeAll(lst2);
        lst1.addAll(lst2);
        return lst1;
      }
    );
  }

  private static class HistoryIterator {

    private final String contributorID;
    private final List<String> list;
    private int index;

    HistoryIterator(String id, List<String> list) {
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
