// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.gotoByName.QuickSearchComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Konstantin Bulenkov
 * @author Mikhail.Sokolov
 */
public class SearchEverywhereUI extends BorderLayoutPanel implements Disposable, DataProvider, QuickSearchComponent {
  private static final Logger LOG = Logger.getInstance(SearchEverywhereUI.class);
  public static final int SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT = 30;
  public static final int MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT = 15;

  private final List<SearchEverywhereContributor> myServiceContributors;
  private final List<SearchEverywhereContributor> myShownContributors;
  private final Map<String, SearchEverywhereContributorFilter<?>> myContributorFilters;
  private final Project myProject;

  private SETab mySelectedTab;
  private final JTextField mySearchField;
  private final JCheckBox myNonProjectCB;
  private final List<SETab> myTabs = new ArrayList<>();

  private boolean nonProjectCheckBoxAutoSet = true;
  private String notFoundString;

  private final JBList<Object> myResultsList = new JBList<>();
  private final SearchListModel myListModel = new SearchListModel(); //todo using in different threads? #UX-1

  private JBPopup myHint;

  private CalcThread myCalcThread; //todo using in different threads? #UX-1
  private volatile ActionCallback myCurrentWorker = ActionCallback.DONE;
  private int myCalcThreadRestartRequestId = 0;
  private final Object myWorkerRestartRequestLock = new Object();
  private final Alarm listOperationsAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication());
  private final Alarm emptyListAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication());

  private Runnable searchFinishedHandler = () -> {};

  public SearchEverywhereUI(Project project,
                            List<SearchEverywhereContributor> serviceContributors,
                            List<SearchEverywhereContributor> contributors,
                            Map<String, SearchEverywhereContributorFilter<?>> filters) {
    withMinimumWidth(670);
    withPreferredWidth(670);
    withBackground(JBUI.CurrentTheme.SearchEverywhere.dialogBackground());

    myProject = project;
    myServiceContributors = serviceContributors;
    myShownContributors  = contributors;
    myContributorFilters = filters;

    myNonProjectCB = new JBCheckBox();
    myNonProjectCB.setOpaque(false);
    myNonProjectCB.setFocusable(false);

    JPanel contributorsPanel = createTabPanel(contributors);
    JPanel settingsPanel = createSettingsPanel();
    mySearchField = createSearchField();
    JPanel suggestionsPanel = createSuggestionsPanel();

    myResultsList.setModel(myListModel);
    myResultsList.setFocusable(false);
    myResultsList.setCellRenderer(new CompositeCellRenderer());
    myResultsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myResultsList.addListSelectionListener(e -> {
      int[] selectedIndices = myResultsList.getSelectedIndices();
      if (selectedIndices.length > 1) {
        boolean multiselectAllowed = Arrays.stream(selectedIndices)
                                           .allMatch(i -> myListModel.getContributorForIndex(i).isMultiselectSupported());
        if (!multiselectAllowed) {
          int index = myResultsList.getLeadSelectionIndex();
          myResultsList.setSelectedIndex(index);
        }
      }
    });

    ScrollingUtil.installActions(myResultsList, getSearchField());

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.setOpaque(false);
    topPanel.add(contributorsPanel, BorderLayout.WEST);
    topPanel.add(settingsPanel, BorderLayout.EAST);
    topPanel.add(mySearchField, BorderLayout.SOUTH);

    WindowMoveListener moveListener = new WindowMoveListener(this);
    topPanel.addMouseListener(moveListener);
    topPanel.addMouseMotionListener(moveListener);

    addToTop(topPanel);
    addToCenter(suggestionsPanel);

    initSearchActions();
  }

  private JPanel createSuggestionsPanel() {
    JPanel pnl = new JPanel(new BorderLayout());
    pnl.setOpaque(false);
    pnl.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.SearchEverywhere.searchFieldBorderColor(), 1, 0, 0, 0));

    JScrollPane resultsScroll = new JBScrollPane(myResultsList);
    resultsScroll.setBorder(null);
    resultsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    resultsScroll.setPreferredSize(JBUI.size(670, JBUI.CurrentTheme.SearchEverywhere.maxListHeight()));
    pnl.add(resultsScroll, BorderLayout.CENTER);

    String hint = IdeBundle.message("searcheverywhere.history.shortcuts.hint",
                                    KeymapUtil.getKeystrokeText(SearchTextField.ALT_SHOW_HISTORY_KEYSTROKE),
                                    KeymapUtil.getKeystrokeText(SearchTextField.SHOW_HISTORY_KEYSTROKE));
    JLabel hintLabel = HintUtil.createAdComponent(hint, JBUI.Borders.empty(), SwingConstants.LEFT);
    hintLabel.setOpaque(false);
    hintLabel.setForeground(JBColor.GRAY);
    pnl.add(hintLabel, BorderLayout.SOUTH);

    return pnl;
  }

  public JTextField getSearchField() {
    return mySearchField;
  }

  public void setUseNonProjectItems(boolean use) {
    doSetUseNonProjectItems(use, false);
  }

  private void doSetUseNonProjectItems(boolean use, boolean isAutoSet) {
    myNonProjectCB.setSelected(use);
    nonProjectCheckBoxAutoSet = isAutoSet;
  }

  public boolean isUseNonProjectItems() {
    return myNonProjectCB.isSelected();
  }

  public void switchToContributor(String contributorID) {
    SETab selectedTab = myTabs.stream()
                       .filter(tab -> tab.getID().equals(contributorID))
                       .findAny()
                       .orElseThrow(() -> new IllegalArgumentException(String.format("Contributor %s is not supported", contributorID)));
    switchToTab(selectedTab);
  }

  private void switchToNextTab() {
    int currentIndex = myTabs.indexOf(mySelectedTab);
    SETab nextTab = currentIndex == myTabs.size() - 1 ? myTabs.get(0) : myTabs.get(currentIndex + 1);
    switchToTab(nextTab);
  }

  private void switchToPrevTab() {
    int currentIndex = myTabs.indexOf(mySelectedTab);
    SETab prevTab = currentIndex == 0 ? myTabs.get(myTabs.size() - 1) : myTabs.get(currentIndex - 1);
    switchToTab(prevTab);
  }

  private void switchToTab(SETab tab) {
    mySelectedTab = tab;

    String checkBoxText = tab.getContributor()
                     .map(SearchEverywhereContributor::includeNonProjectItemsText)
                     .orElse(IdeBundle.message("checkbox.include.non.project.items", IdeUICustomization.getInstance().getProjectConceptName()));
    if (checkBoxText.indexOf(UIUtil.MNEMONIC) != -1) {
      DialogUtil.setTextWithMnemonic(myNonProjectCB, checkBoxText);
    } else {
      myNonProjectCB.setText(checkBoxText);
      myNonProjectCB.setDisplayedMnemonicIndex(-1);
      myNonProjectCB.setMnemonic(0);
    }
    if (nonProjectCheckBoxAutoSet && isUseNonProjectItems()) {
      doSetUseNonProjectItems(false, true);
    }

    myResultsList.getEmptyText().setText(getEmptyText());

    repaint();
    rebuildList();
  }

  public void setSearchFinishedHandler(@NotNull Runnable searchFinishedHandler) {
    this.searchFinishedHandler = searchFinishedHandler;
  }

  public String getSelectedContributorID() {
    return mySelectedTab.getID();
  }

  @Override
  public void dispose() {
    stopSearching();
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    IntStream indicesStream = Arrays.stream(myResultsList.getSelectedIndices())
                                    .filter(i -> !myListModel.isMoreElement(i));

    //common data section---------------------
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      List<PsiElement> elements = indicesStream.mapToObj(i -> {
                                                  SearchEverywhereContributor contributor = myListModel.getContributorForIndex(i);
                                                  Object item = myListModel.getElementAt(i);
                                                  Object psi = contributor.getDataForItem(item, CommonDataKeys.PSI_ELEMENT.getName());
                                                  return (PsiElement)psi;
                                                })
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toList());
      return PsiUtilCore.toPsiElementArray(elements);
    }

    //item-specific data section--------------
    return indicesStream.mapToObj(i -> {
                          SearchEverywhereContributor contributor = myListModel.getContributorForIndex(i);
                          Object item = myListModel.getElementAt(i);
                          return contributor.getDataForItem(item, dataId);
                        })
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
  }

  @Override
  public void registerHint(JBPopup h) {
    if (myHint != null && myHint.isVisible() && myHint != h) {
      myHint.cancel();
    }
    myHint = h;
  }

  @Override
  public void unregisterHint() {
    registerHint(null);
  }

  @Override
  public Component asComponent() {
    return this;
  }

  private void hideHint() {
    if (myHint != null && myHint.isVisible()) {
      myHint.cancel();
    }
  }

  private void updateHint(Object element) {
    if (myHint == null || !myHint.isVisible()) return;
    final PopupUpdateProcessor updateProcessor = myHint.getUserData(PopupUpdateProcessor.class);
    if (updateProcessor != null) {
      updateProcessor.updatePopup(element);
    }
  }

  private boolean isAllTabSelected() {
    return SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID.equals(getSelectedContributorID());
  }

  private JTextField createSearchField() {
    ExtendableTextField searchField = new ExtendableTextField() {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = JBUI.scale(29);
        return size;
      }
    };

    ExtendableTextField.Extension searchExtension = new ExtendableTextField.Extension() {
      @Override
      public Icon getIcon(boolean hovered) {
        return AllIcons.Actions.Find;
      }

      @Override
      public boolean isIconBeforeText() {
        return true;
      }

      @Override
      public int getIconGap() {
        return JBUI.scale(10);
      }
    };
    ExtendableTextField.Extension hintExtension = new ExtendableTextField.Extension() {
      private final TextIcon icon;
      {
        icon = new TextIcon(IdeBundle.message("searcheverywhere.switch.scope.hint"), JBColor.GRAY, null, 0);
        icon.setFont(RelativeFont.SMALL.derive(getFont()));
      }

      @Override
      public Icon getIcon(boolean hovered) {
        return icon;
      }
    };
    searchField.setExtensions(searchExtension, hintExtension);

    //todo gap between icon and text #UX-1
    Insets insets = JBUI.CurrentTheme.SearchEverywhere.searchFieldInsets();
    Border empty = JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right);
    Border topLine = JBUI.Borders.customLine(JBUI.CurrentTheme.SearchEverywhere.searchFieldBorderColor(), 1, 0, 0, 0);
    searchField.setBorder(JBUI.Borders.merge(empty, topLine, true));
    searchField.setBackground(JBUI.CurrentTheme.SearchEverywhere.searchFieldBackground());
    searchField.setFocusTraversalKeysEnabled(false);

    return searchField;
  }

  private JPanel createSettingsPanel() {
    JPanel res = new JPanel();
    BoxLayout bl = new BoxLayout(res, BoxLayout.X_AXIS);
    res.setLayout(bl);
    res.setOpaque(false);

    res.add(myNonProjectCB);
    res.add(Box.createHorizontalStrut(JBUI.scale(19)));

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.addAction(new ShowInFindToolWindowAction());
    actionGroup.addAction(new ShowFilterAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("search.everywhere.toolbar", actionGroup, true);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setOpaque(false);
    res.add(toolbarComponent);
    return res;
  }

  @NotNull
  private JPanel createTabPanel(List<SearchEverywhereContributor> contributors) {
    JPanel contributorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    contributorsPanel.setOpaque(false);

    SETab allTab = new SETab(null);
    contributorsPanel.add(allTab);
    myTabs.add(allTab);

    contributors.stream()
                .filter(SearchEverywhereContributor::isShownInSeparateTab)
                .forEach(contributor -> {
                  SETab tab = new SETab(contributor);
                  contributorsPanel.add(tab);
                  myTabs.add(tab);
                });
    switchToTab(allTab);

    return contributorsPanel;
  }

  private class SETab extends JLabel {
    private final SearchEverywhereContributor myContributor;

    public SETab(SearchEverywhereContributor contributor) {
      super(contributor == null ? IdeBundle.message("searcheverywhere.allelements.tab.name") : contributor.getGroupName());
      myContributor = contributor;
      Insets insets = JBUI.CurrentTheme.SearchEverywhere.tabInsets();
      setBorder(JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right));
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          switchToTab(SETab.this);
        }
      });
    }

    public String getID() {
      return getContributor()
        .map(SearchEverywhereContributor::getSearchProviderId)
        .orElse(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID);
    }

    public Optional<SearchEverywhereContributor> getContributor() {
      return Optional.ofNullable(myContributor);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = JBUI.scale(29);
      return size;
    }

    @Override
    public boolean isOpaque() {
      return mySelectedTab == this;
    }

    @Override
    public Color getBackground() {
      return mySelectedTab == this
             ? JBUI.CurrentTheme.SearchEverywhere.selectedTabColor()
             : super.getBackground();
    }
  }

  private void rebuildList() {
    assert EventQueue.isDispatchThread() : "Must be EDT";
    if (myCalcThread != null && !myCurrentWorker.isProcessed()) {
      myCurrentWorker = myCalcThread.cancel();
    }
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }

    String pattern = getSearchPattern();
    String matcherString = mySelectedTab.getContributor()
                                        .map(contributor -> contributor.filterControlSymbols(pattern))
                                        .orElse(pattern);

    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + matcherString, NameUtil.MatchingCaseSensitivity.NONE);
    MatcherHolder.associateMatcher(myResultsList, matcher);

    //assert project != null;
    //myRenderer.myProject = project;
    synchronized (myWorkerRestartRequestLock) { // this lock together with RestartRequestId should be enough to prevent two CalcThreads running at the same time
      final int currentRestartRequest = ++myCalcThreadRestartRequestId;
      myCurrentWorker.doWhenProcessed(() -> {
        synchronized (myWorkerRestartRequestLock) {
          if (currentRestartRequest != myCalcThreadRestartRequestId) {
            return;
          }
          myCalcThread = new CalcThread(pattern, null);

          myCurrentWorker = myCalcThread.start();
        }
      });
    }
  }

  private String getSearchPattern() {
    return mySearchField != null ? mySearchField.getText() : "";
  }

  private void initSearchActions() {
    mySearchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_TAB) {
          if (e.getModifiers() == 0) {
            switchToNextTab();
            e.consume();
          } else if (e.getModifiers() == InputEvent.SHIFT_MASK) {
            switchToPrevTab();
            e.consume();
          }
        }

        if (e.isShiftDown()) {
          if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            //ScrollingUtil.moveDown(myResultsList, e.getModifiersEx());
            myResultsList.dispatchEvent(e);
            e.consume();
          }
          if (e.getKeyCode() == KeyEvent.VK_UP) {
            //ScrollingUtil.moveUp(myResultsList, e.getModifiersEx());
            myResultsList.dispatchEvent(e);
            e.consume();
          }
        }

        int[] indices = myResultsList.getSelectedIndices();
        if (e.getKeyCode() == KeyEvent.VK_ENTER && indices.length != 0) {
          elementsSelected(indices, e.getModifiers());
        }
      }
    });

    AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(__ -> {
      stopSearching();
      searchFinishedHandler.run();
    }).registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), this);

    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String newSearchString = getSearchPattern();
        if (nonProjectCheckBoxAutoSet && isUseNonProjectItems()
            && newSearchString != null && !newSearchString.contains(notFoundString)) {
          doSetUseNonProjectItems(false, true);
        }
        rebuildList();
      }
    });

    myNonProjectCB.addItemListener(e -> rebuildList());
    myNonProjectCB.addActionListener(e -> nonProjectCheckBoxAutoSet = false);

    myResultsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        boolean multiSelectMode = e.isShiftDown() || e.isControlDown();
        if (e.getButton() == MouseEvent.BUTTON1 && !multiSelectMode) {
          e.consume();
          final int i = myResultsList.locationToIndex(e.getPoint());
          if (i > -1) {
            myResultsList.setSelectedIndex(i);
            elementsSelected(new int[]{i}, e.getModifiers());
          }
        }
      }
    });

    myResultsList.addListSelectionListener(e -> {
      Object selectedValue = myResultsList.getSelectedValue();
      if (selectedValue != null && myHint != null && myHint.isVisible()) {
        updateHint(selectedValue);
      }
    });

    myProject.getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        ApplicationManager.getApplication().invokeLater(() -> rebuildList());
      }
    });

    ApplicationManager.getApplication()
                      .getMessageBus()
                      .connect(this)
                      .subscribe(ProgressWindow.TOPIC, pw -> Disposer.register(pw,() -> myResultsList.repaint()));
  }

  private void elementsSelected(int[] indexes, int modifiers) {
    if (indexes.length == 1 && myListModel.isMoreElement(indexes[0])) {
      SearchEverywhereContributor contributor = myListModel.getContributorForIndex(indexes[0]);
      showMoreElements(contributor);
      return;
    }

    indexes = Arrays.stream(indexes)
                    .filter(i -> !myListModel.isMoreElement(i))
                    .toArray();

    boolean closePopup = false;
    for (int i: indexes) {
      SearchEverywhereContributor contributor = myListModel.getContributorForIndex(i);
      Object value = myListModel.getElementAt(i);
      closePopup |= contributor.processSelectedItem(value, modifiers, getSearchPattern());
    }

    if (closePopup) {
      stopSearching();
      searchFinishedHandler.run();
    } else {
      myResultsList.repaint();
    }
  }

  private void showMoreElements(SearchEverywhereContributor contributor) {
    synchronized (myWorkerRestartRequestLock) { // this lock together with RestartRequestId should be enough to prevent two CalcThreads running at the same time
      final int currentRestartRequest = ++myCalcThreadRestartRequestId;
      myCurrentWorker.doWhenProcessed(() -> {
        synchronized (myWorkerRestartRequestLock) {
          if (currentRestartRequest != myCalcThreadRestartRequestId) {
            return;
          }
          myCalcThread = new CalcThread(getSearchPattern(), contributor);

          myCurrentWorker = myCalcThread.start();
        }
      });
    }
  }

  private void stopSearching() {
    listOperationsAlarm.cancelAllRequests();
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }
  }

  private void handleEmptyResults() {
    assert EventQueue.isDispatchThread() : "Must be EDT";
    if (nonProjectCheckBoxAutoSet && !isUseNonProjectItems() && !getSearchPattern().isEmpty()) {
      doSetUseNonProjectItems(true, true);
      notFoundString = getSearchPattern();
      return;
    }

    hideHint();
  }

  @SuppressWarnings("Duplicates") //todo remove suppress #UX-1
  private class CalcThread implements Runnable {
    private final String pattern;
    private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();
    private final ActionCallback myDone = new ActionCallback();
    private final SearchEverywhereContributor contributorToExpand;

    public CalcThread(@NotNull String pattern, @Nullable SearchEverywhereContributor expand) {
      this.pattern = pattern;
      contributorToExpand = expand;
    }

    @Override
    public void run() {
      try {
        check();

        if (contributorToExpand == null) {
          resetList();
        } else {
          showMore(contributorToExpand);
        }
      }
      catch (ProcessCanceledException ignore) {
        myDone.setRejected();
      }
      catch (Exception e) {
        LOG.error(e);
        myDone.setRejected();
      }
      finally {
        if (!isCanceled()) {
          listOperationsAlarm.addRequest(() -> myResultsList.getEmptyText().setText(getEmptyText()), 0);
        }
        if (!myDone.isProcessed()) {
          myDone.setDone();
        }
      }
    }

    private void resetList() {
      listOperationsAlarm.cancelAllRequests();
      emptyListAlarm.cancelAllRequests();
      listOperationsAlarm.addRequest(() -> {
        Dimension oldSize = getPreferredSize();
        myResultsList.getEmptyText().setText(IdeBundle.message("label.choosebyname.searching"));
        myListModel.clear();
        Dimension newSize = getPreferredSize();
        firePropertyChange("preferredSize", oldSize, newSize);
      }, 200);

      boolean anyFound = false;
      SearchEverywhereContributor selectedContributor = mySelectedTab.getContributor().orElse(null);
      if (selectedContributor != null) {
        anyFound = addContributorItems(selectedContributor, SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT, true);
      } else {
        boolean clearBefore = true;
        for (SearchEverywhereContributor contributor : getUsedContributors()) {
          int count = myServiceContributors.contains(contributor) ? -1 : MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT; //show ALL items for service contributors
          anyFound |= addContributorItems(contributor, count, clearBefore);
          clearBefore = false;
        }
      }

      if (!anyFound) {
        emptyListAlarm.addRequest(() -> handleEmptyResults(), 50);
      }
    }

    private void showMore(SearchEverywhereContributor contributor) {
      int delta = isAllTabSelected() ? MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT : SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT;
      int size = myListModel.getItemsForContributor(contributor) + delta;
      addContributorItems(contributor, size, false);
    }

    private boolean addContributorItems(SearchEverywhereContributor contributor, int count, boolean clearBefore) {
      ContributorSearchResult<Object> results =
        contributor.search(pattern, isUseNonProjectItems(), myContributorFilters.get(contributor.getSearchProviderId()), myProgressIndicator, count);
      boolean found = !results.isEmpty();

      if (clearBefore) {
        listOperationsAlarm.cancelAllRequests();
      }

      listOperationsAlarm.addRequest(() -> {
        if (isCanceled()) {
          return;
        }

        Dimension oldSize = getPreferredSize();
        if (clearBefore) {
          myListModel.clear();
        }
        List<Object> itemsToAdd = results.getItems().stream()
                                         .filter(o -> !myListModel.contains(o))
                                         .collect(Collectors.toList());
        if (!itemsToAdd.isEmpty()) {
          myListModel.addElements(itemsToAdd, contributor, results.hasMoreItems());
          ScrollingUtil.ensureSelectionExists(myResultsList);
        }
        firePropertyChange("preferredSize", oldSize, getPreferredSize());
      }, 50);

      return found;
    }

    protected void check() {
      myProgressIndicator.checkCanceled();
      if (myDone.isRejected()) throw new ProcessCanceledException();
      assert myCalcThread == this : "There are two CalcThreads running before one of them was cancelled";
    }

    private boolean isCanceled() {
      return myProgressIndicator.isCanceled() || myDone.isRejected();
    }

    public ActionCallback cancel() {
      myProgressIndicator.cancel();
      //myDone.setRejected();
      return myDone;
    }

    public ActionCallback start() {
      ApplicationManager.getApplication().executeOnPooledThread(this);
      return myDone;
    }
  }

  @NotNull
  private List<SearchEverywhereContributor> getUsedContributors() {
    SearchEverywhereContributorFilter<String> contributorsFilter =
      (SearchEverywhereContributorFilter<String>) myContributorFilters.get(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID);

    List<SearchEverywhereContributor> contributors = new ArrayList<>(myServiceContributors);
    myShownContributors.stream()
                       .filter(contributor -> contributorsFilter.isSelected(contributor.getSearchProviderId()))
                       .forEach(contributor -> contributors.add(contributor));
    return contributors;
  }

  private class CompositeCellRenderer implements ListCellRenderer<Object> {

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (myListModel.isMoreElement(index)) {
        Component cmp = moreRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        return wrap(cmp, 1, 7);
      }

      SearchEverywhereContributor contributor = myListModel.getContributorForIndex(index);
      Component component = contributor.getElementsRenderer(myResultsList)
                                       .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (isAllTabSelected() && myListModel.isGroupFirstItem(index)) {
        component = groupTitleRenderer.withDisplayedData(contributor.getGroupName(), component);
      }

      return wrap(component, 1, 0);
    }

    private Component wrap(Component cmp, int verticalGap, int hotizontalGap) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.setOpaque(cmp.isOpaque());
      if (cmp.isOpaque()) {
        panel.setBackground(cmp.getBackground());
      }
      panel.add(cmp, BorderLayout.CENTER);
      panel.setBorder(JBUI.Borders.empty(verticalGap, hotizontalGap));
      return panel;
    }
  }

  private final MoreRenderer moreRenderer = new MoreRenderer();

  public static class MoreRenderer extends JPanel implements ListCellRenderer<Object> {
    final JLabel label;

    private MoreRenderer() {
      super(new BorderLayout());
      label = groupInfoLabel("... more");
      add(label, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      setBackground(UIUtil.getListBackground(isSelected));
      return this;
    }
  }

  private final GroupTitleRenderer groupTitleRenderer = new GroupTitleRenderer();

  public static class GroupTitleRenderer extends JPanel {

    private final JLabel titleLabel;
    private final BorderLayout myLayout = new BorderLayout();

    public GroupTitleRenderer() {
      setLayout(myLayout);
      setBackground(UIUtil.getListBackground(false));
      titleLabel = groupInfoLabel("Group");
      SeparatorComponent separatorComponent =
        new SeparatorComponent(titleLabel.getPreferredSize().height / 2, JBUI.CurrentTheme.SearchEverywhere.listSeparatorColor(),null);

      JPanel topPanel = JBUI.Panels.simplePanel(5, 0)
                                           .addToCenter(separatorComponent)
                                           .addToLeft(titleLabel)
                                           .withBorder(JBUI.Borders.empty(0, 7))
                                           .withBackground(UIUtil.getListBackground());
      add(topPanel, BorderLayout.NORTH);
    }

    public GroupTitleRenderer withDisplayedData(String title, Component itemContent) {
      titleLabel.setText(title);
      Component prevContent = myLayout.getLayoutComponent(BorderLayout.CENTER);
      if (prevContent != null) {
        remove(prevContent);
      }
      add(itemContent, BorderLayout.CENTER);
      return this;
    }
  }

  private static class SearchListModel extends AbstractListModel<Object> {

    private static final Object MORE_ELEMENT = new Object();

    private final List<Pair<Object, SearchEverywhereContributor>> listElements = new ArrayList<>();

    @Override
    public int getSize() {
      return listElements.size();
    }

    @Override
    public Object getElementAt(int index) {
      return listElements.get(index).first;
    }

    public Collection<Object> getFoundItems(SearchEverywhereContributor contributor) {
      return listElements.stream()
                         .filter(pair -> pair.second == contributor && pair.first != MORE_ELEMENT)
                         .map(pair -> pair.getFirst())
                         .collect(Collectors.toList());
    }

    public boolean hasMoreElements(SearchEverywhereContributor contributor) {
      return listElements.stream()
        .anyMatch(pair -> pair.first == MORE_ELEMENT && pair.second == contributor);
    }

    public void addElements(List<Object> items, SearchEverywhereContributor contributor, boolean hasMore) {
      if (items.isEmpty()) {
        return;
      }

      List<Pair<Object, SearchEverywhereContributor>> pairsToAdd = items.stream()
                                                                        .map(o -> Pair.create(o, contributor))
                                                                        .collect(Collectors.toList());

      int insertPoint = contributors().lastIndexOf(contributor);
      int startIndex;
      int endIndex;
      if (insertPoint < 0) {
        // no items of this contributor - add to the end of list
        startIndex = listElements.size();
        listElements.addAll(pairsToAdd);
        if (hasMore) {
          listElements.add(Pair.create(MORE_ELEMENT, contributor));
        }
        endIndex = listElements.size() - 1;
        fireIntervalAdded(this, startIndex, endIndex);
      } else {
        // contributor elements already exists in list - add before 'more' element
        boolean hadMoreBeforeAdd = isMoreElement(insertPoint);
        if (!hadMoreBeforeAdd) {
          insertPoint += 1;
        }
        startIndex = insertPoint;
        endIndex = startIndex + pairsToAdd.size() - 1;
        listElements.addAll(insertPoint, pairsToAdd);

        if (hasMore && !hadMoreBeforeAdd) {
          listElements.add(insertPoint + pairsToAdd.size(), Pair.create(MORE_ELEMENT, contributor));
          endIndex += 1;
        }
        fireIntervalAdded(this, startIndex, endIndex);

        if (hadMoreBeforeAdd && !hasMore) {
          listElements.remove(endIndex + 1);
          fireIntervalRemoved(this, endIndex + 1, endIndex + 1);
        }
      }
    }

    public void clear() {
      int index = listElements.size() - 1;
      listElements.clear();
      if (index >= 0) {
        fireIntervalRemoved(this, 0, index);
      }
    }

    public boolean contains(Object val) {
      return values().contains(val);
    }

    public boolean isMoreElement(int index) {
      return listElements.get(index).first == MORE_ELEMENT;
    }

    public SearchEverywhereContributor getContributorForIndex(int index) {
      return listElements.get(index).second;
    }

    public boolean isGroupFirstItem(int index) {
      return index == 0
        || listElements.get(index).second != listElements.get(index - 1).second;
    }

    public int getItemsForContributor(SearchEverywhereContributor contributor) {
      List<SearchEverywhereContributor> contributorsList = contributors();
      int first = contributorsList.indexOf(contributor);
      int last = contributorsList.lastIndexOf(contributor);
      if (isMoreElement(last)) {
        last -= 1;
      }
      return last - first + 1;
    }

    @NotNull
    private List<SearchEverywhereContributor> contributors() {
      return Lists.transform(listElements, pair -> pair.getSecond());
    }

    @NotNull
    private List<Object> values() {
      return Lists.transform(listElements, pair -> pair.getFirst());
    }
  }

  private class ShowInFindToolWindowAction extends DumbAwareAction {

    public ShowInFindToolWindowAction() {
      super(IdeBundle.message("searcheverywhere.show.in.find.window.button.name"),
        IdeBundle.message("searcheverywhere.show.in.find.window.button.name"), AllIcons.General.Pin_tab);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      stopSearching();

      Collection<SearchEverywhereContributor> contributors = isAllTabSelected() ? getUsedContributors() : Collections.singleton(mySelectedTab.getContributor().get());
      contributors = contributors.stream()
                                 .filter(SearchEverywhereContributor::showInFindResults)
                                 .collect(Collectors.toList());

      if (contributors.isEmpty()) {
        return;
      }

      String searchText = getSearchPattern();
      boolean everywhere = isUseNonProjectItems();

      String contributorsString = contributors.stream()
                                   .map(SearchEverywhereContributor::getGroupName)
                                   .collect(Collectors.joining(", "));

      UsageViewPresentation presentation = new UsageViewPresentation();
      String tabCaptionText = IdeBundle.message("searcheverywhere.found.matches.title", searchText, contributorsString);
      presentation.setCodeUsagesString(tabCaptionText);
      presentation.setUsagesInGeneratedCodeString(IdeBundle.message("searcheverywhere.found.matches.generated.code.title", searchText, contributorsString));
      presentation.setTargetsNodeText(IdeBundle.message("searcheverywhere.found.targets.title", searchText, contributorsString));
      presentation.setTabName(tabCaptionText);
      presentation.setTabText(tabCaptionText);

      Collection<Usage> usages = new LinkedHashSet<>();
      Collection<PsiElement> targets = new LinkedHashSet<>();

      Collection<Object> cached = contributors.stream()
                                              .flatMap(contributor -> myListModel.getFoundItems(contributor).stream())
                                              .collect(Collectors.toList());
      fillUsages(cached, usages, targets);

      Collection<SearchEverywhereContributor> contributorsForAdditionalSearch;
      contributorsForAdditionalSearch = contributors.stream()
                                                    .filter(contributor -> myListModel.hasMoreElements(contributor))
                                                    .collect(Collectors.toList());

      searchFinishedHandler.run();
      if (!contributorsForAdditionalSearch.isEmpty()) {
        ProgressManager.getInstance().run(new Task.Modal(myProject, tabCaptionText, true) {
          private final ProgressIndicator progressIndicator = new ProgressIndicatorBase();

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            contributorsForAdditionalSearch.forEach(contributor -> {
              if (!progressIndicator.isCanceled()) {
                //todo overflow #UX-1
                List<Object> foundElements =
                  contributor.search(searchText, everywhere, myContributorFilters.get(contributor.getSearchProviderId()), progressIndicator);
                fillUsages(foundElements, usages, targets);
              }
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
            progressIndicator.cancel();
          }
        });
      } else {
        showInFindWindow(targets, usages, presentation);
      }
    }

    private void fillUsages(Collection<Object> foundElements, Collection<Usage> usages, Collection<PsiElement> targets) {
      foundElements.stream()
                   .filter(o -> o instanceof PsiElement)
                   .forEach(o -> {
                     PsiElement element = (PsiElement)o;
                     if (element.getTextRange() != null) {
                       UsageInfo usageInfo = new UsageInfo(element);
                       usages.add(new UsageInfo2UsageAdapter(usageInfo));
                     }
                     else {
                       targets.add(element);
                     }
                   });
    }

    private void showInFindWindow(Collection<PsiElement> targets, Collection<Usage> usages, UsageViewPresentation presentation) {
      UsageTarget[] targetsArray = targets.isEmpty() ? UsageTarget.EMPTY_ARRAY : PsiElement2UsageTargetAdapter.convert(PsiUtilCore.toPsiElementArray(targets));
      Usage[] usagesArray = usages.toArray(Usage.EMPTY_ARRAY);
      UsageViewManager.getInstance(myProject).showUsages(targetsArray, usagesArray, presentation);
    }
  }

  private class ShowFilterAction extends ToggleAction implements DumbAware {
    private JBPopup myFilterPopup;

    public ShowFilterAction() {
      super("Filter", "Filter files by type", AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(final AnActionEvent e) {
      return myFilterPopup != null && !myFilterPopup.isDisposed();
    }

    @Override
    public void setSelected(final AnActionEvent e, final boolean state) {
      if (state) {
        showPopup(e.getInputEvent().getComponent());
      }
      else {
        if (myFilterPopup != null && !myFilterPopup.isDisposed()) {
          myFilterPopup.cancel();
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Icon icon = getTemplatePresentation().getIcon();
      e.getPresentation().setIcon(isActive() ? ExecutionUtil.getLiveIndicator(icon) : icon);
      e.getPresentation().setEnabled(myContributorFilters.get(getSelectedContributorID()) != null);
      e.getPresentation().putClientProperty(SELECTED_PROPERTY, isSelected(e));
    }

    private boolean isActive() {
      String contributorID = getSelectedContributorID();
      SearchEverywhereContributorFilter<?> filter = myContributorFilters.get(contributorID);
      if (filter == null) {
        return false;
      }
      return filter.getAllElements().size() != filter.getSelectedElements().size();
    }

    private void showPopup(Component anchor) {
      if (myFilterPopup != null) {
        return;
      }
      JBPopupListener popupCloseListener = new JBPopupListener() {
        @Override
        public void onClosed(LightweightWindowEvent event) {
          myFilterPopup = null;
        }
      };
      myFilterPopup = JBPopupFactory.getInstance()
                                    .createComponentPopupBuilder(createFilterPanel(), null)
                                    .setModalContext(false)
                                    .setFocusable(false)
                                    .setResizable(true)
                                    .setCancelOnClickOutside(false)
                                    .setMinSize(new Dimension(200, 200))
                                    .setDimensionServiceKey(myProject, "Search_Everywhere_Filter_Popup", false)
                                    .addListener(popupCloseListener)
                                    .createPopup();
      Disposer.register(SearchEverywhereUI.this, myFilterPopup);
      myFilterPopup.showUnderneathOf(anchor);
    }

    private JComponent createFilterPanel() {
      SearchEverywhereContributorFilter<?> filter = myContributorFilters.get(getSelectedContributorID());
      ElementsChooser<?> chooser = createChooser(filter);

      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      panel.add(chooser);
      JPanel buttons = new JPanel();
      JButton all = new JButton("All");
      all.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.setAllElementsMarked(true);
        }
      });
      buttons.add(all);
      JButton none = new JButton("None");
      none.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.setAllElementsMarked(false);
        }
      });
      buttons.add(none);
      JButton invert = new JButton("Invert");
      invert.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
          chooser.invertSelection();
        }
      });
      buttons.add(invert);
      panel.add(buttons);
      return panel;
    }

    private <T> ElementsChooser<T> createChooser(SearchEverywhereContributorFilter<T> filter) {
      ElementsChooser<T> res = new ElementsChooser<T>(filter.getAllElements(), false) {
        @Override
        protected String getItemText(@NotNull T value) {
          return filter.getElementText(value);
        }

        @Nullable
        @Override
        protected Icon getItemIcon(@NotNull T value) {
          return filter.getElementIcon(value);
        }
      };
      res.markElements(filter.getSelectedElements());
      ElementsChooser.ElementsMarkListener<T> listener = (element, isMarked) -> {
        filter.setSelected(element, isMarked);
        rebuildList();
      };
      res.addElementsMarkListener(listener);
      return res;
    }
  }

  private static JLabel groupInfoLabel(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    label.setFont(UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL)));
    label.setOpaque(false);
    return label;
  }

  private String getEmptyText() {
    return mySelectedTab.getContributor()
                        .map(c -> IdeBundle.message("searcheverywhere.nothing.found.for.contributor.anywhere", c.getGroupName()))
                        .orElse(IdeBundle.message("searcheverywhere.nothing.found.for.all.anywhere"));
  }
}
