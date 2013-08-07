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
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.OnOffButton;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 */
public class SearchEverywhereAction extends AnAction implements CustomComponentAction {
  private final SearchEverywhereAction.MyListRenderer myRenderer;
  SearchTextField field;
  private GotoClassModel2 myClassModel;
  private GotoFileModel myFileModel;
  private GotoActionModel myActionModel;
  private String[] myClasses;
  private String[] myFiles;
  private String[] myActions;
  private Component myFocusComponent;
  private JBPopup myPopup;
  private Map<String, String> myConfigurables = new HashMap<String, String>();

  private Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication());
  private JList myList = new JList(); //don't use JBList here!!! todo[kb]
  private AnActionEvent myActionEvent;
  private Component myContextComponent;
  private CalcThread myCalcThread;
  private static AtomicBoolean ourShiftCanBeUsed = new AtomicBoolean(false);

  static {
    IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(AWTEvent event) {
        if (event instanceof KeyEvent) {
          ourShiftCanBeUsed.set((((KeyEvent)event).getKeyCode() != KeyEvent.VK_SHIFT) || event.getID() != KeyEvent.KEY_PRESSED);
        }
        return false;
      }
    }, null);

  }

  private int myTopHitsCount;

  public SearchEverywhereAction() {
    createSearchField();
    LafManager.getInstance().addLafManagerListener(new LafManagerListener() {
      @Override
      public void lookAndFeelChanged(LafManager source) {
        createSearchField();
      }
    });
    myRenderer = new MyListRenderer();
    myList.setCellRenderer(myRenderer);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        onFocusLost(field.getTextEditor());
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(!ourShiftCanBeUsed.get() && Registry.is("search.everywhere.enabled"));
  }


  private void createSearchField() {
    field = new MySearchTextField();

    final JTextField editor = field.getTextEditor();
    onFocusLost(editor);
    editor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final String pattern = editor.getText();
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (StringUtil.isEmpty(pattern.trim())) {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (myPopup != null && myPopup.isVisible()) {
                    myPopup.cancel();
                  }
                }
              });
              return;
            }
            rebuildList(pattern);
          }
        }, 400);
      }
    });
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        field.setText("");
        field.getTextEditor().setForeground(UIUtil.getLabelForeground());

        editor.setColumns(25);
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
      }

      @Override
      public void focusLost(FocusEvent e) {
        onFocusLost(editor);
      }
    });

    editor.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_ESCAPE && (myPopup == null || !myPopup.isVisible())) {
          IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(editor);
          focusManager.requestDefaultFocus(true);
        }
        else if (keyCode == KeyEvent.VK_ENTER) {
          doNavigate();
        }
      }
    });
  }

  private void onFocusLost(final JTextField editor) {
    editor.setColumns(SystemInfo.isMac ? 5 : 8);
    field.getTextEditor().setForeground(UIUtil.getLabelDisabledForeground());
    field.setText(" " + KeymapUtil.getFirstKeyboardShortcutText(this));
    if (myCalcThread != null) {
      myCalcThread.cancel();
      myCalcThread = null;
    }
    myAlarm.cancelAllRequests();
    myList.setModel(new DefaultListModel());

    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        final JComponent parent = (JComponent)editor.getParent();
        parent.revalidate();
        parent.repaint();
      }
    });
  }

  private void doNavigate() {
    final String pattern = field.getText();
    final Object value = myList.getSelectedValue();
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(field.getTextEditor()));
    IdeFocusManager focusManager = IdeFocusManager.findInstanceByComponent(field.getTextEditor());
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }
    AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
    if (value instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement)value, true);
      return;
    } else if (isVirtualFile(value)) {
      OpenFileDescriptor navigatable = new OpenFileDescriptor(project, (VirtualFile)value);
      if (navigatable.canNavigate()) {
        navigatable.navigate(true);
        return;
      }
    } else {
      focusManager.requestDefaultFocus(true);
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
        @Override
        public void run() {
          GotoActionAction.openOptionOrPerformAction(value, pattern, project, myContextComponent ,myActionEvent);
        }
      });
      return;
    }
    } finally {
      token.finish();
    }
    focusManager.requestDefaultFocus(true);
  }

  private void rebuildList(final String pattern) {
    if (myCalcThread != null) {
      myCalcThread.cancel();
    }
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(field.getTextEditor()));

    assert project != null;
    myCalcThread = new CalcThread(project, pattern);
    myCalcThread.start();
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    IdeFocusManager focusManager = IdeFocusManager.getInstance(e.getProject());
    focusManager.requestFocus(field.getTextEditor(), true);
    myActionEvent = e;
    myContextComponent = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    return field;
  }

  private static class MySearchTextField extends SearchTextField {
    public MySearchTextField() {
      super(false);
      setOpaque(false);
      getTextEditor().setOpaque(false);
    }

    @Override
    protected void showPopup() {
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
    private Icon myLocationIcon;
    private JPanel myMainPanel = new JPanel(new BorderLayout());
    private JLabel myTitle = new JLabel();
    private JPanel myLeftPanel = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getMinimumSize() {
        return new Dimension(myLeftWidth, super.getMinimumSize().height);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(myLeftWidth, super.getPreferredSize().height);
      }
    };
    private int myLeftWidth;

    public void setLeftWidth(int width) {
      myLeftWidth = width;
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

    public void setLocationIcon(Icon locationIcon) {
      myLocationIcon = locationIcon;
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component cmp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (myLocationString != null || value instanceof BooleanOptionDescription) {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(cmp, BorderLayout.CENTER);
        final Component rightComponent;
        if (value instanceof BooleanOptionDescription) {
          final OnOffButton button = new OnOffButton();
          button.setSelected(((BooleanOptionDescription)value).isOptionEnabled());
          rightComponent = button;
        } else {
          rightComponent = myLocation.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
        panel.add(rightComponent, BorderLayout.EAST);
        cmp = panel;
      }

      cmp.setBackground(isSelected ? UIUtil.getListSelectionBackground() : getRightBackground());
      String title = getTitle(index, value, index == 0 ? null : list.getModel().getElementAt(index -1));
      myTitle.setText(title == null ? "" : title);
      myLeftPanel.removeAll();
      myLeftPanel.setBackground(Gray._242);
      myMainPanel.removeAll();
      myLeftPanel.add(myTitle, BorderLayout.EAST);
      myMainPanel.add(myLeftPanel, BorderLayout.WEST);
      myMainPanel.add(cmp, BorderLayout.CENTER);
      return myMainPanel;
      }

    private Color getRightBackground() {
      return UIUtil.isUnderAquaLookAndFeel() ? Gray._236 : UIUtil.getListBackground();
    }


    private String getTitle(int index, Object value, Object prevValue) {
      if (index == 0 && myTopHitsCount > 0) {
        return myTopHitsCount == 1 ? "Top Hit" : "Top Hits";
      }
      if (index < myTopHitsCount) {
        return null;
      }
      if (index == myTopHitsCount) {
        prevValue = null;
      }
      String gotoClass = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoClass"));
      gotoClass = StringUtil.isEmpty(gotoClass) ? "Classes" : "Classes (" + gotoClass + ")";
      String gotoFile = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoFile"));
      gotoFile = StringUtil.isEmpty(gotoFile) ? "Files" : "Files (" + gotoFile + ")";
      String gotoAction = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("GotoAction"));
      gotoAction = StringUtil.isEmpty(gotoAction) ? "Actions" : "Actions (" + gotoAction + ")";
      String gotoSettings = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ShowSettings"));
      gotoSettings = StringUtil.isEmpty(gotoAction) ? "Preferences" : "Preferences (" + gotoSettings + ")";
      String toolWindows = "Tool Windows";
      if (prevValue == null) { // firstElement
        if (value instanceof PsiElement)return gotoClass;
        if (isVirtualFile(value))return gotoFile;
        if (isToolWindowAction(value)) return toolWindows;
        if (isSetting(value)) return gotoSettings;
        if (isActionValue(value))return gotoAction;
        return gotoSettings;
      } else {
        if (!isVirtualFile(prevValue) && isVirtualFile(value)) return gotoFile;
        if (!isSetting(prevValue) && isSetting(value)) return gotoSettings;
        if (!isToolWindowAction(prevValue) && isToolWindowAction(value)) return toolWindows;
        if ((!isActionValue(prevValue) || isToolWindowAction(prevValue)) && isActionValue(value)) return gotoAction;
      }
      return null;
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
          append(((VirtualFile)value).getName());
        } else if (isActionValue(value)) {
          final Map.Entry actionWithParentGroup = value instanceof Map.Entry ? (Map.Entry)value : null;
          final AnAction anAction = actionWithParentGroup == null ? (AnAction)value : (AnAction)actionWithParentGroup.getKey();
          final Presentation templatePresentation = anAction.getTemplatePresentation();
          final Icon icon = templatePresentation.getIcon();

          final DataContext dataContext = DataManager.getInstance().getDataContext(myContextComponent);

          final AnActionEvent event = GotoActionModel.updateActionBeforeShow(anAction, dataContext);
          final Presentation presentation = event.getPresentation();

          append(templatePresentation.getText());

          final String groupName = actionWithParentGroup == null ? null : (String)actionWithParentGroup.getValue();
          if (!StringUtil.isEmpty(groupName)) {
            setLocationString(groupName);
          }
          if (icon != null) {
            setIcon(icon);
          }
        }
        else if (isSetting(value)) {
          String hit = ((OptionDescription)value).getHit();
          if (hit == null) {
            hit = ((OptionDescription)value).getOption();
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
      myLeftWidth = 16;
      ListModel model = myList.getModel();
      myTitle.setIcon(EmptyIcon.ICON_16);
      myTitle.setFont(getTitleFont());
      int index = 0;
      while (index < model.getSize()) {
        Object el = model.getElementAt(index);
        Object prev = index == 0 ? null : model.getElementAt(index - 1);
        String title = getTitle(index, el, prev);
        if (title != null) {
          myTitle.setText(title);
          myLeftWidth = Math.max(myLeftWidth, myTitle.getPreferredSize().width);
        }
        index++;
      }

      myLeftWidth += 10;
      myTitle.setForeground(Gray._122);
      myTitle.setAlignmentY(BOTTOM_ALIGNMENT);
      myLeftPanel.setBorder(new CompoundBorder(new CustomLineBorder(Gray._206, 0,0,0,1), new EmptyBorder(0,0,0,5)));
    }

    private Font getTitleFont() {
      return UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
    }
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
      if (pattern.trim().length() == 0) {
        return;
      }

      if (myClassModel == null) {
        myClassModel = new GotoClassModel2(project);
        myFileModel = new GotoFileModel(project);
        myActionModel = new GotoActionModel(project, myFocusComponent) {
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
                } else {
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
        myClasses = myClassModel.getNames(false);
        myFiles = myFileModel.getNames(false);
        myActions = myActionModel.getNames(true);
        myConfigurables.clear();
        fillConfigurablesIds(null, new IdeConfigurablesGroup().getConfigurables());
        fillConfigurablesIds(null, new ProjectConfigurablesGroup(project).getConfigurables());
      }
      int clsCounter = 0;
      int filesCounter = 0;
      int actionsCount = 0;
      final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
      try {
        List<MatchResult> classes = collectResults(pattern, myClasses, myClassModel);
        List<MatchResult> files = collectResults(pattern, myFiles, myFileModel);
        List<MatchResult> actions = collectResults(pattern, myActions, myActionModel);
        DefaultListModel listModel = new DefaultListModel();
        Set<VirtualFile> alreadyAddedFiles = new HashSet<VirtualFile>();

        for (MatchResult o : classes) {
          if (clsCounter > 15) break;
          myProgressIndicator.checkCanceled();
          Object[] objects = myClassModel.getElementsByName(o.elementName, false, pattern);
          for (Object object : objects) {
            myProgressIndicator.checkCanceled();
            if (!listModel.contains(object)) {
              listModel.addElement(object);
              clsCounter++;
              if (object instanceof PsiElement) {
                VirtualFile file = PsiUtilCore.getVirtualFile((PsiElement)object);
                if (file != null) {
                  alreadyAddedFiles.add(file);
                }
              }
              if (clsCounter > 15) break;
            }
          }
        }
        for (MatchResult o : files) {
          if (filesCounter > 15) break;
          myProgressIndicator.checkCanceled();
          Object[] objects = myFileModel.getElementsByName(o.elementName, false, pattern, myProgressIndicator);
          for (Object object : objects) {
            myProgressIndicator.checkCanceled();
            if (!listModel.contains(object)) {
              if (object instanceof PsiFile) {
                object = ((PsiFile)object).getVirtualFile();
              }
              if (object instanceof VirtualFile && !alreadyAddedFiles.contains((VirtualFile)object) && !((VirtualFile)object).isDirectory()) {
                myProgressIndicator.checkCanceled();
                listModel.addElement(object);
                filesCounter++;
              }
            }
            if (filesCounter > 15) break;
          }
        }

        final Set<AnAction> addedActions = new HashSet<AnAction>();
        final List<Object> actionsAndSettings = new ArrayList<Object>();
        for (MatchResult o : actions) {
          //if (actionsCount > 15) break;
          myProgressIndicator.checkCanceled();
          Object[] objects = myActionModel.getElementsByName(o.elementName, true, pattern);
          for (Object object : objects) {
            myProgressIndicator.checkCanceled();
            if (isActionValue(object)) {
              final AnAction action = (AnAction)((Map.Entry)object).getKey();
              if (!addedActions.add(action)) continue;
            }
            actionsAndSettings.add(object);
            actionsCount++;
            //if (actionsCount > 15) break;
          }
        }

        Collections.sort(actionsAndSettings, new Comparator<Object>() {
          @Override
          public int compare(Object o1, Object o2) {
            final boolean b1 = isSetting(o1);
            final boolean b2 = isSetting(o2);
            final boolean t1 = isToolWindowAction(o1);
            final boolean t2 = isToolWindowAction(o2);
            return t1 == t2 ? b1 == b2  ? 0 : b1 ? 1 : -1 : t1 ? -1 : 1;
          }
        });

        for (Object actionOrSetting : actionsAndSettings) {
          listModel.addElement(actionOrSetting);
        }

        updateModel(listModel);
      }
      finally {
        token.finish();
      }
    }

    @SuppressWarnings("SSBasedInspection")
    private void updateModel(final DefaultListModel listModel) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myProgressIndicator.checkCanceled();
          final DataContext dataContext = DataManager.getInstance().getDataContext(myContextComponent);
          int settings = 0;
          int actions = 0;
          final DefaultListModel model = new DefaultListModel();

          final String pattern = field.getText();
          final Consumer<Object> consumer = new Consumer<Object>() {
            @Override
            public void consume(Object o) {
              if (isSetting(o) || isVirtualFile(o) || isActionValue(o) || o instanceof PsiElement) {
                model.addElement(o);
              }
            }
          };

          for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
            provider.consumeTopHits(pattern, consumer);
          }

          myTopHitsCount = model.size();

          for (Object o : listModel.toArray()) {
            if (model.contains(o)) {
              continue;
            }

            if (isSetting(o)) {
              if (settings < 15) {
                settings++;
                model.addElement(o);
              }
            } else if (isActionValue(o)) {
              if (actions < 15) {
                final AnAction action = (AnAction)((Map.Entry)o).getKey();

                if (model.contains(action)) {
                  continue;
                }

                final AnActionEvent event = GotoActionModel.updateActionBeforeShow(action, dataContext);
                if (event.getPresentation().isEnabledAndVisible()) {
                  actions++;
                  model.addElement(action);
                }
              }
            } else {
              model.addElement(o);
            }
          }
          myList.setModel(model);
          myRenderer.recalculateWidth();
          if (myPopup == null || !myPopup.isVisible()) {
            final ActionCallback callback = ListDelegationUtil.installKeyboardDelegation(field.getTextEditor(), myList);
            myPopup = JBPopupFactory.getInstance()
              .createListPopupBuilder(myList)
              .setRequestFocus(false)
              .createPopup();
            Disposer.register(myPopup, new Disposable() {
              @Override
              public void dispose() {
                callback.setDone();
              }
            });
            myPopup.showUnderneathOf(field);
            ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
              @Override
              public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
                myPopup.cancel();
              }
            }, myPopup);
          } else {
            myList.revalidate();
            myList.repaint();
          }
          ListScrollingUtil.ensureSelectionExists(myList);
          if (myList.getModel().getSize() == 0) {
            myPopup.cancel();
          } else {
            final Dimension size = myList.getPreferredSize();
            myPopup.setSize(new Dimension(Math.min(600, Math.max(field.getWidth(), size.width + 15)), Math.min(600, size.height + 10)));
            final Point screen = field.getLocationOnScreen();
            final int x = screen.x + field.getWidth() - myPopup.getSize().width;

            myPopup.setLocation(new Point(x, myPopup.getLocationOnScreen().y));
          }
        }
      });
    }

    private List<MatchResult> collectResults(String pattern, String[] names, final ChooseByNameModel model) {
      if (names == null) return Collections.emptyList();
      final String trimmedPattern = pattern.trim();
      if (!pattern.startsWith("*") && pattern.length() > 1) {
        pattern = "*" + pattern;
      }
      final ArrayList<MatchResult> results = new ArrayList<MatchResult>();

      MinusculeMatcher matcher = new MinusculeMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE) {
        @Override
        public boolean matches(@NotNull String name) {
          if (!(model instanceof GotoActionModel) && trimmedPattern.indexOf(' ') > 0 && name.trim().indexOf(' ') < 0) {
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
      if (!myProgressIndicator.isCanceled()) {
        ProgressManager.getInstance().runProcess(this, myProgressIndicator);
      }
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
}
