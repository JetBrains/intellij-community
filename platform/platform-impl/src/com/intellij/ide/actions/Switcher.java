// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static com.intellij.ide.actions.RecentLocationsAction.SHORTCUT_HEX_COLOR;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.*;
import static javax.swing.KeyStroke.getKeyStroke;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class Switcher extends AnAction implements DumbAware {
  private static volatile SwitcherPanel SWITCHER = null;
  private static final Color SEPARATOR_COLOR = JBColor.namedColor("Popup.separatorColor", new JBColor(Gray.xC0, Gray.x4B));

  private static final int MINIMUM_HEIGHT = JBUI.scale(100);
  private static final int SEPARATOR_HEIGHT_IN_ELEMENTS = 1;

  @NonNls private static final String SWITCHER_FEATURE_ID = "switcher";
  private static final Color ON_MOUSE_OVER_BG_COLOR = new JBColor(new Color(231, 242, 249), new Color(77, 80, 84));
  private static int CTRL_KEY;
  @Nullable public static final Runnable CHECKER = () -> {
    synchronized (Switcher.class) {
      if (SWITCHER != null) {
        SWITCHER.navigate(null);
      }
    }
  };
  @NotNull private static final CustomShortcutSet TW_SHORTCUT;

  static {
    Shortcut recentFiles = ArrayUtil.getFirstElement(getActiveKeymapShortcuts("RecentFiles").getShortcuts());
    List<Shortcut> shortcuts = ContainerUtil.newArrayList();
    for (char ch = '0'; ch <= '9'; ch++) {
      shortcuts.add(CustomShortcutSet.fromString("control " + ch).getShortcuts()[0]);
    }
    for (char ch = 'A'; ch <= 'Z'; ch++) {
      Shortcut shortcut = CustomShortcutSet.fromString("control " + ch).getShortcuts()[0];
      if (shortcut.equals(recentFiles)) continue;
      shortcuts.add(shortcut);
    }
    TW_SHORTCUT = new CustomShortcutSet(shortcuts.toArray(Shortcut.EMPTY_ARRAY));

    IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(@NotNull AWTEvent event) {
        ToolWindow tw;
        if (SWITCHER != null && event instanceof KeyEvent && !SWITCHER.isPinnedMode()) {
          final KeyEvent keyEvent = (KeyEvent)event;
          if (event.getID() == KEY_RELEASED && keyEvent.getKeyCode() == CTRL_KEY) {
            ApplicationManager.getApplication().invokeLater(CHECKER, ModalityState.current());
          }
          else if (event.getID() == KEY_PRESSED && event != INIT_EVENT
                   && (tw = SWITCHER.twShortcuts.get(String.valueOf((char)keyEvent.getKeyCode()))) != null) {
            SWITCHER.myPopup.closeOk(null);
            tw.activate(null, true, true);
          }
        }
        return false;
      }
    }, null);
  }

  @NonNls private static final String SWITCHER_TITLE = "Switcher";
  @NonNls private static InputEvent INIT_EVENT;

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    boolean isNewSwitcher = false;
    synchronized (Switcher.class) {
      INIT_EVENT = e.getInputEvent();
      if (SWITCHER != null && SWITCHER.isPinnedMode()) {
        SWITCHER.cancel();
        SWITCHER = null;
      }
      if (SWITCHER == null) {
        isNewSwitcher = true;
        // Assigns SWITCHER field
        createAndShowSwitcher(project, SWITCHER_TITLE, IdeActions.ACTION_SWITCHER, false, false);
        FeatureUsageTracker.getInstance().triggerFeatureUsed(SWITCHER_FEATURE_ID);
      }
    }

    assert SWITCHER != null;
    if (!SWITCHER.isPinnedMode()) {
      if (e.getInputEvent() != null && e.getInputEvent().isShiftDown()) {
        SWITCHER.goBack();
      }
      else {
        if (isNewSwitcher && !FileEditorManagerEx.getInstanceEx(project).hasOpenedFile()) {
          SWITCHER.files.setSelectedIndex(0);
        }
        else {
          SWITCHER.goForward();
        }
      }
    }
  }

  /**
   * @deprecated Please use {@link Switcher#createAndShowSwitcher(AnActionEvent, String, boolean, boolean)}
   */
  @Deprecated
  @Nullable
  public static SwitcherPanel createAndShowSwitcher(@NotNull AnActionEvent e, @NotNull String title, boolean pinned, @Nullable final VirtualFile[] vFiles) {
    return createAndShowSwitcher(e, title, "RecentFiles", pinned, vFiles != null);
  }
  
  public static SwitcherPanel createAndShowSwitcher(@NotNull AnActionEvent e, @NotNull String title, @NotNull String actionId, boolean onlyEdited, boolean pinned) {
    Project project = e.getProject();
    if (SWITCHER != null) {
      final boolean sameShortcut = Comparing.equal(SWITCHER.myTitle, title);
      if (SWITCHER.isCheckboxMode()) {
        if (sameShortcut) {
          SWITCHER.toggleShowEditedFiles();
        }
        else {
          SWITCHER.setShowOnlyEditedFiles(onlyEdited);
        }
        return null;
      }
      else if (sameShortcut) {
        SWITCHER.goForward();
        return null;
      }
    }
    return project == null ? null : createAndShowSwitcher(project, title, actionId, onlyEdited, pinned);
  }

  @Nullable
  private static SwitcherPanel createAndShowSwitcher(@NotNull Project project,
                                                     @NotNull String title,
                                                     @NotNull String actionId,
                                                     boolean onlyEdited,
                                                     boolean pinned) {
    synchronized (Switcher.class) {
      if (SWITCHER != null) {
        SWITCHER.cancel();
      }
      SWITCHER = new SwitcherPanel(project, title, actionId, onlyEdited, pinned);
      return SWITCHER;
    }
  }

  @NotNull
  private static AnAction[] getActionsForAdvertisement() {
    return new AnAction[]{
      ActionManager.getInstance().getActionOrStub(RecentLocationsAction.RECENT_LOCATIONS_ACTION_ID)
    };
  }

  public static class SwitcherPanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener, DataProvider {
    final JBPopup myPopup;
    final JBList toolWindows;
    final JBList actionAds;
    final JBList files;
    final JPanel separator;
    final JPanel adsSeparator;
    final ToolWindowManager twManager;
    final JBCheckBox myShowOnlyEditedFilesCheckBox;
    final JLabel pathLabel = new JLabel(" ");
    final JPanel myTopPanel;
    final JPanel descriptions;
    final Project project;
    private final boolean myPinned;
    final Map<String, ToolWindow> twShortcuts;
    final Alarm myAlarm;
    final SwitcherSpeedSearch mySpeedSearch;
    final String myTitle;
    final String myActionId;

    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
        final List list = getSelectedList().getSelectedValuesList();
        if (!list.isEmpty()) {
          final List<VirtualFile> vFiles = new ArrayList<>();
          for (Object o : list) {
            if (o instanceof FileInfo) {
              vFiles.add(((FileInfo)o).first);
            }
          }
          return vFiles.isEmpty() ? null : vFiles.toArray(VirtualFile.EMPTY_ARRAY);
        }
      }
      return null;
    }


    private class MyFocusTraversalPolicy extends FocusTraversalPolicy {

      @Override
      public Component getComponentAfter(Container aContainer, Component aComponent) {
        return calculateOppositeComponent(aComponent);
      }

      @Override
      public Component getComponentBefore(Container aContainer, Component aComponent) {
        return calculateOppositeComponent(aComponent);
      }

      @Override
      public Component getFirstComponent(Container aContainer) {
        return toolWindows;
      }

      @Override
      public Component getLastComponent(Container aContainer) {
        return files;
      }

      @Override
      public Component getDefaultComponent(Container aContainer) {
        return files;
      }
    }

    private Component calculateOppositeComponent(Component component) {
      if (component == toolWindows || component == actionAds) {
        return files;
      }

      if (actionAds.getModel().getSize() == 0) {
        return toolWindows;
      }
      else if (toolWindows.getModel().getSize() == 0) {
        return actionAds;
      }

      int index = files.getSelectedIndex();
      if (index <= toolWindows.getModel().getSize()) {
        return toolWindows;
      }
      else {
        return actionAds;
      }
    }


    private void exchangeSelectionState(JBList toSelect) {
      int offsetToSelect = findComponentWithSelectionAndClearSelection();

      if (toSelect.getModel().getSize() > 0) {
        if (toSelect == actionAds) {
          offsetToSelect = Math.max(0, offsetToSelect - toolWindows.getModel().getSize() - SEPARATOR_HEIGHT_IN_ELEMENTS);
        }

        offsetToSelect = Math.min(offsetToSelect, toSelect.getModel().getSize() - 1);
        toSelect.setSelectedIndex(offsetToSelect);
        toSelect.ensureIndexIsVisible(offsetToSelect);
      }
    }

    private int findComponentWithSelectionAndClearSelection() {
      return getListsInOrder().stream()
        .filter(list -> list.getSelectedIndex() != -1)
        .map(list -> {
          int selectedIndex = list.getSelectedIndex();
          if (list == actionAds) {
            selectedIndex += toolWindows.getModel().getSize() + SEPARATOR_HEIGHT_IN_ELEMENTS;
          }
          list.clearSelection();
          return selectedIndex;
        })
        .findAny()
        .orElse(-1);
    }

    @SuppressWarnings({"ConstantConditions"})
    SwitcherPanel(@NotNull final Project project, @NotNull String title, @NotNull String actionId, boolean onlyEdited, boolean pinned) {
      setLayout(new SwitcherLayouter());
      this.project = project;
      myTitle = title;
      myActionId = actionId;
      myPinned = pinned;
      mySpeedSearch = pinned ? new SwitcherSpeedSearch() : null;

      //setFocusable(true);
      //addKeyListener(this);
      setBorder(JBUI.Borders.empty());
      setBackground(JBColor.background());
      pathLabel.setHorizontalAlignment(SwingConstants.LEFT);

      final Font font = pathLabel.getFont();
      pathLabel.setFont(font.deriveFont(Math.max(10f, font.getSize() - 4f)));

      descriptions = new JPanel(new BorderLayout());

      pathLabel.setBorder(JBUI.CurrentTheme.Advertiser.border());
      pathLabel.setForeground(JBUI.CurrentTheme.Advertiser.foreground());
      pathLabel.setBackground(JBUI.CurrentTheme.Advertiser.background());
      pathLabel.setOpaque(true);

      descriptions.setBorder(new CustomLineBorder(JBUI.CurrentTheme.Advertiser.borderColor(), JBUI.insetsTop(1)));
      descriptions.add(pathLabel, BorderLayout.CENTER);
      twManager = ToolWindowManager.getInstance(project);
      CollectionListModel<ToolWindow> twModel = new CollectionListModel<>();
      List<ActivateToolWindowAction> actions = ToolWindowsGroup.getToolWindowActions(project, true);
      List<ToolWindow> windows = ContainerUtil.newArrayList();
      for (ActivateToolWindowAction action : actions) {
        ToolWindow tw = twManager.getToolWindow(action.getToolWindowId());
        if (tw.isAvailable()) {
          windows.add(tw);
        }
      }
      twShortcuts = createShortcuts(windows);
      final Map<ToolWindow, String> map = ContainerUtil.reverseMap(twShortcuts);
      Collections.sort(windows, (o1, o2) -> StringUtil.compare(map.get(o1), map.get(o2), false));
      for (ToolWindow window : windows) {
        twModel.add(window);
      }

      final ShortcutSet shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_SWITCHER).getShortcutSet();
      final int modifiers = getModifiers(shortcutSet);
      final boolean isAlt = (modifiers & Event.ALT_MASK) != 0;
      CTRL_KEY = isAlt ? VK_ALT : VK_CONTROL;

      toolWindows = createSwitcherList(twModel, new SwitcherToolWindowsListRenderer(mySpeedSearch, map, myPinned) {
        @NotNull
        @Override
        public Component getListCellRendererComponent(@NotNull JList list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          final JComponent renderer = (JComponent)super.getListCellRendererComponent(list, value, index, selected, selected);
          if (selected) {
            return renderer;
          }
          final Color bgColor = list == mouseMoveSrc && index == mouseMoveListIndex ? ON_MOUSE_OVER_BG_COLOR : list.getBackground();
          UIUtil.changeBackGround(renderer, bgColor);
          return renderer;
        }
      }, ToolWindow::getStripeTitle);

      boolean actionAdvertisementEnabled = Experiments.isFeatureEnabled("advertise.recent.locations");
      actionAds = createSwitcherList(new CollectionListModel<>(actionAdvertisementEnabled ? getActionsForAdvertisement() : AnAction.EMPTY_ARRAY),
                                     new ActionsCellListRenderer(this) {
                                       @Override
                                       public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
                                         final JComponent renderer = (JComponent)super.getListCellRendererComponent(list, value, index, selected, selected);
                                         if (selected) {
                                           return renderer;
                                         }
                                         final Color bgColor = list == mouseMoveSrc && index == mouseMoveListIndex ? ON_MOUSE_OVER_BG_COLOR : list.getBackground();
                                         UIUtil.changeBackGround(renderer, bgColor);
                                         return renderer;
                                       }
                                     }, AnAction::getTemplateText);
      actionAds.setEmptyText("");

      JPanel leftPanel = new JBPanel<>(new VerticalFlowLayout()).withBorder(JBUI.Borders.empty(5, 5, 5, 20));
      leftPanel.add(toolWindows);
      adsSeparator = new JBPanel<>()
                      .withBorder(new CustomLineBorder(SEPARATOR_COLOR, JBUI.insets(4, 0, 4, 0)))
                      .withPreferredSize(10, 1)
                      .withBackground(toolWindows.getBackground());
      adsSeparator.setVisible(actionAdvertisementEnabled);
      leftPanel.add(adsSeparator);
      leftPanel.add(actionAds);

      bindLists(toolWindows, actionAds);

      separator = new JPanel();
      separator.setBorder(new CustomLineBorder(SEPARATOR_COLOR, JBUI.insetsLeft(1)));
      separator.setPreferredSize(JBUI.size(9, 10));
      separator.setBackground(toolWindows.getBackground());

      final Pair<List<FileInfo>, Integer> filesAndSelection = getFilesToShowAndSelectionIndex(project, collectFiles(project, onlyEdited),
                                                                                              toolWindows.getModel().getSize(), pinned);
      final int selectionIndex = filesAndSelection.getSecond();
      final CollectionListModel<FileInfo> filesModel = new CollectionListModel<>();
      for (FileInfo editor : filesAndSelection.getFirst()) {
        filesModel.add(editor);
      }

      final VirtualFilesRenderer filesRenderer = new VirtualFilesRenderer(this) {
        final JPanel myPanel = new JPanel(new BorderLayout());
        final JLabel myLabel = createPaleLabel("* ");

        {
          myPanel.setOpaque(false);
          myPanel.setBackground(UIUtil.getListBackground());
        }

        @NotNull
        @Override
        public Component getListCellRendererComponent(@NotNull JList list,
                                                      Object value,
                                                      int index,
                                                      boolean selected,
                                                      boolean hasFocus) {
          assert value instanceof FileInfo;
          final Component c = super.getListCellRendererComponent(list, value, index, selected, selected);
          final Color bg = UIUtil.getListBackground();
          final Color fg = UIUtil.getListForeground();
          myLabel.setFont(list.getFont());
          myLabel.setForeground(open ? fg : bg);

          myPanel.removeAll();
          myPanel.add(myLabel, BorderLayout.WEST);
          myPanel.add(c, BorderLayout.CENTER);

          // Note: Name=name rendered in cell, Description=path to file, as displayed in bottom panel
          myPanel.getAccessibleContext().setAccessibleName(c.getAccessibleContext().getAccessibleName());
          VirtualFile file = ((FileInfo)value).first;
          String presentableUrl = ObjectUtils.notNull(file.getParent(), file).getPresentableUrl();
          String location = FileUtil.getLocationRelativeToUserHome(presentableUrl);
          myPanel.getAccessibleContext().setAccessibleDescription(location);
          if (!selected && list == mouseMoveSrc && index == mouseMoveListIndex) {
            setBackground(ON_MOUSE_OVER_BG_COLOR);
          }
          return myPanel;
        }

        @Override
        protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
          setPaintFocusBorder(false);
          super.customizeCellRenderer(list, value, index, selected, hasFocus);
        }
      };

      final ListSelectionListener filesSelectionListener = new ListSelectionListener() {
        @Nullable
        private String getTitle2Text(@Nullable String fullText) {
          int labelWidth = pathLabel.getWidth();
          if (fullText == null || fullText.length() == 0) return " ";
          while (pathLabel.getFontMetrics(pathLabel.getFont()).stringWidth(fullText) > labelWidth) {
            int sep = fullText.indexOf(File.separatorChar, 4);
            if (sep < 0) return fullText;
            fullText = "..." + fullText.substring(sep);
          }

          return fullText;
        }

        @Override
        public void valueChanged(@NotNull final ListSelectionEvent e) {
          ApplicationManager.getApplication().invokeLater(this::updatePathLabel);
        }

        private void updatePathLabel() {
          Object[] values = files.getSelectedValues();
          if (values != null && values.length == 1) {
            VirtualFile file = ((FileInfo)values[0]).first;
            String presentableUrl = ObjectUtils.notNull(file.getParent(), file).getPresentableUrl();
            pathLabel.setText(getTitle2Text(FileUtil.getLocationRelativeToUserHome(presentableUrl)));
          }
          else {
            pathLabel.setText(" ");
          }
        }
      };

      files = createSwitcherList(filesModel, filesRenderer, FileInfo::getNameForRendering);
      files.getSelectionModel().addListSelectionListener(filesSelectionListener);
      files.setBorder(JBUI.Borders.empty(5));
      ScrollingUtil.installActions(files);

      myShowOnlyEditedFilesCheckBox = new MyCheckBox(actionId, onlyEdited);
      myTopPanel = createTopPanel(myShowOnlyEditedFilesCheckBox,
                                  isCheckboxMode() ? IdeBundle.message("title.popup.recent.files") : title,
                                  pinned);
      if (isCheckboxMode()) {
        myShowOnlyEditedFilesCheckBox.addActionListener(e -> setShowOnlyEditedFiles(myShowOnlyEditedFilesCheckBox.isSelected()));
      }
      else {
        myShowOnlyEditedFilesCheckBox.setEnabled(false);
        myShowOnlyEditedFilesCheckBox.setVisible(false);
      }

      this.add(myTopPanel, BorderLayout.NORTH);
      this.add(leftPanel, BorderLayout.WEST);
      if (filesModel.getSize() > 0) {
        files.setAlignmentY(1f);
        final JScrollPane pane = ScrollPaneFactory.createScrollPane(files, true);
        pane.setPreferredSize(new Dimension(Math.max(myTopPanel.getPreferredSize().width - toolWindows.getPreferredSize().width,
                                                     files.getPreferredSize().width),
                                            20 * 20));
        this.add(pane, BorderLayout.EAST);
        if (selectionIndex > -1) {
          files.setSelectedIndex(selectionIndex);
        }
        this.add(separator, BorderLayout.CENTER);
      }
      this.add(descriptions, BorderLayout.SOUTH);

      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, filesModel.getSize() > 0 ? files : toolWindows)
        .setResizable(pinned)
        .setModalContext(false)
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelOnOtherWindowOpen(true)
        .setMovable(pinned)
        .setMinSize(new Dimension(myTopPanel.getMinimumSize().width, MINIMUM_HEIGHT))
        .setCancelKeyEnabled(false)
        .setCancelCallback(() -> {
          Container popupFocusAncestor = getPopupFocusAncestor();
          if (popupFocusAncestor != null) popupFocusAncestor.setFocusTraversalPolicy(null);
          SWITCHER = null;
          return true;
        }).createPopup();

      if (isPinnedMode()) {
        new DumbAwareAction(null, null, null) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
              mySpeedSearch.hidePopup();
              Object[] elements = mySpeedSearch.getAllElements();
              if (elements != null && elements.length > 0) {
                mySpeedSearch.selectElement(elements[0], "");
              }
            }
            else {
              myPopup.cancel();
            }
          }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), this, myPopup);
      }
      new DumbAwareAction(null, null, null) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          //suppress all actions to activate a toolwindow : IDEA-71277
        }
      }.registerCustomShortcutSet(TW_SHORTCUT, this, myPopup);

      Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window == null) {
        window = WindowManager.getInstance().getFrame(project);
      }
      myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myPopup);
      IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
      myPopup.showInCenterOf(window);

      Container popupFocusAncestor = getPopupFocusAncestor();
      popupFocusAncestor.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

      addFocusTraversalKeys(popupFocusAncestor, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, "RIGHT");
      addFocusTraversalKeys(popupFocusAncestor, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, "LEFT");
      addFocusTraversalKeys(popupFocusAncestor, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, "control RIGHT");
      addFocusTraversalKeys(popupFocusAncestor, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, "control LEFT");
    }

    private <T> JBList<T> createSwitcherList(@NotNull CollectionListModel<T> model,
                                             @NotNull ColoredListCellRenderer<T> cellRenderer,
                                             @NotNull Function<? super T, String> filteringNamer) {
      JBList list = new JBList(model);
      list.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          super.focusGained(e);
          exchangeSelectionState(list);
        }
      });

      if (isPinnedMode()) {
        new NameFilteringListModel<>(list, filteringNamer,
                                     s -> !mySpeedSearch.isPopupActive()
                                          || StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())
                                          || mySpeedSearch.getComparator().matchingFragments(mySpeedSearch.getEnteredPrefix(), s) != null,
                                     mySpeedSearch);
      }

      list.setSelectionMode(isPinnedMode() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
      list.setCellRenderer(cellRenderer);
      list.addKeyListener(this);
      list.addMouseListener(this);
      list.addMouseMotionListener(this);
      ScrollingUtil.ensureSelectionExists(list);

      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (isPinnedMode() && (e.isControlDown() || e.isMetaDown() || e.isShiftDown())) return false;
          final Object source = e.getSource();
          if (source instanceof JList) {
            JList jList = (JList)source;
            if (jList.getSelectedIndex() == -1 && jList.getAnchorSelectionIndex() != -1) {
              jList.setSelectedIndex(jList.getAnchorSelectionIndex());
            }
            if (jList.getSelectedIndex() != -1) {
              navigate(e);
            }
          }
          return true;
        }
      }.installOn(list);
      list.getSelectionModel().addListSelectionListener(e -> {
        if (!list.isSelectionEmpty()) {
          getListsInOrder().stream()
            .filter(other -> list != other)
            .forEach(other -> other.getSelectionModel().clearSelection());
        }
      });

      list.addKeyListener(ArrayUtil.getLastElement(getKeyListeners()));
      KeymapUtil.reassignAction(list, getKeyStroke(VK_UP, 0), getKeyStroke(VK_UP, CTRL_DOWN_MASK), WHEN_FOCUSED, false);
      KeymapUtil.reassignAction(list, getKeyStroke(VK_DOWN, 0), getKeyStroke(VK_DOWN, CTRL_DOWN_MASK), WHEN_FOCUSED, false);
      return list;
    }

    private void bindLists(@NotNull JBList topList, @NotNull JBList bottomList) {
      BiConsumer<AnActionEvent, Integer> scrollingAction = (e, delta) -> {
        JBList selectedList = getSelectedList();
        JBList otherList = selectedList == topList ? bottomList : topList;
        int index = selectedList.getSelectedIndex() + delta;
        if ((index >= 0 && index < selectedList.getModel().getSize()) || otherList.getModel().getSize() == 0) {
          if (delta > 0) {
            ScrollingUtil.moveDown(selectedList, 0);
          }
          else {
            ScrollingUtil.moveUp(selectedList, 0);
          }
        }
        else {
          int newIndex = index < 0 ? otherList.getModel().getSize() - 1 : 0;

          selectedList.clearSelection();
          ScrollingUtil.selectItem(otherList, newIndex);
          ScrollingUtil.ensureIndexIsVisible(otherList, newIndex, delta);
          otherList.requestFocusInWindow();
        }
      };

      for (JList list : Arrays.asList(topList, bottomList)) {
        ScrollingUtil.installActions(list);
        List<AnAction> actions = new ArrayList<>(ActionUtil.getActions(list));
        for (AnAction action : actions) {
          if (CommonShortcuts.getMoveUp().equals(action.getShortcutSet())
              || CommonShortcuts.getMoveDown().equals(action.getShortcutSet())) {
            action.unregisterCustomShortcutSet(list);
          }
        }
        new ScrollingUtil.ListScrollAction(CommonShortcuts.getMoveUp(), list) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            scrollingAction.accept(e, -1);
          }
        };
        new ScrollingUtil.ListScrollAction(CommonShortcuts.getMoveDown(), list) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            scrollingAction.accept(e, +1);
          }
        };
      }
    }

    private Container getPopupFocusAncestor() {
      JComponent content = myPopup.getContent();
      return content == null ? null : content.getFocusCycleRootAncestor();
    }

    @NotNull
    private static List<VirtualFile> collectFiles(@NotNull Project project, boolean onlyEdited) {
      return onlyEdited ? Arrays.asList(IdeDocumentHistory.getInstance(project).getChangedFiles())
                        : getRecentFiles(project);
    }

    @NotNull
    private static Pair<List<FileInfo>, Integer> getFilesToShowAndSelectionIndex(@NotNull Project project,
                                                                                 @NotNull List<VirtualFile> filesForInit,
                                                                                 int toolWindowsCount,
                                                                                 boolean pinned) {
      int selectionIndex = -1;
      final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
      final ArrayList<FileInfo> filesData = new ArrayList<>();
      final ArrayList<FileInfo> editors = new ArrayList<>();
      if (!pinned) {
        if (UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE) {
          for (Pair<VirtualFile, EditorWindow> pair : editorManager.getSelectionHistory()) {
            editors.add(new FileInfo(pair.first, pair.second, project));
          }
        }
      }
      if (editors.size() < 2 || pinned) {
        if (pinned && editors.size() > 1) {
          filesData.addAll(editors);
        }
        final List<VirtualFile> recentFiles = filesForInit;
        final int maxFiles = Math.max(editors.size(), recentFiles.size());
        final int minIndex = pinned ? 0 : (recentFiles.size() - Math.min(toolWindowsCount, maxFiles));
        boolean firstRecentMarked = false;
        final List<VirtualFile> selectedFiles = Arrays.asList(editorManager.getSelectedFiles());
        for (int i = recentFiles.size() - 1; i >= minIndex; i--) {
          if (pinned
              && selectedFiles.contains(recentFiles.get(i))
              && UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE) {
            continue;
          }

          final FileInfo info = new FileInfo(recentFiles.get(i), null, project);
          boolean add = true;
          if (pinned) {
            for (FileInfo fileInfo : filesData) {
              if (fileInfo.first.equals(info.first)) {
                add = false;
                break;
              }
            }
          }
          if (add) {
            filesData.add(info);
            if (!firstRecentMarked) {
              selectionIndex = filesData.size() - 1;
              if (selectionIndex != 0 || UISettings.getInstance().getEditorTabPlacement() != UISettings.TABS_NONE || !pinned || selectedFiles.isEmpty()) {
                firstRecentMarked = true;
              }
            }
          }
        }
        //if (editors.size() == 1) selectionIndex++;
        if (editors.size() == 1 && (filesData.isEmpty() || !editors.get(0).getFirst().equals(filesData.get(0).getFirst()))) {
          filesData.add(0, editors.get(0));
        }
      } else {
        for (int i = 0; i < Math.min(30, editors.size()); i++) {
          filesData.add(editors.get(i));
        }
      }

      return Pair.create(filesData, selectionIndex);
    }

    @NotNull
    private static JPanel createTopPanel(@NotNull JBCheckBox showOnlyEditedFilesCheckBox,
                                         @NotNull String title,
                                         boolean isMovable) {
      JPanel topPanel = new CaptionPanel();
      JBLabel titleLabel = new JBLabel(title);
      titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
      topPanel.add(titleLabel, BorderLayout.WEST);
      topPanel.add(showOnlyEditedFilesCheckBox, BorderLayout.EAST);

      Dimension size = topPanel.getPreferredSize();
      size.height = JBUI.scale(29);
      size.width = titleLabel.getPreferredSize().width + showOnlyEditedFilesCheckBox.getPreferredSize().width + JBUI.scale(50);
      topPanel.setPreferredSize(size);
      topPanel.setMinimumSize(size);
      topPanel.setBorder(JBUI.Borders.empty(5, 8));

      if (isMovable) {
        WindowMoveListener moveListener = new WindowMoveListener(topPanel);
        topPanel.addMouseListener(moveListener);
        topPanel.addMouseMotionListener(moveListener);
      }

      return topPanel;
    }

    private static void  addFocusTraversalKeys (Container focusCycleRoot, int focusTraversalType, String keyStroke) {
      Set<AWTKeyStroke> focusTraversalKeySet = focusCycleRoot.getFocusTraversalKeys(focusTraversalType);

      Set<AWTKeyStroke> set = new HashSet<>(focusTraversalKeySet);
      set.add(getKeyStroke(keyStroke));
      focusCycleRoot.setFocusTraversalKeys(focusTraversalType, set);
    }

    @NotNull
    private List<JBList> getListsInOrder() {
      return Arrays.asList(files, toolWindows, actionAds);
    }

    @NotNull
    private JBList getSequentList(@NotNull JBList currentList, boolean forward) {
      int delta = forward ? +1 : -1;
      List<JBList> lists = getListsInOrder();
      return lists.get((lists.indexOf(currentList) + delta + lists.size()) % lists.size());
    }

    @NotNull
    private JBList getListByElement(@NotNull Object o) {
      if (o instanceof FileInfo) {
        return files;
      }
      else if (o instanceof ToolWindow) {
        return toolWindows;
      }
      else {
        return actionAds;
      }
    }

    @Deprecated
    @NotNull
    protected List<VirtualFile> getFiles(@NotNull Project project) {
      throw new UnsupportedOperationException("deprecated");
    }

    @NotNull
    private static List<VirtualFile> getRecentFiles(@NotNull Project project) {
      List<VirtualFile> recentFiles = EditorHistoryManager.getInstance(project).getFileList();
      VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();

      Set<VirtualFile> recentFilesSet = ContainerUtil.newHashSet(recentFiles);
      Set<VirtualFile> openFilesSet = ContainerUtil.newHashSet(openFiles);

      // Add missing FileEditor tabs right after the last one, that is available via "Recent Files"
      int index = 0;
      for (int i = 0; i < recentFiles.size(); i++) {
        if (openFilesSet.contains(recentFiles.get(i))) {
          index = i;
          break;
        }
      }

      List<VirtualFile> result = new ArrayList<>(recentFiles);
      result.addAll(index, ContainerUtil.filter(openFiles, it -> !recentFilesSet.contains(it)));
      return result;
    }

    @NotNull
    private static Map<String, ToolWindow> createShortcuts(@NotNull List<ToolWindow> windows) {
      final Map<String, ToolWindow> keymap = new HashMap<>(windows.size());
      final List<ToolWindow> otherTW = new ArrayList<>();
      for (ToolWindow window : windows) {
        int index = ActivateToolWindowAction.getMnemonicForToolWindow(((ToolWindowImpl)window).getId());
        if (index >= '0' && index <= '9') {
          keymap.put(getIndexShortcut(index - '0'), window);
        }
        else {
          otherTW.add(window);
        }
      }
      int i = 0;
      for (ToolWindow window : otherTW) {
        String bestShortcut = getSmartShortcut(window, keymap);
        if (bestShortcut != null) {
          keymap.put(bestShortcut, window);
          continue;
        }

        while (keymap.get(getIndexShortcut(i)) != null) {
          i++;
        }
        keymap.put(getIndexShortcut(i), window);
        i++;
      }
      return keymap;
    }

    @Nullable
    private static String getSmartShortcut(ToolWindow window, Map<String, ToolWindow> keymap) {
      String title = window.getStripeTitle();
      if (StringUtil.isEmpty(title))
        return null;
      for (int i = 0; i < title.length(); i++) {
        char c = title.charAt(i);
        if (Character.isUpperCase(c)) {
          String shortcut = String.valueOf(c);
          if (keymap.get(shortcut) == null)
            return shortcut;
        }
      }
      return null;
    }

    private static String getIndexShortcut(int index) {
      return StringUtil.toUpperCase(Integer.toString(index, index + 1));
    }

    private static int getModifiers(@Nullable ShortcutSet shortcutSet) {
      if (shortcutSet == null
          || shortcutSet.getShortcuts().length == 0
          || !(shortcutSet.getShortcuts()[0] instanceof KeyboardShortcut)) {
        return Event.CTRL_MASK;
      }
      return ((KeyboardShortcut)shortcutSet.getShortcuts()[0]).getFirstKeyStroke().getModifiers();
    }

    @Override
    public void keyTyped(@NotNull KeyEvent e) {
      if (e.getKeyCode() == VK_ENTER) {
        navigate(e);
      }
    }

    @Override
    public void keyReleased(@NotNull KeyEvent e) {
      boolean ctrl = e.getKeyCode() == CTRL_KEY;
      if ((ctrl && isAutoHide()) || e.getKeyCode() == VK_ENTER) {
        navigate(e);
      }
    }

    KeyEvent lastEvent;

    @Override
    public void keyPressed(@NotNull KeyEvent e) {
      if (mySpeedSearch != null && mySpeedSearch.isPopupActive() || lastEvent == e) return;
      lastEvent = e;
      switch (e.getKeyCode()) {
        case VK_DELETE:
        case VK_BACK_SPACE: // Mac users
        case VK_Q:
          closeTabOrToolWindow();
          break;
        case VK_ESCAPE:
          cancel();
          break;
      }
    }

    private void closeTabOrToolWindow() {
      final JBList selectedList = getSelectedList();
      final int[] selected = selectedList.getSelectedIndices();
      Arrays.sort(selected);
      int selectedIndex = 0;
      for (int i = selected.length - 1; i >= 0; i--) {
        selectedIndex = selected[i];
        Object value = selectedList.getModel().getElementAt(selectedIndex);
        if (value instanceof FileInfo) {
          final FileInfo info = (FileInfo)value;
          final VirtualFile virtualFile = info.first;
          final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
          final JList jList = getSelectedList();
          final EditorWindow wnd = findAppropriateWindow(info);
          if (wnd == null) {
            editorManager.closeFile(virtualFile, false, false);
          }
          else {
            editorManager.closeFile(virtualFile, wnd, false);
          }

          final IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
          myAlarm.cancelAllRequests();
          myAlarm.addRequest(() -> {
            JComponent focusTarget = selectedList;
            if (selectedList.getModel().getSize() == 0) {
              focusTarget = selectedList == files ? toolWindows : files;
            }
            focusManager.requestFocus(focusTarget, true);
          }, 300);
          if (jList.getModel().getSize() == 1) {
            removeElementAt(jList, selectedIndex);
            this.remove(jList);
            this.remove(separator);
            final Dimension size = toolWindows.getSize();
            myPopup.setSize(new Dimension(size.width, myPopup.getSize().height));
          }
          else {
            removeElementAt(jList, selectedIndex);
            jList.setSize(jList.getPreferredSize());
          }
          if (isPinnedMode()) {
            EditorHistoryManager.getInstance(project).removeFile(virtualFile);
          }
        }
        else if (value instanceof ToolWindow) {
          final ToolWindow toolWindow = (ToolWindow)value;
          if (twManager instanceof ToolWindowManagerImpl) {
            ToolWindowManagerImpl manager = (ToolWindowManagerImpl)twManager;
            manager.hideToolWindow(((ToolWindowImpl)toolWindow).getId(), false, false);
          }
          else {
            toolWindow.hide(null);
          }
        }
      }
      pack();
      myPopup.getContent().revalidate();
      myPopup.getContent().repaint();
      if (getSelectedList().getModel().getSize() > selectedIndex) {
        getSelectedList().setSelectedIndex(selectedIndex);
        getSelectedList().ensureIndexIsVisible(selectedIndex);
      }
    }

    private static void removeElementAt(@NotNull JList jList, int index) {
      ListModel model = jList.getModel();
      if (model instanceof FilteringListModel) {
        model = ((FilteringListModel)model).getOriginalModel();
      }

      if (model instanceof CollectionListModel) {
        ((CollectionListModel)model).remove(index);
      }
      else if (model instanceof DefaultListModel) {
        ((DefaultListModel)model).remove(index);
      }
      else {
        throw new IllegalArgumentException("Wrong list model " + model.getClass());
      }
    }

    private void pack() {
      this.setSize(this.getPreferredSize());
      final JRootPane rootPane = SwingUtilities.getRootPane(this);
      Container container = this;
      do {
        container = container.getParent();
        container.setSize(container.getPreferredSize());
      }
      while (container != rootPane);
      container.getParent().setSize(container.getPreferredSize());
    }

    private void cancel() {
      myPopup.cancel();
    }

    public void go(boolean forward) {
      final int delta = forward ? +1 : -1;
      final JBList selectedList = getSelectedList();

      JBList list = selectedList;
      int index = selectedList.getSelectedIndex() + delta;
      do {
        if (index >= 0 && index < list.getModel().getSize()) {
          // success
          break;
        }
        list = getSequentList(list, forward);
        index = forward ? 0 : list.getModel().getSize() - 1;
      } while (list != selectedList);

      list.setSelectedIndex(index);
      list.ensureIndexIsVisible(index);
      if (selectedList != list) {
        IdeFocusManager.findInstanceByComponent(list).requestFocus(list, true);
      }
    }

    public void goForward() {
      go(true);
    }

    public void goBack() {
      go(false);
    }

    public JBList getSelectedList() {
      return getSelectedList(files);
    }

    @Nullable
    JBList getSelectedList(@Nullable JBList preferable) {
      return ObjectUtils.coalesce(ContainerUtil.find(getListsInOrder(), JList::hasFocus), preferable);
    }

    boolean isCheckboxMode() {
      return isPinnedMode() && Experiments.isFeatureEnabled("recent.and.edited.files.together");
    }

    void toggleShowEditedFiles() {
      myShowOnlyEditedFilesCheckBox.doClick();
    }

    void setShowOnlyEditedFiles(boolean onlyEdited) {
      if (myShowOnlyEditedFilesCheckBox.isSelected() != onlyEdited) {
        myShowOnlyEditedFilesCheckBox.setSelected(onlyEdited);
      }
      
      final boolean listWasSelected = files.getSelectedIndex() != -1;
      
      final Pair<List<FileInfo>, Integer> filesAndSelection = getFilesToShowAndSelectionIndex(
        project, collectFiles(project, onlyEdited), toolWindows.getModel().getSize(), isPinnedMode());
      final int selectionIndex = filesAndSelection.getSecond();

      final ListModel model = files.getModel();
      if (model instanceof CollectionListModel) {
        ((CollectionListModel)model).replaceAll(filesAndSelection.getFirst());
      }
      else if (model instanceof NameFilteringListModel) {
        ((NameFilteringListModel)model).replaceAll(filesAndSelection.getFirst());
      }

      if (selectionIndex > -1 && listWasSelected) {
        files.setSelectedIndex(selectionIndex);
      }
      files.revalidate();
      files.repaint();
    }

    void navigate(final InputEvent e) {
      final boolean openInNewWindow = e != null && e.isShiftDown() && e instanceof KeyEvent && ((KeyEvent)e).getKeyCode() == VK_ENTER;
      final Object[] values = getSelectedList().getSelectedValues();
      final String searchQuery = mySpeedSearch != null ? mySpeedSearch.getEnteredPrefix() : null;
      myPopup.cancel(null);
      if (values.length == 0) {
        tryToOpenFileSearch(e, searchQuery);
      }
      else if (values[0] instanceof ToolWindow) {
        final ToolWindow toolWindow = (ToolWindow)values[0];
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> toolWindow.activate(null, true, true),
                                                                    ModalityState.current());
      }
      else if (values[0] instanceof AnAction) {
        AnAction action = ((AnAction)values[0]);
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
          () -> DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(
            dataContext -> ActionUtil.invokeAction(action, dataContext, ActionPlaces.POPUP, null, null)),
          ModalityState.current());
      }
      else {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
          final FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);
          for (Object value : values) {
            if (value instanceof FileInfo) {
              final FileInfo info = (FileInfo)value;

              VirtualFile file = info.first;
              if (openInNewWindow) {
                manager.openFileInNewWindow(file);
              }
              else if (info.second != null) {
                EditorWindow wnd = findAppropriateWindow(info);
                if (wnd != null) {
                  manager.openFileImpl2(wnd, file, true);
                  manager.addSelectionRecord(file, wnd);
                }
              }
              else {
                UISettingsState settings = UISettings.getInstance().getState();
                boolean oldValue = settings.getReuseNotModifiedTabs();
                settings.setReuseNotModifiedTabs(false);
                manager.openFile(file, true, true);
                if (oldValue) {
                  CommandProcessor.getInstance().executeCommand(project, () -> settings.setReuseNotModifiedTabs(true), "", null);
                }
              }
            }
          }
        }, ModalityState.current());
      }
    }

    private void tryToOpenFileSearch(final InputEvent e, final String fileName) {
      AnAction gotoFile = ActionManager.getInstance().getAction("GotoFile");
      if (gotoFile != null && !StringUtil.isEmpty(fileName)) {
        myPopup.cancel();
        final AnAction action = gotoFile;
        ApplicationManager.getApplication().invokeLater(() -> DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)context -> {
          final DataContext dataContext = dataId -> {
            if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
              return fileName;
            }
            return context.getData(dataId);
          };
          final AnActionEvent event =
            new AnActionEvent(e, dataContext, ActionPlaces.EDITOR_POPUP, new PresentationFactory().getPresentation(action),
                              ActionManager.getInstance(), 0);
          action.actionPerformed(event);
        }), ModalityState.current());
      }
    }

    @Nullable
    private static EditorWindow findAppropriateWindow(@NotNull FileInfo info) {
      if (info.second == null) return null;
      final EditorWindow[] windows = info.second.getOwner().getWindows();
      return ArrayUtil.contains(info.second, windows) ? info.second : windows.length > 0 ? windows[0] : null;
    }

    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
    }

    private boolean mouseMovedFirstTime = true;
    private JList mouseMoveSrc = null;
    private int mouseMoveListIndex = -1;

    @Override
    public void mouseMoved(@NotNull MouseEvent e) {
      if (mouseMovedFirstTime) {
        mouseMovedFirstTime = false;
        return;
      }
      final Object source = e.getSource();
      boolean changed = false;
      if (source instanceof JList) {
        JList list = (JList)source;
        int index = list.locationToIndex(e.getPoint());
        if (0 <= index && index < list.getModel().getSize()) {
          mouseMoveSrc = list;
          mouseMoveListIndex = index;
          changed = true;
        }
      }
      if (!changed) {
        mouseMoveSrc = null;
        mouseMoveListIndex = -1;
      }

      repaintLists();
    }

    private void repaintLists() {
      getListsInOrder().forEach(JList::repaint);
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      mouseMoveSrc = null;
      mouseMoveListIndex = -1;
      repaintLists();
    }

    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
    }

    private class SwitcherSpeedSearch extends SpeedSearchBase<SwitcherPanel> implements PropertyChangeListener {
      private Object[] myElements;

      SwitcherSpeedSearch() {
        super(SwitcherPanel.this);
        addChangeListener(this);
        setComparator(new SpeedSearchComparator(false, true));
      }

      @Override
      protected void processKeyEvent(@NotNull final KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (keyCode == VK_ENTER) {
          SWITCHER.navigate(e);
          e.consume();
          return;
        }
        if (keyCode == VK_LEFT || keyCode == VK_RIGHT) {
          return;
        }
        super.processKeyEvent(e);
      }

      @Override
      protected int getSelectedIndex() {
        List<JBList> lists = getListsInOrder();
        int accumulated = 0;

        for (JBList list : lists) {
          if (getSelectedList() == list) {
            return accumulated + list.getSelectedIndex();
          }
          accumulated += list.getModel().getSize();
        }
        throw new AssertionError("At least some list must be selected internally");
      }

      @NotNull
      @Override
      protected Object[] getAllElements() {
        List<JBList> lists = getListsInOrder();
        return lists.stream()
          .map(JList::getModel)
          .flatMap(model -> IntStream.range(0, model.getSize()).mapToObj(model::getElementAt))
          .toArray();
      }


      @Override
      protected String getElementText(Object element) {
        if (element instanceof ToolWindow) {
          return ((ToolWindow)element).getStripeTitle();
        }
        else if (element instanceof FileInfo) {
          return ((FileInfo)element).getNameForRendering();
        }
        else if (element instanceof AnAction) {
          return ((AnAction)element).getTemplateText();
        }
        return "";
      }

      @Override
      protected void selectElement(final Object element, String selectedText) {
        getListsInOrder().forEach(JList::clearSelection);
        if (element == null) {
          return;
        }
        JBList selectedList = getListByElement(element);
        selectedList.clearSelection();
        selectedList.setSelectedValue(element, true);
        selectedList.requestFocusInWindow();
      }

      @Nullable
      @Override
      protected Object findElement(String s) {
        final List<SpeedSearchObjectWithWeight> elements = SpeedSearchObjectWithWeight.findElement(s, this);
        return elements.isEmpty() ? null : elements.get(0).node;
      }

      @Override
      public void propertyChange(@NotNull PropertyChangeEvent evt) {
        if (project.isDisposed()) {
          myPopup.cancel();
          return;
        }
        ((NameFilteringListModel)files.getModel()).refilter();
        ((NameFilteringListModel)toolWindows.getModel()).refilter();
        ((NameFilteringListModel)actionAds.getModel()).refilter();

        int filesSize = files.getModel().getSize();
        int twSize = toolWindows.getModel().getSize();
        int adsSize = actionAds.getModel().getSize();

        adsSeparator.setVisible(twSize != 0 && adsSize != 0);

        toolWindows.setEmptyText(filesSize != 0 && adsSize == 0 ? StatusText.DEFAULT_EMPTY_TEXT
                                                                : "");
        files.setEmptyText(twSize + adsSize == 0 ? "Press 'Enter' to search in Project"
                                                 : StatusText.DEFAULT_EMPTY_TEXT);

        refreshSelection();
      }
    }

    public boolean isAutoHide() {
      return !myPinned;
    }

    public boolean isPinnedMode() {
      return myPinned;
    }

    private class SwitcherLayouter extends BorderLayout {
      private Rectangle sBounds;
      private Rectangle tBounds;
      private Rectangle fBounds;
      private Rectangle dBounds;
      private Rectangle headerBounds;

      @Override
      public void layoutContainer(@NotNull Container target) {
        final JScrollPane scrollPane = UIUtil.getParentOfType(JScrollPane.class, files);
        JComponent filesPane = scrollPane != null ? scrollPane : files;
        if (sBounds == null || !target.isShowing()) {
          super.layoutContainer(target);
          sBounds = separator.getBounds();
          tBounds = toolWindows.getBounds();
          fBounds = filesPane.getBounds();
          dBounds = descriptions.getBounds();
          headerBounds = myTopPanel.getBounds();
        }
        else {
          final int h = target.getHeight();
          final int w = target.getWidth();
          sBounds.height = h - dBounds.height - headerBounds.height;
          tBounds.height = h - dBounds.height - headerBounds.height;
          fBounds.height = h - dBounds.height - headerBounds.height;
          fBounds.width = w - fBounds.x + JBUI.scale(10);
          dBounds.width = w;
          headerBounds.width = w;
          dBounds.y = h - dBounds.height;
          separator.setBounds(sBounds);
          toolWindows.setBounds(tBounds);
          filesPane.setBounds(fBounds);
          descriptions.setBounds(dBounds);
          myTopPanel.setBounds(headerBounds);
        }
      }
    }
  }

  private static class MyCheckBox extends JBCheckBox {
    private MyCheckBox(@NotNull String actionId, boolean selected) {
      super(layoutText(actionId), selected);
      setOpaque(false);
      setFocusable(false);
    }

    private static String layoutText(@NotNull String actionId) {
      ShortcutSet shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionId);
      return "<html>"
             + IdeBundle.message("recent.files.checkbox.label")
             + " <font color=\"" + SHORTCUT_HEX_COLOR + "\">"
             + KeymapUtil.getShortcutsText(shortcuts.getShortcuts()) + "</font>"
             + "</html>";
    }
  }
  
  private static class VirtualFilesRenderer extends ColoredListCellRenderer {
    private final SwitcherPanel mySwitcherPanel;
    boolean open;

    VirtualFilesRenderer(@NotNull SwitcherPanel switcherPanel) {
      mySwitcherPanel = switcherPanel;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof FileInfo) {
        Project project = mySwitcherPanel.project;
        VirtualFile virtualFile = ((FileInfo)value).getFirst();
        String renderedName = ((FileInfo)value).getNameForRendering();
        setIcon(IconUtil.getIcon(virtualFile, Iconable.ICON_FLAG_READ_STATUS, project));

        FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
        open = FileEditorManager.getInstance(project).isFileOpen(virtualFile);

        boolean hasProblem = WolfTheProblemSolver.getInstance(project).isProblemFile(virtualFile);
        TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, hasProblem ? JBColor.red : null, EffectType.WAVE_UNDERSCORE, Font.PLAIN);
        append(renderedName, SimpleTextAttributes.fromTextAttributes(attributes));

        // calc color the same way editor tabs do this, i.e. including EPs
        Color color = EditorTabPresentationUtil.getFileBackgroundColor(project, virtualFile);

        if (!selected && color != null) {
          setBackground(color);
        }
        SpeedSearchUtil.applySpeedSearchHighlighting(mySwitcherPanel, this, false, selected);
      }
    }
  }

  private static class ActionsCellListRenderer extends ColoredListCellRenderer {
    private final SwitcherPanel mySwitcherPanel;

    ActionsCellListRenderer(@NotNull SwitcherPanel switcherPanel) {
      mySwitcherPanel = switcherPanel;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      setPaintFocusBorder(false);
      if (value instanceof AnAction) {
        append(StringUtil.notNullize(((AnAction)value).getTemplateText()));

        SpeedSearchUtil.applySpeedSearchHighlighting(mySwitcherPanel, this, false, selected);
      }
    }
  }

  private static class FileInfo extends Pair<VirtualFile, EditorWindow> {
    private final Project myProject;
    private String myNameForRendering;

    FileInfo(VirtualFile first, EditorWindow second, Project project) {
      super(first, second);
      myProject = project;
    }

    String getNameForRendering() {
      if (myNameForRendering == null) {
        // Recently changed files would also be taken into account (not only open 'visible' files)
        myNameForRendering = EditorTabPresentationUtil.getUniqueEditorTabTitle(myProject, first, second);
      }
      return myNameForRendering;
    }
  }

  @NotNull
  public static JLabel createPaleLabel(@NotNull String text) {
    return new JLabel(text) {
      @Override
      protected void paintComponent(@NotNull Graphics g) {
        GraphicsConfig config = new GraphicsConfig(g);
        ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        super.paintComponent(g);
        config.restore();
      }
    };
  }
}
