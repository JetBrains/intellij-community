/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.OnOffButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.util.*;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Konstantin Bulenkov
 */
public class SearchEverywhereAction extends AnAction implements CustomComponentAction, DumbAware{
  public static final String SE_HISTORY_KEY = "SearchEverywhereHistory";
  public static final int SEARCH_FIELD_COLUMNS = 25;
  private static final int MAX_CLASSES = 6;
  private static final int MAX_FILES = 6;
  private static final int MAX_TOOL_WINDOWS = 4;
  private static final int MAX_SYMBOLS = 6;
  private static final int MAX_SETTINGS = 5;
  private static final int MAX_ACTIONS = 5;
  private static final int MAX_RECENT_FILES = 10;
  public static final int MAX_SEARCH_EVERYWHERE_HISTORY = 50;

  private SearchEverywhereAction.MyListRenderer myRenderer;
  MySearchTextField myPopupField;
  private GotoClassModel2 myClassModel;
  private GotoFileModel myFileModel;
  private GotoActionModel myActionModel;
  private GotoSymbolModel2 mySymbolsModel;
  private String[] myClasses;
  private String[] myFiles;
  private String[] myActions;
  private String[] mySymbols;
  private Component myFocusComponent;
  private JBPopup myPopup;
  private SearchListModel myListModel = new SearchListModel();
  private int myMoreClassesIndex = -1;
  private int myMoreFilesIndex = -1;
  private int myMoreActionsIndex = -1;
  private int myMoreSettingsIndex = -1;
  private int myMoreSymbolsIndex = -1;
  private TitleIndexes myTitleIndexes;
  private Map<String, String> myConfigurables = new HashMap<String, String>();

  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private Alarm myUpdateAlarm = new Alarm(ApplicationManager.getApplication());
  private JBList myList;
  private JCheckBox myNonProjectCheckBox;
  private AnActionEvent myActionEvent;
  private Component myContextComponent;
  private CalcThread myCalcThread;
  private static AtomicBoolean shift1Pressed = new AtomicBoolean(false);
  private static AtomicBoolean shift1Released = new AtomicBoolean(false);
  private static AtomicBoolean shift2Pressed = new AtomicBoolean(false);
  private static AtomicBoolean shift2Released = new AtomicBoolean(false);
  private static AtomicBoolean ourOtherKeyWasPressed = new AtomicBoolean(false);
  private static AtomicLong ourLastTimePressed = new AtomicLong(0);
  private static AtomicBoolean showAll = new AtomicBoolean(false);
  private ArrayList<VirtualFile> myAlreadyAddedFiles = new ArrayList<VirtualFile>();
  private int myHistoryIndex = 0;

  static {
    IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(AWTEvent event) {
        if (event instanceof KeyEvent) {
          final KeyEvent keyEvent = (KeyEvent)event;
          final int keyCode = keyEvent.getKeyCode();

          if (keyCode == KeyEvent.VK_SHIFT) {
            if (keyEvent.isControlDown() || keyEvent.isAltDown() || keyEvent.isMetaDown()) {
              resetState();
              return false;
            }
            if (ourOtherKeyWasPressed.get() && System.currentTimeMillis() - ourLastTimePressed.get() < 500) {
              resetState();
              return false;
            }
            ourOtherKeyWasPressed.set(false);
            if (shift1Pressed.get() && System.currentTimeMillis() - ourLastTimePressed.get() > 500) {
              resetState();
            }
            handleShift((KeyEvent)event);
            return false;
          } else {
            ourLastTimePressed.set(System.currentTimeMillis());
            ourOtherKeyWasPressed.set(true);
            if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_TAB)  {
              ourLastTimePressed.set(0);
            }
          }
          resetState();
        }
        return false;
      }

      private void resetState() {
        shift1Pressed.set(false);
        shift1Released.set(false);
        shift2Pressed.set(false);
        shift2Released.set(false);
      }

      private void handleShift(KeyEvent event) {
        if (shift1Pressed.get() && System.currentTimeMillis() - ourLastTimePressed.get() > 300) {
          resetState();
          return;
        }

        if (event.getID() == KeyEvent.KEY_PRESSED) {
          if (!shift1Pressed.get()) {
            resetState();
            shift1Pressed.set(true);
            ourLastTimePressed.set(System.currentTimeMillis());
            return;
          } else {
            if (shift1Pressed.get() && shift1Released.get()) {
              shift2Pressed.set(true);
              ourLastTimePressed.set(System.currentTimeMillis());
              return;
            }
          }
        } else if (event.getID() == KeyEvent.KEY_RELEASED) {
          if (shift1Pressed.get() && !shift1Released.get()) {
            shift1Released.set(true);
            ourLastTimePressed.set(System.currentTimeMillis());
            return;
          } else if (shift1Pressed.get() && shift1Released.get() && shift2Pressed.get()) {
            resetState();
            run(event);
            return;
          }
        }
        resetState();
      }

