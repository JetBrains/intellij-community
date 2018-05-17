// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
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
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 * @author Mikhail.Sokolov
 */
public class SearchEverywhereUI extends BorderLayoutPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(SearchEverywhereUI.class);
  public static final int SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT = 15;
  public static final int MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT = 8;

  private final List<SearchEverywhereContributor> allContributors;
  private final Project myProject;

  private SETab mySelectedTab;
  private final JTextField mySearchField;
  private final JCheckBox myNonProjectCB;
  private final List<SETab> myTabs = new ArrayList<>();

  private final JBList<Object> myResultsList;
  private final SearchListModel myListModel = new SearchListModel(); //todo using in different threads? #UX-1

  private CalcThread myCalcThread;
  private volatile ActionCallback myCurrentWorker = ActionCallback.DONE;
  private int myCalcThreadRestartRequestId = 0;
  private final Object myWorkerRestartRequestLock = new Object();
  private final Alarm modelOperationsAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication());

  private Runnable searchFinishedHandler = () -> {};
  private final JPanel mySuggestionsPanel;

  // todo remove second param #UX-1
  public SearchEverywhereUI(Project project,
                            List<SearchEverywhereContributor> contributors,
                            @Nullable SearchEverywhereContributor selected) {
    withMinimumWidth(670);
    withPreferredWidth(670);
    withBackground(JBUI.CurrentTheme.SearchEverywhere.dialogBackground());

    myProject = project;
    allContributors = contributors;

    myNonProjectCB = new JBCheckBox();
    myNonProjectCB.setOpaque(false);
    myNonProjectCB.setFocusable(false);

    myResultsList = new JBList<Object>() {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
        Dimension size = super.getPreferredScrollableViewportSize();
        size.height = Integer.min(myResultsList.getPreferredSize().height, JBUI.CurrentTheme.SearchEverywhere.maxListHeght());
        return size;
      }
    };

    JPanel contributorsPanel = createTabPanel(contributors, selected);
    JPanel settingsPanel = createSettingsPanel();
    mySearchField = createSearchField();
    mySuggestionsPanel = createSuggestionsPanel();

    myResultsList.setModel(myListModel);
    myResultsList.setCellRenderer(new CompositeCellRenderer());

    ScrollingUtil.installActions(myResultsList, getSearchField());

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.setOpaque(false);
    topPanel.add(contributorsPanel, BorderLayout.WEST);
    topPanel.add(settingsPanel, BorderLayout.EAST);
    topPanel.add(mySearchField, BorderLayout.SOUTH);

    addToTop(topPanel);

    initSearchActions();
  }

  private JPanel createSuggestionsPanel() {
    JPanel pnl = new JPanel(new BorderLayout());
    pnl.setOpaque(false);
    pnl.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.SearchEverywhere.searchFieldBorderColor(), 1, 0, 0, 0));

    JScrollPane resultsScroll = new JBScrollPane(myResultsList);
    resultsScroll.setBorder(null);
    resultsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pnl.add(resultsScroll, BorderLayout.CENTER);

    String hint = IdeBundle.message("searcheverywhere.history.shortcuts.hint",
                                    KeymapUtil.getKeystrokeText(SearchTextField.ALT_SHOW_HISTORY_KEYSTROKE),
                                    KeymapUtil.getKeystrokeText(SearchTextField.SHOW_HISTORY_KEYSTROKE));
    JLabel hintLabel = new JLabel(hint);
    hintLabel.setOpaque(false);
    hintLabel.setForeground(JBColor.GRAY);
    pnl.add(hintLabel, BorderLayout.SOUTH);

    return pnl;
  }

  public JTextField getSearchField() {
    return mySearchField;
  }

  public void setUseNonProjectItems(boolean use) {
    myNonProjectCB.setSelected(use);
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

  private void switchToNextTab() {
    int currentIndex = myTabs.indexOf(mySelectedTab);
    SETab nextTab = currentIndex == myTabs.size() - 1 ? myTabs.get(0) : myTabs.get(currentIndex + 1);
    switchToTab(nextTab);
  }

  private void switchToTab(SETab tab) {
    mySelectedTab = tab;
    String text = tab.getContributor()
      .map(SearchEverywhereContributor::includeNonProjectItemsText)
      .orElse(IdeBundle.message("checkbox.include.non.project.items", IdeUICustomization.getInstance().getProjectConceptName()));
    if (text.indexOf(UIUtil.MNEMONIC) != -1) {
      DialogUtil.setTextWithMnemonic(myNonProjectCB, text);
    } else {
      myNonProjectCB.setText(text);
      myNonProjectCB.setDisplayedMnemonicIndex(-1);
      myNonProjectCB.setMnemonic(0);
    }
    myNonProjectCB.setSelected(false);
    repaint();
    rebuildList();
  }

  private boolean isAllTabSelected() {
    return SearchEverywhereContributor.ALL_CONTRIBUTORS_GROUP_ID.equals(getSelectedContributorID());
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
        return AllIcons.Actions.Search;
      }

      @Override
      public boolean isIconBeforeText() {
        return true;
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

    AnAction pinAction = new ShowInFindToolWindowAction();
    ActionButton pinButton = new ActionButton(pinAction, pinAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    res.add(pinButton);
    res.add(Box.createHorizontalStrut(JBUI.scale(10)));

    AnAction emptyAction = new AnAction(AllIcons.General.Filter) {
      @Override
      public void actionPerformed(AnActionEvent e) {}
    };
    ActionButton filterButton = new ActionButton(emptyAction, emptyAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    res.add(filterButton);
    res.add(Box.createHorizontalStrut(JBUI.scale(10)));

    return res;
  }

  @NotNull
  private JPanel createTabPanel(List<SearchEverywhereContributor> contributors, @Nullable SearchEverywhereContributor selected) {
    JPanel contributorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    contributorsPanel.setOpaque(false);

    SETab allTab = new SETab(null);
    contributorsPanel.add(allTab);
    myTabs.add(allTab);


    contributors.forEach(contributor -> {
      SETab tab = new SETab(contributor);
      if (contributor == selected) {
        switchToTab(tab);
      }
      contributorsPanel.add(tab);
      myTabs.add(tab);
    });

    if (mySelectedTab == null) {
      switchToTab(allTab);
    }

    return contributorsPanel;
  }

  private void showSuggestions(boolean show) {
    Container parent = mySuggestionsPanel.getParent();

    Dimension oldSize = null;
    Dimension newSize = null;

    if (show && parent != this) {
      oldSize = getPreferredSize();
      addToCenter(mySuggestionsPanel);
      newSize = getPreferredSize();
    } else if (!show && parent == this) {
      oldSize = getPreferredSize();
      remove(mySuggestionsPanel);
      newSize = getPreferredSize();
    }

    if (oldSize != null && newSize != null) {
      firePropertyChange("preferredSize", oldSize, newSize);
    }
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
        .orElse(SearchEverywhereContributor.ALL_CONTRIBUTORS_GROUP_ID);
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

    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
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
          myCalcThread = new CalcThread(myProject, pattern, null);

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
        if (e.getKeyCode() == KeyEvent.VK_TAB && e.getModifiers() == 0) {
          switchToNextTab();
          e.consume();
        }

        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          elementSelected(myResultsList.getSelectedIndex(), e.getModifiers());
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
        boolean show = !getSearchPattern().isEmpty();
        if (show) {
          rebuildList();
        }
        showSuggestions(show);
      }
    });

    myNonProjectCB.addItemListener(e -> rebuildList());

    myResultsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          e.consume();
          final int i = myResultsList.locationToIndex(e.getPoint());
          if (i != -1) {
            myResultsList.setSelectedIndex(i);
            elementSelected(i, e.getModifiers());
          }
        }
      }
    });
  }

  private void elementSelected(int i, int modifiers) {
    SearchEverywhereContributor contributor = myListModel.getContributorForIndex(i);
    if (myListModel.isMoreElement(i)) {
      showMoreElements(contributor);
    } else {
      gotoSelectedItem(myListModel.getElementAt(i), contributor, modifiers);
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
          myCalcThread = new CalcThread(myProject, getSearchPattern(), contributor);

          myCurrentWorker = myCalcThread.start();
        }
      });
    }
  }

  private void gotoSelectedItem(Object value, SearchEverywhereContributor contributor, int modifiers) {
    boolean closePopup = contributor.processSelectedItem(value, modifiers);
    if (closePopup) {
      stopSearching();
      searchFinishedHandler.run();
    }
  }

  private void stopSearching() {
    modelOperationsAlarm.cancelAllRequests();
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }
  }

  @SuppressWarnings("Duplicates") //todo remove suppress #UX-1
  private class CalcThread implements Runnable {
    private final Project project;
    private final String pattern;
    private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();
    private final ActionCallback myDone = new ActionCallback();
    private final SearchEverywhereContributor contributorToExpand;

    public CalcThread(@NotNull Project project, @NotNull String pattern, @Nullable SearchEverywhereContributor expand) {
      this.project = project;
      this.pattern = pattern;
      contributorToExpand = expand;
    }

    @Override
    public void run() {
      try {
        check();

        // this line must be called on EDT to avoid context switch at clear().append("text") Don't touch. Ask [kb]
        modelOperationsAlarm.addRequest(() -> myResultsList.getEmptyText().setText("Searching..."), 0);

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
          modelOperationsAlarm.addRequest(() -> myResultsList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT), 0);
        }
        if (!myDone.isProcessed()) {
          myDone.setDone();
        }
      }
    }

    private void resetList() {
      modelOperationsAlarm.cancelAllRequests();
      modelOperationsAlarm.addRequest(() -> {
        Dimension oldSize = getPreferredSize();
        myListModel.clear();
        Dimension newSize = getPreferredSize();
        firePropertyChange("preferredSize", oldSize, newSize);
      }, 200);

      SearchEverywhereContributor selectedContributor = mySelectedTab.getContributor().orElse(null);
      if (selectedContributor != null) {
        addContributorItems(selectedContributor, SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT, true);
      } else {
        boolean clearBefore = true;
        for (SearchEverywhereContributor contributor : allContributors) {
          addContributorItems(contributor, MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT, clearBefore);
          clearBefore = false;
        }
      }
    }

    private void showMore(SearchEverywhereContributor contributor) {
      int delta = isAllTabSelected() ? MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT : SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT;
      int size = myListModel.getItemsForContributor(contributor) + delta;
      addContributorItems(contributor, size, false);
    }

    private void addContributorItems(SearchEverywhereContributor contributor, int count, boolean clearBefore) {
      if (!DumbService.getInstance(project).isDumb()) {
        ApplicationManager.getApplication().runReadAction(() -> {
          ContributorSearchResult<Object> results = contributor.search(project, pattern, isUseNonProjectItems(), myProgressIndicator, count);

          if (clearBefore) {
            modelOperationsAlarm.cancelAllRequests();
          }

          modelOperationsAlarm.addRequest(() -> {
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
          }, 0);
        });
      }
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

  private class CompositeCellRenderer implements ListCellRenderer<Object> {

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (myListModel.isMoreElement(index)) {
        return moreRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }

      SearchEverywhereContributor contributor = myListModel.getContributorForIndex(index);
      Component component = contributor.getElementsRenderer(myProject)
                                       .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (isAllTabSelected() && myListModel.isGroupFirstItem(index)) {
        return groupTitleRenderer.withDisplayedData(contributor.getGroupName(), component);
      }

      return component;
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
      SeparatorComponent separatorComponent = new SeparatorComponent(titleLabel.getPreferredSize().height / 2, UIUtil.getLabelDisabledForeground(), null);


      JPanel topPanel = JBUI.Panels.simplePanel(5, 0)
                                           .addToCenter(separatorComponent)
                                           .addToLeft(titleLabel)
                                           .withBorder(JBUI.Borders.empty())
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
        // no items of this contributor
        startIndex = listElements.size();
        listElements.addAll(pairsToAdd);
        if (hasMore) {
          listElements.add(Pair.create(MORE_ELEMENT, contributor));
        }
        endIndex = listElements.size() - 1;
      } else {
        // contributor elements already exists in list
        if (isMoreElement(insertPoint)) {
          listElements.remove(insertPoint);
        } else {
          insertPoint += 1;
        }
        startIndex = insertPoint;
        endIndex = startIndex + pairsToAdd.size();
        listElements.addAll(insertPoint, pairsToAdd);
        if (hasMore) {
          listElements.add(insertPoint + pairsToAdd.size(), Pair.create(MORE_ELEMENT, contributor));
          endIndex += 1;
        }
      }

      fireIntervalAdded(this, startIndex, endIndex);
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

      Collection<SearchEverywhereContributor> contributors = isAllTabSelected() ? allContributors : Collections.singleton(mySelectedTab.getContributor().get());
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
                ApplicationManager.getApplication().runReadAction(() -> {
                  //todo overflow #UX-1
                  List<Object> foundElements = contributor.search(myProject, searchText, everywhere, progressIndicator);
                  fillUsages(foundElements, usages, targets);
                });
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

  private static JLabel groupInfoLabel(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    label.setFont(UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL)));
    label.setOpaque(false);
    return label;
  }
}
