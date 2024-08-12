// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.actions.BigPopupUI;
import com.intellij.ide.actions.OpenInRightSplitAction;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.WindowStateService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collector;

import static com.intellij.ide.actions.SearchEverywhereAction.SEARCH_EVERYWHERE_POPUP;
import static com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.DIALOG_CLOSED;

public final class SearchEverywhereManagerImpl implements SearchEverywhereManager {
  public static final String ALL_CONTRIBUTORS_GROUP_ID = "SearchEverywhereContributor.All";
  public static final String LOCATION_SETTINGS_KEY = "search.everywhere.popup";

  public static final DataKey<Boolean> IS_SELECT_SEARCH_TEXT = DataKey.create("search.everywhere.is.select.search.text");

  private final Map<String, String> myTabsShortcutsMap;

  private final Project myProject;

  private JBPopup myBalloon;
  private SearchEverywhereUI mySearchEverywhereUI;
  private Dimension myBalloonFullSize;

  private final SearchHistoryList myHistoryList = new SearchHistoryList();
  private final Map<String, Object> myPrevSelections = new HashMap<>();
  private HistoryIterator myHistoryIterator;
  private boolean myEverywhere;

  public SearchEverywhereManagerImpl() {
    myProject = null;
    myTabsShortcutsMap = Collections.emptyMap();
  }

  public SearchEverywhereManagerImpl(Project project) {
    myProject = project;
    myTabsShortcutsMap = createShortcutsMap();
  }

  @Override
  public void show(@NotNull String tabID, @Nullable String searchText, @NotNull AnActionEvent initEvent) {
    if (isShown()) {
      throw new IllegalStateException("Method should cannot be called when popup is shown");
    }

    Project project = initEvent.getProject();

    List<SearchEverywhereContributor<?>> contributors = createContributors(initEvent, project);
    SearchEverywhereContributorValidationRule.updateContributorsMap(contributors);
    SearchEverywhereSpellingCorrector spellingCorrector = SearchEverywhereSpellingCorrector.getInstance(project);
    mySearchEverywhereUI = createView(myProject, contributors, spellingCorrector);
    contributors.forEach(c -> Disposer.register(mySearchEverywhereUI, c));
    mySearchEverywhereUI.switchToTab(tabID);

    myHistoryIterator = myHistoryList.getIterator(tabID);
    //history could be suppressed by user for some reasons (creating promo video, conference demo etc.)
    boolean suppressHistory = SystemProperties.getBooleanProperty("idea.searchEverywhere.noHistory", false);
    //or could be suppressed just for All tab in registry
    suppressHistory = suppressHistory ||
                      (ALL_CONTRIBUTORS_GROUP_ID.equals(tabID) &&
                       Registry.is("search.everywhere.disable.history.for.all"));

    if (searchText == null && !suppressHistory) {
      searchText = myHistoryIterator.prev();
    }

    myBalloon = JBPopupFactory.getInstance().createComponentPopupBuilder(mySearchEverywhereUI, mySearchEverywhereUI.getSearchField())
      .setProject(myProject)
      .setModalContext(false)
      .setNormalWindowLevel(StartupUiUtil.isWaylandToolkit())
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setCancelKeyEnabled(false)
      .setCancelCallback(() -> {
        saveSearchText();
        DIALOG_CLOSED.log(myProject);
        return true;
      })
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, LOCATION_SETTINGS_KEY, true)
      .setLocateWithinScreenBounds(false)
      .createPopup();
    Disposer.register(myBalloon, mySearchEverywhereUI);
    OpenInRightSplitAction.Companion.overrideDoubleClickWithOneClick(myBalloon.getContent());

    if (project != null) {
      Disposer.register(project, myBalloon);
    }

    Dimension size = mySearchEverywhereUI.getMinimumSize();
    JBInsets.addTo(size, myBalloon.getContent().getInsets());
    myBalloon.setMinimumSize(size);

