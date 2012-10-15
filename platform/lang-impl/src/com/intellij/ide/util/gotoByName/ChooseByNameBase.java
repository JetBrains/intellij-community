/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.ide.util.gotoByName;

import com.intellij.Patches;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public abstract class ChooseByNameBase {
  protected final Project myProject;
  protected final ChooseByNameModel myModel;
  protected ChooseByNameItemProvider myProvider;
  protected final String myInitialText;
  private boolean myPreselectInitialText;
  private boolean mySearchInAnyPlace = false;

  protected Component myPreviouslyFocusedComponent;

  protected JPanelProvider myTextFieldPanel;// Located in the layered pane
  protected MyTextField myTextField;
  private JPanel myCardContainer;
  private CardLayout myCard;
  protected JCheckBox myCheckBox;
  /**
   * the tool area of the popup, it is just after card box
   */
  private JComponent myToolArea;

  protected JScrollPane myListScrollPane; // Located in the layered pane
  protected JList myList;
  private MyListModel<Object> myListModel;
  private List<Pair<String, Integer>> myHistory;
  private List<Pair<String, Integer>> myFuture;

  protected ChooseByNamePopupComponent.Callback myActionListener;

  protected final Alarm myAlarm = new Alarm();

  private final ListUpdater myListUpdater = new ListUpdater();

  private volatile boolean myListIsUpToDate = false;
  private boolean myDisposedFlag = false;
  private ActionCallback myPostponedOkAction;

  private final String[][] myNames = new String[2][];
  private volatile CalcElementsThread myCalcElementsThread;
  private static int VISIBLE_LIST_SIZE_LIMIT = 10;
  public static final int MAXIMUM_LIST_SIZE_LIMIT = 30;
  private int myMaximumListSizeLimit = MAXIMUM_LIST_SIZE_LIMIT;
  @NonNls private static final String NOT_FOUND_IN_PROJECT_CARD = "syslib";
  @NonNls private static final String NOT_FOUND_CARD = "nfound";
  @NonNls private static final String CHECK_BOX_CARD = "chkbox";
  @NonNls private static final String SEARCHING_CARD = "searching";
  private static final int REBUILD_DELAY = 300;

  private final Alarm myHideAlarm = new Alarm();
  private boolean myShowListAfterCompletionKeyStroke = false;
  protected JBPopup myTextPopup;
  protected JBPopup myDropdownPopup;

  private boolean myClosedByShiftEnter = false;
  protected final int myInitialIndex;
  private String myFindUsagesTitle;
  private ShortcutSet myCheckBoxShortcut;

  public boolean checkDisposed() {
    if (myDisposedFlag && myPostponedOkAction != null && !myPostponedOkAction.isProcessed()) {
      myPostponedOkAction.setRejected();
    }

    return myDisposedFlag;
  }

  public void setDisposed(boolean disposedFlag) {
    myDisposedFlag = disposedFlag;
    if (disposedFlag) {
      myNames[0] = myNames[1] = null;
    }
  }

  /**
   * @param initialText initial text which will be in the lookup text field
   * @param context
   */
  protected ChooseByNameBase(Project project, ChooseByNameModel model, String initialText, PsiElement context) {
    this(project, model, new DefaultChooseByNameItemProvider(context), initialText, 0);
  }

  @SuppressWarnings("UnusedDeclaration") // Used in MPS
  protected ChooseByNameBase(Project project, ChooseByNameModel model, ChooseByNameItemProvider provider, String initialText) {
    this(project, model, provider, initialText, 0);
  }

  /**
   * @param initialText  initial text which will be in the lookup text field
   * @param initialIndex
   */
  protected ChooseByNameBase(Project project,
                             ChooseByNameModel model,
                             ChooseByNameItemProvider provider,
                             String initialText,
                             final int initialIndex) {
    myProject = project;
    myModel = model;
    myInitialText = initialText;
    myProvider = provider;
    myInitialIndex = initialIndex;
    mySearchInAnyPlace = Registry.is("ide.goto.middle.matching") && model.useMiddleMatching();
  }

  public void setShowListAfterCompletionKeyStroke(boolean showListAfterCompletionKeyStroke) {
    myShowListAfterCompletionKeyStroke = showListAfterCompletionKeyStroke;
  }

  public boolean isSearchInAnyPlace() {
    return mySearchInAnyPlace;
  }

  public void setSearchInAnyPlace(boolean searchInAnyPlace) {
    mySearchInAnyPlace = searchInAnyPlace;
  }

  public boolean isClosedByShiftEnter() {
    return myClosedByShiftEnter;
  }

  public boolean isOpenInCurrentWindowRequested() {
    return isClosedByShiftEnter();
  }

  /**
   * Set tool area. The method may be called only before invoke.
   *
   * @param toolArea a tool area component
   */
  public void setToolArea(JComponent toolArea) {
    if (myCard != null) {
      throw new IllegalStateException("Tool area is modifiable only before invoke()");
    }
    myToolArea = toolArea;
  }

  public void setFindUsagesTitle(@Nullable String findUsagesTitle) {
    myFindUsagesTitle = findUsagesTitle;
  }

  public void invoke(final ChooseByNamePopupComponent.Callback callback,
                     final ModalityState modalityState,
                     boolean allowMultipleSelection) {
    initUI(callback, modalityState, allowMultipleSelection);
  }

  public ChooseByNameModel getModel() {
    return myModel;
  }

  public class JPanelProvider extends JPanel implements DataProvider {
    private JBPopup myHint = null;
    private boolean myFocusRequested = false;

    JPanelProvider() {
    }

    @Override
    public Object getData(String dataId) {
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return myModel.getHelpId();
      }
      if (!myListIsUpToDate) {
        return null;
      }
      if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
        Object element = getChosenElement();

        if (element instanceof PsiElement) {
          return element;
        }

        if (element instanceof DataProvider) {
          return ((DataProvider)element).getData(dataId);
        }
      }
      else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        final List<Object> chosenElements = getChosenElements();
        if (chosenElements != null) {
          List<PsiElement> result = new ArrayList<PsiElement>(chosenElements.size());
          for (Object element : chosenElements) {
            if (element instanceof PsiElement) {
              result.add((PsiElement)element);
            }
          }
          return PsiUtilCore.toPsiElementArray(result);
        }
      }
      else if (PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.is(dataId)) {
        return getBounds();
      }
      return null;
    }

    public void registerHint(JBPopup h) {
      if (myHint != null && myHint.isVisible() && myHint != h) {
        myHint.cancel();
      }
      myHint = h;
    }

    public boolean focusRequested() {
      boolean focusRequested = myFocusRequested;

      myFocusRequested = false;

      return focusRequested;
    }

    @Override
    public void requestFocus() {
      myFocusRequested = true;
    }

    public void unregisterHint() {
      myHint = null;
    }

    public void hideHint() {
      if (myHint != null) {
        myHint.cancel();
      }
    }

    @Nullable
    public JBPopup getHint() {
      return myHint;
    }

    public void updateHint(PsiElement element) {
      if (myHint == null || !myHint.isVisible()) return;
      final PopupUpdateProcessor updateProcessor = myHint.getUserData(PopupUpdateProcessor.class);
      if (updateProcessor != null) {
        updateProcessor.updatePopup(element);
      }
    }

    public void repositionHint() {
      if (myHint == null || !myHint.isVisible()) return;
      PopupPositionManager.positionPopupInBestPosition(myHint, null, null);
    }
  }

  /**
   * @param callback
   * @param modalityState          - if not null rebuilds list in given {@link ModalityState}
   * @param allowMultipleSelection
   */
  protected void initUI(final ChooseByNamePopupComponent.Callback callback,
                        final ModalityState modalityState,
                        boolean allowMultipleSelection) {
    myPreviouslyFocusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);

    myActionListener = callback;
    myTextFieldPanel = new JPanelProvider();
    myTextFieldPanel.setLayout(new BoxLayout(myTextFieldPanel, BoxLayout.Y_AXIS));

    final JPanel hBox = new JPanel();
    hBox.setLayout(new BoxLayout(hBox, BoxLayout.X_AXIS));

    JPanel caption2Tools = new JPanel(new BorderLayout());

    if (myModel.getPromptText() != null) {
      JLabel label = new JLabel(myModel.getPromptText());
      if (UIUtil.isUnderAquaLookAndFeel()) {
        label.setBorder(new CompoundBorder(new EmptyBorder(0, 9, 0, 0), label.getBorder()));
      }
      label.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
      caption2Tools.add(label, BorderLayout.WEST);
    }

    caption2Tools.add(hBox, BorderLayout.EAST);

    myCard = new CardLayout();
    myCardContainer = new JPanel(myCard);
    myCardContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));  // space between checkbox and filter/show all in view buttons

    final String checkBoxName = myModel.getCheckBoxName();
    myCheckBox = new JCheckBox(checkBoxName != null ? checkBoxName +
                                                      (myCheckBoxShortcut != null ? " (" +
                                                                                    KeymapUtil
                                                                                      .getShortcutsText(myCheckBoxShortcut.getShortcuts()) +
                                                                                    ")" : "") : "");
    myCheckBox.setAlignmentX(SwingConstants.RIGHT);

    if (!SystemInfo.isMac) {
      myCheckBox.setBorder(null);
    }

    myCheckBox.setSelected(myModel.loadInitialCheckBoxState());

    if (checkBoxName == null) {
      myCheckBox.setVisible(false);
    }

    addCard(myCheckBox, CHECK_BOX_CARD);

    addCard(new HintLabel(myModel.getNotInMessage()), NOT_FOUND_IN_PROJECT_CARD);
    addCard(new HintLabel(IdeBundle.message("label.choosebyname.no.matches.found")), NOT_FOUND_CARD);
    JPanel searching = new JPanel(new BorderLayout(5, 0));
    searching.add(new AsyncProcessIcon("searching"), BorderLayout.WEST);
    searching.add(new HintLabel(IdeBundle.message("label.choosebyname.searching")), BorderLayout.CENTER);
    addCard(searching, SEARCHING_CARD);
    myCard.show(myCardContainer, CHECK_BOX_CARD);

    if (isCheckboxVisible()) {
      hBox.add(myCardContainer);
    }


    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ShowFindUsagesAction() {
      @Override
      public PsiElement[][] getElements() {
        if (myListModel == null) return new PsiElement[][]{PsiElement.EMPTY_ARRAY, PsiElement.EMPTY_ARRAY};
        final Object[] objects = myListModel.toArray();
        final List<PsiElement> prefixMatchElements = new ArrayList<PsiElement>(objects.length);
        final List<PsiElement> nonPrefixMatchElements = new ArrayList<PsiElement>(objects.length);
        List<PsiElement> curElements = prefixMatchElements;
        for (Object object : objects) {
          if (object instanceof PsiElement) {
            curElements.add((PsiElement)object);
          }
          else if (object instanceof DataProvider) {
            final PsiElement psi = LangDataKeys.PSI_ELEMENT.getData((DataProvider)object);
            if (psi != null) {
              curElements.add(psi);
            }
          }
          else if (object == NON_PREFIX_SEPARATOR) {
            curElements = nonPrefixMatchElements;
          }
        }
        return new PsiElement[][]{PsiUtilCore.toPsiElementArray(prefixMatchElements),
          PsiUtilCore.toPsiElementArray(nonPrefixMatchElements)};
      }
    });
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    final JComponent toolbarComponent = actionToolbar.getComponent();
    toolbarComponent.setBorder(null);

    hBox.add(toolbarComponent);

    if (myToolArea == null) {
      myToolArea = new JLabel(EmptyIcon.create(1, 24));
    }
    hBox.add(myToolArea);
    myTextFieldPanel.add(caption2Tools);

    myHistory = new ArrayList<Pair<String, Integer>>();
    myFuture = new ArrayList<Pair<String, Integer>>();
    myTextField = new MyTextField();
    myTextField.setText(myInitialText);
    if (myPreselectInitialText) {
      myTextField.select(0, myInitialText.length());
    }

    final ActionMap actionMap = new ActionMap();
    actionMap.setParent(myTextField.getActionMap());
    actionMap.put(DefaultEditorKit.copyAction, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myTextField.getSelectedText() != null) {
          actionMap.getParent().get(DefaultEditorKit.copyAction).actionPerformed(e);
          return;
        }
        final Object chosenElement = getChosenElement();
        if (chosenElement instanceof PsiElement) {
          CopyReferenceAction.doCopy((PsiElement)chosenElement, myProject);
        }
      }
    });
    myTextField.setActionMap(actionMap);

    myTextFieldPanel.add(myTextField);
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    myTextField.setFont(editorFont);

    if (checkBoxName != null) {
      if (myCheckBox != null && myCheckBoxShortcut != null) {
        new AnAction("change goto check box", null, null) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            myCheckBox.setSelected(!myCheckBox.isSelected());
          }
        }.registerCustomShortcutSet(myCheckBoxShortcut, myTextField);
      }
    }

    if (isCloseByFocusLost()) {
      myTextField.addFocusListener(new FocusAdapter() {
        @Override
        public void focusLost(@NotNull final FocusEvent e) {
          myHideAlarm.addRequest(new Runnable() {
            @Override
            public void run() {
              JBPopup popup = JBPopupFactory.getInstance().getChildFocusedPopup(e.getComponent());
              if (popup != null) {
                popup.addListener(new JBPopupListener.Adapter() {
                  @Override
                  public void onClosed(@NotNull LightweightWindowEvent event) {
                    if (event.isOk()) {
                      hideHint();
                    }
                  }
                });
              }
              else {
                Component oppositeComponent = e.getOppositeComponent();
                if (oppositeComponent != null && !(oppositeComponent instanceof JFrame) &&
                    myList.isShowing() &&
                    (oppositeComponent == myList || SwingUtilities.isDescendingFrom(myList, oppositeComponent))) {
                  myTextField.requestFocus();// Otherwise me may skip some KeyEvents
                  return;
                }

                EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
                if (queue instanceof IdeEventQueue) {
                  if (!((IdeEventQueue)queue).wasRootRecentlyClicked(oppositeComponent)) {
                    Component root = SwingUtilities.getRoot(myTextField);
                    if (root != null) {
                      root.requestFocus();
                      myTextField.requestFocus();
                      return;
                    }
                  }
                }

                hideHint();
              }
            }
          }, 5);
        }
      });
    }

    if (myCheckBox != null) {
      myCheckBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          rebuildList(false);
        }
      });
      myCheckBox.setFocusable(false);
    }

    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        clearPosponedOkAction(false);
        rebuildList(false);
      }
    });

    final Set<KeyStroke> upShortcuts = getShortcuts(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
    final Set<KeyStroke> downShortcuts = getShortcuts(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    myTextField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(@NotNull KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.getModifiers() & InputEvent.SHIFT_MASK) != 0) {
          myClosedByShiftEnter = true;
          close(true);
        }
        if (!myListScrollPane.isVisible()) {
          return;
        }
        final int keyCode;

        // Add support for user-defined 'caret up/down' shortcuts.
        KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);
        if (upShortcuts.contains(stroke)) {
          keyCode = KeyEvent.VK_UP;
        }
        else if (downShortcuts.contains(stroke)) {
          keyCode = KeyEvent.VK_DOWN;
        }
        else {
          keyCode = e.getKeyCode();
        }
        switch (keyCode) {
          case KeyEvent.VK_DOWN:
            ListScrollingUtil.moveDown(myList, e.getModifiersEx());
            break;
          case KeyEvent.VK_UP:
            ListScrollingUtil.moveUp(myList, e.getModifiersEx());
            break;
          case KeyEvent.VK_PAGE_UP:
            ListScrollingUtil.movePageUp(myList);
            break;
          case KeyEvent.VK_PAGE_DOWN:
            ListScrollingUtil.movePageDown(myList);
            break;
          case KeyEvent.VK_TAB:
            close(true);
            break;
          case KeyEvent.VK_ENTER:
            if (myList.getSelectedValue() == EXTRA_ELEM) {
              myMaximumListSizeLimit += MAXIMUM_LIST_SIZE_LIMIT;
              rebuildList(myList.getSelectedIndex(), REBUILD_DELAY, null, ModalityState.current());
              e.consume();
            }
            break;
        }

        if (myList.getSelectedValue() == NON_PREFIX_SEPARATOR) {
          if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_PAGE_UP) {
            ListScrollingUtil.moveUp(myList, e.getModifiersEx());
          }
          else {
            ListScrollingUtil.moveDown(myList, e.getModifiersEx());
          }
        }
      }
    });

    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        doClose(true);
      }
    });

    myListModel = new MyListModel<Object>();
    myList = new JBList(myListModel);
    myList.setFocusable(false);
    myList.setSelectionMode(allowMultipleSelection ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION :
                            ListSelectionModel.SINGLE_SELECTION);
    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        if (!myTextField.hasFocus()) {
          myTextField.requestFocus();
        }

        if (clickCount == 2) {
          int selectedIndex = myList.getSelectedIndex();
          Rectangle selectedCellBounds = myList.getCellBounds(selectedIndex, selectedIndex);

          if (selectedCellBounds.contains(e.getPoint())) { // Otherwise it was reselected in the selection listener
            if (myList.getSelectedValue() == EXTRA_ELEM) {
              myMaximumListSizeLimit += MAXIMUM_LIST_SIZE_LIMIT;
              rebuildList(selectedIndex, REBUILD_DELAY, null, ModalityState.current());
            }
            else {
              doClose(true);
            }
          }
          return true;
        }

        return false;
      }
    }.installOn(myList);

    myList.setCellRenderer(myModel.getListCellRenderer());
    myList.setFont(editorFont);

    myList.addListSelectionListener(new ListSelectionListener() {
      private int myPreviousSelectionIndex = 0;

      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (myList.getSelectedValue() != NON_PREFIX_SEPARATOR) {
          myPreviousSelectionIndex = myList.getSelectedIndex();
          chosenElementMightChange();
          updateDocumentation();
        }
        else {
          myList.setSelectedIndex(myPreviousSelectionIndex);
        }
      }
    });

    myListScrollPane = ScrollPaneFactory.createScrollPane(myList);
    myListScrollPane.setViewportBorder(new EmptyBorder(0, 0, 0, 0));

    myTextFieldPanel.setBorder(new EmptyBorder(2, 2, 2, 2));

    showTextFieldPanel();

    if (modalityState != null) {
      rebuildList(myInitialIndex, 0, null, modalityState);
    }
  }

  private void addCard(JComponent comp, String cardId) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(comp, BorderLayout.EAST);
    myCardContainer.add(wrapper, cardId);
  }

  public void setCheckBoxShortcut(ShortcutSet shortcutSet) {
    myCheckBoxShortcut = shortcutSet;
  }

  @NotNull
  private static Set<KeyStroke> getShortcuts(@NotNull String actionId) {
    Set<KeyStroke> result = new HashSet<KeyStroke>();
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    if (shortcuts == null) {
      return result;
    }
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
        result.add(keyboardShortcut.getFirstKeyStroke());
      }
    }
    return result;
  }

  private void hideHint() {
    if (!myTextFieldPanel.focusRequested()) {
      doClose(false);
      myTextFieldPanel.hideHint();
    }
  }

  /**
   * Default rebuild list. It uses {@link #REBUILD_DELAY} and current modality state.
   */
  public void rebuildList(boolean initial) {
    // TODO this method is public, because the chooser does not listed for the model.
    rebuildList(initial ? myInitialIndex : 0, REBUILD_DELAY, null, ModalityState.current());
  }

  private void updateDocPosition() {
    final JBPopup hint = myTextFieldPanel.getHint();
    if (hint != null) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (myTextFieldPanel != null) myTextFieldPanel.repositionHint();
        }
      });
    }
  }

  private void updateDocumentation() {
    final JBPopup hint = myTextFieldPanel.getHint();
    final Object element = getChosenElement();
    if (hint != null) {
      if (element instanceof PsiElement) {
        myTextFieldPanel.updateHint((PsiElement)element);
      }
      else if (element instanceof DataProvider) {
        final Object o = ((DataProvider)element).getData(LangDataKeys.PSI_ELEMENT.getName());
        if (o instanceof PsiElement) {
          myTextFieldPanel.updateHint((PsiElement)o);
        }
      }
    }
  }

  public String transformPattern(String pattern) {
    return pattern;
  }

  protected void doClose(final boolean ok) {
    try {
      if (checkDisposed()) return;

      if (postponeCloseWhenListReady(ok)) return;

      cancelListUpdater();
      close(ok);

      clearPosponedOkAction(ok);
    }
    finally {
      myListModel.clear();
      CalcElementsThread thread = myCalcElementsThread;
      if (thread != null) {
        thread.clear();
      }
    }
  }

  protected void cancelListUpdater() {
    myListUpdater.cancelAll();
  }

  private boolean postponeCloseWhenListReady(boolean ok) {
    if (!isToFixLostTyping()) return false;

    final String text = myTextField.getText();
    if (ok && !myListIsUpToDate && text != null && !text.trim().isEmpty()) {
      myPostponedOkAction = new ActionCallback();
      IdeFocusManager.getInstance(myProject).typeAheadUntil(myPostponedOkAction);
      return true;
    }

    return false;
  }

  protected static boolean isToFixLostTyping() {
    return Registry.is("actionSystem.fixLostTyping");
  }

  private synchronized void ensureNamesLoaded(boolean checkboxState) {
    int index = checkboxState ? 1 : 0;
    if (myNames[index] != null) return;

    Window window = (Window)SwingUtilities.getAncestorOfClass(Window.class, myTextField);
    //LOG.assertTrue (myTextField != null);
    //LOG.assertTrue (window != null);
    Window ownerWindow = null;
    if (window != null) {
      window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      ownerWindow = window.getOwner();
      if (ownerWindow != null) {
        ownerWindow.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      }
    }
    myNames[index] = myModel.getNames(checkboxState);
    assert myNames[index] != null : "Model "+myModel+ "("+myModel.getClass()+") returned null names";

    if (window != null) {
      window.setCursor(Cursor.getDefaultCursor());
      if (ownerWindow != null) {
        ownerWindow.setCursor(Cursor.getDefaultCursor());
      }
    }
  }

  @NotNull
  public String[] getNames(boolean checkboxState) {
    return checkboxState ? myNames[1] : myNames[0];
  }


  @NotNull
  protected Set<Object> filter(@NotNull Set<Object> elements) {
    return elements;
  }

  protected abstract boolean isCheckboxVisible();

  protected abstract boolean isShowListForEmptyPattern();

  protected abstract boolean isCloseByFocusLost();

  protected void showTextFieldPanel() {
    final JLayeredPane layeredPane = getLayeredPane();
    final Dimension preferredTextFieldPanelSize = myTextFieldPanel.getPreferredSize();
    final int x = (layeredPane.getWidth() - preferredTextFieldPanelSize.width) / 2;
    final int paneHeight = layeredPane.getHeight();
    final int y = paneHeight / 3 - preferredTextFieldPanelSize.height / 2;

    VISIBLE_LIST_SIZE_LIMIT = Math.max
      (10, (paneHeight - (y + preferredTextFieldPanelSize.height)) / (preferredTextFieldPanelSize.height / 2) - 1);

    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myTextFieldPanel, myTextField);
    builder.setCancelCallback(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        myTextPopup = null;
        close(false);
        return Boolean.TRUE;
      }
    }).setFocusable(true).setRequestFocus(true).setModalContext(false).setCancelOnClickOutside(false);

    Point point = new Point(x, y);
    SwingUtilities.convertPointToScreen(point, layeredPane);
    Rectangle bounds = new Rectangle(point, new Dimension(preferredTextFieldPanelSize.width + 20, preferredTextFieldPanelSize.height));
    myTextPopup = builder.createPopup();
    myTextPopup.setSize(bounds.getSize());
    myTextPopup.setLocation(bounds.getLocation());

    new MnemonicHelper().register(myTextFieldPanel);
    if (myProject != null) {
      DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(myTextPopup);
    }

    Disposer.register(myTextPopup, new Disposable() {
      @Override
      public void dispose() {
        cancelCalcElementsThread();
      }
    });
    myTextPopup.show(layeredPane);
  }

  private JLayeredPane getLayeredPane() {
    JLayeredPane layeredPane;
    final Window window = WindowManager.getInstance().suggestParentWindow(myProject);

    Component parent = UIUtil.findUltimateParent(window);

    if (parent instanceof JFrame) {
      layeredPane = ((JFrame)parent).getLayeredPane();
    }
    else if (parent instanceof JDialog) {
      layeredPane = ((JDialog)parent).getLayeredPane();
    }
    else {
      throw new IllegalStateException("cannot find parent window: project=" + myProject +
                                      (myProject != null ? "; open=" + myProject.isOpen() : "") +
                                      "; window=" + window);
    }
    return layeredPane;
  }

  private final Object myRebuildMutex = new Object();

  protected void rebuildList(final int pos,
                             final int delay,
                             @Nullable final Runnable postRunnable,
                             @NotNull final ModalityState modalityState) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myListIsUpToDate = false;
    myAlarm.cancelAllRequests();
    myListUpdater.cancelAll();

    cancelCalcElementsThread();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final String text = myTextField.getText();
        if (!canShowListForEmptyPattern() &&
            (text == null || text.trim().isEmpty())) {
          myListModel.clear();
          hideList();
          if (myTextFieldPanel != null) myTextFieldPanel.hideHint();
          myCard.show(myCardContainer, CHECK_BOX_CARD);
          return;
        }
        final Runnable request = new Runnable() {
          @Override
          public void run() {
            final CalcElementsCallback callback = new CalcElementsCallback() {
              @Override
              public void run(@NotNull final Set<?> elements) {
                synchronized (myRebuildMutex) {
                  ApplicationManager.getApplication().assertIsDispatchThread();
                  if (checkDisposed()) {
                    return;
                  }

                  myListIsUpToDate = true;
                  setElementsToList(pos, elements);
                  myList.repaint();
                  chosenElementMightChange();

                  if (elements.isEmpty() && myTextFieldPanel != null) {
                    myTextFieldPanel.hideHint();
                  }

                  if (postRunnable != null) {
                    postRunnable.run();
                  }
                }
              }
            };

            cancelCalcElementsThread();

            ListCellRenderer cellRenderer = myList.getCellRenderer();
            if (cellRenderer instanceof ExpandedItemListCellRendererWrapper) {
              cellRenderer = ((ExpandedItemListCellRendererWrapper)cellRenderer).getWrappee();
            }
            if (cellRenderer instanceof MatcherHolder) {
              final String pattern = transformPattern(text);
              final Matcher matcher = buildPatternMatcher(isSearchInAnyPlace() ? "*" + pattern + "*" : pattern);
              ((MatcherHolder)cellRenderer).setPatternMatcher(matcher);
            }

            CalcElementsThread calcElementsThread =
              new CalcElementsThread(text, myCheckBox.isSelected(), callback, modalityState, postRunnable == null);
            myCalcElementsThread = calcElementsThread;
            ApplicationManager.getApplication().executeOnPooledThread(calcElementsThread);
          }
        };

        if (delay > 0) {
          myAlarm.addRequest(request, delay, ModalityState.stateForComponent(myTextField));
        }
        else {
          request.run();
        }
      }
    }, modalityState);
  }

  private boolean isShowListAfterCompletionKeyStroke() {
    return myShowListAfterCompletionKeyStroke;
  }

  private void cancelCalcElementsThread() {
    CalcElementsThread calcElementsThread = myCalcElementsThread;
    if (calcElementsThread != null) {
      calcElementsThread.cancel();
      myCalcElementsThread = null;
    }
  }

  private void setElementsToList(int pos, @NotNull Set<?> elements) {
    myListUpdater.cancelAll();
    if (checkDisposed()) return;
    if (elements.isEmpty()) {
      myListModel.clear();
      myTextField.setForeground(Color.red);
      myListUpdater.cancelAll();
      hideList();
      clearPosponedOkAction(false);
      return;
    }

    Object[] oldElements = myListModel.toArray();
    Object[] newElements = elements.toArray();
    List<ModelDiff.Cmd> commands = ModelDiff.createDiffCmds(myListModel, oldElements, newElements);
    if (commands == null) {
      myListUpdater.doPostponedOkIfNeeded();
      return;
    }

    myTextField.setForeground(UIUtil.getTextFieldForeground());
    if (commands.isEmpty()) {
      if (pos <= 0) {
        pos = detectBestStatisticalPosition();
      }

      ListScrollingUtil.selectItem(myList, Math.min(pos, myListModel.size() - 1));
      myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
      showList();
      updateDocPosition();
    }
    else {
      showList();
      myListUpdater.appendToModel(commands, pos);
    }
  }

  private int detectBestStatisticalPosition() {
    int best = 0;
    int bestPosition = 0;
    int bestMatch = Integer.MIN_VALUE;
    final int count = myListModel.getSize();

    Matcher matcher = buildPatternMatcher(transformPattern(myTextField.getText()));

    final String statContext = statisticsContext();
    for (int i = 0; i < count; i++) {
      final Object modelElement = myListModel.getElementAt(i);
      String text = EXTRA_ELEM.equals(modelElement) || NON_PREFIX_SEPARATOR.equals(modelElement) ? null : myModel.getFullName(modelElement);
      if (text != null) {
        String shortName = myModel.getElementName(modelElement);
        int match = shortName != null && matcher instanceof MinusculeMatcher
                    ? ((MinusculeMatcher)matcher).matchingDegree(shortName) : Integer.MIN_VALUE;
        int stats = StatisticsManager.getInstance().getUseCount(new StatisticsInfo(statContext, text));
        if (stats > best || stats == best && match > bestMatch) {
          best = stats;
          bestPosition = i;
          bestMatch = match;
        }
      }
    }

    if (bestPosition < count - 1 && myListModel.getElementAt(bestPosition) == NON_PREFIX_SEPARATOR) {
      bestPosition++;
    }

    return bestPosition;
  }

  @NotNull
  @NonNls
  protected String statisticsContext() {
    return "choose_by_name#" + myModel.getPromptText() + "#" + myCheckBox.isSelected() + "#" + myTextField.getText();
  }

  private static class MyListModel<T> extends DefaultListModel implements ModelDiff.Model<T> {
    @Override
    public void addToModel(int idx, T element) {
      if (idx < size()) {
        add(idx, element);
      }
      else {
        addElement(element);
      }
    }

    @Override
    public void removeRangeFromModel(int start, int end) {
      if (start < size() && size() != 0) {
        removeRange(start, Math.min(end, size()-1));
      }
    }
  }

  private class ListUpdater {
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private static final int DELAY = 10;
    private static final int MAX_BLOCKING_TIME = 30;
    private final List<ModelDiff.Cmd> myCommands = Collections.synchronizedList(new ArrayList<ModelDiff.Cmd>());

    public void cancelAll() {
      myCommands.clear();
      myAlarm.cancelAllRequests();
    }

    public void appendToModel(@NotNull List<ModelDiff.Cmd> commands, final int selectionPos) {
      myAlarm.cancelAllRequests();
      myCommands.addAll(commands);

      if (myCommands.isEmpty() || checkDisposed()) {
        return;
      }
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          if (checkDisposed()) {
            return;
          }
          final long startTime = System.currentTimeMillis();
          while (!myCommands.isEmpty() && System.currentTimeMillis() - startTime < MAX_BLOCKING_TIME) {
            final ModelDiff.Cmd cmd = myCommands.remove(0);
            cmd.apply();
          }

          myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
          if (!myListModel.isEmpty()) {
            int pos = selectionPos <= 0 ? detectBestStatisticalPosition() : selectionPos;
            ListScrollingUtil.selectItem(myList, Math.min(pos, myListModel.size() - 1));
          }

          if (!myCommands.isEmpty()) {
            myAlarm.addRequest(this, DELAY);
          }
          else {
            doPostponedOkIfNeeded();
          }
          if (!checkDisposed()) {
            showList();
            updateDocPosition();
          }
        }
      }, DELAY);
    }

    private void doPostponedOkIfNeeded() {
      if (myPostponedOkAction != null) {
        if (getChosenElement() != null) {
          doClose(true);
        }
        clearPosponedOkAction(checkDisposed());
      }
    }
  }

  private void clearPosponedOkAction(boolean success) {
    if (myPostponedOkAction != null) {
      if (success) {
        myPostponedOkAction.setDone();
      }
      else {
        myPostponedOkAction.setRejected();
      }
    }

    myPostponedOkAction = null;
  }

  protected abstract void showList();

  protected abstract void hideList();

  protected abstract void close(boolean isOk);

  @Nullable
  public Object getChosenElement() {
    final List<Object> elements = getChosenElements();
    return elements != null && elements.size() == 1 ? elements.get(0) : null;
  }

  protected List<Object> getChosenElements() {
    if (myListIsUpToDate) {
      List<Object> values = new ArrayList<Object>(Arrays.asList(myList.getSelectedValues()));
      values.remove(EXTRA_ELEM);
      values.remove(NON_PREFIX_SEPARATOR);
      return values;
    }

    final String text = myTextField.getText();
    final boolean checkBoxState = myCheckBox.isSelected();
    //ensureNamesLoaded(checkBoxState);
    final String[] names = checkBoxState ? myNames[1] : myNames[0];
    if (names == null) return Collections.emptyList();

    Object uniqueElement = null;

    for (final String name : names) {
      if (text.equalsIgnoreCase(name)) {
        final Object[] elements = myModel.getElementsByName(name, checkBoxState, text);
        if (elements.length > 1) return Collections.emptyList();
        if (elements.length == 0) continue;
        if (uniqueElement != null) return Collections.emptyList();
        uniqueElement = elements[0];
      }
    }
    return uniqueElement == null ? Collections.emptyList() : Collections.singletonList(uniqueElement);
  }

  protected void chosenElementMightChange() {
  }

  protected final class MyTextField extends JTextField implements PopupOwner, TypeSafeDataProvider {
    private final KeyStroke myCompletionKeyStroke;
    private final KeyStroke forwardStroke;
    private final KeyStroke backStroke;

    private boolean completionKeyStrokeHappened = false;

    private MyTextField() {
      super(40);
      enableEvents(AWTEvent.KEY_EVENT_MASK);
      myCompletionKeyStroke = getShortcut(IdeActions.ACTION_CODE_COMPLETION);
      forwardStroke = getShortcut(IdeActions.ACTION_GOTO_FORWARD);
      backStroke = getShortcut(IdeActions.ACTION_GOTO_BACK);
      setFocusTraversalKeysEnabled(false);
      putClientProperty("JTextField.variant", "search");
    }

    @Nullable
    private KeyStroke getShortcut(String actionCodeCompletion) {
      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionCodeCompletion);
      for (final Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          return ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        }
      }
      return null;
    }

    @Override
    public void calcData(final DataKey key, @NotNull final DataSink sink) {
      if (LangDataKeys.POSITION_ADJUSTER_POPUP.equals(key)) {
        if (myDropdownPopup != null && myDropdownPopup.isVisible()) {
          sink.put(key, myDropdownPopup);
        }
      }
      else if (LangDataKeys.PARENT_POPUP.equals(key)) {
        if (myTextPopup != null && myTextPopup.isVisible()) {
          sink.put(key, myTextPopup);
        }
      }
    }

    @Override
    protected void processKeyEvent(@NotNull KeyEvent e) {
      final KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);

      if (myCompletionKeyStroke != null && keyStroke.equals(myCompletionKeyStroke)) {
        completionKeyStrokeHappened = true;
        e.consume();
        final String pattern = myTextField.getText();
        final String oldText = myTextField.getText();
        final int oldPos = myList.getSelectedIndex();
        myHistory.add(Pair.create(oldText, oldPos));
        final Runnable postRunnable = new Runnable() {
          @Override
          public void run() {
            fillInCommonPrefix(pattern);
          }
        };
        rebuildList(0, 0, postRunnable, ModalityState.current());
        return;
      }
      if (backStroke != null && keyStroke.equals(backStroke)) {
        e.consume();
        if (!myHistory.isEmpty()) {
          final String oldText = myTextField.getText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> last = myHistory.remove(myHistory.size() - 1);
          myTextField.setText(last.first);
          myFuture.add(Pair.create(oldText, oldPos));
          rebuildList(0, 0, null, ModalityState.current());
        }
        return;
      }
      if (forwardStroke != null && keyStroke.equals(forwardStroke)) {
        e.consume();
        if (!myFuture.isEmpty()) {
          final String oldText = myTextField.getText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> next = myFuture.remove(myFuture.size() - 1);
          myTextField.setText(next.first);
          myHistory.add(Pair.create(oldText, oldPos));
          rebuildList(0, 0, null, ModalityState.current());
        }
        return;
      }
      try {
        super.processKeyEvent(e);
      }
      catch (NullPointerException e1) {
        if (!Patches.SUN_BUG_ID_6322854) {
          throw e1;
        }
      }
    }

    private void fillInCommonPrefix(@NotNull final String pattern) {
      if (StringUtil.isEmpty(pattern) && !canShowListForEmptyPattern()) {
        return;
      }

      final List<String> list = myProvider.filterNames(ChooseByNameBase.this, getNames(myCheckBox.isSelected()), pattern);

      if (isComplexPattern(pattern)) return; //TODO: support '*'
      final String oldText = myTextField.getText();
      final int oldPos = myList.getSelectedIndex();

      String commonPrefix = null;
      if (!list.isEmpty()) {
        for (String name : list) {
          final String string = name.toLowerCase();
          if (commonPrefix == null) {
            commonPrefix = string;
          }
          else {
            while (!commonPrefix.isEmpty()) {
              if (string.startsWith(commonPrefix)) {
                break;
              }
              commonPrefix = commonPrefix.substring(0, commonPrefix.length() - 1);
            }
            if (commonPrefix.isEmpty()) break;
          }
        }
        commonPrefix = list.get(0).substring(0, commonPrefix.length());
        for (int i = 1; i < list.size(); i++) {
          final String string = list.get(i).substring(0, commonPrefix.length());
          if (!string.equals(commonPrefix)) {
            commonPrefix = commonPrefix.toLowerCase();
            break;
          }
        }
      }
      if (commonPrefix == null) commonPrefix = "";
      if (!StringUtil.startsWithIgnoreCase(commonPrefix, pattern)) {
        commonPrefix = pattern;
      }
      final String newPattern = commonPrefix;

      myHistory.add(Pair.create(oldText, oldPos));
      myTextField.setText(newPattern);
      myTextField.setCaretPosition(newPattern.length());

      rebuildList(false);
    }

    private boolean isComplexPattern(@NotNull final String pattern) {
      if (pattern.indexOf('*') >= 0) return true;
      for (String s : myModel.getSeparators()) {
        if (pattern.contains(s)) return true;
      }

      return false;
    }

    @Override
    @Nullable
    public Point getBestPopupPosition() {
      return new Point(myTextFieldPanel.getWidth(), getHeight());
    }

    @Override
    protected void paintComponent(@NotNull final Graphics g) {
      GraphicsUtil.setupAntialiasing(g);
      super.paintComponent(g);
    }

    public boolean isCompletionKeyStroke() {
      return completionKeyStrokeHappened;
    }
  }

  private static final String EXTRA_ELEM = "...";
  public static final String NON_PREFIX_SEPARATOR = "non-prefix matches:";

  public static Component renderNonPrefixSeparatorComponent(Color backgroundColor) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    panel.add(separator, BorderLayout.CENTER);
    if (!UIUtil.isUnderAquaBasedLookAndFeel()) {
      panel.setBorder(new EmptyBorder(3, 0, 2, 0));
    }
    panel.setBackground(backgroundColor);
    return panel;
  }

  private class CalcElementsThread implements Runnable {
    private final String myPattern;
    private boolean myCheckboxState;
    private final CalcElementsCallback myCallback;
    private final ModalityState myModalityState;

    private Set<Object> myElements = null;

    private final ProgressIndicator myCancelled = new ProgressIndicatorBase();
    private final boolean myCanCancel;

    private CalcElementsThread(String pattern,
                               boolean checkboxState,
                               CalcElementsCallback callback,
                               @NotNull ModalityState modalityState,
                               boolean canCancel) {
      myPattern = pattern;
      myCheckboxState = checkboxState;
      myCallback = callback;
      myModalityState = modalityState;
      myCanCancel = canCancel;
    }

    private final Alarm myShowCardAlarm = new Alarm();

    @Override
    public void run() {
      showCard(SEARCHING_CARD, 200);

      final Set<Object> elements = new LinkedHashSet<Object>();
      Runnable action = new Runnable() {
        @Override
        public void run() {
          try {
            ensureNamesLoaded(myCheckboxState);

            addElementsByPattern(myPattern, elements, myCancelled);

            for (Object elem : elements) {
              if (myCancelled.isCanceled()) {
                break;
              }
              if (elem instanceof PsiElement) {
                final PsiElement psiElement = (PsiElement)elem;
                psiElement.isWritable(); // That will cache writable flag in VirtualFile. Taking the action here makes it canceleable.
              }
            }
          }
          catch (ProcessCanceledException e) {
            //OK
          }
        }
      };
      ApplicationManager.getApplication().runReadAction(action);

      if (myCancelled.isCanceled()) {
        myShowCardAlarm.cancelAllRequests();
        return;
      }

      final String cardToShow;
      if (elements.isEmpty() && !myCheckboxState) {
        myCheckboxState = true;
        ApplicationManager.getApplication().runReadAction(action);
        cardToShow = elements.isEmpty() ? NOT_FOUND_CARD : NOT_FOUND_IN_PROJECT_CARD;
      }
      else {
        cardToShow = elements.isEmpty() ? NOT_FOUND_CARD : CHECK_BOX_CARD;
      }
      showCard(cardToShow, 0);

      myElements = filter(elements);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myCallback.run(myElements);
        }
      }, myModalityState);
    }

    private void addElementsByPattern(@NotNull String pattern,
                                      @NotNull final Set<Object> elements,
                                      @NotNull final ProgressIndicator cancelled) {
      myProvider.filterElements(
        ChooseByNameBase.this, pattern, myCheckboxState,
        cancelled,
        new Processor<Object>() {
          @Override
          public boolean process(Object o) {
            if (cancelled.isCanceled()) return false;
            elements.add(o);

            if (isOverflow(elements)) {
              elements.add(EXTRA_ELEM);
              return false;
            }
            return true;
          }
        }
      );
    }

    private void showCard(final String card, final int delay) {
      myShowCardAlarm.cancelAllRequests();
      myShowCardAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          myCard.show(myCardContainer, card);
        }
      }, delay, myModalityState);
    }

    protected boolean isOverflow(@NotNull Set<Object> elementsArray) {
      return elementsArray.size() >= myMaximumListSizeLimit;
    }

    private void cancel() {
      if (myCanCancel) {
        myCancelled.cancel();
        clear();
      }
    }

    private void clear() {
      Set<Object> elements = myElements;
      if (elements != null) {
        elements.clear();
      }
    }
  }


  public boolean canShowListForEmptyPattern() {
    return isShowListForEmptyPattern() || isShowListAfterCompletionKeyStroke() && lastKeyStrokeIsCompletion();
  }

  protected boolean lastKeyStrokeIsCompletion() {
    return myTextField.isCompletionKeyStroke();
  }

  private static Matcher buildPatternMatcher(@NotNull String pattern) {
    return NameUtil.buildMatcher(pattern, 0, true, true, pattern.toLowerCase().equals(pattern));
  }

  private interface CalcElementsCallback {
    void run(Set<?> elements);
  }

  private static class HintLabel extends JLabel {
    private HintLabel(String text) {
      super(text, RIGHT);
      setForeground(Color.darkGray);
    }
  }

  public int getMaximumListSizeLimit() {
    return myMaximumListSizeLimit;
  }

  private static final String ACTION_NAME = "Show All in View";

  private abstract class ShowFindUsagesAction extends AnAction {
    public ShowFindUsagesAction() {
      super(ACTION_NAME, ACTION_NAME, AllIcons.Actions.Find);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      cancelCalcElementsThread();
      cancelListUpdater();

      final UsageViewPresentation presentation = new UsageViewPresentation();
      final String prefixPattern = myFindUsagesTitle + " \'" + myTextField.getText().trim() + "\'";
      final String nonPrefixPattern = myFindUsagesTitle + " \'*" + myTextField.getText().trim() + "*\'";
      presentation.setCodeUsagesString(prefixPattern);
      presentation.setDynamicUsagesString(nonPrefixPattern);
      presentation.setTabName(prefixPattern);
      presentation.setTabText(prefixPattern);
      presentation.setTargetsNodeText("Unsorted " + StringUtil.toLowerCase(prefixPattern.toLowerCase()));
      final Object[][] elements = getElements();
      final List<PsiElement> targets = new ArrayList<PsiElement>();
      final List<Usage> usages = new ArrayList<Usage>();
      fillUsages(Arrays.asList(elements[0]), usages, targets, false);
      fillUsages(Arrays.asList(elements[1]), usages, targets, true);
      if (myListModel.contains(EXTRA_ELEM)) { //start searching for the rest
        final String text = myTextField.getText();
        final boolean checkboxState = myCheckBox.isSelected();
        final LinkedHashSet<Object> prefixMatchElementsArray = new LinkedHashSet<Object>();
        final LinkedHashSet<Object> nonPrefixMatchElementsArray = new LinkedHashSet<Object>();
        hideHint();
        ProgressManager.getInstance().run(new Task.Modal(myProject, prefixPattern, true) {
          private ChooseByNameBase.CalcElementsThread myCalcElementsThread;

          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            ensureNamesLoaded(checkboxState);
            indicator.setIndeterminate(true);
            ApplicationManager.getApplication().runReadAction(new Runnable() {

              @Override
              public void run() {
                final boolean[] overFlow = {false};
                myCalcElementsThread = new CalcElementsThread(text, checkboxState, null, ModalityState.NON_MODAL, true) {
                  @Override
                  protected boolean isOverflow(@NotNull Set<Object> elementsArray) {
                    if (elementsArray.size() > UsageLimitUtil.USAGES_LIMIT - myMaximumListSizeLimit) {
                      final int ret = UsageLimitUtil.showTooManyUsagesWarning(myProject, UsageViewBundle
                        .message("find.excessive.usage.count.prompt", elementsArray.size() + myMaximumListSizeLimit));
                      if (ret != 0) {
                        overFlow[0] = true;
                        return true;
                      }
                    }
                    return false;
                  }
                };

                boolean anyPlace = isSearchInAnyPlace();
                setSearchInAnyPlace(false);
                myCalcElementsThread.addElementsByPattern(text, prefixMatchElementsArray, indicator);
                setSearchInAnyPlace(anyPlace);

                if (anyPlace && !overFlow[0]) {
                  myCalcElementsThread.addElementsByPattern(text, nonPrefixMatchElementsArray, indicator);
                  nonPrefixMatchElementsArray.removeAll(prefixMatchElementsArray);
                }

                indicator.setText("Prepare...");
                fillUsages(prefixMatchElementsArray, usages, targets, false);
                fillUsages(nonPrefixMatchElementsArray, usages, targets, true);
              }
            });
          }

          @Override
          public void onSuccess() {
            showUsageView(targets, usages, presentation);
          }

          @Override
          public void onCancel() {
            if (myCalcElementsThread != null) {
              myCalcElementsThread.cancel();
            }
          }
        });
      }
      else {
        hideHint();
        showUsageView(targets, usages, presentation);
      }
    }

    private void fillUsages(Collection<Object> matchElementsArray,
                            List<Usage> usages,
                            List<PsiElement> targets,
                            final boolean separateGroup) {
      for (Object o : matchElementsArray) {
        if (o instanceof PsiElement) {
          PsiElement element = (PsiElement)o;
          if (element.getTextRange() != null) {
            usages.add(new UsageInfo2UsageAdapter(new UsageInfo(element) {
              @Override
              public boolean isDynamicUsage() {
                return separateGroup || super.isDynamicUsage();
              }
            }));
          }
          else {
            targets.add(element);
          }
        }
      }
    }

    private void showUsageView(@NotNull List<PsiElement> targets,
                               @NotNull List<Usage> usages,
                               @NotNull UsageViewPresentation presentation) {
      UsageViewManager
        .getInstance(myProject).showUsages(targets.isEmpty() ? UsageTarget.EMPTY_ARRAY
                                                             : PsiElement2UsageTargetAdapter
                                             .convert(PsiUtilCore.toPsiElementArray(targets)),
                                           usages.toArray(new Usage[usages.size()]), presentation);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myFindUsagesTitle == null || myProject == null) {
        e.getPresentation().setVisible(false);
        return;
      }
      final Object[][] elements = getElements();
      e.getPresentation().setEnabled(elements != null && elements[0].length + elements[1].length > 0);
    }

    public abstract Object[][] getElements();
  }
}
