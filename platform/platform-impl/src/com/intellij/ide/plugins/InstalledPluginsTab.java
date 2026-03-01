// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.newui.ListPluginComponent;
import com.intellij.ide.plugins.newui.MultiSelectionEventHandler;
import com.intellij.ide.plugins.newui.MyPluginModel;
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent;
import com.intellij.ide.plugins.newui.PluginModelFacade;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.plugins.newui.PluginsGroup;
import com.intellij.ide.plugins.newui.PluginsGroupComponent;
import com.intellij.ide.plugins.newui.PluginsGroupComponentWithProgress;
import com.intellij.ide.plugins.newui.PluginsTab;
import com.intellij.ide.plugins.newui.SearchQueryParser;
import com.intellij.ide.plugins.newui.SearchResultPanel;
import com.intellij.ide.plugins.newui.SearchUpDownPopupController;
import com.intellij.ide.plugins.newui.SearchWords;
import com.intellij.ide.plugins.newui.UIPluginGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeepPopupOnPerform;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.containers.ContainerUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.applyUpdates;
import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.clearUpdates;
import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.createScrollPane;
import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.registerCopyProvider;
import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.setState;
import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.showRightBottomPopup;


@ApiStatus.Internal
class InstalledPluginsTab extends PluginsTab {
  private static final ExtensionPointName<PluginCategoryPromotionProvider> PROMOTION_EP_NAME =
    ExtensionPointName.create("com.intellij.pluginCategoryPromotionProvider");

  private final @NotNull PluginModelFacade myPluginModelFacade;
  private final @NotNull CoroutineScope myCoroutineScope;
  private final @Nullable Consumer<String> mySearchInMarketplaceTabHandler;

  private @Nullable PluginsGroupComponentWithProgress myInstalledPanel = null;
  private @Nullable InstalledPluginsTabSearchResultPanel myInstalledSearchPanel = null;
  private final DefaultActionGroup myInstalledSearchGroup;

  private final PluginsGroup myBundledUpdateGroup =
    new PluginsGroup(IdeBundle.message("plugins.configurable.bundled.updates"), PluginsGroupType.BUNDLED_UPDATE);

  private final LinkLabel<Object> myUpdateAll =
    new PluginManagerConfigurablePanel.LinkLabelButton<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final LinkLabel<Object> myUpdateAllBundled =
    new PluginManagerConfigurablePanel.LinkLabelButton<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final JLabel myUpdateCounter = new CountComponent();
  private final JLabel myUpdateCounterBundled = new CountComponent();

