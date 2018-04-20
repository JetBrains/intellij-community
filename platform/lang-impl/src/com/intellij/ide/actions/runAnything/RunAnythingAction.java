// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.items.RunAnythingActionItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingCommandItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder;
import com.intellij.ide.ui.laf.intellij.WinIntelliJTextBorder;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ToolbarClicksCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.PoppedIcon;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.TextUI;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.ide.actions.runAnything.RunAnythingIconHandler.*;
import static com.intellij.ide.actions.runAnything.RunAnythingRunConfigurationItem.RUN_ANYTHING_RUN_CONFIGURATION_AD_TEXT;
import static com.intellij.ide.actions.runAnything.items.RunAnythingCommandItem.UNDEFINED_COMMAND_ICON;
import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

@SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
public class RunAnythingAction extends AnAction implements CustomComponentAction, DumbAware, DataProvider {
  public static final String RUN_ANYTHING_HISTORY_KEY = "RunAnythingHistoryKey";
  public static final int SEARCH_FIELD_COLUMNS = 25;
  public static final String UNKNOWN_CONFIGURATION = "UNKNOWN_CONFIGURATION";
  public static final AtomicBoolean SHIFT_IS_PRESSED = new AtomicBoolean(false);
  public static final AtomicBoolean ALT_IS_PRESSED = new AtomicBoolean(false);
  public static final Key<JBPopup> RUN_ANYTHING_POPUP = new Key<>("RunAnythingPopup");
  public static final String RUN_ANYTHING_ACTION_ID = "RunAnything";
  public static final DataKey<AnActionEvent> RUN_ANYTHING_EVENT_KEY = DataKey.create("RUN_ANYTHING_EVENT_KEY");
  public static final DataKey<Component> FOCUS_COMPONENT_KEY_NAME = DataKey.create("FOCUS_COMPONENT_KEY_NAME");
  public static final DataKey<Executor> EXECUTOR_KEY = DataKey.create("EXECUTOR_KEY");
  static final String SHIFT_SHORTCUT_TEXT = KeymapUtil.getShortcutText(KeyboardShortcut.fromString(("SHIFT")));
  public static final String AD_ACTION_TEXT
    = String.format(IdeBundle.message("run.anything.ad.run.action.with.default.settings"), SHIFT_SHORTCUT_TEXT);
  public static final String AD_DEBUG_TEXT
    = String.format(IdeBundle.message("run.anything.ad.run.with.debug"), SHIFT_SHORTCUT_TEXT);
  public static final String AD_CONTEXT_TEXT = String
    .format(IdeBundle.message("run.anything.ad.run.in.context"), KeymapUtil.getShortcutText(KeyboardShortcut.fromString("pressed ALT")));
  public static final String AD_DELETE_COMMAND_TEXT = String.format(IdeBundle.message("run.anything.ad.command.delete"), KeymapUtil
    .getShortcutText(KeyboardShortcut.fromString("shift BACK_SPACE")));

