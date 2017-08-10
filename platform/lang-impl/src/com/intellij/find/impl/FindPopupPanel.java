/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.find.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.find.*;
import com.intellij.find.actions.ShowUsagesAction;
import com.intellij.find.editorHeaderActions.ShowMoreOptions;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
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
  @NotNull private final FindPopupScopeUI myScopeUI;
  private JComponent myCodePreviewComponent;
  private SearchTextArea mySearchTextArea;
  private SearchTextArea myReplaceTextArea;
  private ActionListener myOkActionListener;
  private AtomicBoolean myCanClose = new AtomicBoolean(true);
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
  private ArrayList<String> myFileMasks = new ArrayList<>();
  private ActionButton myFilterContextButton;
  private ActionButton myTabResultsButton;
  private JButton myOKButton;
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

  FindPopupPanel(@NotNull FindUIHelper helper) {
    myHelper = helper;
    myProject = myHelper.getProject();
    myDisposable = Disposer.newDisposable();
    myScopeUI = FindPopupScopeUIProvider.getInstance().create(this);

    Disposer.register(myDisposable, new Disposable() {
      @Override
      public void dispose() {
        FindPopupPanel.this.finishPreviousPreviewSearch();
        if (mySearchRescheduleOnCancellationsAlarm != null) Disposer.dispose(mySearchRescheduleOnCancellationsAlarm);
        if (myUsagePreviewPanel != null) Disposer.dispose(myUsagePreviewPanel);
      }
    });

    initComponents();
    initByModel();

    ApplicationManager.getApplication().invokeLater(() -> this.scheduleResultsUpdate(), ModalityState.any());
  }

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
        .setCancelCallback(() -> {
          if (!myCanClose.get()) return false;
          if (!ApplicationManager.getApplication().isActive()) return false;
          if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null) return false;
          List<JBPopup> popups = JBPopupFactory.getInstance().getChildPopups(this);
          if (!popups.isEmpty()) {
            for (JBPopup popup : popups) {
              popup.cancel();
            }
            return false;
          }
          if (myScopeUI.hideAllPopups()) {
            return false;
          }
          DimensionService.getInstance().setSize(SERVICE_KEY, myBalloon.getSize(), myHelper.getProject() );
          DimensionService.getInstance().setLocation(SERVICE_KEY, myBalloon.getLocationOnScreen(), myHelper.getProject() );
          ((FindManagerImpl)FindManager.getInstance(myProject)).changeGlobalSettings(myHelper.getModel());
          return true;
        })
        .createPopup();
      Disposer.register(myBalloon, myDisposable);
      registerCloseAction(myBalloon);
      final Window window = WindowManager.getInstance().suggestParentWindow(myProject);
      Component parent = UIUtil.findUltimateParent(window);
      RelativePoint showPoint = null;
      Point screenPoint = DimensionService.getInstance().getLocation(SERVICE_KEY);
      if (screenPoint != null) {
        showPoint = new RelativePoint(screenPoint);
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
      panelSize.height *= 2;
      if (prev != null && prev.height < panelSize.height) prev.height = panelSize.height;
      myBalloon.setMinimumSize(panelSize);
      if (prev == null) {
        panelSize.height *= 1.5;
        panelSize.width *= 1.15;
      }
      myBalloon.setSize(prev != null ? prev : panelSize);

      if (showPoint != null && showPoint.getComponent() != null) {
        myBalloon.show(showPoint);
      } else {
        myBalloon.showCenteredInCurrentWindow(myProject);
      }
    }
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
    ItemListener liveResultsPreviewUpdateListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        scheduleResultsUpdate();
      }
    };
    myCbCaseSensitive.addItemListener(liveResultsPreviewUpdateListener);
    myCbPreserveCase = createCheckBox("find.options.replace.preserve.case");
    myCbPreserveCase.addItemListener(liveResultsPreviewUpdateListener);
    myCbPreserveCase.setVisible(myHelper.getModel().isReplaceState());
    myCbWholeWordsOnly = createCheckBox("find.popup.whole.words");
    myCbWholeWordsOnly.addItemListener(liveResultsPreviewUpdateListener);
    myCbRegularExpressions = createCheckBox("find.popup.regex");
    myCbRegularExpressions.addItemListener(liveResultsPreviewUpdateListener);
    myCbFileFilter = createCheckBox("find.popup.filemask");
    myCbFileFilter.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
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
      }
    });
    myCbFileFilter.addItemListener(liveResultsPreviewUpdateListener);
    myFileMaskField =
      new TextFieldWithAutoCompletion<String>(myProject, new TextFieldWithAutoCompletion.StringsCompletionProvider(myFileMasks, null),
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
    myOkActionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FindModel validateModel = myHelper.getModel().clone();
        applyTo(validateModel, false);

        ValidationInfo validationInfo = getValidationInfo(validateModel);

        if (validationInfo == null) {
          myHelper.getModel().copyFrom(validateModel);
          myHelper.updateFindSettings();
          myHelper.doOKAction();
        }
        else {
          String message = validationInfo.message;
          Messages.showMessageDialog(
            FindPopupPanel.this,
            message,
            CommonBundle.getErrorTitle(),
            Messages.getErrorIcon()
          );
          return;
        }
        Disposer.dispose(myBalloon);
      }
    };
    myOKButton.addActionListener(myOkActionListener);
    boolean enterAsOK = Registry.is("ide.find.enter.as.ok", false);

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (enterAsOK || myHelper.isReplaceState()) {
          myOkActionListener.actionPerformed(null);
        } else {
          navigateToSelectedUsage();
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(ENTER), this);
    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myHelper.isReplaceState()) return;
        if (enterAsOK) {
          navigateToSelectedUsage();
        } else {
          myOkActionListener.actionPerformed(null);
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(ENTER_WITH_MODIFIERS), this);
    
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
    DocumentAdapter documentAdapter = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        mySearchComponent.setRows(Math.max(1, Math.min(3, StringUtil.countChars(mySearchComponent.getText(), '\n') + 1)));
        myReplaceComponent.setRows(Math.max(1, Math.min(3, StringUtil.countChars(myReplaceComponent.getText(), '\n') + 1)));

        if (myBalloon == null) return;
        if (e.getDocument() == mySearchComponent.getDocument()) {
          scheduleResultsUpdate();
        }
      }
    };
    mySearchComponent.getDocument().addDocumentListener(documentAdapter);
    myReplaceComponent.getDocument().addDocumentListener(documentAdapter);
    mySearchTextArea.setMultilineEnabled(false);
    myReplaceTextArea.setMultilineEnabled(false);

    Pair<FindPopupScopeUI.ScopeType, JComponent>[] scopeComponents = myScopeUI.getComponents();
    List<AnAction> scopeActions = new LinkedList<>();

    myScopeDetailsPanel = new JPanel(new CardLayout());
    myScopeDetailsPanel.setBorder(JBUI.Borders.emptyBottom(UIUtil.isUnderDefaultMacTheme() ? 0 : 3));

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
    myResultsPreviewTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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

    ActionListener helpAction = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        HelpManager.getInstance().invokeHelp("reference.dialogs.findinpath");
      }
    };
    registerKeyboardAction(helpAction,KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),JComponent.WHEN_IN_FOCUSED_WINDOW);
    registerKeyboardAction(helpAction,KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),JComponent.WHEN_IN_FOCUSED_WINDOW);

    myUsagePreviewPanel = new UsagePreviewPanel(myProject, new UsageViewPresentation(), Registry.is("ide.find.as.popup.editable.code")) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(myResultsPreviewTable.getWidth(), Math.max(getHeight(), getLineHeight() * 15));
      }
    };
    Disposer.register(myDisposable, myUsagePreviewPanel);
    myResultsPreviewTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        int index = myResultsPreviewTable.getSelectedRow();
        if (index != -1) {
          UsageInfo2UsageAdapter adapter = (UsageInfo2UsageAdapter)myResultsPreviewTable.getModel().getValueAt(index, 0);
          myUsagePreviewPanel.updateLayout(adapter.isValid() ? Arrays.asList(adapter.getMergedInfos()) : null);
          VirtualFile file = adapter.getFile();
          String path = "";
          if (file != null) {
            String relativePath = VfsUtilCore.getRelativePath(file, myProject.getBaseDir());
            if (relativePath == null) relativePath = file.getPath();
            path = "<html><body>&nbsp;&nbsp;&nbsp;" +
                   relativePath
                     .replace(file.getName(), "<b>" + file.getName() + "</b>") + "</body></html>";
          }
          myUsagePreviewPanel.setBorder(IdeBorderFactory.createTitledBorder(path, false, new JBInsets(8, 0, 0, 0)).setShowLine(false));
        }
        else {
          myUsagePreviewPanel.updateLayout(null);
          myUsagePreviewPanel.setBorder(IdeBorderFactory.createBorder());
        }
      }
    });
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
    add(myFilterContextButton, "wrap");
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
        return (c == myResultsPreviewTable) ? mySearchComponent : super.getComponentAfter(container, c);
      }
    });
  }

  @NotNull
  private static StateRestoringCheckBox createCheckBox(String message) {
    StateRestoringCheckBox checkBox = new StateRestoringCheckBox(FindBundle.message(message));
    checkBox.setFocusable(false);
    return checkBox;
  }

  private void registerCloseAction(JBPopup popup) {
    final AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction closeAction = new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myBalloon != null && myBalloon.isVisible()) {
          myBalloon.cancel();
        }
      }
    };
    closeAction.registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), popup.getContent(), popup);
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
    if (Registry.is("ide.find.enter.as.ok", false) || isReplaceState) {
      myOKHintLabel.setText(KeymapUtil.getKeystrokeText(ENTER));
    } else {
      myOKHintLabel.setText(KeymapUtil.getKeystrokeText(ENTER_WITH_MODIFIERS));
    }
    myOKButton.setText(FindBundle.message(isReplaceState ? "find.popup.replace.button" : "find.popup.find.button"));
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

  public void scheduleResultsUpdate() {
    if (myBalloon == null || !myBalloon.isVisible()) return;
    if (mySearchRescheduleOnCancellationsAlarm == null || mySearchRescheduleOnCancellationsAlarm.isDisposed()) return;
    updateControls();
    mySearchRescheduleOnCancellationsAlarm.cancelAllRequests();
    mySearchRescheduleOnCancellationsAlarm.addRequest(() -> findSettingsChanged(), 100);
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
    myHelper.updateFindSettings();
    FindModel findInProjectModel = FindManager.getInstance(myProject).getFindInProjectModel();
    FindModel copy = new FindModel();
    copy.copyFrom(findInProjectModel);

    findInProjectModel.copyFrom(myHelper.getModel());
    FindSettings findSettings = FindSettings.getInstance();
    myScopeUI.applyTo(findSettings, mySelectedScope);
    findSettings.setFileMask(myHelper.getModel().getFileFilter());

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

    myCodePreviewComponent.setVisible(false);

    mySearchTextArea.setInfoText(null);
    myResultsPreviewTable.setModel(model);

    if (result != null) {
      myResultsPreviewTable.getEmptyText().setText(UIBundle.message("message.nothingToShow") + " ("+result.message+")");
      onStop(hash);
      return;
    }

    GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(
      FindInProjectUtil.getScopeFromModel(myProject, myHelper.myPreviousModel), myProject);
    myResultsPreviewTable.getColumnModel().getColumn(0).setCellRenderer(
      new FindDialog.UsageTableCellRenderer(myCbFileFilter.isSelected(), false, scope));
    myResultsPreviewTable.getEmptyText().setText("Searching...");
    onStart(hash);

    final AtomicInteger resultsCount = new AtomicInteger();
    final AtomicInteger resultsFilesCount = new AtomicInteger();

    ProgressIndicatorUtils.scheduleWithWriteActionPriority(myResultsPreviewSearchProgress, new ReadTask() {
      @Override
      public Continuation performInReadAction(@NotNull ProgressIndicator indicator) {
        final UsageViewPresentation presentation =
          FindInProjectUtil.setupViewPresentation(findSettings.isShowResultsInSeparateView(), /*findModel*/myHelper.getModel().clone());
        final boolean showPanelIfOnlyOneUsage = !findSettings.isSkipResultsWithOneUsage();

        final FindUsagesProcessPresentation processPresentation =
          FindInProjectUtil.setupProcessPresentation(myProject, showPanelIfOnlyOneUsage, presentation);
        ThreadLocal<VirtualFile> lastUsageFileRef = new ThreadLocal<>();
        ThreadLocal<Usage> recentUsageRef = new ThreadLocal<>();

        FindInProjectUtil.findUsages(myHelper.getModel().clone(), myProject, info -> {
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
          final boolean merged;
          Usage recent = recentUsageRef.get();
          UsageInfo2UsageAdapter recentAdapter =
            recent != null && recent instanceof UsageInfo2UsageAdapter ? (UsageInfo2UsageAdapter)recent : null;
          UsageInfo2UsageAdapter currentAdapter = usage instanceof UsageInfo2UsageAdapter ? (UsageInfo2UsageAdapter)usage : null;
          if (currentAdapter != null && recentAdapter != null) {
            merged = recentAdapter.merge(currentAdapter);
          } else {
            merged = false;
          }
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
            if (occurrences == 0) myResultsPreviewTable.getEmptyText().setText(UIBundle.message("message.nothingToShow"));
            myCodePreviewComponent.setVisible(occurrences > 0);
            StringBuilder stringBuilder = new StringBuilder();
            if (occurrences > 0) {
              stringBuilder.append(Math.min(ShowUsagesAction.getUsagesPageSize(), occurrences));
              boolean foundAllUsages = occurrences < ShowUsagesAction.getUsagesPageSize();
              if (!foundAllUsages) {
                stringBuilder.append("+");
              }
              stringBuilder.append(UIBundle.message("message.matches", occurrences));
              stringBuilder.append(" in ");
              stringBuilder.append(filesWithOccurrences);
              if (!foundAllUsages) {
                stringBuilder.append("+");
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
            if (resultsCount.get() == 0) myResultsPreviewTable.getEmptyText().setText(UIBundle.message("message.nothingToShow"));
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

  private void onStart(int hash) {
    myLoadingHash = hash;
    myLoadingDecorator.startLoading(false);
  }


  private void onStop(int hash) {
    if (hash != myLoadingHash) {
      return;
    }
    UIUtil.invokeLaterIfNeeded(() -> {
      myLoadingDecorator.stopLoading();
    });
  }

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
    String stringToFind = getStringToFind();
    model.setStringToFind(stringToFind);

    if (model.isReplaceState()) {
      model.setPromptOnReplace(true);
      model.setReplaceAll(false);
      String stringToReplace = getStringToReplace();
      model.setStringToReplace(StringUtil.convertLineSeparators(stringToReplace));
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
    Usage[] usages = getSelectedUsages();
    if (usages != null) {
      applyTo(FindManager.getInstance(myProject).getFindInProjectModel(), false);
      myBalloon.cancel();

      usages[0].navigate(true);
      for (int i = 1; i < usages.length; ++i) usages[i].highlightInEditor();
    }
  }

  @Nullable
  private Usage[] getSelectedUsages() {
    int[] rows = myResultsPreviewTable.getSelectedRows();
    List<Usage> usages = null;
    for (int row : rows) {
      Object valueAt = myResultsPreviewTable.getModel().getValueAt(row, 0);
      if (valueAt instanceof Usage) {
        if (usages == null) usages = new SmartList<>();
        Usage at = (Usage)valueAt;
        usages.add(at);
      }
    }
    return usages != null ? ContainerUtil.toArray(usages, Usage.EMPTY_ARRAY) : null;
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

  private class MySwitchContextToggleAction extends ToggleAction {
    public MySwitchContextToggleAction(FindModel.SearchContext context) {
      super(FindDialog.getPresentableName(context));
    }

    @Override
    public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
      super.beforeActionPerformedUpdate(e);
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

    public MySelectScopeToggleAction(FindPopupScopeUI.ScopeType scope) {
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

  private class MyShowFilterPopupAction extends AnAction {
    private final DefaultActionGroup mySwitchContextGroup;

    public MyShowFilterPopupAction() {
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
}
