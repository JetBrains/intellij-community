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
package com.intellij.ide.actions;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.Language;
import com.intellij.lang.LanguagePsiElementExternalizer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.OnOffButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
public class SearchEverywhereAction extends AnAction implements CustomComponentAction, DumbAware, DataProvider, RightAlignedToolbarAction {
  public static final String SE_HISTORY_KEY = "SearchEverywhereHistoryKey";
  public static final int SEARCH_FIELD_COLUMNS = 25;
  private static final int MAX_CLASSES = 6;
  private static final int MAX_FILES = 6;
  private static final int MAX_RUN_CONFIGURATION = 6;
  private static final int MAX_TOOL_WINDOWS = 4;
  private static final int MAX_SYMBOLS = 6;
  private static final int MAX_SETTINGS = 5;
  private static final int MAX_ACTIONS = 5;
  private static final int MAX_RECENT_FILES = 10;
  private static final int DEFAULT_MORE_STEP_COUNT = 15;
  public static final int MAX_SEARCH_EVERYWHERE_HISTORY = 50;
  public static final int MAX_TOP_HIT = 15;
  private static final Logger LOG = Logger.getInstance(SearchEverywhereAction.class);
  private static final Border RENDERER_BORDER = JBUI.Borders.empty(1, 0);
  private static final Border RENDERER_TITLE_BORDER = JBUI.Borders.emptyTop(3);