    UserDataHolder dataHolder = myProject != null ? project : ApplicationManager.getApplication();
    ConcurrentHashMap<ClientId, JBPopup> map = dataHolder.getUserData(SEARCH_EVERYWHERE_POPUP);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      dataHolder.putUserData(SEARCH_EVERYWHERE_POPUP, map);
    }
    map.put(ClientId.getCurrent(), myBalloon);

    if (searchText != null && !searchText.isEmpty()) {
      mySearchEverywhereUI.getSearchField().setText(searchText);
      if (!Boolean.FALSE.equals(initEvent.getData(IS_SELECT_SEARCH_TEXT))) {
        mySearchEverywhereUI.getSearchField().selectAll();
      }
    }

    Disposer.register(myBalloon, () -> {
      saveSize();
      Objects.requireNonNull(dataHolder.getUserData(SEARCH_EVERYWHERE_POPUP)).remove(ClientId.getCurrent());
      mySearchEverywhereUI = null;
      myBalloon = null;
      myBalloonFullSize = null;
    });

    myBalloonFullSize = getStateService().getSize(LOCATION_SETTINGS_KEY);
    if (mySearchEverywhereUI.getViewType() == BigPopupUI.ViewType.SHORT) {
      Dimension prefSize = mySearchEverywhereUI.getPreferredSize();
      myBalloon.setSize(prefSize);
    }
    calcPositionAndShow(initEvent, project, myBalloon);
  }

  @Override
  public @NotNull SearchEverywhereUI getCurrentlyShownUI() {
    checkIsShown();
    return mySearchEverywhereUI;
  }

  private WindowStateService getStateService() {
    return myProject != null ? WindowStateService.getInstance(myProject) : WindowStateService.getInstance();
  }

  private static List<SearchEverywhereContributor<?>> createContributors(@NotNull AnActionEvent initEvent, Project project) {
    SearchEverywhereMlContributorReplacement.saveInitEvent(initEvent);
    if (project == null) {
      ActionSearchEverywhereContributor.Factory factory = new ActionSearchEverywhereContributor.Factory();
      return Collections.singletonList(factory.createContributor(initEvent));
    }

    List<SearchEverywhereContributor<?>> res = new ArrayList<>();
    for (SearchEverywhereContributorFactory<?> factory : SearchEverywhereContributor.EP_NAME.getExtensionList()) {
      if (factory.isAvailable(project)) {
        SearchEverywhereContributor<?> contributor = factory.createContributor(initEvent);
        res.add(contributor);
      }
    }

    return res;
  }

  private void calcPositionAndShow(@NotNull AnActionEvent initEvent,
                                   Project project,
                                   JBPopup balloon) {
    if (initEvent.getPlace().equals(ActionPlaces.RUN_TOOLBAR_LEFT_SIDE)) {
      var component = (Component)initEvent.getInputEvent().getSource();
      balloon.setLocation(component.getLocationOnScreen());
      ((AbstractPopup)balloon).show(component, 0, 0, true);
      return;
    }

    Point savedLocation = getStateService().getLocation(LOCATION_SETTINGS_KEY);

    //for first show and short mode popup should be shifted to the top screen half
    if (savedLocation == null && mySearchEverywhereUI.getViewType() == BigPopupUI.ViewType.SHORT) {
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
        int shift = bottomEdge - (int)screenRectangle.getMaxY();
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
    }
    else {
      balloon.showInFocusCenter();
    }
  }

  @Override
  public boolean isShown() {
    return mySearchEverywhereUI != null && myBalloon != null && !myBalloon.isDisposed();
  }

  @Override
  public @NotNull String getSelectedTabID() {
    checkIsShown();
    return mySearchEverywhereUI.getSelectedTabID();
  }

  @Override
  public void setSelectedTabID(@NotNull String tabID) {
    checkIsShown();
    if (!tabID.equals(getSelectedTabID())) {
      mySearchEverywhereUI.switchToTab(tabID);
    }
  }

  @Override
  public void toggleEverywhereFilter() {
    checkIsShown();
    mySearchEverywhereUI.toggleEverywhereFilter();
  }

  @Override
  public boolean isEverywhere() {
    return myEverywhere;
  }

  public void setEverywhere(boolean everywhere) {
    myEverywhere = everywhere;
  }

  private SearchEverywhereUI createView(Project project, List<SearchEverywhereContributor<?>> contributors,
                                        @Nullable SearchEverywhereSpellingCorrector spellingCorrector) {
    if (LightEdit.owns(project)) {
      contributors = ContainerUtil.filter(contributors, (contributor) -> contributor instanceof LightEditCompatible);
    }
    SearchEverywhereUI view = new SearchEverywhereUI(project, contributors, myTabsShortcutsMap::get, spellingCorrector);

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
        if (myBalloon == null || myBalloon.isDisposed()) return;

        Dimension minSize = view.getMinimumSize();
        JBInsets.addTo(minSize, myBalloon.getContent().getInsets());
        myBalloon.setMinimumSize(minSize);

        if (viewType == BigPopupUI.ViewType.SHORT) {
          myBalloonFullSize = myBalloon.getSize();
          JBInsets.removeFrom(myBalloonFullSize, myBalloon.getContent().getInsets());
          myBalloon.pack(false, true);
        }
        else {
          if (myBalloonFullSize == null) {
            myBalloonFullSize = view.getPreferredSize();
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
      myHistoryList.saveText(searchText, mySearchEverywhereUI.getSelectedTabID());
    }
    myPrevSelections.put(mySearchEverywhereUI.getSelectedTabID(), mySearchEverywhereUI.getSelectionIdentity());
  }

  public @Nullable Object getPrevSelection(String contributorID) {
    return myPrevSelections.remove(contributorID);
  }

  private void saveSize() {
    if (mySearchEverywhereUI.getViewType() == BigPopupUI.ViewType.SHORT) {
      getStateService().putSize(LOCATION_SETTINGS_KEY, myBalloonFullSize);
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
  @NotNull
  List<String> getHistoryItems() {
    if (!isShown()) return ContainerUtil.emptyList();

    updateHistoryIterator();
    return myHistoryIterator.list;
  }

  private void updateHistoryIterator() {
    if (!isShown()) {
      return;
    }

    String selectedContributorID = mySearchEverywhereUI.getSelectedTabID();
    if (myHistoryIterator == null || !myHistoryIterator.getContributorID().equals(selectedContributorID)) {
      myHistoryIterator = myHistoryList.getIterator(selectedContributorID);
    }
  }

  private static Map<String, String> createShortcutsMap() {
    Map<String, @Nls String> res = new HashMap<>();

    res.put(ALL_CONTRIBUTORS_GROUP_ID, LangBundle.message("double.shift"));
    addShortcut(res, "ClassSearchEverywhereContributor", "GotoClass");
    addShortcut(res, "FileSearchEverywhereContributor", "GotoFile");
    addShortcut(res, "SymbolSearchEverywhereContributor", "GotoSymbol");
    addShortcut(res, "ActionSearchEverywhereContributor", "GotoAction");
    addShortcut(res, "DbSETablesContributor", "GotoDatabaseObject");
    addShortcut(res, "TextSearchContributor", "TextSearchAction");
    addShortcut(res, "UrlSearchEverywhereContributor", "GotoUrlAction");

    return res;
  }

  private static void addShortcut(Map<String, @Nls String> map, String tabId, String actionID) {
    KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(actionID);
    if (shortcut != null) map.put(tabId, KeymapUtil.getShortcutText(shortcut));
  }

  private static final class SearchHistoryList {

    private static final int HISTORY_LIMIT = 50;

    private record HistoryItem(String searchText, String contributorID) {
    }

    private final List<HistoryItem> historyList = new ArrayList<>();

    public HistoryIterator getIterator(String contributorID) {
      List<String> list = getHistoryForContributor(contributorID);
      return new HistoryIterator(contributorID, list);
    }

    public void saveText(@NotNull String text, @NotNull String contributorID) {
      historyList.stream()
        .filter(item -> text.equals(item.searchText()) && contributorID.equals(item.contributorID()))
        .findFirst()
        .ifPresent(historyList::remove);

      historyList.add(new HistoryItem(text, contributorID));

      List<String> list = filteredHistory(item -> item.contributorID().equals(contributorID));
      if (list.size() > HISTORY_LIMIT) {
        historyList.stream()
          .filter(item -> item.contributorID().equals(contributorID))
          .findFirst()
          .ifPresent(historyList::remove);
      }
    }

    private List<String> getHistoryForContributor(String contributorID) {
      if (ALL_CONTRIBUTORS_GROUP_ID.equals(contributorID)) {
        List<String> res = filteredHistory(item -> true);
        int size = res.size();
        return size > HISTORY_LIMIT ? res.subList(size - HISTORY_LIMIT, size) : res;
      }
      else {
        return filteredHistory(item -> item.contributorID().equals(contributorID));
      }
    }

    private @NotNull List<String> filteredHistory(Predicate<? super HistoryItem> predicate) {
      return historyList.stream()
        .filter(predicate)
        .map(item -> item.searchText())
        .collect(distinctCollector);
    }

    private static final Collector<String, List<String>, List<String>> distinctCollector = Collector.of(
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

  private static final class HistoryIterator {

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