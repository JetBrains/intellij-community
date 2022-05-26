// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.gotoByName.SearchEverywhereConfiguration;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import static com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersStatisticsCollector.ContributorFilterCollector;
import static com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.getReportableContributorID;

public class SearchEverywhereHeader {

  private final @NotNull Runnable myScopeChangedCallback;
  private final Function<? super String, String> myShortcutSupplier;

  private final List<SETab> myTabs;
  private SETab mySelectedTab;

  private final @Nullable Project myProject;
  private final @NotNull JPanel myTabsPanel;
  private final @NotNull JPanel myToolbarPanel;
  private final @NotNull ActionToolbar myToolbar;
  private boolean myEverywhereAutoSet = true;

  public SearchEverywhereHeader(@Nullable Project project,
                                Map<SearchEverywhereContributor<?>, SearchEverywhereTabDescriptor> contributors,
                                @NotNull Runnable scopeChangedCallback, Function<? super String, String> shortcutSupplier,
                                @Nullable AnAction showInFindToolWindowAction, SearchEverywhereUI ui) {
    myScopeChangedCallback = scopeChangedCallback;
    myProject = project;
    myShortcutSupplier = shortcutSupplier;
    myTabs = createTabs(contributors);
    mySelectedTab = myTabs.get(0);
    myTabsPanel = createTabsPanel(myTabs);
    myToolbar = createToolbar(showInFindToolWindowAction);
    myToolbar.setTargetComponent(myTabsPanel);
    myToolbarPanel = (JPanel)myToolbar.getComponent();

    MessageBusConnection busConnection = myProject != null
                                         ? myProject.getMessageBus().connect(ui)
                                         : ApplicationManager.getApplication().getMessageBus().connect(ui);
    busConnection.subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
        if (action == mySelectedTab.everywhereAction && event.getInputEvent() != null) {
          myEverywhereAutoSet = false;
        }
      }
    });

    scopeChangedCallback.run();
  }

  public @NotNull JPanel getTabsPanel() {
    return myTabsPanel;
  }

  public @NotNull JPanel getToolbarPanel() {
    return myToolbarPanel;
  }

  public void toggleEverywhere() {
    myEverywhereAutoSet = false;
    if (mySelectedTab.everywhereAction == null) return;
    if (!mySelectedTab.everywhereAction.canToggleEverywhere()) return;
    mySelectedTab.everywhereAction.setEverywhere(
      !mySelectedTab.everywhereAction.isEverywhere());
    myToolbar.updateActionsImmediately();
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
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setOpaque(false);
    toolbarComponent.setBorder(JBUI.Borders.empty(2, 18, 2, 9));
    return toolbar;
  }

  @NotNull
  private static JPanel createTabsPanel(List<? extends SETab> tabs) {
    JPanel contributorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    contributorsPanel.setOpaque(false);
    tabs.forEach(tab -> contributorsPanel.add(tab));
    return contributorsPanel;
  }

  private List<SETab> createTabs(Map<SearchEverywhereContributor<?>, SearchEverywhereTabDescriptor> contributors) {
    if (Registry.is("search.everywhere.group.contributors.by.type")) {
      return createGroupedTabs(contributors);
    } else {
      ArrayList<SearchEverywhereContributor<?>> contributorsList = new ArrayList<>(contributors.keySet());
      contributorsList.sort(Comparator.comparingInt(SearchEverywhereContributor::getSortWeight));
      return createSeparateTabs(contributorsList);
    }
  }

  private List<SETab> createGroupedTabs(Map<SearchEverywhereContributor<?>, SearchEverywhereTabDescriptor> contributors) {
    List<SearchEverywhereContributor<?>> projectContributors = new ArrayList<>();
    List<SearchEverywhereContributor<?>> ideContributors = new ArrayList<>();
    contributors.forEach((contributor, tab) -> {
      if (tab == SearchEverywhereTabDescriptor.PROJECT) projectContributors.add(contributor);
      else if (tab == SearchEverywhereTabDescriptor.IDE) ideContributors.add(contributor);
      else throw new IllegalArgumentException("Unsupported tab - " + tab.getId());
    });
    List<SETab> res = new ArrayList<>();

    ElementsChooser.StatisticsCollector<String> filterStatisticsCollector = new ContributorFilterCollector();
    if (myProject != null && !projectContributors.isEmpty()) {
      PersistentSearchEverywhereContributorFilter<String> projectContributorsFilter = createContributorsFilter(projectContributors);
      List<AnAction> projectActions = Arrays.asList(
        new MyScopeChooserAction(myProject, projectContributors, myScopeChangedCallback),
        new SearchEverywhereFiltersAction<>(projectContributorsFilter, myScopeChangedCallback, filterStatisticsCollector)
      );
      res.add(createTab(SearchEverywhereTabDescriptor.PROJECT.getId(), IdeBundle.message("searcheverywhere.project.search.tab.name"),
                        projectContributors, projectActions, projectContributorsFilter));

    }

    if (!ideContributors.isEmpty()) {
      PersistentSearchEverywhereContributorFilter<String> ideContributorsFilter = createContributorsFilter(ideContributors);
      List<AnAction> ideActions = Arrays.asList(
        new CheckBoxSearchEverywhereToggleAction(IdeBundle.message("checkbox.disabled.included")) {
          private boolean everywhere;

          @Override
          public boolean isEverywhere() {
            return everywhere;
          }

          @Override
          public void setEverywhere(boolean val) {
            everywhere = val;
            ideContributors.stream()
              .flatMap(contributor -> contributor.getActions(myScopeChangedCallback).stream())
              .filter(action -> action instanceof SearchEverywhereToggleAction)
              .forEach(action -> ((SearchEverywhereToggleAction)action).setEverywhere(val));
            myScopeChangedCallback.run();
          }
        },
        new SearchEverywhereFiltersAction<>(ideContributorsFilter, myScopeChangedCallback, filterStatisticsCollector)
      );
      res.add(createTab(SearchEverywhereTabDescriptor.IDE.getId(), IdeBundle.message("searcheverywhere.ide.search.tab.name"),
                        ideContributors, ideActions, ideContributorsFilter));
    }

    return res;
  }

  private List<SETab> createSeparateTabs(List<? extends SearchEverywhereContributor<?>> contributors) {
    List<SETab> res = new ArrayList<>();

    if (contributors.size() > 1) {
      Runnable onChanged = () -> {
        myToolbar.updateActionsImmediately();
        myScopeChangedCallback.run();
      };
      String actionText = IdeUICustomization.getInstance().projectMessage("checkbox.include.non.project.items");
      PersistentSearchEverywhereContributorFilter<String> filter = createContributorsFilter(contributors);
      List<AnAction> actions = Arrays.asList(new CheckBoxSearchEverywhereToggleAction(actionText) {
        final SearchEverywhereManagerImpl seManager = (SearchEverywhereManagerImpl)SearchEverywhereManager.getInstance(myProject);
        @Override
        public boolean isEverywhere() {
          return seManager.isEverywhere();
        }

        @Override
        public void setEverywhere(boolean state) {
          if (isEverywhere() == state) return;
          seManager.setEverywhere(state);

          myTabs.forEach(tab -> {
            if (tab.everywhereAction != null) tab.everywhereAction.setEverywhere(state);
          });
          onChanged.run();
        }
      }, new SearchEverywhereFiltersAction<>(filter, onChanged, new ContributorFilterCollector()));
      SETab allTab = createTab(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID,
                               IdeBundle.message("searcheverywhere.allelements.tab.name"),
                               contributors, actions, filter);
      res.add(allTab);
    }

    contributors.stream()
      .filter(SearchEverywhereContributor::isShownInSeparateTab)
      .forEach(contributor -> {
        SETab tab = createTab(contributor, () -> {
          myToolbar.updateActionsImmediately();
          myScopeChangedCallback.run();
        });
        res.add(tab);
      });

    return res;
  }

  private static PersistentSearchEverywhereContributorFilter<String> createContributorsFilter(List<? extends SearchEverywhereContributor<?>> contributors) {
    Map<String, @Nls String> namesMap = ContainerUtil.map2Map(contributors, c -> Pair.create(c.getSearchProviderId(),
                                                                                               c.getFullGroupName()));
    return new PersistentSearchEverywhereContributorFilter<>(
      ContainerUtil.map(contributors, c -> c.getSearchProviderId()),
      SearchEverywhereConfiguration.getInstance(),
      namesMap::get, c -> null);
  }

  public List<SETab> getTabs() {
    return myTabs;
  }

  @NotNull
  public SETab getSelectedTab() {
    return mySelectedTab;
  }

  public void switchToTab(SETab tab) {
    if (!myTabs.contains(tab))
      throw new IllegalArgumentException(String.format("Tab %s is not found in tabs list", tab.toString()));

    mySelectedTab.setSelected(false);
    mySelectedTab = tab;
    mySelectedTab.setSelected(true);

    if (myEverywhereAutoSet && isEverywhere() && canToggleEverywhere()) {
      autoSetEverywhere(false);
    }

    if (myToolbar != null) {
      myToolbar.updateActionsImmediately();
    }

    myTabsPanel.repaint();
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
    mySelectedTab.everywhereAction.setEverywhere(everywhere);
    myToolbar.updateActionsImmediately();
  }

  public boolean canSetEverywhere() {
    return myEverywhereAutoSet && canToggleEverywhere();
  }

  public boolean canResetScope() {
    return Boolean.TRUE.equals(ObjectUtils.doIfNotNull(mySelectedTab.everywhereAction, action -> !action.isEverywhere()));
  }

  public void resetScope() {
    ObjectUtils.consumeIfNotNull(mySelectedTab.everywhereAction, action -> action.setEverywhere(true));
  }

  public boolean isEverywhere() {
    if (mySelectedTab.everywhereAction == null) return true;
    return mySelectedTab.everywhereAction.isEverywhere();
  }

  private boolean canToggleEverywhere() {
    if (mySelectedTab.everywhereAction == null) return false;
    return mySelectedTab.everywhereAction.canToggleEverywhere();
  }

  private SETab createTab(@NonNls @NotNull String id, @NlsContexts.Label @NotNull String name,
                          @NotNull List<? extends SearchEverywhereContributor<?>> contributors,
                          List<AnAction> actions,
                          @Nullable PersistentSearchEverywhereContributorFilter<String> filter) {
    SETab tab = new SETab(id, name, contributors, actions, filter);

    @NlsSafe String shortcut = myShortcutSupplier.apply(tab.getID());
    if (shortcut != null) {
      tab.setToolTipText(shortcut);
    }

    tab.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        switchToTab(tab);
        SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(myProject,
                                                               SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(tab.getReportableID()),
                                                               EventFields.InputEventByMouseEvent.with(e));

      }
    });

    return tab;
  }

  private SETab createTab(@NotNull SearchEverywhereContributor<?> contributor, Runnable onChanged) {
    return createTab(contributor.getSearchProviderId(), contributor.getGroupName(), Collections.singletonList(contributor),
                     contributor.getActions(onChanged), null);
  }

  public static class SETab extends JLabel {
    private final @NotNull @NonNls String id;
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
      super(name);
      myContributorsFilter = filter;
      Insets insets = JBUI.CurrentTheme.BigPopup.tabInsets();
      setBorder(JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right));

      assert contributors.size() > 0 : "Contributors list cannot be empty";
      this.id = id;
      this.contributors = new ArrayList<>(contributors);
      this.actions = actions;

      everywhereAction = (SearchEverywhereToggleAction)ContainerUtil.find(actions, o -> o instanceof SearchEverywhereToggleAction);
      myFilterToReset = actions.stream()
        .filter(a -> a instanceof SearchEverywhereFiltersAction)
        .findAny().map(a -> ((SearchEverywhereFiltersAction) a).getFilter())
        .orElse(null);
    }

    public void setSelected(boolean selected) {
      isSelected = selected;
    }

    public String getID() {
      return id;
    }

    public String getReportableID() {
      if (!isSingleContributor()) return getID();

      return getReportableContributorID(contributors.get(0));
    }

    public boolean isSingleContributor() {
      return contributors.size() == 1;
    }

    @NotNull
    public List<SearchEverywhereContributor<?>> getContributors() {
      if (myContributorsFilter == null) return contributors;

      return ContainerUtil.filter(contributors, contributor -> myContributorsFilter.isSelected(contributor.getSearchProviderId()));
    }

    public boolean canClearFilter() {
      return myFilterToReset != null && canClearFilter(myFilterToReset);
    }

    private static <T> boolean canClearFilter(@NotNull PersistentSearchEverywhereContributorFilter<T> filter) {
      return filter.getAllElements().stream().anyMatch(o -> !filter.isSelected(o));
    }

    public void clearFilter() {
      if (myFilterToReset != null) doClearFilter(myFilterToReset);
    }

    private static <T> void doClearFilter(@NotNull PersistentSearchEverywhereContributorFilter<T> filter) {
      filter.getAllElements().forEach(s -> filter.setSelected(s, true));
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = JBUIScale.scale(29);
      return size;
    }

    @Override
    public boolean isOpaque() {
      return isSelected;
    }

    @Override
    public Color getBackground() {
      return isSelected
             ? JBUI.CurrentTheme.BigPopup.selectedTabColor()
             : super.getBackground();
    }

    @Override
    public Color getForeground() {
      return isSelected
             ? JBUI.CurrentTheme.BigPopup.selectedTabTextColor()
             : super.getForeground();
    }
  }

  private static class MyScopeChooserAction extends ScopeChooserAction {
    private ScopeDescriptor myScope;
    private final Collection<SearchEverywhereContributor<?>> myContributors;
    private final Runnable onChange;

    private final GlobalSearchScope myEverywhereScope;
    private final GlobalSearchScope myProjectScope;

    private MyScopeChooserAction(@NotNull Project project,
                                 Collection<SearchEverywhereContributor<?>> contributors,
                                 Runnable onChange) {
      myContributors = contributors;
      this.onChange = onChange;

      myEverywhereScope = GlobalSearchScope.everythingScope(project);
      myProjectScope = GlobalSearchScope.projectScope(project);
      myScope = new ScopeDescriptor(myProjectScope);

      doSetScope(myScope);
    }

    private void doSetScope(@NotNull ScopeDescriptor sd) {
      myScope = sd;
      myContributors.stream()
        .filter(c -> c instanceof ScopeSupporting)
        .forEach(c -> ((ScopeSupporting)c).setScope(sd));
    }

    @Override
    protected void onScopeSelected(@NotNull ScopeDescriptor sd) {
      doSetScope(sd);
      onChange.run();
    }

    @Override
    @NotNull
    protected ScopeDescriptor getSelectedScope() {
      return myScope;
    }

    @Override
    protected void onProjectScopeToggled() {
      setEverywhere(!myScope.scopeEquals(myEverywhereScope));
    }

    @Override
    protected boolean processScopes(@NotNull Processor<? super ScopeDescriptor> processor) {
      return ContainerUtil.process(extractScopes(), processor);
    }

    @Override
    public boolean isEverywhere() {
      return myScope.scopeEquals(myEverywhereScope);
    }

    @Override
    public void setEverywhere(boolean everywhere) {
      doSetScope(new ScopeDescriptor(everywhere ? myEverywhereScope : myProjectScope));
      onChange.run();
    }

    @Override
    public boolean canToggleEverywhere() {
      return myScope.scopeEquals(myEverywhereScope) || myScope.scopeEquals(myProjectScope);
    }

    private List<ScopeDescriptor> extractScopes() {
      BinaryOperator<List<ScopeDescriptor>> intersection = (descriptors1, descriptors2) -> {
        ArrayList<ScopeDescriptor> res = new ArrayList<>(descriptors1);
        List<String> scopes = Lists.transform(res, descriptor -> descriptor.getDisplayName());
        scopes.retainAll(Lists.transform(descriptors2, descriptor -> descriptor.getDisplayName()));

        return res;
      };

      Optional<List<ScopeDescriptor>> maybeScopes = myContributors.stream()
        .filter(c -> c instanceof ScopeSupporting)
        .map(c -> ((ScopeSupporting)c).getSupportedScopes())
        .reduce(intersection);

      return maybeScopes.orElse(Collections.emptyList());
    }
  }
}