  private static final int MAX_RUN_ANYTHING_HISTORY = 50;
  private static final Logger LOG = Logger.getInstance(RunAnythingAction.class);
  private static final Border RENDERER_BORDER = JBUI.Borders.empty(1, 0);
  private static final Icon RUN_ANYTHING_POPPED_ICON = new PoppedIcon(AllIcons.Actions.Run_anything, 16, 16);
  static final String RUN_ANYTHING = "RunAnything";
  private RunAnythingAction.MyListRenderer myRenderer;
  private MySearchTextField myPopupField;
  private JBPopup myPopup;
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, ApplicationManager.getApplication());
  private JBList myList;
  private AnActionEvent myActionEvent;
  private Component myContextComponent;
  private CalcThread myCalcThread;
  private volatile ActionCallback myCurrentWorker = ActionCallback.DONE;
  private int myCalcThreadRestartRequestId = 0;
  private final Object myWorkerRestartRequestLock = new Object();
  private int myHistoryIndex = 0;
  private boolean mySkipFocusGain = false;
  private volatile JBPopup myBalloon;
  private int myPopupActualWidth;
  private Component myFocusOwner;
  private Editor myEditor;
  @Nullable
  private VirtualFile myVirtualFile;
  private RunAnythingHistoryItem myHistoryItem;
  private JLabel myAdComponent;
  private DataContext myDataContext;
  private static final NotNullLazyValue<Map<String, Icon>> ourIconsMap;
  private JLabel myTextFieldTitle;
  private boolean myIsItemSelected;
  private String myLastInputText = null;

  static {
    ModifierKeyDoubleClickHandler.getInstance().registerAction(RUN_ANYTHING_ACTION_ID, KeyEvent.VK_CONTROL, -1, false);

    ourIconsMap = new NotNullLazyValue<Map<String, Icon>>() {
      @NotNull
      @Override
      protected Map<String, Icon> compute() {
        Map<String, Icon> map = ContainerUtil.newHashMap();
        map.put(UNKNOWN_CONFIGURATION, UNDEFINED_COMMAND_ICON);

        for (RunAnythingRunConfigurationProvider provider : RunAnythingRunConfigurationProvider.EP_NAME.getExtensions()) {
          map.put(provider.getConfigurationFactory().getName(), provider.getConfigurationFactory().getIcon());
        }

        return map;
      }
    };

    IdeEventQueue.getInstance().addPostprocessor(event -> {
      if (event instanceof KeyEvent) {
        final int keyCode = ((KeyEvent)event).getKeyCode();
        if (keyCode == KeyEvent.VK_SHIFT) {
          SHIFT_IS_PRESSED.set(event.getID() == KeyEvent.KEY_PRESSED);
        }
        else if (keyCode == KeyEvent.VK_ALT) {
          ALT_IS_PRESSED.set(event.getID() == KeyEvent.KEY_PRESSED);
        }
      }
      return false;
    }, null);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new BorderLayoutPanel() {
      @Override
      public Dimension getPreferredSize() {
        return JBUI.size(25);
      }
    };
    panel.setOpaque(false);

    final JLabel label = new JBLabel(AllIcons.Actions.Run_anything) {
      {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
        enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK);
      }
    };
    panel.add(label, BorderLayout.CENTER);
    RunAnythingUtil.initTooltip(label);
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (myBalloon != null) {
          myBalloon.cancel();
        }
        myFocusOwner = IdeFocusManager.findInstance().getFocusOwner();
        label.setToolTipText(null);
        IdeTooltipManager.getInstance().hideCurrentNow(false);
        ActionToolbarImpl toolbar = UIUtil.getParentOfType(ActionToolbarImpl.class, panel);
        if (toolbar != null) {
          ToolbarClicksCollector.record(RunAnythingAction.this, toolbar.getPlace());
        }
        actionPerformed(null, e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (myBalloon == null || myBalloon.isDisposed()) {
          label.setIcon(RUN_ANYTHING_POPPED_ICON);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (myBalloon == null || myBalloon.isDisposed()) {
          label.setIcon(AllIcons.Actions.Run_anything);
        }
      }
    });

    return panel;
  }

  private void updateComponents() {
    //noinspection unchecked
    myList = new JBList(new RunAnythingSearchListModel()) {
      int lastKnownHeight = JBUI.scale(30);

      @Override
      public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        if (size.height == -1) {
          size.height = lastKnownHeight;
        }
        else {
          lastKnownHeight = size.height;
        }
        int width = myBalloon != null ? myBalloon.getSize().width : 0;
        return new Dimension(Math.max(width, Math.min(size.width - 2, RunAnythingUtil.getPopupMaxWidth())),
                             myList.isEmpty() ? JBUI.scale(30) : size.height);
      }

      @Override
      public void clearSelection() {
        //avoid blinking
      }

      @Override
      public Object getSelectedValue() {
        try {
          return super.getSelectedValue();
        }
        catch (Exception e) {
          return null;
        }
      }
    };
    myRenderer = new MyListRenderer();
    myList.setCellRenderer(myRenderer);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (clickCount > 1 && clickCount % 2 == 0 || myList.getModel() instanceof RunAnythingSettingsModel) {
          event.consume();
          final int i = myList.locationToIndex(event.getPoint());
          if (i != -1) {
            getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(getField(), true));
            ApplicationManager.getApplication().invokeLater(() -> {
              myList.setSelectedIndex(i);
              executeCommand();
            });
          }
        }
        return false;
      }
    }.installOn(myList);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return null;
  }

  private void initSearchField(final MySearchTextField search) {
    final JTextField editor = search.getTextEditor();
    //    onFocusLost();
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        if (editor.hasFocus()) {
          ApplicationManager.getApplication().invokeLater(() -> myIsItemSelected = false);

          if (!myIsItemSelected) {
            myLastInputText = null;
            clearSelection();

            rebuildList(pattern);
          }
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
        String text = RunAnythingUtil.getInitialTextForNavigation(myEditor);
        text = text != null ? text.trim() : "";

        search.setText(text);
        search.getTextEditor().setForeground(UIUtil.getLabelForeground());
        search.selectText();
        editor.setColumns(SEARCH_FIELD_COLUMNS);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          final JComponent parent = (JComponent)editor.getParent();
          parent.revalidate();
          parent.repaint();
        });
        rebuildList(text);
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (myPopup instanceof AbstractPopup && myPopup.isVisible()
            && ((myList == e.getOppositeComponent()) || ((AbstractPopup)myPopup).getPopupWindow() == e.getOppositeComponent())) {
          return;
        }

        onPopupFocusLost();
      }
    });
  }

  private void clearSelection() {
    myList.getSelectionModel().clearSelection();
  }

  @NotNull
  private ActionCallback onPopupFocusLost() {
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

  private void executeCommand() {
    final String pattern = getField().getText();
    int index = myList.getSelectedIndex();

    //do nothing on attempt to execute empty command
    if (pattern.isEmpty() && index == -1) return;

    final Project project = getProject();
    final Module module = getModule();

    if (index != -1) {

      final RunAnythingSearchListModel model = getSearchingModel(myList);
      if (model != null) {
        if (isMoreItem(index)) {
          RunAnythingGroup group = RunAnythingGroup.findRunAnythingGroup(index);

          if (group != null) {
            myCurrentWorker.doWhenProcessed(() -> {
              myCalcThread = new CalcThread(project, pattern, true);
              myPopupActualWidth = 0;
              RunAnythingUtil.triggerMoreStatistics(group);
              myCurrentWorker = myCalcThread.insert(index, group);
            });

            return;
          }
        }
      }
    }

    final Object value = myList.getSelectedValue();
    saveHistory(project, pattern, value);
    IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(getField().getTextEditor());
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }

    if (value instanceof BooleanOptionDescription) {
      updateOption((BooleanOptionDescription)value);
      return;
    }

    RunAnythingUtil.triggerExecCategoryStatistics(index);

    Runnable onDone = null;
    try {
      if (value instanceof RunAnythingRunConfigurationItem || value instanceof RunAnythingActionItem) {
        runOnFocusSettlesDown(project, (RunAnythingItem)value, focusManager);
        return;
      }
      VirtualFile directory = getWorkDirectory(module);
      DataContext dataContext = createDataContext(directory, null, null, module, project);
      if (value instanceof RunAnythingCommandItem) {
        onDone = () -> ((RunAnythingCommandItem)value).run(dataContext);
      }
      else if (value == null) {
        onDone = () -> RunAnythingUtil.runOrCreateRunConfiguration(myDataContext, pattern, directory);
        return;
      }
    }
    finally {
      final ActionCallback callback = onPopupFocusLost();
      if (onDone != null) {
        callback.doWhenDone(onDone);
      }
    }
    focusManager.requestDefaultFocus(true);
  }

  private void runOnFocusSettlesDown(@NotNull Project project, @NotNull RunAnythingItem value, @NotNull IdeFocusManager focusManager) {
    focusManager.requestDefaultFocus(true);
    final Component comp = myContextComponent;
    final AnActionEvent event = myActionEvent;
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
      Component c = comp;
      if (c == null) c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

      value.run(createDataContext(null, c, event, null, project));
    });
  }

  @NotNull
  private static DataContext createDataContext(@Nullable VirtualFile directory,
                                               @Nullable Component focusOwner,
                                               @Nullable AnActionEvent event,
                                               @Nullable Module module,
                                               @NotNull Project project) {
    HashMap<String, Object> map = ContainerUtil.newHashMap();
    if (directory != null) {
      map.put(CommonDataKeys.VIRTUAL_FILE.getName(), directory);
    }

    if (event != null) {
      map.put(RUN_ANYTHING_EVENT_KEY.getName(), event);
    }

    map.put(EXECUTOR_KEY.getName(), getExecutor());

    if (focusOwner != null) {
      map.put(FOCUS_COMPONENT_KEY_NAME.getName(), focusOwner);
    }

    if (module != null) {
      map.put(LangDataKeys.MODULE.getName(), module);
    }

    map.put(CommonDataKeys.PROJECT.getName(), project);

    return SimpleDataContext.getSimpleContext(map, DataContext.EMPTY_CONTEXT);
  }

  @NotNull
  private Project getProject() {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(getField().getTextEditor()));
    assert project != null;
    return project;
  }

  @Nullable
  private Module getModule() {
    Module module = (Module)myDataContext.getData(LangDataKeys.MODULE.getName());
    if (module != null) {
      return module;
    }

    Project project = getProject();
    if (myVirtualFile != null) {
      return ModuleUtilCore.findModuleForFile(myVirtualFile, project);
    }

    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    if (selectedFiles.length == 0) {
      return null;
    }

    return ModuleUtilCore.findModuleForFile(selectedFiles[0], project);
  }

  @NotNull
  private VirtualFile getWorkDirectory(@Nullable Module module) {
    if (ALT_IS_PRESSED.get() && myVirtualFile != null) {
      return myVirtualFile.isDirectory() ? myVirtualFile : myVirtualFile.getParent();
    }

    VirtualFile[] selectedFiles = FileEditorManager.getInstance(getProject()).getSelectedFiles();
    if (selectedFiles.length > 0) {
      return selectedFiles[0].getParent();
    }

    return getBaseDirectory(module);
  }

  @NotNull
  private VirtualFile getBaseDirectory(@Nullable Module module) {
    VirtualFile projectBaseDir = getProject().getBaseDir();
    if (module == null) {
      return projectBaseDir;
    }

    VirtualFile firstContentRoot = getFirstContentRoot(module);
    if (firstContentRoot == null) {
      return projectBaseDir;
    }

    return firstContentRoot;
  }

  @Nullable
  public VirtualFile getFirstContentRoot(@NotNull final Module module) {
    if (module.isDisposed()) return null;
    return ArrayUtil.getFirstElement(ModuleRootManager.getInstance(module).getContentRoots());
  }

  private void updateOption(BooleanOptionDescription value) {
    value.setOptionState(!value.isOptionEnabled());
    myList.revalidate();
    myList.repaint();
    getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(getField(), true));
  }

  private boolean isMoreItem(int index) {
    return getSearchingModel(myList) != null && RunAnythingGroup.isMoreIndex(index);
  }

  @Nullable
  public static RunAnythingSearchListModel getSearchingModel(@NotNull JBList list) {
    ListModel model = list.getModel();
    return model instanceof RunAnythingSearchListModel ? (RunAnythingSearchListModel)model : null;
  }

  private void rebuildList(final String pattern) {
    assert EventQueue.isDispatchThread() : "Must be EDT";
    if (myCalcThread != null && !myCurrentWorker.isProcessed()) {
      myCurrentWorker = myCalcThread.cancel();
    }
    if (myCalcThread != null && !myCalcThread.isCanceled()) {
      myCalcThread.cancel();
    }
    synchronized (myWorkerRestartRequestLock) { // this lock together with RestartRequestId should be enough to prevent two CalcThreads running at the same time
      final int currentRestartRequest = ++myCalcThreadRestartRequestId;
      myCurrentWorker.doWhenProcessed(() -> {
        synchronized (myWorkerRestartRequestLock) {
          if (currentRestartRequest != myCalcThreadRestartRequestId) {
            return;
          }
          myCalcThread = new CalcThread(getProject(), pattern, false);
          myPopupActualWidth = 0;
          myCurrentWorker = myCalcThread.start();
        }
      });
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (Registry.is("ide.suppress.double.click.handler") && e.getInputEvent() instanceof KeyEvent) {
      if (((KeyEvent)e.getInputEvent()).getKeyCode() == KeyEvent.VK_CONTROL) {
        return;
      }
    }

    actionPerformed(e, null);
  }

  public void actionPerformed(AnActionEvent e, MouseEvent me) {
    if (myBalloon != null && myBalloon.isVisible()) {
      rebuildList(myPopupField.getText());
      return;
    }
    myCurrentWorker = ActionCallback.DONE;
    if (e != null) {
      myEditor = e.getData(CommonDataKeys.EDITOR);
      myVirtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
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

    HashMap<String, Object> dataMap = ContainerUtil.newHashMap();
    dataMap.put(CommonDataKeys.PROJECT.getName(), project);
    dataMap.put(RUN_ANYTHING_EVENT_KEY.getName(), myActionEvent);
    myDataContext = SimpleDataContext.getSimpleContext(dataMap, e.getDataContext());

    if (myPopupField != null) {
      Disposer.dispose(myPopupField);
    }
    myPopupField = new MySearchTextField();
    myPopupField.setPreferredSize(new Dimension(500, 43));
    myPopupField.getTextEditor().setFont(EditorUtil.getEditorFont().deriveFont(18f));

    JBTextField myTextField = myPopupField.getTextEditor();
    myTextField.putClientProperty(MATCHED_CONFIGURATION_PROPERTY, UNKNOWN_CONFIGURATION);

    setHandleMatchedConfiguration();

    myTextField.setMinimumSize(new Dimension(500, 50));

    myTextField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_SHIFT:
            myTextFieldTitle.setText(IdeBundle.message("run.anything.run.debug.title"));
            break;
          case KeyEvent.VK_ALT:
            updateByContextSwitch(IdeBundle.message("run.anything.run.in.context.title"), true);
            break;
        }

        if (e.isShiftDown() && e.isAltDown()) {
          myTextFieldTitle.setText(IdeBundle.message("run.anything.run.in.context.debug.title"));
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_SHIFT:
            myTextFieldTitle.setText(IdeBundle.message("run.anything.run.anything.title"));
            break;
          case KeyEvent.VK_ALT:
            updateByContextSwitch(IdeBundle.message("run.anything.run.anything.title"), false);
            break;
        }
      }

      private void updateByContextSwitch(@NotNull String message, boolean isAltPressed) {
        ALT_IS_PRESSED.set(isAltPressed);
        myTextFieldTitle.setText(message);
        updateMatchedRunConfigurationStuff();
      }
    });

    myPopupField.getTextEditor().addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        myHistoryIndex = 0;
        myHistoryItem = null;
      }
    });
    initSearchField(myPopupField);

    JTextField editor = myPopupField.getTextEditor();
    editor.setColumns(SEARCH_FIELD_COLUMNS);
    JPanel panel = new JPanel(new BorderLayout());

    myTextFieldTitle = new JLabel(IdeBundle.message("run.anything.run.anything.title"));
    JPanel topPanel = new NonOpaquePanel(new BorderLayout());
    Color foregroundColor = UIUtil.isUnderDarcula()
                            ? UIUtil.isUnderWin10LookAndFeel() ? JBColor.WHITE : new JBColor(Gray._240, Gray._200)
                            : UIUtil.getLabelForeground();


    myTextFieldTitle.setForeground(foregroundColor);
    myTextFieldTitle.setBorder(BorderFactory.createEmptyBorder(3, 5, 5, 0));
    if (SystemInfo.isMac) {
      myTextFieldTitle.setFont(myTextFieldTitle.getFont().deriveFont(Font.BOLD, myTextFieldTitle.getFont().getSize() - 1f));
    }
    else {
      myTextFieldTitle.setFont(myTextFieldTitle.getFont().deriveFont(Font.BOLD));
    }

    topPanel.add(myTextFieldTitle, BorderLayout.WEST);
    JPanel controls = new JPanel(new BorderLayout());
    controls.setOpaque(false);

    JLabel settings = new JLabel(AllIcons.General.GearPlain);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        showSettings();
        return true;
      }
    }.installOn(settings);

    settings.setBorder(UIUtil.isUnderWin10LookAndFeel() ? JBUI.Borders.emptyLeft(6) : JBUI.Borders.empty());

    controls.add(settings, BorderLayout.EAST);
    controls.setBorder(UIUtil.isUnderWin10LookAndFeel() ? JBUI.Borders.emptyTop(1) : JBUI.Borders.empty());

    topPanel.add(controls, BorderLayout.EAST);
    panel.add(myPopupField, BorderLayout.CENTER);
    panel.add(topPanel, BorderLayout.NORTH);
    panel.setBorder(JBUI.Borders.empty(3, 5, 4, 5));

    myAdComponent = HintUtil.createAdComponent(AD_CONTEXT_TEXT, JBUI.Borders.empty(1, 5), SwingConstants.LEFT);

    panel.add(myAdComponent, BorderLayout.SOUTH);

    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateAdText();

        Object selectedValue = myList.getSelectedValue();
        if (selectedValue == null || getSearchingModel(myList) == null) return;

        String lastInput = myTextField.getText();
        myIsItemSelected = true;

        if (isMoreItem(myList.getSelectedIndex()) && myLastInputText != null) {
          myTextField.setText(myLastInputText);
          return;
        }

        myTextField.setText(selectedValue instanceof RunAnythingItem ? ((RunAnythingItem)selectedValue).getText() : myLastInputText);

        if (myLastInputText == null) myLastInputText = lastInput;
      }
    });

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

    final RelativePoint showPoint;
    if (parent != null) {
      int height = UISettings.getInstance().getShowMainToolbar() ? 135 : 115;
      if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
        height -= 20;
      }
      showPoint = new RelativePoint(parent, new Point((parent.getSize().width - panel.getPreferredSize().width) / 2, height));
    }
    else {
      showPoint = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    }
    myList.setFont(UIUtil.getListFont());
    myBalloon.show(showPoint);
    initSearchActions(myBalloon, myPopupField);
    IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
    focusManager.requestFocus(editor, true);
    FeatureUsageTracker.getInstance().triggerFeatureUsed(RUN_ANYTHING);
  }

  private void setHandleMatchedConfiguration() {
    myPopupField.getTextEditor().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateMatchedRunConfigurationStuff();
      }
    });
  }

  private void updateMatchedRunConfigurationStuff() {
    JBTextField textField = myPopupField.getTextEditor();
    String pattern = textField.getText();

    RunAnythingRunConfigurationProvider
      provider = RunAnythingRunConfigurationProvider.findMatchedProvider(getProject(), pattern, getWorkDirectory(getModule()));
    String name = provider != null ? provider.getConfigurationFactory().getName() : null;

    if (name != null) {
      textField.putClientProperty(MATCHED_CONFIGURATION_PROPERTY, name);
      setAdText(RUN_ANYTHING_RUN_CONFIGURATION_AD_TEXT);
    }
    else {
      textField.putClientProperty(MATCHED_CONFIGURATION_PROPERTY, UNKNOWN_CONFIGURATION);
    }
  }

  private void updateAdText() {
    Object value = myList.getSelectedValue();

    if (value instanceof RunAnythingItem) {
      setAdText(((RunAnythingItem)value).getAdText());
    }
  }

  private void showSettings() {
    myPopupField.setText("");
    final RunAnythingSettingsModel model = new RunAnythingSettingsModel();
    Arrays.stream(RunAnythingGroup.EP_NAME.getExtensions()).map(
      group -> new RunAnythingSEOption(getProject(), IdeBundle.message("run.anything.group.settings.title", group.getTitle()),
                                       group.getVisibilityKey())).forEach(model::addElement);

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

  private static void saveHistory(Project project, String text, Object value) {
    if (project == null || project.isDisposed() || !project.isInitialized()) {
      return;
    }
    HistoryType type = null;
    String fqn = null;
    if (value instanceof RunAnythingActionItem) {
      type = HistoryType.ACTION;
      fqn = ActionManager.getInstance().getId(((RunAnythingActionItem)value).getValue());
    }
    else if (value instanceof RunAnythingRunConfigurationItem) {
      type = HistoryType.RUN_CONFIGURATION;
      fqn = ((RunAnythingRunConfigurationItem)value).getText();
    }

    final PropertiesComponent storage = PropertiesComponent.getInstance(project);
    final String[] values = storage.getValues(RUN_ANYTHING_HISTORY_KEY);
    List<RunAnythingHistoryItem> history = new ArrayList<>();
    if (values != null) {
      for (String s : values) {
        final String[] split = s.split("\t");
        if (split.length != 3 || text.equals(split[0])) {
          continue;
        }
        if (!StringUtil.isEmpty(split[0])) {
          history.add(new RunAnythingHistoryItem(split[0], split[1], split[2]));
        }
      }
    }
    history.add(0, new RunAnythingHistoryItem(text, type == null ? null : type.name(), fqn));

    if (history.size() > MAX_RUN_ANYTHING_HISTORY) {
      history = history.subList(0, MAX_RUN_ANYTHING_HISTORY);
    }
    final String[] newValues = new String[history.size()];
    for (int i = 0; i < newValues.length; i++) {
      newValues[i] = history.get(i).toString();
    }
    storage.setValues(RUN_ANYTHING_HISTORY_KEY, newValues);
  }

  private void initSearchActions(@NotNull JBPopup balloon, @NotNull MySearchTextField searchTextField) {

    final JTextField editor = searchTextField.getTextEditor();
    DumbAwareAction.create(e -> RunAnythingUtil.jumpNextGroup(true, myList))
                   .registerCustomShortcutSet(CustomShortcutSet.fromString("TAB"), editor, balloon);
    DumbAwareAction.create(e -> RunAnythingUtil.jumpNextGroup(false, myList))
                   .registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), editor, balloon);
    AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(e -> {
      if (myBalloon != null && myBalloon.isVisible()) {
        myBalloon.cancel();
      }
      if (myPopup != null && myPopup.isVisible()) {
        myPopup.cancel();
      }
    }).registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), editor, balloon);

    DumbAwareAction.create(e -> executeCommand())
                   .registerCustomShortcutSet(
                     CustomShortcutSet.fromString("ENTER", "shift ENTER", "alt ENTER", "alt shift ENTER", "meta ENTER"), editor, balloon);

    DumbAwareAction.create(e -> {
      RunAnythingSearchListModel model = getSearchingModel(myList);
      if (model == null) return;

      Object selectedValue = myList.getSelectedValue();
      int index = myList.getSelectedIndex();
      if (!(selectedValue instanceof RunAnythingCommandItem) || isMoreItem(index)) return;

      RunAnythingCache.getInstance(getProject()).getState().getCommands().remove(((RunAnythingCommandItem)selectedValue).getText());

      model.remove(index);
      RunAnythingGroup.shiftIndexes(index, -1);
      if (model.size() > 0) ScrollingUtil.selectItem(myList, index < model.size() ? index : index - 1);

      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (myCalcThread != null) {
          myCalcThread.updatePopup();
        }
      });
    }).registerCustomShortcutSet(CustomShortcutSet.fromString("shift BACK_SPACE"), editor, balloon);

    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final PropertiesComponent storage = PropertiesComponent.getInstance(e.getProject());
        final String[] values = storage.getValues(RUN_ANYTHING_HISTORY_KEY);
        if (values != null) {
          if (values.length > myHistoryIndex) {
            final List<String> data = StringUtil.split(values[myHistoryIndex], "\t");
            myHistoryItem = new RunAnythingHistoryItem(data.get(0), data.get(1), data.get(2));
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


  public void setAdText(@NotNull final String s) {
    myAdComponent.setText(s);
  }

  @NotNull
  public static Executor getExecutor() {
    final Executor runExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final Executor debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);

    return !SHIFT_IS_PRESSED.get() ? runExecutor : debugExecutor;
  }

  private class MyListRenderer extends ColoredListCellRenderer {
    private final RunAnythingMyAccessibleComponent myMainPanel = new RunAnythingMyAccessibleComponent(new BorderLayout());
    private final JLabel myTitle = new JLabel();

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component cmp = null;
      if (isMoreItem(index)) {
        cmp = RunAnythingMore.get(isSelected);
      }

      if (cmp == null) {
        if (value instanceof RunAnythingItem) {
          cmp = ((RunAnythingItem)value).createComponent(isSelected);
        }
        else {
          cmp = super.getListCellRendererComponent(list, value, index, isSelected, isSelected);
          final JPanel p = new JPanel(new BorderLayout());
          p.setBackground(UIUtil.getListBackground(isSelected));
          p.add(cmp, BorderLayout.CENTER);
          cmp = p;
        }

        if (value instanceof BooleanOptionDescription) {
          final JPanel panel = new JPanel(new BorderLayout());
          panel.setBackground(UIUtil.getListBackground(isSelected));
          panel.add(cmp, BorderLayout.CENTER);
          final Component rightComponent;
          final OnOffButton button = new OnOffButton();
          button.setSelected(((BooleanOptionDescription)value).isOptionEnabled());
          rightComponent = button;
          panel.add(rightComponent, BorderLayout.EAST);

          JLabel settingLabel = new JLabel(RunAnythingUtil.getSettingText((OptionDescription)value));
          settingLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
          panel.add(settingLabel, BorderLayout.WEST);
          panel.add(rightComponent, BorderLayout.EAST);
          cmp = panel;
        }
      }

      Color bg = cmp.getBackground();
      if (bg == null) {
        cmp.setBackground(UIUtil.getListBackground(isSelected));
        bg = cmp.getBackground();
      }
      myMainPanel.removeAll();
      RunAnythingSearchListModel model = getSearchingModel(myList);
      if (model != null) {
        String title = RunAnythingGroup.getTitle(index);
        if (title != null) {
          myTitle.setText(title);
          myMainPanel.add(RunAnythingUtil.createTitle(" " + title), BorderLayout.NORTH);
        }
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

    @Override
    protected void customizeCellRenderer(@NotNull JList list, final Object value, int index, final boolean selected, boolean hasFocus) {
    }

    public void recalculateWidth() {
      RunAnythingSearchListModel model = getSearchingModel(myList);
      if (model == null) return;

      myTitle.setIcon(EmptyIcon.ICON_16);
      myTitle.setFont(RunAnythingUtil.getTitleFont());
      int index = 0;
      while (index < model.getSize()) {
        String title = RunAnythingGroup.getTitle(index);
        if (title != null) {
          myTitle.setText(title);
        }
        index++;
      }

      myTitle.setForeground(Gray._122);
      myTitle.setAlignmentY(BOTTOM_ALIGNMENT);
    }
  }

  @SuppressWarnings({"SSBasedInspection", "unchecked"})
  private class CalcThread implements Runnable {
    @NotNull private final Project myProject;
    @NotNull private final String myPattern;
    private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();
    private final ActionCallback myDone = new ActionCallback();
    @NotNull private final RunAnythingSearchListModel myListModel;
    @Nullable private final Module myModule;

    public CalcThread(@NotNull Project project, @NotNull String pattern, boolean reuseModel) {
      myProject = project;
      myModule = getModule();
      myPattern = pattern;
      RunAnythingSearchListModel model = getSearchingModel(myList);
      myListModel = reuseModel ? model != null ? model : new RunAnythingSearchListModel() : new RunAnythingSearchListModel();
    }

    @Override
    public void run() {
      try {
        check();

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          // this line must be called on EDT to avoid context switch at clear().append("text") Don't touch. Ask [kb]
          myList.getEmptyText().setText("Searching...");

          if (getSearchingModel(myList) != null) {
            //noinspection unchecked
            myAlarm.cancelAllRequests();
            myAlarm.addRequest(() -> {
              if (!myDone.isRejected()) {
                myList.setModel(myListModel);
                updatePopup();
              }
            }, 50);
          }
          else {
            myList.setModel(myListModel);
          }
        });

        if (myPattern.trim().length() == 0) {
          buildGroups(true);
          return;
        }

        check();
        buildGroups(false);
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
          SwingUtilities.invokeLater(() -> myList.getEmptyText().setText(IdeBundle.message("run.anything.command.empty.list.title")));
          updatePopup();
        }
        if (!myDone.isProcessed()) {
          myDone.setDone();
        }
      }
    }

    private void buildGroups(boolean isRecent) {
      buildAllGroups(myPattern, () -> check(), isRecent);
      updatePopup();
    }

    private void runReadAction(@NotNull Runnable action) {
      if (!DumbService.getInstance(myProject).isDumb()) {
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

    private void buildAllGroups(@NotNull String pattern, @NotNull Runnable checkCancellation, boolean isRecent) {
      Condition<RunAnythingGroup> recent = isRecent ? ((group) -> group.shouldBeShownInitially()) : Condition.TRUE;
      Arrays.stream(RunAnythingGroup.EP_NAME.getExtensions())
            .filter(group -> recent.value(group))
            .forEach(runAnythingGroup -> {
              runReadAction(() -> runAnythingGroup.collectItems(myProject, myModule, myListModel, pattern, checkCancellation));
              checkCancellation.run();
            });
    }

    private boolean isCanceled() {
      return myProgressIndicator.isCanceled() || myDone.isRejected();
    }

    @SuppressWarnings("SSBasedInspection")
    void updatePopup() {
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
            installActions();
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
                final boolean canClose =
                  balloon == null || balloon.isDisposed() || (!getField().getTextEditor().hasFocus() && !mySkipFocusGain);
                if (canClose) {
                  PropertiesComponent.getInstance()
                                     .setValue("run.anything.max.popup.width", Math.max(content.getWidth(), JBUI.scale(600)),
                                               JBUI.scale(600));
                }
                return canClose;
              })
              .setShowShadow(false)
              .setShowBorder(false)
              .createPopup();
            myProject.putUserData(RUN_ANYTHING_POPUP, myPopup);
            myPopup.getContent().setBorder(null);
            Disposer.register(myPopup, new Disposable() {
              @Override
              public void dispose() {
                myProject.putUserData(RUN_ANYTHING_POPUP, null);
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                  resetFields();
                  //noinspection SSBasedInspection
                  SwingUtilities.invokeLater(() -> ActionToolbarImpl.updateAllToolbarsImmediately());
                  if (myActionEvent != null && myActionEvent.getInputEvent() instanceof MouseEvent) {
                    final Component component = myActionEvent.getInputEvent().getComponent();
                    if (component != null) {
                      final JLabel label = UIUtil.getParentOfType(JLabel.class, component);
                      if (label != null) {
                        SwingUtilities.invokeLater(() -> label.setIcon(AllIcons.Actions.Run_anything));
                      }
                    }
                  }
                  myActionEvent = null;
                  myLastInputText = null;
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
                if (myPopup != null) {
                  myPopup.cancel();
                }
              }
            }, myPopup);
          }
          else {
            myList.revalidate();
            myList.repaint();
          }
          //ScrollingUtil.ensureSelectionExists(myList);
          if (myList.getModel().getSize() > 0) {
            updatePopupBounds();
          }
        }
      });
    }

    public ActionCallback cancel() {
      myProgressIndicator.cancel();
      //myDone.setRejected();
      return myDone;
    }

    public ActionCallback insert(final int index, @NotNull RunAnythingGroup group) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> runReadAction(() -> {
        try {
          RunAnythingGroup.SearchResult result = group.getVisibleItems(myProject, myModule, myListModel, myPattern, true, this::check);

          check();
          SwingUtilities.invokeLater(() -> {
            try {
              int shift = 0;
              int i = index + 1;
              for (Object o : result) {
                //noinspection unchecked
                myListModel.insertElementAt(o, i);
                shift++;
                i++;
              }

              RunAnythingGroup.shiftIndexes(index, shift);
              if (!result.isNeedMore()) {
                group.dropMoreIndex();
              }

              clearSelection();
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
      }));
      return myDone;
    }

    public ActionCallback start() {
      ApplicationManager.getApplication().executeOnPooledThread(this);
      return myDone;
    }
  }

  private void installActions() {
    RunAnythingScrollingUtil.installActions(myList, getField().getTextEditor(), () -> {
      myIsItemSelected = true;
      getField().getTextEditor().setText(myLastInputText);
      clearSelection();
    }, UISettings.getInstance().getCycleScrolling());

    ScrollingUtil.installActions(myList, getField().getTextEditor());
  }

  protected void resetFields() {
    if (myBalloon != null) {
      final JBPopup balloonToBeCanceled = myBalloon;
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> balloonToBeCanceled.cancel());
      myBalloon = null;
    }
    myCurrentWorker.doWhenProcessed(() -> {
      final Object lock = myCalcThread;
      if (lock != null) {
        synchronized (lock) {
          myContextComponent = null;
          myFocusOwner = null;
          myPopup = null;
          myHistoryIndex = 0;
          myPopupActualWidth = 0;
          myCurrentWorker = ActionCallback.DONE;
          myCalcThread = null;
          myEditor = null;
          myVirtualFile = null;
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
      if ((sz.width > RunAnythingUtil.getPopupMaxWidth() || sz.height > RunAnythingUtil.getPopupMaxWidth())) {
        final JBScrollPane pane = new JBScrollPane();
        final int extraWidth = pane.getVerticalScrollBar().getWidth() + 1;
        final int extraHeight = pane.getHorizontalScrollBar().getHeight() + 1;
        sz = new Dimension(Math.min(RunAnythingUtil.getPopupMaxWidth(), Math.max(getField().getWidth(), sz.width + extraWidth)),
                           Math.min(RunAnythingUtil.getPopupMaxWidth(), sz.height + extraHeight));
        sz.width += 20;
      }
      else {
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
    }
    else {
      try {
        RunAnythingUtil.adjustPopup(myBalloon, myPopup);
      }
      catch (Exception ignore) {
      }
    }
  }

  private enum HistoryType {PSI, FILE, SETTING, ACTION, RUN_CONFIGURATION}

  static class MySearchTextField extends SearchTextField implements DataProvider, Disposable {
    public MySearchTextField() {
      super(false, "RunAnythingHistory");
      JTextField editor = getTextEditor();
      editor.setOpaque(false);
      editor.putClientProperty("JTextField.Search.noBorderRing", Boolean.TRUE);
      if (UIUtil.isUnderDarcula()) {
        editor.setBackground(Gray._45);
        editor.setForeground(Gray._240);
      }
    }

    @Override
    protected boolean customSetupUIAndTextField(@NotNull TextFieldWithProcessing textField, @NotNull Consumer<TextUI> uiConsumer) {
      if (UIUtil.isUnderDarcula()) {
        uiConsumer.consume(new MyDarcula(ourIconsMap));
        textField.setBorder(new DarculaTextBorder());
      }
      else {
        if (SystemInfo.isMac) {
          uiConsumer.consume(new MyMacUI(ourIconsMap));
          textField.setBorder(new MacIntelliJTextBorder());
        }
        else {
          uiConsumer.consume(new MyWinUI(ourIconsMap));
          textField.setBorder(new WinIntelliJTextBorder());
        }
      }
      return true;
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

  private static class RunAnythingSettingsModel extends DefaultListModel<RunAnythingSEOption> {
  }
}