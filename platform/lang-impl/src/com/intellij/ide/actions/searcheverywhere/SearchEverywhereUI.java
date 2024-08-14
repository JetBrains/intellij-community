// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.accessibility.TextFieldWithListAccessibleContext;
import com.intellij.find.findInProject.FindInProjectManager;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.find.impl.SearchEverywhereItem;
import com.intellij.find.impl.TextSearchRightActionAction;
import com.intellij.find.impl.UsageAdaptersKt;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.actions.BigPopupUI;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereHeader.SETab;
import com.intellij.ide.actions.searcheverywhere.footer.ExtendedInfoComponent;
import com.intellij.ide.actions.searcheverywhere.footer.ExtendedInfoImpl;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchFieldStatisticsCollector;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchPerformanceTracker;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.ide.util.gotoByName.QuickSearchComponent;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.local.ContributorsLocalSummary;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.popup.PopupUpdateProcessorBase;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.util.Alarm;
import com.intellij.util.PlatformUtils;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.*;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.toPsi;
import static com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID;
import static com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.getReportableContributorID;
import static com.intellij.ui.dsl.listCellRenderer.BuilderKt.textListCellRenderer;

/**
 * @author Konstantin Bulenkov
 * @author Mikhail.Sokolov
 */
public final class SearchEverywhereUI extends BigPopupUI implements UiDataProvider, QuickSearchComponent {

  public static final Topic<SearchListener> SEARCH_EVENTS = Topic.create("Search events", SearchListener.class);

  public static final String SEARCH_EVERYWHERE_SEARCH_FILED_KEY = "search-everywhere-textfield"; //only for testing purposes

  static final DataKey<SearchEverywhereFoundElementInfo> SELECTED_ITEM_INFO = DataKey.create("selectedItemInfo");

  public static final int SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT = 30;
  public static final int MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT = 15;

  private static Icon getShowInFindToolWindowIcon() {
    return ExperimentalUI.isNewUI() ? AllIcons.General.OpenInToolWindow : AllIcons.General.Pin_tab;
  }

  private final SEResultsListFactory myListFactory;
  private SearchListModel myListModel;
  private final SearchEverywhereHeader myHeader;
  private String myNotFoundString;
  private final SESearcher mySearcher;
  private final MySearchListener mySearchListener = new MySearchListener();
  private final List<SearchListener> myExternalSearchListeners = new ArrayList<>();
  private ProgressIndicator mySearchProgressIndicator;
  private final SEListSelectionTracker mySelectionTracker;
  private final SearchFieldTypingListener mySearchTypingListener;
  private final HintHelper myHintHelper;
  private final SearchEverywhereMlService myMlService;
  private final @Nullable SearchEverywhereSpellingCorrector mySpellingCorrector;
  private JComponent myExtendedInfoPanel;
  private @Nullable ExtendedInfoComponent myExtendedInfoComponent;
  private final SearchListener topicPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(SEARCH_EVENTS);

  private UsagePreviewPanel myUsagePreviewPanel;
  private final List<Disposable> myUsagePreviewDisposableList = new ArrayList<>();
  private UsageViewPresentation myUsageViewPresentation;
  private static final String SPLITTER_SERVICE_KEY = "search.everywhere.splitter";

  private int prevSelectedIndex = -1;

  public SearchEverywhereUI(@Nullable Project project, List<SearchEverywhereContributor<?>> contributors) {
    this(project, contributors, s -> null);
  }

  public SearchEverywhereUI(@Nullable Project project, List<SearchEverywhereContributor<?>> contributors,
                            @NotNull Function<? super String, String> shortcutSupplier) {
    this(project, contributors, shortcutSupplier, null);
  }

  public SearchEverywhereUI(@Nullable Project project, List<SearchEverywhereContributor<?>> contributors,
                            @NotNull Function<? super String, String> shortcutSupplier,
                            @Nullable SearchEverywhereSpellingCorrector spellingCorrector) {
    super(project);

    mySpellingCorrector = spellingCorrector;

    Runnable scopeChangedCallback = () -> {
      updateSearchFieldAdvertisement();
      scheduleRebuildList(SearchRestartReason.SCOPE_CHANGED);
    };

    AnAction showInFindToolWindowAction = project == null ? null : new ShowInFindToolWindowAction();
    myHeader = new SearchEverywhereHeader(project, contributors, scopeChangedCallback,
                                          shortcutSupplier, showInFindToolWindowAction,
                                          this);

    myMlService = SearchEverywhereMlService.getInstance();

    if (Experiments.getInstance().isFeatureEnabled("search.everywhere.mixed.results")) {
      myListFactory =
        (myMlService != null && !myMlService.getShouldAllTabPrioritizeRecentFiles()) ?
        new MixedListFactory(true) : new MixedListFactory();
    }
    else {
      myListFactory = new GroupedListFactory();
    }

    if (myMlService != null) {
      myMlService.onSessionStarted(myProject, new SearchEverywhereMixedListInfo(myListFactory));
    }

    init();
    myHeader.setResultsNotifyCallback(myListModel::addNotificationElement); // should be performed after myListModel creation in init()

    myHintHelper = new HintHelper(mySearchField);

    List<SEResultsEqualityProvider> equalityProviders = SEResultsEqualityProvider.getProviders();
    SearchListener wrapperListener = createListenerWrapper();

    mySelectionTracker = new SEListSelectionTracker(myResultsList, myListModel);

    if (myMlService != null) {
      SearchListener mlListener = myMlService.buildListener(myListModel, myResultsList, mySelectionTracker);

      if (mlListener != null) {
        addSearchListener(mlListener);
      }
    }

    myExternalSearchListeners.add(topicPublisher);
    mySearcher = Experiments.getInstance().isFeatureEnabled("search.everywhere.mixed.results")
                 ? new MixedResultsSearcher(wrapperListener, run -> ApplicationManager.getApplication().invokeLater(run),
                                            equalityProviders)
                 : new GroupedResultsSearcher(wrapperListener, run -> ApplicationManager.getApplication().invokeLater(run),
                                              equalityProviders);
    addSearchListener(new SearchProcessLogger());

    initSearchActions();

    myResultsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myResultsList.addListSelectionListener(e -> {
      int[] selectedIndices = myResultsList.getSelectedIndices();
      if (selectedIndices.length > 1) {
        boolean multiSelection = Arrays.stream(selectedIndices)
          .allMatch(i -> {
            SearchEverywhereContributor<Object> contributor = myListModel.getContributorForIndex(i);
            return contributor != null && contributor.isMultiSelectionSupported();
          });
        if (!multiSelection) {
          int index = myResultsList.getLeadSelectionIndex();
          myResultsList.setSelectedIndex(index);
        }
      }
    });

    myResultsList.addListSelectionListener(mySelectionTracker);
    mySearchTypingListener = new SearchFieldTypingListener();
    mySearchField.addKeyListener(mySearchTypingListener);

    SearchPerformanceTracker performanceTracker = new SearchPerformanceTracker(() -> myHeader.getSelectedTab().getID());
    addSearchListener(performanceTracker);
    Disposer.register(this, SearchFieldStatisticsCollector.createAndStart(mySearchField, performanceTracker, myMlService, myProject));
  }

  public void addSearchListener(SearchListener listener) {
    myExternalSearchListeners.add(listener);
  }

  public void removeSearchListener(SearchListener listener) {
    myExternalSearchListeners.remove(listener);
  }

  private @NotNull SearchListener createListenerWrapper() {
    SearchListener wrapper = AdvancedSettings.getBoolean("search.everywhere.wait.for.contributors")
                             ? new WaitForContributorsListenerWrapper(mySearchListener, myListModel,
                                                                      WaitForContributorsListenerWrapper.DEFAULT_WAIT_TIMEOUT_MS,
                                                                      WaitForContributorsListenerWrapper.DEFAULT_THROTTLING_TIMEOUT_MS,
                                                                      () -> getSearchPattern())
                             : new ThrottlingListenerWrapper(mySearchListener);
    Disposer.register(this, (Disposable)wrapper);
    if (Registry.is("search.everywhere.detect.slow.contributors")) {
      wrapper = SearchListener.combine(wrapper, new SlowContributorDetector());
    }

    return wrapper;
  }

