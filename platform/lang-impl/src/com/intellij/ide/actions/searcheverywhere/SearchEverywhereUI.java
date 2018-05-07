// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.SearchEverywhereAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.Alarm;
import com.intellij.util.Range;
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

import static com.intellij.ide.actions.SearchEverywhereAction.SEARCH_EVERYWHERE_POPUP;

/**
 * @author Konstantin Bulenkov
 * @author Mikhail.Sokolov
 */
public class SearchEverywhereUI extends BorderLayoutPanel {
  private static final Logger LOG = Logger.getInstance(SearchEverywhereUI.class);
  public static final int ELEMENTS_LIMIT = 15;

  private final List<SearchEverywhereContributor> allContributors;
  private final Project myProject;

  private boolean myShown;

  private SETab mySelectedTab;
  private final JTextField mySearchField;
  private final JCheckBox myNonProjectCB;
  private final List<SETab> myTabs = new ArrayList<>();

  private JBPopup myResultsPopup;
  private final JBList<Object> myResultsList = new JBList<>();

  private final Map<ResultsRange, SearchEverywhereContributor> myResultsRanges = new HashMap<>();

  private CalcThread myCalcThread;
  private volatile ActionCallback myCurrentWorker = ActionCallback.DONE;
  private int myCalcThreadRestartRequestId = 0;
  private final Object myWorkerRestartRequestLock = new Object();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication());

  private Runnable searchFinishedHandler = () -> {};

  // todo remove second param #UX-1
  public SearchEverywhereUI(Project project,
                            List<SearchEverywhereContributor> contributors,
                            @Nullable SearchEverywhereContributor selected) {
    myProject = project;
    withMinimumWidth(670);
    withPreferredWidth(670);
    setBackground(JBUI.CurrentTheme.SearchEverywhere.dialogBackground());

    allContributors = contributors;

    myNonProjectCB = new JBCheckBox();
    myNonProjectCB.setOpaque(false);
    myNonProjectCB.setFocusable(false);

    JPanel contributorsPanel = createTabPanel(contributors, selected);
    JPanel settingsPanel = createSettingsPanel();
    mySearchField = createSearchField();

    addToLeft(contributorsPanel);
    addToRight(settingsPanel);
    addToBottom(mySearchField);

    myResultsList.setCellRenderer(new CompositeCellRenderer());

    initSearchActions();
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

  public void clear() {
    mySearchField.setText("");
    myNonProjectCB.setSelected(false);
  }

  public void setShown(boolean shown) {
    myShown = shown;
    //todo cancel all threads #UX-1
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
    rebuildList(mySearchField != null ? mySearchField.getText() : "");
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
    Border border = JBUI.Borders.merge(
      JBUI.Borders.empty(insets.top, searchExtension.getPreferredSpace() + insets.left, insets.bottom, hintExtension.getPreferredSpace() + insets.right),
      IdeBorderFactory.createBorder(JBUI.CurrentTheme.SearchEverywhere.searchFieldBorderColor(), SideBorder.BOTTOM | SideBorder.TOP),
      true);
    searchField.setBorder(border);
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

    ToggleAction pinAction = new ToggleAction(null, null, AllIcons.General.AutohideOff) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return UISettings.getInstance().getPinFindInPath();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        UISettings.getInstance().setPinFindInPath(state);
      }
    };
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

  private void rebuildList(final String pattern) {
    assert EventQueue.isDispatchThread() : "Must be EDT";
    if (myCalcThread != null && !myCurrentWorker.isProcessed()) {
      myCurrentWorker = myCalcThread.cancel();
    }
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }

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
          myCalcThread = new CalcThread(myProject, pattern, false);

          myCurrentWorker = myCalcThread.start();
        }
      });
    }
  }

  private void initSearchActions() {
    mySearchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_TAB && e.getModifiers() == 0) {
          switchToNextTab();
          e.consume();
        }
      }
    });

    AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(__ -> searchFinishedHandler.run())
                   .registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), this);

    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        rebuildList(mySearchField.getText());
      }
    });

    myResultsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        e.consume();
        final int i = myResultsList.locationToIndex(e.getPoint());
        if (i != -1) {
          ApplicationManager.getApplication().invokeLater(() -> {
            myResultsList.setSelectedIndex(i);
            elementSelected(i);
          });
        }
      }
    });

  }

  private void elementSelected(int i) {
    Map.Entry<ResultsRange, SearchEverywhereContributor> entry = myResultsRanges.entrySet().stream()
         .filter(e -> e.getKey().containsIndex(i))
         .findAny()
         .orElseThrow(() -> new IllegalStateException("Contributor for element is not specified"));

    Boolean isMoreElement = entry.getKey().getMoreElementIndex().map(moreIndex -> moreIndex.equals(i)).orElse(false);
    if (isMoreElement) {
      showMoreElements(entry.getValue());
    } else {
      gotoSelectedItem(entry.getValue());
    }
  }

  private void showMoreElements(SearchEverywhereContributor contributor) {

  }

  private void gotoSelectedItem(SearchEverywhereContributor contributor) {
    Object value = myResultsList.getSelectedValue();
    stopSearching();
    searchFinishedHandler.run();
    contributor.processSelectedItem(value);
  }

  private void stopSearching() {
    myAlarm.cancelAllRequests();
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
    private final SearchEverywhereAction.SearchListModel myListModel; //todo use usual model #UX-1

    public CalcThread(Project project, String pattern, boolean reuseModel) {
      this.project = project;
      this.pattern = pattern;
      myListModel = reuseModel ? (SearchEverywhereAction.SearchListModel) myResultsList.getModel() : new SearchEverywhereAction.SearchListModel();
    }

    @Override
    public void run() {
      try {
        check();

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          // this line must be called on EDT to avoid context switch at clear().append("text") Don't touch. Ask [kb]
          myResultsList.getEmptyText().setText("Searching...");

          if (myResultsList.getModel() instanceof SearchEverywhereAction.SearchListModel) {
            //noinspection unchecked
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(() -> {
              if (!myDone.isRejected()) {
                myResultsList.setModel(myListModel);
                updatePopup();
              }
            }, 50);
          } else {
            myResultsList.setModel(myListModel);
          }
        });

        myResultsRanges.clear();
        SearchEverywhereContributor selectedContributor = mySelectedTab.getContributor().orElse(null);
        if (selectedContributor != null) {
          runReadAction(() -> addContributorItems(selectedContributor, true), true);
        } else {
          for (SearchEverywhereContributor contributor : allContributors) {
            runReadAction(() -> addContributorItems(contributor, false), true);
          }
        }
        updatePopup();
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
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> myResultsList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT));
          updatePopup();
        }
        if (!myDone.isProcessed()) {
          myDone.setDone();
        }
      }
    }

    private void addContributorItems(SearchEverywhereContributor contributor, boolean exclusiveContributor) {
      SearchEverywhereContributor.ContributorSearchResult
        results = contributor.search(project, pattern, isUseNonProjectItems(), myProgressIndicator, ELEMENTS_LIMIT);
      if (!results.isEmpty()) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;

          int start = myListModel.getSize();
          for (Object item : results.getItems()) {
            myListModel.addElement(item);
          }
          int end = myListModel.getSize() - 1;

          if (results.hasMoreItems()) {
            myListModel.addElement("more");
          }
          ResultsRange range = results.hasMoreItems() ? new ResultsRange(start, end, end + 1) : new ResultsRange(start, end);
          myResultsRanges.put(range, contributor);
        });
      }
    }

    private void runReadAction(Runnable action, boolean checkDumb) {
      if (!checkDumb || !DumbService.getInstance(project).isDumb()) {
        ApplicationManager.getApplication().runReadAction(action);
        updatePopup();
      }
    }

    protected void check() {
      myProgressIndicator.checkCanceled();
      if (myDone.isRejected()) throw new ProcessCanceledException();
      if (!myShown) throw new ProcessCanceledException();
      assert myCalcThread == this : "There are two CalcThreads running before one of them was cancelled";
    }

    private boolean isCanceled() {
      return myProgressIndicator.isCanceled() || myDone.isRejected();
    }

    @SuppressWarnings("SSBasedInspection")
    private void updatePopup() {
      check();
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myListModel.update();
          myResultsList.revalidate();
          myResultsList.repaint();

          //myRenderer.recalculateWidth();
          if (!myShown) {
            return;
          }
          if (myResultsPopup == null || !myResultsPopup.isVisible()) {
            ScrollingUtil.installActions(myResultsList, getSearchField());
            JBScrollPane content = new JBScrollPane(myResultsList) {
              {
                if (UIUtil.isUnderDarcula()) {
                  setBorder(null);
                }
              }
              @Override
              public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Dimension listSize = myResultsList.getPreferredSize();
                if (size.height > listSize.height || myResultsList.getModel().getSize() == 0) {
                  size.height = Math.max(JBUI.scale(30), listSize.height);
                }

                if (size.width < getWidth()) {
                  size.width = getWidth();
                }

                return size;
              }
            };
            content.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            content.setMinimumSize(new Dimension(getWidth(), 30));
            final ComponentPopupBuilder builder = JBPopupFactory.getInstance()
                                                                .createComponentPopupBuilder(content, null);
            myResultsPopup = builder
              .setRequestFocus(false)
              .setCancelKeyEnabled(false)
              .setResizable(true)
              .setCancelCallback(() -> {
                final AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
                if (event instanceof MouseEvent) {
                  final Component comp = ((MouseEvent)event).getComponent();
                  if (UIUtil.getWindow(comp) == UIUtil.getWindow(SearchEverywhereUI.this)) {
                    return false;
                  }
                }
                //final boolean canClose = balloon == null || balloon.isDisposed() || (!getSearchField().hasFocus() && !mySkipFocusGain);
                //if (canClose) {
                //  PropertiesComponent.getInstance().setValue("search.everywhere.max.popup.width", Math.max(content.getWidth(), JBUI.scale(600)), JBUI.scale(600));
                //}
                return true;
              })
              .setShowShadow(false)
              .setShowBorder(false)
              .createPopup();
            project.putUserData(SEARCH_EVERYWHERE_POPUP, myResultsPopup);
            //myResultsPopup.setMinimumSize(new Dimension(myBalloon.getSize().width, 30));
            myResultsPopup.getContent().setBorder(null);
            Disposer.register(myResultsPopup, new Disposable() {
              @Override
              public void dispose() {
                project.putUserData(SEARCH_EVERYWHERE_POPUP, null);
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                  //noinspection SSBasedInspection
                  SwingUtilities.invokeLater(() -> ActionToolbarImpl.updateAllToolbarsImmediately());
                });
              }
            });
            updateResultsPopupBounds();
            myResultsPopup.show(new RelativePoint(SearchEverywhereUI.this, new Point(0, getHeight())));

            //ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
            //  @Override
            //  public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
            //    if (action instanceof TextComponentEditorAction) {
            //      return;
            //    }
            //    if (myResultsPopup != null) {
            //      myResultsPopup.cancel();
            //    }
            //  }
            //}, myResultsPopup);
          }
          else {
            myResultsList.revalidate();
            myResultsList.repaint();
          }
          ScrollingUtil.ensureSelectionExists(myResultsList);
          if (myResultsList.getModel().getSize() > 0) {
            updateResultsPopupBounds();
          }
        }
      });
    }

    public ActionCallback cancel() {
      myProgressIndicator.cancel();
      //myDone.setRejected();
      return myDone;
    }

    //public ActionCallback insert(final int index, final SearchEverywhereAction.WidgetID id) {
    //  ApplicationManager.getApplication().executeOnPooledThread(() -> runReadAction(() -> {
    //    try {
    //
    //      check();
    //      SwingUtilities.invokeLater(() -> {
    //        try {
    //          int shift = 0;
    //          int i = index+1;
    //          for (Object o : result) {
    //            //noinspection unchecked
    //            myListModel.insertElementAt(o, i);
    //            shift++;
    //            i++;
    //          }
    //          SearchEverywhereAction.MoreIndex moreIndex = myListModel.moreIndex;
    //          myListModel.titleIndex.shift(index, shift);
    //          moreIndex.shift(index, shift);
    //
    //          if (!result.needMore) {
    //            switch (id) {
    //              case CLASSES: moreIndex.classes = -1; break;
    //              case FILES: moreIndex.files = -1; break;
    //              case ACTIONS: moreIndex.actions = -1; break;
    //              case SETTINGS: moreIndex.settings = -1; break;
    //              case SYMBOLS: moreIndex.symbols = -1; break;
    //              case RUN_CONFIGURATIONS: moreIndex.runConfigurations = -1; break;
    //            }
    //          }
    //          ScrollingUtil.selectItem(myResultsList, index);
    //          myDone.setDone();
    //        }
    //        catch (Exception e) {
    //          myDone.setRejected();
    //        }
    //      });
    //    }
    //    catch (Exception e) {
    //      myDone.setRejected();
    //    }
    //  }, true));
    //  return myDone;
    //}

    public ActionCallback start() {
      ApplicationManager.getApplication().executeOnPooledThread(this);
      return myDone;
    }
  }

  private  class CompositeCellRenderer implements ListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (isMoreElement(index)) {
        return moreRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }

      Map.Entry<ResultsRange, SearchEverywhereContributor> delegateEntry = myResultsRanges.entrySet().stream()
               .filter(entry -> entry.getKey().getElementsRange()
               .isWithin(index, true))
               .findAny()
               .orElseThrow(() -> new IllegalStateException("Contributor for element is not specified"));

      SearchEverywhereContributor contributor = delegateEntry.getValue();
      Component component = contributor.getElementsRenderer(myProject)
                                       .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      int rangeStart = delegateEntry.getKey().getElementsRange().getFrom();
      boolean allGroupSelected = SearchEverywhereContributor.ALL_CONTRIBUTORS_GROUP_ID.equals(getSelectedContributorID());
      if (allGroupSelected && rangeStart == index) {
        return groupTitleRenderer.withDisplayedData(contributor.getGroupName(), component);
      }

      return component;
    }

    private boolean isMoreElement(int index) {
      return myResultsRanges.entrySet().stream()
                            .map(entry -> entry.getKey().getMoreElementIndex().orElse(null))
                            .filter(Objects::nonNull)
                            .anyMatch(i -> i.equals(index));
    }
  }

  private static class ResultsRange {
    private final Range<Integer> elementsRange;
    private final Integer moreElementIndex;

    private ResultsRange(int from, int to, Integer moreIndex) {
      elementsRange = new Range<>(from, to);
      moreElementIndex = moreIndex;
    }

    public ResultsRange(int from, int to) {
      this(from, to, null);
    }

    public Range<Integer> getElementsRange() {
      return elementsRange;
    }

    public Optional<Integer> getMoreElementIndex() {
      return Optional.ofNullable(moreElementIndex);
    }

    public boolean containsIndex(int i) {
      return elementsRange.isWithin(i, true) || (moreElementIndex != null && moreElementIndex.equals(i));
    }
  }

  private static final MoreRenderer moreRenderer = new MoreRenderer();

  public static class MoreRenderer extends JPanel implements ListCellRenderer<Object> {
    final JLabel label;

    private MoreRenderer() {
      super(new BorderLayout());
      label = groupInfoLabel("    ... more   ");
      add(label, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      setBackground(UIUtil.getListBackground(isSelected));
      return this;
    }
  }

  private static final GroupTitleRenderer groupTitleRenderer = new GroupTitleRenderer();

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

  private static JLabel groupInfoLabel(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    label.setFont(UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL)));
    label.setOpaque(false);
    return label;
  }

  private void updateResultsPopupBounds() {
    int height = myResultsList.getPreferredSize().height + 2;
    int width = getWidth();
    myResultsPopup.setSize(JBUI.size(width, height));
  }
}
