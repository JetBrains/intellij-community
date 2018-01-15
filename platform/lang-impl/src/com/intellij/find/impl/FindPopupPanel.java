// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.find.*;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.find.editorHeaderActions.ShowMoreOptions;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FindPopupPanel extends JBPanel implements FindUI {
  private static final Logger LOG = Logger.getInstance(FindPopupPanel.class);

  private static final KeyStroke ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
  private static final KeyStroke ENTER_WITH_MODIFIERS =
    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);

  private static final String SERVICE_KEY = "find.popup";
  private static final String SPLITTER_SERVICE_KEY = "find.popup.splitter";
  @NotNull private final FindUIHelper myHelper;
  @NotNull private final Project myProject;
  @NotNull private final Disposable myDisposable;
  private final Alarm myPreviewUpdater;
  @NotNull private final FindPopupScopeUI myScopeUI;
  private JComponent myCodePreviewComponent;
  private SearchTextArea mySearchTextArea;
  private SearchTextArea myReplaceTextArea;
  private ActionListener myOkActionListener;
  private final AtomicBoolean myCanClose = new AtomicBoolean(true);
  private final AtomicBoolean myIsPinned = new AtomicBoolean(false);
  private JBLabel myOKHintLabel;
  private Alarm mySearchRescheduleOnCancellationsAlarm;
  private volatile ProgressIndicatorBase myResultsPreviewSearchProgress;

  private JLabel myTitleLabel;
  private StateRestoringCheckBox myCbCaseSensitive;
  private StateRestoringCheckBox myCbPreserveCase;
  private StateRestoringCheckBox myCbWholeWordsOnly;
  private StateRestoringCheckBox myCbRegularExpressions;
  private StateRestoringCheckBox myCbFileFilter;
  private ActionToolbarImpl myScopeSelectionToolbar;
  private TextFieldWithAutoCompletion<String> myFileMaskField;
  private final ArrayList<String> myFileMasks = new ArrayList<>();
  private ActionButton myFilterContextButton;
  private ActionButton myTabResultsButton;
  private ActionButton myPinButton;
  private JButton myOKButton;
  private JButton myReplaceAllButton;
  private JButton myReplaceSelectedButton;
  private JTextArea mySearchComponent;
  private JTextArea myReplaceComponent;
  private String mySelectedContextName = FindBundle.message("find.context.anywhere.scope.label");
  private FindPopupScopeUI.ScopeType mySelectedScope;
  private JPanel myScopeDetailsPanel;

  private JBTable myResultsPreviewTable;
  private UsagePreviewPanel myUsagePreviewPanel;
  private JBPopup myBalloon;
  private LoadingDecorator myLoadingDecorator;
  private int myLoadingHash;
  private JPanel myTitlePanel;
  private String[] myMessageState = new String[2];
  private UsageViewPresentation myUsageViewPresentation;

  FindPopupPanel(@NotNull FindUIHelper helper) {
    myHelper = helper;
    myProject = myHelper.getProject();
    myDisposable = Disposer.newDisposable();
    myPreviewUpdater = new Alarm(myDisposable);
    myScopeUI = FindPopupScopeUIProvider.getInstance().create(this);

    Disposer.register(myDisposable, () -> {
      finishPreviousPreviewSearch();
      if (mySearchRescheduleOnCancellationsAlarm != null) Disposer.dispose(mySearchRescheduleOnCancellationsAlarm);
      if (myUsagePreviewPanel != null) Disposer.dispose(myUsagePreviewPanel);
    });

    initComponents();
    initByModel();

    ApplicationManager.getApplication().invokeLater(this::scheduleResultsUpdate, ModalityState.any());
  }

  @Override
  public void showUI() {
    if (myBalloon != null && myBalloon.isVisible()) {
      return;
    }
    if (myBalloon != null && !myBalloon.isDisposed()) {
      myBalloon.cancel();
    }
    if (myBalloon == null || myBalloon.isDisposed()) {
      final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(this, mySearchComponent);
      myBalloon = builder
        .setProject(myHelper.getProject())
        .setMovable(true)
        .setResizable(true)
        .setMayBeParent(true)
        .setCancelOnClickOutside(true)
        .setRequestFocus(true)
        .setCancelKeyEnabled(false)
        .setCancelCallback(() -> {
          boolean canBeClosed = canBeClosed();
          if (canBeClosed) {
            saveSettings();
          }
          return canBeClosed;
        })
        .createPopup();
      Disposer.register(myBalloon, myDisposable);
      registerCloseAction(myBalloon);
      final Window window = WindowManager.getInstance().suggestParentWindow(myProject);
      Component parent = UIUtil.findUltimateParent(window);
      RelativePoint showPoint = null;
      Point screenPoint = DimensionService.getInstance().getLocation(SERVICE_KEY);
      if (screenPoint != null) {
        if (parent != null) {
          SwingUtilities.convertPointFromScreen(screenPoint, parent);
          showPoint = new RelativePoint(parent, screenPoint);
        } else {
          showPoint = new RelativePoint(screenPoint);
        }
      }
      if (parent != null && showPoint == null) {
        int height = UISettings.getInstance().getShowNavigationBar() ? 135 : 115;
        if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
          height -= 20;
        }
        showPoint = new RelativePoint(parent, new Point((parent.getSize().width - getPreferredSize().width) / 2, height));
      }
      mySearchComponent.selectAll();
      WindowMoveListener windowListener = new WindowMoveListener(this);
      myTitlePanel.addMouseListener(windowListener);
      myTitlePanel.addMouseMotionListener(windowListener);
      Dimension panelSize = getPreferredSize();
      Dimension prev = DimensionService.getInstance().getSize(SERVICE_KEY);
      if (!myCbPreserveCase.isVisible()) {
        panelSize.width += myCbPreserveCase.getPreferredSize().width + 8;
      }
      panelSize.width += JBUI.scale(24);//hidden 'loading' icon
      panelSize.height *= 2;
      if (prev != null && prev.height < panelSize.height) prev.height = panelSize.height;
      myBalloon.setMinimumSize(panelSize);
      if (prev == null) {
        panelSize.height *= 1.5;
        panelSize.width *= 1.15;
      }
      myBalloon.setSize(prev != null ? prev : panelSize);

      IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
      if (showPoint != null && showPoint.getComponent() != null) {
        myBalloon.show(showPoint);
      } else {
        myBalloon.showCenteredInCurrentWindow(myProject);
      }
    }
  }

  protected boolean canBeClosed() {
    if (!myCanClose.get()) return false;
    if (myIsPinned.get()) return false;
    if (!ApplicationManager.getApplication().isActive()) return false;
    if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null) return false;
    List<JBPopup> popups = ContainerUtil.filter(JBPopupFactory.getInstance().getChildPopups(this), popup -> !popup.isDisposed());
    if (!popups.isEmpty()) {
      for (JBPopup popup : popups) {
        popup.cancel();
      }
      return false;
    }
    if (myScopeUI.hideAllPopups()) {
      return false;
    }
    return true;
  }

  protected void saveSettings() {
    DimensionService.getInstance().setSize(SERVICE_KEY, myBalloon.getSize(), myHelper.getProject() );
    DimensionService.getInstance().setLocation(SERVICE_KEY, myBalloon.getLocationOnScreen(), myHelper.getProject() );
    FindSettings findSettings = FindSettings.getInstance();
    myScopeUI.applyTo(findSettings, mySelectedScope);
    myHelper.updateFindSettings();
    applyTo(FindManager.getInstance(myProject).getFindInProjectModel(), false);
  }

  @NotNull
  @Override
  public Disposable getDisposable() {
    return myDisposable;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public FindUIHelper getHelper() {
    return myHelper;
  }

  @NotNull
  public JBPopup getBalloon() {
    return myBalloon;
  }

  @NotNull
  public AtomicBoolean getCanClose() {
    return myCanClose;
  }

  private void initComponents() {
    myTitleLabel = new JBLabel(FindBundle.message("find.in.path.dialog.title"), UIUtil.ComponentStyle.REGULAR);
    myTitleLabel.setFont(myTitleLabel.getFont().deriveFont(Font.BOLD));
    //myTitleLabel.setBorder(JBUI.Borders.emptyRight(16));
    myLoadingDecorator = new LoadingDecorator(new JLabel(EmptyIcon.ICON_16), getDisposable(), 250, true, new AsyncProcessIcon("FindInPathLoading"));
    myLoadingDecorator.setLoadingText("");
    myCbCaseSensitive = createCheckBox("find.popup.case.sensitive");
    ItemListener liveResultsPreviewUpdateListener = __ -> scheduleResultsUpdate();
    myCbCaseSensitive.addItemListener(liveResultsPreviewUpdateListener);
    myCbPreserveCase = createCheckBox("find.options.replace.preserve.case");
    myCbPreserveCase.addItemListener(liveResultsPreviewUpdateListener);
    myCbPreserveCase.setVisible(myHelper.getModel().isReplaceState());
    myCbWholeWordsOnly = createCheckBox("find.popup.whole.words");
    myCbWholeWordsOnly.addItemListener(liveResultsPreviewUpdateListener);
    myCbRegularExpressions = createCheckBox("find.popup.regex");
    myCbRegularExpressions.addItemListener(liveResultsPreviewUpdateListener);
    myCbFileFilter = createCheckBox("find.popup.filemask");
    myCbFileFilter.addItemListener(__ -> {
      if (myCbFileFilter.isSelected()) {
        myFileMaskField.setEnabled(true);
        if (myCbFileFilter.getClientProperty("dontRequestFocus") == null) {
          myFileMaskField.selectAll();
          IdeFocusManager.getInstance(myProject).requestFocus(myFileMaskField, true);
        }
      }
      else {
        myFileMaskField.setEnabled(false);
        if (myCbFileFilter.getClientProperty("dontRequestFocus") == null) {
          IdeFocusManager.getInstance(myProject).requestFocus(mySearchComponent, true);
        }
      }
    });
    myCbFileFilter.addItemListener(liveResultsPreviewUpdateListener);
    myFileMaskField =
      new TextFieldWithAutoCompletion<String>(myProject, new TextFieldWithAutoCompletion.StringsCompletionProvider(myFileMasks, null) {
        @Override
        public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                           @NotNull String prefix,
                                           @NotNull CompletionResultSet result) {
          for (String s : myVariants) {
            result.addElement(new MyLookupElement(s));
          }
          result.stopHere();
        }
      },
                                              true, null) {
        @Override
        public void setEnabled(boolean enabled) {
          super.setEnabled(enabled);
          setBackground(enabled ? JBColor.background() : UIUtil.getComboBoxDisabledBackground());
        }

        @Override
        protected EditorEx createEditor() {
          EditorEx editor = super.createEditor();
          editor.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, Boolean.TRUE);
          editor.putUserData(AutoPopupController.NO_ADS, Boolean.TRUE);
          editor.putUserData(AutoPopupController.AUTO_POPUP_ON_FOCUS_GAINED, Boolean.TRUE);
          editor.getContentComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
              if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                AutoPopupController.getInstance(getProject()).scheduleAutoPopup(editor);
              }
            }
          });
          return editor;
        }
      };
    myFileMaskField.setPreferredWidth(JBUI.scale(100));
    myFileMaskField.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        scheduleResultsUpdate();
      }
    });
    AnAction myShowFilterPopupAction = new MyShowFilterPopupAction();
    myFilterContextButton =
      new ActionButton(myShowFilterPopupAction, myShowFilterPopupAction.getTemplatePresentation(), ActionPlaces.UNKNOWN,
                       ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        @Override
        public int getPopState() {
          int state = super.getPopState();
          if (state != ActionButtonComponent.NORMAL) return state;
          return mySelectedContextName.equals(FindDialog.getPresentableName(FindModel.SearchContext.ANY))
                 ? ActionButtonComponent.NORMAL
                 : ActionButtonComponent.PUSHED;
        }
      };
    myShowFilterPopupAction.registerCustomShortcutSet(myShowFilterPopupAction.getShortcutSet(), this);
    ToggleAction pinAction = new ToggleAction(null, null, AllIcons.General.AutohideOff) {
      @Override
      public boolean isDumbAware() {
        return true;
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return UISettings.getInstance().getPinFindInPath();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myIsPinned.set(state);
        UISettings.getInstance().setPinFindInPath(state);
      }
    };
    myPinButton = new ActionButton(pinAction, pinAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);


    DefaultActionGroup tabResultsContextGroup = new DefaultActionGroup();
    tabResultsContextGroup.add(new ToggleAction(FindBundle.message("find.options.skip.results.tab.with.one.usage.checkbox")) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myHelper.isSkipResultsWithOneUsage();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myHelper.setSkipResultsWithOneUsage(state);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setVisible(!myHelper.isReplaceState());
      }
    });
    tabResultsContextGroup.add(new ToggleAction(FindBundle.message("find.open.in.new.tab.checkbox")) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myHelper.isUseSeparateView();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myHelper.setUseSeparateView(state);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(myHelper.getModel().isOpenInNewTabEnabled());
        e.getPresentation().setVisible(myHelper.getModel().isOpenInNewTabVisible());
      }
    });
    tabResultsContextGroup.setPopup(true);
    Presentation tabSettingsPresentation = new Presentation();
    tabSettingsPresentation.setIcon(AllIcons.General.SecondaryGroup);
    myTabResultsButton =
      new ActionButton(tabResultsContextGroup, tabSettingsPresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTabResultsButton.click();
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("alt DOWN"), this);
    myOKButton = new JButton(FindBundle.message("find.popup.find.button"));
    myReplaceAllButton = new JButton(FindBundle.message("find.popup.replace.all.button"));
    myReplaceSelectedButton = new JButton(FindBundle.message("find.popup.replace.selected.button", 0));
    myReplaceSelectedButton.setToolTipText("Replace " + KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK)));

    myOkActionListener = __ -> {
      doOK(true);
    };
    myReplaceAllButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myResultsPreviewTable.getRowCount() < 2 || ReplaceInProjectManager.getInstance(myProject).showReplaceAllConfirmDialog(
          myMessageState[0],
          getStringToFind(),
          myMessageState[1],
          getStringToReplace())) {
          doOK(false);
        }
      }
    });
    myReplaceSelectedButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int rowToSelect = myResultsPreviewTable.getSelectionModel().getMinSelectionIndex();
        Map<Integer, Usage> usages = getSelectedUsages();
        if (usages == null) {
          return;
        }
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          for (Map.Entry<Integer, Usage> entry : usages.entrySet()) {
            try {
              ReplaceInProjectManager.getInstance(myProject).replaceUsage(entry.getValue(), myHelper.getModel(), Collections.emptySet(), false);
              ((DefaultTableModel)myResultsPreviewTable.getModel()).removeRow(entry.getKey());
            }
            catch (FindManager.MalformedReplacementStringException ex) {
              if (!ApplicationManager.getApplication().isUnitTestMode()) {
                Messages.showErrorDialog(FindPopupPanel.this, ex.getMessage(), FindBundle.message("find.replace.invalid.replacement.string.title"));
              }
              break;
            }
          }


          ApplicationManager.getApplication().invokeLater(() -> {
            if (myResultsPreviewTable.getRowCount() > rowToSelect) {
              myResultsPreviewTable.getSelectionModel().setSelectionInterval(rowToSelect, rowToSelect);
            }
            ScrollingUtil.ensureSelectionExists(myResultsPreviewTable);
          });
        }, FindBundle.message("find.replace.command"), null);
      }
    });
    myOKButton.addActionListener(myOkActionListener);
    boolean enterAsOK = Registry.is("ide.find.enter.as.ok", false);

    new DumbAwareAction() {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(e.getData(CommonDataKeys.EDITOR) == null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        if (enterAsOK) {
          myOkActionListener.actionPerformed(null);
        }
        else {
          navigateToSelectedUsage();
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(ENTER), this);
    DumbAwareAction.create(e -> {
      if (enterAsOK) {
        navigateToSelectedUsage();
      }
      else {
        myOkActionListener.actionPerformed(null);
      }
    }).registerCustomShortcutSet(new CustomShortcutSet(ENTER_WITH_MODIFIERS), this);
    
    List<Shortcut> navigationKeyStrokes = ContainerUtil.newArrayList();
    KeyStroke viewSourceKeyStroke = KeymapUtil.getKeyStroke(CommonShortcuts.getViewSource());
    if (viewSourceKeyStroke != null && !Comparing.equal(viewSourceKeyStroke, ENTER_WITH_MODIFIERS) && !Comparing.equal(viewSourceKeyStroke, ENTER)) {
      navigationKeyStrokes.add(new KeyboardShortcut(viewSourceKeyStroke, null));
    }
    KeyStroke editSourceKeyStroke = KeymapUtil.getKeyStroke(CommonShortcuts.getEditSource());
    if (editSourceKeyStroke != null && !Comparing.equal(editSourceKeyStroke, ENTER_WITH_MODIFIERS) && !Comparing.equal(editSourceKeyStroke, ENTER)) {
      navigationKeyStrokes.add(new KeyboardShortcut(editSourceKeyStroke, null));
    }
    if (!navigationKeyStrokes.isEmpty()) {
      new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          navigateToSelectedUsage();
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(navigationKeyStrokes.toArray(Shortcut.EMPTY_ARRAY)), this);
    }

    mySearchComponent = new JTextArea();
    mySearchComponent.setColumns(25);
    mySearchComponent.setRows(1);
    myReplaceComponent = new JTextArea();
    myReplaceComponent.setColumns(25);
    myReplaceComponent.setRows(1);
    mySearchTextArea = new SearchTextArea(mySearchComponent, true, true);
    myReplaceTextArea = new SearchTextArea(myReplaceComponent, false, false);
    mySearchTextArea.setMultilineEnabled(false);
    myReplaceTextArea.setMultilineEnabled(false);

    Pair<FindPopupScopeUI.ScopeType, JComponent>[] scopeComponents = myScopeUI.getComponents();

    myScopeDetailsPanel = new JPanel(new CardLayout());
    myScopeDetailsPanel.setBorder(JBUI.Borders.emptyBottom(UIUtil.isUnderDefaultMacTheme() ? 0 : 3));

    List<AnAction> scopeActions = new ArrayList<>(scopeComponents.length);
    for (Pair<FindPopupScopeUI.ScopeType, JComponent> scopeComponent : scopeComponents) {
      FindPopupScopeUI.ScopeType scopeType = scopeComponent.first;
      scopeActions.add(new MySelectScopeToggleAction(scopeType));
      myScopeDetailsPanel.add(scopeType.name, scopeComponent.second);
    }
    myScopeSelectionToolbar = createToolbar(scopeActions.toArray(AnAction.EMPTY_ARRAY));
    myScopeSelectionToolbar.setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    mySelectedScope = scopeComponents[0].first;

    myResultsPreviewTable = new JBTable() {
      @Override
      public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(getWidth(), 1 + getRowHeight() * 4);
      }
    };
    myResultsPreviewTable.setFocusable(false);
    myResultsPreviewTable.getEmptyText().setShowAboveCenter(false);
    myResultsPreviewTable.setShowColumns(false);
    myResultsPreviewTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myResultsPreviewTable.setShowGrid(false);
    myResultsPreviewTable.setIntercellSpacing(JBUI.emptySize());
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        if (event.getSource() != myResultsPreviewTable) return false;
        navigateToSelectedUsage();
        return true;
      }
    }.installOn(myResultsPreviewTable);
    myResultsPreviewTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myResultsPreviewTable.transferFocus();
      }
    });
    applyFont(JBUI.Fonts.label(), myCbCaseSensitive, myCbPreserveCase, myCbWholeWordsOnly, myCbRegularExpressions,
              myResultsPreviewTable);
    ScrollingUtil.installActions(myResultsPreviewTable, false, mySearchComponent);
    ScrollingUtil.installActions(myResultsPreviewTable, false, myReplaceComponent);
    ScrollingUtil.installActions(myResultsPreviewTable, false, myReplaceSelectedButton);

    ActionListener helpAction = __ -> HelpManager.getInstance().invokeHelp("reference.dialogs.findinpath");
    registerKeyboardAction(helpAction,KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),JComponent.WHEN_IN_FOCUSED_WINDOW);
    registerKeyboardAction(helpAction,KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),JComponent.WHEN_IN_FOCUSED_WINDOW);

    myUsageViewPresentation = new UsageViewPresentation();
    myUsagePreviewPanel = new UsagePreviewPanel(myProject, myUsageViewPresentation, Registry.is("ide.find.as.popup.editable.code")) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(myResultsPreviewTable.getWidth(), Math.max(getHeight(), getLineHeight() * 15));
      }
    };
    Disposer.register(myDisposable, myUsagePreviewPanel);
    final Runnable updatePreviewRunnable = () -> {
      if (Disposer.isDisposed(myDisposable)) return;
      int[] selectedRows = myResultsPreviewTable.getSelectedRows();
      final List<UsageInfo> selection = new SmartList<>();
      VirtualFile file = null;
      for (int row : selectedRows) {
        UsageInfo2UsageAdapter adapter = (UsageInfo2UsageAdapter)myResultsPreviewTable.getModel().getValueAt(row, 0);
        file = adapter.getFile();
        if (adapter.isValid()) {
          selection.addAll(Arrays.asList(adapter.getMergedInfos()));
        }
      }
      String title = getTitle(file);
      myReplaceSelectedButton.setText(FindBundle.message("find.popup.replace.selected.button", selection.size()));
      FindInProjectUtil.setupViewPresentation(myUsageViewPresentation, FindSettings.getInstance().isShowResultsInSeparateView(), myHelper.getModel().clone());
      myUsagePreviewPanel.updateLayout(selection);
      if (myUsagePreviewPanel.getCannotPreviewMessage(selection) == null && title != null) {
        myUsagePreviewPanel.setBorder(IdeBorderFactory.createTitledBorder(title, false, new JBInsets(8, 0, 0, 0)).setShowLine(false));
      }
      else {
        myUsagePreviewPanel.setBorder(JBUI.Borders.empty());
      }
    };
    myResultsPreviewTable.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      myPreviewUpdater.addRequest(updatePreviewRunnable, 50); //todo[vasya]: remove this dirty hack of updating preview panel after clicking on Replace button
    });
    DocumentAdapter documentAdapter = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        mySearchComponent.setRows(Math.max(1, Math.min(3, StringUtil.countChars(mySearchComponent.getText(), '\n') + 1)));
        myReplaceComponent.setRows(Math.max(1, Math.min(3, StringUtil.countChars(myReplaceComponent.getText(), '\n') + 1)));

        if (myBalloon == null) return;
        if (e.getDocument() == mySearchComponent.getDocument()) {
          scheduleResultsUpdate();
        }
        if (e.getDocument() == myReplaceComponent.getDocument()) {
          applyTo(myHelper.getModel(), false);
          ApplicationManager.getApplication().invokeLater(updatePreviewRunnable);
        }
      }
    };
    mySearchComponent.getDocument().addDocumentListener(documentAdapter);
    myReplaceComponent.getDocument().addDocumentListener(documentAdapter);

    mySearchRescheduleOnCancellationsAlarm = new Alarm();

    JBSplitter splitter = new JBSplitter(true, .33f);
    splitter.setSplitterProportionKey(SPLITTER_SERVICE_KEY);
    splitter.setDividerWidth(JBUI.scale(2));
    splitter.getDivider().setBackground(OnePixelDivider.BACKGROUND);
    JBScrollPane scrollPane = new JBScrollPane(myResultsPreviewTable) {
      @Override
      public Dimension getMinimumSize() {
        Dimension size = super.getMinimumSize();
        size.height = myResultsPreviewTable.getPreferredScrollableViewportSize().height;
        return size;
      }
    };
    scrollPane.setBorder(JBUI.Borders.empty());
    splitter.setFirstComponent(scrollPane);
    JPanel bottomPanel = new JPanel(new MigLayout("flowx, ins 4 4 0 4, fillx, hidemode 2, gap 0"));
    bottomPanel.add(myTabResultsButton);
    bottomPanel.add(Box.createHorizontalGlue(), "growx, pushx");
    myOKHintLabel = new JBLabel("");
    myOKHintLabel.setEnabled(false);
    bottomPanel.add(myOKHintLabel, "gapright 10");
    bottomPanel.add(myOKButton);
    bottomPanel.add(myReplaceAllButton);
    bottomPanel.add(myReplaceSelectedButton);

    myCodePreviewComponent = myUsagePreviewPanel.createComponent();
    splitter.setSecondComponent(myCodePreviewComponent);
    JPanel scopesPanel = new JPanel(new MigLayout("flowx, gap 26, ins 0"));
    scopesPanel.add(myScopeSelectionToolbar.getComponent());
    scopesPanel.add(myScopeDetailsPanel, "growx, pushx");
    setLayout(new MigLayout("flowx, ins 4, gap 0, fillx, hidemode 3"));
    int cbGapLeft = myCbCaseSensitive.getInsets().left;
    int cbGapRight = myCbCaseSensitive.getInsets().right;
    myTitlePanel = new JPanel(new MigLayout("flowx, ins 0, gap 0, fillx, filly"));
    myTitlePanel.add(myTitleLabel);
    myTitlePanel.add(myLoadingDecorator.getComponent(), "w 24, wmin 24");
    myTitlePanel.add(Box.createHorizontalGlue(), "growx, pushx");
    add(myTitlePanel, "sx 2, growx, pushx, growy");
    String cbGap = cbGapLeft + cbGapRight < 16 ? "gapright " + (16 - cbGapLeft - cbGapRight) : "";
    add(myCbCaseSensitive, cbGap);
    add(myCbPreserveCase, cbGap);
    add(myCbWholeWordsOnly, cbGap);
    add(myCbRegularExpressions, "gapright 0");
    add(RegExHelpPopup.createRegExLink("<html><body><b>?</b></body></html>", myCbRegularExpressions, LOG), "gapright " + (16-cbGapLeft));
    add(myCbFileFilter);
    add(myFileMaskField, "gapright 16, w 80, wmax 80, wmin 80");
    if (Registry.is("ide.find.as.popup.allow.pin") || ApplicationManager.getApplication().isInternal()) {
      myIsPinned.set(UISettings.getInstance().getPinFindInPath());
      JPanel twoButtons = new JPanel(new GridLayout(1, 2, 4, 0));
      twoButtons.add(myFilterContextButton);
      twoButtons.add(myPinButton);
      add(twoButtons, "wrap");
    } else {
      add(myFilterContextButton, "wrap");
    }
    add(mySearchTextArea, "pushx, growx, sx 10, gaptop 4, wrap");
    add(myReplaceTextArea, "pushx, growx, sx 10, gaptop 4, wrap");
    add(scopesPanel, "sx 10, pushx, growx, ax left, wrap, gaptop 4, gapbottom 4");
    add(splitter, "pushx, growx, growy, pushy, sx 10, wrap, pad -4 -4 4 4");
    add(bottomPanel, "pushx, growx, dock south, sx 10");

    MnemonicHelper.init(this);
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
      @Override
      public Component getComponentAfter(Container container, Component c) {
        return c == myResultsPreviewTable ? mySearchComponent : super.getComponentAfter(container, c);
      }
    });
  }

  //Some popups shown above may prevent panel closing, first of all we should close them
  private boolean canBeClosedImmediately() {
    boolean state = myIsPinned.get();
    myIsPinned.set(false);
    try {
      //Here we actually close popups
      return myBalloon != null && canBeClosed();
    } finally {
      myIsPinned.set(state);
    }
  }

  private void doOK(boolean promptOnReplace) {
    if (!canBeClosedImmediately()) {
      return;
    }

    FindModel validateModel = myHelper.getModel().clone();
    applyTo(validateModel, false);

    ValidationInfo validationInfo = getValidationInfo(validateModel);

    if (validationInfo == null) {
      myHelper.getModel().copyFrom(validateModel);
      myHelper.getModel().setPromptOnReplace(promptOnReplace);
      myHelper.doOKAction();
    }
    else {
      String message = validationInfo.message;
      Messages.showMessageDialog(
        this,
        message,
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
      return;
    }
    myIsPinned.set(false);
    myBalloon.cancel();
  }

  @Nullable
  private String getTitle(@Nullable VirtualFile file) {
    if (file == null) return null;
    String path = VfsUtilCore.getRelativePath(file, myProject.getBaseDir());
    if (path == null) path = file.getPath();
    return "<html><body>&nbsp;&nbsp;&nbsp;" + path.replace(file.getName(), "<b>" + file.getName() + "</b>") + "</body></html>";
  }

  @NotNull
  private static StateRestoringCheckBox createCheckBox(String message) {
    StateRestoringCheckBox checkBox = new StateRestoringCheckBox(FindBundle.message(message));
    checkBox.setFocusable(false);
    return checkBox;
  }

  private void registerCloseAction(JBPopup popup) {
    final AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(e -> {
      if (canBeClosedImmediately() && myBalloon != null && myBalloon.isVisible()) {
        myIsPinned.set(false);
        myBalloon.cancel();
      }
    }).registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), popup.getContent(), popup);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    ApplicationManager.getApplication().invokeLater(() -> ScrollingUtil.ensureSelectionExists(myResultsPreviewTable), ModalityState.any());
    myScopeSelectionToolbar.updateActionsImmediately();
  }

  @Override
  public void initByModel() {
    FindModel myModel = myHelper.getModel();
    myCbCaseSensitive.setSelected(myModel.isCaseSensitive());
    myCbWholeWordsOnly.setSelected(myModel.isWholeWordsOnly());
    myCbRegularExpressions.setSelected(myModel.isRegularExpressions());

    mySelectedContextName = FindDialog.getSearchContextName(myModel);
    if (myModel.isReplaceState()) {
      myCbPreserveCase.setSelected(myModel.isPreserveCase());
    }

    mySelectedScope = myScopeUI.initByModel(myModel);

    boolean isThereFileFilter = myModel.getFileFilter() != null && !myModel.getFileFilter().isEmpty();
    try {
      myCbFileFilter.putClientProperty("dontRequestFocus", Boolean.TRUE);
      myCbFileFilter.setSelected(isThereFileFilter);
    } finally {
      myCbFileFilter.putClientProperty("dontRequestFocus", null);
    }
    List<String> variants = Arrays.asList(ArrayUtil.reverseArray(FindSettings.getInstance().getRecentFileMasks()));
    myFileMaskField.setVariants(variants);
    if (!variants.isEmpty()) {
      myFileMaskField.setText(variants.get(0));
    }
    myFileMaskField.setEnabled(isThereFileFilter);
    String toSearch = myModel.getStringToFind();
    FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(myProject);

    if (StringUtil.isEmpty(toSearch)) {
      String[] history = findInProjectSettings.getRecentFindStrings();
      toSearch = history.length > 0 ? history[history.length - 1] : "";
    }

    mySearchComponent.setText(toSearch);
    String toReplace = myModel.getStringToReplace();

    if (StringUtil.isEmpty(toReplace)) {
      String[] history = findInProjectSettings.getRecentReplaceStrings();
      toReplace = history.length > 0 ? history[history.length - 1] : "";
    }
    myReplaceComponent.setText(toReplace);
    updateControls();
    updateScopeDetailsPanel();

    boolean isReplaceState = myHelper.isReplaceState();
    myTitleLabel.setText(myHelper.getTitle());
    myReplaceTextArea.setVisible(isReplaceState);
    myCbPreserveCase.setVisible(isReplaceState);
    if (Registry.is("ide.find.enter.as.ok", false)) {
      myOKHintLabel.setText(KeymapUtil.getKeystrokeText(ENTER));
    } else {
      myOKHintLabel.setText(KeymapUtil.getKeystrokeText(ENTER_WITH_MODIFIERS));
    }
    myOKButton.setText(FindBundle.message("find.popup.find.button"));
  }

  private void updateControls() {
    FindModel myModel = myHelper.getModel();
    if (myCbRegularExpressions.isSelected()) {
      myCbWholeWordsOnly.makeUnselectable(false);
    }
    else {
      myCbWholeWordsOnly.makeSelectable();
    }
    if (myModel.isReplaceState()) {
      if (myCbRegularExpressions.isSelected() || myCbCaseSensitive.isSelected()) {
        myCbPreserveCase.makeUnselectable(false);
      }
      else {
        myCbPreserveCase.makeSelectable();
      }

      if (myCbPreserveCase.isSelected()) {
        myCbRegularExpressions.makeUnselectable(false);
        myCbCaseSensitive.makeUnselectable(false);
      }
      else {
        myCbRegularExpressions.makeSelectable();
        myCbCaseSensitive.makeSelectable();
      }
    }
    myReplaceAllButton.setVisible(myHelper.isReplaceState());
    myReplaceSelectedButton.setVisible(myHelper.isReplaceState());
  }

  private void updateScopeDetailsPanel() {
    ((CardLayout)myScopeDetailsPanel.getLayout()).show(myScopeDetailsPanel, mySelectedScope.name);
    Component firstFocusableComponent =
      UIUtil.uiTraverser(myScopeDetailsPanel).bfsTraversal().find(c -> c.isFocusable() && c.isEnabled() && c.isShowing() &&
                                                                       (c instanceof JComboBox ||
                                                                        c instanceof AbstractButton ||
                                                                        c instanceof JTextComponent));
    myScopeDetailsPanel.revalidate();
    myScopeDetailsPanel.repaint();
    if (firstFocusableComponent != null) {
      ApplicationManager.getApplication().invokeLater(
        () -> IdeFocusManager.getInstance(myProject).requestFocus(firstFocusableComponent, true));
    }
    if (firstFocusableComponent == null && !mySearchComponent.isFocusOwner() && !myReplaceComponent.isFocusOwner()) {
      ApplicationManager.getApplication().invokeLater(
        () -> IdeFocusManager.getInstance(myProject).requestFocus(mySearchComponent, true));
    }
  }

  void scheduleResultsUpdate() {
    if (myBalloon == null || !myBalloon.isVisible()) return;
    if (mySearchRescheduleOnCancellationsAlarm == null || mySearchRescheduleOnCancellationsAlarm.isDisposed()) return;
    updateControls();
    mySearchRescheduleOnCancellationsAlarm.cancelAllRequests();
    mySearchRescheduleOnCancellationsAlarm.addRequest(this::findSettingsChanged, 100);
  }

  private void finishPreviousPreviewSearch() {
    if (myResultsPreviewSearchProgress != null && !myResultsPreviewSearchProgress.isCanceled()) {
      myResultsPreviewSearchProgress.cancel();
    }
  }

  private void findSettingsChanged() {
    if (isShowing()) {
      ScrollingUtil.ensureSelectionExists(myResultsPreviewTable);
    }
    final ModalityState state = ModalityState.current();
    finishPreviousPreviewSearch();
    mySearchRescheduleOnCancellationsAlarm.cancelAllRequests();
    applyTo(myHelper.getModel(), false);
    FindModel findModel = new FindModel();
    findModel.copyFrom(myHelper.getModel());

    ValidationInfo result = getValidationInfo(myHelper.getModel());

    final ProgressIndicatorBase progressIndicatorWhenSearchStarted = new ProgressIndicatorBase() {
      @Override
      public void stop() {
        super.stop();
        onStop(System.identityHashCode(this));
      }
    };
    myResultsPreviewSearchProgress = progressIndicatorWhenSearchStarted;
    final int hash = System.identityHashCode(myResultsPreviewSearchProgress);

    final DefaultTableModel model = new DefaultTableModel() {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    };

    model.addColumn("Usages");
    // Use previously shown usage files as hint for faster search and better usage preview performance if pattern length increased
    final LinkedHashSet<VirtualFile> filesToScanInitially = new LinkedHashSet<>();

    if (myHelper.myPreviousModel != null && myHelper.myPreviousModel.getStringToFind().length() < myHelper.getModel().getStringToFind().length()) {
      final DefaultTableModel previousModel = (DefaultTableModel)myResultsPreviewTable.getModel();
      for (int i = 0, len = previousModel.getRowCount(); i < len; ++i) {
        final UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)previousModel.getValueAt(i, 0);
        final VirtualFile file = usage.getFile();
        if (file != null) filesToScanInitially.add(file);
      }
    }

    myHelper.myPreviousModel = myHelper.getModel().clone();

    myReplaceAllButton.setEnabled(false);
    myReplaceSelectedButton.setEnabled(false);
    myReplaceSelectedButton.setText(FindBundle.message("find.popup.replace.selected.button", 0));
    myReplaceSelectedButton.setMnemonic('r');
    myCodePreviewComponent.setVisible(false);

    mySearchTextArea.setInfoText(null);
    myResultsPreviewTable.setModel(model);

    if (result != null) {
      onStop(hash, result.message);
      return;
    }

    GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(
      FindInProjectUtil.getScopeFromModel(myProject, myHelper.myPreviousModel), myProject);
    myResultsPreviewTable.getColumnModel().getColumn(0).setCellRenderer(
      new FindDialog.UsageTableCellRenderer(myCbFileFilter.isSelected(), false, scope));
    onStart(hash);

    final AtomicInteger resultsCount = new AtomicInteger();
    final AtomicInteger resultsFilesCount = new AtomicInteger();
    FindSettings findSettings = FindSettings.getInstance();
    FindInProjectUtil.setupViewPresentation(myUsageViewPresentation, findSettings.isShowResultsInSeparateView(), findModel);

    ProgressIndicatorUtils.scheduleWithWriteActionPriority(myResultsPreviewSearchProgress, new ReadTask() {
      @Override
      public Continuation performInReadAction(@NotNull ProgressIndicator indicator) {
        final boolean showPanelIfOnlyOneUsage = !findSettings.isSkipResultsWithOneUsage();

        final FindUsagesProcessPresentation processPresentation =
          FindInProjectUtil.setupProcessPresentation(myProject, showPanelIfOnlyOneUsage, myUsageViewPresentation);
        ThreadLocal<VirtualFile> lastUsageFileRef = new ThreadLocal<>();
        ThreadLocal<Usage> recentUsageRef = new ThreadLocal<>();

        FindInProjectUtil.findUsages(findModel, myProject, info -> {
          if(isCancelled()) {
            onStop(hash);
            return false;
          }
          final Usage usage = UsageInfo2UsageAdapter.CONVERTER.fun(info);
          usage.getPresentation().getIcon(); // cache icon

          VirtualFile file = lastUsageFileRef.get();
          VirtualFile usageFile = info.getVirtualFile();
          if (file == null || !file.equals(usageFile)) {
            resultsFilesCount.incrementAndGet();
            lastUsageFileRef.set(usageFile);
          }
          Usage recent = recentUsageRef.get();
          UsageInfo2UsageAdapter recentAdapter =
            recent instanceof UsageInfo2UsageAdapter ? (UsageInfo2UsageAdapter)recent : null;
          UsageInfo2UsageAdapter currentAdapter = usage instanceof UsageInfo2UsageAdapter ? (UsageInfo2UsageAdapter)usage : null;
          final boolean merged = !myHelper.isReplaceState() && currentAdapter != null && recentAdapter != null && recentAdapter.merge(currentAdapter);
          if (!merged) {
            recentUsageRef.set(usage);
          }


          ApplicationManager.getApplication().invokeLater(() -> {
            if (isCancelled()) {
              onStop(hash);
              return;
            }
            if (!merged) {
              model.addRow(new Object[]{usage});
            } else {
              model.fireTableRowsUpdated(model.getRowCount() - 1, model.getRowCount() - 1);
            }
            myCodePreviewComponent.setVisible(true);
            if (model.getRowCount() == 1 && myResultsPreviewTable.getModel() == model) {
              myResultsPreviewTable.setRowSelectionInterval(0, 0);
            }
            int occurrences = resultsCount.get();
            int filesWithOccurrences = resultsFilesCount.get();
            myCodePreviewComponent.setVisible(occurrences > 0);
            myReplaceAllButton.setEnabled(occurrences > 0);
            myReplaceSelectedButton.setEnabled(occurrences > 0);

            StringBuilder stringBuilder = new StringBuilder();
            if (occurrences > 0) {
              stringBuilder.append(Math.min(ShowUsagesAction.getUsagesPageSize(), occurrences));
              boolean foundAllUsages = occurrences < ShowUsagesAction.getUsagesPageSize();
              myMessageState[0] = String.valueOf(occurrences);
              if (!foundAllUsages) {
                stringBuilder.append("+");
                myMessageState[0]  += "+";
              }
              stringBuilder.append(UIBundle.message("message.matches", occurrences));
              stringBuilder.append(" in ");
              stringBuilder.append(filesWithOccurrences);
              myMessageState[1] = String.valueOf(filesWithOccurrences);
              if (!foundAllUsages) {
                stringBuilder.append("+");
                myMessageState[1]  += "+";
              }
              stringBuilder.append(UIBundle.message("message.files", filesWithOccurrences));
            }
            mySearchTextArea.setInfoText(stringBuilder.toString());
          }, state);

          boolean continueSearch = resultsCount.incrementAndGet() < ShowUsagesAction.getUsagesPageSize();
          if (!continueSearch) {
            onStop(hash);
          }
          return continueSearch;
        }, processPresentation, filesToScanInitially);

        return new Continuation(() -> {
          if (!isCancelled()) {
            if (resultsCount.get() == 0) {
              showEmptyText(null);
            }
          }
          onStop(hash);
        }, state);
      }

      boolean isCancelled() {
        return progressIndicatorWhenSearchStarted != myResultsPreviewSearchProgress || progressIndicatorWhenSearchStarted.isCanceled();
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
        if (isShowing() && progressIndicatorWhenSearchStarted == myResultsPreviewSearchProgress) {
          scheduleResultsUpdate();
        }
      }
    });
  }

  protected void showEmptyText(@Nullable String message) {
    StatusText emptyText = myResultsPreviewTable.getEmptyText();
    emptyText.clear();
    emptyText.setText(message != null ? UIBundle.message("message.nothingToShow.with.problem", message)
                                                                 : UIBundle.message("message.nothingToShow"));
    if (mySelectedScope == FindPopupScopeUIImpl.DIRECTORY && !myHelper.getModel().isWithSubdirectories()) {
      emptyText.appendSecondaryText(FindBundle.message("find.recursively.hint"),
                                                               SimpleTextAttributes.LINK_ATTRIBUTES,
                                                               new ActionListener() {
                                                                 @Override
                                                                 public void actionPerformed(ActionEvent e) {
                                                                   myHelper.getModel().setWithSubdirectories(true);
                                                                   scheduleResultsUpdate();
                                                                 }
                                                               });
    }
  }

  private void onStart(int hash) {
    myLoadingHash = hash;
    myLoadingDecorator.startLoading(false);
    myResultsPreviewTable.getEmptyText().setText("Searching...");
  }


  private void onStop(int hash) {
    onStop(hash, null);
  }

  private void onStop(int hash, String message) {
    if (hash != myLoadingHash) {
      return;
    }
    UIUtil.invokeLaterIfNeeded(() -> {
      showEmptyText(message);
      myLoadingDecorator.stopLoading();
    });
  }

  @Override
  @Nullable
  public String getFileTypeMask() {
    String mask = null;
    if (myCbFileFilter != null && myCbFileFilter.isSelected()) {
      mask = myFileMaskField.getText();
    }
    return mask;
  }

  @Nullable("null means OK")
  private ValidationInfo getValidationInfo(@NotNull FindModel model) {
    ValidationInfo scopeValidationInfo = myScopeUI.validate(model, mySelectedScope);
    if (scopeValidationInfo != null) {
      return scopeValidationInfo;
    }

    if (!myHelper.canSearchThisString()) {
      return new ValidationInfo(FindBundle.message("find.empty.search.text.error"), mySearchComponent);
    }

    if (myCbRegularExpressions != null && myCbRegularExpressions.isSelected() && myCbRegularExpressions.isEnabled()) {
      String toFind = getStringToFind();
      try {
        boolean isCaseSensitive = myCbCaseSensitive != null && myCbCaseSensitive.isSelected() && myCbCaseSensitive.isEnabled();
        Pattern pattern =
          Pattern.compile(toFind, isCaseSensitive ? Pattern.MULTILINE : Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        if (pattern.matcher("").matches() && !toFind.endsWith("$") && !toFind.startsWith("^")) {
          return new ValidationInfo(FindBundle.message("find.empty.match.regular.expression.error"), mySearchComponent);
        }
      }
      catch (PatternSyntaxException e) {
        return new ValidationInfo(FindBundle.message("find.invalid.regular.expression.error", toFind, e.getDescription()),
                                  mySearchComponent);
      }
    }

    final String mask = getFileTypeMask();

    if (mask != null) {
      if (mask.isEmpty()) {
        return new ValidationInfo(FindBundle.message("find.filter.empty.file.mask.error"), myFileMaskField);
      }

      if (mask.contains(";")) {
        return new ValidationInfo("File masks should be comma-separated", myFileMaskField);
      }

      else {
        try {
          FindInProjectUtil.createFileMaskRegExp(mask);   // verify that the regexp compiles
        }
        catch (PatternSyntaxException ex) {
          return new ValidationInfo(FindBundle.message("find.filter.invalid.file.mask.error", mask), myFileMaskField);
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getStringToFind() {
    return mySearchComponent.getText();
  }

  @NotNull
  private String getStringToReplace() {
    return myReplaceComponent.getText();
  }

  private void applyTo(@NotNull FindModel model, boolean findAll) {
    model.setCaseSensitive(myCbCaseSensitive.isSelected());

    if (model.isReplaceState()) {
      model.setPreserveCase(myCbPreserveCase.isSelected());
    }

    model.setWholeWordsOnly(myCbWholeWordsOnly.isSelected());

    String selectedSearchContextInUi = mySelectedContextName;
    FindModel.SearchContext searchContext = FindDialog.parseSearchContext(selectedSearchContextInUi);

    model.setSearchContext(searchContext);

    model.setRegularExpressions(myCbRegularExpressions.isSelected());
    model.setStringToFind(getStringToFind());

    if (model.isReplaceState()) {
      model.setStringToReplace(StringUtil.convertLineSeparators(getStringToReplace()));
    }


    model.setProjectScope(false);
    model.setDirectoryName(null);
    model.setModuleName(null);
    model.setCustomScopeName(null);
    model.setCustomScope(null);
    model.setCustomScope(false);
    myScopeUI.applyTo(model, mySelectedScope);

    model.setFindAll(findAll);

    String mask = getFileTypeMask();
    model.setFileFilter(mask);
  }

  private void navigateToSelectedUsage() {
    Map<Integer, Usage> usages = getSelectedUsages();
    if (usages != null) {
      myBalloon.cancel();
      boolean first = true;
      for (Usage usage : usages.values()) {
        if (first) {
          usage.navigate(true);
        }
        else {
          usage.highlightInEditor();
        }
        first = false;
      }
    }
  }

  @Nullable
  private Map<Integer, Usage> getSelectedUsages() {
    int[] rows = myResultsPreviewTable.getSelectedRows();
    Map<Integer, Usage> result = null;
    for (int i = rows.length - 1; i >= 0; i--) {
      int row = rows[i];
      Object valueAt = myResultsPreviewTable.getModel().getValueAt(row, 0);
      if (valueAt instanceof Usage) {
        if (result == null) result = ContainerUtil.newLinkedHashMap();
        result.put(row, (Usage)valueAt);
      }
    }
    return result;
  }

  public static ActionToolbarImpl createToolbar(AnAction... actions) {
    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, new DefaultActionGroup(actions), true);
    toolbar.setForceMinimumSize(true);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    return toolbar;
  }

  private static void applyFont(JBFont font, Component... components) {
    for (Component component : components) {
      component.setFont(font);
    }
  }

  private class MySwitchContextToggleAction extends ToggleAction implements DumbAware {
    MySwitchContextToggleAction(FindModel.SearchContext context) {
      super(FindDialog.getPresentableName(context));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return Comparing.equal(mySelectedContextName, getTemplatePresentation().getText());
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        mySelectedContextName = getTemplatePresentation().getText();
        scheduleResultsUpdate();
      }
    }
  }

  private class MySelectScopeToggleAction extends ToggleAction {
    private final FindPopupScopeUI.ScopeType myScope;

    MySelectScopeToggleAction(FindPopupScopeUI.ScopeType scope) {
      super(scope.text, null, scope.icon);
      getTemplatePresentation().setHoveredIcon(scope.icon);
      getTemplatePresentation().setDisabledIcon(scope.icon);
      myScope = scope;
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySelectedScope == myScope;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        mySelectedScope = myScope;
        myScopeSelectionToolbar.updateActionsImmediately();
        updateScopeDetailsPanel();
        scheduleResultsUpdate();
      }
    }
  }

  private class MyShowFilterPopupAction extends DumbAwareAction {
    private final DefaultActionGroup mySwitchContextGroup;

    MyShowFilterPopupAction() {
      super(FindBundle.message("find.popup.show.filter.popup"), null, AllIcons.General.Filter);
      LayeredIcon icon = JBUI.scale(new LayeredIcon(2));
      icon.setIcon(AllIcons.General.Filter, 0);
      icon.setIcon(AllIcons.General.Dropdown, 1, 3, 0);
      getTemplatePresentation().setIcon(icon);

      setShortcutSet(new CustomShortcutSet(ShowMoreOptions.SHORT_CUT));
      mySwitchContextGroup = new DefaultActionGroup();
      mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.ANY));
      mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.IN_COMMENTS));
      mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.IN_STRING_LITERALS));
      mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.EXCEPT_COMMENTS));
      mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.EXCEPT_STRING_LITERALS));
      mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS));
      mySwitchContextGroup.setPopup(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext()) == null) return;

      ListPopup listPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(null, mySwitchContextGroup, e.getDataContext(), false, null, 10);
      listPopup.showUnderneathOf(myFilterContextButton);
    }
  }

  private static class MyLookupElement extends LookupElement {
    private final String myValue;

    public MyLookupElement(String value) {
      myValue = value;
    }

    @NotNull
    @Override
    public String getLookupString() {
      return myValue;
    }

    @Nullable
    @Override
    public PsiElement getPsiElement() {
      return null;
    }

    @Override
    public boolean isValid() {
      return true;
    }
  }

}