  @Override
  protected @NotNull ListCellRenderer<Object> createCellRenderer() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return (list, value, index, isSelected, cellHasFocus) -> new JPanel();
    }

    ListCellRenderer<Object> renderer = myListFactory.createListRenderer(myListModel, myHeader);

    if (myMlService != null) {
      return myMlService.wrapRenderer(renderer, myListModel);
    }
    return renderer;
  }

  @Override
  public @NotNull JBList<Object> createList() {
    myListModel = myListFactory.createModel(this::getSelectedTabID);
    addListDataListener(myListModel);
    addPreviewDataListener(myListModel);
    return myListFactory.createList(myListModel);
  }

  private void addPreviewDataListener(@NotNull AbstractListModel<Object> model) {
    model.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        updatePreviewVisibility();
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        updatePreviewVisibility();
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        updatePreviewVisibility();
      }
    });
  }

  public void toggleEverywhereFilter() {
    myHeader.toggleEverywhere();
  }

  @ApiStatus.Internal
  public void changeScope(@NotNull BiFunction<? super ScopeDescriptor, ? super List<ScopeDescriptor>, @Nullable ScopeDescriptor> processor) {
    myHeader.changeScope(processor);
  }

  public void switchToTab(@NotNull String tabID) {
    SETab selectedTab = myHeader.getTabs().stream()
      .filter(tab -> tab.getID().equals(tabID))
      .findAny()
      .orElseThrow(() -> new IllegalArgumentException(String.format("There is no such tab - %s", tabID)));
    switchToTab(selectedTab);
  }

  private void switchToTab(SETab tab) {
    boolean prevTabIsSingleContributor = myHeader.getSelectedTab().isSingleContributor();
    myHeader.switchToTab(tab);
    boolean nextTabIsSingleContributor = myHeader.getSelectedTab().isSingleContributor();

    updateSearchFieldAdvertisement();

    if (prevTabIsSingleContributor != nextTabIsSingleContributor) {
      //reset cell renderer to show/hide group titles in "All" tab
      myResultsList.setCellRenderer(myResultsList.getCellRenderer());
    }
  }

  private void updateFooter() {
    if (mySearchField == null) return;

    myExtendedInfoPanel.removeAll();
    myExtendedInfoComponent = createExtendedInfoComponent();
    if (myExtendedInfoComponent != null) {
      myExtendedInfoPanel.add(myExtendedInfoComponent.component);
    }
  }

  private void updateSearchFieldAdvertisement() {
    if (mySearchField == null) return;

    List<SearchEverywhereContributor<?>> contributors = myHeader.getSelectedTab().getContributors();
    String advertisementText = getWarning(contributors);
    if (advertisementText != null) {
      myHintHelper.setWarning(advertisementText);
      updateRightActions(contributors);
      return;
    }

    advertisementText = getAdvertisement(contributors);
    myHintHelper.setHint(advertisementText);

    updateRightActions(contributors);
  }

  private void updateRightActions(@NotNull List<? extends SearchEverywhereContributor<?>> contributors) {
    List<AnAction> actions = getRightActions(contributors);
    myHintHelper.removeRightExtensions();
    if (!actions.isEmpty()) {
      myHintHelper.setRightExtensions(actions);
    }
  }

  private @NotNull List<AnAction> getRightActions(@NotNull List<? extends SearchEverywhereContributor<?>> contributors) {
    for (SearchEverywhereContributor<?> contributor : contributors) {
      if (!Objects.equals(getSelectedTabID(), contributor.getSearchProviderId()) ||
          !(contributor instanceof SearchFieldActionsContributor)) {
        continue;
      }

      Function1<AnAction, Unit> registerShortcut = action -> {
        ShortcutSet shortcut = ActionUtil.getMnemonicAsShortcut(action);
        if (shortcut != null) {
          action.setShortcutSet(shortcut);
          action.registerCustomShortcutSet(shortcut, this);
        }
        return Unit.INSTANCE;
      };

      return ((SearchFieldActionsContributor)contributor).createRightActions(registerShortcut, () -> {
        scheduleRebuildList(SearchRestartReason.TEXT_SEARCH_OPTION_CHANGED);
      });
    }
    return ContainerUtil.emptyList();
  }

  @RequiresReadLock
  private @Nls @Nullable String getWarning(List<SearchEverywhereContributor<?>> contributors) {
    if (myProject == null) return null;

    boolean isDumb = DumbService.isDumb(myProject);
    boolean isIncomplete = !myProject.getService(IncompleteDependenciesService.class).getState().isComplete();
    if (!isDumb && !isIncomplete) return null;

    boolean containsPSIContributors = ContainerUtil.exists(contributors, c -> c instanceof AbstractGotoSEContributor ||
                                                                              c instanceof PSIPresentationBgRendererWrapper);
    if (!containsPSIContributors) return null;

    return isDumb
           ? IdeBundle.message("dumb.mode.results.might.be.incomplete")
           : IdeBundle.message("incomplete.mode.results.might.be.incomplete");
  }

  private static @Nls @Nullable String getAdvertisement(List<? extends SearchEverywhereContributor<?>> contributors) {

    boolean commandsSupported = ContainerUtil.exists(contributors, contributor -> !contributor.getSupportedCommands().isEmpty());
    if (commandsSupported) {
      return IdeBundle.message("searcheverywhere.textfield.hint", SearchTopHitProvider.getTopHitAccelerator());
    }

    List<String> advertisements = ContainerUtil.mapNotNull(contributors, c -> c.getAdvertisement());

    return advertisements.isEmpty() ? null : advertisements.get(new Random().nextInt(advertisements.size()));
  }

  public String getSelectedTabID() {
    return myHeader.getSelectedTab().getID();
  }

  public @Nullable Object getSelectionIdentity() {
    Object value = myResultsList.getSelectedValue();
    return value == null ? null : Objects.hashCode(value);
  }

  @Override
  public void dispose() {
    stopSearching();
    mySearchProgressIndicator = null;
    myListModel.clear();
    myExternalSearchListeners.remove(topicPublisher);

    if (myMlService != null) {
      myMlService.onDialogClose();
    }

    for (Disposable disposable : myUsagePreviewDisposableList) {
      Disposer.dispose(disposable);
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    List<SearchEverywhereFoundElementInfo> selection = getSelectedInfos();
    SearchEverywhereFoundElementInfo onlyItem = ContainerUtil.getOnlyItem(selection);

    sink.set(PlatformDataKeys.PREDEFINED_TEXT, getSearchPattern());
    sink.set(CommonDataKeys.PROJECT, myProject);
    sink.set(SELECTED_ITEM_INFO, onlyItem);
    sink.set(PlatformCoreDataKeys.SELECTED_ITEM, onlyItem == null ? null : onlyItem.getElement());
    sink.set(PlatformCoreDataKeys.SELECTED_ITEMS,
             ContainerUtil.map2Array(selection, Object.class, SearchEverywhereFoundElementInfo::getElement));
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
      List<PsiElement> list =
        ContainerUtil.mapNotNull(selection, o -> (PsiElement)getDataFromElementInfo(CommonDataKeys.PSI_ELEMENT.getName(), o));
      return list.isEmpty() ? null : list.toArray(PsiElement.EMPTY_ARRAY);
    });
    sink.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY, () -> {
      List<VirtualFile> list =
        ContainerUtil.mapNotNull(selection, o -> (VirtualFile)getDataFromElementInfo(CommonDataKeys.VIRTUAL_FILE.getName(), o));
      return list.isEmpty() ? null : list.toArray(VirtualFile.EMPTY_ARRAY);
    });
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY, () -> {
      List<Navigatable> list = ContainerUtil.mapNotNull(selection, o -> {
        Navigatable navigatable = (Navigatable)getDataFromElementInfo(CommonDataKeys.NAVIGATABLE.getName(), o);
        if (navigatable != null) return navigatable;
        // make F4 work on multi-selection
        Object psi = getDataFromElementInfo(CommonDataKeys.PSI_ELEMENT.getName(), o);
        return psi instanceof Navigatable ? (Navigatable)psi : null;
      });
      return list.isEmpty() ? null : list.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
    });
    if (onlyItem != null) {
      sink.set(PlatformCoreDataKeys.BGT_DATA_PROVIDER, dataId -> {
        return getDataFromElementInfo(dataId, onlyItem);
      });
    }
  }

  private static @Nullable Object getDataFromElementInfo(@NotNull String dataId, @NotNull SearchEverywhereFoundElementInfo info) {
    //noinspection unchecked
    SearchEverywhereContributor<Object> contributor = (SearchEverywhereContributor<Object>)info.getContributor();
    if (contributor == null) return null;

    return contributor.getDataForItem(info.getElement(), dataId);
  }

  @NotNull List<SearchEverywhereFoundElementInfo> getSelectedInfos() {
    return Arrays.stream(myResultsList.getSelectedIndices())
      .mapToObj(myListModel::getRawFoundElementAt)
      .filter(o -> o.getElement() != SearchListModel.MORE_ELEMENT
                   && !(o.getElement() instanceof SearchListModel.ResultsNotificationElement))
      .collect(Collectors.toList());
  }

  public List<SearchEverywhereFoundElementInfo> getFoundElementsInfo() {
    return myListModel.getFoundElementsInfo();
  }

  @Override
  public void registerHint(@NotNull JBPopup h) {
    if (myHint != null && myHint.isVisible() && myHint != h) {
      myHint.cancel();
    }
    myHint = h;
  }

  @Override
  public void unregisterHint() {
    myHint = null;
  }

  private void hideHint() {
    if (myHint != null && myHint.isVisible()) {
      myHint.cancel();
    }
  }

  private void updateHint(Object element) {
    if (myHint == null || !myHint.isVisible()) return;
    final PopupUpdateProcessorBase updateProcessor = myHint.getUserData(PopupUpdateProcessorBase.class);
    if (updateProcessor != null) {
      updateProcessor.updatePopup(element);
    }
  }

  @Override
  protected @NotNull JComponent createHeader() {
    return myHeader.getComponent();
  }

  @Override
  protected @NlsContexts.PopupAdvertisement String @NotNull [] getInitialHints() {
    return new String[]{
      IdeBundle.message("searcheverywhere.open.in.split.shortcuts.hint",
                        KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT)),
      IdeBundle.message("searcheverywhere.open.in.new.window.shortcuts.hint",
                        KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_EDIT_SOURCE_IN_NEW_WINDOW)),
      IdeBundle.message("searcheverywhere.history.shortcuts.hint",
                        KeymapUtil.getKeystrokeText(SearchTextField.ALT_SHOW_HISTORY_KEYSTROKE),
                        KeymapUtil.getKeystrokeText(SearchTextField.SHOW_HISTORY_KEYSTROKE))};
  }

  @Override
  protected @NotNull @Nls String getAccessibleName() {
    return IdeBundle.message("searcheverywhere.accessible.name");
  }

  @Override
  protected @NotNull ExtendableTextField createSearchField() {
    SearchField res = new SearchField() {
      @Override
      public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
          accessibleContext = new TextFieldWithListAccessibleContext(this, myResultsList.getAccessibleContext());
        }
        return accessibleContext;
      }
    };

    ExtendableTextComponent.Extension leftExt = new ExtendableTextComponent.Extension() {
      @Override
      public Icon getIcon(boolean hovered) {
        return isExtendedInfoEnabled() ? AllIcons.Actions.SearchWithHistory : AllIcons.Actions.Search;
      }

      @Override
      public boolean isIconBeforeText() {
        return true;
      }

      @Override
      public int getIconGap() {
        return JBUIScale.scale(ExperimentalUI.isNewUI() ? 6 : 10);
      }

      @Override
      public Runnable getActionOnClick() {
        if (!isExtendedInfoEnabled()) return null;

        Rectangle bounds = ((TextFieldWithPopupHandlerUI)mySearchField.getUI()).getExtensionIconBounds(this);
        Point point = bounds.getLocation();
        point.y += bounds.width + JBUIScale.scale(2);
        RelativePoint relativePoint = new RelativePoint(mySearchField, point);
        return () -> showPopup(relativePoint);
      }
    };
    res.addExtension(leftExt);
    res.putClientProperty(SEARCH_EVERYWHERE_SEARCH_FILED_KEY, true);
    res.setLayout(new BorderLayout());
    return res;
  }

  private void showPopup(@NotNull RelativePoint relativePoint) {
    List<String> items = ((SearchEverywhereManagerImpl)SearchEverywhereManager.getInstance(myProject)).getHistoryItems();
    if (items.isEmpty()) return;

    JBPopupFactory.getInstance().createPopupChooserBuilder(items)
      .setMovable(false)
      .setRequestFocus(true)
      .setItemChosenCallback(text -> {
        mySearchField.setText(text);
        mySearchField.selectAll();
      })
      .setRenderer(textListCellRenderer((@Nls var s) -> s))
      .createPopup()
      .show(relativePoint);
  }

  @Override
  protected JPanel createSuggestionsPanel() {
    if (myProject == null) return super.createSuggestionsPanel();

    JScrollPane resultScroll = createListPane();

    myUsageViewPresentation = new UsageViewPresentation();
    myUsagePreviewPanel = new UsagePreviewPanel(myProject, myUsageViewPresentation, true) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(myHeader.getComponent().getWidth(), Math.max(getHeight(), getLineHeight() * 10));
      }

      @Override
      protected void onEditorCreated(@NotNull Editor editor) {
        if (editor instanceof EditorEx) {
          ((EditorEx)editor).setRendererMode(true);
        }

        editor.getContentComponent().addFocusListener(new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            onFocusLost(e);
          }
        });
      }
    };
    Disposer.register(this, myUsagePreviewPanel);

    myUsagePreviewPanel.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);

    updatePreviewVisibility();

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
      SETabSwitcherListener.Companion.getSE_TAB_TOPIC(), new SETabSwitcherListener() {
        @Override
        public void tabSwitched(@NotNull SETabSwitcherListener.SETabSwitchedEvent event) {
          updatePreviewVisibility(event.getNewTab());
        }
      });

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
      SEHeaderActionListener.Companion.getSE_HEADER_ACTION_TOPIC(), new SEHeaderActionListener() {
        @Override
        public void performed(@NotNull SEHeaderActionListener.SearchEverywhereActionEvent event) {
          if (event.getActionID().equals(PreviewActionKt.PREVIEW_ACTION_ID)) {
            updatePreviewVisibility();
          }
        }
      });

    OnePixelSplitter splitter = new OnePixelSplitter(true, .33f);
    splitter.setSplitterProportionKey(SPLITTER_SERVICE_KEY);
    splitter.getDivider().setBackground(OnePixelDivider.BACKGROUND);
    splitter.setFirstComponent(resultScroll);
    splitter.setSecondComponent(myUsagePreviewPanel);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(splitter, BorderLayout.CENTER);

    return createFooterPanel(panel);
  }

  private void updatePreviewVisibility() {
    updatePreviewVisibility(myHeader.getSelectedTab());
  }

  private void updatePreviewVisibility(@NotNull SETab tab) {
    boolean noProviders = !hasPreviewProvider(tab);
    if (myUsagePreviewPanel != null) {
      myUsagePreviewPanel.setVisible(isPreviewEnabled() && isPreviewActive() && !noProviders && !myResultsList.isEmpty());
    }
  }

  @Override
  protected @NotNull JPanel createFooterPanel(@NotNull JPanel panel) {
    if (!isExtendedInfoEnabled()) return super.createFooterPanel(panel);

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(
      SETabSwitcherListener.Companion.getSE_TAB_TOPIC(), new SETabSwitcherListener() {
        @Override
        public void tabSwitched(@NotNull SETabSwitcherListener.SETabSwitchedEvent event) {
          updateFooter();
        }
      });

    myExtendedInfoPanel = new JPanel(new BorderLayout());
    myExtendedInfoComponent = createExtendedInfoComponent();
    if (myExtendedInfoComponent != null) {
      myExtendedInfoPanel.add(myExtendedInfoComponent.component);
    }
    panel.add(myExtendedInfoPanel, BorderLayout.SOUTH);

    return panel;
  }

  private @Nullable ExtendedInfoComponent createExtendedInfoComponent() {
    SETab tab = myHeader.getSelectedTab();

    com.intellij.util.Function<SearchEverywhereContributor<?>, @Nullable ExtendedInfo> extendedInfoFunction =
      it -> it instanceof SearchEverywhereExtendedInfoProvider
            ? ((SearchEverywhereExtendedInfoProvider)it).createExtendedInfo()
            : null;

    boolean isExtendedInfoAvailable = !ContainerUtil.mapNotNull(tab.getContributors(), extendedInfoFunction).isEmpty();
    return ALL_CONTRIBUTORS_GROUP_ID.equals(tab.getID()) || isExtendedInfoAvailable
           ? new ExtendedInfoComponent(myProject, new ExtendedInfoImpl(tab.getContributors()))
           : null;
  }

  @Override
  protected void installScrollingActions() {
    ScrollingUtil.installMoveUpAction(myResultsList, getSearchField());
    ScrollingUtil.installMoveDownAction(myResultsList, getSearchField());
  }

  private static final long REBUILD_LIST_DELAY = 100;
  private final Alarm rebuildListAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  private void scheduleRebuildList(SearchRestartReason reason) {
    if (!rebuildListAlarm.isDisposed() && rebuildListAlarm.getActiveRequestCount() == 0) {
      long delay = StringUtil.isEmpty(getSearchPattern()) ? 0 : REBUILD_LIST_DELAY;
      rebuildListAlarm.addRequest(() -> rebuildList(reason), delay);
    }
  }

  private void rebuildList(SearchRestartReason reason) {
    ThreadingAssertions.assertEventDispatchThread();

    stopSearching();

    myResultsList.setEmptyText(IdeBundle.message("label.choosebyname.searching"));
    String rawPattern = getSearchPattern();
    updateViewType(rawPattern.isEmpty() ? ViewType.SHORT : ViewType.FULL);
    String namePattern = myHeader.getSelectedTab().isSingleContributor()
                         ? myHeader.getSelectedTab().getContributors().get(0).filterControlSymbols(rawPattern)
                         : rawPattern;

    MinusculeMatcher matcher =
      NameUtil.buildMatcherWithFallback("*" + rawPattern, "*" + namePattern, NameUtil.MatchingCaseSensitivity.NONE);
    MatcherHolder.associateMatcher(myResultsList, matcher);

    Map<SearchEverywhereContributor<?>, Integer> contributorsMap = new HashMap<>();

    List<SearchEverywhereContributor<?>> contributors = myHeader.getSelectedTab().getContributors();
    int limit = contributors.size() > 1 ? MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT : SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT;
    contributors.forEach(c -> contributorsMap.put(c, limit));

    if (myProject != null) {
      contributors = DumbService.getInstance(myProject).filterByDumbAwareness(contributorsMap.keySet());
      if (contributors.isEmpty() && DumbService.isDumb(myProject)) {
        DumbModeBlockedFunctionalityCollector.INSTANCE.logFunctionalityBlocked(myProject, DumbModeBlockedFunctionality.SearchEverywhere);
        myResultsList.setEmptyText(IdeBundle.message("searcheverywhere.indexing.mode.not.supported",
                                                     myHeader.getSelectedTab().getName(),
                                                     ApplicationNamesInfo.getInstance().getFullProductName()));
        myListModel.clear();
        return;
      }
      if (contributors.size() != contributorsMap.size()) {
        myResultsList.setEmptyText(IdeBundle.message("searcheverywhere.indexing.incomplete.results",
                                                     myHeader.getSelectedTab().getName(),
                                                     ApplicationNamesInfo.getInstance().getFullProductName()));
      }
    }

    String tabId = myHeader.getSelectedTab().getID();
    if (myMlService != null) {
      myMlService.onSearchRestart(
        myProject, tabId, reason,
        mySearchTypingListener.mySymbolKeysTyped, mySearchTypingListener.myBackspacesTyped, namePattern,
        () -> myListModel.getFoundElementsInfo(),
        getSelectedSearchScope(myHeader.getSelectedTab()), myHeader.isEverywhere()
      );
    }

    myListModel.expireResults();
    contributors.forEach(contributor -> myListModel.setHasMore(contributor, false));

    List<SearchEverywhereFoundElementInfo> completionElements = AutoCompletionProvider.getCompletionElements(contributors, mySearchField);
    myListModel.addElements(completionElements);

    String commandPrefix = SearchTopHitProvider.getTopHitAccelerator();
    if (rawPattern.startsWith(commandPrefix)) {
      String typedCommand = rawPattern.split(" ")[0].substring(commandPrefix.length());
      List<SearchEverywhereCommandInfo> commands = getCommandsForCompletion(contributors, typedCommand);

      if (!commands.isEmpty()) {
        if (rawPattern.contains(" ")) {
          Set<String> commandContributorsIds = commands.stream()
            .map(info -> info.getContributor().getSearchProviderId())
            .collect(Collectors.toSet());
          contributorsMap.keySet().removeIf(c -> !commandContributorsIds.contains(c.getSearchProviderId()));
        }
        else {
          myListModel.clear();
          List<SearchEverywhereFoundElementInfo> lst = ContainerUtil.map(
            commands, command -> new SearchEverywhereFoundElementInfo(command, 0, myStubCommandContributor));
          myListModel.addElements(lst);
          ScrollingUtil.ensureSelectionExists(myResultsList);
        }
      }
    }

    addSpellingCorrectionSuggestionIfAvailable(tabId, rawPattern);

    myHintHelper.setSearchInProgress(StringUtil.isNotEmpty(getSearchPattern()));
    mySearchProgressIndicator = mySearcher.search(contributorsMap, rawPattern);
  }

  private void initSearchActions() {
    MouseAdapter listMouseListener = new MouseAdapter() {
      private int currentDescriptionIndex = -1;

      @Override
      public void mouseClicked(MouseEvent e) {
        onMouseClicked(e);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        int index = myResultsList.locationToIndex(e.getPoint());
        indexChanged(index);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        int index = myResultsList.getSelectedIndex();
        indexChanged(index);
      }

      private void indexChanged(int index) {
        if (index != currentDescriptionIndex) {
          currentDescriptionIndex = index;
          showDescriptionForIndex(index);
        }
      }
    };
    myResultsList.addMouseMotionListener(listMouseListener);
    myResultsList.addMouseListener(listMouseListener);

    ScrollingUtil.redirectExpandSelection(myResultsList, mySearchField);

    Consumer<AnActionEvent> nextTabAction = e -> {
      myHeader.switchToNextTab();
      triggerTabSwitched(e);
    };
    Consumer<AnActionEvent> prevTabAction = e -> {
      myHeader.switchToPrevTab();
      triggerTabSwitched(e);
    };

    registerAction(SearchEverywhereActions.AUTOCOMPLETE_COMMAND, CompleteCommandAction::new);
    registerAction(SearchEverywhereActions.SWITCH_TO_NEXT_TAB, nextTabAction);
    registerAction(SearchEverywhereActions.SWITCH_TO_PREV_TAB, prevTabAction);
    registerAction(IdeActions.ACTION_NEXT_TAB, nextTabAction);
    registerAction(IdeActions.ACTION_PREVIOUS_TAB, prevTabAction);
    registerAction(IdeActions.ACTION_SWITCHER, e -> {
      if (e.getInputEvent().isShiftDown()) {
        myHeader.switchToPrevTab();
      }
      else {
        myHeader.switchToNextTab();
      }
      triggerTabSwitched(e);
    });
    registerAction(SearchEverywhereActions.NAVIGATE_TO_NEXT_GROUP, e -> {
      scrollList(true);
      SearchEverywhereUsageTriggerCollector.GROUP_NAVIGATE.log(myProject, e);
    });
    registerAction(SearchEverywhereActions.NAVIGATE_TO_PREV_GROUP, e -> {
      scrollList(false);
      SearchEverywhereUsageTriggerCollector.GROUP_NAVIGATE.log(myProject, e);
    });
    registerSelectItemAction();

    AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(__ -> sendStatisticsAndClose())
      .registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), this);

    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        String newSearchString = getSearchPattern();
        if (myNotFoundString != null) {
          boolean newPatternContainsPrevious = myNotFoundString.length() > 1 && newSearchString.contains(myNotFoundString);
          if (myHeader.canSetEverywhere() && myHeader.isEverywhere() && !newPatternContainsPrevious) {
            myNotFoundString = null;
            myHeader.autoSetEverywhere(false);
            return;
          }
        }

        scheduleRebuildList(SearchRestartReason.TEXT_CHANGED);
      }
    });

    myResultsList.addListSelectionListener(e -> {
      Object selectedValue = myResultsList.getSelectedValue();
      if (selectedValue != null && myHint != null && myHint.isVisible()) {
        updateHint(selectedValue);
      }

      showDescriptionForIndex(myResultsList.getSelectedIndex());
      if (isExtendedInfoEnabled()) {
        if (selectedValue != null && myExtendedInfoComponent != null) {
          myExtendedInfoComponent.updateElement(selectedValue, this);
        }
      }
    });

    myResultsList.addListSelectionListener(new ListSelectionListener() {
      private Object currentValue = null;

      @Override
      public void valueChanged(ListSelectionEvent e) {
        int selectedIndex = myResultsList.getSelectedIndex();
        if (prevSelectedIndex != -1 &&
            selectedIndex != -1 &&
            myListModel.getElementAt(selectedIndex) instanceof SearchListModel.ResultsNotificationElement) {
          int newIndex;
          int listSize = myListModel.getSize();
          if (prevSelectedIndex == selectedIndex + 1) {
            newIndex = (selectedIndex - 1 + listSize) % listSize;
          }
          else if (prevSelectedIndex == (selectedIndex - 1 + listSize) % listSize) {
            newIndex = selectedIndex + 1;
          }
          else {
            newIndex = prevSelectedIndex;
          }
          myResultsList.setSelectedIndex(newIndex);
          ScrollingUtil.ensureIndexIsVisible(myResultsList, newIndex, 0);
          return;
        }
        else {
          prevSelectedIndex = selectedIndex;
        }
        Object newValue = myResultsList.getSelectedValue();
        if (isPreviewEnabled() && myProject != null && newValue != null && newValue != currentValue && myUsagePreviewPanel != null) {
          schedulePreview(newValue);
          currentValue = newValue;
        }
      }
    });

    MessageBusConnection busConnection = myProject != null
                                         ? myProject.getMessageBus().connect(this)
                                         : ApplicationManager.getApplication().getMessageBus().connect(this);

    busConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        ApplicationManager.getApplication().invokeLater(() -> {
          updateSearchFieldAdvertisement();
          scheduleRebuildList(SearchRestartReason.EXIT_DUMB_MODE);
        });
      }
    });
    (myProject == null ? busConnection : ApplicationManager.getApplication().getMessageBus().connect(this))
      .subscribe(ProgressWindow.TOPIC, pw -> Disposer.register(pw, () -> myResultsList.repaint()));

    mySearchField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        onFocusLost(e);
      }
    });
  }

  private void onFocusLost(FocusEvent e) {
    if (StartupUiUtil.isWaylandToolkit()) {
      // In Wayland focus is always lost when the window is being moved.
      return;
    }
    if(ApplicationManagerEx.isInIntegrationTest()){
      return;
    }
    Component oppositeComponent = e.getOppositeComponent();
    if (!isHintComponent(oppositeComponent) && !UIUtil.haveCommonOwner(this, oppositeComponent)) {
      sendStatisticsAndClose();
    }
  }

  private void schedulePreview(@NotNull Object selectedValue) {
    new Task.Backgroundable(myProject, IdeBundle.message("search.everywhere.preview.showing"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        UsageInfo usageInfo = ReadAction.compute(() -> {
          return findFirstChild();
        });

        List<UsageInfo2UsageAdapter> usages = new ArrayList<>();
        if (usageInfo != null) {
          usages.add(new UsageInfo2UsageAdapter(usageInfo));
        }
        else {
          if (selectedValue instanceof UsageInfo2UsageAdapter) {
            usages.add((UsageInfo2UsageAdapter)selectedValue);
          }
          else if (selectedValue instanceof SearchEverywhereItem) {
            usages.add(((SearchEverywhereItem)selectedValue).getUsage());
          }
        }

        if (myProject != null) {
          UsageAdaptersKt.getUsageInfo(usages, myProject).thenAccept(infos -> {
            List<UsageInfo> usageInfos = !infos.isEmpty() ? infos : null;
            ReadAction.nonBlocking(() -> UsagePreviewPanel.isOneAndOnlyOnePsiFileInUsages(usageInfos))
              .finishOnUiThread(ModalityState.nonModal(), isOneAndOnlyOnePsiFileInUsages -> {
                myUsagePreviewPanel.updateLayout(myProject, usageInfos);
              })
              .coalesceBy(this)
              .submit(AppExecutorUtil.getAppExecutorService());
          }).exceptionally(throwable -> {
            Logger.getInstance(SearchEverywhereUI.class).error(throwable);
            return null;
          });
        }
      }

      private @Nullable UsageInfo findFirstChild() {
        if (myProject == null) return null;

        PsiElement psiElement = toPsi(selectedValue);
        if (psiElement == null || !psiElement.isValid()) return null;

        PsiFile psiFile = psiElement instanceof PsiFile ? (PsiFile)psiElement : null;
        if (psiFile == null) {
          if (psiElement instanceof PsiFileSystemItem pfsi) {
            VirtualFile vFile = pfsi.getVirtualFile();
            PsiFile file = vFile == null ? null : psiElement.getManager().findFile(vFile);
            if (file != null) {
              return new UsageInfo(file, 0, 0, true);
            }
          }
          return new UsageInfo(psiElement);
        }

        StructureViewBuilder structureViewBuilder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psiFile);
        if (!(structureViewBuilder instanceof TreeBasedStructureViewBuilder)) return new UsageInfo(psiElement);

        @NotNull StructureViewModel structureViewModel =
          ((TreeBasedStructureViewBuilder)structureViewBuilder).createStructureViewModel(null);
        myUsagePreviewDisposableList.add(new Disposable() {
          @Override
          public void dispose() {
            Disposer.dispose(structureViewModel);
          }
        });

        TreeElement firstChild = ContainerUtil.getFirstItem(Arrays.stream(structureViewModel.getRoot().getChildren()).toList());
        if (!(firstChild instanceof StructureViewTreeElement)) return new UsageInfo(psiFile);

        Object firstChildElement = ((StructureViewTreeElement)firstChild).getValue();
        if (!(firstChildElement instanceof PsiElement)) return new UsageInfo(psiFile);

        return new UsageInfo((PsiElement)firstChildElement);
      }
    }.queue();
  }

  static boolean isExtendedInfoEnabled() {
    return Registry.is("search.everywhere.footer.extended.info") || ApplicationManager.getApplication().isInternal();
  }

  static boolean isPreviewEnabled() {
    return PreviewExperiment.isExperimentEnabled() && !PlatformUtils.isJetBrainsClient();
  }

  private static boolean isPreviewActive() {
    return UISettings.getInstance().getShowPreviewInSearchEverywhere();
  }

  /**
   * Returns selected search scope for a single-contributor scope-supporting tab, null for any other type of tab.
   */
  private static @Nullable ScopeDescriptor getSelectedSearchScope(@NotNull SETab tab) {
    if (tab.isSingleContributor() && tab.getContributors().get(0) instanceof ScopeSupporting tabContributor) {
      return tabContributor.getScope();
    }
    return null;
  }

  private void showDescriptionForIndex(int index) {
    if (index < 0 || myListModel.isMoreElement(index)
        || myListModel.getElementAt(index) instanceof SearchListModel.ResultsNotificationElement) {
      return;
    }

    if (Registry.is("search.everywhere.show.weights")) {
      @NlsSafe String weight = Integer.toString(myListModel.getWeightAt(index));
      ActionMenu.showDescriptionInStatusBar(true, myResultsList, weight);
      return;
    }

    if (UISettings.getInstance().getShowStatusBar()) {
      SearchEverywhereContributor<Object> contributor = myListModel.getContributorForIndex(index);
      Object element = myListModel.getElementAt(index);

      ReadAction.nonBlocking(() -> {
          //noinspection ConstantConditions
          return contributor.getItemDescription(element);
        })
        .expireWith(this)
        .coalesceBy(this)
        .finishOnUiThread(ModalityState.any(), (@NlsSafe var data) -> {
          if (data != null) {
            ActionMenu.showDescriptionInStatusBar(true, myResultsList, data);
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
  }

  private void addSpellingCorrectionSuggestionIfAvailable(@NotNull String tabId, @NotNull String query) {
    if (mySpellingCorrector == null || !mySpellingCorrector.isAvailableInTab(tabId)) return;

    var spellCheckResult = mySpellingCorrector.checkSpellingOf(query);
    if (spellCheckResult instanceof SearchEverywhereSpellCheckResult.Correction correction) {
      SearchEverywhereFoundElementInfo elementInfo;
      if (myMlService != null) {
        elementInfo = myMlService.createFoundElementInfo(new SearchEverywhereSpellingCorrectorContributor(mySearchField),
                                                         correction,
                                                         Integer.MAX_VALUE);
      }
      else {
        elementInfo = new SearchEverywhereFoundElementInfo(correction,
                                                           Integer.MAX_VALUE,
                                                           new SearchEverywhereSpellingCorrectorContributor(mySearchField));
      }
      myListModel.addElements(Collections.singletonList(elementInfo));
    }
  }

  private void registerAction(String actionID, Supplier<? extends AnAction> actionSupplier) {
    AnAction anAction = ActionManager.getInstance().getAction(actionID);
    if (anAction == null) return;
    ShortcutSet shortcuts = anAction.getShortcutSet();
    actionSupplier.get().registerCustomShortcutSet(shortcuts, this, this);
  }

  private void registerAction(String actionID, Consumer<? super AnActionEvent> action) {
    registerAction(actionID, () -> DumbAwareAction.create(action::accept));
  }

  // when user adds shortcut for "select item" we should add shortcuts
  // with all possible modifiers (Ctrl, Shift, Alt, etc.)
  private void registerSelectItemAction() {
    int[] allowedModifiers = new int[]{
      0,
      InputEvent.SHIFT_MASK,
      InputEvent.CTRL_MASK,
      InputEvent.META_MASK,
      InputEvent.ALT_MASK
    };

    ShortcutSet selectShortcuts = ActionManager.getInstance().getAction(SearchEverywhereActions.SELECT_ITEM).getShortcutSet();
    Collection<KeyboardShortcut> keyboardShortcuts = ContainerUtil.filterIsInstance(selectShortcuts.getShortcuts(), KeyboardShortcut.class);

    for (int modifiers : allowedModifiers) {
      Collection<Shortcut> newShortcuts = new ArrayList<>();
      for (KeyboardShortcut shortcut : keyboardShortcuts) {
        boolean hasSecondStroke = shortcut.getSecondKeyStroke() != null;
        KeyStroke originalStroke = hasSecondStroke ? shortcut.getSecondKeyStroke() : shortcut.getFirstKeyStroke();

        if ((originalStroke.getModifiers() & modifiers) != 0) continue;

        KeyStroke newStroke = KeyStroke.getKeyStroke(originalStroke.getKeyCode(), originalStroke.getModifiers() | modifiers);
        newShortcuts.add(hasSecondStroke
                         ? new KeyboardShortcut(shortcut.getFirstKeyStroke(), newStroke)
                         : new KeyboardShortcut(newStroke, null));
      }
      if (newShortcuts.isEmpty()) continue;

      ShortcutSet newShortcutSet = new CustomShortcutSet(newShortcuts.toArray(Shortcut.EMPTY_ARRAY));
      DumbAwareAction.create(event -> {
        int[] indices = myResultsList.getSelectedIndices();
        elementsSelected(indices, modifiers);
      }).registerCustomShortcutSet(newShortcutSet, this, this);
    }
  }

  private void triggerTabSwitched(AnActionEvent e) {
    String id = myHeader.getSelectedTab().getReportableID();

    SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(myProject,
                                                           SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(id),
                                                           EventFields.InputEventByAnAction.with(e));
  }

  private void scrollList(boolean down) {
    int currentIndex = myResultsList.getSelectedIndex();
    int newIndex = myListModel.getIndexToScroll(currentIndex, down);

    if (newIndex != currentIndex) {
      myResultsList.setSelectedIndex(newIndex);
      ScrollingUtil.ensureIndexIsVisible(myResultsList, newIndex, 0);
    }
  }

  private Optional<SearchEverywhereCommandInfo> getSelectedCommand(String typedCommand) {
    int index = myResultsList.getSelectedIndex();
    if (index < 0) return Optional.empty();

    SearchEverywhereContributor contributor = myListModel.getContributorForIndex(index);
    if (contributor != myStubCommandContributor) return Optional.empty();

    SearchEverywhereCommandInfo selectedCommand = (SearchEverywhereCommandInfo)myListModel.getElementAt(index);
    return selectedCommand.getCommand().contains(typedCommand) ? Optional.of(selectedCommand) : Optional.empty();
  }

  private static @NotNull List<SearchEverywhereCommandInfo> getCommandsForCompletion(Collection<? extends SearchEverywhereContributor<?>> contributors,
                                                                                     String enteredCommandPart) {
    Comparator<SearchEverywhereCommandInfo> cmdComparator = (cmd1, cmd2) -> {
      String cmdName1 = cmd1.getCommand();
      String cmdName2 = cmd2.getCommand();
      if (!enteredCommandPart.isEmpty()) {
        if (cmdName1.startsWith(enteredCommandPart) && !cmdName2.startsWith(enteredCommandPart)) return -1;
        if (!cmdName1.startsWith(enteredCommandPart) && cmdName2.startsWith(enteredCommandPart)) return 1;
      }

      return String.CASE_INSENSITIVE_ORDER.compare(cmdName1, cmd2.getCommand());
    };

    return contributors.stream()
      .flatMap(contributor -> contributor.getSupportedCommands().stream())
      .filter(command -> command.getCommand().contains(enteredCommandPart))
      .sorted(cmdComparator)
      .collect(Collectors.toList());
  }

  private void onMouseClicked(@NotNull MouseEvent e) {
    boolean multiSelectMode = e.isShiftDown() || UIUtil.isControlKeyDown(e);
    boolean isPreviewDoubleClick = !isPreviewActive() || !hasPreviewProvider(myHeader.getSelectedTab()) || e.getClickCount() == 2;
    int selectedIndex = myResultsList.locationToIndex(e.getPoint());
    int listSize = myListModel.getSize();
    if (selectedIndex > 0 && selectedIndex < listSize
        && myListModel.getElementAt(selectedIndex) instanceof SearchListModel.ResultsNotificationElement) {
      if (prevSelectedIndex == (selectedIndex - 1 + listSize) % listSize) {
        myResultsList.setSelectedIndex(selectedIndex + 1);
        ScrollingUtil.ensureIndexIsVisible(myResultsList, selectedIndex + 1, 0);
      }
      else if (prevSelectedIndex == selectedIndex + 1) {
        int newIndex = (selectedIndex - 1 + listSize) % listSize;
        myResultsList.setSelectedIndex(newIndex);
        ScrollingUtil.ensureIndexIsVisible(myResultsList, newIndex, 0);
      }
      return;
    }
    if (e.getButton() == MouseEvent.BUTTON1 && !multiSelectMode && isPreviewDoubleClick) {
      e.consume();
      final int i = myResultsList.locationToIndex(e.getPoint());
      if (i > -1) {
        myResultsList.setSelectedIndex(i);
        elementsSelected(new int[]{i}, e.getModifiers());
      }
    }
  }

  private static boolean hasPreviewProvider(@NotNull SETab tab) {
    return !ContainerUtil.filterIsInstance(tab.getContributors(), SearchEverywherePreviewProvider.class).isEmpty();
  }

  private boolean isHintComponent(Component component) {
    if (myHint != null && !myHint.isDisposed() && component != null) {
      return SwingUtilities.isDescendingFrom(component, myHint.getContent());
    }
    return false;
  }

  @ApiStatus.Experimental
  public void selectFirst() {
    elementsSelected(new int[]{0}, 0);
  }

  private void elementsSelected(int[] indexes, int modifiers) {
    stopSearching();
    if (indexes.length == 1 && myListModel.isMoreElement(indexes[0])) {
      SearchEverywhereContributor contributor = myListModel.getContributorForIndex(indexes[0]);
      showMoreElements(contributor);
      return;
    }
    if (indexes.length == 1 && myListModel.getElementAt(indexes[0]) instanceof SearchListModel.ResultsNotificationElement) {
      return;
    }

    boolean hasNotificationElement = ContainerUtil.find(
      myListModel.getItems(), element -> element instanceof SearchListModel.ResultsNotificationElement) != null;

    indexes = Arrays.stream(indexes)
      .filter(i -> !myListModel.isMoreElement(i))
      .filter(i -> !(myListModel.getElementAt(i) instanceof SearchListModel.ResultsNotificationElement))
      .toArray();

    String searchText = getSearchPattern();
    if (searchText.startsWith(SearchTopHitProvider.getTopHitAccelerator()) && searchText.contains(" ")) {
      SearchEverywhereUsageTriggerCollector.COMMAND_USED.log(myProject);
    }

    boolean closePopup = false;
    List<Object> selectedItems = new ArrayList<>();
    for (int i : indexes) {
      SearchEverywhereContributor<Object> contributor = myListModel.getContributorForIndex(i);
      Object value = myListModel.getElementAt(i);
      selectedItems.add(value);

      SearchEverywhereContributor<?> effectiveContributor =
        contributor instanceof SearchEverywhereContributorWrapper wrapper ? wrapper.getEffectiveContributor() : contributor;

      String selectedTabContributorID = myHeader.getSelectedTab().getReportableID();
      //noinspection ConstantConditions
      String reportableContributorID = getReportableContributorID(contributor);
      List<EventPair<?>> data = new ArrayList<>();
      data.add(SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(reportableContributorID));
      if (selectedTabContributorID != null) {
        data.add(SearchEverywhereUsageTriggerCollector.CURRENT_TAB_FIELD.with(selectedTabContributorID));
      }
      data.add(SearchEverywhereUsageTriggerCollector.SELECTED_ITEM_NUMBER.with(hasNotificationElement ? (i - 1) : i));
      data.add(SearchEverywhereUsageTriggerCollector.HAS_ONLY_SIMILAR_ELEMENT.with(hasNotificationElement));
      data.add(SearchEverywhereUsageTriggerCollector.IS_ELEMENT_SEMANTIC.with(
        effectiveContributor instanceof SemanticSearchEverywhereContributor semanticContributor &&
        semanticContributor.isElementSemantic(value)));
      PsiElement psi = toPsi(value);
      if (psi != null) {
        data.add(EventFields.Language.with(psi.getLanguage()));
      }
      SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ITEM_SELECTED.log(myProject, data);
      closePopup |= contributor.processSelectedItem(value, modifiers, searchText);
    }

    if (myMlService != null) {
      var tabId = myHeader.getSelectedTab().getID();
      var correctIndexes = hasNotificationElement ? Arrays.stream(indexes).map(i -> (i - 1)).toArray() : indexes;
      myMlService.onItemSelected(
        myProject, tabId, correctIndexes, selectedItems, () -> myListModel.getFoundElementsInfo(), closePopup, searchText);
    }

    for (int i : indexes) {
      SearchEverywhereContributor<Object> contributor = myListModel.getContributorForIndex(i);
      String selectedTabContributorID = myHeader.getSelectedTab().getReportableID();
      String reportableContributorID = getReportableContributorID(contributor);
      ContributorsLocalSummary.getInstance().updateContributorsLocalSummary(reportableContributorID,
                                                                            selectedTabContributorID.equals(ALL_CONTRIBUTORS_GROUP_ID));
    }

    if (closePopup) {
      closePopup();
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> myResultsList.repaint());
    }
  }

  private void showMoreElements(SearchEverywhereContributor contributor) {
    SearchEverywhereUsageTriggerCollector.MORE_ITEM_SELECTED.log(myProject);

    myListModel.freezeElements();
    if (contributor != null) {
      myListModel.setHasMore(contributor, false);
    }
    else {
      myListModel.clearMoreItems();
    }

    Map<SearchEverywhereContributor<?>, Collection<SearchEverywhereFoundElementInfo>> found = myListModel.getFoundElementsMap();
    int additionalItemsCount = myHeader.getSelectedTab().isSingleContributor() ? SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT
                                                                               : MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT;

    Stream<Map.Entry<SearchEverywhereContributor<?>, Collection<SearchEverywhereFoundElementInfo>>> stream = found.entrySet().stream();
    if (contributor != null) {
      stream = stream.filter(entry -> entry.getKey() == contributor);
    }
    else {
      stream = stream.filter(entry -> myListModel.hasMoreElements(entry.getKey()));
    }

    Map<? extends SearchEverywhereContributor<?>, Integer> contributorsAndLimits =
      stream.collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().size() + additionalItemsCount));

    myHintHelper.setSearchInProgress(StringUtil.isNotEmpty(getSearchPattern()));
    mySearchProgressIndicator = mySearcher.findMoreItems(found, contributorsAndLimits, getSearchPattern());
  }

  private void stopSearching() {
    if (mySearchProgressIndicator != null && !mySearchProgressIndicator.isCanceled()) {
      mySearchProgressIndicator.cancel();
    }
  }

  @VisibleForTesting
  public void sendStatisticsAndClose() {
    if (isShowing() || ApplicationManager.getApplication().isUnitTestMode()) {
      if (myMlService != null) {
        myMlService.onSearchFinished(
          myProject, () -> myListModel.getFoundElementsInfo()
        );
      }
    }
    closePopup();
  }

  public void closePopup() {
    ActionMenu.showDescriptionInStatusBar(true, myResultsList, null);
    stopSearching();
    searchFinishedHandler.run();
  }

  @TestOnly
  public Future<List<Object>> findElementsForPattern(String pattern) {
    clearResults();
    CompletableFuture<List<Object>> future = new CompletableFuture<>();
    SearchAdapter listener = new SearchAdapter() {
      @Override
      public void searchFinished(@NotNull List<Object> items) {
        future.complete(items);
        SwingUtilities.invokeLater(() -> removeSearchListener(this));
      }
    };
    addSearchListener(listener);
    mySearchField.setText(pattern);
    return future;
  }

  @TestOnly
  public void clearResults() {
    // do not activate preview in tests
    myUsagePreviewPanel = null;
    myListModel.clear();
    mySearchField.setText("");
  }

  private final ListCellRenderer<Object> myCommandRenderer = new ColoredListCellRenderer<>() {

    @Override
    protected void customizeCellRenderer(@NotNull JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
      setPaintFocusBorder(false);
      setIcon(EmptyIcon.ICON_16);
      setFont(list.getFont());

      SearchEverywhereCommandInfo command = (SearchEverywhereCommandInfo)value;
      append(command.getCommandWithPrefix() + " ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
      append(command.getDefinition(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
      setBackground(UIUtil.getListBackground(selected, hasFocus));
    }
  };

  private final class ShowInFindToolWindowAction extends DumbAwareAction {

    ShowInFindToolWindowAction() {
      super(IdeBundle.messagePointer("show.in.find.window.button.name"),
            IdeBundle.messagePointer("show.in.find.window.button.description"), getShowInFindToolWindowIcon());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      stopSearching();

      Collection<SearchEverywhereContributor<?>> contributors = myHeader.getSelectedTab().getContributors();
      contributors = ContainerUtil.filter(contributors, SearchEverywhereContributor::showInFindResults);

      if (contributors.isEmpty()) {
        return;
      }

      String searchText = getSearchPattern();
      String contributorsString = contributors.stream()
        .map(SearchEverywhereContributor::getGroupName)
        .collect(Collectors.joining(", "));

      UsageViewPresentation presentation = new UsageViewPresentation();
      String tabCaptionText = IdeBundle.message("searcheverywhere.found.matches.title", searchText, contributorsString);
      presentation.setCodeUsagesString(tabCaptionText);
      presentation.setTargetsNodeText(IdeBundle.message("searcheverywhere.found.targets.title", searchText, contributorsString));
      presentation.setTabName(tabCaptionText);
      presentation.setTabText(tabCaptionText);

      Collection<Usage> usages = new LinkedHashSet<>();
      Collection<PsiElement> targets = new LinkedHashSet<>();

      Collection<Object> cached = contributors.stream()
        .flatMap(contributor -> myListModel.getFoundItems(contributor).stream())
        .collect(Collectors.toSet());
      fillUsages(cached, usages, targets);

      Collection<SearchEverywhereContributor<?>> contributorsForAdditionalSearch;
      contributorsForAdditionalSearch = ContainerUtil.filter(contributors, contributor -> myListModel.hasMoreElements(contributor));

      if (!contributorsForAdditionalSearch.isEmpty()) {
        ProgressManager.getInstance().run(new Task.Modal(myProject, tabCaptionText, true) {
          private final ProgressIndicator progressIndicator = new ProgressIndicatorBase();

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            progressIndicator.start();
            TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.createFor(progressIndicator);

            Collection<Object> foundElements = new ArrayList<>();
            int alreadyFoundCount = cached.size();
            for (SearchEverywhereContributor<?> contributor : contributorsForAdditionalSearch) {
              if (progressIndicator.isCanceled()) break;
              try {
                fetch(contributor, foundElements, alreadyFoundCount, tooManyUsagesStatus);
              }
              catch (ProcessCanceledException ignore) {
              }
            }
            fillUsages(foundElements, usages, targets);
          }

          <Item> void fetch(SearchEverywhereContributor<Item> contributor,
                            Collection<Object> foundElements,
                            int alreadyFoundCount,
                            TooManyUsagesStatus tooManyUsagesStatus) {
            contributor.fetchElements(searchText, progressIndicator, o -> {
              if (progressIndicator.isCanceled()) {
                return false;
              }

              if (cached.contains(o)) {
                return true;
              }

              foundElements.add(o);
              tooManyUsagesStatus.pauseProcessingIfTooManyUsages();
              if (foundElements.size() + alreadyFoundCount >= UsageLimitUtil.USAGES_LIMIT &&
                  tooManyUsagesStatus.switchTooManyUsagesStatus()) {
                UsageViewManagerImpl.showTooManyUsagesWarningLater(getProject(), tooManyUsagesStatus, progressIndicator, null,
                                                                   () -> UsageViewBundle.message("find.excessive.usage.count.prompt"),
                                                                   null);
                return !progressIndicator.isCanceled();
              }
              return true;
            });
          }

          @Override
          public void onCancel() {
            progressIndicator.cancel();
          }

          @Override
          public void onSuccess() {
            showInFindWindow(targets, usages, presentation);
          }

          @Override
          public void onThrowable(@NotNull Throwable error) {
            super.onThrowable(error);
            progressIndicator.cancel();
          }
        });
      }
      else {
        showInFindWindow(targets, usages, presentation);
      }
      sendStatisticsAndClose();
    }

    private static void fillUsages(Collection<Object> foundElements,
                                   Collection<? super Usage> usages,
                                   Collection<? super PsiElement> targets) {
      for (Object element : foundElements) {
        // TODO this should be managed by the contributor !!!
        if (element instanceof UsageInfo2UsageAdapter) {
          usages.add((UsageInfo2UsageAdapter)element);
        }
        else if (element instanceof SearchEverywhereItem) {
          usages.add(((SearchEverywhereItem)element).getUsage());
        }
      }

      ReadAction.run(() -> foundElements.stream()
        .map(o -> toPsi(o))
        .filter(Objects::nonNull)
        .forEach(element -> {
          if (element.getTextRange() != null) {
            UsageInfo usageInfo = new UsageInfo(element);
            usages.add(new UsageInfo2UsageAdapter(usageInfo));
          }
          else {
            targets.add(element);
          }
        }));
    }

    private void showInFindWindow(Collection<? extends PsiElement> targets, Collection<Usage> usages, UsageViewPresentation presentation) {
      UsageTarget[] targetsArray = targets.isEmpty() ? UsageTarget.EMPTY_ARRAY
                                                     : PsiElement2UsageTargetAdapter.convert(PsiUtilCore.toPsiElementArray(targets));
      Usage[] usagesArray = usages.toArray(Usage.EMPTY_ARRAY);
      UsageViewManager.getInstance(myProject).showUsages(targetsArray, usagesArray, presentation);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myProject == null) {
        e.getPresentation().setEnabled(false);
        return;
      }

      SETab selectedTab = myHeader != null ? myHeader.getSelectedTab() : null;
      boolean enabled = selectedTab == null || ContainerUtil.exists(selectedTab.getContributors(), c -> c.showInFindResults());
      e.getPresentation().setEnabled(enabled);
      if (!ExperimentalUI.isNewUI()) {
        e.getPresentation()
          .setIcon(ToolWindowManager.getInstance(myProject).getLocationIcon(ToolWindowId.FIND, getShowInFindToolWindowIcon()));
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private final class CompleteCommandAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (completeCommand()) {
        SearchEverywhereUsageTriggerCollector.COMMAND_COMPLETED.log(myProject, EventFields.InputEventByAnAction.with(e));
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getCompleteCommand() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    private boolean completeCommand() {
      SearchEverywhereCommandInfo suggestedCommand = getCompleteCommand();
      if (suggestedCommand != null) {
        mySearchField.setText(suggestedCommand.getCommandWithPrefix() + " ");
        return true;
      }

      return false;
    }

    private SearchEverywhereCommandInfo getCompleteCommand() {
      String pattern = getSearchPattern();
      String commandPrefix = SearchTopHitProvider.getTopHitAccelerator();
      if (pattern.startsWith(commandPrefix) && !pattern.contains(" ")) {
        String typedCommand = pattern.substring(commandPrefix.length());
        return getSelectedCommand(typedCommand).orElseGet(() -> {
          List<SearchEverywhereCommandInfo> completions =
            getCommandsForCompletion(myHeader.getSelectedTab().getContributors(), typedCommand);
          return completions.isEmpty() ? null : completions.get(0);
        });
      }

      return null;
    }
  }

  private @Nls(capitalization = Nls.Capitalization.Sentence) String getNotFoundText() {
    SETab selectedTab = myHeader.getSelectedTab();
    if (!selectedTab.isSingleContributor()) return IdeBundle.message("searcheverywhere.nothing.found.for.all.anywhere");

    String groupName = selectedTab.getContributors().get(0).getFullGroupName();
    return IdeBundle.message("searcheverywhere.nothing.found.for.contributor.anywhere", groupName.toLowerCase(Locale.ROOT));
  }

  private final class MySearchListener implements SearchListener {

    @Override
    public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
      if (mySearchProgressIndicator == null || mySearchProgressIndicator.isCanceled()) return;

      boolean wasEmpty = myListModel.getSize() == 0;

      if (myMlService != null) {
        myMlService.notifySearchResultsUpdated();
      }

      mySelectionTracker.lock();
      myListModel.addElements(list);
      mySelectionTracker.unlock();

      mySelectionTracker.restoreSelection();

      if (wasEmpty && myListModel.getSize() > 0) {
        Object prevSelection = ((SearchEverywhereManagerImpl)SearchEverywhereManager.getInstance(myProject))
          .getPrevSelection(getSelectedTabID());
        if (prevSelection instanceof Integer) {
          for (Object item : myListModel.getItems()) {
            if (Objects.hashCode(item) == ((Integer)prevSelection).intValue()) {
              myResultsList.setSelectedValue(item, true);
              break;
            }
          }
        }
      }

      // If the selected element is a spelling correction, but there are more elements in the list
      // preselect the second element instead.
      var selectedItem = myResultsList.getSelectedValue();
      if (selectedItem instanceof SearchEverywhereSpellCheckResult.Correction && myListModel.getSize() > 1) {
        myResultsList.setSelectedIndex(1);
      }
      if (myResultsList.getSelectedValue() instanceof SearchListModel.ResultsNotificationElement && myListModel.getSize() > 1) {
        myResultsList.setSelectedIndex(myResultsList.getSelectedIndex() + 1);
      }

      myExternalSearchListeners.forEach(listener -> listener.elementsAdded(list));
    }

    @Override
    public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
      if (mySearchProgressIndicator == null || mySearchProgressIndicator.isCanceled()) return;

      list.forEach(info -> myListModel.removeElement(info.getElement(), info.getContributor()));
      myExternalSearchListeners.forEach(listener -> listener.elementsRemoved(list));
    }

    @Override
    public void searchStarted(@NotNull String pattern, @NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
      myExternalSearchListeners.forEach(listener -> listener.searchStarted(pattern, contributors));
    }

    @Override
    public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
      String pattern = getSearchPattern();
      pattern = pattern.replaceAll("^" + SearchTopHitProvider.getTopHitAccelerator() + "\\S+\\s*", "");
      if (myResultsList.isEmpty() || myListModel.isResultsExpired()) {
        if (myHeader.canSetEverywhere() && !myHeader.isEverywhere() && !pattern.isEmpty()) {
          myHeader.autoSetEverywhere(true);
          myNotFoundString = pattern;
          return;
        }

        hideHint();
        if (myListModel.isResultsExpired()) {
          myListModel.clear();
        }
      }

      updateEmptyText(pattern);
      hasMoreContributors.forEach(myListModel::setHasMore);
      mySelectionTracker.resetSelectionIfNeeded();
      myHintHelper.setSearchInProgress(false);

      myExternalSearchListeners.forEach(listener -> {
        listener.searchFinished(hasMoreContributors);
        if (listener instanceof SearchListenerEx listenerEx) listenerEx.searchFinished(myListModel.getItems());
      });
    }

    @Override
    public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
      myExternalSearchListeners.forEach(listener -> listener.contributorWaits(contributor));
    }

    @Override
    public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) {
      myExternalSearchListeners.forEach(listener -> listener.contributorFinished(contributor, hasMore));
    }

    private void updateEmptyText(String pattern) {
      StatusText emptyStatus = myResultsList.getEmptyText();
      emptyStatus.clear();

      if (pattern.isEmpty()) return;
      boolean showResetScope = myHeader.canResetScope();

      SearchEverywhereEmptyTextProvider provider = StreamEx.of(myHeader.getSelectedTab().getContributors())
        .select(SearchEverywhereEmptyTextProvider.class)
        .findFirst()
        .orElse(null);

      if (provider != null) {
        provider.updateEmptyStatus(emptyStatus, () -> {
          scheduleRebuildList(SearchRestartReason.TAB_CHANGED);
          return Unit.INSTANCE;
        });

        if (showResetScope) {
          emptyStatus.appendLine("");
          emptyStatus.appendText(IdeBundle.message("searcheverywhere.try.to.reset.scope"));
          emptyStatus.appendText(" " + StringUtil.toLowerCase(EverythingGlobalScope.getNameText()),
                                 SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> myHeader.resetScope());
        }

        return;
      }

      boolean showFindInFilesAction =
        ContainerUtil.exists(myHeader.getSelectedTab().getContributors(), contributor -> contributor.showInFindResults());
      boolean showResetFilter = myHeader.getSelectedTab().canClearFilter();
      boolean anyActionAllowed = showFindInFilesAction || showResetScope || showResetFilter;

      final AtomicBoolean firstPartAdded = new AtomicBoolean();
      final AtomicInteger actionsPrinted = new AtomicInteger(0);
      if (showResetScope) {
        ActionListener resetScopeListener = e -> myHeader.resetScope();
        emptyStatus.appendText(IdeBundle.message("searcheverywhere.try.to.reset.scope"));
        emptyStatus.appendText(" " + StringUtil.toLowerCase(EverythingGlobalScope.getNameText()),
                               SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, resetScopeListener);
        firstPartAdded.set(true);
        actionsPrinted.incrementAndGet();
      }

      if (showResetFilter) {
        ActionListener clearFiltersAction = e -> {
          myHeader.getSelectedTab().clearFilter();
          scheduleRebuildList(SearchRestartReason.TAB_CHANGED);
        };
        if (firstPartAdded.get()) emptyStatus.appendText(", ");
        String resetFilterMessage = IdeBundle.message("searcheverywhere.reset.filters");
        emptyStatus.appendText(firstPartAdded.get() ? Strings.toLowerCase(resetFilterMessage) : resetFilterMessage,
                               SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, clearFiltersAction);
        firstPartAdded.set(true);

        if (actionsPrinted.incrementAndGet() >= 2) {
          emptyStatus.appendLine("");
          actionsPrinted.set(0);
        }
      }

      if (showFindInFilesAction && myProject != null) {
        FindInProjectManager manager = FindInProjectManager.getInstance(myProject);
        if (manager != null && manager.isEnabled()) {
          DataContext context = DataManager.getInstance().getDataContext(SearchEverywhereUI.this);
          ActionListener findInFilesAction = e -> manager.findInProject(context, null);
          emptyStatus.appendText((firstPartAdded.get() ? " " + IdeBundle.message("searcheverywhere.use.optional")
                                                       : IdeBundle.message("searcheverywhere.use.main")) + " ");
          emptyStatus.appendText(IdeBundle.message("searcheverywhere.try.to.find.in.files"),
                                 SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, findInFilesAction);
          String findInFilesShortcut = KeymapUtil.getFirstKeyboardShortcutText("FindInPath");
          if (!StringUtil.isEmpty(findInFilesShortcut)) {
            emptyStatus.appendText(" (" + findInFilesShortcut + ")");
          }

          if (actionsPrinted.incrementAndGet() >= 2) {
            emptyStatus.appendLine("");
            actionsPrinted.set(0);
          }

          emptyStatus.appendText(" " + IdeBundle.message("searcheverywhere.to.perform.fulltext.search"));
        }
      }

      if (anyActionAllowed) {
        emptyStatus.appendText(".");
      }
    }
  }

  private static final class SearchFieldTypingListener extends KeyAdapter {
    private int mySymbolKeysTyped;
    private int myBackspacesTyped;

    @Override
    public void keyTyped(KeyEvent e) {
      mySymbolKeysTyped++;
    }

    @Override
    public void keyReleased(KeyEvent e) {
      final int code = e.getKeyCode();
      if (code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE) {
        myBackspacesTyped++;
      }
    }
  }

  private final SearchEverywhereContributor<Object> myStubCommandContributor = new SearchEverywhereContributor<>() {
    @Override
    public @NotNull String getSearchProviderId() {
      return "CommandsContributor";
    }

    @Override
    public @NotNull String getGroupName() {
      return IdeBundle.message("searcheverywhere.commands.tab.name");
    }

    @Override
    public int getSortWeight() {
      return 10;
    }

    @Override
    public boolean showInFindResults() {
      return false;
    }

    @Override
    public void fetchElements(@NotNull String pattern,
                              @NotNull ProgressIndicator progressIndicator,
                              @NotNull Processor<? super Object> consumer) { }

    @Override
    public boolean processSelectedItem(@NotNull Object selected, int modifiers, @NotNull String searchText) {
      mySearchField.setText(((SearchEverywhereCommandInfo)selected).getCommandWithPrefix() + " ");
      SearchEverywhereUsageTriggerCollector.COMMAND_COMPLETED.log(myProject);
      return false;
    }

    @Override
    public @NotNull ListCellRenderer<? super Object> getElementsRenderer() {
      return myCommandRenderer;
    }
  };

  private static final class HintHelper {

    private final ExtendableTextField myTextField;

    private final TextIcon myHintTextIcon = new TextIcon("", JBUI.CurrentTheme.BigPopup.searchFieldGrayForeground(), Gray.TRANSPARENT, 0);
    private final RowIcon myWarnIcon = new RowIcon(2, com.intellij.ui.icons.RowIcon.Alignment.BOTTOM);
    private final ExtendableTextComponent.Extension myHintExtension = createExtension(myHintTextIcon);
    private final ExtendableTextComponent.Extension mySearchProcessExtension = createExtension(AnimatedIcon.Default.INSTANCE);
    private final ExtendableTextComponent.Extension myWarningExtension;
    private final List<ExtendableTextComponent.Extension> myRightExtensions = new ArrayList<>();

    private HintHelper(ExtendableTextField field) {
      myTextField = field;
      myHintTextIcon.setFont(myTextField.getFont());
      myHintTextIcon.setFontTransform(FontInfo.getFontRenderContext(myTextField).getTransform());
      // Try aligning hint by baseline with the text field
      myHintTextIcon.setInsets(JBUIScale.scale(3), 0, 0, 0);

      myWarnIcon.setIcon(AllIcons.General.Warning, 0);
      myWarnIcon.setIcon(myHintTextIcon, 1);
      myWarningExtension = createExtension(myWarnIcon);
    }

    public void setHint(String hintText) {
      myTextField.removeExtension(myHintExtension);
      myTextField.removeExtension(myWarningExtension);
      if (StringUtil.isNotEmpty(hintText)) {
        myHintTextIcon.setText(hintText);
        addExtensionAsLast(myHintExtension);
      }
    }

    public void setWarning(String warnText) {
      myTextField.removeExtension(myHintExtension);
      myTextField.removeExtension(myWarningExtension);
      if (StringUtil.isNotEmpty(warnText)) {
        myHintTextIcon.setText(warnText);
        myWarnIcon.setIcon(myHintTextIcon, 1);
        addExtensionAsLast(myWarningExtension);
      }
    }

    public void setSearchInProgress(boolean inProgress) {
      myTextField.removeExtension(mySearchProcessExtension);
      if (inProgress) myTextField.addExtension(mySearchProcessExtension);
    }

    //set extension which should be shown last
    private void addExtensionAsLast(ExtendableTextComponent.Extension ext) {
      ArrayList<ExtendableTextComponent.Extension> extensions = new ArrayList<>(myTextField.getExtensions());
      extensions.add(0, ext);
      myTextField.setExtensions(extensions);
    }

    private static @NotNull ExtendableTextComponent.Extension createExtension(Icon icon) {
      return new ExtendableTextComponent.Extension() {
        @Override
        public Icon getIcon(boolean hovered) {
          return icon;
        }
      };
    }

    private void setRightExtensions(@NotNull List<? extends AnAction> actions) {
      myTextField.removeExtension(myHintExtension);
      myTextField.removeExtension(myWarningExtension);
      actions.stream().map(HintHelper::createRightActionExtension).forEach(it -> {
        addExtensionAsLast(it);
        myRightExtensions.add(it);
      });
    }

    private void removeRightExtensions() {
      myRightExtensions.forEach(myTextField::removeExtension);
    }

    private static @NotNull ExtendableTextComponent.Extension createRightActionExtension(@NotNull AnAction action) {
      return new ExtendableTextComponent.Extension() {
        @Override
        public Icon getIcon(boolean hovered) {
          Presentation presentation = action.getTemplatePresentation();
          if (!(action instanceof TextSearchRightActionAction)) return presentation.getIcon();

          if (((TextSearchRightActionAction)action).isSelected()) {
            return presentation.getSelectedIcon();
          }
          else if (hovered) {
            return presentation.getHoveredIcon();
          }
          else {
            return presentation.getIcon();
          }
        }

        @Override
        public String getTooltip() {
          return action instanceof TextSearchRightActionAction
                 ? ((TextSearchRightActionAction)action).getTooltip()
                 : action.getTemplatePresentation().getDescription();
        }

        @Override
        public boolean isSelected() {
          return action instanceof ToggleAction toggleAction && toggleAction.isSelected(createActionEvent());
        }

        @Override
        public Dimension getButtonSize() {
          return ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;
        }

        @Override
        public Runnable getActionOnClick() {
          return () -> {
            action.actionPerformed(createActionEvent());
          };
        }

        private AnActionEvent createActionEvent() {
          return AnActionEvent.createFromDataContext(ActionPlaces.POPUP, action.getTemplatePresentation().clone(),
                                                     DataContext.EMPTY_CONTEXT);
        }
      };
    }
  }
}