      private void run(KeyEvent event) {
        final ActionManager actionManager = ActionManager.getInstance();
                  final AnAction action = actionManager.getAction(IdeActions.ACTION_SEARCH_EVERYWHERE);
                  if (KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).length > 0) {
                    return;
                  }
                  final AnActionEvent anActionEvent = new AnActionEvent(event,
                                                                        DataManager.getInstance().getDataContext(IdeFocusManager.findInstance().getFocusOwner()),
                                                                        ActionPlaces.MAIN_MENU,
                                                                        action.getTemplatePresentation(),
                                                                        actionManager,
                                                                        0);
                  action.actionPerformed(anActionEvent);
      }
    }, null);
  }

  private JBPopup myBalloon;
  private int myPopupActualWidth;
  private Component myFocusOwner;
  private ChooseByNamePopup myFileChooseByName;

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        if (myBalloon != null && !myBalloon.isDisposed() && myActionEvent != null && myActionEvent.getInputEvent() instanceof MouseEvent) {
          final Gradient gradient = getGradientColors();
          ((Graphics2D)g).setPaint(new GradientPaint(0, 0, gradient.getStartColor(), 0, getHeight(), gradient.getEndColor()));
          g.fillRect(0,0,getWidth(), getHeight());
        } else {
          super.paintComponent(g);
        }
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
        label.setIcon(AllIcons.Actions.FindWhite);
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
      new JBColor(new Color(101, 147, 242), new Color(64, 80, 94)),
      new JBColor(new Color(46, 111, 205), new Color(53, 65, 87)));
  }

  public SearchEverywhereAction() {
    updateComponents();
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        onFocusLost();
      }
    });

  }

  private void updateComponents() {
    myRenderer = new MyListRenderer();
    myList = new JBList(myListModel) {
      @Override
      public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        return new Dimension(Math.min(size.width, 800), size.height);
      }
    };
    myList.setCellRenderer(myRenderer);
    myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final int i = myList.locationToIndex(e.getPoint());
        if (i != -1) {
          myList.setSelectedIndex(i);
          doNavigate(i);
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
            myAlarm.addRequest(new Runnable() {
              @Override
              public void run() {
                if (editor.hasFocus()) {
                  rebuildList(pattern);
                }
              }
            }, 30);
          }
        }
      }
    });
  }

  private void initTooltip(JLabel label) {
    final String shortcutText;
    shortcutText = getShortcut();

    label.setToolTipText("<html><body>Search Everywhere<br/>Press <b>"
                                 + shortcutText
                                 + "</b> to access<br/> - Classes<br/> - Files<br/> - Tool Windows<br/> - Actions<br/> - Settings</body></html>");

  }

  private static String getShortcut() {
    String shortcutText;
    final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE);
    if (shortcuts.length == 0) {
      shortcutText = "Double " + (SystemInfo.isMac ? MacKeymapUtil.SHIFT : "Shift");
    } else {
      shortcutText = KeymapUtil.getShortcutsText(shortcuts);
    }
    return shortcutText;
  }

  private void initSearchField(final MySearchTextField search) {
    final JTextField editor = search.getTextEditor();
//    onFocusLost();
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        final int len = pattern.trim().length();
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (editor.hasFocus()) {
              rebuildList(pattern);
            }
          }
        }, len == 1 ? 400 : len == 2 ? 300 : len == 3 ? 250 : 30);
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      boolean skip = false;

      @Override
      public void focusGained(FocusEvent e) {
        if (skip) {
          skip = false;
          return;
        }
        search.setText("");
        search.getTextEditor().setForeground(UIUtil.getLabelForeground());
        myTitleIndexes = new TitleIndexes();
        editor.setColumns(SEARCH_FIELD_COLUMNS);
        myFocusComponent = e.getOppositeComponent();
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final JComponent parent = (JComponent)editor.getParent();
            parent.revalidate();
            parent.repaint();
          }
        });
        if (myPopup != null && myPopup.isVisible()) {
          myPopup.cancel();
          myPopup = null;
        }
        rebuildList("");
      }

      @Override
      public void focusLost(FocusEvent e) {
        if ( myPopup instanceof AbstractPopup && myPopup.isVisible()
             && ((myList == e.getOppositeComponent()) || ((AbstractPopup)myPopup).getPopupWindow() == e.getOppositeComponent())) {
          return;
        }
        if (myNonProjectCheckBox == e.getOppositeComponent()) {
          skip = true;
          editor.requestFocus();
          return;
        }
        onFocusLost();
      }
    });
  }

  private void jumpNextGroup(boolean forward) {
    final int index = myList.getSelectedIndex();
    if (index >= 0) {
      final int newIndex = forward ? myTitleIndexes.next(index) : myTitleIndexes.prev(index);
      myList.setSelectedIndex(newIndex);
      int more = myTitleIndexes.next(newIndex) - 1;
      if (more < newIndex) {
        more = myList.getItemsCount() - 1;
      }
      ListScrollingUtil.ensureIndexIsVisible(myList, more, forward ? 1 : -1);
      ListScrollingUtil.ensureIndexIsVisible(myList, newIndex, forward ? 1 : -1);
    }
  }

  private void onFocusLost() {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myCalcThread != null) {
          myCalcThread.cancel();
          myCalcThread = null;
        }
        myAlarm.cancelAllRequests();
        clearModel();
        if (myBalloon != null && !myBalloon.isDisposed() && myPopup != null && !myPopup.isDisposed()) {
          myBalloon.cancel();
          myPopup.cancel();
        }

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            ActionToolbarImpl.updateAllToolbarsImmediately();
          }
        });
      }
    });
  }

  private void clearModel() {
    myListModel.clear();
    myMoreClassesIndex = -1;
    myMoreFilesIndex = -1;
    myMoreActionsIndex = -1;
    myMoreSettingsIndex = -1;
  }

  private SearchTextField getField() {
    return myPopupField;
  }

  private void doNavigate(int index) {
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(getField().getTextEditor()));

    assert project != null;

    if (isMoreItem(index)) {
      String actionId = null;
      if (index == myMoreClassesIndex) actionId = "GotoClass";
      else if (index == myMoreFilesIndex) actionId = "GotoFile";
      else if (index == myMoreSettingsIndex) actionId = "ShowSettings";
      else if (index == myMoreActionsIndex) actionId = "GotoAction";
      else if (index == myMoreSymbolsIndex) actionId = "GotoSymbol";
      if (actionId != null) {
        final AnAction action = ActionManager.getInstance().getAction(actionId);
        GotoActionAction.openOptionOrPerformAction(action, getField().getText(), project, getField(), myActionEvent);
        if (myPopup != null && myPopup.isVisible()) {
          myPopup.cancel();
        }
        return;
      }
    }
    final String pattern = getField().getText();
    final Object value = myList.getSelectedValue();
    IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(getField().getTextEditor());
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }

    if (value instanceof BooleanOptionDescription) {
      final BooleanOptionDescription option = (BooleanOptionDescription)value;
      option.setOptionState(!option.isOptionEnabled());
      myList.revalidate();
      myList.repaint();
      return;
    }

    AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      if (value instanceof PsiElement) {
        NavigationUtil.activateFileWithPsiElement((PsiElement)value, true);
        return;
      }
      else if (isVirtualFile(value)) {
        OpenFileDescriptor navigatable = new OpenFileDescriptor(project, (VirtualFile)value);
        if (navigatable.canNavigate()) {
          navigatable.navigate(true);
          return;
        }
      }
      else if (isActionValue(value) || isSetting(value)) {
        focusManager.requestDefaultFocus(true);
        final Component comp = myContextComponent;
        final AnActionEvent event = myActionEvent;
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
          @Override
          public void run() {
            Component c = comp;
            if (c == null) {
              c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            }
            GotoActionAction.openOptionOrPerformAction(value, pattern, project, c, event);
          }
        });
        return;
      }
      else if (value instanceof Navigatable) {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
          @Override
          public void run() {
            OpenSourceUtil.navigate(true, (Navigatable)value);
          }
        });
      }
    }
    finally {
      token.finish();
      onFocusLost();
    }
    focusManager.requestDefaultFocus(true);
  }

  private boolean isMoreItem(int index) {
    return index == myMoreClassesIndex || index == myMoreFilesIndex || index == myMoreSettingsIndex || index == myMoreActionsIndex || index == myMoreSymbolsIndex;
  }

  private void rebuildList(final String pattern) {
    if (myCalcThread != null) {
      myCalcThread.cancel();
    }
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(getField().getTextEditor()));

    assert project != null;
    myRenderer.myProject = project;
    myCalcThread = new CalcThread(project, pattern);
    myCalcThread.start();
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
    if (e == null && myFocusOwner != null) {
      e = new AnActionEvent(me, DataManager.getInstance().getDataContext(myFocusOwner), ActionPlaces.UNKNOWN, getTemplatePresentation(), ActionManager.getInstance(), 0);
    }
    if (e == null) return;

    updateComponents();
    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
    final Project project = e.getProject();
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
    myNonProjectCheckBox.setForeground(new JBColor(Gray._240, Gray._200));
    myNonProjectCheckBox.setText("Include non-project items (" + getShortcut() + ")");
    topPanel.add(myNonProjectCheckBox, BorderLayout.EAST);
    panel.add(myPopupField, BorderLayout.CENTER);
    panel.add(topPanel, BorderLayout.NORTH);
    panel.setBorder(IdeBorderFactory.createEmptyBorder(3, 5, 4, 5));
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, editor);
    myBalloon = builder
      .setCancelOnClickOutside(true)
      .setModalContext(false)
      .setCancelCallback(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          final String last = editor.getText().trim();
          if (project == null || project.isDisposed() || !project.isInitialized()) {
            return true;
          }
          final PropertiesComponent storage = PropertiesComponent.getInstance(project);
          final String historyString = storage.getValue(SE_HISTORY_KEY);
          List<String> history = StringUtil.isEmpty(historyString) ? new ArrayList<String>() : StringUtil.split(historyString, "\n");
          history.remove(last);
          history.add(0, last);
          if (history.size() > MAX_SEARCH_EVERYWHERE_HISTORY) {
            history = history.subList(0, MAX_SEARCH_EVERYWHERE_HISTORY);
          }
          storage.setValue(SE_HISTORY_KEY, StringUtil.join(history, "\n"));
          return true;
        }
      })
      .createPopup();
    myBalloon.getContent().setBorder(new EmptyBorder(0,0,0,0));
    final Window window = WindowManager.getInstance().suggestParentWindow(e.getProject());

    Component parent = UIUtil.findUltimateParent(window);

    registerDataProvider(panel);
    final RelativePoint showPoint;
    if (me != null) {
      final Component label = me.getComponent();
      final Component button = label.getParent();
      assert button != null;
      showPoint = new RelativePoint(button, new Point(button.getWidth() - panel.getPreferredSize().width, button.getHeight()));
    } else {
      if (parent != null) {
        int height = UISettings.getInstance().SHOW_MAIN_TOOLBAR ? 135 : 115;
        if (parent instanceof IdeFrameImpl && ((IdeFrameImpl)parent).isInFullScreen()) {
          height -= 20;
        }
        showPoint = new RelativePoint(parent, new Point((parent.getSize().width - panel.getPreferredSize().width)/ 2, height));
      } else {
        showPoint = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
      }
    }
    myList.setFont(UIUtil.getListFont());
    myBalloon.show(showPoint);
    initSearchActions(myBalloon, myPopupField);
    IdeFocusManager focusManager = IdeFocusManager.getInstance(e.getProject());
    focusManager.requestFocus(editor, true);
    FeatureUsageTracker.getInstance().triggerFeatureUsed(IdeActions.ACTION_SEARCH_EVERYWHERE);
  }

  private void registerDataProvider(JPanel panel) {
    DataManager.registerDataProvider(panel, new DataProvider() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        final Object value = myList.getSelectedValue();
        if (CommonDataKeys.PSI_ELEMENT.is(dataId) && value instanceof PsiElement) {
          return value;
        } else if (CommonDataKeys.VIRTUAL_FILE.is(dataId) && value instanceof VirtualFile) {
          return value;
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
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), editor, balloon);
    new DumbAwareAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int index = myList.getSelectedIndex();
        if (index != -1) {
          doNavigate(index);
        }
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), editor, balloon);
    new DumbAwareAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        final PropertiesComponent storage = PropertiesComponent.getInstance(e.getProject());
        final String historyString = storage.getValue(SE_HISTORY_KEY);
        if (historyString != null) {
          final List<String> history = StringUtil.split(historyString, "\n");
          if (history.size() > myHistoryIndex) {
            final String text = history.get(myHistoryIndex);
            editor.setText(text);
            editor.setCaretPosition(text.length());
            editor.moveCaretPosition(0);
            myHistoryIndex++;
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
      super(false);
      getTextEditor().setOpaque(false);
      getTextEditor().setUI((DarculaTextFieldUI)DarculaTextFieldUI.createUI(getTextEditor()));
      getTextEditor().setBorder(new DarculaTextBorder());

      getTextEditor().putClientProperty("JTextField.Search.noBorderRing", Boolean.TRUE);
      if (UIUtil.isUnderDarcula()) {
        getTextEditor().setBackground(Gray._45);
        getTextEditor().setForeground(Gray._240);
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
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append(myLocationString, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(myLocationIcon);
      }
    };
    private String myLocationString;
    private DefaultPsiElementCellRenderer myPsiRenderer = new DefaultPsiElementCellRenderer();
    private Icon myLocationIcon;
    private Project myProject;
    private JPanel myMainPanel = new JPanel(new BorderLayout());
    private JLabel myTitle = new JLabel();

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
      PsiFile file = null;
      myLocationString = null;
      if (isMoreItem(index)) {
        cmp = More.get(isSelected);
      } else if (value instanceof VirtualFile
                 && myProject != null
                 && (((VirtualFile)value).isDirectory()
                     || (file = PsiManager.getInstance(myProject).findFile((VirtualFile)value)) != null)) {
        cmp = new GotoFileCellRenderer(Math.min(800, list.getWidth())).getListCellRendererComponent(list, file == null ? value : file, index, isSelected, cellHasFocus);
      } else if (value instanceof PsiElement) {
        cmp = myPsiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      } else {
        cmp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UIUtil.getListBackground(isSelected));
        p.add(cmp, BorderLayout.CENTER);
        cmp = p;
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
          rightComponent = myLocation.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
        panel.add(rightComponent, BorderLayout.EAST);
        cmp = panel;
      }

      Color bg = cmp.getBackground();
      cmp.setBackground(UIUtil.getListBackground(isSelected));
      if (bg == null) {
        bg = cmp.getBackground();
      }
      myMainPanel.setBorder(new CustomLineBorder(bg, 0, 0, 2, 0));
      String title = myTitleIndexes.getTitle(index);
      myMainPanel.removeAll();
      if (title != null) {
        myTitle.setText(title);
        myMainPanel.add(createTitle(" " + title), BorderLayout.NORTH);
      }
      myMainPanel.add(cmp, BorderLayout.CENTER);
      final int width = myMainPanel.getPreferredSize().width;
      if (width > myPopupActualWidth) {
        myPopupActualWidth = width;
        schedulePopupUpdate();
      }
      return myMainPanel;
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      setIcon(EmptyIcon.ICON_16);
      AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        if (value instanceof PsiElement) {
          String name = myClassModel.getElementName(value);
          assert name != null;
          append(name);
        }
        else if (isVirtualFile(value)) {
          final VirtualFile file = (VirtualFile)value;
          if (file instanceof VirtualFilePathWrapper) {
            append(((VirtualFilePathWrapper)file).getPresentablePath());
          } else {
            append(file.getName());
          }
          setIcon(IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, myProject));
        }
        else if (isActionValue(value)) {
          final Map.Entry actionWithParentGroup = value instanceof Map.Entry ? (Map.Entry)value : null;
          final AnAction anAction = actionWithParentGroup == null ? (AnAction)value : (AnAction)actionWithParentGroup.getKey();
          final Presentation templatePresentation = anAction.getTemplatePresentation();
          Icon icon = templatePresentation.getIcon();
          if (anAction instanceof ActivateToolWindowAction) {
            final String id = ((ActivateToolWindowAction)anAction).getToolWindowId();
            icon = ToolWindowManager.getInstance(myProject).getToolWindow(id).getIcon();
          }

          append(templatePresentation.getText());
          if (actionWithParentGroup != null) {
            final Object groupName = actionWithParentGroup.getValue();
            if (groupName instanceof String && StringUtil.isEmpty((String)groupName)) {
              setLocationString((String)groupName);
            }
          }

          final String groupName = actionWithParentGroup == null ? null : (String)actionWithParentGroup.getValue();
          if (!StringUtil.isEmpty(groupName)) {
            setLocationString(groupName);
          }
          if (icon != null && icon.getIconWidth() <= 16 && icon.getIconHeight() <= 16) {
            setIcon(IconUtil.toSize(icon, 16, 16));
          }
        }
        else if (isSetting(value)) {
          String text = getSettingText((OptionDescription)value);
          append(text);
          final String id = ((OptionDescription)value).getConfigurableId();
          final String name = myConfigurables.get(id);
          if (name != null) {
            setLocationString(name);
          }
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
      }
      finally {
        token.finish();
      }
    }

    public void recalculateWidth() {
      ListModel model = myList.getModel();
      myTitle.setIcon(EmptyIcon.ICON_16);
      myTitle.setFont(getTitleFont());
      int index = 0;
      while (index < model.getSize()) {
        String title = myTitleIndexes.getTitle(index);
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
    if (text.endsWith(":")) {
      text = text.substring(0, text.length() - 1);
    }
    return text;
  }

  private void schedulePopupUpdate() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        updatePopupBounds();
      }
    }, 50);
  }

  private static boolean isActionValue(Object o) {
    return o instanceof Map.Entry || o instanceof AnAction;
  }

  private static boolean isSetting(Object o) {
    return o instanceof OptionDescription;
  }

  private static boolean isVirtualFile(Object o) {
    return o instanceof VirtualFile;
  }

  private static Font getTitleFont() {
    return UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
  }

  @SuppressWarnings("SSBasedInspection")
  private class CalcThread implements Runnable {
    private final Project project;
    private final String pattern;
    private ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();

    public CalcThread(Project project, String pattern) {
      this.project = project;
      this.pattern = pattern;
    }

    @Override
    public void run() {
      try {
        myList.getEmptyText().setText("Searching...");
        //noinspection SSBasedInspection
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myTitleIndexes.clear();
            clearModel();
            myAlreadyAddedFiles.clear();
          }
        });
        if (pattern.trim().length() == 0) {
          buildModelFromRecentFiles();
          updatePopup();
          return;
        }

        checkModelsUpToDate();
        buildTopHit(pattern);
        buildRecentFiles(pattern);
        updatePopup();
        buildToolWindows(pattern);
        updatePopup();

        if (!DumbServiceImpl.getInstance(project).isDumb()) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              buildClasses(pattern, false);
            }
          });
          updatePopup();
        }

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            buildFiles(pattern);
          }
        });

        buildActionsAndSettings(pattern);
        updatePopup();


        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            buildSymbols(pattern);
          }
        });


        updatePopup();
      }
      catch (Exception ignore) {
      }
      finally {
        myList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
      }
    }

    private void buildToolWindows(String pattern) {
      if (myActions == null) {
        myActions = myActionModel.getNames(true);
      }
      final HashSet<AnAction> toolWindows = new HashSet<AnAction>();
      List<MatchResult> matches = collectResults(pattern, myActions, myActionModel);
      for (MatchResult o : matches) {
        myProgressIndicator.checkCanceled();
        Object[] objects = myActionModel.getElementsByName(o.elementName, true, pattern);
        for (Object object : objects) {
          myProgressIndicator.checkCanceled();
          if (isToolWindowAction(object) && toolWindows.size() < MAX_TOOL_WINDOWS) {
            toolWindows.add((AnAction)((Map.Entry)object).getKey());
          }
        }
      }

      myProgressIndicator.checkCanceled();

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (myProgressIndicator.isCanceled()) return;
          if (toolWindows.size() > 0) {
            myTitleIndexes.toolWindows = myListModel.size();
            for (Object toolWindow : toolWindows) {
              myListModel.addElement(toolWindow);
            }
          }
        }
      });
    }

    private void buildActionsAndSettings(String pattern) {
      final Set<Object> actions = new HashSet<Object>();
      final Set<Object> settings = new HashSet<Object>();
      final MinusculeMatcher matcher = new MinusculeMatcher("*" +pattern, NameUtil.MatchingCaseSensitivity.NONE);
      if (myActions == null) {
        myActions = myActionModel.getNames(true);
      }

      List<MatchResult> matches = collectResults(pattern, myActions, myActionModel);

      for (MatchResult o : matches) {
        myProgressIndicator.checkCanceled();
        Object[] objects = myActionModel.getElementsByName(o.elementName, true, pattern);
        for (Object object : objects) {
          myProgressIndicator.checkCanceled();
          if (isSetting(object) && settings.size() < MAX_SETTINGS) {
            if (matcher.matches(getSettingText((OptionDescription)object))) {
              settings.add(object);
            }
          }
          else if (!isToolWindowAction(object) && isActionValue(object) && actions.size() < MAX_ACTIONS) {
            actions.add(object);
          }
        }
      }

      myProgressIndicator.checkCanceled();

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          if (myProgressIndicator.isCanceled()) return;
          if (actions.size() > 0) {
            myTitleIndexes.actions = myListModel.size();
            for (Object action : actions) {
              myListModel.addElement(action);
            }
          }
          myMoreActionsIndex = actions.size() >= MAX_ACTIONS ? myListModel.size() - 1 : -1;
          if (settings.size() > 0) {
            myTitleIndexes.settings = myListModel.size();
            for (Object setting : settings) {
              myListModel.addElement(setting);
            }
          }
          myMoreSettingsIndex = settings.size() >= MAX_SETTINGS ? myListModel.size() - 1 : -1;
        }
      });
    }

    private void buildFiles(final String pattern) {
      int filesCounter = 0;
      if (myFiles == null) {
        myFiles = myFileModel.getNames(showAll.get());
      }
      final Set<Object> elements = new LinkedHashSet<Object>();
      final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
      myFileChooseByName.getProvider().filterElements(myFileChooseByName, pattern, true,
                                                      myProgressIndicator, new Processor<Object>() {
        @Override
        public boolean process(Object o) {
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
              && (showAll.get() || scope.accept(file))) {
            elements.add(o);
          }
          return elements.size() < 30;
        }
      });
      final List<Object> files = new ArrayList<Object>();
      for (Object object : elements) {
        if (filesCounter > MAX_FILES) break;
        if (!myListModel.contains(object)) {
          if (object instanceof PsiFile) {
            object = ((PsiFile)object).getVirtualFile();
          }
          if ((object instanceof VirtualFile && !myAlreadyAddedFiles.contains(object))
              || object instanceof PsiDirectory) {
            files.add(object);
            if (object instanceof VirtualFile) {
              myAlreadyAddedFiles.add((VirtualFile)object);
            }
            filesCounter++;
            if (filesCounter > MAX_FILES) break;
          }
        }
      }


      myProgressIndicator.checkCanceled();

      if (files.size() > 0) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.files = myListModel.size();
              for (Object file : files) {
                myListModel.addElement(file);
              }
              myMoreFilesIndex = files.size() >= MAX_FILES ? myListModel.size() - 1 : -1;
            }
          }
        });
      }
    }

    private void buildSymbols(String pattern) {
      int symbolCounter = 0;
      if (mySymbols == null) {
        mySymbols = mySymbolsModel.getNames(showAll.get());
      }
      List<MatchResult> matches = collectResults(pattern, mySymbols, mySymbolsModel);
      final List<Object> symbols = new ArrayList<Object>();

      for (MatchResult o : matches) {
        if (symbolCounter > MAX_SYMBOLS) break;

        Object[] objects = mySymbolsModel.getElementsByName(o.elementName, showAll.get(), pattern);
        for (Object object : objects) {
          if (!myListModel.contains(object)) {
              symbols.add(object);
              symbolCounter++;
              if (symbolCounter > MAX_SYMBOLS) break;
          }
        }
      }

      myProgressIndicator.checkCanceled();

      if (symbols.size() > 0) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.symbols = myListModel.size();
              for (Object file : symbols) {
                myListModel.addElement(file);
              }
              myMoreSymbolsIndex = symbols.size() >= MAX_SYMBOLS ? myListModel.size() - 1 : -1;
            }
          }
        });
      }
    }

    private void buildClasses(String pattern, boolean includeLibraries) {
      if (pattern.indexOf('.') != -1) {
        //todo[kb] it's not a mistake. If we search for "*.png" or "index.xml" in SearchEverywhere
        //todo[kb] we don't want to see Java classes started with Png or Xml. This approach should be reworked someday.
        return;
      }
      boolean includeLibs = includeLibraries || showAll.get();
      int clsCounter = 0;
      final int maxCount = includeLibraries ? 5 : MAX_CLASSES;
      if (myClasses == null) {
        myClasses = myClassModel.getNames(false);
      }
      List<MatchResult> matches = collectResults(pattern, includeLibs ? myClassModel.getNames(true) : myClasses, myClassModel);
//      FindSymbolParameters parameters = FindSymbolParameters.wrap(pattern, project, includeLibs);
      final List<Object> classes = new ArrayList<Object>();

      for (MatchResult matchResult : matches) {
        if (clsCounter > maxCount) break;

        Object[] objects = myClassModel.getElementsByName(matchResult.elementName, includeLibs, pattern);
        for (Object object : objects) {
          myProgressIndicator.checkCanceled();
          if (!myListModel.contains(object)) {
            if (object instanceof PsiElement) {
              VirtualFile file = PsiUtilCore.getVirtualFile((PsiElement)object);
              if (file != null) {
                if (myAlreadyAddedFiles.contains(file)) {
                  continue;
                }
                else {
                  myAlreadyAddedFiles.add(file);
                }
              }
            }
            classes.add(object);
            clsCounter++;

            if (clsCounter > maxCount) break;
          }
        }
      }

      myProgressIndicator.checkCanceled();

      if (classes.size() > 0) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.classes = myListModel.size();
              for (Object cls : classes) {
                myListModel.addElement(cls);
              }
              myMoreClassesIndex = classes.size() >= maxCount ? myListModel.size() - 1 : -1;
            }
          }
        });
      } else {
        if (!includeLibs) {
          buildClasses(pattern, true);
        }
      }
    }

    private void buildRecentFiles(String pattern) {
      final MinusculeMatcher matcher = new MinusculeMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
      final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
      for (VirtualFile file : ArrayUtil.reverseArray(EditorHistoryManager.getInstance(project).getFiles())) {
        if (StringUtil.isEmptyOrSpaces(pattern) || matcher.matches(file.getName())) {
          if (!files.contains(file)) {
            files.add(file);
          }
        }
        if (files.size() > MAX_RECENT_FILES) break;
      }

      if (files.size() > 0) {
        myAlreadyAddedFiles.addAll(files);

        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.recentFiles = myListModel.size();
              for (Object file : files) {
                myListModel.addElement(file);
              }
            }
          }
        });
      }
    }

    private void buildTopHit(String pattern) {
      final List<Object> elements = new ArrayList<Object>();
      final Consumer<Object> consumer = new Consumer<Object>() {
        @Override
        public void consume(Object o) {
          if (isSetting(o) || isVirtualFile(o) || isActionValue(o) || o instanceof PsiElement) {
            elements.add(o);
          }
        }
      };

      final ActionManager actionManager = ActionManager.getInstance();
      final List<String> actions = AbbreviationManager.getInstance().findActions(pattern);
      for (String actionId : actions) {
        consumer.consume(actionManager.getAction(actionId));
      }

      for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
        myProgressIndicator.checkCanceled();
        provider.consumeTopHits(pattern, consumer);
      }
      if (elements.size() > 0) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!myProgressIndicator.isCanceled()) {
              myTitleIndexes.topHit = myListModel.size();
              for (Object element : elements) {
                myListModel.addElement(element);
              }
            }
          }
        });
      }
    }

    private void checkModelsUpToDate() {
      if (myClassModel == null) {
        myClassModel = new GotoClassModel2(project);
        myFileModel = new GotoFileModel(project);
        myFileChooseByName = ChooseByNamePopup.createPopup(project, myFileModel, (PsiElement)null);
        project.putUserData(ChooseByNamePopup.CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, null);
        myActionModel = createActionModel();
        mySymbolsModel = new GotoSymbolModel2(project);
        myConfigurables.clear();
        fillConfigurablesIds(null, new IdeConfigurablesGroup().getConfigurables());
        fillConfigurablesIds(null, new ProjectConfigurablesGroup(project).getConfigurables());
      }
    }

    private void buildModelFromRecentFiles() {
      buildRecentFiles("");
    }

    private GotoActionModel createActionModel() {
      return new GotoActionModel(project, myFocusComponent) {
        @Override
        public boolean matches(@NotNull String name, @NotNull String pattern) {
          final AnAction anAction = ActionManager.getInstance().getAction(name);
          if (anAction == null) return true;
          return NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE).matches(
            anAction.getTemplatePresentation().getText());
        }

        @NotNull
        @Override
        public Object[] getElementsByName(String id, boolean checkBoxState, String pattern) {
          final HashMap<AnAction, String> map = new HashMap<AnAction, String>();
          final AnAction act = myActionManager.getAction(id);
          if (act != null) {
            map.put(act, myActionsMap.get(act));
            if (checkBoxState) {
              final Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
              for (AnAction action : map.keySet()) { //do not add already included actions
                ids.remove(getActionId(action));
              }
              if (ids.contains(id)) {
                final AnAction anAction = myActionManager.getAction(id);
                map.put(anAction, null);
              }
            }
          }
          Object[] objects = map.entrySet().toArray(new Map.Entry[map.size()]);
          if (Comparing.strEqual(id, SETTINGS_KEY)) {
            final Set<String> words = myIndex.getProcessedWords(pattern);
            Set<OptionDescription> optionDescriptions = null;
            final String actionManagerName = myActionManager.getComponentName();
            for (String word : words) {
              final Set<OptionDescription> descriptions = ((SearchableOptionsRegistrarImpl)myIndex).getAcceptableDescriptions(word);
              if (descriptions != null) {
                for (Iterator<OptionDescription> iterator = descriptions.iterator(); iterator.hasNext(); ) {
                  OptionDescription description = iterator.next();
                  if (actionManagerName.equals(description.getPath())) {
                    iterator.remove();
                  }
                }
                if (!descriptions.isEmpty()) {
                  if (optionDescriptions == null) {
                    optionDescriptions = descriptions;
                  }
                  else {
                    optionDescriptions.retainAll(descriptions);
                  }
                }
              }
              else {
                optionDescriptions = null;
                break;
              }
            }
            if (optionDescriptions != null && !optionDescriptions.isEmpty()) {
              Set<String> currentHits = new HashSet<String>();
              for (Iterator<OptionDescription> iterator = optionDescriptions.iterator(); iterator.hasNext(); ) {
                OptionDescription description = iterator.next();
                final String hit = description.getHit();
                if (hit == null || !currentHits.add(hit.trim())) {
                  iterator.remove();
                }
              }
              final Object[] descriptions = optionDescriptions.toArray();
              Arrays.sort(descriptions);
              objects = ArrayUtil.mergeArrays(objects, descriptions);
            }
          }
          return objects;
        }
      };
    }

    @SuppressWarnings("SSBasedInspection")
    private void updatePopup() {
      myProgressIndicator.checkCanceled();
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myProgressIndicator.checkCanceled();
          myListModel.update();
          myList.revalidate();
          myList.repaint();

          myRenderer.recalculateWidth();
          if (myPopup == null || !myPopup.isVisible()) {
            final ActionCallback callback = ListDelegationUtil.installKeyboardDelegation(getField().getTextEditor(), myList);
            final ComponentPopupBuilder builder = JBPopupFactory.getInstance()
              .createComponentPopupBuilder(new JBScrollPane(myList), null);
            myPopup = builder
              .setRequestFocus(false)
              .setCancelKeyEnabled(false)
              .setCancelCallback(new Computable<Boolean>() {
                @Override
                public Boolean compute() {
                  return myBalloon == null || myBalloon.isDisposed() || !getField().getTextEditor().hasFocus();
                }
              })
              .createPopup();
            myPopup.getContent().setBorder(new EmptyBorder(0,0,0,0));
            Disposer.register(myPopup, new Disposable() {
              @Override
              public void dispose() {
                callback.setDone();
                if (myBalloon!= null) {
                  myBalloon.cancel();
                  myBalloon = null;
                }
                myFileModel = null;
                if (myFileChooseByName != null) {
                  myFileChooseByName.close(false);
                  myFileChooseByName = null;
                }
                myClassModel = null;
                myActionModel = null;
                myActions = null;
                myFiles = null;
                myClasses = null;
                mySymbolsModel = null;
                mySymbols = null;
                myConfigurables.clear();
                myFocusComponent = null;
                myContextComponent = null;
                myFocusOwner = null;
                myRenderer.myProject = null;
                myCalcThread = null;
                myPopup = null;
                myHistoryIndex = 0;
                showAll.set(false);
                myNonProjectCheckBox.setSelected(false);
                ActionToolbarImpl.updateAllToolbarsImmediately();
                if (myActionEvent != null && myActionEvent.getInputEvent() instanceof MouseEvent) {
                  final Component component = myActionEvent.getInputEvent().getComponent();
                  if (component != null) {
                    final JLabel label = UIUtil.getParentOfType(JLabel.class, component);
                    if (label != null) {
                      label.setIcon(AllIcons.Actions.FindPlain);
                    }
                  }
                }
                myActionEvent = null;
              }
            });
            myPopup.show(new RelativePoint(getField().getParent(), new Point(0, getField().getParent().getHeight())));
            updatePopupBounds();

            ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
              @Override
              public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
                if (action instanceof TextComponentEditorAction) {
                  return;
                }
                myPopup.cancel();
              }
            }, myPopup);
          }
          else {
            myList.revalidate();
            myList.repaint();
          }
          ListScrollingUtil.ensureSelectionExists(myList);
          if (myList.getModel().getSize() > 0) {
            updatePopupBounds();
          }
        }
      });
    }

    private List<MatchResult> collectResults(String pattern, String[] names, final ChooseByNameModel model) {
      if (names == null) return Collections.emptyList();
      pattern = ChooseByNamePopup.getTransformedPattern(pattern, model);
      pattern = DefaultChooseByNameItemProvider.getNamePattern(model, pattern);
      if (model != myFileModel && !pattern.startsWith("*") && pattern.length() > 1) {
        pattern = "*" + pattern;
      }
      final ArrayList<MatchResult> results = new ArrayList<MatchResult>();
      final String p = pattern;
      MinusculeMatcher matcher = new MinusculeMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE) {
        @Override
        public boolean matches(@NotNull String name) {
          if (!(model instanceof GotoActionModel) && p.indexOf(' ') > 0 && name.trim().indexOf(' ') < 0) {
            return false;
          }
          return super.matches(name);
        }
      };
      MatchResult result;

      for (String name : names) {
        myProgressIndicator.checkCanceled();
        result = null;
        if (model instanceof CustomMatcherModel) {
          try {
            result = ((CustomMatcherModel)model).matches(name, pattern) ? new MatchResult(name, 0, true) : null;
            if (result != null && model == myActionModel) {
              ((CustomMatcherModel)model).matches(name, pattern);
            }
          }
          catch (Exception ignore) {
          }
        }
        else {
          result = matcher.matches(name) ? new MatchResult(name, matcher.matchingDegree(name), matcher.isStartMatch(name)) : null;
        }

        if (result != null) {
          results.add(result);
        }
      }

      Collections.sort(results, new Comparator<MatchResult>() {
        @Override
        public int compare(MatchResult o1, MatchResult o2) {
          return o1.compareTo(o2);
        }
      });
      return results;
    }

    public void cancel() {
      myProgressIndicator.cancel();
    }

    public void start() {
      ApplicationManager.getApplication().executeOnPooledThread(this);
    }
  }

  private void updatePopupBounds() {
    if (myPopup == null || !myPopup.isVisible()) {
      return;
    }
    final Container parent = getField().getParent();
    final Dimension size = myList.getParent().getParent().getPreferredSize();
    if (size.width < parent.getWidth()) {
      size.width = parent.getWidth();
    }
    if (myList.getItemsCount() == 0) {
      size.height = 70;
    }
    Dimension sz = new Dimension(size.width, myList.getPreferredSize().height);
    if (sz.width > 800 || sz.height > 800) {
      final JBScrollPane pane = new JBScrollPane();
      final int extraWidth = pane.getVerticalScrollBar().getWidth() + 1;
      final int extraHeight = pane.getHorizontalScrollBar().getHeight() + 1;
      sz = new Dimension(Math.min(800, Math.max(getField().getWidth(), sz.width + extraWidth)), Math.min(800, sz.height + extraHeight));
      sz.width += 20;
      sz.height+=2;
    } else {
      sz.width+=2;
      sz.height+=2;
    }
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
      myPopup.setLocation(new Point(r.x, r.y));
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
    return isActionValue(o) && (o instanceof Map.Entry && ((Map.Entry)o).getKey() instanceof ActivateToolWindowAction);
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

  static class TitleIndexes {
    int topHit;
    int recentFiles;
    int classes;
    int files;
    int actions;
    int settings;
    int toolWindows;
    int symbols;

    final String gotoClassTitle;
    final String gotoFileTitle;
    final String gotoActionTitle;
    final String gotoSettingsTitle;
    final String gotoRecentFilesTitle;
    final String gotoSymbolTitle;
    static final String toolWindowsTitle = "Tool Windows";

    TitleIndexes() {
      String gotoClass = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoClass"));
      gotoClassTitle = StringUtil.isEmpty(gotoClass) ? "Classes" : "Classes (" + gotoClass + ")";
      String gotoFile = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoFile"));
      gotoFileTitle = StringUtil.isEmpty(gotoFile) ? "Files" : "Files (" + gotoFile + ")";
      String gotoAction = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoAction"));
      gotoActionTitle = StringUtil.isEmpty(gotoAction) ? "Actions" : "Actions (" + gotoAction + ")";
      String gotoSettings = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ShowSettings"));
      gotoSettingsTitle = StringUtil.isEmpty(gotoAction) ? "Preferences" : "Preferences (" + gotoSettings + ")";
      String gotoRecentFiles = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("RecentFiles"));
      gotoRecentFilesTitle = StringUtil.isEmpty(gotoRecentFiles) ? "Recent Files" : "Recent Files (" + gotoRecentFiles + ")";
      String gotoSymbol = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoSymbol"));
      gotoSymbolTitle = StringUtil.isEmpty(gotoClass) ? "Symbols" : "Symbols (" + gotoSymbol + ")";
    }

    String getTitle(int index) {
      if (index == topHit) return index == 0 ? "Top Hit" : "Top Hits";
      if (index == recentFiles) return gotoRecentFilesTitle;
      if (index == classes) return gotoClassTitle;
      if (index == files) return gotoFileTitle;
      if (index == toolWindows) return toolWindowsTitle;
      if (index == actions) return gotoActionTitle;
      if (index == settings) return gotoSettingsTitle;
      if (index == symbols) return gotoSymbolTitle;
      return null;
    }

    int next(int index) {
      int[] all = new int[]{topHit, recentFiles, classes, files, actions, settings, toolWindows, symbols};
      Arrays.sort(all);
      for (int next : all) {
        if (next > index) return next;
      }
      return 0;
    }

    int prev(int index) {
      int[] all = new int[]{topHit, recentFiles, classes, files, actions, settings, toolWindows, symbols};
      Arrays.sort(all);
      for (int i = all.length-1; i >= 0; i--) {
        if (all[i] != -1 && all[i] < index) return all[i];
      }
      return all[all.length - 1];
    }


    public void clear() {
      topHit = -1;
      recentFiles = -1;
      classes = -1;
      files = -1;
      actions = -1;
      settings = -1;
      toolWindows = -1;
    }
  }

  @SuppressWarnings("unchecked")
  private static class SearchListModel extends DefaultListModel {
    @SuppressWarnings("UseOfObsoleteCollectionType")
    Vector myDelegate;

    private SearchListModel() {
      super();
      try {
        final Field field = DefaultListModel.class.getDeclaredField("delegate");
        field.setAccessible(true);
        myDelegate = (Vector)field.get(this);
      }
      catch (NoSuchFieldException ignore) {}
      catch (IllegalAccessException ignore) {}
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
    final Color bg = UIUtil.getListBackground();
    SeparatorComponent separatorComponent =
      new SeparatorComponent(titleLabel.getPreferredSize().height / 2, new JBColor(Gray._220, Gray._80), null);

    JPanel result = new JPanel(new BorderLayout(5, 10));
    result.add(titleLabel, BorderLayout.WEST);
    result.add(separatorComponent, BorderLayout.CENTER);
    result.setBackground(bg);

    return result;
  }

}
