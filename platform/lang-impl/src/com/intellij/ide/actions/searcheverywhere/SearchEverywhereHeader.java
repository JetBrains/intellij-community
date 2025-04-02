// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector;
import com.intellij.ide.util.gotoByName.SearchEverywhereConfiguration;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersStatisticsCollector.ContributorFilterCollector;
import static com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.getReportableContributorID;

@ApiStatus.Internal
public final class SearchEverywhereHeader {
  private final @NotNull Runnable myScopeChangedCallback;
  private final Function<? super String, String> myShortcutSupplier;

  private final List<SETab> myTabs;
  private SETab mySelectedTab;

  private final @Nullable Project myProject;
  private final @NotNull JComponent header;
  private @Nullable SENewUIHeaderView newUIHeaderView;
  private final @NotNull ActionToolbar myToolbar;
  private boolean myEverywhereAutoSet = true;

  public SearchEverywhereHeader(@Nullable Project project,
                                List<SearchEverywhereContributor<?>> contributors,
                                @NotNull Runnable scopeChangedCallback,
                                Function<? super String, String> shortcutSupplier,
                                @Nullable AnAction showInFindToolWindowAction,
                                SearchEverywhereUI ui) {
    myScopeChangedCallback = scopeChangedCallback;
    myProject = project;
    myShortcutSupplier = shortcutSupplier;
    myTabs = createTabs(contributors);
    mySelectedTab = myTabs.get(0);
    myToolbar = createToolbar(showInFindToolWindowAction);
    header = ExperimentalUI.isNewUI() ? createNewUITabs() : createHeader();

    ApplicationManager.getApplication().getMessageBus().connect(ui).subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
        if (action == mySelectedTab.everywhereAction && event.getInputEvent() != null) {
          myEverywhereAutoSet = false;
        }
      }
    });

    scopeChangedCallback.run();
  }

  public @NotNull JComponent getComponent() {
    return header;
  }

  public void toggleEverywhere() {
    myEverywhereAutoSet = false;
    if (mySelectedTab.everywhereAction == null) return;
    if (!mySelectedTab.everywhereAction.canToggleEverywhere()) return;
    mySelectedTab.everywhereAction.setScopeIsDefaultAndAutoSet(false);
    mySelectedTab.everywhereAction.setEverywhere(
      !mySelectedTab.everywhereAction.isEverywhere());
    myToolbar.updateActionsImmediately();
  }

  @ApiStatus.Internal
  public void changeScope(@NotNull Function2<? super @NotNull ScopeDescriptor, ? super @NotNull List<? extends @NotNull ScopeDescriptor>, ? extends @Nullable ScopeDescriptor> processor) {
    if (mySelectedTab.everywhereAction == null) return;
    if (mySelectedTab.everywhereAction instanceof ScopeChooserAction sca) {
      List<ScopeDescriptor> scopes = new ArrayList<>();
      sca.processScopes(scopes::add);
      ScopeDescriptor currentScope = sca.getSelectedScope();
      ScopeDescriptor newScope = processor.invoke(currentScope, scopes);
      if (newScope != null && newScope != currentScope) {
        sca.onScopeSelected(newScope);
        myToolbar.updateActionsImmediately();
      }
    }
  }

  private @NotNull ActionToolbar createToolbar(@Nullable AnAction showInFindToolWindowAction) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addAction(new ActionGroup() {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        if (e == null || mySelectedTab == null) return EMPTY_ARRAY;
        return mySelectedTab.actions.toArray(EMPTY_ARRAY);
      }
    });

    if (showInFindToolWindowAction != null) {
      actionGroup.addAction(showInFindToolWindowAction);
    }

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("search.everywhere.toolbar", actionGroup, true);
    toolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setOpaque(false);
    toolbarComponent.setBorder(JBUI.Borders.empty(2, 18, 2, 9));
    return toolbar;
  }

  private @NotNull JComponent createHeader() {
    JPanel contributorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    contributorsPanel.setOpaque(false);
    for (SETab tab : myTabs) {
      SETabLabel tabLabel = new SETabLabel(tab);
      @NlsSafe String shortcut = myShortcutSupplier.apply(tab.getID());
      if (shortcut != null) {
        tabLabel.setToolTipText(shortcut);
      }

      tabLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          switchToTab(tab);
          SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(myProject,
                                                                 SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(
                                                                   tab.getReportableID()),
                                                                 EventFields.InputEventByMouseEvent.with(e));
        }
      });
      contributorsPanel.add(tabLabel);
    }
    myToolbar.setTargetComponent(contributorsPanel);

    JPanel result = new JPanel(new BorderLayout());
    result.add(contributorsPanel, BorderLayout.WEST);
    result.add(myToolbar.getComponent(), BorderLayout.EAST);

    return result;
  }

  private @NotNull JComponent createNewUITabs() {
    newUIHeaderView = new SENewUIHeaderView(myTabs, myShortcutSupplier, myToolbar.getComponent());
    newUIHeaderView.tabbedPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        SETab selectedTab = myTabs.get(newUIHeaderView.tabbedPane.getSelectedIndex());
        switchToTab(selectedTab);
        SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(
          myProject, SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(selectedTab.getReportableID()));
        if (SearchEverywhereUI.isExtendedInfoEnabled()) {
          ApplicationManager.getApplication().getMessageBus().syncPublisher(SETabSwitcherListener.Companion.getSE_TAB_TOPIC())
            .tabSwitched(new SETabSwitcherListener.SETabSwitchedEvent(selectedTab));
        }
      }
    });
    myToolbar.setTargetComponent(newUIHeaderView.panel);
    return newUIHeaderView.panel;
  }

  private List<SETab> createTabs(List<SearchEverywhereContributor<?>> contributors) {
    List<SETab> result = new ArrayList<>();

    contributors = contributors.stream()
      .sorted(Comparator.comparingInt(SearchEverywhereContributor::getSortWeight))
      .collect(Collectors.toList());

    Runnable onChanged = () -> {
      myToolbar.updateActionsImmediately();
      myScopeChangedCallback.run();
    };

    if (contributors.size() > 1) {
      result.add(createAllTab(contributors, onChanged));
    }

    List<SearchEverywhereContributor<?>> separateTabContributors;
    try {
      separateTabContributors = TabsCustomizationStrategy.getInstance().getSeparateTabContributors(contributors);
    }
    catch (Exception e) {
      Logger.getInstance(SearchEverywhereHeader.class).error(e);
      separateTabContributors = contributors.stream().filter(it -> it.isShownInSeparateTab()).toList();
    }

    for (SearchEverywhereContributor<?> contributor : separateTabContributors) {
      try {
        result.add(createTab(contributor, onChanged));
      }
      catch (Exception e) {
        Logger.getInstance(SearchEverywhereHeader.class).error(e);
      }
    }

    return result;
  }

  private static PersistentSearchEverywhereContributorFilter<String> createContributorsFilter(List<? extends SearchEverywhereContributor<?>> contributors) {
    Map<String, @Nls String> namesMap = ContainerUtil.map2Map(contributors, c -> new Pair<>(c.getSearchProviderId(), c.getFullGroupName()));
    return new PersistentSearchEverywhereContributorFilter<>(
      ContainerUtil.map(contributors, c -> c.getSearchProviderId()),
      SearchEverywhereConfiguration.getInstance(),
      namesMap::get, c -> null);
  }

  public List<SETab> getTabs() {
    return myTabs;
  }

  public @NotNull SETab getSelectedTab() {
    return mySelectedTab;
  }

  public void switchToTab(SETab tab) {
    if (!myTabs.contains(tab)) {
      throw new IllegalArgumentException(String.format("Tab %s is not found in tabs list", tab.toString()));
    }

    mySelectedTab.setSelected(false);
    mySelectedTab = tab;
    mySelectedTab.setSelected(true);

    if (newUIHeaderView != null) {
      newUIHeaderView.tabbedPane.setSelectedIndex(myTabs.indexOf(tab));
    }

    if (myEverywhereAutoSet && isEverywhere() && canToggleEverywhere()) {
      autoSetEverywhere(false);
    }

    myToolbar.updateActionsImmediately();

    header.repaint();
    myScopeChangedCallback.run();
  }

  public void switchToNextTab() {
    int index = myTabs.indexOf(mySelectedTab);
    index++;
    if (index >= myTabs.size()) index = 0;
    switchToTab(myTabs.get(index));
  }

  public void switchToPrevTab() {
    int index = myTabs.indexOf(mySelectedTab);
    index--;
    if (index < 0) index = myTabs.size() - 1;
    switchToTab(myTabs.get(index));
  }

  public void autoSetEverywhere(boolean everywhere) {
    if (everywhere == isEverywhere()) return;
    myEverywhereAutoSet = true;
    if (mySelectedTab.everywhereAction == null) return;
    if (!mySelectedTab.everywhereAction.canToggleEverywhere()) return;
    mySelectedTab.everywhereAction.setScopeIsDefaultAndAutoSet(!everywhere);
    mySelectedTab.everywhereAction.setEverywhere(everywhere);
    myToolbar.updateActionsImmediately();
  }

  public boolean canSetEverywhere() {
    return myEverywhereAutoSet && canToggleEverywhere();
  }

  public boolean canResetScope() {
    return Boolean.TRUE.equals(mySelectedTab.everywhereAction == null ? null : !mySelectedTab.everywhereAction.isEverywhere());
  }

  public void resetScope() {
    if (mySelectedTab.everywhereAction != null) {
      mySelectedTab.everywhereAction.setEverywhere(true);
    }
  }

  public boolean isEverywhere() {
    if (mySelectedTab.everywhereAction == null) return true;
    return mySelectedTab.everywhereAction.isEverywhere();
  }

  private boolean canToggleEverywhere() {
    if (mySelectedTab.everywhereAction == null) return false;
    return mySelectedTab.everywhereAction.canToggleEverywhere();
  }

  private static SETab createTab(@NotNull SearchEverywhereContributor<?> contributor, Runnable onChanged) {
    return new SETab(contributor.getSearchProviderId(), contributor.getGroupName(), Collections.singletonList(contributor),
                     contributor.getActions(onChanged), null);
  }

  private @NotNull SETab createAllTab(List<? extends SearchEverywhereContributor<?>> contributors, @NotNull Runnable onChanged) {
    String actionText = IdeUICustomization.getInstance().projectMessage("checkbox.include.non.project.items");
    PersistentSearchEverywhereContributorFilter<String> filter = createContributorsFilter(contributors);
    var contributorToEverywhereAction = new IdentityHashMap<SearchEverywhereContributor<?>, SearchEverywhereToggleAction>();
    for (SearchEverywhereContributor<?> c : contributors) {
      var everywhereAction = ContainerUtil.getFirstItem(
        ContainerUtil.filterIsInstance(c.getActions(onChanged), SearchEverywhereToggleAction.class));
      if (everywhereAction != null) {
        contributorToEverywhereAction.put(c, everywhereAction);
      }
    }

    List<AnAction> actions = List.of(new CheckBoxSearchEverywhereToggleAction(actionText) {
      final SearchEverywhereManagerImpl seManager = (SearchEverywhereManagerImpl)SearchEverywhereManager.getInstance(myProject);

      @Override
      public boolean isEverywhere() {
        return seManager.isEverywhere();
      }

      @Override
      public void setEverywhere(boolean state) {
        if (isEverywhere() == state) return;
        seManager.setEverywhere(state);

        var separateTabsContributors = myTabs.stream()
          .filter(t -> !t.id.equals(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID)) // filter other tabs
          .flatMap(t -> t.contributors.stream()).toList();
        // Set everywhere flag in the contributors that only appear in the 'All' tab
        for (var entry : contributorToEverywhereAction.entrySet()) {
          if (!separateTabsContributors.contains(entry.getKey())) {
            entry.getValue().setEverywhere(state);
          }
        }

        myTabs.forEach(tab -> {
          if (tab.everywhereAction != null) tab.everywhereAction.setEverywhere(state);
        });
        onChanged.run();
      }
    }, new PreviewAction(), new SearchEverywhereFiltersAction<>(filter, onChanged, new ContributorFilterCollector()));
    return new SETab(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID,
                     IdeBundle.message("searcheverywhere.allelements.tab.name"),
                     contributors, actions, filter);
  }

  public void setResultsNotifyCallback(@NotNull Consumer<@NotNull @Nls String> notifyCallback) {
    for (SETab tab : myTabs) {
      tab.setResultsNotifyCallback(notifyCallback);
    }
  }

  public static final class SETab {
    private final @NotNull @NonNls String id;
    private final @NlsContexts.Label @NotNull String name;
    private final @NotNull List<SearchEverywhereContributor<?>> contributors;
    private final List<AnAction> actions;
    private final @Nullable SearchEverywhereToggleAction everywhereAction;
    private final @Nullable PersistentSearchEverywhereContributorFilter<String> myContributorsFilter;
    private final @Nullable PersistentSearchEverywhereContributorFilter<?> myFilterToReset;

    private boolean isSelected = false;

    public SETab(@NonNls @NotNull String id,
                 @NlsContexts.Label @NotNull String name,
                 @NotNull List<? extends SearchEverywhereContributor<?>> contributors,
                 List<AnAction> actions,
                 @Nullable PersistentSearchEverywhereContributorFilter<String> filter) {
      this.name = name;
      myContributorsFilter = filter;

      assert !contributors.isEmpty() : "Contributors list cannot be empty";
      this.id = id;
      this.contributors = new ArrayList<>(contributors);
      this.actions = actions;

      everywhereAction = (SearchEverywhereToggleAction)ContainerUtil.find(actions, o -> o instanceof SearchEverywhereToggleAction);
      myFilterToReset = actions.stream()
        .filter(a -> a instanceof SearchEverywhereFiltersAction)
        .findAny().map(a -> ((SearchEverywhereFiltersAction)a).getFilter())
        .orElse(null);
    }

    public void setSelected(boolean selected) {
      isSelected = selected;
    }

    public @NotNull @NonNls String getID() {
      return id;
    }

    public @NlsContexts.Label @NotNull String getName() {
      return name;
    }

    String getReportableID() {
      if (!isSingleContributor()) return getID();

      return getReportableContributorID(contributors.get(0));
    }

    public void setResultsNotifyCallback(@NotNull Consumer<@NotNull @Nls String> notifyCallback) {
      for (SearchEverywhereContributor<?> contributor : contributors) {
        var effectiveContributor = (contributor instanceof SearchEverywhereContributorWrapper wrapper)
                                   ? wrapper.getEffectiveContributor() : contributor;

        if (effectiveContributor instanceof SearchEverywhereResultsNotifier notifierContributor) {
          notifierContributor.setNotifyCallback(notifyCallback);
        }
      }
    }

    public boolean isSingleContributor() {
      return contributors.size() == 1;
    }

    public @Unmodifiable @NotNull List<SearchEverywhereContributor<?>> getContributors() {
      if (myContributorsFilter == null) return contributors;

      return ContainerUtil.filter(contributors, contributor -> myContributorsFilter.isSelected(contributor.getSearchProviderId()));
    }

    public boolean canClearFilter() {
      return myFilterToReset != null && canClearFilter(myFilterToReset);
    }

    private static <T> boolean canClearFilter(@NotNull PersistentSearchEverywhereContributorFilter<T> filter) {
      return ContainerUtil.exists(filter.getAllElements(), o -> !filter.isSelected(o));
    }

    public void clearFilter() {
      if (myFilterToReset != null) doClearFilter(myFilterToReset);
    }

    private static <T> void doClearFilter(@NotNull PersistentSearchEverywhereContributorFilter<T> filter) {
      filter.getAllElements().forEach(s -> filter.setSelected(s, true));
    }
  }

  private static final class SETabLabel extends JLabel {

    /**
     * Can be null while initialization
     */
    private final SETab seTab;

    private SETabLabel(@NotNull SETab tab) {
      super(tab.getName());
      seTab = tab;

      Insets insets = JBUI.CurrentTheme.BigPopup.tabInsets();
      setBorder(JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right));
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = JBUIScale.scale(29);
      return size;
    }

    @Override
    public boolean isOpaque() {
      return seTab != null && seTab.isSelected;
    }

    @Override
    public Color getBackground() {
      return seTab != null && seTab.isSelected
             ? JBUI.CurrentTheme.BigPopup.selectedTabColor()
             : super.getBackground();
    }

    @Override
    public Color getForeground() {
      return seTab != null && seTab.isSelected
             ? JBUI.CurrentTheme.BigPopup.selectedTabTextColor()
             : super.getForeground();
    }
  }
}
