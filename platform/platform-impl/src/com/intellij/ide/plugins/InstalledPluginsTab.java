// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.marketplace.CheckErrorsResult;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.newui.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.containers.ContainerUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.*;


@ApiStatus.Internal
class InstalledPluginsTab extends PluginsTab {
  private final @NotNull PluginModelFacade myPluginModelFacade;
  private final @NotNull PluginUpdatesService myPluginUpdatesService;
  private final @NotNull CoroutineScope myCoroutineScope;
  private final @Nullable Consumer<String> mySearchInMarketplaceTabHandler;

  private @Nullable PluginsGroupComponentWithProgress myInstalledPanel = null;
  private @Nullable SearchResultPanel myInstalledSearchPanel = null;
  private final DefaultActionGroup myInstalledSearchGroup;
  private boolean myInstalledSearchSetState = true;

  private final PluginsGroup myBundledUpdateGroup =
    new PluginsGroup(IdeBundle.message("plugins.configurable.bundled.updates"), PluginsGroupType.BUNDLED_UPDATE);

  private final LinkLabel<Object> myUpdateAll = new PluginManagerConfigurablePanel.LinkLabelButton<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final LinkLabel<Object> myUpdateAllBundled = new PluginManagerConfigurablePanel.LinkLabelButton<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final JLabel myUpdateCounter = new CountComponent();
  private final JLabel myUpdateCounterBundled = new CountComponent();

  InstalledPluginsTab(@NotNull PluginModelFacade facade,
                      @NotNull PluginUpdatesService service,
                      @NotNull CoroutineScope scope,
                      @Nullable Consumer<String> searchInMarketplaceHandler) {
    super();
    myPluginModelFacade = facade;
    myPluginUpdatesService = service;
    myCoroutineScope = scope;
    mySearchInMarketplaceTabHandler = searchInMarketplaceHandler;
    myInstalledSearchGroup = new DefaultActionGroup();
    for (InstalledSearchOption option : InstalledSearchOption.values()) {
      myInstalledSearchGroup.add(new InstalledSearchOptionAction(option));
    }
  }

  public @Nullable PluginsGroupComponentWithProgress getInstalledPanel() {
    return myInstalledPanel;
  }

  public @Nullable SearchResultPanel getInstalledSearchPanel() {
    return myInstalledSearchPanel;
  }

  public @Nullable List<UIPluginGroup> getInstalledGroups() {
    return getInstalledPanel() != null ? getInstalledPanel().getGroups() : null;
  }

