// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.newui.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Url;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableNewLayout
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider,
             PluginManagerConfigurableInfo {

  private static final int MARKETPLACE_TAB = 0;
  private static final int INSTALLED_TAB = 1;

  private TabbedPaneHeaderComponent myTabHeaderComponent;
  private MultiPanel myCardPanel;

  private PluginsTab myMarketplaceTab;
  private PluginsTab myInstalledTab;

  private PluginsGroupComponentWithProgress myMarketplacePanel;
  private PluginsGroupComponent myInstalledPanel;

  private Runnable myMarketplaceRunnable;

  private SearchResultPanel myMarketplaceSearchPanel;
  private SearchResultPanel myInstalledSearchPanel;

  private LinkListener<IdeaPluginDescriptor> myNameListener;
  private LinkListener<String> mySearchListener;

  private final LinkLabel<Object> myUpdateAll = new LinkLabel<>("Update All", null);
  private final JLabel myUpdateCounter = new CountComponent();
  private final CountIcon myCountIcon = new CountIcon();

  private final MyPluginModel myPluginModel = new MyPluginModel() {
    @Override
    public List<IdeaPluginDescriptor> getAllRepoPlugins() {
      return getPluginRepositories();
    }
  };

  private Runnable myShutdownCallback;

  private PluginUpdatesService myPluginUpdatesService;

  private List<IdeaPluginDescriptor> myAllRepositoriesList;
  private Map<String, IdeaPluginDescriptor> myAllRepositoriesMap;
  private Map<String, List<IdeaPluginDescriptor>> myCustomRepositoriesMap;
  private final Object myRepositoriesLock = new Object();
  private List<String> myTagsSorted;
  private List<String> myVendorsSorted;

  private DefaultActionGroup myMarketplaceSortByGroup;
  private Consumer<MarketplaceSortByAction> myMarketplaceSortByCallback;
  private LinkComponent myMarketplaceSortByAction;

  private DefaultActionGroup myInstalledSearchGroup;
  private Consumer<InstalledSearchOptionAction> myInstalledSearchCallback;
  private boolean myInstalledSearchSetState = true;

  @NotNull
  @Override
  public String getId() {
    return PluginManagerConfigurableNew.ID;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  @NotNull
  @Override
  public Component getCenterComponent(@NotNull TopComponentController controller) {
    myPluginModel.setTopController(controller);
    return myTabHeaderComponent;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myTabHeaderComponent = new TabbedPaneHeaderComponent(createGearActions(), index -> {
      myCardPanel.select(index, true);
      storeSelectionTab(index);

      String query = (index == MARKETPLACE_TAB ? myInstalledTab : myMarketplaceTab).getSearchQuery();
      (index == MARKETPLACE_TAB ? myMarketplaceTab : myInstalledTab).setSearchQuery(query);
    });

    myUpdateAll.setVisible(false);
    myUpdateCounter.setVisible(false);

    myTabHeaderComponent.addTab("Marketplace", null);
    myTabHeaderComponent.addTab("Installed", myCountIcon);

    myPluginUpdatesService = PluginUpdatesService.connectConfigurable(countValue -> {
      int count = countValue == null ? 0 : countValue;
      String text = String.valueOf(count);
      boolean visible = count > 0;

      myUpdateAll.setEnabled(true);
      myUpdateAll.setVisible(visible);

      myUpdateCounter.setText(text);
      myUpdateCounter.setVisible(visible);

      myCountIcon.setText(text);
      myTabHeaderComponent.update();
    });
    myPluginModel.setPluginUpdatesService(myPluginUpdatesService);

    myNameListener = (aSource, aLinkData) -> {
      // TODO: unused
    };
    mySearchListener = (aSource, aLinkData) -> {
      // TODO: unused
    };

    createMarketplaceTab();
    createInstalledTab();

    myCardPanel = new MultiPanel() {
      @Override
      protected JComponent create(Integer key) {
        if (key == MARKETPLACE_TAB) {
          return myMarketplaceTab.createPanel();
        }
        if (key == INSTALLED_TAB) {
          return myInstalledTab.createPanel();
        }
        return super.create(key);
      }
    };
    myCardPanel.setMinimumSize(new JBDimension(580, 380));

    myTabHeaderComponent.setListener();

    int selectionTab = getStoredSelectionTab();
    myTabHeaderComponent.setSelection(selectionTab);
    myCardPanel.select(selectionTab, true);

    return myCardPanel;
  }

  @NotNull
  private DefaultActionGroup createGearActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new DumbAwareAction("Manage Plugin Repositories...") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (ShowSettingsUtil.getInstance().editConfigurable(myCardPanel, new PluginHostsConfigurable())) {
          resetPanels();
        }
      }
    });
    actions.add(new DumbAwareAction(IdeBundle.message("button.http.proxy.settings")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (HttpConfigurable.editConfigurable(myCardPanel)) {
          resetPanels();
        }
      }
    });
    actions.addSeparator();
    actions.add(new DumbAwareAction("Install Plugin from Disk...") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        InstalledPluginsManagerMain.chooseAndInstall(myPluginModel, myCardPanel, pair -> {
          myPluginModel.appendOrUpdateDescriptor(pair.second);

          boolean select = myInstalledPanel == null;

          if (myTabHeaderComponent.getSelectionTab() != INSTALLED_TAB) {
            myTabHeaderComponent.setSelectionWithEvents(INSTALLED_TAB);
          }

          myInstalledTab.clearSearchPanel("");

          if (select) {
            for (UIPluginGroup group : myInstalledPanel.getGroups()) {
              CellPluginComponent component = group.findComponent(pair.second);
              if (component != null) {
                myInstalledPanel.setSelection(component);
                break;
              }
            }
          }
        });
      }
    });
    actions.addSeparator();
    actions.add(new ChangePluginStateAction(false));
    actions.add(new ChangePluginStateAction(true));

    return actions;
  }

  private static void showRightBottomPopup(@NotNull Component component, @NotNull String title, @NotNull ActionGroup group) {
    DefaultActionGroup actions = new GroupByActionGroup();
    actions.addSeparator("  " + title);
    actions.addAll(group);

    DataContext context = DataManager.getInstance().getDataContext(component);

    JBPopup popup = new PopupFactoryImpl.ActionGroupPopup(null, actions, context, false, false, false, true, null, -1, null, null) {
      @Override
      protected ListCellRenderer getListElementRenderer() {
        return new PopupListElementRenderer(this) {
          @Override
          protected SeparatorWithText createSeparator() {
            return new SeparatorWithText() {
              {
                setTextForeground(JBColor.BLACK);
                setFont(UIUtil.getLabelFont());
                setCaptionCentered(false);
              }

              @Override
              protected void paintLine(Graphics g, int x, int y, int width) {
              }
            };
          }

          @Override
          protected Border getDefaultItemComponentBorder() {
            return new EmptyBorder(JBInsets.create(UIUtil.getListCellVPadding(), 15));
          }
        };
      }
    };
    popup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        Point location = component.getLocationOnScreen();
        Dimension size = popup.getSize();
        popup.setLocation(new Point(location.x + component.getWidth() - size.width, location.y + component.getHeight()));
      }
    });
    popup.show(component);
  }

  private void resetPanels() {
    synchronized (myRepositoriesLock) {
      myAllRepositoriesList = null;
      myAllRepositoriesMap = null;
      myCustomRepositoriesMap = null;
    }

    myTagsSorted = null;
    myVendorsSorted = null;

    myPluginUpdatesService.recalculateUpdates();

    if (myMarketplacePanel == null) {
      return;
    }

    int selectionTab = myTabHeaderComponent.getSelectionTab();
    if (selectionTab == MARKETPLACE_TAB) {
      myMarketplaceRunnable.run();
    }
    else {
      myMarketplacePanel.setVisibleRunnable(myMarketplaceRunnable);
    }
  }

  private static int getStoredSelectionTab() {
    int value = PropertiesComponent.getInstance().getInt(PluginManagerConfigurableNew.SELECTION_TAB_KEY, MARKETPLACE_TAB);
    return value < MARKETPLACE_TAB || value > INSTALLED_TAB ? MARKETPLACE_TAB : value;
  }

  private static void storeSelectionTab(int value) {
    PropertiesComponent.getInstance().setValue(PluginManagerConfigurableNew.SELECTION_TAB_KEY, value, MARKETPLACE_TAB);
  }

  private void createMarketplaceTab() {
    myMarketplaceTab = new PluginsTab() {
      @Override
      protected void createSearchTextField(int flyDelay) {
        super.createSearchTextField(250);
        mySearchTextField.setHistoryPropertyName("MarketplacePluginsSearchHistory");
      }

      @NotNull
      @Override
      protected PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener) {
        PluginDetailsPageComponent detailPanel = new PluginDetailsPageComponent(myPluginModel, searchListener, true);
        myPluginModel.addDetailPanel(detailPanel);
        return detailPanel;
      }

      @NotNull
      @Override
      protected JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        myMarketplacePanel = new PluginsGroupComponentWithProgress(new PluginListLayout(), eventHandler, myNameListener,
                                                                   PluginManagerConfigurableNewLayout.this.mySearchListener,
                                                                   d -> new NewListPluginComponent(myPluginModel, d, true));

        myMarketplacePanel.setSelectionListener(selectionListener);
        PluginManagerConfigurableNew.registerCopyProvider(myMarketplacePanel);

        //noinspection ConstantConditions
        ((SearchUpDownPopupController)myMarketplaceSearchPanel.controller).setEventHandler(eventHandler);

        Runnable runnable = () -> {
          List<PluginsGroup> groups = new ArrayList<>();

          try {
            Pair<Map<String, IdeaPluginDescriptor>, Map<String, List<IdeaPluginDescriptor>>> pair = loadPluginRepositories();
            Map<String, IdeaPluginDescriptor> allRepositoriesMap = pair.first;
            Map<String, List<IdeaPluginDescriptor>> customRepositoriesMap = pair.second;

            try {
              addGroup(groups, allRepositoriesMap, "Featured", "is_featured_search=true", "/sortBy:featured");
              addGroup(groups, allRepositoriesMap, "New and Updated", "orderBy=update+date", "/sortBy:updated");
              addGroup(groups, allRepositoriesMap, "Top Downloads", "orderBy=downloads", "/sortBy:downloads");
              addGroup(groups, allRepositoriesMap, "Top Rated", "orderBy=rating", "/sortBy:rating");
            }
            catch (IOException e) {
              PluginManagerMain.LOG
                .info("Main plugin repository is not available ('" + e.getMessage() + "'). Please check your network settings.");
            }

            for (String host : UpdateSettings.getInstance().getPluginHosts()) {
              List<IdeaPluginDescriptor> allDescriptors = customRepositoriesMap.get(host);
              if (allDescriptors != null) {
                addGroup(groups, "Repository: " + host, "repository:\"" + host + "\"", descriptors -> {
                  int allSize = allDescriptors.size();
                  descriptors.addAll(ContainerUtil.getFirstItems(allDescriptors, PluginManagerConfigurableNew.ITEMS_PER_GROUP));
                  PluginsGroup.sortByName(descriptors);
                  return allSize > PluginManagerConfigurableNew.ITEMS_PER_GROUP;
                });
              }
            }
          }
          catch (IOException e) {
            PluginManagerMain.LOG.info(e);
          }
          finally {
            ApplicationManager.getApplication().invokeLater(() -> {
              myMarketplacePanel.stopLoading();
              PluginLogo.startBatchMode();

              for (PluginsGroup group : groups) {
                myMarketplacePanel.addGroup(group);
              }

              PluginLogo.endBatchMode();
              myMarketplacePanel.doLayout();
              myMarketplacePanel.initialSelection();
            }, ModalityState.any());
          }
        };

        myMarketplaceRunnable = () -> {
          myMarketplacePanel.clear();
          myMarketplacePanel.startLoading();
          ApplicationManager.getApplication().executeOnPooledThread(runnable);
        };

        myMarketplacePanel.getEmptyText().setText("Marketplace plugins are not loaded.")
          .appendSecondaryText("Check the internet connection and ", StatusText.DEFAULT_ATTRIBUTES, null)
          .appendSecondaryText("refresh", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> myMarketplaceRunnable.run());

        ApplicationManager.getApplication().executeOnPooledThread(runnable);
        return createScrollPane(myMarketplacePanel, false);
      }

      @Override
      protected void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        selectionListener.accept(myMarketplacePanel);
      }

      @NotNull
      @Override
      protected SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        SearchUpDownPopupController marketplaceController = new SearchUpDownPopupController(mySearchTextField) {
          @NotNull
          @Override
          protected List<String> getAttributes() {
            List<String> attributes = new ArrayList<>();
            attributes.add("/tag:");
            attributes.add("/sortBy:");
            attributes.add("/vendor:");
            if (!UpdateSettings.getInstance().getPluginHosts().isEmpty()) {
              attributes.add("/repository:");
            }
            return attributes;
          }

          @Nullable
          @Override
          protected List<String> getValues(@NotNull String attribute) {
            switch (attribute) {
              case "/tag:":
                if (ContainerUtil.isEmpty(myTagsSorted)) {
                  Set<String> allTags = new HashSet<>();
                  for (IdeaPluginDescriptor descriptor : getPluginRepositories()) {
                    if (descriptor instanceof PluginNode) {
                      List<String> tags = ((PluginNode)descriptor).getTags();
                      if (!ContainerUtil.isEmpty(tags)) {
                        allTags.addAll(tags);
                      }
                    }
                  }
                  myTagsSorted = ContainerUtil.sorted(allTags, String::compareToIgnoreCase);
                }
                return myTagsSorted;
              case "/sortBy:":
                return Arrays.asList("downloads", "name", "rating", "updated");
              case "/vendor:":
                if (ContainerUtil.isEmpty(myVendorsSorted)) {
                  myVendorsSorted = MyPluginModel.getVendors(getPluginRepositories());
                }
                return myVendorsSorted;
              case "/repository:":
                return UpdateSettings.getInstance().getPluginHosts();
            }
            return null;
          }

          @Override
          protected void handleAppendToQuery() {
            showPopupForQuery();
          }

          @Override
          protected void handleAppendAttributeValue() {
            showPopupForQuery();
          }

          @Override
          protected void showPopupForQuery() {
            hidePopup();
            showSearchPanel(mySearchTextField.getText());
          }

          @Override
          protected void handleEnter() {
            if (!mySearchTextField.getText().isEmpty()) {
              handleTrigger("marketplace.suggest.popup.enter");
            }
          }

          @Override
          protected void handlePopupListFirstSelection() {
            handleTrigger("marketplace.suggest.popup.select");
          }

          private void handleTrigger(@NonNls String key) {
            if (myPopup != null && myPopup.type == SearchPopup.Type.SearchQuery) {
              FeatureUsageTracker.getInstance().triggerFeatureUsed(key);
            }
          }
        };

        myMarketplaceSortByGroup = new DefaultActionGroup();

        for (SortBySearchOption option : SortBySearchOption.values()) {
          myMarketplaceSortByGroup.addAction(new MarketplaceSortByAction(option));
        }

        myMarketplaceSortByAction = new LinkComponent() {
          @Override
          protected boolean isInClickableArea(Point pt) {
            return true;
          }
        };
        myMarketplaceSortByAction.setIcon(new Icon() {
          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            getIcon().paintIcon(c, g, x, y + 1);
          }

          @Override
          public int getIconWidth() {
            return getIcon().getIconWidth();
          }

          @Override
          public int getIconHeight() {
            return getIcon().getIconHeight();
          }

          @NotNull
          private Icon getIcon() {
            return AllIcons.General.ButtonDropTriangle;
          }
        }); // TODO: icon
        myMarketplaceSortByAction.setPaintUnderline(false);
        myMarketplaceSortByAction.setIconTextGap(JBUIScale.scale(4));
        myMarketplaceSortByAction.setHorizontalTextPosition(SwingConstants.LEFT);
        myMarketplaceSortByAction.setForeground(PluginsGroupComponent.SECTION_HEADER_FOREGROUND);

        //noinspection unchecked
        myMarketplaceSortByAction.setListener(
          (component, __) -> showRightBottomPopup(component.getParent().getParent(), "Sort By", myMarketplaceSortByGroup), null);

        myMarketplaceSortByCallback = updateAction -> {
          MarketplaceSortByAction removeAction = null;
          MarketplaceSortByAction addAction = null;

          if (updateAction.myState) {
            for (AnAction action : myMarketplaceSortByGroup.getChildren(null)) {
              MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
              if (sortByAction != updateAction && sortByAction.myState) {
                sortByAction.myState = false;
                removeAction = sortByAction;
                break;
              }
            }
            addAction = updateAction;
          }
          else {
            if (updateAction.myOption == SortBySearchOption.Relevance) {
              updateAction.myState = true;
              return;
            }

            for (AnAction action : myMarketplaceSortByGroup.getChildren(null)) {
              MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
              if (sortByAction.myOption == SortBySearchOption.Relevance) {
                sortByAction.myState = true;
                break;
              }
            }

            removeAction = updateAction;
          }

          List<String> queries = new ArrayList<>();
          new SearchQueryParser.Marketplace(mySearchTextField.getText()) {
            @Override
            protected void addToSearchQuery(@NotNull String query) {
              super.addToSearchQuery(query);
              queries.add(query);
            }

            @Override
            protected void handleAttribute(@NotNull String name, @NotNull String value, boolean invert) {
              super.handleAttribute(name, value, invert);
              queries.add(name + ":" + SearchQueryParser.wrapAttribute(value));
            }
          };
          if (removeAction != null) {
            String query = removeAction.getQuery();
            if (query != null) {
              queries.remove(query);
            }
          }
          if (addAction != null) {
            String query = addAction.getQuery();
            if (query != null) {
              queries.add(query);
            }
          }

          String query = StringUtil.join(queries, " ");
          mySearchTextField.setTextIgnoreEvents(query);
          if (query.isEmpty()) {
            myMarketplaceTab.hideSearchPanel();
          }
          else {
            myMarketplaceTab.showSearchPanel(query);
          }
        };

        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        marketplaceController.setSearchResultEventHandler(eventHandler);

        PluginsGroupComponentWithProgress panel =
          new PluginsGroupComponentWithProgress(new PluginListLayout(), eventHandler, myNameListener,
                                                PluginManagerConfigurableNewLayout.this.mySearchListener,
                                                descriptor -> new NewListPluginComponent(myPluginModel, descriptor, true));

        panel.setSelectionListener(selectionListener);
        PluginManagerConfigurableNew.registerCopyProvider(panel);

        myMarketplaceSearchPanel =
          new SearchResultPanel(marketplaceController, panel, 0, 0) {
            @Override
            protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
              try {
                Pair<Map<String, IdeaPluginDescriptor>, Map<String, List<IdeaPluginDescriptor>>> p = loadPluginRepositories();
                Map<String, IdeaPluginDescriptor> allRepositoriesMap = p.first;
                Map<String, List<IdeaPluginDescriptor>> customRepositoriesMap = p.second;

                SearchQueryParser.Marketplace parser = new SearchQueryParser.Marketplace(query);

                // TODO: parser.vendors on server
                if (!parser.vendors.isEmpty()) {
                  for (IdeaPluginDescriptor descriptor : getPluginRepositories()) {
                    if (MyPluginModel.isVendor(descriptor, parser.vendors)) {
                      result.descriptors.add(descriptor);
                    }
                  }
                  ContainerUtil.removeDuplicates(result.descriptors);
                  result.sortByName();
                  return;
                }

                if (!parser.repositories.isEmpty()) {
                  for (String repository : parser.repositories) {
                    List<IdeaPluginDescriptor> descriptors = customRepositoriesMap.get(repository);
                    if (descriptors == null) {
                      continue;
                    }
                    if (parser.searchQuery == null) {
                      result.descriptors.addAll(descriptors);
                    }
                    else {
                      for (IdeaPluginDescriptor descriptor : descriptors) {
                        if (StringUtil.containsIgnoreCase(descriptor.getName(), parser.searchQuery)) {
                          result.descriptors.add(descriptor);
                        }
                      }
                    }
                  }
                  ContainerUtil.removeDuplicates(result.descriptors);
                  result.sortByName();
                  return;
                }

                Url url = PluginManagerConfigurableNew.createSearchUrl(parser.getUrlQuery(), 10000);
                for (String pluginId : PluginManagerConfigurableNew.requestToPluginRepository(url)) {
                  IdeaPluginDescriptor descriptor = allRepositoriesMap.get(pluginId);
                  if (descriptor != null) {
                    result.descriptors.add(descriptor);
                  }
                }

                if (parser.searchQuery != null) {
                  String builtinUrl = ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl();
                  List<IdeaPluginDescriptor> builtinList = new ArrayList<>();

                  for (Map.Entry<String, List<IdeaPluginDescriptor>> entry : customRepositoriesMap.entrySet()) {
                    List<IdeaPluginDescriptor> descriptors = entry.getKey().equals(builtinUrl) ? builtinList : result.descriptors;
                    for (IdeaPluginDescriptor descriptor : entry.getValue()) {
                      if (StringUtil.containsIgnoreCase(descriptor.getName(), parser.searchQuery)) {
                        descriptors.add(descriptor);
                      }
                    }
                  }

                  result.descriptors.addAll(0, builtinList);
                }

                ContainerUtil.removeDuplicates(result.descriptors);

                if (!result.descriptors.isEmpty()) {
                  String title = "Sort By";

                  for (AnAction action : myMarketplaceSortByGroup.getChildren(null)) {
                    MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
                    sortByAction.setState(parser);
                    if (sortByAction.myState) {
                      title = "Sort By: " + sortByAction.myOption.name();
                    }
                  }

                  myMarketplaceSortByAction.setText(title);
                  result.addRightAction(myMarketplaceSortByAction);
                }
              }
              catch (IOException e) {
                PluginManagerMain.LOG.info(e);

                ApplicationManager.getApplication().invokeLater(() -> myPanel.getEmptyText().setText("Search result are not loaded.")
                  .appendSecondaryText("Check the internet connection.", StatusText.DEFAULT_ATTRIBUTES, null), ModalityState.any());
              }
            }
          };

        return myMarketplaceSearchPanel;
      }
    };
  }

  private void createInstalledTab() {
    myInstalledSearchGroup = new DefaultActionGroup();

    for (InstalledSearchOption option : InstalledSearchOption.values()) {
      myInstalledSearchGroup.add(new InstalledSearchOptionAction(option));
    }

    myInstalledTab = new PluginsTab() {
      @Override
      protected void createSearchTextField(int flyDelay) {
        super.createSearchTextField(flyDelay);

        JBTextField textField = mySearchTextField.getTextEditor();
        textField.putClientProperty("search.extension", ExtendableTextComponent.Extension
          .create(AllIcons.Actions.More, AllIcons.Actions.More, "Search Options", // TODO: icon
                  () -> showRightBottomPopup(textField, "Show", myInstalledSearchGroup)));
        textField.putClientProperty("JTextField.variant", null);
        textField.putClientProperty("JTextField.variant", "search");

        mySearchTextField.setHistoryPropertyName("InstalledPluginsSearchHistory");
      }

      @NotNull
      @Override
      protected PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener) {
        PluginDetailsPageComponent detailPanel = new PluginDetailsPageComponent(myPluginModel, searchListener, false);
        myPluginModel.addDetailPanel(detailPanel);
        return detailPanel;
      }

      @NotNull
      @Override
      protected JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        myInstalledPanel = new PluginsGroupComponent(new PluginListLayout(), eventHandler, myNameListener,
                                                     PluginManagerConfigurableNewLayout.this.mySearchListener,
                                                     descriptor -> new NewListPluginComponent(myPluginModel, descriptor, false));

        myInstalledPanel.setSelectionListener(selectionListener);
        PluginManagerConfigurableNew.registerCopyProvider(myInstalledPanel);

        //noinspection ConstantConditions
        ((SearchUpDownPopupController)myInstalledSearchPanel.controller).setEventHandler(eventHandler);

        PluginLogo.startBatchMode();

        PluginsGroup installing = new PluginsGroup("Installing");
        installing.descriptors.addAll(MyPluginModel.getInstallingPlugins());
        if (!installing.descriptors.isEmpty()) {
          installing.sortByName();
          installing.titleWithCount();
          myInstalledPanel.addGroup(installing);
        }

        PluginsGroup downloaded = new PluginsGroup("Downloaded");
        downloaded.descriptors.addAll(InstalledPluginsState.getInstance().getInstalledPlugins());

        PluginsGroup bundled = new PluginsGroup("Bundled");

        ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
        int bundledEnabled = 0;
        int downloadedEnabled = 0;

        boolean hideImplDetails = !Registry.is("plugins.show.implementation.details");

        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          if (!appInfo.isEssentialPlugin(descriptor.getPluginId().getIdString())) {
            if (descriptor.isBundled()) {
              if (hideImplDetails && descriptor.isImplementationDetail()) {
                continue;
              }
              bundled.descriptors.add(descriptor);
              if (descriptor.isEnabled()) {
                bundledEnabled++;
              }
            }
            else {
              downloaded.descriptors.add(descriptor);
              if (descriptor.isEnabled()) {
                downloadedEnabled++;
              }
            }
          }
        }

        if (!downloaded.descriptors.isEmpty()) {
          myUpdateAll.setListener(new LinkListener<Object>() {
            @Override
            public void linkSelected(LinkLabel aSource, Object aLinkData) {
              myUpdateAll.setEnabled(false);

              for (CellPluginComponent plugin : downloaded.ui.plugins) {
                ((NewListPluginComponent)plugin).updatePlugin();
              }
            }
          }, null);
          downloaded.addRightAction(myUpdateAll);

          downloaded.addRightAction(myUpdateCounter);

          downloaded.sortByName();
          downloaded.titleWithCount(downloadedEnabled);
          myInstalledPanel.addGroup(downloaded);
          myPluginModel.addEnabledGroup(downloaded);
        }

        myPluginModel.setDownloadedGroup(myInstalledPanel, downloaded, installing);

        bundled.sortByName();
        bundled.titleWithCount(bundledEnabled);
        myInstalledPanel.addGroup(bundled);
        myPluginModel.addEnabledGroup(bundled);

        myPluginUpdatesService.connectInstalled(updates -> {
          if (ContainerUtil.isEmpty(updates)) {
            clearUpdates(myInstalledPanel);
            clearUpdates(myInstalledSearchPanel.getPanel());
          }
          else {
            applyUpdates(myInstalledPanel, updates);
            applyUpdates(myInstalledSearchPanel.getPanel(), updates);
          }
          selectionListener.accept(myInstalledPanel);
        });

        PluginLogo.endBatchMode();

        return createScrollPane(myInstalledPanel, true);
      }

      @Override
      protected void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        selectionListener.accept(myInstalledPanel);
      }

      @Override
      public void hideSearchPanel() {
        super.hideSearchPanel();
        if (myInstalledSearchSetState) {
          for (AnAction action : myInstalledSearchGroup.getChildren(null)) {
            ((InstalledSearchOptionAction)action).setState(null);
          }
        }
        myPluginModel.setInvalidFixCallback(null);
      }

      @NotNull
      @Override
      protected SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        SearchUpDownPopupController installedController = new SearchUpDownPopupController(mySearchTextField) {
          @NotNull
          @Override
          protected List<String> getAttributes() {
            return Arrays.asList("/downloaded", "/outdated", "/enabled", "/disabled", "/invalid", "/bundled", "/vendor:");
          }

          @Nullable
          @Override
          protected List<String> getValues(@NotNull String attribute) {
            return "/vendor:".equals(attribute) ? myPluginModel.getVendors() : null;
          }

          @Override
          protected void handleAppendToQuery() {
            showPopupForQuery();
          }

          @Override
          protected void handleAppendAttributeValue() {
            showPopupForQuery();
          }

          @Override
          protected void showPopupForQuery() {
            showSearchPanel(mySearchTextField.getText());
          }
        };

        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        installedController.setSearchResultEventHandler(eventHandler);

        PluginsGroupComponent panel = new PluginsGroupComponent(new PluginListLayout(), eventHandler, myNameListener,
                                                                PluginManagerConfigurableNewLayout.this.mySearchListener,
                                                                descriptor -> new NewListPluginComponent(myPluginModel, descriptor, false));

        panel.setSelectionListener(selectionListener);
        PluginManagerConfigurableNew.registerCopyProvider(panel);

        myInstalledSearchCallback = updateAction -> {
          List<String> queries = new ArrayList<>();
          new SearchQueryParser.InstalledWithVendor(mySearchTextField.getText()) {
            @Override
            protected void addToSearchQuery(@NotNull String query) {
              super.addToSearchQuery(query);
              queries.add(query);
            }

            @Override
            protected void handleAttribute(@NotNull String name, @NotNull String value, boolean invert) {
              super.handleAttribute(name, value, invert);
              if (!updateAction.myState) {
                queries.add("/" + name + (value.isEmpty() ? "" : ":" + SearchQueryParser.wrapAttribute(value)));
              }
            }
          };

          if (updateAction.myState) {
            for (AnAction action : myInstalledSearchGroup.getChildren(null)) {
              if (action != updateAction) {
                ((InstalledSearchOptionAction)action).myState = false;
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
            mySearchTextField.setTextIgnoreEvents(query);
            if (query.isEmpty()) {
              myInstalledTab.hideSearchPanel();
            }
            else {
              myInstalledTab.showSearchPanel(query);
            }
          }
          finally {
            myInstalledSearchSetState = true;
          }
        };

        myInstalledSearchPanel = new SearchResultPanel(installedController, panel, 0, 0) {
          @Override
          protected void setEmptyText() {
            myPanel.getEmptyText().setText("Nothing found.");
            myPanel.getEmptyText().appendSecondaryText("Search in marketplace", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                       e -> myTabHeaderComponent.setSelectionWithEvents(MARKETPLACE_TAB));
          }

          @Override
          protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
            myPluginModel.setInvalidFixCallback(null);

            SearchQueryParser.InstalledWithVendor parser = new SearchQueryParser.InstalledWithVendor(query);

            if (myInstalledSearchSetState) {
              for (AnAction action : myInstalledSearchGroup.getChildren(null)) {
                ((InstalledSearchOptionAction)action).setState(parser);
              }
            }

            if (parser.vendors.isEmpty()) {
              for (UIPluginGroup uiGroup : myInstalledPanel.getGroups()) {
                for (CellPluginComponent plugin : uiGroup.plugins) {
                  if (parser.attributes) {
                    if (parser.enabled && !myPluginModel.isEnabled(plugin.myPlugin)) {
                      continue;
                    }
                    if (parser.disabled && myPluginModel.isEnabled(plugin.myPlugin)) {
                      continue;
                    }
                    if (parser.bundled && !plugin.myPlugin.isBundled()) {
                      continue;
                    }
                    if (parser.downloaded && plugin.myPlugin.isBundled()) {
                      continue;
                    }
                    if (parser.invalid && !myPluginModel.hasErrors(plugin.myPlugin)) {
                      continue;
                    }
                    if (parser.needUpdate && !PluginUpdatesService.isNeedUpdate(plugin.myPlugin)) {
                      continue;
                    }
                  }
                  if (parser.searchQuery != null && !StringUtil.containsIgnoreCase(plugin.myPlugin.getName(), parser.searchQuery)) {
                    continue;
                  }
                  result.descriptors.add(plugin.myPlugin);
                }
              }

              if (!result.descriptors.isEmpty()) {
                if (parser.invalid) {
                  myPluginModel.setInvalidFixCallback(() -> {
                    PluginsGroup group = myInstalledSearchPanel.getGroup();
                    if (group.ui == null) {
                      myPluginModel.setInvalidFixCallback(null);
                      return;
                    }

                    PluginsGroupComponent resultPanel = myInstalledSearchPanel.getPanel();

                    for (IdeaPluginDescriptor descriptor : new ArrayList<>(group.descriptors)) {
                      if (!myPluginModel.hasErrors(descriptor)) {
                        resultPanel.removeFromGroup(group, descriptor);
                      }
                    }

                    group.titleWithCount();
                    myInstalledSearchPanel.fullRepaint();

                    if (group.descriptors.isEmpty()) {
                      myPluginModel.setInvalidFixCallback(null);
                    }
                  });
                }

                Collection<PluginDownloader> updates = PluginUpdatesService.getUpdates();
                if (!ContainerUtil.isEmpty(updates)) {
                  myPostFillGroupCallback = () -> {
                    applyUpdates(myPanel, updates);
                    selectionListener.accept(myInstalledPanel);
                  };
                }
              }
            }
            else {
              for (UIPluginGroup uiGroup : myInstalledPanel.getGroups()) {
                for (CellPluginComponent plugin : uiGroup.plugins) {
                  if (MyPluginModel.isVendor(plugin.myPlugin, parser.vendors)) {
                    result.descriptors.add(plugin.myPlugin);
                  }
                }
              }
            }
          }
        };

        return myInstalledSearchPanel;
      }
    };
  }

  private static void clearUpdates(@NotNull PluginsGroupComponent panel) {
    for (UIPluginGroup group : panel.getGroups()) {
      for (CellPluginComponent plugin : group.plugins) {
        ((NewListPluginComponent)plugin).setUpdateDescriptor(null);
      }
    }
  }

  private static void applyUpdates(@NotNull PluginsGroupComponent panel, @NotNull Collection<? extends PluginDownloader> updates) {
    for (PluginDownloader downloader : updates) {
      IdeaPluginDescriptor descriptor = downloader.getDescriptor();
      for (UIPluginGroup group : panel.getGroups()) {
        CellPluginComponent component = group.findComponent(descriptor);
        if (component != null) {
          ((NewListPluginComponent)component).setUpdateDescriptor(descriptor);
          break;
        }
      }
    }
  }

  private enum SortBySearchOption {
    Downloads, Name, Rating, Relevance, Updated
  }

  private class MarketplaceSortByAction extends ToggleAction {
    private final SortBySearchOption myOption;
    private boolean myState;

    private MarketplaceSortByAction(@NotNull SortBySearchOption option) {
      super(option.name());
      myOption = option;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myState;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myState = state;
      myMarketplaceSortByCallback.accept(this);
    }

    public void setState(@NotNull SearchQueryParser.Marketplace parser) {
      if (myOption == SortBySearchOption.Relevance) {
        myState = parser.sortBy == null;
        getTemplatePresentation().setVisible(parser.sortBy == null || !parser.tags.isEmpty() || parser.searchQuery != null);
      }
      else {
        myState = parser.sortBy != null && myOption.name().equalsIgnoreCase(parser.sortBy);
      }
    }

    @Nullable
    public String getQuery() {
      switch (myOption) {
        case Downloads:
          return "/sortBy:downloads";
        case Name:
          return "/sortBy:name";
        case Rating:
          return "/sortBy:rating";
        case Updated:
          return "/sortBy:updated";
        case Relevance:
        default:
          return null;
      }
    }
  }

  private enum InstalledSearchOption {
    Downloaded, NeedUpdate, Enabled, Disabled, Invalid, Bundled
  }

  private class InstalledSearchOptionAction extends ToggleAction {
    private final InstalledSearchOption myOption;
    private boolean myState;

    private InstalledSearchOptionAction(@NotNull InstalledSearchOption option) {
      super(option == InstalledSearchOption.NeedUpdate ? "Update available" : option.name());
      myOption = option;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myState;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myState = state;
      myInstalledSearchCallback.accept(this);
    }

    public void setState(@Nullable SearchQueryParser.InstalledWithVendor parser) {
      if (parser == null) {
        myState = false;
        return;
      }

      switch (myOption) {
        case Enabled:
          myState = parser.enabled;
          break;
        case Disabled:
          myState = parser.disabled;
          break;
        case Downloaded:
          myState = parser.downloaded;
          break;
        case Bundled:
          myState = parser.bundled;
          break;
        case Invalid:
          myState = parser.invalid;
          break;
        case NeedUpdate:
          myState = parser.needUpdate;
          break;
      }
    }

    @NotNull
    public String getQuery() {
      return myOption == InstalledSearchOption.NeedUpdate ? "/outdated" : "/" + StringUtil.decapitalize(myOption.name());
    }
  }

  private static class GroupByActionGroup extends DefaultActionGroup implements CheckedActionGroup {
  }

  private class ChangePluginStateAction extends AnAction {
    private final boolean myEnable;

    private ChangePluginStateAction(boolean enable) {
      super(enable ? "Enable All Downloaded Plugins" : "Disable All Downloaded Plugins");
      myEnable = enable;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      IdeaPluginDescriptor[] descriptors;
      PluginsGroup group = myPluginModel.getDownloadedGroup();

      if (group == null || group.ui == null) {
        ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
        List<IdeaPluginDescriptor> descriptorList = new ArrayList<>();

        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          if (!appInfo.isEssentialPlugin(descriptor.getPluginId().getIdString()) &&
              !descriptor.isBundled() && descriptor.isEnabled() != myEnable) {
            descriptorList.add(descriptor);
          }
        }

        descriptors = descriptorList.toArray(new IdeaPluginDescriptor[0]);
      }
      else {
        descriptors = group.ui.plugins.stream().filter(component -> myPluginModel.isEnabled(component.myPlugin) != myEnable)
          .map(component -> component.myPlugin).toArray(IdeaPluginDescriptor[]::new);
      }

      if (descriptors.length > 0) {
        myPluginModel.changeEnableDisable(descriptors, myEnable);
      }
    }
  }

  @NotNull
  private static JComponent createScrollPane(@NotNull PluginsGroupComponent panel, boolean initSelection) {
    JBScrollPane pane =
      new JBScrollPane(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setBorder(JBUI.Borders.empty());
    if (initSelection) {
      panel.initialSelection();
    }
    return pane;
  }

  @NotNull
  private List<IdeaPluginDescriptor> getPluginRepositories() {
    synchronized (myRepositoriesLock) {
      if (myAllRepositoriesList != null) {
        return myAllRepositoriesList;
      }
    }
    try {
      List<IdeaPluginDescriptor> list = RepositoryHelper.loadCachedPlugins();
      if (list != null) {
        return list;
      }
    }
    catch (IOException e) {
      PluginManagerMain.LOG.info(e);
    }
    return Collections.emptyList();
  }

  @NotNull
  private Pair<Map<String, IdeaPluginDescriptor>, Map<String, List<IdeaPluginDescriptor>>> loadPluginRepositories() {
    synchronized (myRepositoriesLock) {
      if (myAllRepositoriesMap != null) {
        return Pair.create(myAllRepositoriesMap, myCustomRepositoriesMap);
      }
    }

    List<IdeaPluginDescriptor> list = new ArrayList<>();
    Map<String, IdeaPluginDescriptor> map = new HashMap<>();
    Map<String, List<IdeaPluginDescriptor>> custom = new HashMap<>();

    for (String host : RepositoryHelper.getPluginHosts()) {
      try {
        List<IdeaPluginDescriptor> descriptors = RepositoryHelper.loadPlugins(host, null);
        if (host != null) {
          custom.put(host, descriptors);
        }
        for (IdeaPluginDescriptor plugin : descriptors) {
          String id = plugin.getPluginId().getIdString();
          if (!map.containsKey(id)) {
            list.add(plugin);
            map.put(id, plugin);
          }
        }
      }
      catch (IOException e) {
        if (host == null) {
          PluginManagerMain.LOG
            .info("Main plugin repository is not available ('" + e.getMessage() + "'). Please check your network settings.");
        }
        else {
          PluginManagerMain.LOG.info(host, e);
        }
      }
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      InstalledPluginsState state = InstalledPluginsState.getInstance();
      for (IdeaPluginDescriptor descriptor : list) {
        state.onDescriptorDownload(descriptor);
      }
    });

    synchronized (myRepositoriesLock) {
      if (myAllRepositoriesList == null) {
        myAllRepositoriesList = list;
        myAllRepositoriesMap = map;
        myCustomRepositoriesMap = custom;
      }
      return Pair.create(myAllRepositoriesMap, myCustomRepositoriesMap);
    }
  }

  private void addGroup(@NotNull List<? super PluginsGroup> groups,
                        @NotNull String name,
                        @NotNull String showAllQuery,
                        @NotNull ThrowableNotNullFunction<? super List<IdeaPluginDescriptor>, Boolean, ? extends IOException> function)
    throws IOException {
    PluginsGroup group = new PluginsGroup(name);

    if (Boolean.TRUE.equals(function.fun(group.descriptors))) {
      //noinspection unchecked
      group.rightAction = new LinkLabel("Show All", null, myMarketplaceTab.mySearchListener, showAllQuery);
      group.rightAction.setBorder(JBUI.Borders.emptyRight(5));
    }

    if (!group.descriptors.isEmpty()) {
      groups.add(group);
    }
  }

  private void addGroup(@NotNull List<? super PluginsGroup> groups,
                        @NotNull Map<String, IdeaPluginDescriptor> allRepositoriesMap,
                        @NotNull String name,
                        @NotNull String query,
                        @NotNull String showAllQuery) throws IOException {
    addGroup(groups, name, showAllQuery, descriptors -> PluginManagerConfigurableNew.loadPlugins(descriptors, allRepositoriesMap, query));
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return PluginManagerConfigurable.ID;
  }

  @Override
  public void disposeUIResources() {
    myPluginModel.toBackground();

    myMarketplaceTab.dispose();
    myInstalledTab.dispose();

    if (myMarketplacePanel != null) {
      myMarketplacePanel.dispose();
    }
    if (myMarketplaceSearchPanel != null) {
      myMarketplaceSearchPanel.dispose();
    }

    myPluginUpdatesService.dispose();

    if (myShutdownCallback != null) {
      myShutdownCallback.run();
      myShutdownCallback = null;
    }
  }

  @Override
  public boolean isModified() {
    if (myPluginModel.needRestart) {
      return true;
    }

    int rowCount = myPluginModel.getRowCount();

    for (int i = 0; i < rowCount; i++) {
      IdeaPluginDescriptor descriptor = myPluginModel.getObjectAt(i);
      boolean enabledInTable = myPluginModel.isEnabled(descriptor);

      if (descriptor.isEnabled() != enabledInTable) {
        if (enabledInTable && !PluginManagerCore.isDisabled(descriptor.getPluginId().getIdString())) {
          continue; // was disabled automatically on startup
        }
        return true;
      }
    }

    for (Map.Entry<PluginId, Boolean> entry : myPluginModel.getEnabledMap().entrySet()) {
      Boolean enabled = entry.getValue();
      if (enabled != null && !enabled && !PluginManagerCore.isDisabled(entry.getKey().getIdString())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    Map<PluginId, Boolean> enabledMap = myPluginModel.getEnabledMap();
    List<String> dependencies = new ArrayList<>();

    for (Map.Entry<PluginId, Set<PluginId>> entry : myPluginModel.getDependentToRequiredListMap().entrySet()) {
      PluginId id = entry.getKey();

      if (enabledMap.get(id) == null) {
        continue;
      }

      for (PluginId dependId : entry.getValue()) {
        if (!PluginManagerCore.isModuleDependency(dependId)) {
          IdeaPluginDescriptor descriptor = PluginManager.getPlugin(id);
          if (!(descriptor instanceof IdeaPluginDescriptorImpl) ||
              !((IdeaPluginDescriptorImpl)descriptor).isDeleted() && !descriptor.isImplementationDetail()) {
            dependencies.add("\"" + (descriptor == null ? id.getIdString() : descriptor.getName()) + "\"");
          }
          break;
        }
      }
    }

    if (!dependencies.isEmpty()) {
      throw new ConfigurationException("<html><body style=\"padding: 5px;\">Unable to apply changes: plugin" +
                                       (dependencies.size() == 1 ? " " : "s ") +
                                       StringUtil.join(dependencies, ", ") +
                                       " won't be able to load.</body></html>");
    }

    int rowCount = myPluginModel.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      IdeaPluginDescriptor descriptor = myPluginModel.getObjectAt(i);
      descriptor.setEnabled(myPluginModel.isEnabled(descriptor.getPluginId()));
    }

    List<String> disableIds = new ArrayList<>();
    for (Map.Entry<PluginId, Boolean> entry : enabledMap.entrySet()) {
      Boolean enabled = entry.getValue();
      if (enabled != null && !enabled) {
        disableIds.add(entry.getKey().getIdString());
      }
    }

    try {
      PluginManagerCore.saveDisabledPlugins(disableIds, false);
    }
    catch (IOException e) {
      PluginManagerMain.LOG.error(e);
    }

    if (myShutdownCallback == null && myPluginModel.createShutdownCallback) {
      myShutdownCallback = () -> ApplicationManager.getApplication().invokeLater(
        () -> PluginManagerConfigurable.shutdownOrRestartApp(IdeBundle.message("update.notifications.title")));
    }
  }

  @NotNull
  @Override
  public MyPluginModel getPluginModel() {
    return myPluginModel;
  }

  @Override
  public void select(@NotNull IdeaPluginDescriptor... descriptors) {
    if (myTabHeaderComponent.getSelectionTab() != INSTALLED_TAB) {
      myTabHeaderComponent.setSelectionWithEvents(INSTALLED_TAB);
    }

    if (descriptors.length == 0) {
      return;
    }

    List<CellPluginComponent> components = new ArrayList<>();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      for (UIPluginGroup group : myInstalledPanel.getGroups()) {
        CellPluginComponent component = group.findComponent(descriptor);
        if (component != null) {
          components.add(component);
          break;
        }
      }
    }

    if (!components.isEmpty()) {
      myInstalledPanel.setSelection(components);
    }
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    if (StringUtil.isEmpty(option) &&
        (myTabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB ? myMarketplaceSearchPanel : myInstalledSearchPanel).isEmpty()) {
      return null;
    }

    return () -> {
      if (myTabHeaderComponent.getSelectionTab() != INSTALLED_TAB) {
        myTabHeaderComponent.setSelectionWithEvents(INSTALLED_TAB);
      }

      myInstalledTab.clearSearchPanel(option);

      if (!StringUtil.isEmpty(option)) {
        myInstalledTab.showSearchPanel(option);
      }
    };
  }
}