// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.BigPopupUI;
import com.intellij.ide.actions.bigPopup.ShowFilterAction;
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.ide.actions.runAnything.ui.RunAnythingScrollingUtil;
import com.intellij.ide.actions.searcheverywhere.GroupTitleRenderer;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.TextComponentEmptyText;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.dsl.gridLayout.builders.RowBuilder;
import com.intellij.ui.popup.list.SelectablePanel;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

import static com.intellij.ide.actions.runAnything.RunAnythingAction.ALT_IS_PRESSED;
import static com.intellij.ide.actions.runAnything.RunAnythingAction.SHIFT_IS_PRESSED;
import static com.intellij.ide.actions.runAnything.RunAnythingIconHandler.MATCHED_PROVIDER_PROPERTY;
import static com.intellij.ide.actions.runAnything.RunAnythingSearchListModel.RunAnythingMainListModel;
import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public final class RunAnythingPopupUI extends BigPopupUI {
  public static final int SEARCH_FIELD_COLUMNS = 25;
  public static final Icon UNKNOWN_CONFIGURATION_ICON = AllIcons.Actions.Run_anything;
  static final String RUN_ANYTHING = "RunAnything";
  public static final KeyStroke DOWN_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
  public static final KeyStroke UP_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
  private static final Border RENDERER_BORDER = JBUI.Borders.empty(1, 0);
  private static final String HELP_PLACEHOLDER = "?";
  private boolean myIsUsedTrigger;
  private volatile ActionCallback myCurrentWorker;
  private final @Nullable VirtualFile myVirtualFile;
  private JLabel myTextFieldTitle;
  private boolean myIsItemSelected;
  private volatile boolean myShiftIsPressed;
  private volatile boolean myAltIsPressed;
  private String myLastInputText = null;
  private final Module myModule;

  private RunAnythingContext mySelectedExecutingContext;
  private final List<RunAnythingContext> myAvailableExecutingContexts = new ArrayList<>();
  private RunAnythingChooseContextAction myChooseContextAction;
  private final Alarm myListRenderingAlarm = new Alarm();
  private final ExecutorService myExecutorService =
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Run Anything list building");

  public @Nullable String getUserInputText() {
    return myResultsList.getSelectedIndex() >= 0 ? myLastInputText : mySearchField.getText();
  }

  private void onMouseClicked(@NotNull MouseEvent event) {
    int clickCount = event.getClickCount();
    if (clickCount > 1 && clickCount % 2 == 0) {
      event.consume();
      final int i = myResultsList.locationToIndex(event.getPoint());
      if (i != -1) {
        getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(getField(), true));
        ApplicationManager.getApplication().invokeLater(() -> {
          myResultsList.setSelectedIndex(i);
          executeCommand();
        });
      }
    }
  }

  private void initSearchField() {
    updateContextCombobox();
    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myIsUsedTrigger = true;

        final String pattern = mySearchField.getText();
        if (mySearchField.hasFocus()) {
          ApplicationManager.getApplication().invokeLater(() -> myIsItemSelected = false);

          if (!myIsItemSelected) {
            myLastInputText = null;
            clearSelection();

            //invoke later here allows to get correct pattern from mySearchField
            ApplicationManager.getApplication().invokeLater(() -> {
              rebuildList();
            });
          }

          if (!isHelpMode(pattern)) {
            updateContextCombobox();
            adjustMainListEmptyText(mySearchField);
            return;
          }

          adjustEmptyText(mySearchField, field -> true, "",
                          IdeBundle.message("run.anything.help.list.empty.secondary.text"));
        }
      }
    });
    mySearchField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        rebuildList();
      }
    });
  }

  private static void adjustMainListEmptyText(@NotNull JBTextField editor) {
    adjustEmptyText(editor, field -> field.getText().isEmpty(), IdeBundle.message("run.anything.main.list.empty.primary.text"),
                    IdeBundle.message("run.anything.main.list.empty.secondary.text"));
  }

  static boolean isHelpMode(@NotNull String pattern) {
    return pattern.startsWith(HELP_PLACEHOLDER);
  }

  private void clearSelection() {
    myResultsList.getSelectionModel().clearSelection();
  }

  private JTextField getField() {
    return mySearchField;
  }

  private void executeCommand() {
    final String pattern = getField().getText();
    int index = myResultsList.getSelectedIndex();

    //do nothing on attempt to execute empty command
    if (pattern.isEmpty() && index == -1) return;

    final RunAnythingSearchListModel model = getSearchingModel(myResultsList);
    if (index != -1 && model != null && isMoreItem(index)) {
      RunAnythingGroup group = model.findGroupByMoreIndex(index);

      if (group != null) {
        myCurrentWorker.doWhenProcessed(() -> {
          RunAnythingUsageCollector.triggerMoreStatistics(myProject, group, model.getClass());
          RunAnythingSearchListModel listModel = (RunAnythingSearchListModel)myResultsList.getModel();
          myCurrentWorker = insert(group, listModel, getDataContext(), getSearchPattern(), index, -1);
          myCurrentWorker.doWhenProcessed(() -> {
            clearSelection();
            ScrollingUtil.selectItem(myResultsList, index);
          });
        });

        return;
      }
    }

    if (model != null) {
      RunAnythingUsageCollector.triggerExecCategoryStatistics(myProject, model.getGroups(), model.getClass(), index,
                                                                        myShiftIsPressed, myAltIsPressed);
    }
    RunAnythingUtil.executeMatched(getDataContext(), pattern);

    mySearchField.setText("");
    searchFinishedHandler.run();
    triggerUsed();
  }

  public static @NotNull ActionCallback insert(@NotNull RunAnythingGroup group,
                                               @NotNull RunAnythingSearchListModel listModel,
                                               @NotNull DataContext dataContext,
                                               @NotNull String pattern,
                                               int index,
                                               int itemsNumberToInsert) {
    ActionCallback callback = new ActionCallback();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<RunAnythingItem> items = ContainerUtil.filterIsInstance(listModel.getItems(), RunAnythingItem.class);
      RunAnythingGroup.SearchResult result;
      try {
        result = ProgressManager.getInstance().runProcess(
          () -> group.getItems(dataContext,
                               items,
                               trimHelpPattern(pattern),
                               itemsNumberToInsert == -1 ? group.getMaxItemsToInsert() : itemsNumberToInsert),
          new EmptyProgressIndicator());
      }
      catch (ProcessCanceledException e) {
        callback.setRejected();
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        int shift = 0;
        int i = index + 1;
        for (Object o : result) {
          listModel.add(i, o);
          shift++;
          i++;
        }

        listModel.shiftIndexes(index, shift);
        if (!result.isNeedMore()) {
          group.resetMoreIndex();
        }

        callback.setDone();
      });
    });
    return callback;
  }

  private @NotNull Project getProject() {
    return myProject;
  }

  private @Nullable Module getModule() {
    if (myModule != null) {
      return myModule;
    }

    Project project = getProject();
    if (myVirtualFile != null) {
      Module moduleForFile = ModuleUtilCore.findModuleForFile(myVirtualFile, project);
      if (moduleForFile != null) {
        return moduleForFile;
      }
    }

    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    if (selectedFiles.length != 0) {
      Module moduleForFile = ModuleUtilCore.findModuleForFile(selectedFiles[0], project);
      if (moduleForFile != null) {
        return moduleForFile;
      }
    }

    return null;
  }

  private @NotNull VirtualFile getWorkDirectory() {
    if (myAltIsPressed) {
      if (myVirtualFile != null) {
        VirtualFile file = myVirtualFile.isDirectory() ? myVirtualFile : myVirtualFile.getParent();
        if (file != null) {
          return file;
        }
      }

      VirtualFile[] selectedFiles = FileEditorManager.getInstance(getProject()).getSelectedFiles();
      if (selectedFiles.length > 0) {
        VirtualFile file = selectedFiles[0].getParent();
        if (file != null) {
          return file;
        }
      }
    }

    return getBaseDirectory(getModule());
  }

  private @NotNull VirtualFile getBaseDirectory(@Nullable Module module) {
    VirtualFile projectBaseDir = getProject().getBaseDir();
    if (module == null) {
      return projectBaseDir;
    }

    VirtualFile firstContentRoot = getFirstContentRoot(module);
    if (firstContentRoot == null || !firstContentRoot.isDirectory()) {
      return projectBaseDir;
    }

    return firstContentRoot;
  }

  public @Nullable VirtualFile getFirstContentRoot(final @NotNull Module module) {
    if (module.isDisposed()) return null;
    return ArrayUtil.getFirstElement(ModuleRootManager.getInstance(module).getContentRoots());
  }

  private boolean isMoreItem(int index) {
    RunAnythingSearchListModel model = getSearchingModel(myResultsList);
    return model != null && model.isMoreIndex(index);
  }

  public static @Nullable RunAnythingSearchListModel getSearchingModel(@NotNull JBList list) {
    ListModel model = list.getModel();
    return model instanceof RunAnythingSearchListModel ? (RunAnythingSearchListModel)model : null;
  }

  private void rebuildList() {
    ThreadingAssertions.assertEventDispatchThread();

    myListRenderingAlarm.cancelAllRequests();
    myResultsList.getEmptyText().setText(FindBundle.message("empty.text.searching"));

    if (DumbService.getInstance(myProject).isDumb()) {
      myResultsList.setEmptyText(IdeBundle.message("run.anything.indexing.mode.not.supported"));
      return;
    }

    ReadAction.nonBlocking(() -> new RunAnythingCalcThread(myProject, getDataContext(), getSearchPattern()).compute())
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.defaultModalityState(), model ->
        myListRenderingAlarm.addRequest(() -> {
          addListDataListener(model);
          myResultsList.setModel(model);
          model.update();
        }, 150))
      .submit(myExecutorService);
  }

  @Override
  protected void addListDataListener(@NotNull AbstractListModel<Object> model) {
    model.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        updateViewType(ViewType.FULL);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        if (myResultsList.isEmpty()) {
          updateViewType(ViewType.SHORT);
        }
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        updateViewType(myResultsList.isEmpty() ? ViewType.SHORT : ViewType.FULL);
      }
    });
  }

  public void initResultsList() {
    myResultsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateAdText(getDataContext());

        Object selectedValue = myResultsList.getSelectedValue();
        if (selectedValue == null) return;

        String lastInput = mySearchField.getText();
        myIsItemSelected = true;

        if (isMoreItem(myResultsList.getSelectedIndex())) {
          if (myLastInputText != null) {
            mySearchField.setText(myLastInputText);
          }
          return;
        }

        mySearchField.setText(selectedValue instanceof RunAnythingItem ? ((RunAnythingItem)selectedValue).getCommand() : myLastInputText);

        if (myLastInputText == null) myLastInputText = lastInput;
      }
    });
  }

  private void updateContextCombobox() {
    ReadAction.nonBlocking(() -> {
      DataContext dataContext = getDataContext();
      Object value = myResultsList.getSelectedValue();
      String text = value instanceof RunAnythingItem ? ((RunAnythingItem)value).getCommand() : getSearchPattern();
      RunAnythingProvider<?> provider = RunAnythingProvider.findMatchedProvider(dataContext, text);
      if (provider != null) {
        myChooseContextAction.setAvailableContexts(provider.getExecutionContexts(dataContext));
      }

      return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext);
    }).finishOnUiThread(ModalityState.defaultModalityState(), event -> {
      ActionUtil.performDumbAwareUpdate(myChooseContextAction, event, false);
    }).submit(AppExecutorUtil.getAppExecutorService());
  }

  private void createTextFieldTitle() {
    myTextFieldTitle = new JLabel(IdeBundle.message("run.anything.run.anything.title")) {
      @Override
      public void updateUI() {
        super.updateUI();
        Font defaultFont = JBFont.label();
        if (SystemInfo.isMac) {
          setFont(defaultFont.deriveFont(Font.BOLD, defaultFont.getSize() - 1f));
        }
        else {
          setFont(defaultFont.deriveFont(Font.BOLD));
        }
      }
    };

    Color foregroundColor = StartupUiUtil.isUnderDarcula()
                            ? UIUtil.isUnderWin10LookAndFeel() ? JBColor.WHITE : new JBColor(Gray._240, Gray._200)
                            : UIUtil.getLabelForeground();


    myTextFieldTitle.setForeground(foregroundColor);
  }

  private @NotNull DataContext getDataContext() {
    return SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.VIRTUAL_FILE, getWorkDirectory())
      .add(RunAnythingAction.EXECUTOR_KEY, getCurrentExecutor())
      .add(RunAnythingProvider.EXECUTING_CONTEXT, myChooseContextAction.getSelectedContext())
      .build();
  }

  public void initMySearchField() {
    mySearchField.putClientProperty(MATCHED_PROVIDER_PROPERTY, UNKNOWN_CONFIGURATION_ICON);

    setHandleMatchedConfiguration();

    adjustMainListEmptyText(mySearchField);

    mySearchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        updateByModifierKeysEvent(e);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        updateByModifierKeysEvent(e);
      }

      private void updateByModifierKeysEvent(@NotNull KeyEvent e) {
        String message;
        myShiftIsPressed = e.isShiftDown();
        myAltIsPressed = e.isAltDown();
        if (myShiftIsPressed && myAltIsPressed) {
          message = IdeBundle.message("run.anything.run.in.context.debug.title");
        }
        else if (myShiftIsPressed) {
          message = IdeBundle.message("run.anything.run.debug.title");
        }
        else if (myAltIsPressed) {
          message = IdeBundle.message("run.anything.run.in.context.title");
        }
        else {
          message = IdeBundle.message("run.anything.run.anything.title");
        }
        SHIFT_IS_PRESSED.set(myShiftIsPressed);
        ALT_IS_PRESSED.set(myAltIsPressed);
        myTextFieldTitle.setText(message);
        updateMatchedRunConfigurationStuff();
      }
    });

    initSearchField();

    mySearchField.setColumns(SEARCH_FIELD_COLUMNS);
  }

  public static void adjustEmptyText(@NotNull JBTextField textEditor,
                                     @NotNull Predicate<JBTextField> function,
                                     @NotNull @NlsContexts.StatusText String leftText,
                                     @NotNull @NlsContexts.StatusText String rightText) {
    textEditor.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, function);
    StatusText statusText = textEditor.getEmptyText();
    statusText.setShowAboveCenter(false);
    statusText.setText(leftText, SimpleTextAttributes.GRAY_ATTRIBUTES);
    statusText.appendText(false, 0, rightText, SimpleTextAttributes.GRAY_ATTRIBUTES, null);
    statusText.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
  }

  private void setHandleMatchedConfiguration() {
    mySearchField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateMatchedRunConfigurationStuff();
      }
    });
  }

  private void updateMatchedRunConfigurationStuff() {
    JBTextField textField = mySearchField;
    String pattern = textField.getText();

    ReadAction.nonBlocking(() -> {
      DataContext dataContext = getDataContext();
      RunAnythingProvider provider = RunAnythingProvider.findMatchedProvider(dataContext, pattern);
      if (provider == null) {
        return null;
      }

      Object value = provider.findMatchingValue(dataContext, pattern);
      if (value == null) {
        return null;
      }
      //noinspection unchecked
      return provider.getIcon(value);
    }).finishOnUiThread(ModalityState.defaultModalityState(), icon -> {
      if (icon == null) {
        return;
      }

      textField.putClientProperty(MATCHED_PROVIDER_PROPERTY, icon);
    }).submit(AppExecutorUtil.getAppExecutorService());
  }

  private void updateAdText(@NotNull DataContext dataContext) {
    Object value = myResultsList.getSelectedValue();

    if (value instanceof RunAnythingItem) {
      RunAnythingProvider provider = RunAnythingProvider.findMatchedProvider(dataContext, ((RunAnythingItem)value).getCommand());
      if (provider != null) {
        String adText = provider.getAdText();
        if (adText != null) {
          setAdText(adText);
        }
      }
    }
  }

  private void triggerUsed() {
    if (myIsUsedTrigger) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(RUN_ANYTHING);
    }
    myIsUsedTrigger = false;
  }


  public void setAdText(final @NlsContexts.PopupAdvertisement @NotNull String s) {
    myHintLabel.clearAdvertisements();
    myHintLabel.addAdvertisement(s, null);
  }

  /**
   * @deprecated this is an internal method, must not be used outside the class
   */
  @SuppressWarnings("DataFlowIssue")
  @Deprecated
  public static @NotNull Executor getExecutor() {
    final Executor runExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final Executor debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);

    return !SHIFT_IS_PRESSED.get() ? runExecutor : debugExecutor;
  }
  
  private @NotNull Executor getCurrentExecutor() {
    Executor debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
    return myShiftIsPressed && debugExecutor != null ? debugExecutor : DefaultRunExecutor.getRunExecutorInstance();
  }

  private final class MyListRenderer extends ColoredListCellRenderer<Object> {

    private final GroupTitleRenderer groupTitleRenderer = new GroupTitleRenderer();

    private final RunAnythingMore runAnythingMore = new RunAnythingMore();

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus) {
      Component cmp;
      Color bg = isSelected ? UIUtil.getListBackground(isSelected, true) : list.getBackground();
      if (isMoreItem(index)) {
        runAnythingMore.setBackground(bg);
        cmp = runAnythingMore;
      } else {
        if (value instanceof RunAnythingItem) {
          cmp = ((RunAnythingItem)value).createComponent(myLastInputText, isSelected, hasFocus);
          if (cmp.getBackground() == null || cmp.getBackground() == UIUtil.getListBackground()) {
            cmp.setBackground(bg);
          }
        }
        else {
          cmp = super.getListCellRendererComponent(list, value, index, isSelected, isSelected);
          final JPanel p = new JPanel(new BorderLayout());
          p.setBackground(bg);
          p.add(cmp, BorderLayout.CENTER);
          cmp = p;
        }
      }

      Color foreground = cmp.getForeground();
      if (foreground == null) {
        cmp.setForeground(UIUtil.getListForeground(isSelected));
        foreground = cmp.getBackground();
      }
      RunAnythingSearchListModel model = getSearchingModel(myResultsList);
      SelectablePanel wrapped = SelectablePanel.wrap(cmp, list.getBackground());
      wrapped.setSelectionColor(bg);
      wrapped.setForeground(foreground);
      if (ExperimentalUI.isNewUI()) {
        PopupUtil.configListRendererFixedHeight(wrapped);
      }
      else {
        wrapped.setBorder(RENDERER_BORDER);
      }
      if (model != null) {
        String title = model.getTitle(index);
        if (title != null) {
          return groupTitleRenderer.withDisplayedData(title, wrapped);
        }
      }

      return wrapped;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, final Object value, int index, final boolean selected, boolean hasFocus) {
    }
  }

  public static @NotNull String trimHelpPattern(@NotNull String pattern) {
    return isHelpMode(pattern) ? pattern.substring(HELP_PLACEHOLDER.length()) : pattern;
  }

  @Override
  public void installScrollingActions() {
    RunAnythingScrollingUtil.installActions(myResultsList, getField(), () -> {
      myIsItemSelected = true;
      mySearchField.setText(myLastInputText);
      clearSelection();
    }, UISettings.getInstance().getCycleScrolling());

    super.installScrollingActions();
  }

  public RunAnythingPopupUI(@NotNull AnActionEvent actionEvent) {
    super(actionEvent.getProject());

    myCurrentWorker = ActionCallback.DONE;
    myVirtualFile = actionEvent.getData(CommonDataKeys.VIRTUAL_FILE);

    myModule = actionEvent.getData(PlatformCoreDataKeys.MODULE);

    init();

    initSearchActions();

    initResultsList();

    initSearchField();

    initMySearchField();
  }

  @Override
  public @NotNull JBList<Object> createList() {
    RunAnythingSearchListModel listModel = new RunAnythingMainListModel();
    addListDataListener(listModel);

    return new JBList<>(listModel);
  }

  private void initSearchActions() {
    myResultsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        onMouseClicked(e);
      }
    });

    DumbAwareAction.create(e -> RunAnythingUtil.jumpNextGroup(true, myResultsList))
      .registerCustomShortcutSet(CustomShortcutSet.fromString("TAB"), mySearchField, this);
    DumbAwareAction.create(e -> RunAnythingUtil.jumpNextGroup(false, myResultsList))
      .registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), mySearchField, this);

    AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
    DumbAwareAction.create(__ -> {
      triggerUsed();
      searchFinishedHandler.run();
    }).registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), this);

    DumbAwareAction.create(e -> executeCommand())
      .registerCustomShortcutSet(
        CustomShortcutSet.fromString("ENTER", "shift ENTER", "alt ENTER", "alt shift ENTER", "meta ENTER"), mySearchField, this);

    DumbAwareAction.create(e -> {
      RunAnythingSearchListModel model = getSearchingModel(myResultsList);
      if (model == null) return;

      Object selectedValue = myResultsList.getSelectedValue();
      int index = myResultsList.getSelectedIndex();
      if (!(selectedValue instanceof RunAnythingItem) || isMoreItem(index)) return;

      RunAnythingCache.getInstance(getProject()).getState().getCommands().remove(((RunAnythingItem)selectedValue).getCommand());

      model.remove(index);
      model.shiftIndexes(index, -1);
      if (!model.isEmpty()) ScrollingUtil.selectItem(myResultsList, index < model.getSize() ? index : index - 1);
    }).registerCustomShortcutSet(CustomShortcutSet.fromString("shift BACK_SPACE"), mySearchField, this);

    myProject.getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        ApplicationManager.getApplication().invokeLater(() -> rebuildList());
      }
    });
  }

  @Override
  protected @NotNull ListCellRenderer<Object> createCellRenderer() {
    return new MyListRenderer();
  }

  @Override
  protected @NotNull JComponent createHeader() {
    createTextFieldTitle();

    JPanel result = new JPanel();
    RowBuilder builder = new RowBuilder(result);
    builder.addResizable(myTextFieldTitle);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    myChooseContextAction = new RunAnythingChooseContextAction(result) {
      @Override
      public void setAvailableContexts(@NotNull List<? extends RunAnythingContext> executionContexts) {
        myAvailableExecutingContexts.clear();
        myAvailableExecutingContexts.addAll(executionContexts);
      }

      @Override
      public @NotNull List<RunAnythingContext> getAvailableContexts() {
        return myAvailableExecutingContexts;
      }

      @Override
      public void setSelectedContext(@Nullable RunAnythingContext context) {
        mySelectedExecutingContext = context;
      }

      @Override
      public @Nullable RunAnythingContext getSelectedContext() {
        return mySelectedExecutingContext;
      }
    };
    actionGroup.addAction(myChooseContextAction);
    actionGroup.addAction(new RunAnythingShowFilterAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("run.anything.toolbar", actionGroup, true);
    toolbar.setTargetComponent(mySearchField);
    toolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setOpaque(false);

    if (ExperimentalUI.isNewUI()) {
      myTextFieldTitle.setBorder(PopupUtil.getComplexPopupVerticalHeaderBorder());
      toolbarComponent.setBorder(null);
      result.setBorder(JBUI.Borders.compound(
        JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 1, 0),
        PopupUtil.getComplexPopupHorizontalHeaderBorder()));
    }
    else {
      result.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
    }

    builder.add(toolbarComponent);
    return result;
  }

  @Override
  protected @NlsContexts.PopupAdvertisement String @NotNull [] getInitialHints() {
    return new String[]{IdeBundle.message("run.anything.hint.initial.text",
                                          KeymapUtil.getKeystrokeText(UP_KEYSTROKE),
                                          KeymapUtil.getKeystrokeText(DOWN_KEYSTROKE))};
  }

  @Override
  protected @NotNull @Nls String getAccessibleName() {
    return IdeBundle.message("run.anything.accessible.name");
  }

  @Override
  protected @NotNull ExtendableTextField createSearchField() {
    ExtendableTextField searchField = super.createSearchField();

    Consumer<? super ExtendableTextComponent.Extension> extensionConsumer = (extension) -> searchField.addExtension(extension);
    searchField.addPropertyChangeListener(new RunAnythingIconHandler(extensionConsumer, searchField));

    return searchField;
  }

  @Override
  public void dispose() {}

  private final class RunAnythingShowFilterAction extends ShowFilterAction {
    private final @NotNull Collection<RunAnythingGroup> myTemplateGroups;

    private RunAnythingShowFilterAction() {
      myTemplateGroups = RunAnythingCompletionGroup.createCompletionGroups();
    }

    @Override
    public @NotNull String getDimensionServiceKey() {
      return "RunAnythingAction_Filter_Popup";
    }

    @Override
    protected boolean isEnabled() {
      return true;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    protected boolean isActive() {
      return myTemplateGroups.size() != getVisibleGroups().size();
    }

    @Override
    protected ElementsChooser<?> createChooser() {
      ElementsChooser<RunAnythingGroup> res =
        new ElementsChooser<>(new ArrayList<>(myTemplateGroups), false) {
          @Override
          protected String getItemText(@NotNull RunAnythingGroup value) {
            return value.getTitle();
          }
        };

      res.markElements(getVisibleGroups());
      ElementsChooser.ElementsMarkListener<RunAnythingGroup> listener = (element, isMarked) -> {
        RunAnythingCache.getInstance(myProject)
          .saveGroupVisibilityKey(element instanceof RunAnythingCompletionGroup
                                  ? ((RunAnythingCompletionGroup<?, ?>)element).getProvider().getClass().getCanonicalName()
                                  : element.getTitle(), isMarked);
        rebuildList();
      };
      res.addElementsMarkListener(listener);
      return res;
    }

    private @NotNull List<RunAnythingGroup> getVisibleGroups() {
      return ContainerUtil.filter(myTemplateGroups, group -> RunAnythingCache.getInstance(myProject).isGroupVisible(group));
    }
  }
}