  @Override
  protected @NotNull PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener) {
    PluginDetailsPageComponent detailPanel = new PluginDetailsPageComponent(myPluginModelFacade, searchListener, false);
    myPluginModelFacade.getModel().addDetailPanel(detailPanel);
    return detailPanel;
  }

  @Override
  protected @NotNull JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
    MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
    myInstalledPanel = new PluginsGroupComponentWithProgress(eventHandler) {
      @Override
      protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                 @NotNull PluginsGroup group,
                                                                 @NotNull ListPluginModel listPluginModel) {
        return new ListPluginComponent(myPluginModelFacade, model, group, listPluginModel, searchListener, myCoroutineScope, false);
      }
    };

    myInstalledPanel.setSelectionListener(selectionListener);
    myInstalledPanel.getAccessibleContext().setAccessibleName(IdeBundle.message("plugin.manager.installed.panel.accessible.name"));
    registerCopyProvider(myInstalledPanel);

    //noinspection ConstantConditions
    ((SearchUpDownPopupController)myInstalledSearchPanel.controller).setEventHandler(eventHandler);
    myInstalledPanel.showLoadingIcon();

    PluginsGroup downloaded =
      new PluginsGroup(IdeBundle.message("plugins.configurable.downloaded"), PluginsGroupType.INSTALLED);

    PluginsGroup installing = new PluginsGroup(IdeBundle.message("plugins.configurable.installing"), PluginsGroupType.INSTALLING);
    PluginManagerPanelFactory.INSTANCE.createInstalledPanel(myCoroutineScope, myPluginModelFacade.getModel(), model -> {
      try {
        myPluginModelFacade.getModel().setDownloadedGroup(myInstalledPanel, downloaded, installing);
        installing.getPreloadedModel().setErrors(model.getErrors());
        installing.getPreloadedModel().setPluginInstallationStates(model.getInstallationStates());
        installing.addModels(MyPluginModel.getInstallingPlugins());
        if (!installing.getModels().isEmpty()) {
          installing.sortByName();
          installing.titleWithCount();
          myInstalledPanel.addGroup(installing);
        }

        downloaded.getPreloadedModel().setErrors(model.getErrors());
        downloaded.getPreloadedModel().setPluginInstallationStates(model.getInstallationStates());
        downloaded.addModels(model.getInstalledPlugins());

        myBundledUpdateGroup.getPreloadedModel().setErrors(model.getErrors());
        myBundledUpdateGroup.getPreloadedModel().setPluginInstallationStates(model.getInstallationStates());

        // bundled includes bundled plugin updates
        List<PluginUiModel> visibleNonBundledPlugins, visibleBundledPlugins;
        {
          Map<Boolean, List<PluginUiModel>> visiblePlugins = model.getVisiblePlugins()
            .stream()
            .collect(Collectors.partitioningBy(plugin -> plugin.isBundled() || plugin.isBundledUpdate()));
          visibleNonBundledPlugins = visiblePlugins.get(Boolean.FALSE);
          visibleBundledPlugins = visiblePlugins.get(Boolean.TRUE);
        }
        List<PluginId> installedPluginIds = ContainerUtil.map(model.getInstalledPlugins(), it -> it.getPluginId());
        List<PluginUiModel> nonBundledPlugins =
          ContainerUtil.filter(visibleNonBundledPlugins, it -> !installedPluginIds.contains(it.getPluginId()));
        downloaded.addModels(nonBundledPlugins);

        LinkListener<Object> updateAllListener = new LinkListener<>() {
          @Override
          public void linkSelected(LinkLabel<Object> aSource, Object aLinkData) {
            myUpdateAll.setEnabled(false);
            myUpdateAllBundled.setEnabled(false);

            for (UIPluginGroup group : getInstalledGroups()) {
              if (group.excluded) {
                continue;
              }
              for (ListPluginComponent plugin : group.plugins) {
                plugin.updatePlugin();
              }
            }
          }
        };

        myUpdateAll.setVisible(false);
        myUpdateAllBundled.setVisible(false);
        myUpdateCounter.setVisible(false);
        myUpdateCounterBundled.setVisible(false);

        myUpdateAll.setListener(updateAllListener, null);
        downloaded.addRightAction(myUpdateAll);
        downloaded.addRightAction(myUpdateCounter);

        if (!downloaded.getModels().isEmpty()) {
          downloaded.sortByName();

          long enabledNonBundledCount = nonBundledPlugins.stream()
            .filter(descriptor -> !myPluginModelFacade.getModel().isDisabled(descriptor.getPluginId()))
            .count();
          downloaded.titleWithCount(Math.toIntExact(enabledNonBundledCount));
          if (downloaded.ui == null) {
            myInstalledPanel.addGroup(downloaded);
          }
          myPluginModelFacade.getModel().addEnabledGroup(downloaded);
        }

        String defaultCategory = IdeBundle.message("plugins.configurable.other.bundled");
        visibleBundledPlugins
          .stream()
          .collect(Collectors.groupingBy(descriptor -> StringUtil.defaultIfEmpty(descriptor.getDisplayCategory(), defaultCategory)))
          .entrySet()
          .stream()
          .map(entry -> new ComparablePluginsGroup(entry.getKey(), entry.getValue(), model.getVisiblePluginsRequiresUltimate()))
          .sorted((o1, o2) -> defaultCategory.equals(o1.title) ? 1 :
                              defaultCategory.equals(o2.title) ? -1 :
                              o1.compareTo(o2))
          .forEachOrdered(group -> {
            group.getPreloadedModel().setErrors(model.getErrors());
            group.getPreloadedModel().setPluginInstallationStates(model.getInstallationStates());
            myInstalledPanel.addGroup(group);
            myPluginModelFacade.getModel().addEnabledGroup(group);
          });

        myUpdateAllBundled.setListener(updateAllListener, null);
        myBundledUpdateGroup.addRightAction(myUpdateAllBundled);
        myBundledUpdateGroup.addRightAction(myUpdateCounterBundled);

        myPluginUpdatesService.calculateUpdates(updates -> {
          if (ContainerUtil.isEmpty(updates)) {
            clearUpdates(myInstalledPanel);
            clearUpdates(myInstalledSearchPanel.getPanel());
          }
          else {
            applyUpdates(myInstalledPanel, updates);
            applyUpdates(myInstalledSearchPanel.getPanel(), updates);
          }
          applyBundledUpdates(updates);
          selectionListener.accept(myInstalledPanel);
          selectionListener.accept(myInstalledSearchPanel.getPanel());
        });
      }
      finally {
        myInstalledPanel.hideLoadingIcon();
      }
      return null;
    });

    return createScrollPane(myInstalledPanel, true);
  }

  @Override
  protected void createSearchTextField(int flyDelay) {
    super.createSearchTextField(flyDelay);

    JBTextField textField = searchTextField.getTextEditor();
    ExtendableTextComponent.Extension searchFieldExtension = ExtendableTextComponent.Extension.create(
      AllIcons.General.Filter, AllIcons.General.Filter,
      IdeBundle.message("plugins.configurable.search.options"),
      true,
      () -> showRightBottomPopup(textField, IdeBundle.message("plugins.configurable.show"), myInstalledSearchGroup)
    );
    textField.putClientProperty("search.extension", searchFieldExtension);
    textField.putClientProperty("JTextField.variant", null);
    textField.putClientProperty("JTextField.variant", "search");

    searchTextField.setHistoryPropertyName("InstalledPluginsSearchHistory");
  }

  @Override
  protected @NotNull SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
    SearchUpDownPopupController installedController = new SearchUpDownPopupController(searchTextField) {
      @Override
      protected @NotNull @NonNls List<String> getAttributes() {
        return Arrays
          .asList(
            "/downloaded",
            "/outdated",
            "/enabled",
            "/disabled",
            "/invalid",
            "/bundled",
            SearchWords.VENDOR.getValue(),
            SearchWords.TAG.getValue()
          );
      }

      @Override
      protected @Nullable SortedSet<String> getValues(@NotNull String attribute) {
        return SearchWords.VENDOR.getValue().equals(attribute) ?
               myPluginModelFacade.getModel().getVendors() :
               SearchWords.TAG.getValue().equals(attribute) ?
               myPluginModelFacade.getModel().getTags() :
               null;
      }

      @Override
      protected void showPopupForQuery() {
        showSearchPanel(searchTextField.getText());
      }
    };

    MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
    installedController.setSearchResultEventHandler(eventHandler);

    PluginsGroupComponent panel = new PluginsGroupComponentWithProgress(eventHandler) {
      @Override
      protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                 @NotNull PluginsGroup group,
                                                                 @NotNull ListPluginModel listPluginModel) {
        return new ListPluginComponent(myPluginModelFacade, model, group, listPluginModel, searchListener, myCoroutineScope, false);
      }
    };

    panel.setSelectionListener(selectionListener);
    registerCopyProvider(panel);

    myInstalledSearchPanel = new SearchResultPanel(installedController, panel, false, 0, 0) {
      @Override
      protected void setEmptyText(@NotNull String query) {
        myPanel.getEmptyText().setText(IdeBundle.message("plugins.configurable.nothing.found"));
        if (query.contains("/downloaded") || query.contains("/outdated") ||
            query.contains("/enabled") || query.contains("/disabled") ||
            query.contains("/invalid") || query.contains("/bundled")) {
          return;
        }
        if (mySearchInMarketplaceTabHandler != null) {
          myPanel.getEmptyText().appendSecondaryText(IdeBundle.message("plugins.configurable.search.in.marketplace"),
                                                     SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                     e -> mySearchInMarketplaceTabHandler.accept(query));
        }
      }

      @Override
      protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result, AtomicBoolean runQuery) {
        int searchIndex = PluginManagerUsageCollector.updateAndGetSearchIndex();
        myPluginModelFacade.getModel().setInvalidFixCallback(null);

        SearchQueryParser.Installed parser = new SearchQueryParser.Installed(query);

        if (myInstalledSearchSetState) {
          for (AnAction action : myInstalledSearchGroup.getChildren(ActionManager.getInstance())) {
            ((InstalledSearchOptionAction)action).setState(parser);
          }
        }

        List<PluginUiModel> descriptors = myPluginModelFacade.getModel().getInstalledDescriptors();

        if (!parser.vendors.isEmpty()) {
          for (Iterator<PluginUiModel> I = descriptors.iterator(); I.hasNext(); ) {
            if (!MyPluginModel.isVendor(I.next(), parser.vendors)) {
              I.remove();
            }
          }
        }
        if (!parser.tags.isEmpty()) {
          String sessionId = myPluginModelFacade.getModel().getSessionId();

          for (Iterator<PluginUiModel> I = descriptors.iterator(); I.hasNext(); ) {
            if (!ContainerUtil.intersects(PluginUiModelKt.calculateTags(I.next(), sessionId), parser.tags)) {
              I.remove();
            }
          }
        }
        for (Iterator<PluginUiModel> I = descriptors.iterator(); I.hasNext(); ) {
          PluginUiModel descriptor = I.next();
          if (parser.attributes) {
            if (parser.enabled &&
                (!myPluginModelFacade.isEnabled(descriptor) || !myPluginModelFacade.getErrors(descriptor).isEmpty())) {
              I.remove();
              continue;
            }
            if (parser.disabled &&
                (myPluginModelFacade.isEnabled(descriptor) || !myPluginModelFacade.getErrors(descriptor).isEmpty())) {
              I.remove();
              continue;
            }
            if (parser.bundled && !descriptor.isBundled()) {
              I.remove();
              continue;
            }
            if (parser.downloaded && descriptor.isBundled()) {
              I.remove();
              continue;
            }
            if (parser.invalid && myPluginModelFacade.getErrors(descriptor).isEmpty()) {
              I.remove();
              continue;
            }
            if (parser.needUpdate && !UiPluginManager.getInstance().isNeedUpdate(descriptor.getPluginId())) {
              I.remove();
              continue;
            }
          }
          if (parser.searchQuery != null && !containsQuery(descriptor, parser.searchQuery)) {
            I.remove();
          }
        }

        result.addModels(descriptors);
        Map<PluginId, CheckErrorsResult> errors = UiPluginManager.getInstance()
          .loadErrors(myPluginModelFacade.getModel().mySessionId.toString(),
                      ContainerUtil.map(descriptors, PluginUiModel::getPluginId));
        result.getPreloadedModel().setErrors(MyPluginModel.getErrors(errors));
        result.getPreloadedModel().setPluginInstallationStates(UiPluginManager.getInstance().getInstallationStatesSync());
        PluginManagerUsageCollector.performInstalledTabSearch(
          ProjectUtil.getActiveProject(), parser, result.getModels(), searchIndex, null);

        if (!result.getModels().isEmpty()) {
          if (parser.invalid) {
            myPluginModelFacade.getModel().setInvalidFixCallback(() -> {
              PluginsGroup group = myInstalledSearchPanel.getGroup();
              if (group.ui == null) {
                myPluginModelFacade.getModel().setInvalidFixCallback(null);
                return;
              }

              PluginsGroupComponent resultPanel = myInstalledSearchPanel.getPanel();

              for (PluginUiModel descriptor : new ArrayList<>(group.getModels())) {
                if (myPluginModelFacade.getErrors(descriptor).isEmpty()) {
                  resultPanel.removeFromGroup(group, descriptor);
                }
              }

              group.titleWithCount();
              myInstalledSearchPanel.fullRepaint();

              if (group.getModels().isEmpty()) {
                myPluginModelFacade.getModel().setInvalidFixCallback(null);
                myInstalledSearchPanel.removeGroup();
              }
            });
          }
          else if (parser.needUpdate) {
            result.rightAction = new PluginManagerConfigurablePanel.LinkLabelButton<>(IdeBundle.message("plugin.manager.update.all"), null, (__, ___) -> {
              result.rightAction.setEnabled(false);

              for (ListPluginComponent plugin : result.ui.plugins) {
                plugin.updatePlugin();
              }
            });
          }
          PluginModelAsyncOperationsExecutor.INSTANCE.loadUpdates(myCoroutineScope, updates -> {
            if (!ContainerUtil.isEmpty(updates)) {
              myPostFillGroupCallback = () -> {
                //noinspection unchecked
                applyUpdates(myPanel, (Collection<PluginUiModel>)updates);
                selectionListener.accept(myInstalledPanel);
                selectionListener.accept(myInstalledSearchPanel.getPanel());
              };
            }
            return null;
          });
        }
        updatePanel(runQuery);
      }
    };

    return myInstalledSearchPanel;
  }

  @Override
  protected void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
    selectionListener.accept(myInstalledPanel);
  }

  @Override
  public void hideSearchPanel() {
    super.hideSearchPanel();
    if (myInstalledSearchSetState) {
      for (AnAction action : myInstalledSearchGroup.getChildren(ActionManager.getInstance())) {
        ((InstalledSearchOptionAction)action).setState(null);
      }
    }
    myPluginModelFacade.getModel().setInvalidFixCallback(null);
  }

  @Override
  protected void onSearchReset() {
    PluginManagerUsageCollector.INSTANCE.searchReset();
  }

  private void handleSearchOptionSelection(InstalledSearchOptionAction updateAction) {
    List<String> queries = new ArrayList<>();
    new SearchQueryParser.Installed(searchTextField.getText()) {
      @Override
      protected void addToSearchQuery(@NotNull String query) {
        queries.add(query);
      }

      @Override
      protected void handleAttribute(@NotNull String name, @NotNull String value) {
        if (!updateAction.myIsSelected) {
          queries.add(name + (value.isEmpty() ? "" : SearchQueryParser.wrapAttribute(value)));
        }
      }
    };

    if (updateAction.myIsSelected) {
      for (AnAction action : myInstalledSearchGroup.getChildren(ActionManager.getInstance())) {
        if (action != updateAction) {
          ((InstalledSearchOptionAction)action).myIsSelected = false;
        }
      }

      queries.add(updateAction.getQuery());
    }
    else {
      queries.remove(updateAction.getQuery());
    }

    try {
      myInstalledSearchSetState = false;

      String query = StringUtil.join(queries, " ");
      searchTextField.setTextIgnoreEvents(query);
      if (query.isEmpty()) {
        hideSearchPanel();
      }
      else {
        showSearchPanel(query);
      }
    }
    finally {
      myInstalledSearchSetState = true;
    }
  }

  private void applyBundledUpdates(@Nullable Collection<? extends PluginUiModel> updates) {
    if (ContainerUtil.isEmpty(updates)) {
      if (myBundledUpdateGroup.ui != null) {
        getInstalledPanel().removeGroup(myBundledUpdateGroup);
        getInstalledPanel().doLayout();
      }
    }
    else if (myBundledUpdateGroup.ui == null) {
      for (PluginUiModel descriptor : updates) {
        for (UIPluginGroup group : getInstalledPanel().getGroups()) {
          ListPluginComponent component = group.findComponent(descriptor.getPluginId());
          if (component != null && component.getPluginModel().isBundled()) {
            myBundledUpdateGroup.addModel(component.getPluginModel());
            break;
          }
        }
      }
      if (!myBundledUpdateGroup.getModels().isEmpty()) {
        getInstalledPanel().addGroup(myBundledUpdateGroup, 0);
        myBundledUpdateGroup.ui.excluded = true;

        for (PluginUiModel descriptor : updates) {
          ListPluginComponent component = myBundledUpdateGroup.ui.findComponent(descriptor.getPluginId());
          if (component != null) {
            component.setUpdateDescriptor(descriptor);
          }
        }

        getInstalledPanel().doLayout();
      }
    }
    else {
      List<ListPluginComponent> toDelete = new ArrayList<>();

      for (ListPluginComponent plugin : myBundledUpdateGroup.ui.plugins) {
        boolean exist = false;
        for (PluginUiModel update : updates) {
          if (plugin.getPluginModel().getPluginId().equals(update.getPluginId())) {
            exist = true;
            break;
          }
        }
        if (!exist) {
          toDelete.add(plugin);
        }
      }

      for (ListPluginComponent component : toDelete) {
        getInstalledPanel().removeFromGroup(myBundledUpdateGroup, component.getPluginModel());
      }

      for (PluginUiModel update : updates) {
        ListPluginComponent exist = myBundledUpdateGroup.ui.findComponent(update.getPluginId());
        if (exist != null) {
          continue;
        }
        for (UIPluginGroup group : getInstalledPanel().getGroups()) {
          if (group == myBundledUpdateGroup.ui) {
            continue;
          }
          ListPluginComponent component = group.findComponent(update.getPluginId());
          if (component != null && component.getPluginModel().isBundled()) {
            getInstalledPanel().addToGroup(myBundledUpdateGroup, component.getPluginModel());
            break;
          }
        }
      }

      if (myBundledUpdateGroup.getModels().isEmpty()) {
        getInstalledPanel().removeGroup(myBundledUpdateGroup);
      }
      else {
        for (PluginUiModel descriptor : updates) {
          ListPluginComponent component = myBundledUpdateGroup.ui.findComponent(descriptor.getPluginId());
          if (component != null) {
            component.setUpdateDescriptor(descriptor);
          }
        }
      }

      getInstalledPanel().doLayout();
    }

    myUpdateAll.setVisible(myUpdateAll.isVisible() && myBundledUpdateGroup.ui == null);
    myUpdateCounter.setVisible(myUpdateCounter.isVisible() && myBundledUpdateGroup.ui == null);
  }

  void onPluginUpdatesRecalculation(Integer updatesCount, @Nls String tooltip) {
    int count = updatesCount == null ? 0 : updatesCount;
    String text = Integer.toString(count);
    boolean visible = count > 0;

    myUpdateAll.setEnabled(true);
    myUpdateAllBundled.setEnabled(true);
    myUpdateAll.setVisible(visible && myBundledUpdateGroup.ui == null);
    myUpdateAllBundled.setVisible(visible);

    myUpdateCounter.setText(text);
    myUpdateCounter.setToolTipText(tooltip);
    myUpdateCounterBundled.setText(text);
    myUpdateCounterBundled.setToolTipText(tooltip);
    myUpdateCounter.setVisible(visible && myBundledUpdateGroup.ui == null);
    myUpdateCounterBundled.setVisible(visible);
  }

  private final class InstalledSearchOptionAction extends ToggleAction implements DumbAware {
    private final InstalledSearchOption myOption;
    private boolean myIsSelected;

    private InstalledSearchOptionAction(@NotNull InstalledSearchOption option) {
      super(option.myPresentableNameSupplier);
      getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested);
      myOption = option;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myIsSelected;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myIsSelected = state;
      handleSearchOptionSelection(this);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    public void setState(@Nullable SearchQueryParser.Installed parser) {
      if (parser == null) {
        myIsSelected = false;
        return;
      }

      myIsSelected = switch (myOption) {
        case Enabled -> parser.enabled;
        case Disabled -> parser.disabled;
        case Downloaded -> parser.downloaded;
        case Bundled -> parser.bundled;
        case Invalid -> parser.invalid;
        case NeedUpdate -> parser.needUpdate;
      };
    }

    public @NotNull String getQuery() {
      return myOption == InstalledSearchOption.NeedUpdate ? "/outdated" : "/" + StringUtil.decapitalize(myOption.name());
    }
  }

  private final class ComparablePluginsGroup extends PluginsGroup
    implements Comparable<ComparablePluginsGroup> {

    private boolean myIsEnable = false;

    private ComparablePluginsGroup(@NotNull @NlsSafe String category,
                                   @NotNull List<PluginUiModel> descriptors,
                                   @NotNull Map<PluginId, Boolean> pluginsRequiresUltimate) {
      super(category, PluginsGroupType.INSTALLED);

      this.addModels(descriptors);
      sortByName();

      rightAction = new PluginManagerConfigurablePanel.LinkLabelButton<>("",
                                                                         null,
                                                                         (__, ___) -> setEnabledState());
      boolean hasPluginsAvailableForEnableDisable =
        ContainerUtil.exists(descriptors, it -> !pluginsRequiresUltimate.get(it.getPluginId()));
      rightAction.setVisible(hasPluginsAvailableForEnableDisable);
      titleWithEnabled(myPluginModelFacade);
    }

    @Override
    public int compareTo(@NotNull ComparablePluginsGroup other) {
      return StringUtil.compare(title, other.title, true);
    }

    @Override
    public void titleWithCount(int enabled) {
      myIsEnable = enabled == 0;
      String key = myIsEnable ? "plugins.configurable.enable.all" : "plugins.configurable.disable.all";
      rightAction.setText(IdeBundle.message(key));
    }

    private void setEnabledState() {
      setState(myPluginModelFacade, getModels(), myIsEnable);
    }
  }

  private enum InstalledSearchOption {
    Downloaded(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Downloaded")),
    NeedUpdate(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.NeedUpdate")),
    Enabled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Enabled")),
    Disabled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Disabled")),
    Invalid(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Invalid")),
    Bundled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Bundled"));

    private final Supplier<@Nls String> myPresentableNameSupplier;

    InstalledSearchOption(Supplier<@Nls String> name) { myPresentableNameSupplier = name; }
  }
}
