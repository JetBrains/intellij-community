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
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.gotoByName.*;
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
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAware;
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
import com.intellij.openapi.wm.WindowManager;
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
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.*;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  public static final int SEARCH_FIELD_COLUMNS = 25;
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
  private TitleIndexes myTitleIndexes;
  private Map<String, String> myConfigurables = new HashMap<String, String>();

  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private Alarm myUpdateAlarm = new Alarm(ApplicationManager.getApplication());
  private JBList myList = new JBList(myListModel);
  private AnActionEvent myActionEvent;
  private Component myContextComponent;
  private CalcThread myCalcThread;
  private static AtomicBoolean ourPressed = new AtomicBoolean(false);
  private static AtomicBoolean ourReleased = new AtomicBoolean(false);
  private static AtomicBoolean ourOtherKeyWasPressed = new AtomicBoolean(false);
  private static AtomicLong ourLastTimePressed = new AtomicLong(0);
  private ArrayList<VirtualFile> myAlreadyAddedFiles = new ArrayList<VirtualFile>();

  static {
    IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(AWTEvent event) {
        if (event instanceof KeyEvent) {
          final KeyEvent keyEvent = (KeyEvent)event;
          final int keyCode = keyEvent.getKeyCode();

          if (keyCode == KeyEvent.VK_SHIFT) {
            if (ourOtherKeyWasPressed.get() && System.currentTimeMillis() - ourLastTimePressed.get() < 300) {
              ourPressed.set(false);
              ourReleased.set(false);
              return false;
            }
            ourOtherKeyWasPressed.set(false);
            if (System.currentTimeMillis() - ourLastTimePressed.get() > 300) {
              ourPressed.set(false);
              ourReleased.set(false);
            }
            if (event.getID() == KeyEvent.KEY_PRESSED) {
              if (!ourPressed.get()) {
                ourPressed.set(true);
                ourLastTimePressed.set(System.currentTimeMillis());
              } else {
                if (ourPressed.get() && ourReleased.get()) {
                    ourPressed.set(false);
                    ourReleased.set(false);
                    ourLastTimePressed.set(System.currentTimeMillis());
                    final ActionManager actionManager = ActionManager.getInstance();
                    final AnAction action = actionManager.getAction("SearchEverywhere");

                    final AnActionEvent anActionEvent = new AnActionEvent(keyEvent,
                                                                          DataManager.getInstance().getDataContext(IdeFocusManager.findInstance().getFocusOwner()),
                                                                          ActionPlaces.UNKNOWN,
                                                                          action.getTemplatePresentation(),
                                                                          actionManager,
                                                                          0);
                    action.actionPerformed(anActionEvent);
                }
              }
            } else if (event.getID() == KeyEvent.KEY_RELEASED) {
              if (ourPressed.get()) {
                ourReleased.set(true);
              }
            }
            return false;
          } else {
            ourLastTimePressed.set(System.currentTimeMillis());
            ourOtherKeyWasPressed.set(true);
          }
          ourPressed.set(false);
          ourReleased.set(false);
        }
        return false;
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
          ((Graphics2D)g).setPaint(new GradientPaint(0,0, new JBColor(new Color(101, 136, 242), new Color(16, 91, 180)), 0, getHeight(),
                                                     new JBColor(new Color(44, 96, 238), new Color(16, 80, 147))));
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


  public SearchEverywhereAction() {
    myRenderer = new MyListRenderer();
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
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        onFocusLost();
      }
    });

  }

  private void initTooltip(JLabel label) {
    label.setToolTipText("<html><body>Search Everywhere<br/>Press <b>" +
                                 "Double " +
                                 (SystemInfo.isMac ? MacKeymapUtil.SHIFT : "Shift") +
                                 "</b> to access<br/> - Classes<br/> - Files<br/> - Tool Windows<br/> - Actions<br/> - Settings</body></html>");

  }

  private void initSearchField(final MySearchTextField search) {
    final JTextField editor = search.getTextEditor();
//    editor.setOpaque(false);

    editor.putClientProperty("JTextField.Search.noFocusRing", Boolean.TRUE);
    onFocusLost();
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        final int len = pattern.trim().length();
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (search.getTextEditor().hasFocus()) {
              rebuildList(pattern);
            }
          }
        }, /*Registry.intValue("ide.goto.rebuild.delay")*/ len == 1 ? 400 : len == 2 ? 300 : len == 3 ? 250 : 30);
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
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
        if ( myPopup instanceof AbstractPopup && myPopup.isVisible() && ((AbstractPopup)myPopup).getPopupWindow() == e.getOppositeComponent()) {
          return;
        }
        onFocusLost();
      }
    });

    editor.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_ESCAPE:
            if (myBalloon != null && myBalloon.isVisible()) {
              myBalloon.cancel();
            }
            if (myPopup != null && myPopup.isVisible()) {
              myPopup.cancel();
            }
            IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(editor);
            focusManager.requestDefaultFocus(true);
            break;
          case KeyEvent.VK_ENTER:
            doNavigate(myList.getSelectedIndex());
            break;
          case KeyEvent.VK_TAB:
            jumpNextGroup(!e.isShiftDown());
            break;
        }
      }
    });
  }

  private void jumpNextGroup(boolean forward) {
    final int index = myList.getSelectedIndex();
    if (index >= 0) {
      final int newIndex = forward ? myTitleIndexes.next(index) : myTitleIndexes.prev(index);
      myList.setSelectedIndex(newIndex);
      ListScrollingUtil.ensureIndexIsVisible(myList, myList.getSelectedIndex(), forward ? 1 : -1);
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
      else {
        focusManager.requestDefaultFocus(true);
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
          @Override
          public void run() {
            if (value instanceof BooleanOptionDescription) {
              final BooleanOptionDescription option = (BooleanOptionDescription)value;
              option.setOptionState(!option.isOptionEnabled());
            }
            else {
              GotoActionAction.openOptionOrPerformAction(value, pattern, project, myContextComponent, myActionEvent);
            }
          }
        });
        return;
      }
    }
    finally {
      token.finish();
      onFocusLost();
    }
    focusManager.requestDefaultFocus(true);
  }

  private boolean isMoreItem(int index) {
    return index == myMoreClassesIndex || index == myMoreFilesIndex || index == myMoreSettingsIndex || index == myMoreActionsIndex;
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
      return;
    }
    if (e == null && myFocusOwner != null) {
      e = new AnActionEvent(me, DataManager.getInstance().getDataContext(myFocusComponent), ActionPlaces.UNKNOWN, getTemplatePresentation(), ActionManager.getInstance(), 0);
    }
    if (e == null) return;
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
    initSearchField(myPopupField);
    myPopupField.setOpaque(false);
    myPopupField.getTextEditor().setColumns(SEARCH_FIELD_COLUMNS);
    final JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        ((Graphics2D)g).setPaint(new GradientPaint(0,0, new JBColor(new Color(0x6688f2), new Color(59, 81, 162)), 0, getHeight(),
                                                   new JBColor(new Color(0x2d60ee), new Color(53, 67, 134))));
        g.fillRect(0,0, getWidth(), getHeight());
      }
   };
    final JLabel title = new JLabel(" Search Everywhere:");
    title.setForeground(new JBColor(Gray._255, Gray._160));
    if (SystemInfo.isMac) {
      title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() - 1f));
    } else {
      title.setFont(title.getFont().deriveFont(Font.BOLD));
    }
    panel.add(title, BorderLayout.WEST);
    panel.add(myPopupField, BorderLayout.CENTER);
    panel.setBorder(IdeBorderFactory.createEmptyBorder(3, 5, 4, 5));
    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myPopupField.getTextEditor());
    myBalloon = builder
      .setCancelOnClickOutside(true)
      .setModalContext(false)
      .createPopup();

    final Window window = WindowManager.getInstance().suggestParentWindow(e.getProject());

    Component parent = UIUtil.findUltimateParent(window);


    final RelativePoint showPoint;
    if (me != null) {
      final Component label = me.getComponent();
      final Component button = label.getParent();
      assert button != null;
      showPoint = new RelativePoint(button, new Point(button.getWidth() - panel.getPreferredSize().width, button.getHeight()));
    } else {
      if (parent != null) {
        showPoint = new RelativePoint(parent, new Point((parent.getSize().width - panel.getPreferredSize().width)/ 2, parent.getHeight()/4));
      } else {
        showPoint = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
      }
    }
    myBalloon.show(showPoint);
    initSearchActions(myBalloon, myPopupField);
    IdeFocusManager focusManager = IdeFocusManager.getInstance(e.getProject());
    focusManager.requestFocus(myPopupField.getTextEditor(), true);
  }

  private void initSearchActions(JBPopup balloon, MySearchTextField searchTextField) {
    new AnAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        jumpNextGroup(true);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("TAB"), searchTextField.getTextEditor(), balloon);
    new AnAction(){
      @Override
      public void actionPerformed(AnActionEvent e) {
        jumpNextGroup(false);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("shift TAB"), searchTextField.getTextEditor(), balloon);
  }

  private static class MySearchTextField extends SearchTextField implements DataProvider, Disposable {
    public MySearchTextField() {
      super(false);
      if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
        getTextEditor().setOpaque(false);
      }
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
        cmp = new GotoFileCellRenderer(list.getWidth()).getListCellRendererComponent(list, file == null ? value : file, index, isSelected, cellHasFocus);
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
          final Icon icon = templatePresentation.getIcon();

          append(templatePresentation.getText());

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

        AccessToken readLock = ApplicationManager.getApplication().acquireReadActionLock();
        if (!DumbServiceImpl.getInstance(project).isDumb()) {
          try {
            buildClasses(pattern, false);
          } finally {readLock.finish();}
          updatePopup();
        }

        readLock = ApplicationManager.getApplication().acquireReadActionLock();
        try {
          buildFiles(pattern);
        } finally {readLock.finish();}

        buildActionsAndSettings(pattern);
        updatePopup();

        readLock = ApplicationManager.getApplication().acquireReadActionLock();
        try {
          buildSymbols(pattern);
        } finally {readLock.finish();}
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
          if (isToolWindowAction(object) && toolWindows.size() < 10) {
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
      final Set<AnAction> actions = new HashSet<AnAction>();
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
          if (isSetting(object) && settings.size() < 7) {
            if (matcher.matches(getSettingText((OptionDescription)object))) {
              settings.add(object);
            }
          }
          else if (!isToolWindowAction(object) && isActionValue(object) && actions.size() < 7) {
            actions.add((AnAction)((Map.Entry)object).getKey());
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
          myMoreActionsIndex = actions.size() >= 7 ? myListModel.size() - 1 : -1;
          if (settings.size() > 0) {
            myTitleIndexes.settings = myListModel.size();
            for (Object setting : settings) {
              myListModel.addElement(setting);
            }
          }
          myMoreSettingsIndex = settings.size() >= 7 ? myListModel.size() - 1 : -1;
        }
      });
    }

    private void buildFiles(String pattern) {
      int filesCounter = 0;
      if (myFiles == null) {
        myFiles = myFileModel.getNames(false);
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
          if (file != null && scope.accept(file)) {
            elements.add(o);
          }
          return elements.size() < 30;
        }
      });
      final List<Object> files = new ArrayList<Object>();
      final int maxFiles = 8;
      for (Object object : elements) {
        if (filesCounter > maxFiles) break;
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
            if (filesCounter > maxFiles) break;
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
              myMoreFilesIndex = files.size() >= maxFiles ? myListModel.size() - 1 : -1;
            }
          }
        });
      }
    }

    private void buildSymbols(String pattern) {
      int symbolCounter = 0;
      if (mySymbols == null) {
        mySymbols = mySymbolsModel.getNames(false);
      }
      List<MatchResult> matches = collectResults(pattern, mySymbols, mySymbolsModel);
      final List<Object> symbols = new ArrayList<Object>();

      final int maxFiles = 8;
      for (MatchResult o : matches) {
        if (symbolCounter > maxFiles) break;

        Object[] objects = mySymbolsModel.getElementsByName(o.elementName, false, pattern);
        for (Object object : objects) {
          if (!myListModel.contains(object)) {
              symbols.add(object);
              symbolCounter++;
              if (symbolCounter > maxFiles) break;
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
              myMoreFilesIndex = symbols.size() >= maxFiles ? myListModel.size() - 1 : -1;
            }
          }
        });
      }
    }

    private void buildClasses(String pattern, boolean includeLibraries) {
      int clsCounter = 0;
      final int maxCount = includeLibraries ? 5 : 8;
      if (myClasses == null) {
        myClasses = myClassModel.getNames(false);
      }
      List<MatchResult> matches = collectResults(pattern, includeLibraries ? myClassModel.getNames(true) : myClasses, myClassModel);
      FindSymbolParameters parameters = FindSymbolParameters.wrap(pattern, project, includeLibraries);
      final List<Object> classes = new ArrayList<Object>();

      for (MatchResult matchResult : matches) {
        if (clsCounter > maxCount) break;

        Object[] objects = myClassModel.getElementsByName(matchResult.elementName, parameters, myProgressIndicator);
        for (Object object : objects) {
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
        if (!includeLibraries) {
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
        if (files.size() > 15) break;
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
                myFocusOwner = null;
                myRenderer.myProject = null;
                myCalcThread = null;
                myPopup = null;
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
    final Dimension size = myList.getPreferredSize();
    if (size.width < parent.getWidth()) {
      size.width = parent.getWidth();
    }
    Dimension sz = new Dimension(size.width, size.height);
    if (sz.width > 800 || sz.height > 800) {
      final int extra = new JBScrollPane().getVerticalScrollBar().getWidth();
      sz = new Dimension(Math.min(800, Math.max(getField().getWidth(), size.width + extra)), Math.min(800, size.height + extra));
      sz.width += 16;
    }
    else {
      sz.height++;
      sz.height++;
      sz.width++;
      sz.width++;
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
      adjustPopup();
    }
  }

  private void adjustPopup() {
//    new PopupPositionManager.PositionAdjuster(getField().getParent()).adjust(myPopup, BOTTOM, RIGHT, LEFT, TOP);
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