  InstalledPluginsTab(@NotNull PluginModelFacade facade,
                      @NotNull CoroutineScope scope,
                      @Nullable Consumer<String> searchInMarketplaceHandler) {
    super();
    myPluginModelFacade = facade;
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

    PluginsGroup userInstalled = new PluginsGroup(IdeBundle.message("plugins.configurable.userInstalled"), PluginsGroupType.INSTALLED);
    PluginsGroup installing = new PluginsGroup(IdeBundle.message("plugins.configurable.installing"), PluginsGroupType.INSTALLING);

    myUpdateAll.setVisible(false);
    myUpdateAllBundled.setVisible(false);
    myUpdateCounter.setVisible(false);
    myUpdateCounterBundled.setVisible(false);

    LinkListener<Object> updateAllListener = new LinkListener<>() {
      @Override
      public void linkSelected(LinkLabel<Object> aSource, Object aLinkData) {
        myUpdateAll.setEnabled(false);
        myUpdateAllBundled.setEnabled(false);

        for (UIPluginGroup group : getInstalledGroups()) {
          if (group.isBundledUpdatesGroup) {
            continue;
          }
          for (ListPluginComponent plugin : group.plugins) {
            plugin.updatePlugin();
          }
        }
      }
    };

    myUpdateAll.setListener(updateAllListener, null);
    userInstalled.addSecondaryAction(myUpdateAll);
    userInstalled.addSecondaryAction(myUpdateCounter);

    myUpdateAllBundled.setListener(updateAllListener, null);
    myBundledUpdateGroup.addSecondaryAction(myUpdateAllBundled);
    myBundledUpdateGroup.addSecondaryAction(myUpdateCounterBundled);

    PluginManagerPanelFactory.INSTANCE.createInstalledPanel(myCoroutineScope, myPluginModelFacade.getModel(), model -> {
      try {
        myPluginModelFacade.getModel().setDownloadedGroup(myInstalledPanel, userInstalled, installing);
        installing.getPreloadedModel().setErrors(model.getErrors());
        installing.getPreloadedModel().setPluginInstallationStates(model.getInstallationStates());
        installing.addModels(MyPluginModel.getInstallingPlugins());
        if (!installing.getModels().isEmpty()) {
          installing.sortByName();
          installing.titleWithCount();
          myInstalledPanel.addGroup(installing);
        }

        userInstalled.getPreloadedModel().setErrors(model.getErrors());
        userInstalled.getPreloadedModel().setPluginInstallationStates(model.getInstallationStates());
        userInstalled.addModels(model.getInstalledPlugins());

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
        userInstalled.addModels(nonBundledPlugins);

        if (!userInstalled.getModels().isEmpty()) {
          userInstalled.sortByName();

          long enabledNonBundledCount = nonBundledPlugins.stream()
            .filter(descriptor -> !myPluginModelFacade.getModel().isDisabled(descriptor.getPluginId()))
            .count();
          userInstalled.titleWithCount(Math.toIntExact(enabledNonBundledCount));
          if (userInstalled.ui == null) {
            myInstalledPanel.addGroup(userInstalled);
          }
          myPluginModelFacade.getModel().addEnabledGroup(userInstalled);
        }

        String defaultCategory = IdeBundle.message("plugins.configurable.other.bundled");

        Map<String, Supplier<JComponent>> promotionPanelSuppliers = new HashMap<>();
        if (Registry.is("ide.plugins.category.promotion.enabled")) {
          for (PluginCategoryPromotionProvider provider : PROMOTION_EP_NAME.getExtensionList()) {
            promotionPanelSuppliers.put(provider.getCategoryName(), provider::createPromotionPanel);
          }
        }

        visibleBundledPlugins
          .stream()
          .collect(Collectors.groupingBy(descriptor -> StringUtil.defaultIfEmpty(descriptor.getDisplayCategory(), defaultCategory)))
          .entrySet()
          .stream()
          .map(entry -> {
            ComparablePluginsGroup group =
              new ComparablePluginsGroup(entry.getKey(), entry.getValue(), model.getVisiblePluginsRequiresUltimate());
            if (Registry.is("ide.plugins.category.promotion.enabled")) {
              Supplier<JComponent> promotionPanelSupplier = promotionPanelSuppliers.get(entry.getKey());
              if (promotionPanelSupplier != null) {
                JComponent promotionPanel = promotionPanelSupplier.get();
                if (promotionPanel != null) {
                  group.setPromotionPanel(promotionPanel);
                }
              }
            }
            return group;
          })
          .sorted((o1, o2) -> {
            if (Registry.is("ide.plugins.category.promotion.enabled")) {
              boolean isPriorityO1 = false;
              boolean isPriorityO2 = false;
              for (PluginCategoryPromotionProvider provider : PROMOTION_EP_NAME.getExtensionList()) {
                if (provider.isPriorityCategory()) {
                  String priorityCategory = provider.getCategoryName();
                  if (priorityCategory.equals(o1.title)) isPriorityO1 = true;
                  if (priorityCategory.equals(o2.title)) isPriorityO2 = true;
                }
              }
              if (isPriorityO1 != isPriorityO2) {
                return isPriorityO1 ? -1 : 1;
              }
            }
            if (defaultCategory.equals(o1.title)) return 1;
            if (defaultCategory.equals(o2.title)) return -1;
            return o1.compareTo(o2);
          })
          .forEachOrdered(group -> {
            group.getPreloadedModel().setErrors(model.getErrors());
            group.getPreloadedModel().setPluginInstallationStates(model.getInstallationStates());
            myInstalledPanel.addGroup(group);
            myPluginModelFacade.getModel().addEnabledGroup(group);
          });

        PluginUpdateListener.calculateUpdates(myCoroutineScope, updates -> {
          List<PluginUiModel> updateModels = updates == null ? null : new ArrayList<>(updates);
          if (ContainerUtil.isEmpty(updateModels)) {
            clearUpdates(myInstalledPanel);
            clearUpdates(myInstalledSearchPanel.getPanel());
          }
          else {
            applyUpdates(myInstalledPanel, updateModels);
            applyUpdates(myInstalledSearchPanel.getPanel(), updateModels);
          }
          applyBundledUpdates(updateModels);
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
            "/userInstalled", // don't suggest /downloaded in popup
            "/outdated",
            "/enabled",
            "/disabled",
            "/invalid",
            "/bundled",
            "/updatedBundled",
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

    PluginsGroupComponentWithProgress panel = new PluginsGroupComponentWithProgress(eventHandler) {
      @Override
      protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                 @NotNull PluginsGroup group,
                                                                 @NotNull ListPluginModel listPluginModel) {
        return new ListPluginComponent(myPluginModelFacade, model, group, listPluginModel, searchListener, myCoroutineScope, false);
      }
    };

    panel.setSelectionListener(selectionListener);
    registerCopyProvider(panel);

    myInstalledSearchPanel = new InstalledPluginsTabSearchResultPanel(
      myCoroutineScope,
      installedController,
      panel,
      myInstalledSearchGroup,
      () -> myInstalledPanel,
      selectionListener,
      mySearchInMarketplaceTabHandler,
      myPluginModelFacade
    );
    return myInstalledSearchPanel;
  }

  @Override
  protected void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
    selectionListener.accept(myInstalledPanel);
  }

  @Override
  public void hideSearchPanel() {
    super.hideSearchPanel();
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
      queries.add(updateAction.getQuery());
    }
    else {
      queries.remove(updateAction.getQuery());
    }

    String query = StringUtil.join(queries, " ");
    searchTextField.setTextIgnoreEvents(query);
    if (query.isEmpty()) {
      hideSearchPanel();
    }
    else {
      showSearchPanel(query);
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
      if (myBundledUpdateGroup.secondaryActions == null || myBundledUpdateGroup.secondaryActions.isEmpty()) {
        // removeGroup clears actions too
        myBundledUpdateGroup.addSecondaryAction(myUpdateAllBundled);
        myBundledUpdateGroup.addSecondaryAction(myUpdateCounterBundled);
      }
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
        myBundledUpdateGroup.ui.isBundledUpdatesGroup = true;

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

  @ApiStatus.Internal
  final class InstalledSearchOptionAction extends ToggleAction implements DumbAware {
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
        case UserInstalled -> parser.userInstalled;
        case Bundled -> parser.bundled;
        case UpdatedBundled -> parser.updatedBundled;
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

      mainAction = new PluginManagerConfigurablePanel.LinkLabelButton<>("",
                                                                        null,
                                                                        (__, ___) -> setEnabledState());
      boolean hasPluginsAvailableForEnableDisable =
        ContainerUtil.exists(descriptors, it -> !pluginsRequiresUltimate.get(it.getPluginId()));
      mainAction.setVisible(hasPluginsAvailableForEnableDisable);
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
      mainAction.setText(IdeBundle.message(key));
    }

    private void setEnabledState() {
      setState(myPluginModelFacade, getModels(), myIsEnable);
    }
  }

  private enum InstalledSearchOption {
    UserInstalled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.UserInstalled")),
    NeedUpdate(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.NeedUpdate")),
    Enabled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Enabled")),
    Disabled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Disabled")),
    Invalid(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Invalid")),
    Bundled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Bundled")),
    UpdatedBundled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.UpdatedBundled"));

    private final Supplier<@Nls String> myPresentableNameSupplier;

    InstalledSearchOption(Supplier<@Nls String> name) { myPresentableNameSupplier = name; }
  }
}
