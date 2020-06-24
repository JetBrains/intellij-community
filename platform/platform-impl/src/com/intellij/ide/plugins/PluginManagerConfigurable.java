// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.newui.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurable
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider {

  private static final Logger LOG = Logger.getInstance(PluginManagerConfigurable.class);

  public static final String ID = "preferences.pluginManager";
  public static final String SELECTION_TAB_KEY = "PluginConfigurable.selectionTab";

  @SuppressWarnings("UseJBColor") public static final Color MAIN_BG_COLOR =
    JBColor.namedColor("Plugins.background", new JBColor(() -> JBColor.isBright() ? UIUtil.getListBackground() : new Color(0x313335)));
  public static final Color SEARCH_BG_COLOR = JBColor.namedColor("Plugins.SearchField.background", MAIN_BG_COLOR);
  public static final Color SEARCH_FIELD_BORDER_COLOR =
    JBColor.namedColor("Plugins.SearchField.borderColor", new JBColor(0xC5C5C5, 0x515151));

  private static final int MARKETPLACE_TAB = 0;
  private static final int INSTALLED_TAB = 1;

  public static final int ITEMS_PER_GROUP = 9;

  public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");
  private static final DecimalFormat K_FORMAT = new DecimalFormat("###.#K");
  private static final DecimalFormat M_FORMAT = new DecimalFormat("###.#M");

  private TabbedPaneHeaderComponent myTabHeaderComponent;
  private MultiPanel myCardPanel;

  private PluginsTab myMarketplaceTab;
  private PluginsTab myInstalledTab;

  private PluginsGroupComponentWithProgress myMarketplacePanel;
  private PluginsGroupComponent myInstalledPanel;

  private Runnable myMarketplaceRunnable;

  private SearchResultPanel myMarketplaceSearchPanel;
  private SearchResultPanel myInstalledSearchPanel;

  private final LinkLabel<Object> myUpdateAll = new LinkLabel<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final JLabel myUpdateCounter = new CountComponent();
  private final CountIcon myCountIcon = new CountIcon();

  private final MyPluginModel myPluginModel = new MyPluginModel() {
    @Override
    @NotNull
    public Collection<IdeaPluginDescriptor> getCustomRepoPlugins() {
      return getCustomRepositoryPlugins();
    }
  };

  private PluginUpdatesService myPluginUpdatesService;

  private Collection<IdeaPluginDescriptor> myCustomRepositoryPluginsList;
  private Map<String, List<IdeaPluginDescriptor>> myCustomRepositoryPluginsMap;
  private final Object myRepositoriesLock = new Object();
  private List<String> myTagsSorted;
  private List<String> myVendorsSorted;

  private DefaultActionGroup myMarketplaceSortByGroup;
  private Consumer<MarketplaceSortByAction> myMarketplaceSortByCallback;
  private LinkComponent myMarketplaceSortByAction;

  private DefaultActionGroup myInstalledSearchGroup;
  private Consumer<InstalledSearchOptionAction> myInstalledSearchCallback;
  private boolean myInstalledSearchSetState = true;

  private Collection<PluginDownloader> myInitUpdates;

  public PluginManagerConfigurable() {
  }

  /**
   * @deprecated use {@link PluginManagerConfigurable}
   */
  @Deprecated
  public PluginManagerConfigurable(PluginManagerUISettings uiSettings) {
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
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

  @NotNull
  public JComponent getTopComponent() {
    myPluginModel.setTopController(TopComponentController.EMPTY);
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

    myTabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.marketplace"), null);
    myTabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.installed"), myCountIcon);

    Consumer<Integer> callback = countValue -> {
      int count = countValue == null ? 0 : countValue;
      String text = String.valueOf(count);
      boolean visible = count > 0;

      myUpdateAll.setEnabled(true);
      myUpdateAll.setVisible(visible);

      myUpdateCounter.setText(text);
      myUpdateCounter.setVisible(visible);

      myCountIcon.setText(text);
      myTabHeaderComponent.update();
    };
    if (myInitUpdates != null) {
      callback.accept(myInitUpdates.size());
    }
    myPluginUpdatesService = PluginUpdatesService.connectConfigurable(callback);
    myPluginModel.setPluginUpdatesService(myPluginUpdatesService);

    boolean selectInstalledTab = !ContainerUtil.isEmpty(myInitUpdates);

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
    myCardPanel.setPreferredSize(new JBDimension(800, 600));

    myTabHeaderComponent.setListener();

    int selectionTab = selectInstalledTab ? INSTALLED_TAB : getStoredSelectionTab();
    myTabHeaderComponent.setSelection(selectionTab);
    myCardPanel.select(selectionTab, true);

    if (selectInstalledTab) {
      myInstalledTab.setSearchQuery("/outdated");
    }

    return myCardPanel;
  }

  @NotNull
  private DefaultActionGroup createGearActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new DumbAwareAction(IdeBundle.message("plugin.manager.repositories")) {
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
    actions.add(new InstallFromDiskAction());
    actions.addSeparator();
    actions.add(new ChangePluginStateAction(false));
    actions.add(new ChangePluginStateAction(true));

    return actions;
  }

  private static void showRightBottomPopup(@NotNull Component component, @NotNull @Nls String title, @NotNull ActionGroup group) {
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
                setCaptionCentered(false);
              }

              @Override
              protected void paintLine(Graphics g, int x, int y, int width) {
              }
            };
          }

          @Override
          protected void setSeparatorFont(Font font) {
            mySeparatorComponent.setFont(font);
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
      myCustomRepositoryPluginsList = null;
      myCustomRepositoryPluginsMap = null;
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
    int value = PropertiesComponent.getInstance().getInt(SELECTION_TAB_KEY, MARKETPLACE_TAB);
    return value < MARKETPLACE_TAB || value > INSTALLED_TAB ? MARKETPLACE_TAB : value;
  }

  private static void storeSelectionTab(int value) {
    PropertiesComponent.getInstance().setValue(SELECTION_TAB_KEY, value, MARKETPLACE_TAB);
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
        myMarketplacePanel =
          new PluginsGroupComponentWithProgress(new PluginListLayout(), eventHandler,
                                                d -> new ListPluginComponent(myPluginModel, d, mySearchListener, true));

        myMarketplacePanel.setSelectionListener(selectionListener);
        registerCopyProvider(myMarketplacePanel);

        //noinspection ConstantConditions
        ((SearchUpDownPopupController)myMarketplaceSearchPanel.controller).setEventHandler(eventHandler);

        Runnable runnable = () -> {
          List<PluginsGroup> groups = new ArrayList<>();

          try {
            Map<String, List<IdeaPluginDescriptor>> customRepositoriesMap = loadCustomRepositoryPlugins();
            try {
              addGroupViaLightDescriptor(groups, IdeBundle.message("plugins.configurable.featured"), "is_featured_search=true",
                                         "/sortBy:featured");
              addGroupViaLightDescriptor(groups, IdeBundle.message("plugins.configurable.new.and.updated"), "orderBy=update+date",
                                         "/sortBy:updated");
              addGroupViaLightDescriptor(groups, IdeBundle.message("plugins.configurable.top.downloads"), "orderBy=downloads",
                                         "/sortBy:downloads");
              addGroupViaLightDescriptor(groups, IdeBundle.message("plugins.configurable.top.rated"), "orderBy=rating", "/sortBy:rating");
            }
            catch (IOException e) {
              LOG.info("Main plugin repository is not available ('" + e.getMessage() + "'). Please check your network settings.");
            }

            for (String host : UpdateSettings.getInstance().getPluginHosts()) {
              List<IdeaPluginDescriptor> allDescriptors = customRepositoriesMap.get(host);
              if (allDescriptors != null) {
                addGroup(groups, IdeBundle.message("plugins.configurable.repository.0", host), "/repository:\"" + host + "\"",
                         descriptors -> {
                           int allSize = allDescriptors.size();
                           descriptors.addAll(ContainerUtil.getFirstItems(allDescriptors, ITEMS_PER_GROUP));
                           PluginsGroup.sortByName(descriptors);
                           return allSize > ITEMS_PER_GROUP;
                         });
              }
            }
          }
          catch (IOException e) {
            LOG.info(e);
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

        myMarketplacePanel.getEmptyText().setText(IdeBundle.message("plugins.configurable.marketplace.plugins.not.loaded"))
          .appendSecondaryText(IdeBundle.message("message.check.the.internet.connection.and") + " ", StatusText.DEFAULT_ATTRIBUTES, null)
          .appendSecondaryText(IdeBundle.message("message.link.refresh"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                               e -> myMarketplaceRunnable.run());

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
            attributes.add(SearchWords.TAG.getValue());
            attributes.add(SearchWords.SORT_BY.getValue());
            attributes.add(SearchWords.ORGANIZATION.getValue());
            if (!UpdateSettings.getInstance().getPluginHosts().isEmpty()) {
              attributes.add(SearchWords.REPOSITORY.getValue());
            }
            return attributes;
          }

          @Nullable
          @Override
          protected List<String> getValues(@NotNull String attribute) {
            SearchWords word = SearchWords.find(attribute);
            if (word == null) return null;
            switch (word) {
              case TAG:
                if (ContainerUtil.isEmpty(myTagsSorted)) { // XXX
                  Set<String> allTags = new HashSet<>();
                  for (IdeaPluginDescriptor descriptor : getCustomRepositoryPlugins()) {
                    if (descriptor instanceof PluginNode) {
                      List<String> tags = ((PluginNode)descriptor).getTags();
                      if (!ContainerUtil.isEmpty(tags)) {
                        allTags.addAll(tags);
                      }
                    }
                  }
                  try {
                    ProcessIOExecutorService.INSTANCE.submit(() -> {
                      allTags.addAll(MarketplaceRequests.getInstance().getAllPluginsTags());
                    }).get();
                  }
                  catch (InterruptedException | ExecutionException e) {
                    LOG.error("Error while getting tags from marketplace", e);
                  }
                  myTagsSorted = ContainerUtil.sorted(allTags, String::compareToIgnoreCase);
                }
                return myTagsSorted;
              case SORT_BY:
                return Arrays.asList("downloads", "name", "rating", "updated");
              case ORGANIZATION:
                if (ContainerUtil.isEmpty(myVendorsSorted)) { // XXX
                  LinkedHashSet<String> vendors = new LinkedHashSet<>();
                  try {
                    ProcessIOExecutorService.INSTANCE.submit(() -> {
                      vendors.addAll(MarketplaceRequests.getInstance().getAllPluginsVendors());
                    }).get();
                  }
                  catch (InterruptedException | ExecutionException e) {
                    LOG.error("Error while getting vendors from marketplace", e);
                  }
                  myVendorsSorted = new ArrayList<>(vendors);
                }
                return myVendorsSorted;
              case REPOSITORY:
                return UpdateSettings.getInstance().getPluginHosts();
            }
            return null;
          }

          @Override
          protected void showPopupForQuery() {
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
          (component, __) -> showRightBottomPopup(component.getParent().getParent(), IdeBundle.message("plugins.configurable.sort.by"),
                                                  myMarketplaceSortByGroup), null);

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
              queries.add(query);
            }

            @Override
            protected void handleAttribute(@NotNull String name, @NotNull String value) {
              queries.add(name + SearchQueryParser.wrapAttribute(value));
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
          new PluginsGroupComponentWithProgress(new PluginListLayout(), eventHandler,
                                                descriptor -> new ListPluginComponent(myPluginModel, descriptor, mySearchListener, true));

        panel.setSelectionListener(selectionListener);
        registerCopyProvider(panel);

        myMarketplaceSearchPanel =
          new SearchResultPanel(marketplaceController, panel, 0, 0) {
            @Override
            protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
              try {
                Map<String, List<IdeaPluginDescriptor>> customRepositoriesMap = loadCustomRepositoryPlugins();

                SearchQueryParser.Marketplace parser = new SearchQueryParser.Marketplace(query);

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

                List<PluginNode> pluginsFromMarketplace = MarketplaceRequests.getInstance().searchPlugins(parser.getUrlQuery(), 10000);
                List<IdeaPluginDescriptor> plugins = UpdateChecker.mergePluginsFromRepositories(
                  pluginsFromMarketplace,
                  ContainerUtil.flatten(customRepositoriesMap.values())
                ); // compare plugin versions between marketplace & custom repositories
                result.descriptors.addAll(plugins);

                if (parser.searchQuery != null) {
                  String builtinUrl = ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl();
                  List<IdeaPluginDescriptor> builtinList = new ArrayList<>();

                  for (Entry<String, List<IdeaPluginDescriptor>> entry : customRepositoriesMap.entrySet()) {
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
                LOG.info(e);
                ApplicationManager.getApplication().invokeLater(
                  () -> myPanel.getEmptyText()
                    .setText(IdeBundle.message("plugins.configurable.search.result.not.loaded"))
                    .appendSecondaryText(
                      IdeBundle.message("plugins.configurable.check.internet"), StatusText.DEFAULT_ATTRIBUTES, null), ModalityState.any()
                );
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
          .create(AllIcons.Actions.More, AllIcons.Actions.More, IdeBundle.message("plugins.configurable.search.options"), // TODO: icon
                  () -> showRightBottomPopup(textField, IdeBundle.message("plugins.configurable.show"), myInstalledSearchGroup)));
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
        myInstalledPanel =
          new PluginsGroupComponent(new PluginListLayout(), eventHandler,
                                    descriptor -> new ListPluginComponent(myPluginModel, descriptor, mySearchListener, false));

        myInstalledPanel.setSelectionListener(selectionListener);
        registerCopyProvider(myInstalledPanel);

        //noinspection ConstantConditions
        ((SearchUpDownPopupController)myInstalledSearchPanel.controller).setEventHandler(eventHandler);

        PluginLogo.startBatchMode();

        PluginsGroup installing = new PluginsGroup(IdeBundle.message("plugins.configurable.installing"));
        installing.descriptors.addAll(MyPluginModel.getInstallingPlugins());
        if (!installing.descriptors.isEmpty()) {
          installing.sortByName();
          installing.titleWithCount();
          myInstalledPanel.addGroup(installing);
        }

        PluginsGroup downloaded = new PluginsGroup(IdeBundle.message("plugins.configurable.downloaded"));
        downloaded.descriptors.addAll(InstalledPluginsState.getInstance().getInstalledPlugins());

        Map<String, List<IdeaPluginDescriptor>> bundledGroups = new HashMap<>();
        ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
        int downloadedEnabled = 0;
        boolean hideImplDetails = PluginManager.getInstance().hideImplementationDetails();
        String otherCategoryTitle = IdeBundle.message("plugins.configurable.other.bundled");

        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          if (!appInfo.isEssentialPlugin(descriptor.getPluginId())) {
            if (descriptor.isBundled()) {
              if (hideImplDetails && descriptor.isImplementationDetail()) {
                continue;
              }
              String category = StringUtil.defaultIfEmpty(descriptor.getCategory(), otherCategoryTitle);
              List<IdeaPluginDescriptor> groupDescriptors = bundledGroups.get(category);
              if (groupDescriptors == null) {
                bundledGroups.put(category, groupDescriptors = new ArrayList<>());
              }
              groupDescriptors.add(descriptor);
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
            public void linkSelected(LinkLabel<Object> aSource, Object aLinkData) {
              myUpdateAll.setEnabled(false);

              for (UIPluginGroup group : myInstalledPanel.getGroups()) {
                for (ListPluginComponent plugin : group.plugins) {
                  plugin.updatePlugin();
                }
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

        List<PluginsGroup> groups = new ArrayList<>();

        for (Entry<String, List<IdeaPluginDescriptor>> entry : bundledGroups.entrySet()) {
          PluginsGroup group = new PluginsGroup(entry.getKey()) {
            @Override
            public void titleWithCount(int enabled) {
              rightAction.setText(enabled == 0 ? IdeBundle.message("plugins.configurable.enable.all")
                                               : IdeBundle.message("plugins.configurable.disable.all"));
            }
          };
          group.descriptors.addAll(entry.getValue());
          group.sortByName();
          group.rightAction = new LinkLabel<>("", null, (__, ___) -> myPluginModel
            .changeEnableDisable(ContainerUtil.toArray(group.descriptors, IdeaPluginDescriptor[]::new),
                                 group.rightAction.getText().startsWith("Enable")));
          group.titleWithEnabled(myPluginModel);
          groups.add(group);
        }

        ContainerUtil.sort(groups, (o1, o2) -> StringUtil.compare(o1.title, o2.title, true));
        PluginsGroup otherGroup = ContainerUtil.find(groups, group -> group.title.equals(otherCategoryTitle));
        if (otherGroup != null) {
          groups.remove(otherGroup);
          groups.add(otherGroup);
        }

        for (PluginsGroup group : groups) {
          myInstalledPanel.addGroup(group);
          myPluginModel.addEnabledGroup(group);
        }

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

        if (myInitUpdates != null) {
          applyUpdates(myInstalledPanel, myInitUpdates);
        }

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
            return Arrays
              .asList(
                "/downloaded",
                "/outdated",
                "/enabled",
                "/disabled",
                "/invalid",
                "/bundled",
                SearchWords.ORGANIZATION.getValue(),
                SearchWords.TAG.getValue()
              );
          }

          @Nullable
          @Override
          protected List<String> getValues(@NotNull String attribute) {
            if (SearchWords.ORGANIZATION.getValue().equals(attribute)) {
              return myPluginModel.getVendors();
            }
            if (SearchWords.TAG.getValue().equals(attribute)) {
              return myPluginModel.getTags();
            }
            return null;
          }

          @Override
          protected void showPopupForQuery() {
            showSearchPanel(mySearchTextField.getText());
          }
        };

        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        installedController.setSearchResultEventHandler(eventHandler);

        PluginsGroupComponent panel =
          new PluginsGroupComponent(new PluginListLayout(), eventHandler,
                                    descriptor -> new ListPluginComponent(myPluginModel, descriptor, mySearchListener, false));

        panel.setSelectionListener(selectionListener);
        registerCopyProvider(panel);

        myInstalledSearchCallback = updateAction -> {
          List<String> queries = new ArrayList<>();
          new SearchQueryParser.Installed(mySearchTextField.getText()) {
            @Override
            protected void addToSearchQuery(@NotNull String query) {
              queries.add(query);
            }

            @Override
            protected void handleAttribute(@NotNull String name, @NotNull String value) {
              if (!updateAction.myState) {
                queries.add(name + (value.isEmpty() ? "" : SearchQueryParser.wrapAttribute(value)));
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
          protected void setEmptyText(@NotNull String query) {
            myPanel.getEmptyText().setText(IdeBundle.message("plugins.configurable.nothing.found"));
            if (query.contains("/downloaded") || query.contains("/outdated") ||
                query.contains("/enabled") || query.contains("/disabled") ||
                query.contains("/invalid") || query.contains("/bundled")) {
              return;
            }
            myPanel.getEmptyText().appendSecondaryText(IdeBundle.message("plugins.configurable.search.in.marketplace"),
                                                       SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                       e -> myTabHeaderComponent.setSelectionWithEvents(MARKETPLACE_TAB));
          }

          @Override
          protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
            myPluginModel.setInvalidFixCallback(null);

            SearchQueryParser.Installed parser = new SearchQueryParser.Installed(query);

            if (myInstalledSearchSetState) {
              for (AnAction action : myInstalledSearchGroup.getChildren(null)) {
                ((InstalledSearchOptionAction)action).setState(parser);
              }
            }

            List<IdeaPluginDescriptor> descriptors = myPluginModel.getInstalledDescriptors();

            if (!parser.vendors.isEmpty()) {
              for (Iterator<IdeaPluginDescriptor> I = descriptors.iterator(); I.hasNext(); ) {
                if (!MyPluginModel.isVendor(I.next(), parser.vendors)) {
                  I.remove();
                }
              }
            }
            if (!parser.tags.isEmpty()) {
              for (Iterator<IdeaPluginDescriptor> I = descriptors.iterator(); I.hasNext(); ) {
                if (!ContainerUtil.intersects(getTags(I.next()), parser.tags)) {
                  I.remove();
                }
              }
            }
            for (Iterator<IdeaPluginDescriptor> I = descriptors.iterator(); I.hasNext(); ) {
              IdeaPluginDescriptor descriptor = I.next();
              if (parser.attributes) {
                if (parser.enabled && (!myPluginModel.isEnabled(descriptor) || myPluginModel.hasErrors(descriptor))) {
                  I.remove();
                  continue;
                }
                if (parser.disabled && (myPluginModel.isEnabled(descriptor) || myPluginModel.hasErrors(descriptor))) {
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
                if (parser.invalid && !myPluginModel.hasErrors(descriptor)) {
                  I.remove();
                  continue;
                }
                if (parser.needUpdate && !PluginUpdatesService.isNeedUpdate(descriptor)) {
                  I.remove();
                  continue;
                }
              }
              if (parser.searchQuery != null && !StringUtil.containsIgnoreCase(descriptor.getName(), parser.searchQuery)) {
                I.remove();
              }
            }

            result.descriptors.addAll(descriptors);

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
              else if (parser.needUpdate) {
                result.rightAction = new LinkLabel<>(IdeBundle.message("plugin.manager.update.all"), null, (__, ___) -> {
                  result.rightAction.setEnabled(false);

                  for (ListPluginComponent plugin : result.ui.plugins) {
                    plugin.updatePlugin();
                  }
                });
              }

              Collection<PluginDownloader> updates = myInitUpdates == null ? PluginUpdatesService.getUpdates() : myInitUpdates;
              myInitUpdates = null;
              if (!ContainerUtil.isEmpty(updates)) {
                myPostFillGroupCallback = () -> {
                  applyUpdates(myPanel, updates);
                  selectionListener.accept(myInstalledPanel);
                };
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
      for (ListPluginComponent plugin : group.plugins) {
        plugin.setUpdateDescriptor(null);
      }
    }
  }

  private static void applyUpdates(@NotNull PluginsGroupComponent panel, @NotNull Collection<PluginDownloader> updates) {
    for (PluginDownloader downloader : updates) {
      IdeaPluginDescriptor descriptor = downloader.getDescriptor();
      for (UIPluginGroup group : panel.getGroups()) {
        ListPluginComponent component = group.findComponent(descriptor);
        if (component != null) {
          component.setUpdateDescriptor(descriptor);
          break;
        }
      }
    }
  }

  public static void registerCopyProvider(@NotNull PluginsGroupComponent component) {
    CopyProvider copyProvider = new CopyProvider() {
      @Override
      public void performCopy(@NotNull DataContext dataContext) {
        StringBuilder result = new StringBuilder();
        for (ListPluginComponent pluginComponent : component.getSelection()) {
          result.append(pluginComponent.myPlugin.getName()).append(" (").append(pluginComponent.myPlugin.getVersion()).append(")\n");
        }
        CopyPasteManager.getInstance().setContents(new TextTransferable(result.substring(0, result.length() - 1)));
      }

      @Override
      public boolean isCopyEnabled(@NotNull DataContext dataContext) {
        return !component.getSelection().isEmpty();
      }

      @Override
      public boolean isCopyVisible(@NotNull DataContext dataContext) {
        return true;
      }
    };

    DataManager.registerDataProvider(component, dataId -> PlatformDataKeys.COPY_PROVIDER.is(dataId) ? copyProvider : null);
  }

  @NotNull
  public static List<String> getTags(@NotNull IdeaPluginDescriptor plugin) {
    List<String> tags = null;
    String productCode = plugin.getProductCode();

    if (plugin instanceof PluginNode) {
      tags = ((PluginNode)plugin).getTags();

      if (productCode != null) {
        if (LicensePanel.isEA2Product(productCode)) {
          if (tags != null && tags.contains(Tags.Paid.name())) {
            tags = new ArrayList<>(tags);
            tags.remove(Tags.Paid.name());
          }
        }
        else if (tags == null) {
          return Collections.singletonList(Tags.Paid.name());
        }
        else if (!tags.contains(Tags.Paid.name())) {
          tags = new ArrayList<>(tags);
          tags.add(Tags.Paid.name());
        }
      }
    }
    else if (productCode != null && !plugin.isBundled() && !LicensePanel.isEA2Product(productCode)) {
      LicensingFacade instance = LicensingFacade.getInstance();
      if (instance != null) {
        String stamp = instance.getConfirmationStamp(productCode);
        if (stamp != null) {
          return Collections.singletonList(stamp.startsWith("eval:") ? Tags.Trial.name() : Tags.Purchased.name());
        }
      }
      return Collections.singletonList(Tags.Paid.name());
    }
    if (ContainerUtil.isEmpty(tags)) {
      return Collections.emptyList();
    }

    if (tags.size() > 1) {
      tags = new ArrayList<>(tags);
      if (tags.remove(Tags.EAP.name())) {
        tags.add(0, Tags.EAP.name());
      }
      if (tags.remove(Tags.Paid.name())) {
        tags.add(0, Tags.Paid.name());
      }
    }

    return tags;
  }

  @NotNull
  public static <T extends Component> T setTinyFont(@NotNull T component) {
    return SystemInfo.isMac ? RelativeFont.TINY.install(component) : component;
  }

  public static int offset5() {
    return JBUIScale.scale(5);
  }

  @Nullable
  public static synchronized String getDownloads(@NotNull IdeaPluginDescriptor plugin) {
    String downloads = null;
    if (plugin instanceof PluginNode) {
      downloads = ((PluginNode)plugin).getDownloads();
    }
    return getFormatLength(downloads);
  }

  @Nullable
  static synchronized String getFormatLength(@Nullable String len) {
    if (!StringUtil.isEmptyOrSpaces(len)) {
      try {
        long value = Long.parseLong(len);
        if (value > 1000) {
          return value < 1000000 ? K_FORMAT.format(value / 1000D) : M_FORMAT.format(value / 1000000D);
        }
        return Long.toString(value);
      }
      catch (NumberFormatException ignore) {
      }
    }

    return null;
  }

  @Nullable
  public static synchronized String getLastUpdatedDate(@NotNull IdeaPluginDescriptor plugin) {
    long date = 0;
    if (plugin instanceof PluginNode) {
      date = ((PluginNode)plugin).getDate();
    }
    return date > 0 && date != Long.MAX_VALUE ? DATE_FORMAT.format(new Date(date)) : null;
  }

  @Nullable
  public static String getRating(@NotNull IdeaPluginDescriptor plugin) {
    String rating = null;
    if (plugin instanceof PluginNode) {
      rating = ((PluginNode)plugin).getRating();
    }
    if (rating != null) {
      try {
        if (Double.valueOf(rating) > 0) {
          return StringUtil.trimEnd(rating, ".0");
        }
      }
      catch (NumberFormatException ignore) {
      }
    }
    return null;
  }

  @Nullable
  public static synchronized String getSize(@NotNull IdeaPluginDescriptor plugin) {
    String size = null;
    if (plugin instanceof PluginNode) {
      size = ((PluginNode)plugin).getSize();
    }
    if (!StringUtil.isEmptyOrSpaces(size)) {
      try {
        return StringUtilRt.formatFileSize(Long.parseLong(size)).toUpperCase(Locale.ENGLISH);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return null;
  }

  @NotNull
  public static String getVersion(@NotNull IdeaPluginDescriptor oldPlugin, @NotNull IdeaPluginDescriptor newPlugin) {
    return StringUtil.defaultIfEmpty(oldPlugin.getVersion(), "unknown") +
           " " + UIUtil.rightArrow() + " " +
           StringUtil.defaultIfEmpty(newPlugin.getVersion(), "unknown");
  }

  @Messages.YesNoResult
  public static int showRestartDialog() {
    return showRestartDialog(IdeBundle.message("update.notifications.title"));
  }

  @Messages.YesNoResult
  public static int showRestartDialog(@NotNull String title) {
    return showRestartDialog(title, action -> IdeBundle
      .message("ide.restart.required.message", action, ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  @Messages.YesNoResult
  public static int showRestartDialog(@NotNull String title, @NotNull Function<String, String> message) {
    String action =
      IdeBundle.message(ApplicationManager.getApplication().isRestartCapable() ? "ide.restart.action" : "ide.shutdown.action");
    return Messages
      .showYesNoDialog(message.apply(action), title, action, IdeBundle.message("ide.notnow.action"), Messages.getQuestionIcon());
  }

  public static void shutdownOrRestartApp() {
    shutdownOrRestartApp(IdeBundle.message("update.notifications.title"));
  }

  public static void shutdownOrRestartApp(@NotNull String title) {
    if (showRestartDialog(title) == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  public static void shutdownOrRestartAppAfterInstall(@NotNull String plugin) {
    String title = IdeBundle.message("update.notifications.title");
    Function<String, String> message = action -> IdeBundle
      .message("plugin.installed.ide.restart.required.message", plugin, action, ApplicationNamesInfo.getInstance().getFullProductName());

    if (showRestartDialog(title, message) == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  public static void showPluginConfigurableAndEnable(@Nullable Project project, IdeaPluginDescriptor @NotNull ... descriptors) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> {
      configurable.getPluginModel().changeEnableDisable(descriptors, true);
      configurable.select(descriptors);
    });
  }

  public static void showPluginConfigurable(@Nullable Project project, IdeaPluginDescriptor @NotNull ... descriptors) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.select(descriptors));
  }

  public static void showPluginConfigurable(@Nullable Project project, @NotNull Collection<PluginDownloader> updates) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    configurable.setInitUpdates(updates);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }

  private enum SortBySearchOption {
    Downloads, Name, Rating, Relevance, Updated
  }

  private final class MarketplaceSortByAction extends ToggleAction implements DumbAware {
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
        getTemplatePresentation().setVisible(
          parser.sortBy == null || !parser.tags.isEmpty() || !parser.vendors.isEmpty() || parser.searchQuery != null
        );
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

  private final class InstalledSearchOptionAction extends ToggleAction implements DumbAware {
    private final InstalledSearchOption myOption;
    private boolean myState;

    private InstalledSearchOptionAction(@NotNull InstalledSearchOption option) {
      super(option == InstalledSearchOption.NeedUpdate ? IdeBundle.message("plugins.configurable.update.available") : option.name());
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

    public void setState(@Nullable SearchQueryParser.Installed parser) {
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

  private final class ChangePluginStateAction extends DumbAwareAction {
    private final boolean myEnable;

    private ChangePluginStateAction(boolean enable) {
      super(enable ? IdeBundle.message("plugins.configurable.enable.all.downloaded")
                   : IdeBundle.message("plugins.configurable.disable.all.downloaded"));
      myEnable = enable;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      IdeaPluginDescriptor[] descriptors;
      PluginsGroup group = myPluginModel.getDownloadedGroup();

      if (group == null || group.ui == null) {
        ApplicationInfoImpl appInfo = (ApplicationInfoImpl)ApplicationInfo.getInstance();
        List<IdeaPluginDescriptor> descriptorList = new ArrayList<>();

        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          if (!appInfo.isEssentialPlugin(descriptor.getPluginId()) &&
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
  public static JComponent createScrollPane(@NotNull PluginsGroupComponent panel, boolean initSelection) {
    JBScrollPane pane =
      new JBScrollPane(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setBorder(JBUI.Borders.empty());
    if (initSelection) {
      panel.initialSelection();
    }
    return pane;
  }

  @NotNull
  private Collection<IdeaPluginDescriptor> getCustomRepositoryPlugins() {
    synchronized (myRepositoriesLock) {
      if (myCustomRepositoryPluginsList != null) {
        return myCustomRepositoryPluginsList;
      }
    }
    LOG.info("PluginManagerConfigurable#getCustomRepoPlugins() has been called before PluginManagerConfigurable#createMarketplaceTab()"); // XXX
    return ContainerUtil.emptyList();
  }

  @NotNull
  private Map<String, List<IdeaPluginDescriptor>> loadCustomRepositoryPlugins() {
    synchronized (myRepositoriesLock) {
      if (myCustomRepositoryPluginsMap != null) {
        return myCustomRepositoryPluginsMap;
      }
    }
    Map<PluginId, IdeaPluginDescriptor> latestCustomPluginsAsMap = new HashMap<>();
    Map<String, List<IdeaPluginDescriptor>> customRepositoryPluginsMap = new HashMap<>();
    for (String host : RepositoryHelper.getPluginHosts()) {
      try {
        if (host != null) {
          List<IdeaPluginDescriptor> descriptors = RepositoryHelper.loadPlugins(host, null);
          for (IdeaPluginDescriptor descriptor : descriptors) {
            PluginId pluginId = descriptor.getPluginId();
            IdeaPluginDescriptor savedDescriptor = latestCustomPluginsAsMap.get(pluginId);
            if (savedDescriptor == null) {
              latestCustomPluginsAsMap.put(pluginId, descriptor);
            } else {
              if (StringUtil.compareVersionNumbers(descriptor.getVersion(), savedDescriptor.getVersion()) > 0) {
                latestCustomPluginsAsMap.put(pluginId, descriptor);
              }
            }
          }
          customRepositoryPluginsMap.put(host, descriptors);
        }
      }
      catch (IOException e) {
        LOG.info(host, e);
      }
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      UpdateChecker.updateDescriptorsForInstalledPlugins(InstalledPluginsState.getInstance());
    });

    synchronized (myRepositoriesLock) {
      if (myCustomRepositoryPluginsMap == null) {
        myCustomRepositoryPluginsMap = customRepositoryPluginsMap;
        myCustomRepositoryPluginsList = latestCustomPluginsAsMap.values();
      }
      return myCustomRepositoryPluginsMap;
    }
  }

  private void addGroup(
    @NotNull List<? super PluginsGroup> groups,
    @NotNull @Nls String name,
    @NotNull String showAllQuery,
    @NotNull ThrowableNotNullFunction<? super List<IdeaPluginDescriptor>, Boolean, ? extends IOException> function
  )
    throws IOException {
    PluginsGroup group = new PluginsGroup(name);

    if (Boolean.TRUE.equals(function.fun(group.descriptors))) {
      group.rightAction =
        new LinkLabel<>(IdeBundle.message("plugins.configurable.show.all"), null, myMarketplaceTab.mySearchListener, showAllQuery);
      group.rightAction.setBorder(JBUI.Borders.emptyRight(5));
    }

    if (!group.descriptors.isEmpty()) {
      groups.add(group);
    }
  }

  private void addGroupViaLightDescriptor(
    @NotNull List<? super PluginsGroup> groups,
    @NotNull @Nls String name,
    @NotNull @NonNls String query,
    @NotNull @NonNls String showAllQuery
  ) throws IOException {
    addGroup(groups, name, showAllQuery, descriptors -> {
      List<PluginNode> pluginNodes = MarketplaceRequests.getInstance().searchPlugins(query, ITEMS_PER_GROUP * 2);
      descriptors.addAll(ContainerUtil.getFirstItems(pluginNodes, ITEMS_PER_GROUP));
      return pluginNodes.size() >= ITEMS_PER_GROUP;
    });
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return ID;
  }

  @Override
  public void disposeUIResources() {
    if (myPluginModel.toBackground()) {
      InstallPluginInfo.showRestart();
      InstalledPluginsState.getInstance().clearShutdownCallback();
    }

    myMarketplaceTab.dispose();
    myInstalledTab.dispose();

    if (myMarketplacePanel != null) {
      myMarketplacePanel.dispose();
    }
    if (myMarketplaceSearchPanel != null) {
      myMarketplaceSearchPanel.dispose();
    }

    myPluginUpdatesService.dispose();
    PluginPriceService.cancel();

    InstalledPluginsState.getInstance().runShutdownCallback();

    InstalledPluginsState.getInstance().resetChangesAppliedWithoutRestart();
  }

  @Override
  public void cancel() {
    myPluginModel.removePluginsOnCancel(myCardPanel);
  }

  @Override
  public boolean isModified() {
    return myPluginModel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPluginModel.apply(myCardPanel)) return;

    if (myPluginModel.createShutdownCallback) {
      InstalledPluginsState.getInstance()
        .setShutdownCallback(() -> ApplicationManager.getApplication().invokeLater(() -> shutdownOrRestartApp()));
    }
  }

  @Override
  public void reset() {
    myPluginModel.removePluginsOnCancel(myCardPanel);
  }

  @NotNull
  public MyPluginModel getPluginModel() {
    return myPluginModel;
  }

  public void setInitUpdates(@NotNull Collection<PluginDownloader> initUpdates) {
    myInitUpdates = initUpdates;
  }

  public void select(IdeaPluginDescriptor @NotNull ... descriptors) {
    if (myTabHeaderComponent.getSelectionTab() != INSTALLED_TAB) {
      myTabHeaderComponent.setSelectionWithEvents(INSTALLED_TAB);
    }

    if (descriptors.length == 0) {
      return;
    }

    List<ListPluginComponent> components = new ArrayList<>();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      for (UIPluginGroup group : myInstalledPanel.getGroups()) {
        ListPluginComponent component = group.findComponent(descriptor);
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
    if (StringUtil.isEmpty(option) && (myTabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB || myInstalledSearchPanel.isEmpty())) {
      return null;
    }

    return () -> {
      boolean marketplace = option != null && option.startsWith(SearchWords.TAG.getValue());
      int tabIndex = marketplace ? MARKETPLACE_TAB : INSTALLED_TAB;

      if (myTabHeaderComponent.getSelectionTab() != tabIndex) {
        myTabHeaderComponent.setSelectionWithEvents(tabIndex);
      }

      PluginsTab tab = marketplace ? myMarketplaceTab : myInstalledTab;
      tab.clearSearchPanel(option);

      if (!StringUtil.isEmpty(option)) {
        tab.showSearchPanel(option);
      }
    };
  }

  private final class InstallFromDiskAction extends DumbAwareAction {
    private InstallFromDiskAction() {super(IdeBundle.messagePointer("action.InstallFromDiskAction.text"));}

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      PluginInstaller.chooseAndInstall(myPluginModel, myCardPanel, callbackData -> {
        myPluginModel.pluginInstalledFromDisk(callbackData);

        boolean select = myInstalledPanel == null;

        if (myTabHeaderComponent.getSelectionTab() != INSTALLED_TAB) {
          myTabHeaderComponent.setSelectionWithEvents(INSTALLED_TAB);
        }

        myInstalledTab.clearSearchPanel("");

        if (select) {
          for (UIPluginGroup group : myInstalledPanel.getGroups()) {
            ListPluginComponent component = group.findComponent(callbackData.getPluginDescriptor());
            if (component != null) {
              myInstalledPanel.setSelection(component);
              break;
            }
          }
        }
      });
    }
  }
}