  private SearchEverywhereAction.MyListRenderer myRenderer;
  MySearchTextField myPopupField;
  private volatile GotoClassModel2 myClassModel;
  private volatile GotoFileModel myFileModel;
  private volatile GotoActionItemProvider myActionProvider;
  private volatile GotoSymbolModel2 mySymbolsModel;
  private Component myFocusComponent;
  private JBPopup myPopup;
  private Map<String, String> myConfigurables = new HashMap<>();

  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication());
  private JBList myList;
  private JCheckBox myNonProjectCheckBox;
  private AnActionEvent myActionEvent;
  private Set<AnAction> myDisabledActions = new HashSet<>();
  private Component myContextComponent;
  private CalcThread myCalcThread;
  private static AtomicBoolean ourShiftIsPressed = new AtomicBoolean(false);
  private static AtomicBoolean showAll = new AtomicBoolean(false);
  private volatile ActionCallback myCurrentWorker = ActionCallback.DONE;
  private int myHistoryIndex = 0;
  boolean mySkipFocusGain = false;

  public static final Key<JBPopup> SEARCH_EVERYWHERE_POPUP = new Key<>("SearchEverywherePopup");

  static {
    ModifierKeyDoubleClickHandler.getInstance().registerAction(IdeActions.ACTION_SEARCH_EVERYWHERE, KeyEvent.VK_SHIFT, -1, false);

    IdeEventQueue.getInstance().addPostprocessor(event -> {
      if (event instanceof KeyEvent) {
        final int keyCode = ((KeyEvent)event).getKeyCode();
        if (keyCode == KeyEvent.VK_SHIFT) {
          ourShiftIsPressed.set(event.getID() == KeyEvent.KEY_PRESSED);
        }
      }
      return false;
    }, null);
  }

  private volatile JBPopup myBalloon;
  private int myPopupActualWidth;
  private Component myFocusOwner;
  private ChooseByNamePopup myFileChooseByName;
  private ChooseByNamePopup myClassChooseByName;
  private ChooseByNamePopup mySymbolsChooseByName;
  private StructureViewModel myStructureModel;


  private Editor myEditor;
  private FileEditor myFileEditor;
  private PsiFile myFile;
  private HistoryItem myHistoryItem;

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new BorderLayoutPanel() {
      @Override
      public Dimension getPreferredSize() {
        return JBUI.size(25);
      }
    };
    panel.setOpaque(false);

    final JLabel label = new JBLabel(AllIcons.Actions.FindPlain) {
      {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
        enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK);
      }
    };
    panel.add(label, BorderLayout.CENTER);
    initTooltip(label);
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (myBalloon != null) {
          myBalloon.cancel();
        }
        myFocusOwner = IdeFocusManager.findInstance().getFocusOwner();
        label.setToolTipText(null);
        IdeTooltipManager.getInstance().hideCurrentNow(false);
        actionPerformed(null, e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (myBalloon == null || myBalloon.isDisposed()) {
          label.setIcon(AllIcons.Actions.Find);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (myBalloon == null || myBalloon.isDisposed()) {
          label.setIcon(AllIcons.Actions.FindPlain);
        }
      }
    });

    return panel;
  }

  private static Gradient getGradientColors() {
    return new Gradient(
      new JBColor(0x6593f2, 0x40505e),
      new JBColor(0x2e6fcd, 0x354157));
  }

  private void updateComponents() {
    myList = new JBList(new SearchListModel()) {
      int lastKnownHeight = JBUI.scale(30);
      @Override
      public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        if (size.height == -1) {
          size.height = lastKnownHeight;
        } else {
          lastKnownHeight = size.height;
        }
        return new Dimension(Math.max(myBalloon.getSize().width, Math.min(size.width - 2, getPopupMaxWidth())), myList.isEmpty() ? JBUI.scale(30) : size.height);
      }

      @Override
      public void clearSelection() {
        //avoid blinking
      }

      @Override
      public Object getSelectedValue() {
        try {
          return super.getSelectedValue();
        } catch (Exception e) {
          return null;
        }
      }
    };
    myRenderer = new MyListRenderer(myList);
    myList.setCellRenderer(myRenderer);
    myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        e.consume();
        final int i = myList.locationToIndex(e.getPoint());
        if (i != -1) {
          mySkipFocusGain = true;
          getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(getField(), true));
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            myList.setSelectedIndex(i);
            doNavigate(i);
          });
        }
      }
    });

    myNonProjectCheckBox = new JCheckBox();
    myNonProjectCheckBox.setOpaque(false);
    myNonProjectCheckBox.setAlignmentX(1.0f);
    myNonProjectCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (showAll.get() != myNonProjectCheckBox.isSelected()) {
          showAll.set(!showAll.get());
          final JTextField editor = UIUtil.findComponentOfType(myBalloon.getContent(), JTextField.class);
          if (editor != null) {
            final String pattern = editor.getText();
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(() -> {
              if (editor.hasFocus()) {
                rebuildList(pattern);
              }
            }, 30);
          }
        }
      }
    });
  }

  private static void initTooltip(JLabel label) {
    final String shortcutText;
    shortcutText = getShortcut();

    label.setToolTipText("<html><body>Search Everywhere<br/>Press <b>"
                                 + shortcutText
                                 + "</b> to access<br/> - Classes<br/> - Files<br/> - Tool Windows<br/> - Actions<br/> - Settings</body></html>");

  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return null;
  }

  private static String getShortcut() {
    Shortcut[] shortcuts = getActiveKeymapShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).getShortcuts();
    if (shortcuts.length == 0) {
      return "Double" + (SystemInfo.isMac ? FontUtil.thinSpace() + MacKeymapUtil.SHIFT : " Shift");
    }
    return KeymapUtil.getShortcutsText(shortcuts);
  }

  private void initSearchField(final MySearchTextField search) {
    final JTextField editor = search.getTextEditor();
//    onFocusLost();
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        if (editor.hasFocus()) {
          rebuildList(pattern);
        }
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (mySkipFocusGain) {
          mySkipFocusGain = false;
          return;
        }
        String text = GotoActionBase.getInitialTextForNavigation(myEditor);
        text = text != null ? text.trim() : "";

        search.setText(text);
        search.getTextEditor().setForeground(UIUtil.getLabelForeground());
        search.selectText();
        //titleIndex = new TitleIndexes();
        editor.setColumns(SEARCH_FIELD_COLUMNS);
        myFocusComponent = e.getOppositeComponent();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          final JComponent parent = (JComponent)editor.getParent();
          parent.revalidate();
          parent.repaint();
        });
        //if (myPopup != null && myPopup.isVisible()) {
        //  myPopup.cancel();
        //  myPopup = null;
        //}
        rebuildList(text);
      }

      @Override
      public void focusLost(FocusEvent e) {
        if ( myPopup instanceof AbstractPopup && myPopup.isVisible()
             && ((myList == e.getOppositeComponent()) || ((AbstractPopup)myPopup).getPopupWindow() == e.getOppositeComponent())) {
          return;
        }
        if (myNonProjectCheckBox == e.getOppositeComponent()) {
          mySkipFocusGain = true;
          getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(editor, true));
          return;
        }
        onFocusLost();
      }
    });
  }

  private void jumpNextGroup(boolean forward) {
    final int index = myList.getSelectedIndex();
    final SearchListModel model = getModel();
    if (index >= 0) {
      final int newIndex = forward ? model.next(index) : model.prev(index);
      myList.setSelectedIndex(newIndex);
      int more = model.next(newIndex) - 1;
      if (more < newIndex) {
        more = myList.getItemsCount() - 1;
      }
      ScrollingUtil.ensureIndexIsVisible(myList, more, forward ? 1 : -1);
      ScrollingUtil.ensureIndexIsVisible(myList, newIndex, forward ? 1 : -1);
    }
  }

  private SearchListModel getModel() {
    return (SearchListModel)myList.getModel();
  }

  private ActionCallback onFocusLost() {
    final ActionCallback result = new ActionCallback();
    //noinspection SSBasedInspection
    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        if (myCalcThread != null) {
          myCalcThread.cancel();
          //myCalcThread = null;
        }
        myAlarm.cancelAllRequests();
        if (myBalloon != null && !myBalloon.isDisposed() && myPopup != null && !myPopup.isDisposed()) {
          myBalloon.cancel();
          myPopup.cancel();
        }

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> ActionToolbarImpl.updateAllToolbarsImmediately());
      }
      finally {
        result.setDone();
      }
    });
    return result;
  }

  private SearchTextField getField() {
    return myPopupField;
  }

  private void doNavigate(final int index) {
    final DataManager dataManager = DataManager.getInstance();
    if (dataManager == null) return;
    final Project project = CommonDataKeys.PROJECT.getData(dataManager.getDataContext(getField().getTextEditor()));
    assert project != null;
    final SearchListModel model = getModel();
    if (isMoreItem(index)) {
      final String pattern = myPopupField.getText();
      WidgetID wid = null;
      if (index == model.moreIndex.classes) wid = WidgetID.CLASSES;
      else if (index == model.moreIndex.files) wid = WidgetID.FILES;
      else if (index == model.moreIndex.settings) wid = WidgetID.SETTINGS;
      else if (index == model.moreIndex.actions) wid = WidgetID.ACTIONS;
      else if (index == model.moreIndex.symbols) wid = WidgetID.SYMBOLS;
      else if (index == model.moreIndex.runConfigurations) wid = WidgetID.RUN_CONFIGURATIONS;
      if (wid != null) {
        final WidgetID widgetID = wid;
        myCurrentWorker.doWhenProcessed(() -> {
          myCalcThread = new CalcThread(project, pattern, true);
          myPopupActualWidth = 0;
          myCurrentWorker = myCalcThread.insert(index, widgetID);
        });

        return;
      }
    }
    final String pattern = getField().getText();
    final Object value = myList.getSelectedValue();
    saveHistory(project, pattern, value);
    IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(getField().getTextEditor());
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }

    if (value instanceof BooleanOptionDescription) {
      final BooleanOptionDescription option = (BooleanOptionDescription)value;
      option.setOptionState(!option.isOptionEnabled());
      myList.revalidate();
      myList.repaint();
      getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(getField(), true));
      return;
    }

    if (value instanceof OptionsTopHitProvider) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> getField().setText("#" + ((OptionsTopHitProvider)value).getId() + " "));
      return;
    }
    Runnable onDone = null;

    AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      if (value instanceof PsiElement) {
        onDone = () -> NavigationUtil.activateFileWithPsiElement((PsiElement)value, true);
        return;
      }
      else if (isVirtualFile(value)) {
        onDone = () -> OpenSourceUtil.navigate(true, new OpenFileDescriptor(project, (VirtualFile)value));
        return;
      }
      else if (isActionValue(value) || isSetting(value) || isRunConfiguration(value)) {
        focusManager.requestDefaultFocus(true);
        final Component comp = myContextComponent;
        final AnActionEvent event = myActionEvent;
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
          Component c = comp;
          if (c == null) {
            c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
          }

          if (isRunConfiguration(value)) {
            ChooseRunConfigurationPopup.ItemWrapper itemWrapper = (ChooseRunConfigurationPopup.ItemWrapper)value;
            RunnerAndConfigurationSettings settings = ObjectUtils.tryCast(itemWrapper.getValue(), RunnerAndConfigurationSettings.class);
            if (settings != null) {
              Executor executor = findExecutor(settings);
              if (executor != null) {
                itemWrapper.perform(project, executor, dataManager.getDataContext(c));
              }
            }
          } else {
            GotoActionAction.openOptionOrPerformAction(value, pattern, project, c, event);
            if (isToolWindowAction(value)) return;
          }
        });
        return;
      }
      else if (value instanceof Navigatable) {
        onDone = () -> OpenSourceUtil.navigate(true, (Navigatable)value);
        return;
      }
    }
    finally {
      token.finish();
      final ActionCallback callback = onFocusLost();
      if (onDone != null) {
        callback.doWhenDone(onDone);
      }
    }
    focusManager.requestDefaultFocus(true);
  }

  private boolean isMoreItem(int index) {
    final SearchListModel model = getModel();
    return index == model.moreIndex.classes ||
           index == model.moreIndex.files ||
           index == model.moreIndex.settings ||
           index == model.moreIndex.actions ||
           index == model.moreIndex.symbols ||
           index == model.moreIndex.runConfigurations;
  }

  private void rebuildList(final String pattern) {
    assert EventQueue.isDispatchThread() : "Must be EDT";
    if (myCalcThread != null && !myCurrentWorker.isProcessed()) {
      myCurrentWorker = myCalcThread.cancel();
    }
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(getField().getTextEditor()));

    assert project != null;
    myRenderer.myProject = project;
    final Runnable run = () -> {
      myCalcThread = new CalcThread(project, pattern, false);
      myPopupActualWidth = 0;
      myCurrentWorker = myCalcThread.start();
    };
    if (myCurrentWorker.isDone()) {
      myCurrentWorker.doWhenDone(run);
    } else {
      myCurrentWorker.doWhenRejected(run);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    actionPerformed(e, null);
  }

  public void actionPerformed(AnActionEvent e, MouseEvent me) {
    if (myBalloon != null && myBalloon.isVisible()) {
      showAll.set(!showAll.get());
      myNonProjectCheckBox.setSelected(showAll.get());
//      myPopupField.getTextEditor().setBackground(showAll.get() ? new JBColor(new Color(0xffffe4), new Color(0x494539)) : UIUtil.getTextFieldBackground());
      rebuildList(myPopupField.getText());
      return;
    }
    myCurrentWorker = ActionCallback.DONE;
    if (e != null) {
      myEditor = e.getData(CommonDataKeys.EDITOR);
      myFileEditor = e.getData(PlatformDataKeys.FILE_EDITOR);
      myFile = e.getData(CommonDataKeys.PSI_FILE);
    }
    if (e == null && myFocusOwner != null) {
      e = AnActionEvent.createFromAnAction(this, me, ActionPlaces.UNKNOWN, DataManager.getInstance().getDataContext(myFocusOwner));
    }
    if (e == null) return;
    final Project project = e.getProject();
    if (project == null) return;

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> LookupManager.getInstance(project).hideActiveLookup());

    updateComponents();
    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
    Window wnd = myContextComponent != null ? SwingUtilities.windowForComponent(myContextComponent)
      : KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if (wnd == null && myContextComponent instanceof Window) {
      wnd = (Window)myContextComponent;
    }
    if (wnd == null || wnd.getParent() != null) return;
    myActionEvent = e;
    if (myPopupField != null) {
      Disposer.dispose(myPopupField);
    }
    myPopupField = new MySearchTextField();
    myPopupField.getTextEditor().addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        myHistoryIndex = 0;
        myHistoryItem = null;
      }

      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
          myList.repaint();
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
          myList.repaint();
        }
      }
    });
    initSearchField(myPopupField);
    myPopupField.setOpaque(false);
    final JTextField editor = myPopupField.getTextEditor();
    editor.setColumns(SEARCH_FIELD_COLUMNS);
    final JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        final Gradient gradient = getGradientColors();
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, gradient.getStartColor(), 0, getHeight(), gradient.getEndColor()));
        g.fillRect(0, 0, getWidth(), getHeight());
      }
    };
    final JLabel title = new JLabel(" Search Everywhere:       ");
    final JPanel topPanel = new NonOpaquePanel(new BorderLayout());
    title.setForeground(new JBColor(Gray._240, Gray._200));
    if (SystemInfo.isMac) {
      title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() - 1f));
    } else {
      title.setFont(title.getFont().deriveFont(Font.BOLD));
    }
    topPanel.add(title, BorderLayout.WEST);
    final JPanel controls = new JPanel(new BorderLayout());
    controls.setOpaque(false);
    final JLabel settings = new JLabel(AllIcons.General.SearchEverywhereGear);
    new ClickListener(){
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        showSettings();
        return true;
      }
    }.installOn(settings);
    controls.add(settings, BorderLayout.EAST);
    myNonProjectCheckBox.setForeground(new JBColor(Gray._240, Gray._200));
    myNonProjectCheckBox.setText("<html>Include non-project items <b>" + getShortcut() + "</b>  </html>");
    if (!NonProjectScopeDisablerEP.isSearchInNonProjectDisabled()) {
      controls.add(myNonProjectCheckBox, BorderLayout.WEST);
    }
    topPanel.add(controls, BorderLayout.EAST);
    panel.add(myPopupField, BorderLayout.CENTER);
    panel.add(topPanel, BorderLayout.NORTH);
    panel.setBorder(JBUI.Borders.empty(3, 5, 4, 5));
    DataManager.registerDataProvider(panel, this);
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, editor);
    myBalloon = builder
      .setCancelOnClickOutside(true)
      .setModalContext(false)
      .setRequestFocus(true)
      .setCancelCallback(() -> !mySkipFocusGain)
      .createPopup();
    myBalloon.getContent().setBorder(JBUI.Borders.empty());
    final Window window = WindowManager.getInstance().suggestParentWindow(project);

    project.getMessageBus().connect(myBalloon).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
      }

      @Override
      public void exitDumbMode() {
        ApplicationManager.getApplication().invokeLater(() -> rebuildList(myPopupField.getText()));
      }
    });

    Component parent = UIUtil.findUltimateParent(window);
    registerDataProvider(panel, project);
    final RelativePoint showPoint;
    if (parent != null) {
      int height = UISettings.getInstance().getShowMainToolbar() ? 135 : 115;
      if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
        height -= 20;
      }
      showPoint = new RelativePoint(parent, new Point((parent.getSize().width - panel.getPreferredSize().width) / 2, height));
    } else {
      showPoint = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    }
    myList.setFont(UIUtil.getListFont());
    myBalloon.show(showPoint);
    initSearchActions(myBalloon, myPopupField);
    IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
    focusManager.requestFocus(editor, true);
    FeatureUsageTracker.getInstance().triggerFeatureUsed(IdeActions.ACTION_SEARCH_EVERYWHERE);
  }

  private void showSettings() {
    myPopupField.setText("");
    final SearchListModel model = new SearchListModel();
    //model.addElement(new SEOption("Show current file structure elements", "search.everywhere.structure"));
    model.addElement(new SEOption("Show files", "search.everywhere.files"));
    model.addElement(new SEOption("Show symbols", "search.everywhere.symbols"));
    model.addElement(new SEOption("Show tool windows", "search.everywhere.toolwindows"));
    model.addElement(new SEOption("Show run configurations", "search.everywhere.configurations"));
    model.addElement(new SEOption("Show actions", "search.everywhere.actions"));
    model.addElement(new SEOption("Show IDE settings", "search.everywhere.settings"));

    if (myCalcThread != null && !myCurrentWorker.isProcessed()) {
      myCurrentWorker = myCalcThread.cancel();
    }
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }
    myCurrentWorker.doWhenProcessed(() -> {
      myList.setModel(model);
      updatePopupBounds();
    });
  }

  static class SEOption extends BooleanOptionDescription {
    private final String myKey;

    public SEOption(String option, String registryKey) {
      super(option, null);
      myKey = registryKey;
    }

    @Override
    public boolean isOptionEnabled() {
      return Registry.is(myKey);
    }

    @Override
    public void setOptionState(boolean enabled) {
      Registry.get(myKey).setValue(enabled);
    }
  }

  private static void saveHistory(Project project, String text, Object value) {
    if (project == null || project.isDisposed() || !project.isInitialized()) {
      return;
    }
    HistoryType type = null;
    String fqn = null;
    if (isActionValue(value)) {
      type = HistoryType.ACTION;
      AnAction action = (AnAction)(value instanceof GotoActionModel.ActionWrapper ? ((GotoActionModel.ActionWrapper)value).getAction() : value);
      fqn = ActionManager.getInstance().getId(action);
    } else if (value instanceof VirtualFile) {
      type = HistoryType.FILE;
      fqn = ((VirtualFile)value).getUrl();
    } else if (value instanceof ChooseRunConfigurationPopup.ItemWrapper) {
      type = HistoryType.RUN_CONFIGURATION;
      fqn = ((ChooseRunConfigurationPopup.ItemWrapper)value).getText();
    } else if (value instanceof PsiElement) {
      final PsiElement psiElement = (PsiElement)value;
      final Language language = psiElement.getLanguage();
      final String name = LanguagePsiElementExternalizer.INSTANCE.forLanguage(language).getQualifiedName(psiElement);
      if (name != null) {
        type = HistoryType.PSI;
        fqn = language.getID() + "://" + name;
      }
    }

    final PropertiesComponent storage = PropertiesComponent.getInstance(project);
    final String[] values = storage.getValues(SE_HISTORY_KEY);
    List<HistoryItem> history = new ArrayList<>();
    if (values != null) {
      for (String s : values) {
        final String[] split = s.split("\t");
        if (split.length != 3 || text.equals(split[0])) {
          continue;
        }
        if (!StringUtil.isEmpty(split[0])) {
          history.add(new HistoryItem(split[0], split[1], split[2]));
        }
      }
    }
    history.add(0, new HistoryItem(text, type == null ? null : type.name(), fqn));

    if (history.size() > MAX_SEARCH_EVERYWHERE_HISTORY) {
      history = history.subList(0, MAX_SEARCH_EVERYWHERE_HISTORY);
    }
    final String[] newValues = new String[history.size()];
    for (int i = 0; i < newValues.length; i++) {
      newValues[i] = history.get(i).toString();
    }
    storage.setValues(SE_HISTORY_KEY, newValues);
  }

  @Nullable
  public Executor findExecutor(@NotNull RunnerAndConfigurationSettings settings) {
    final Executor runExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final Executor debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);

    Executor executor = ourShiftIsPressed.get() ? runExecutor : debugExecutor;
    RunConfiguration runConf = settings.getConfiguration();
    if (executor == null) return null;
    ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), runConf);
    if (runner == null) {
      executor = runExecutor == executor ? debugExecutor : runExecutor;
    }
    return executor;
  }

  private void registerDataProvider(JPanel panel, final Project project) {
    DataManager.registerDataProvider(panel, new DataProvider() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        final Object value = myList.getSelectedValue();
        if (CommonDataKeys.PSI_ELEMENT.is(dataId) && value instanceof PsiElement) {
          return value;
        }
        if (CommonDataKeys.VIRTUAL_FILE.is(dataId) && value instanceof VirtualFile) {
          return value;
        }
        if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
          if (value instanceof Navigatable) return value;
          if (value instanceof ChooseRunConfigurationPopup.ItemWrapper) {
            final Object config = ((ChooseRunConfigurationPopup.ItemWrapper)value).getValue();
            if (config instanceof RunnerAndConfigurationSettings) {
              return new Navigatable() {
                @Override
                public void navigate(boolean requestFocus) {
                  Executor executor = findExecutor((RunnerAndConfigurationSettings)config);
                  RunDialog.editConfiguration(project, (RunnerAndConfigurationSettings)config, "Edit Configuration", executor);
                }

                @Override
                public boolean canNavigate() {
                  return true;
                }

                @Override
                public boolean canNavigateToSource() {
                  return true;
                }
              };
            }
          }
        }
        if (PlatformDataKeys.SEARCH_INPUT_TEXT.is(dataId)) {
          return myPopupField == null ? null : myPopupField.getText();
        }
        return null;
      }
    });
  }

  private void initSearchActions(JBPopup balloon, MySearchTextField searchTextField) {
    final JTextField editor = searchTextField.getTextEditor();
    new DumbAwareAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        jumpNextGroup(true);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("TAB"), editor, balloon);
    new DumbAwareAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        jumpNextGroup(false);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), editor, balloon);
    final AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    new DumbAwareAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myBalloon != null && myBalloon.isVisible()) {
          myBalloon.cancel();
        }
        if (myPopup != null && myPopup.isVisible()) {
          myPopup.cancel();
        }
      }
    }.registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), editor, balloon);
    new DumbAwareAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int index = myList.getSelectedIndex();
        if (index != -1) {
          doNavigate(index);
        }
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER", "shift ENTER"), editor, balloon);
    new DumbAwareAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        final PropertiesComponent storage = PropertiesComponent.getInstance(e.getProject());
        final String[] values = storage.getValues(SE_HISTORY_KEY);
        if (values != null) {
          if (values.length > myHistoryIndex) {
            final List<String> data = StringUtil.split(values[myHistoryIndex], "\t");
            myHistoryItem = new HistoryItem(data.get(0), data.get(1), data.get(2));
            myHistoryIndex++;
            editor.setText(myHistoryItem.pattern);
            editor.setCaretPosition(myHistoryItem.pattern.length());
            editor.moveCaretPosition(0);
          }
        }
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(editor.getCaretPosition() == 0);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("LEFT"), editor, balloon);
  }

  private static class MySearchTextField extends SearchTextField implements DataProvider, Disposable {
    public MySearchTextField() {
      super(false, "SearchEveryWhereHistory");
      JTextField editor = getTextEditor();
      editor.setOpaque(false);
      if (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF()) {
        editor.setUI((MacIntelliJTextFieldUI)MacIntelliJTextFieldUI.createUI(editor));
        editor.setBorder(new MacIntelliJTextBorder());
      } else {
        editor.setUI((DarculaTextFieldUI)DarculaTextFieldUI.createUI(editor));
        editor.setBorder(new DarculaTextBorder());
      }

      editor.putClientProperty("JTextField.Search.noBorderRing", Boolean.TRUE);
      if (UIUtil.isUnderDarcula()) {
        editor.setBackground(Gray._45);
        editor.setForeground(Gray._240);
      }
    }

    @Override
    protected boolean isSearchControlUISupported() {
      return true;
    }

    @Override
    protected boolean hasIconsOutsideOfTextField() {
      return false;
    }

    @Override
    protected void showPopup() {
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
        return getTextEditor().getText();
      }
      return null;
    }

    @Override
    public void dispose() {
    }
  }

  private class MyListRenderer extends ColoredListCellRenderer {
    ColoredListCellRenderer myLocation = new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        setPaintFocusBorder(false);
        append(myLocationString, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(myLocationIcon);
      }
    };
    SearchEverywherePsiRenderer myFileRenderer = new SearchEverywherePsiRenderer(myList);
    @SuppressWarnings("unchecked")
    ListCellRenderer myActionsRenderer = new GotoActionModel.GotoActionListCellRenderer(Function.TO_STRING);

    private String myLocationString;
    private Icon myLocationIcon;
    private Project myProject;
    private MyAccessibleComponent myMainPanel = new MyAccessibleComponent(new BorderLayout());
    private JLabel myTitle = new JLabel();

    MyListRenderer(@NotNull JBList myList) {
      assert myList == SearchEverywhereAction.this.myList;
    }

    private class MyAccessibleComponent extends JPanel {
      private Accessible myAccessible;
      public MyAccessibleComponent(LayoutManager layout) {
        super(layout);
        setOpaque(false);
      }
      void setAccessible(Accessible comp) {
        myAccessible = comp;
      }
      @Override
      public AccessibleContext getAccessibleContext() {
        return accessibleContext = (myAccessible != null ? myAccessible.getAccessibleContext() : super.getAccessibleContext());
      }
    }

    @Override
    public void clear() {
      super.clear();
      myLocation.clear();
      myLocationString = null;
      myLocationIcon = null;
    }

    public void setLocationString(String locationString) {
      myLocationString = locationString;
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component cmp;
      myLocationString = null;
      String pattern = "*" + myPopupField.getText();
      Matcher matcher = NameUtil.buildMatcher(pattern, 0, true, true);
      if (isMoreItem(index)) {
        cmp = More.get(isSelected);
      } else {
        cmp = SearchEverywhereClassifier.EP_Manager.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }

      if (cmp == null) {
        cmp = tryFileRenderer(matcher, list, value, index, isSelected);
      }

      if (cmp == null) {
        if (value instanceof GotoActionModel.ActionWrapper) {
          cmp = myActionsRenderer.getListCellRendererComponent(list, new GotoActionModel.MatchedValue(((GotoActionModel.ActionWrapper)value), pattern), index, isSelected, isSelected);
          if (cmp instanceof JComponent) ((JComponent)cmp).setBorder(null);
        } else {
          cmp = super.getListCellRendererComponent(list, value, index, isSelected, isSelected);
          final JPanel p = new JPanel(new BorderLayout());
          p.setBackground(UIUtil.getListBackground(isSelected));
          p.add(cmp, BorderLayout.CENTER);
          cmp = p;
        }
      }
      if (myLocationString != null || value instanceof BooleanOptionDescription) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UIUtil.getListBackground(isSelected));
        panel.add(cmp, BorderLayout.CENTER);
        final Component rightComponent;
        if (value instanceof BooleanOptionDescription) {
          final OnOffButton button = new OnOffButton();
          button.setSelected(((BooleanOptionDescription)value).isOptionEnabled());
          rightComponent = button;
        }
        else {
          rightComponent = myLocation.getListCellRendererComponent(list, value, index, isSelected, isSelected);
        }
        panel.add(rightComponent, BorderLayout.EAST);
        cmp = panel;
      }

      Color bg = cmp.getBackground();
      if (bg == null) {
        cmp.setBackground(UIUtil.getListBackground(isSelected));
        bg = cmp.getBackground();
      }
      String title = getModel().titleIndex.getTitle(index);
      myMainPanel.removeAll();
      if (title != null) {
        myTitle.setText(title);
        myMainPanel.add(createTitle(" " + title), BorderLayout.NORTH);
      }
      JPanel wrapped = new JPanel(new BorderLayout());
      wrapped.setBackground(bg);
      wrapped.setBorder(RENDERER_BORDER);
      wrapped.add(cmp, BorderLayout.CENTER);
      myMainPanel.add(wrapped, BorderLayout.CENTER);
      if (cmp instanceof Accessible) {
        myMainPanel.setAccessible((Accessible)cmp);
      }
      final int width = myMainPanel.getPreferredSize().width;
      if (width > myPopupActualWidth) {
        myPopupActualWidth = width;
        //schedulePopupUpdate();
      }
      return myMainPanel;
    }

    @Nullable
    private Component tryFileRenderer(Matcher matcher, JList list, Object value, int index, boolean isSelected) {
      if (myProject != null && value instanceof VirtualFile) {
        PsiManager psiManager = PsiManager.getInstance(myProject);
        VirtualFile virtualFile = (VirtualFile)value;
        value = !virtualFile.isValid() ? virtualFile :
                virtualFile.isDirectory() ? psiManager.findDirectory(virtualFile) :
                psiManager.findFile(virtualFile);
      }

      if (value instanceof PsiElement) {
        MatcherHolder.associateMatcher(list, matcher);
        try {
          return myFileRenderer.getListCellRendererComponent(list, value, index, isSelected, isSelected);
        }
        finally {
          MatcherHolder.associateMatcher(list, null);
        }
      }
      return null;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, final Object value, int index, final boolean selected, boolean hasFocus) {
      setPaintFocusBorder(false);
      setIcon(EmptyIcon.ICON_16);
      ApplicationManager.getApplication().runReadAction(() -> {
        if (value instanceof PsiElement) {
          String name1 = myClassModel.getElementName(value);
          assert name1 != null;
          append(name1);
        }
        else if (value instanceof ChooseRunConfigurationPopup.ItemWrapper) {
          final ChooseRunConfigurationPopup.ItemWrapper wrapper = (ChooseRunConfigurationPopup.ItemWrapper)value;
          append(wrapper.getText());
          setIcon(wrapper.getIcon());
          RunnerAndConfigurationSettings settings = ObjectUtils.tryCast(wrapper.getValue(), RunnerAndConfigurationSettings.class);
          if (settings != null) {
            Executor executor = findExecutor(settings);
            if (executor != null) {
              setLocationString(executor.getId());
              myLocationIcon = executor.getToolWindowIcon();
            }
          }
        }
        else if (isVirtualFile(value)) {
          final VirtualFile file = (VirtualFile)value;
          if (file instanceof VirtualFilePathWrapper) {
            append(((VirtualFilePathWrapper)file).getPresentablePath());
          }
          else {
            append(file.getName());
          }
          setIcon(IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, myProject));
        }
        else if (isActionValue(value)) {
          final GotoActionModel.ActionWrapper actionWithParentGroup =
            value instanceof GotoActionModel.ActionWrapper ? (GotoActionModel.ActionWrapper)value : null;
          final AnAction anAction = actionWithParentGroup == null ? (AnAction)value : actionWithParentGroup.getAction();
          final Presentation templatePresentation = anAction.getTemplatePresentation();
          Icon icon = templatePresentation.getIcon();
          if (anAction instanceof ActivateToolWindowAction) {
            final String id = ((ActivateToolWindowAction)anAction).getToolWindowId();
            ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(id);
            if (toolWindow != null) {
              icon = toolWindow.getIcon();
            }
          }

          append(String.valueOf(templatePresentation.getText()));
          if (actionWithParentGroup != null) {
            final String groupName = actionWithParentGroup.getGroupName();
            if (!StringUtil.isEmpty(groupName)) {
              setLocationString(groupName);
            }
          }

          final String groupName = actionWithParentGroup == null ? null : actionWithParentGroup.getGroupName();
          if (!StringUtil.isEmpty(groupName)) {
            setLocationString(groupName);
          }
          if (icon != null && icon.getIconWidth() <= 16 && icon.getIconHeight() <= 16) {
            setIcon(IconUtil.toSize(icon, 16, 16));
          }
        }
        else if (isSetting(value)) {
          String text = getSettingText((OptionDescription)value);
          SimpleTextAttributes attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES;
          if (value instanceof Changeable && ((Changeable)value).hasChanged()) {
            if (selected) {
              attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
            }
            else {
              SimpleTextAttributes base = SimpleTextAttributes.LINK_BOLD_ATTRIBUTES;
              attrs = base.derive(SimpleTextAttributes.STYLE_BOLD, base.getFgColor(), null, null);
            }
          }
          append(text, attrs);
          final String id = ((OptionDescription)value).getConfigurableId();
          String location = myConfigurables.get(id);
          if (location == null) location = ((OptionDescription)value).getValue();
          if (location != null) {
            setLocationString(location);
          }
        }
        else if (value instanceof OptionsTopHitProvider) {
          append("#" + ((OptionsTopHitProvider)value).getId());
        }
        else {
          ItemPresentation presentation = null;
          if (value instanceof ItemPresentation) {
            presentation = (ItemPresentation)value;
          }
          else if (value instanceof NavigationItem) {
            presentation = ((NavigationItem)value).getPresentation();
          }
          if (presentation != null) {
            final String text = presentation.getPresentableText();
            append(text == null ? value.toString() : text);
            final String location = presentation.getLocationString();
            if (!StringUtil.isEmpty(location)) {
              setLocationString(location);
            }
            Icon icon = presentation.getIcon(false);
            if (icon != null) setIcon(icon);
          }
        }
      });
    }

    public void recalculateWidth() {
      ListModel model = myList.getModel();
      myTitle.setIcon(EmptyIcon.ICON_16);
      myTitle.setFont(getTitleFont());
      int index = 0;
      while (index < model.getSize()) {
        String title = getModel().titleIndex.getTitle(index);
        if (title != null) {
          myTitle.setText(title);
        }
        index++;
      }

      myTitle.setForeground(Gray._122);
      myTitle.setAlignmentY(BOTTOM_ALIGNMENT);
    }
  }

  private static String getSettingText(OptionDescription value) {
    String hit = value.getHit();
    if (hit == null) {
      hit = value.getOption();
    }
    hit = StringUtil.unescapeXml(hit);
    if (hit.length() > 60) {
      hit = hit.substring(0, 60) + "...";
    }
    hit = hit.replace("  ", " "); //avoid extra spaces from mnemonics and xml conversion
    String text = hit.trim();
    text = StringUtil.trimEnd(text, ":");
    return text;
  }

  private static boolean isActionValue(Object o) {
    return o instanceof GotoActionModel.ActionWrapper || o instanceof AnAction;
  }

  private static boolean isSetting(Object o) {
    return o instanceof OptionDescription;
  }

  private static boolean isRunConfiguration(Object o) {
    return o instanceof ChooseRunConfigurationPopup.ItemWrapper;
  }

  private static boolean isVirtualFile(Object o) {
    return o instanceof VirtualFile;
  }

  private static Font getTitleFont() {
    return UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
  }

  enum WidgetID {CLASSES, FILES, ACTIONS, SETTINGS, SYMBOLS, RUN_CONFIGURATIONS}

  @SuppressWarnings({"SSBasedInspection", "unchecked"})
  private class CalcThread implements Runnable {
    private final Project project;
    private final String pattern;
    private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();
    private final ActionCallback myDone = new ActionCallback();
    private final SearchListModel myListModel;
    private final ArrayList<VirtualFile> myAlreadyAddedFiles = new ArrayList<>();
    private final ArrayList<AnAction> myAlreadyAddedActions = new ArrayList<>();


    public CalcThread(Project project, String pattern, boolean reuseModel) {
      this.project = project;
      this.pattern = pattern;
      myListModel = reuseModel ? (SearchListModel)myList.getModel() : new SearchListModel();
    }

    @Override
    public void run() {
      try {
        check();

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          // this line must be called on EDT to avoid context switch at clear().append("text") Don't touch. Ask [kb]
          myList.getEmptyText().setText("Searching...");

          if (myList.getModel() instanceof SearchListModel) {
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

        if (pattern.trim().length() == 0) {
          buildModelFromRecentFiles();
          //updatePopup();
          return;
        }

        checkModelsUpToDate();              check();
        buildTopHit(pattern);               check();

        if (!pattern.startsWith("#")) {
          buildRecentFiles(pattern);
          check();
          runReadAction(() -> buildStructure(pattern), true);
          updatePopup();
          check();
          buildToolWindows(pattern);
          check();
          updatePopup();
          check();

          checkModelsUpToDate();
          runReadAction(() -> buildRunConfigurations(pattern), true);
          runReadAction(() -> buildClasses(pattern), true);
          runReadAction(() -> buildFiles(pattern), false);
          runReadAction(() -> buildSymbols(pattern), true);

          buildActionsAndSettings(pattern);

          updatePopup();

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
    }

    private synchronized void buildToolWindows(String pattern) {
      if (!Registry.is("search.everywhere.toolwindows")) {
        return;
      }
      final List<ActivateToolWindowAction> actions = new ArrayList<>();
      for (ActivateToolWindowAction action : ToolWindowsGroup.getToolWindowActions(project, false)) {
        String text = action.getTemplatePresentation().getText();
        if (text != null && StringUtil.startsWithIgnoreCase(text, pattern)) {
          actions.add(action);

          if (actions.size() == MAX_TOOL_WINDOWS) {
            break;
          }
        }
      }

      check();

      if (actions.isEmpty()) {
        return;
      }

      SwingUtilities.invokeLater(() -> {
        myListModel.titleIndex.toolWindows = myListModel.size();
        for (Object toolWindow : actions) {
          myListModel.addElement(toolWindow);
        }
      });
    }

    private SearchResult getActionsOrSettings(final String pattern, final int max, final boolean actions) {
      final SearchResult result = new SearchResult();
      if ((actions && !Registry.is("search.everywhere.actions")) || (!actions && !Registry.is("search.everywhere.settings"))) {
        return result;
      }
      final MinusculeMatcher matcher = NameUtil.buildMatcher("*" +pattern).build();
      if (myActionProvider == null) {
        myActionProvider = createActionProvider();
      }

      myActionProvider.filterElements(pattern, matched -> {
        check();
        Object object = matched.value;
        if (myListModel.contains(object)) return true;

        if (!actions && isSetting(object)) {
          if (matcher.matches(getSettingText((OptionDescription)object))) {
            result.add(object);
          }
        }
        else if (actions && !isToolWindowAction(object) && isActionValue(object)) {
          AnAction action = object instanceof AnAction ? ((AnAction)object) : ((GotoActionModel.ActionWrapper)object).getAction();
          Object lock = myCalcThread;
          if (lock != null) {
            synchronized (lock) {
              if (isEnabled(action)) {
                result.add(object);
              }
            }
          }
        }
        return result.size() <= max;
      });

      return result;
    }

    private synchronized void buildActionsAndSettings(String pattern) {
      final SearchResult actions = getActionsOrSettings(pattern, MAX_ACTIONS, true);
      final SearchResult settings = getActionsOrSettings(pattern, MAX_SETTINGS, false);

      check();

      SwingUtilities.invokeLater(() -> {
        if (isCanceled()) return;
        if (actions.size() > 0) {
          myListModel.titleIndex.actions = myListModel.size();
          for (Object action : actions) {
            myListModel.addElement(action);
          }
        }
        myListModel.moreIndex.actions = actions.size() >= MAX_ACTIONS ? myListModel.size() - 1 : -1;
        if (settings.size() > 0) {
          myListModel.titleIndex.settings = myListModel.size();
          for (Object setting : settings) {
            myListModel.addElement(setting);
          }
        }
        myListModel.moreIndex.settings = settings.size() >= MAX_SETTINGS ? myListModel.size() - 1 : -1;
      });
    }

    private synchronized void buildFiles(final String pattern) {
      final SearchResult files = getFiles(pattern, showAll.get(), MAX_FILES, myFileChooseByName);

      check();

      if (files.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.files = myListModel.size();
          for (Object file : files) {
            myListModel.addElement(file);
          }
          myListModel.moreIndex.files = files.needMore ? myListModel.size() - 1 : -1;
        });
      }
    }

    private synchronized void buildStructure(final String pattern) {
      if (!Registry.is("search.everywhere.structure") || myStructureModel == null) return;
      final List<StructureViewTreeElement> elements = new ArrayList<>();
      final MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern).build();
      fillStructure(myStructureModel.getRoot(), elements, matcher);
      if (elements.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.structure = myListModel.size();
          for (Object element : elements) {
            myListModel.addElement(element);
          }
          myListModel.moreIndex.files = -1;
        });
      }
    }

    private void fillStructure(StructureViewTreeElement element, List<StructureViewTreeElement> elements, Matcher matcher) {
      final TreeElement[] children = element.getChildren();
      check();
      for (TreeElement child : children) {
        check();
        if (child instanceof StructureViewTreeElement) {
          final String text = child.getPresentation().getPresentableText();
          if (text != null && matcher.matches(text)) {
            elements.add((StructureViewTreeElement)child);
          }
          fillStructure((StructureViewTreeElement)child, elements, matcher);
        }
      }
    }


    private synchronized void buildSymbols(final String pattern) {
      final SearchResult symbols = getSymbols(pattern, MAX_SYMBOLS, showAll.get(), mySymbolsChooseByName);
      check();

      if (symbols.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.symbols = myListModel.size();
          for (Object file : symbols) {
            myListModel.addElement(file);
          }
          myListModel.moreIndex.symbols = symbols.needMore ? myListModel.size() - 1 : -1;
        });
      }
    }

    @Nullable
    private ChooseRunConfigurationPopup.ItemWrapper getRunConfigurationByName(String name) {
      final ChooseRunConfigurationPopup.ItemWrapper[] wrappers =
        ChooseRunConfigurationPopup.createSettingsList(project, new ExecutorProvider() {
          @Override
          public Executor getExecutor() {
            return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
          }
        }, false);

      for (ChooseRunConfigurationPopup.ItemWrapper wrapper : wrappers) {
        if (wrapper.getText().equals(name)) {
          return wrapper;
        }
      }
      return null;
    }

    private synchronized void buildRunConfigurations(String pattern) {
      final SearchResult runConfigurations = getConfigurations(pattern, MAX_RUN_CONFIGURATION);

      if (runConfigurations.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.runConfigurations = myListModel.size();
          for (Object runConfiguration : runConfigurations) {
            myListModel.addElement(runConfiguration);
          }
          myListModel.moreIndex.runConfigurations = runConfigurations.needMore ? myListModel.getSize() - 1 : -1;
        });
      }
    }

    private SearchResult getConfigurations(String pattern, int max) {
      SearchResult configurations = new SearchResult();
      if (!Registry.is("search.everywhere.configurations")) {
        return configurations;
      }
      final MinusculeMatcher matcher = NameUtil.buildMatcher(pattern).build();
      final ChooseRunConfigurationPopup.ItemWrapper[] wrappers =
        ChooseRunConfigurationPopup.createSettingsList(project, new ExecutorProvider() {
          @Override
          public Executor getExecutor() {
            return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
          }
        }, false);
      check();
      for (ChooseRunConfigurationPopup.ItemWrapper wrapper : wrappers) {
        if (matcher.matches(wrapper.getText()) && !myListModel.contains(wrapper)) {
          if (configurations.size() == max) {
            configurations.needMore = true;
            break;
          }
          configurations.add(wrapper);
        }
        check();
      }

      return configurations;
    }


    private synchronized void buildClasses(final String pattern) {
      final SearchResult classes = getClasses(pattern, showAll.get(), MAX_CLASSES, myClassChooseByName);
      check();

      if (classes.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.classes = myListModel.size();
          for (Object file : classes) {
            myListModel.addElement(file);
          }
          myListModel.moreIndex.classes = -1;
          if (classes.needMore) {
            myListModel.moreIndex.classes = myListModel.size() - 1;
          }
        });
      }
    }

    private SearchResult getSymbols(String pattern, final int max, final boolean includeLibs, ChooseByNamePopup chooseByNamePopup) {
      final SearchResult symbols = new SearchResult();
      if (!Registry.is("search.everywhere.symbols") || shouldSkipPattern(pattern)) {
        return symbols;
      }
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
      if (chooseByNamePopup == null) return symbols;
      final ChooseByNameItemProvider provider = chooseByNamePopup.getProvider();
      provider.filterElements(chooseByNamePopup, pattern, includeLibs,
                              myProgressIndicator, o -> {
                                if (SearchEverywhereClassifier.EP_Manager.isSymbol(o) && !myListModel.contains(o) && !symbols.contains(o)) {
                                  PsiElement element = null;
                                  if (o instanceof PsiElement) {
                                    element = (PsiElement)o;
                                  }
                                  else if (o instanceof PsiElementNavigationItem) {
                                    element = ((PsiElementNavigationItem)o).getTargetElement();
                                  }
                                  VirtualFile virtualFile = SearchEverywhereClassifier.EP_Manager.getVirtualFile(o);
                                  //some elements are non-physical like DB columns
                                  boolean isElementWithoutFile = element != null && element.getContainingFile() == null;
                                  boolean isFileInScope = virtualFile != null && (includeLibs || scope.accept(virtualFile));
                                  boolean isSpecialElement = element == null && virtualFile == null; //all Rider elements don't have any psi elements within
                                  if (isElementWithoutFile || isFileInScope || isSpecialElement) {
                                    symbols.add(o);
                                  }
                                }
                                symbols.needMore = symbols.size() == max;
                                return !symbols.needMore;
                              });

      if (!includeLibs && symbols.isEmpty()) {
        return getSymbols(pattern, max, true, chooseByNamePopup);
      }

      return symbols;
    }

    private SearchResult getClasses(String pattern, boolean includeLibs, final int max, ChooseByNamePopup chooseByNamePopup) {
      final SearchResult classes = new SearchResult();
      if (chooseByNamePopup == null || shouldSkipPattern(pattern)) {
        return classes;
      }
      chooseByNamePopup.getProvider().filterElements(chooseByNamePopup, pattern, includeLibs,
                                                     myProgressIndicator, o -> {
                                                       if (SearchEverywhereClassifier.EP_Manager.isClass(o) && !myListModel.contains(o) && !classes.contains(o)) {
                                                         if (classes.size() == max) {
                                                           classes.needMore = true;
                                                           return false;
                                                         }

                                                         PsiElement element = null;
                                                         if (o instanceof PsiElement) {
                                                           element = (PsiElement)o;
                                                         }
                                                         else if (o instanceof PsiElementNavigationItem) {
                                                           element = ((PsiElementNavigationItem)o).getTargetElement();
                                                         }
                                                         classes.add(o);

                                                         if (element instanceof PsiNamedElement) {
                                                           final String name = ((PsiNamedElement)element).getName();
                                                           VirtualFile virtualFile = SearchEverywhereClassifier.EP_Manager.getVirtualFile(o);
                                                           if (virtualFile != null) {
                                                             if (StringUtil.equals(name, virtualFile.getNameWithoutExtension())) {
                                                               myAlreadyAddedFiles.add(virtualFile);
                                                             }
                                                           }
                                                         }
                                                       }
                                                       return true;
                                                     });
      if (!includeLibs && classes.isEmpty()) {
        return getClasses(pattern, true, max, chooseByNamePopup);
      }
      return classes;
    }

    private SearchResult getFiles(final String pattern, final boolean includeLibs, final int max, ChooseByNamePopup chooseByNamePopup) {
      final SearchResult files = new SearchResult();
      if (chooseByNamePopup == null || !Registry.is("search.everywhere.files")) {
        return files;
      }
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
      chooseByNamePopup.getProvider().filterElements(chooseByNamePopup, pattern, true,
                                                     myProgressIndicator, o -> {
                                                       VirtualFile file = null;
                                                       if (o instanceof VirtualFile) {
                                                         file = (VirtualFile)o;
                                                       } else if (o instanceof PsiFile) {
                                                         file = ((PsiFile)o).getVirtualFile();
                                                       } else if (o instanceof PsiDirectory) {
                                                         file = ((PsiDirectory)o).getVirtualFile();
                                                       }
                                                       if (file != null
                                                           && !(pattern.indexOf(' ') != -1 && file.getName().indexOf(' ') == -1)
                                                           && (includeLibs || scope.accept(file)
                                                           && !myListModel.contains(file)
                                                           && !myAlreadyAddedFiles.contains(file))
                                                           && !files.contains(file)) {
                                                         if (files.size() == max) {
                                                           files.needMore = true;
                                                           return false;
                                                         }
                                                         files.add(file);
                                                       }
                                                       return true;
                                                     });
      if (!includeLibs && files.isEmpty()) {
        return getFiles(pattern, true, max, chooseByNamePopup);
      }

      return files;
    }

    private synchronized void buildRecentFiles(String pattern) {
      final MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern).build();
      final ArrayList<VirtualFile> files = new ArrayList<>();
      final List<VirtualFile> selected = Arrays.asList(FileEditorManager.getInstance(project).getSelectedFiles());
      for (VirtualFile file : ArrayUtil.reverseArray(EditorHistoryManager.getInstance(project).getFiles())) {
        if (StringUtil.isEmptyOrSpaces(pattern) || matcher.matches(file.getName())) {
          if (!files.contains(file) && !selected.contains(file)) {
            files.add(file);
          }
        }
        if (files.size() > MAX_RECENT_FILES) break;
      }

      if (files.size() > 0) {
        myAlreadyAddedFiles.addAll(files);

        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;
          myListModel.titleIndex.recentFiles = myListModel.size();
          for (Object file : files) {
            myListModel.addElement(file);
          }
          updatePopup();
        });
      }
    }

    private boolean isCanceled() {
      return myProgressIndicator.isCanceled() || myDone.isRejected();
    }

    private synchronized void buildTopHit(String pattern) {
      final List<Object> elements = new ArrayList<>();
      final HistoryItem history = myHistoryItem;
      if (history != null) {
        final HistoryType type = parseHistoryType(history.type);
        if (type != null) {
          switch (type){
            case PSI:
              if (!DumbService.isDumb(project)) {
                ApplicationManager.getApplication().runReadAction(() -> {

                  final int i = history.fqn.indexOf("://");
                  if (i != -1) {
                    final String langId = history.fqn.substring(0, i);
                    final Language language = Language.findLanguageByID(langId);
                    final String psiFqn = history.fqn.substring(i + 3);
                    if (language != null) {
                      final PsiElement psi =
                        LanguagePsiElementExternalizer.INSTANCE.forLanguage(language).findByQualifiedName(project, psiFqn);
                      if (psi != null) {
                        elements.add(psi);
                        final PsiFile psiFile = psi.getContainingFile();
                        if (psiFile != null) {
                          final VirtualFile file = psiFile.getVirtualFile();
                          if (file != null) {
                            myAlreadyAddedFiles.add(file);
                          }
                        }
                      }
                    }
                  }
                });
              }
              break;
            case FILE:
              final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(history.fqn);
              if (file != null) {
                elements.add(file);
              }
              break;
            case SETTING:
              break;
            case ACTION:
              final AnAction action = ActionManager.getInstance().getAction(history.fqn);
              if (action != null) {
                elements.add(action);
                myAlreadyAddedActions.add(action);
              }
              break;
            case RUN_CONFIGURATION:
              if (!DumbService.isDumb(project)) {
                ApplicationManager.getApplication().runReadAction(() -> {
                  final ChooseRunConfigurationPopup.ItemWrapper runConfiguration = getRunConfigurationByName(history.fqn);
                  if (runConfiguration != null) {
                    elements.add(runConfiguration);
                  }
                });
              }
              break;
          }
        }
      }
      final Consumer<Object> consumer = o -> {
        if (isSetting(o) || isVirtualFile(o) || isActionValue(o) || o instanceof PsiElement || o instanceof OptionsTopHitProvider) {
          if (o instanceof AnAction && myAlreadyAddedActions.contains(o)) {
            return;
          }
          elements.add(o);
        }
      };

      if (pattern.startsWith("#") && !pattern.contains(" ")) {
        String id = pattern.substring(1);
        final HashSet<String> ids = new HashSet<>();
        for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
          check();
          if (provider instanceof OptionsTopHitProvider) {
            final String providerId = ((OptionsTopHitProvider)provider).getId();
            if (!ids.contains(providerId) && StringUtil.startsWithIgnoreCase(providerId, id)) {
              consumer.consume(provider);
              ids.add(providerId);
            }
          }
        }
      } else {
        final ActionManager actionManager = ActionManager.getInstance();
        final List<String> actions = AbbreviationManager.getInstance().findActions(pattern);
        for (String actionId : actions) {
          consumer.consume(actionManager.getAction(actionId));
        }

        for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
          check();
          if (provider instanceof OptionsTopHitProvider && !((OptionsTopHitProvider)provider).isEnabled(project)) {
            continue;
          }
          provider.consumeTopHits(pattern, consumer, project);
        }
      }
      if (elements.size() > 0) {
        SwingUtilities.invokeLater(() -> {
          if (isCanceled()) return;

          for (Object element : new ArrayList(elements)) {
            if (element instanceof AnAction) {
              if (!isEnabled((AnAction)element)) {
                elements.remove(element);
              }
              if (isCanceled()) return;
            }
          }
          if (isCanceled() || elements.isEmpty()) return;
          myListModel.titleIndex.topHit = myListModel.size();
          for (Object element : ContainerUtil.getFirstItems(elements, MAX_TOP_HIT)) {
            myListModel.addElement(element);
          }
        });
      }
    }

    protected boolean isEnabled(final AnAction action) {
      if (myDisabledActions.contains(action)) return false;
      final AnActionEvent e = new AnActionEvent(myActionEvent.getInputEvent(),
                                                myActionEvent.getDataContext(),
                                                myActionEvent.getPlace(),
                                                action.getTemplatePresentation().clone(),
                                                myActionEvent.getActionManager(),
                                                myActionEvent.getModifiers());

      ApplicationManager.getApplication().invokeAndWait(() -> ActionUtil.performDumbAwareUpdate(action, e, false), ModalityState.NON_MODAL);
      final Presentation presentation = e.getPresentation();
      final boolean enabled = presentation.isEnabled() && presentation.isVisible() && !StringUtil.isEmpty(presentation.getText());
      if (!enabled) {
        myDisabledActions.add(action);
      }
      return enabled;
    }

    private synchronized void checkModelsUpToDate() {
      if (myClassModel == null) {
        myClassModel = new GotoClassModel2(project);
        myFileModel = new GotoFileModel(project);
        mySymbolsModel = new GotoSymbolModel2(project);
        myFileChooseByName = ChooseByNamePopup.createPopup(project, myFileModel, (PsiElement)null);
        myClassChooseByName = ChooseByNamePopup.createPopup(project, myClassModel, (PsiElement)null);
        mySymbolsChooseByName = ChooseByNamePopup.createPopup(project, mySymbolsModel, (PsiElement)null);
        project.putUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, null);
        myActionProvider = createActionProvider();
        myConfigurables.clear();
        fillConfigurablesIds(null, ShowSettingsUtilImpl.getConfigurables(project, true));
      }
      if (myStructureModel == null && myFileEditor != null && Registry.is("search.everywhere.structure")) {
        runReadAction(() -> {
          StructureViewBuilder structureViewBuilder = myFileEditor.getStructureViewBuilder();
          if (structureViewBuilder == null) return;
          StructureView structureView = structureViewBuilder.createStructureView(myFileEditor, project);
          myStructureModel = structureView.getTreeModel();
        }, true);
      }
    }

    private void buildModelFromRecentFiles() {
      buildRecentFiles("");
    }

    private GotoActionItemProvider createActionProvider() {
      GotoActionModel model = new GotoActionModel(project, myFocusComponent, myEditor, myFile) {
        @Override
        protected MatchMode actionMatches(@NotNull String pattern, MinusculeMatcher matcher, @NotNull AnAction anAction) {
          MatchMode mode = super.actionMatches(pattern, matcher, anAction);
          return mode == MatchMode.NAME ? mode : MatchMode.NONE;
        }
      };
      return new GotoActionItemProvider(model);
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

          myRenderer.recalculateWidth();
          if (myBalloon == null || myBalloon.isDisposed()) {
            return;
          }
          if (myPopup == null || !myPopup.isVisible()) {
            ScrollingUtil.installActions(myList, getField().getTextEditor());
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

                if (size.width < myBalloon.getSize().width) {
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
                final boolean canClose = balloon == null || balloon.isDisposed() || (!getField().getTextEditor().hasFocus() && !mySkipFocusGain);
                if (canClose) {
                  PropertiesComponent.getInstance().setValue("search.everywhere.max.popup.width", Math.max(content.getWidth(), JBUI.scale(600)), JBUI.scale(600));
                }
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
                  resetFields();
                  myNonProjectCheckBox.setSelected(false);
                  //noinspection SSBasedInspection
                  SwingUtilities.invokeLater(() -> ActionToolbarImpl.updateAllToolbarsImmediately());
                  if (myActionEvent != null && myActionEvent.getInputEvent() instanceof MouseEvent) {
                    final Component component = myActionEvent.getInputEvent().getComponent();
                    if (component != null) {
                      final JLabel label = UIUtil.getParentOfType(JLabel.class, component);
                      if (label != null) {
                        SwingUtilities.invokeLater(() -> label.setIcon(AllIcons.Actions.FindPlain));
                      }
                    }
                  }
                  myActionEvent = null;
                });
              }
            });
            updatePopupBounds();
            myPopup.show(new RelativePoint(getField().getParent(), new Point(0, getField().getParent().getHeight())));

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
            updatePopupBounds();
          }
        }
      });
    }

    public ActionCallback cancel() {
      myProgressIndicator.cancel();
      myDone.setRejected();
      return myDone;
    }

    public ActionCallback insert(final int index, final WidgetID id) {
       ApplicationManager.getApplication().executeOnPooledThread(() -> runReadAction(() -> {
         try {
           final SearchResult result
             = id == WidgetID.CLASSES ? getClasses(pattern, showAll.get(), DEFAULT_MORE_STEP_COUNT, myClassChooseByName)
             : id == WidgetID.FILES ? getFiles(pattern, showAll.get(), DEFAULT_MORE_STEP_COUNT, myFileChooseByName)
             : id == WidgetID.RUN_CONFIGURATIONS ? getConfigurations(pattern, DEFAULT_MORE_STEP_COUNT)
             : id == WidgetID.SYMBOLS ? getSymbols(pattern, DEFAULT_MORE_STEP_COUNT, showAll.get(), mySymbolsChooseByName)
             : id == WidgetID.ACTIONS ? getActionsOrSettings(pattern, DEFAULT_MORE_STEP_COUNT, true)
             : id == WidgetID.SETTINGS ? getActionsOrSettings(pattern, DEFAULT_MORE_STEP_COUNT, false)
             : new SearchResult();

           check();
           SwingUtilities.invokeLater(() -> {
             try {
               int shift = 0;
               int i = index+1;
               for (Object o : result) {
                 //noinspection unchecked
                 myListModel.insertElementAt(o, i);
                 shift++;
                 i++;
               }
               MoreIndex moreIndex = myListModel.moreIndex;
               myListModel.titleIndex.shift(index, shift);
               moreIndex.shift(index, shift);

               if (!result.needMore) {
                 switch (id) {
                   case CLASSES: moreIndex.classes = -1; break;
                   case FILES: moreIndex.files = -1; break;
                   case ACTIONS: moreIndex.actions = -1; break;
                   case SETTINGS: moreIndex.settings = -1; break;
                   case SYMBOLS: moreIndex.symbols = -1; break;
                   case RUN_CONFIGURATIONS: moreIndex.runConfigurations = -1; break;
                 }
               }
               ScrollingUtil.selectItem(myList, index);
               myDone.setDone();
             }
             catch (Exception e) {
               myDone.setRejected();
             }
           });
         }
         catch (Exception e) {
           myDone.setRejected();
         }
       }, true));
      return myDone;
    }

    public ActionCallback start() {
      ApplicationManager.getApplication().executeOnPooledThread(this);
      return myDone;
    }
  }

  private static boolean shouldSkipPattern(String pattern) {
    return Registry.is("search.everywhere.pattern.checking") && StringUtil.split(pattern, ".").size() == 2;
  }

  protected void resetFields() {
    if (myBalloon != null) {
      final JBPopup balloonToBeCanceled = myBalloon;
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> balloonToBeCanceled.cancel());
      myBalloon = null;
    }
    myCurrentWorker.doWhenProcessed(() -> {
      myFileModel = null;
      if (myFileChooseByName != null) {
        myFileChooseByName.close(false);
        myFileChooseByName = null;
      }
      if (myClassChooseByName != null) {
        myClassChooseByName.close(false);
        myClassChooseByName = null;
      }
      if (mySymbolsChooseByName != null) {
        mySymbolsChooseByName.close(false);
        mySymbolsChooseByName = null;
      }
      final Object lock = myCalcThread;
      if (lock != null) {
        synchronized (lock) {
          myClassModel = null;
          myActionProvider = null;
          mySymbolsModel = null;
          myConfigurables.clear();
          myFocusComponent = null;
          myContextComponent = null;
          myFocusOwner = null;
          myRenderer.myProject = null;
          myPopup = null;
          myHistoryIndex = 0;
          myPopupActualWidth = 0;
          myCurrentWorker = ActionCallback.DONE;
          showAll.set(false);
          myCalcThread = null;
          myEditor = null;
          myFileEditor = null;
          myStructureModel = null;
          myDisabledActions.clear();
        }
      }
    });
    mySkipFocusGain = false;
  }

  private void updatePopupBounds() {
    if (myPopup == null || !myPopup.isVisible()) {
      return;
    }
    final Container parent = getField().getParent();
    final Dimension size = myList.getParent().getParent().getPreferredSize();
    size.width = myPopupActualWidth - 2;
    if (size.width + 2 < parent.getWidth()) {
      size.width = parent.getWidth();
    }
    if (myList.getItemsCount() == 0) {
      size.height = JBUI.scale(30);
    }
    Dimension sz = new Dimension(size.width, myList.getPreferredSize().height);
    if (!SystemInfo.isMac) {
      if ((sz.width > getPopupMaxWidth() || sz.height > getPopupMaxWidth())) {
        final JBScrollPane pane = new JBScrollPane();
        final int extraWidth = pane.getVerticalScrollBar().getWidth() + 1;
        final int extraHeight = pane.getHorizontalScrollBar().getHeight() + 1;
        sz = new Dimension(Math.min(getPopupMaxWidth(), Math.max(getField().getWidth(), sz.width + extraWidth)), Math.min(getPopupMaxWidth(), sz.height + extraHeight));
        sz.width += 20;
      } else {
        sz.width += 2;
      }
    }
    sz.height += 2;
    sz.width = Math.max(sz.width, myPopup.getSize().width);
    myPopup.setSize(sz);
    if (myActionEvent != null && myActionEvent.getInputEvent() == null) {
      final Point p = parent.getLocationOnScreen();
      p.y += parent.getHeight();
      if (parent.getWidth() < sz.width) {
        p.x -= sz.width - parent.getWidth();
      }
      myPopup.setLocation(p);
    } else {
      try {
        adjustPopup();
      }
      catch (Exception ignore) {}
    }
  }

  private static int getPopupMaxWidth() {
    return PropertiesComponent.getInstance().getInt("search.everywhere.max.popup.width", JBUI.scale(600));
  }

  private void adjustPopup() {
//    new PopupPositionManager.PositionAdjuster(getField().getParent(), 0).adjust(myPopup, PopupPositionManager.Position.BOTTOM);
    final Dimension d = PopupPositionManager.PositionAdjuster.getPopupSize(myPopup);
    final JComponent myRelativeTo = myBalloon.getContent();
    Point myRelativeOnScreen = myRelativeTo.getLocationOnScreen();
    Rectangle screen = ScreenUtil.getScreenRectangle(myRelativeOnScreen);
    Rectangle popupRect = null;
    Rectangle r = new Rectangle(myRelativeOnScreen.x, myRelativeOnScreen.y + myRelativeTo.getHeight(), d.width, d.height);

      if (screen.contains(r)) {
        popupRect = r;
      }

    if (popupRect != null) {
      Point location = new Point(r.x, r.y);
      if (!location.equals(myPopup.getLocationOnScreen())) {
        myPopup.setLocation(location);
      }
    }
    else {
      if (r.y + d.height > screen.y + screen.height) {
        r.height =  screen.y + screen.height - r.y - 2;
      }
      if (r.width > screen.width) {
        r.width = screen.width - 50;
      }
      if (r.x + r.width > screen.x + screen.width) {
        r.x = screen.x + screen.width - r.width - 2;
      }

      myPopup.setSize(r.getSize());
      myPopup.setLocation(r.getLocation());
    }

  }

  private static boolean isToolWindowAction(Object o) {
    return isActionValue(o)
           && o instanceof GotoActionModel.ActionWrapper
           && ((GotoActionModel.ActionWrapper)o).getAction() instanceof ActivateToolWindowAction;
  }

  private void fillConfigurablesIds(String pathToParent, Configurable[] configurables) {
    for (Configurable configurable : configurables) {
      if (configurable instanceof SearchableConfigurable) {
        final String id = ((SearchableConfigurable)configurable).getId();
        String name = configurable.getDisplayName();
        if (pathToParent != null) {
          name = pathToParent + " -> " + name;
        }
        myConfigurables.put(id, name);
        if (configurable instanceof SearchableConfigurable.Parent) {
          fillConfigurablesIds(name, ((SearchableConfigurable.Parent)configurable).getConfigurables());
        }
      }
    }
  }

  static class MoreIndex {
    volatile int classes = -1;
    volatile int files = -1;
    volatile int actions = -1;
    volatile int settings = -1;
    volatile int symbols = -1;
    volatile int runConfigurations = -1;
    volatile int structure = -1;

    public void shift(int index, int shift) {
      if (runConfigurations >= index) runConfigurations += shift;
      if (classes >= index) classes += shift;
      if (files >= index) files += shift;
      if (symbols >= index) symbols += shift;
      if (actions >= index) actions += shift;
      if (settings >= index) settings += shift;
      if (structure >= index) structure += shift;
    }
  }

  static class TitleIndex {
    volatile int topHit = -1;
    volatile int recentFiles = -1;
    volatile int runConfigurations = -1;
    volatile int classes = -1;
    volatile int structure = -1;
    volatile int files = -1;
    volatile int actions = -1;
    volatile int settings = -1;
    volatile int toolWindows = -1;
    volatile int symbols = -1;

    final String gotoClassTitle;
    final String gotoFileTitle;
    final String gotoActionTitle;
    final String gotoSettingsTitle;
    final String gotoRecentFilesTitle;
    final String gotoRunConfigurationsTitle;
    final String gotoSymbolTitle;
    final String gotoStructureTitle;
    static final String toolWindowsTitle = "Tool Windows";

    TitleIndex() {
      String gotoClass = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoClass"));
      gotoClassTitle = StringUtil.isEmpty(gotoClass) ? "Classes" : "Classes (" + gotoClass + ")";
      String gotoFile = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoFile"));
      gotoFileTitle = StringUtil.isEmpty(gotoFile) ? "Files" : "Files (" + gotoFile + ")";
      String gotoAction = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoAction"));
      gotoActionTitle = StringUtil.isEmpty(gotoAction) ? "Actions" : "Actions (" + gotoAction + ")";
      String gotoSettings = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ShowSettings"));
      gotoSettingsTitle = StringUtil.isEmpty(gotoAction) ? ShowSettingsUtil.getSettingsMenuName() : ShowSettingsUtil.getSettingsMenuName() + " (" + gotoSettings + ")";
      String gotoRecentFiles = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("RecentFiles"));
      gotoRecentFilesTitle = StringUtil.isEmpty(gotoRecentFiles) ? "Recent Files" : "Recent Files (" + gotoRecentFiles + ")";
      String gotoSymbol = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoSymbol"));
      gotoSymbolTitle = StringUtil.isEmpty(gotoSymbol) ? "Symbols" : "Symbols (" + gotoSymbol + ")";
      String gotoRunConfiguration = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ChooseDebugConfiguration"));
      if (StringUtil.isEmpty(gotoRunConfiguration)) {
        gotoRunConfiguration = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ChooseRunConfiguration"));
      }
      gotoRunConfigurationsTitle = StringUtil.isEmpty(gotoRunConfiguration) ? "Run Configurations" : "Run Configurations (" + gotoRunConfiguration + ")";
      String gotoStructure = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("FileStructurePopup"));
      gotoStructureTitle = StringUtil.isEmpty(gotoStructure) ? "File Structure" : "File Structure (" + gotoStructure + ")";
    }

    String getTitle(int index) {
      if (index == topHit) return index == 0 ? "Top Hit" : "Top Hits";
      if (index == recentFiles) return gotoRecentFilesTitle;
      if (index == structure) return gotoStructureTitle;
      if (index == runConfigurations) return gotoRunConfigurationsTitle;
      if (index == classes) return gotoClassTitle;
      if (index == files) return gotoFileTitle;
      if (index == toolWindows) return toolWindowsTitle;
      if (index == actions) return gotoActionTitle;
      if (index == settings) return gotoSettingsTitle;
      if (index == symbols) return gotoSymbolTitle;
      return null;
    }

    public void clear() {
      topHit = -1;
      runConfigurations = -1;
      recentFiles = -1;
      classes = -1;
      files = -1;
      structure = -1;
      actions = -1;
      settings = -1;
      toolWindows = -1;
    }

    public void shift(int index, int shift) {
      if (toolWindows != - 1 && toolWindows > index) toolWindows += shift;
      if (settings != - 1 && settings > index) settings += shift;
      if (actions != - 1 && actions > index) actions += shift;
      if (files != - 1 && files > index) files += shift;
      if (structure != - 1 && structure > index) structure += shift;
      if (classes != - 1 && classes > index) classes += shift;
      if (runConfigurations != - 1 && runConfigurations > index) runConfigurations += shift;
      if (symbols != - 1 && symbols > index) symbols += shift;
    }
  }

  static class SearchResult extends ArrayList<Object> {
    boolean needMore;
  }

  @SuppressWarnings("unchecked")
  private static class SearchListModel extends DefaultListModel {
    @SuppressWarnings("UseOfObsoleteCollectionType")
    Vector myDelegate;

    volatile TitleIndex titleIndex = new TitleIndex();
    volatile MoreIndex moreIndex = new MoreIndex();

    private SearchListModel() {
      super();
      myDelegate = ReflectionUtil.getField(DefaultListModel.class, this, Vector.class, "delegate");
    }

    int next(int index) {
      int[] all = getAll();
      Arrays.sort(all);
      for (int next : all) {
        if (next > index) return next;
      }
      return 0;
    }

    int[] getAll() {
      return new int[]{
        titleIndex.topHit,
        titleIndex.recentFiles,
        titleIndex.structure,
        titleIndex.runConfigurations,
        titleIndex.classes,
        titleIndex.files,
        titleIndex.actions,
        titleIndex.settings,
        titleIndex.toolWindows,
        titleIndex.symbols,
        moreIndex.classes,
        moreIndex.actions,
        moreIndex.files,
        moreIndex.settings,
        moreIndex.symbols,
        moreIndex.runConfigurations,
        moreIndex.structure
      };
    }

    int prev(int index) {
      int[] all = getAll();
      Arrays.sort(all);
      for (int i = all.length-1; i >= 0; i--) {
        if (all[i] != -1 && all[i] < index) return all[i];
      }
      return all[all.length - 1];
    }

    @Override
    public void addElement(Object obj) {
      myDelegate.add(obj);
    }

    public void update() {
      fireContentsChanged(this, 0, getSize() - 1);
    }
  }

  static class More extends JPanel {
    static final More instance = new More();
    final JLabel label = new JLabel("    ... more   ");

    private More() {
      super(new BorderLayout());
      add(label, BorderLayout.CENTER);
    }

    static More get(boolean isSelected) {
      instance.setBackground(UIUtil.getListBackground(isSelected));
      instance.label.setForeground(UIUtil.getLabelDisabledForeground());
      instance.label.setFont(getTitleFont());
      instance.label.setBackground(UIUtil.getListBackground(isSelected));
      return instance;
    }
  }

  private static JComponent createTitle(String titleText) {
    JLabel titleLabel = new JLabel(titleText);
    titleLabel.setFont(getTitleFont());
    titleLabel.setForeground(UIUtil.getLabelDisabledForeground());
    SeparatorComponent separatorComponent =
      new SeparatorComponent(titleLabel.getPreferredSize().height / 2, new JBColor(Gray._220, Gray._80), null);

    return JBUI.Panels.simplePanel(5, 10)
      .addToCenter(separatorComponent)
      .addToLeft(titleLabel)
      .withBorder(RENDERER_TITLE_BORDER)
      .withBackground(UIUtil.getListBackground());
  }

  private enum HistoryType {PSI, FILE, SETTING, ACTION, RUN_CONFIGURATION}

  @Nullable
  private static HistoryType parseHistoryType(@Nullable String name) {
    try {
      return HistoryType.valueOf(name);
    } catch (Exception e) {
      return null;
    }
  }

  private static class HistoryItem {
    final String pattern, type, fqn;

    private HistoryItem(String pattern, String type, String fqn) {
      this.pattern = pattern;
      this.type = type;
      this.fqn = fqn;
    }

    public String toString() {
      return pattern + "\t" + type + "\t" + fqn;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HistoryItem item = (HistoryItem)o;

      if (!pattern.equals(item.pattern)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return pattern.hashCode();
    }
  }
}

