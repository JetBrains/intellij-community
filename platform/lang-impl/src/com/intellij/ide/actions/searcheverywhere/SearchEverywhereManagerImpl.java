// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.actions.*;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor.ContributorSearchResult;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.Language;
import com.intellij.lang.LanguagePsiElementExternalizer;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.intellij.ide.actions.SearchEverywhereAction.SEARCH_EVERYWHERE_POPUP;

public class SearchEverywhereManagerImpl implements SearchEverywhereManager {

  private static final Logger LOG = Logger.getInstance(SearchEverywhereManagerImpl.class);
  public static final int ELEMENTS_LIMIT = 15;

  private final Project myProject;

  private JBPopup myBalloon; //todo appropriate names #UX-1
  private JBPopup myPopup;
  private final SearchEverywhereUI mySearchEverywhereUI;
  private final JBList<Object> myList = new JBList<>();

  private CalcThread myCalcThread;
  private volatile ActionCallback myCurrentWorker = ActionCallback.DONE;
  private int myCalcThreadRestartRequestId = 0;
  private final Object myWorkerRestartRequestLock = new Object();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication());
  private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();

  private final List<SearchEverywhereContributor> allContributors = SearchEverywhereContributor.getProvidersSorted();

  public SearchEverywhereManagerImpl(Project project) {
    myProject = project;

    //SearchEverywhereContributor selected = contributors.stream()
    //                                                   .filter(contributor -> contributor.getSearchProviderId().equals(mySelectedProviderID))
    //                                                   .findAny()
    //                                                   .orElse(null);
    mySearchEverywhereUI = new SearchEverywhereUI(allContributors, null);
    JTextField editor = mySearchEverywhereUI.getSearchField();
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (isShown()) {
          rebuildList(editor.getText());
        }
      }
    });
  }

  @Override
  public void show(@NotNull String selectedContributorID) {
    if (isShown()) {
      setShownContributor(selectedContributorID);
    }
    else {
      mySearchEverywhereUI.clear();
      mySearchEverywhereUI.switchToContributor(selectedContributorID);
      myBalloon = JBPopupFactory.getInstance().createComponentPopupBuilder(mySearchEverywhereUI, getSearchField())
                                .setProject(myProject)
                                .setResizable(false)
                                .setModalContext(false)
                                .setCancelOnClickOutside(true)
                                .setRequestFocus(true)
                                .setCancelKeyEnabled(false)
                                .setCancelCallback(() -> true)
                                .addUserData("SIMPLE_WINDOW")
                                .createPopup();

      AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
      DumbAwareAction.create(__ -> myBalloon.cancel())
                     .registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(),
                                                myBalloon.getContent(), myBalloon);

      RelativePoint showingPoint = calculateShowingPoint();
      if (showingPoint != null) {
        myBalloon.show(showingPoint);
      }
      else {
        myBalloon.showInFocusCenter();
      }
    }

  }

  @Override
  public boolean isShown() {
    return myBalloon != null && !myBalloon.isDisposed();
  }

  @Override
  public SearchEverywhereContributor getShownContributor() {
    return mySearchEverywhereUI.getSelectedContributor();
  }

  @Override
  public void setShownContributor(@NotNull String contributorID) {
    if (!contributorID.equals(getShownContributor().getSearchProviderId())) {
      mySearchEverywhereUI.switchToContributor(contributorID);
    }
  }

  @Override
  public boolean isShowNonProjectItems() {
    return mySearchEverywhereUI.isUseNonProjectItems();
  }

  @Override
  public void setShowNonProjectItems(boolean show) {
    mySearchEverywhereUI.setUseNonProjectItems(show);
  }

  private RelativePoint calculateShowingPoint() {
    final Window window = myProject != null
                          ? WindowManager.getInstance().suggestParentWindow(myProject)
                          : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    Component parent = UIUtil.findUltimateParent(window);
    if (parent == null) {
      return null;
    }

    int height = UISettings.getInstance().getShowMainToolbar() ? 135 : 115;
    if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
      height -= 20;
    }
    return new RelativePoint(parent, new Point((parent.getSize().width - mySearchEverywhereUI.getPreferredSize().width) / 2, height));
  }

  private void rebuildList(final String pattern) {
    assert EventQueue.isDispatchThread() : "Must be EDT";
    if (myCalcThread != null && !myCurrentWorker.isProcessed()) {
      myCurrentWorker = myCalcThread.cancel();
    }
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }

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

  @SuppressWarnings("Duplicates") //todo remove suppress #UX-1
  private class CalcThread implements Runnable {
    private final Project project;
    private final String pattern;
    private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();
    private final ActionCallback myDone = new ActionCallback();
    private final SearchEverywhereAction.SearchListModel myListModel;
    private final ArrayList<VirtualFile> myAlreadyAddedFiles = new ArrayList<>();
    private final ArrayList<AnAction> myAlreadyAddedActions = new ArrayList<>();


    public CalcThread(Project project, String pattern, boolean reuseModel) {
      this.project = project;
      this.pattern = pattern;
      myListModel = reuseModel ? (SearchEverywhereAction.SearchListModel)myList.getModel() : new SearchEverywhereAction.SearchListModel();
    }

    @Override
    public void run() {
      try {
        check();

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          // this line must be called on EDT to avoid context switch at clear().append("text") Don't touch. Ask [kb]
          myList.getEmptyText().setText("Searching...");

          if (myList.getModel() instanceof SearchEverywhereAction.SearchListModel) {
            //noinspection unchecked
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(() -> {
              if (!myDone.isRejected()) {
                myList.setModel(myListModel);
                updatePopup();
              }
            }, 50);
          } else {
            myList.setModel(myListModel);
          }
        });

        //if (pattern.trim().length() == 0) {
        //  buildModelFromRecentFiles();
        //  //updatePopup();
        //  return;
        //}

        //checkModelsUpToDate();              check();
        //buildTopHit(pattern);               check();

        if (!pattern.startsWith("#")) {
          //buildRecentFiles(pattern);
          //check();

          SearchEverywhereContributor selectedContributor = mySearchEverywhereUI.getSelectedContributor();
          if (selectedContributor != null) {
            runReadAction(() -> addContributorItems(selectedContributor, true), true);
          } else {
            for (SearchEverywhereContributor contributor : allContributors) {
              runReadAction(() -> addContributorItems(contributor, false), true);
            }
          }

          //runReadAction(() -> buildStructure(pattern), true);
          //updatePopup();
          //check();
          //buildToolWindows(pattern);
          //check();
          //updatePopup();
          //check();
          //
          //checkModelsUpToDate();
          //runReadAction(() -> buildRunConfigurations(pattern), true);
          //runReadAction(() -> buildClasses(pattern), true);
          //runReadAction(() -> buildFiles(pattern), false);
          //runReadAction(() -> buildSymbols(pattern), true);
          //
          //buildActionsAndSettings(pattern);
          //
          //updatePopup();

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
          SwingUtilities.invokeLater(() -> myList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT));
          updatePopup();
        }
        if (!myDone.isProcessed()) {
          myDone.setDone();
        }
      }
    }

    private void addContributorItems(SearchEverywhereContributor contributor, boolean exclusiveContributor) {
      ContributorSearchResult results = contributor.search(myProject, pattern, mySearchEverywhereUI.isUseNonProjectItems(), myProgressIndicator, ELEMENTS_LIMIT);
      if (!results.isEmpty()) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;

          if (!exclusiveContributor) {
            myListModel.titleIndex.classes = myListModel.size();
          }
          for (Object item : results.getItems()) {
            myListModel.addElement(item);
          }
          if (!exclusiveContributor) {
            myListModel.moreIndex.classes = results.hasMoreItems() ? myListModel.size() - 1 : -1;
          }
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
      if (myBalloon == null || myBalloon.isDisposed()) throw new ProcessCanceledException();
      assert myCalcThread == this : "There are two CalcThreads running before one of them was cancelled";
    }



    @NotNull
    private GlobalSearchScope getProjectScope(@NotNull Project project) {
      final GlobalSearchScope scope = SearchEverywhereClassifier.EP_Manager.getProjectScope(project);
      if (scope != null) return scope;
      return GlobalSearchScope.projectScope(project);
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
          myList.revalidate();
          myList.repaint();

          //myRenderer.recalculateWidth();
          if (myBalloon == null || myBalloon.isDisposed()) {
            return;
          }
          if (myPopup == null || !myPopup.isVisible()) {
            ScrollingUtil.installActions(myList, getSearchField());
            JBScrollPane content = new JBScrollPane(myList) {
              {
                if (UIUtil.isUnderDarcula()) {
                  setBorder(null);
                }
              }
              @Override
              public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Dimension listSize = myList.getPreferredSize();
                if (size.height > listSize.height || myList.getModel().getSize() == 0) {
                  size.height = Math.max(JBUI.scale(30), listSize.height);
                }

                if (myBalloon != null && size.width < myBalloon.getSize().width) {
                  size.width = myBalloon.getSize().width;
                }

                return size;
              }
            };
            content.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            content.setMinimumSize(new Dimension(myBalloon.getSize().width, 30));
            final ComponentPopupBuilder builder = JBPopupFactory.getInstance()
                                                                .createComponentPopupBuilder(content, null);
            myPopup = builder
              .setRequestFocus(false)
              .setCancelKeyEnabled(false)
              .setResizable(true)
              .setCancelCallback(() -> {
                final JBPopup balloon = myBalloon;
                final AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
                if (event instanceof MouseEvent) {
                  final Component comp = ((MouseEvent)event).getComponent();
                  if (balloon != null && UIUtil.getWindow(comp) == UIUtil.getWindow(balloon.getContent())) {
                    return false;
                  }
                }
                final boolean canClose = balloon == null || balloon.isDisposed();// || (!getSearchField().hasFocus() && !mySkipFocusGain);
                //if (canClose) {
                //  PropertiesComponent.getInstance().setValue("search.everywhere.max.popup.width", Math.max(content.getWidth(), JBUI.scale(600)), JBUI.scale(600));
                //}
                return canClose;
              })
              .setShowShadow(false)
              .setShowBorder(false)
              .createPopup();
            project.putUserData(SEARCH_EVERYWHERE_POPUP, myPopup);
            //myPopup.setMinimumSize(new Dimension(myBalloon.getSize().width, 30));
            myPopup.getContent().setBorder(null);
            Disposer.register(myPopup, new Disposable() {
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
            myPopup.show(new RelativePoint(mySearchEverywhereUI, new Point(0, mySearchEverywhereUI.getHeight())));

            ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
              @Override
              public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
                if (action instanceof TextComponentEditorAction) {
                  return;
                }
                if (myPopup!=null) {
                  myPopup.cancel();
                }
              }
            }, myPopup);
          }
          else {
            myList.revalidate();
            myList.repaint();
          }
          ScrollingUtil.ensureSelectionExists(myList);
          if (myList.getModel().getSize() > 0) {
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
    //          ScrollingUtil.selectItem(myList, index);
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

  private void updateResultsPopupBounds() {
    int height = myList.getPreferredSize().height + 2;
    int width = mySearchEverywhereUI.getWidth();
    myPopup.setSize(JBUI.size(width, height));

  }

  private JTextField getSearchField() {
    return mySearchEverywhereUI.getSearchField();
  }
}
