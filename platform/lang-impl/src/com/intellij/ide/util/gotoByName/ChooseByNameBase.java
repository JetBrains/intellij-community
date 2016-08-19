/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.ide.actions.GotoFileAction;
import com.intellij.ide.actions.WindowAction;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
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
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public abstract class ChooseByNameBase {
  public static final String TEMPORARILY_FOCUSABLE_COMPONENT_KEY = "ChooseByNameBase.TemporarilyFocusableComponent";

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ChooseByNameBase");
  protected final Project myProject;
  protected final ChooseByNameModel myModel;
  protected ChooseByNameItemProvider myProvider;
  protected final String myInitialText;
  private boolean mySearchInAnyPlace = false;

  protected Component myPreviouslyFocusedComponent;
  private boolean myInitialized;

  protected final JPanelProvider myTextFieldPanel = new JPanelProvider();// Located in the layered pane
  protected final MyTextField myTextField = new MyTextField();
  private final CardLayout myCard = new CardLayout();
  private final JPanel myCardContainer = new JPanel(myCard);
  protected JCheckBox myCheckBox;
  /**
   * the tool area of the popup, it is just after card box
   */
  private JComponent myToolArea;

  protected JScrollPane myListScrollPane; // Located in the layered pane
  private final MyListModel<Object> myListModel = new MyListModel<>();
  protected final JList myList = new JBList(myListModel);
  private final List<Pair<String, Integer>> myHistory = ContainerUtil.newArrayList();
  private final List<Pair<String, Integer>> myFuture = ContainerUtil.newArrayList();

  protected ChooseByNamePopupComponent.Callback myActionListener;

  protected final Alarm myAlarm = new Alarm();

  private final ListUpdater myListUpdater = new ListUpdater();

  private boolean myDisposedFlag = false;
  private ActionCallback myPostponedOkAction;

  private final String[][] myNames = new String[2][];
  private volatile CalcElementsThread myCalcElementsThread;
  private static int VISIBLE_LIST_SIZE_LIMIT = 10;
  private int myListSizeIncreasing = 30;
  private int myMaximumListSizeLimit = 30;
  @NonNls private static final String NOT_FOUND_IN_PROJECT_CARD = "syslib";
  @NonNls private static final String NOT_FOUND_CARD = "nfound";
  @NonNls private static final String CHECK_BOX_CARD = "chkbox";
  @NonNls private static final String SEARCHING_CARD = "searching";
  private final int myRebuildDelay;

  private final Alarm myHideAlarm = new Alarm();
  private boolean myShowListAfterCompletionKeyStroke = false;
  protected JBPopup myTextPopup;
  protected JBPopup myDropdownPopup;

  private boolean myClosedByShiftEnter = false;
  protected final int myInitialIndex;
  private String myFindUsagesTitle;
  private ShortcutSet myCheckBoxShortcut;
  protected boolean myInitIsDone;
  static final boolean ourLoadNamesEachTime = FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping;
  private boolean myFixLostTyping = true;
  private boolean myAlwaysHasMore = false;
  private Point myFocusPoint;

  public boolean checkDisposed() {
    if (myDisposedFlag && myPostponedOkAction != null && !myPostponedOkAction.isProcessed()) {
      myPostponedOkAction.setRejected();
    }

    return myDisposedFlag;
  }

  public void setDisposed(boolean disposedFlag) {
    myDisposedFlag = disposedFlag;
    if (disposedFlag) {
      setNamesSync(true, null);
      setNamesSync(false, null);
    }
  }

  private void setNamesSync(boolean checkboxState, @Nullable String[] value) {
    synchronized (myNames) {
      myNames[checkboxState ? 1 : 0] = value;
    }
  }

  /**
   * @param initialText initial text which will be in the lookup text field
   */
  protected ChooseByNameBase(Project project, @NotNull ChooseByNameModel model, String initialText, PsiElement context) {
    this(project, model, new DefaultChooseByNameItemProvider(context), initialText, 0);
  }

  @SuppressWarnings("UnusedDeclaration") // Used in MPS
  protected ChooseByNameBase(Project project,
                             @NotNull ChooseByNameModel model,
                             @NotNull ChooseByNameItemProvider provider,
                             String initialText) {
    this(project, model, provider, initialText, 0);
  }

  /**
   * @param initialText initial text which will be in the lookup text field
   */
  protected ChooseByNameBase(Project project,
                             @NotNull ChooseByNameModel model,
                             @NotNull ChooseByNameItemProvider provider,
                             String initialText,
                             final int initialIndex) {
    myProject = project;
    myModel = model;
    myInitialText = initialText;
    myProvider = provider;
    myInitialIndex = initialIndex;
    mySearchInAnyPlace = Registry.is("ide.goto.middle.matching") && model.useMiddleMatching();
    myRebuildDelay = Registry.intValue("ide.goto.rebuild.delay");

    myTextField.setText(myInitialText);
    myInitIsDone = true;
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
    if (myToolArea != null) {
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

  @NotNull
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
      if (PlatformDataKeys.SEARCH_INPUT_TEXT.is(dataId)) {
        return myTextField == null ? null : myTextField.getText();
      }

      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return myModel.getHelpId();
      }

      if (myCalcElementsThread != null) {
        return null;
      }
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
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
          List<PsiElement> result = new ArrayList<>(chosenElements.size());
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
      else if (PlatformDataKeys.SEARCH_INPUT_TEXT.is(dataId)) {
        return myTextField == null ? null : myTextField.getText();
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
   * @param modalityState          - if not null rebuilds list in given {@link ModalityState}
   */
  protected void initUI(final ChooseByNamePopupComponent.Callback callback,
                        final ModalityState modalityState,
                        final boolean allowMultipleSelection) {
    myPreviouslyFocusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);

    myActionListener = callback;
    myTextFieldPanel.setLayout(new BoxLayout(myTextFieldPanel, BoxLayout.Y_AXIS));

    final JPanel hBox = new JPanel();
    hBox.setLayout(new BoxLayout(hBox, BoxLayout.X_AXIS));

    JPanel caption2Tools = new JPanel(new BorderLayout());

    if (myModel.getPromptText() != null) {
      JLabel label = new JLabel(myModel.getPromptText());
      label.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
      caption2Tools.add(label, BorderLayout.WEST);
    }

    caption2Tools.add(hBox, BorderLayout.EAST);

    myCardContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));  // space between checkbox and filter/show all in view buttons

    final String checkBoxName = myModel.getCheckBoxName();
    myCheckBox = new JCheckBox(checkBoxName != null ? checkBoxName +
                                                      (myCheckBoxShortcut != null && myCheckBoxShortcut.getShortcuts().length > 0
                                                       ? " (" + KeymapUtil.getShortcutsText(myCheckBoxShortcut.getShortcuts()) + ")"
                                                       : "")
                                                    : "");
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
        final Object[] objects = myListModel.toArray();
        final List<PsiElement> prefixMatchElements = new ArrayList<>(objects.length);
        final List<PsiElement> nonPrefixMatchElements = new ArrayList<>(objects.length);
        List<PsiElement> curElements = prefixMatchElements;
        for (Object object : objects) {
          if (object instanceof PsiElement) {
            curElements.add((PsiElement)object);
          }
          else if (object instanceof DataProvider) {
            final PsiElement psi = CommonDataKeys.PSI_ELEMENT.getData((DataProvider)object);
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

    if (myToolArea == null) {
      myToolArea = new JLabel(EmptyIcon.create(1, 24));
    }
    hBox.add(myToolArea);
    hBox.add(toolbarComponent);

    myTextFieldPanel.add(caption2Tools);

    final ActionMap actionMap = new ActionMap();
    actionMap.setParent(myTextField.getActionMap());
    actionMap.put(DefaultEditorKit.copyAction, new AbstractAction() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
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
    Font editorFont = EditorUtil.getEditorFont();
    myTextField.setFont(editorFont);

    if (checkBoxName != null) {
      if (myCheckBox != null && myCheckBoxShortcut != null) {
        new DumbAwareAction("change goto check box", null, null) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            myCheckBox.setSelected(!myCheckBox.isSelected());
          }
        }.registerCustomShortcutSet(myCheckBoxShortcut, myTextField);
      }
    }

    if (isCloseByFocusLost()) {
      myTextField.addFocusListener(new FocusAdapter() {
        @Override
        public void focusLost(@NotNull final FocusEvent e) {
          if (Registry.is("focus.follows.mouse.workarounds")) {
            if (myFocusPoint != null) {
              PointerInfo pointerInfo = MouseInfo.getPointerInfo();
              if (pointerInfo != null && myFocusPoint.equals(pointerInfo.getLocation())) {
                // Ignore the loss of focus if the mouse hasn't moved between the last dropdown resize
                // and the loss of focus event. This happens in focus follows mouse mode if the mouse is
                // over the dropdown and it resizes to leave the mouse outside the dropdown.
                IdeFocusManager.getInstance(myProject).requestFocus(myTextField, true);
                myFocusPoint = null;
                return;
              }
            }
            myFocusPoint = null;
          }
          cancelListUpdater(); // cancel thread as early as possible
          myHideAlarm.addRequest(() -> {
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
              if (oppositeComponent == myCheckBox) {
                IdeFocusManager.getInstance(myProject).requestFocus(myTextField, true);
                return;
              }
              if (oppositeComponent != null && !(oppositeComponent instanceof JFrame) &&
                  myList.isShowing() &&
                  (oppositeComponent == myList || SwingUtilities.isDescendingFrom(myList, oppositeComponent))) {
                IdeFocusManager.getInstance(myProject).requestFocus(myTextField, true);// Otherwise me may skip some KeyEvents
                return;
              }

              if (isDescendingFromTemporarilyFocusableToolWindow(oppositeComponent)) {
                return; // Allow toolwindows to gain focus (used by QuickDoc shown in a toolwindow)
              }

              hideHint();
            }
          }, 5);
        }
      });
    }

    if (myCheckBox != null) {
      myCheckBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(@NotNull ItemEvent e) {
          rebuildList(false);
        }
      });
      myCheckBox.setFocusable(false);
    }

    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        clearPostponedOkAction(false);
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
            ScrollingUtil.moveDown(myList, e.getModifiersEx());
            break;
          case KeyEvent.VK_UP:
            ScrollingUtil.moveUp(myList, e.getModifiersEx());
            break;
          case KeyEvent.VK_PAGE_UP:
            ScrollingUtil.movePageUp(myList);
            break;
          case KeyEvent.VK_PAGE_DOWN:
            ScrollingUtil.movePageDown(myList);
            break;
          case KeyEvent.VK_TAB:
            close(true);
            break;
          case KeyEvent.VK_ENTER:
            if (myList.getSelectedValue() == EXTRA_ELEM) {
              myMaximumListSizeLimit += myListSizeIncreasing;
              rebuildList(myList.getSelectedIndex(), myRebuildDelay, ModalityState.current(), null);
              e.consume();
            }
            break;
        }

        if (myList.getSelectedValue() == NON_PREFIX_SEPARATOR) {
          if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_PAGE_UP) {
            ScrollingUtil.moveUp(myList, e.getModifiersEx());
          }
          else {
            ScrollingUtil.moveDown(myList, e.getModifiersEx());
          }
        }
      }
    });

    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent actionEvent) {
        doClose(true);
      }
    });

    myList.setFocusable(false);
    myList.setSelectionMode(allowMultipleSelection ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION :
                            ListSelectionModel.SINGLE_SELECTION);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (!myTextField.hasFocus()) {
          IdeFocusManager.getInstance(myProject).requestFocus(myTextField, true);
        }

        if (clickCount == 2) {
          int selectedIndex = myList.getSelectedIndex();
          Rectangle selectedCellBounds = myList.getCellBounds(selectedIndex, selectedIndex);

          if (selectedCellBounds != null && selectedCellBounds.contains(e.getPoint())) { // Otherwise it was reselected in the selection listener
            if (myList.getSelectedValue() == EXTRA_ELEM) {
              myMaximumListSizeLimit += myListSizeIncreasing;
              rebuildList(selectedIndex, myRebuildDelay, ModalityState.current(), null);
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
      public void valueChanged(@NotNull ListSelectionEvent e) {
        if (myList.getSelectedValue() != NON_PREFIX_SEPARATOR) {
          myPreviousSelectionIndex = myList.getSelectedIndex();
          chosenElementMightChange();
          updateDocumentation();
        }
        else if (allowMultipleSelection) {
          myList.setSelectedIndex(myPreviousSelectionIndex);
        }
      }
    });

    myListScrollPane = ScrollPaneFactory.createScrollPane(myList);
    myListScrollPane.setViewportBorder(JBUI.Borders.empty());

    myTextFieldPanel.setBorder(JBUI.Borders.empty(5));

    showTextFieldPanel();

    myInitialized = true;

    if (modalityState != null) {
      rebuildList(myInitialIndex, 0, modalityState, null);
    }
  }

  private boolean isDescendingFromTemporarilyFocusableToolWindow(@Nullable Component component) {
    if (component == null || myProject == null || myProject.isDisposed()) return false;

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowManager.getActiveToolWindowId());
    JComponent toolWindowComponent = toolWindow != null ? toolWindow.getComponent() : null;
    return toolWindowComponent != null &&
           toolWindowComponent.getClientProperty(TEMPORARILY_FOCUSABLE_COMPONENT_KEY) != null &&
           SwingUtilities.isDescendingFrom(component, toolWindowComponent);
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
    Set<KeyStroke> result = new HashSet<>();
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
   * Default rebuild list. It uses {@link #myRebuildDelay} and current modality state.
   */
  public void rebuildList(boolean initial) {
    // TODO this method is public, because the chooser does not listed for the model.
    rebuildList(initial ? myInitialIndex : 0, myRebuildDelay, ModalityState.current(), null);
  }

  private void updateDocumentation() {
    final JBPopup hint = myTextFieldPanel.getHint();
    final Object element = getChosenElement();
    if (hint != null) {
      if (element instanceof PsiElement) {
        myTextFieldPanel.updateHint((PsiElement)element);
      }
      else if (element instanceof DataProvider) {
        final Object o = ((DataProvider)element).getData(CommonDataKeys.PSI_ELEMENT.getName());
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
    if (checkDisposed()) return;

    if (closeForbidden(ok)) return;
    if (postponeCloseWhenListReady(ok)) return;

    cancelListUpdater();
    close(ok);

    clearPostponedOkAction(ok);
    myListModel.clear();
  }

  protected boolean closeForbidden(boolean ok) {
    return false;
  }

  protected void cancelListUpdater() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (checkDisposed()) return;

    final CalcElementsThread calcElementsThread = myCalcElementsThread;
    if (calcElementsThread != null) {
      calcElementsThread.cancel();
      backgroundCalculationFinished(Collections.emptyList(), 0);
    }
    myListUpdater.cancelAll();
  }

  private boolean postponeCloseWhenListReady(boolean ok) {
    if (!isToFixLostTyping()) return false;

    final String text = getTrimmedText();
    if (ok && myCalcElementsThread != null && !text.isEmpty()) {
      myPostponedOkAction = new ActionCallback();
      IdeFocusManager.getInstance(myProject).typeAheadUntil(myPostponedOkAction);
      return true;
    }

    return false;
  }

  @NotNull public String getTrimmedText() {
    return StringUtil.trimLeading(StringUtil.notNullize(myTextField.getText()));
  }

  public void setFixLostTyping(boolean fixLostTyping) {
    myFixLostTyping = fixLostTyping;
  }

  protected boolean isToFixLostTyping() {
    return myFixLostTyping && Registry.is("actionSystem.fixLostTyping");
  }

  @NotNull
  private synchronized String[] ensureNamesLoaded(boolean checkboxState) {
    String[] cached = getNamesSync(checkboxState);
    if (cached != null) return cached;

    if (checkboxState &&
        myModel instanceof ContributorsBasedGotoByModel &&
        ((ContributorsBasedGotoByModel)myModel).sameNamesForProjectAndLibraries() &&
        getNamesSync(false) != null) {
      // there is no way in indices to have different keys for project symbols vs libraries, we always have same ones
      String[] allNames = getNamesSync(false);
      setNamesSync(true, allNames);
      return allNames;
    }

    String[] result = myModel.getNames(checkboxState);
    //noinspection ConstantConditions
    assert result != null : "Model "+myModel+ "("+myModel.getClass()+") returned null names";
    setNamesSync(checkboxState, result);

    return result;
  }

  @NotNull
  public String[] getNames(boolean checkboxState) {
    if (ourLoadNamesEachTime) {
      setNamesSync(checkboxState, null);
      return ensureNamesLoaded(checkboxState);
    }
    return getNamesSync(checkboxState);
  }

  private String[] getNamesSync(boolean checkboxState) {
    synchronized (myNames) {
      return myNames[checkboxState ? 1 : 0];
    }
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
    builder.setLocateWithinScreenBounds(false);
    builder.setKeyEventHandler(event -> {
      if (myTextPopup == null || !AbstractPopup.isCloseRequest(event) || !myTextPopup.isCancelKeyEnabled()) {
        return false;
      }

      IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
      if (isDescendingFromTemporarilyFocusableToolWindow(focusManager.getFocusOwner())) {
        focusManager.requestFocus(myTextField, true);
        return false;
      }
      else {
        myTextPopup.cancel(event);
        return true;
      }
    }).setCancelCallback(() -> {
      myTextPopup = null;
      close(false);
      return Boolean.TRUE;
    }).setFocusable(true).setRequestFocus(true).setModalContext(false).setCancelOnClickOutside(false);

    Point point = new Point(x, y);
    SwingUtilities.convertPointToScreen(point, layeredPane);
    Rectangle bounds = new Rectangle(point, new Dimension(preferredTextFieldPanelSize.width + 20, preferredTextFieldPanelSize.height));
    myTextPopup = builder.createPopup();
    myTextPopup.setSize(bounds.getSize());
    myTextPopup.setLocation(bounds.getLocation());

    MnemonicHelper.init(myTextFieldPanel);
    if (myProject != null && !myProject.isDefault()) {
      DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(myTextPopup);
    }

    Disposer.register(myTextPopup, new Disposable() {
      @Override
      public void dispose() {
        cancelListUpdater();
      }
    });
    myTextPopup.show(layeredPane);
    if (myTextPopup instanceof AbstractPopup) {
      Window window = ((AbstractPopup)myTextPopup).getPopupWindow();
      if (window instanceof JDialog) {
        ((JDialog)window).getRootPane().putClientProperty(WindowAction.NO_WINDOW_ACTIONS, Boolean.TRUE);
      }
    }
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

  protected void rebuildList(final int pos,
                             final int delay,
                             @NotNull final ModalityState modalityState,
                             @Nullable final Runnable postRunnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myInitialized) {
      return;
    }

    myAlarm.cancelAllRequests();

    if (delay > 0) {
      myAlarm.addRequest(() -> rebuildList(pos, 0, modalityState, postRunnable), delay, ModalityState.stateForComponent(myTextField));
      return;
    }

    myListUpdater.cancelAll();

    final CalcElementsThread calcElementsThread = myCalcElementsThread;
    if (calcElementsThread != null) {
      calcElementsThread.cancel();
    }

    final String text = getTrimmedText();
    if (!canShowListForEmptyPattern() && text.isEmpty()) {
      myListModel.clear();
      hideList();
      myTextFieldPanel.hideHint();
      myCard.show(myCardContainer, CHECK_BOX_CARD);
      return;
    }

    ListCellRenderer cellRenderer = myList.getCellRenderer();
    if (cellRenderer instanceof ExpandedItemListCellRendererWrapper) {
      cellRenderer = ((ExpandedItemListCellRendererWrapper)cellRenderer).getWrappee();
    }
    final String pattern = patternToLowerCase(transformPattern(text));
    final Matcher matcher = buildPatternMatcher(isSearchInAnyPlace() ? "*" + pattern : pattern);
    if (cellRenderer instanceof MatcherHolder) {
      ((MatcherHolder)cellRenderer).setPatternMatcher(matcher);
    }
    MatcherHolder.associateMatcher(myList, matcher);

    scheduleCalcElements(text, myCheckBox.isSelected(), modalityState, elements -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      backgroundCalculationFinished(elements, pos);

      if (postRunnable != null) {
        postRunnable.run();
      }
    });
  }

  private void backgroundCalculationFinished(Collection<?> result, int toSelect) {
    myCalcElementsThread = null;
    setElementsToList(toSelect, result);
    myList.repaint();
    chosenElementMightChange();

    if (result.isEmpty()) {
      myTextFieldPanel.hideHint();
    }
  }

  public void scheduleCalcElements(String text,
                                   boolean checkboxState,
                                   ModalityState modalityState,
                                   Consumer<Set<?>> callback) {
    new CalcElementsThread(text, checkboxState, callback, modalityState).scheduleThread();
  }

  private boolean isShowListAfterCompletionKeyStroke() {
    return myShowListAfterCompletionKeyStroke;
  }

  private void setElementsToList(int pos, @NotNull Collection<?> elements) {
    myListUpdater.cancelAll();
    if (checkDisposed()) return;
    if (isCloseByFocusLost() && Registry.is("focus.follows.mouse.workarounds")) {
      PointerInfo pointerInfo = MouseInfo.getPointerInfo();
      if (pointerInfo != null) {
        myFocusPoint = pointerInfo.getLocation();
      }
    }
    if (elements.isEmpty()) {
      myListModel.clear();
      myTextField.setForeground(JBColor.red);
      myListUpdater.cancelAll();
      hideList();
      clearPostponedOkAction(false);
      return;
    }

    Object[] oldElements = myListModel.toArray();
    Object[] newElements = elements.toArray();
    List<ModelDiff.Cmd> commands = ModelDiff.createDiffCmds(myListModel, oldElements, newElements);
    if (commands == null) {
      myListUpdater.doPostponedOkIfNeeded();
      return; // Nothing changed
    }

    myTextField.setForeground(UIUtil.getTextFieldForeground());
    if (commands.isEmpty()) {
      if (pos <= 0) {
        pos = detectBestStatisticalPosition();
      }

      ScrollingUtil.selectItem(myList, Math.min(pos, myListModel.size() - 1));
      myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
      showList();
      myTextFieldPanel.repositionHint();
    }
    else {
      showList();
      myListUpdater.appendToModel(commands, pos);
    }
  }

  private int detectBestStatisticalPosition() {
    if (myModel instanceof Comparator) {
      return 0;
    }

    int best = 0;
    int bestPosition = 0;
    int bestMatch = Integer.MIN_VALUE;
    final int count = myListModel.getSize();

    Matcher matcher = buildPatternMatcher(transformPattern(getTrimmedText()));

    final String statContext = statisticsContext();
    for (int i = 0; i < count; i++) {
      final Object modelElement = myListModel.getElementAt(i);
      String text = EXTRA_ELEM.equals(modelElement) || NON_PREFIX_SEPARATOR.equals(modelElement) ? null : myModel.getFullName(modelElement);
      if (text != null) {
        String shortName = myModel.getElementName(modelElement);
        int match = shortName != null && matcher instanceof MinusculeMatcher
                    ? ((MinusculeMatcher)matcher).matchingDegree(shortName) : Integer.MIN_VALUE;
        int stats = StatisticsManager.getInstance().getUseCount(new StatisticsInfo(statContext, text));
        if (match > bestMatch || match == bestMatch && stats > best) {
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
    return "choose_by_name#" + myModel.getPromptText() + "#" + myCheckBox.isSelected() + "#" + getTrimmedText();
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

          if (!myCommands.isEmpty()) {
            myAlarm.addRequest(this, DELAY);
          }
          else {
            doPostponedOkIfNeeded();
          }
          if (!checkDisposed()) {
            showList();
            myTextFieldPanel.repositionHint();

            if (!myListModel.isEmpty()) {
              int pos = selectionPos <= 0 ? detectBestStatisticalPosition() : selectionPos;
              ScrollingUtil.selectItem(myList, Math.min(pos, myListModel.size() - 1));
            }
          }
        }
      }, DELAY);
    }

    private void doPostponedOkIfNeeded() {
      if (myPostponedOkAction != null) {
        if (getChosenElement() != null) {
          doClose(true);
        }
        clearPostponedOkAction(checkDisposed());
      }
    }
  }

  private void clearPostponedOkAction(boolean success) {
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

  public boolean hasPostponedAction() {
    return myPostponedOkAction != null;
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
    return ContainerUtil.filter(myList.getSelectedValues(), o -> o != EXTRA_ELEM && o != NON_PREFIX_SEPARATOR);
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
      if (!UIUtil.isUnderGTKLookAndFeel()) {
        if (SystemInfo.isMac && UIUtil.isUnderIntelliJLaF()) {
          if (!(getUI() instanceof MacIntelliJTextFieldUI)) {
            setUI(MacIntelliJTextFieldUI.createUI(this));
          }
          setBorder(new MacIntelliJTextBorder());
        } else {
          if (!(getUI() instanceof DarculaTextFieldUI)) {
            setUI(DarculaTextFieldUI.createUI(this));
          }
          setBorder(new DarculaTextBorder());
        }
      }
      enableEvents(AWTEvent.KEY_EVENT_MASK);
      myCompletionKeyStroke = getShortcut(IdeActions.ACTION_CODE_COMPLETION);
      forwardStroke = getShortcut(IdeActions.ACTION_GOTO_FORWARD);
      backStroke = getShortcut(IdeActions.ACTION_GOTO_BACK);
      setFocusTraversalKeysEnabled(false);
      putClientProperty("JTextField.variant", "search");
      setDocument(new PlainDocument() {
        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
          super.insertString(offs, str, a);
          if (str != null && str.length() > 1) {
            handlePaste(str);
          }
        }
      });
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
        final String pattern = getTrimmedText();
        final int oldPos = myList.getSelectedIndex();
        myHistory.add(Pair.create(pattern, oldPos));
        final Runnable postRunnable = () -> fillInCommonPrefix(pattern);
        rebuildList(0, 0, ModalityState.current(), postRunnable);
        return;
      }
      if (backStroke != null && keyStroke.equals(backStroke)) {
        e.consume();
        if (!myHistory.isEmpty()) {
          final String oldText = getTrimmedText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> last = myHistory.remove(myHistory.size() - 1);
          myTextField.setText(last.first);
          myFuture.add(Pair.create(oldText, oldPos));
          rebuildList(0, 0, ModalityState.current(), null);
        }
        return;
      }
      if (forwardStroke != null && keyStroke.equals(forwardStroke)) {
        e.consume();
        if (!myFuture.isEmpty()) {
          final String oldText = getTrimmedText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> next = myFuture.remove(myFuture.size() - 1);
          myTextField.setText(next.first);
          myHistory.add(Pair.create(oldText, oldPos));
          rebuildList(0, 0, ModalityState.current(), null);
        }
        return;
      }
      int position = myTextField.getCaretPosition();
      int code = keyStroke.getKeyCode();
      int modifiers = keyStroke.getModifiers();
      try {
        super.processKeyEvent(e);
      }
      catch (NullPointerException e1) {
        if (!Patches.SUN_BUG_ID_6322854) {
          throw e1;
        }
      }
      finally {
        if ((code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) && modifiers == 0) {
          myTextField.setCaretPosition(position);
        }
      }
    }

    private void fillInCommonPrefix(@NotNull final String pattern) {
      final List<String> list = myProvider.filterNames(ChooseByNameBase.this, getNames(myCheckBox.isSelected()), pattern);
      if (list.isEmpty()) return;

      if (isComplexPattern(pattern)) return; //TODO: support '*'
      final String oldText = getTrimmedText();
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

  public ChooseByNameItemProvider getProvider() {
    return myProvider;
  }

  protected void handlePaste(String str) {
    if (!myInitIsDone) return;
    if (myModel instanceof GotoClassModel2 && isFileName(str)) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        GotoFileAction gotoFile = new GotoFileAction();
        DataContext context = DataManager.getInstance().getDataContext(myTextField);
        gotoFile.actionPerformed(AnActionEvent.createFromAnAction(gotoFile, null, ActionPlaces.UNKNOWN, context));
      });
    }
  }

  private static boolean isFileName(String name) {
    final int index = name.lastIndexOf('.');
    if (index > 0) {
      String ext = name.substring(index + 1);
      if (ext.contains(":")) {
        ext = ext.substring(0, ext.indexOf(':'));
      }
      if (FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(ext) != UnknownFileType.INSTANCE) {
        return true;
      }
    }
    return false;
  }

  public static final String EXTRA_ELEM = "...";
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

  private class CalcElementsThread extends ReadTask {
    private final String myPattern;
    private final boolean myCheckboxState;
    private final Consumer<Set<?>> myCallback;
    private final ModalityState myModalityState;

    private final ProgressIndicator myProgress = new ProgressIndicatorBase();

    CalcElementsThread(String pattern,
                       boolean checkboxState,
                       Consumer<Set<?>> callback,
                       @NotNull ModalityState modalityState) {
      myPattern = pattern;
      myCheckboxState = checkboxState;
      myCallback = callback;
      myModalityState = modalityState;
    }

    private final Alarm myShowCardAlarm = new Alarm();

    void scheduleThread() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myCalcElementsThread = this;
      showCard(SEARCHING_CARD, 200);
      ProgressIndicatorUtils.scheduleWithWriteActionPriority(myProgress, this);
    }

    @Override
    public Continuation runBackgroundProcess(@NotNull final ProgressIndicator indicator) {
      if (DumbService.isDumbAware(myModel)) return super.runBackgroundProcess(indicator);

      return DumbService.getInstance(myProject).runReadActionInSmartMode(() -> performInReadAction(indicator));
    }

    @Nullable
    @Override
    public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
      if (myProject != null && myProject.isDisposed()) return null;

      final Set<Object> elements = new LinkedHashSet<>();

      boolean scopeExpanded = fillWithScopeExpansion(elements, myPattern);

      String lowerCased = patternToLowerCase(myPattern);
      if (elements.isEmpty() && !lowerCased.equals(myPattern)) {
        scopeExpanded = fillWithScopeExpansion(elements, lowerCased);
      }
      final String cardToShow = elements.isEmpty() ? NOT_FOUND_CARD : scopeExpanded ? NOT_FOUND_IN_PROJECT_CARD : CHECK_BOX_CARD;

      final boolean edt = myModel instanceof EdtSortingModel;
      final Set<Object> filtered = !edt ? filter(elements) : Collections.emptySet();
      return new Continuation(() -> {
        if (!checkDisposed() && !myProgress.isCanceled()) {
          CalcElementsThread currentBgProcess = myCalcElementsThread;
          LOG.assertTrue(currentBgProcess == this, currentBgProcess);

          showCard(cardToShow, 0);

          myCallback.consume(edt ? filter(elements) : filtered);
        }
      }, myModalityState);
    }

    private boolean fillWithScopeExpansion(Set<Object> elements, String pattern) {
      if (!ourLoadNamesEachTime) ensureNamesLoaded(myCheckboxState);
      addElementsByPattern(pattern, elements, myProgress, myCheckboxState);

      if (elements.isEmpty() && !myCheckboxState) {
        if (!ourLoadNamesEachTime) ensureNamesLoaded(true);
        addElementsByPattern(pattern, elements, myProgress, true);
        return true;
      }
      return false;
    }

    @Override
    public void onCanceled(@NotNull ProgressIndicator indicator) {
      LOG.assertTrue(myCalcElementsThread == this, myCalcElementsThread);
      new CalcElementsThread(myPattern, myCheckboxState, myCallback, myModalityState).scheduleThread();
    }

    private void addElementsByPattern(@NotNull String pattern,
                                      @NotNull final Set<Object> elements,
                                      @NotNull final ProgressIndicator indicator,
                                      boolean everywhere) {
      long start = System.currentTimeMillis();
      myProvider.filterElements(
        ChooseByNameBase.this, pattern, everywhere,
        indicator,
        o -> {
          if (indicator.isCanceled()) return false;
          elements.add(o);

          if (isOverflow(elements)) {
            elements.add(EXTRA_ELEM);
            return false;
          }
          return true;
        }
      );
      if (myAlwaysHasMore) {
        elements.add(EXTRA_ELEM);
      }
      if (ContributorsBasedGotoByModel.LOG.isDebugEnabled()) {
        long end = System.currentTimeMillis();
        ContributorsBasedGotoByModel.LOG.debug("addElementsByPattern("+pattern+"): "+(end-start)+"ms; "+elements.size()+" elements");
      }
    }

    private void showCard(final String card, final int delay) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      myShowCardAlarm.cancelAllRequests();
      myShowCardAlarm.addRequest(() -> {
        if (!myProgress.isCanceled()) {
          myCard.show(myCardContainer, card);
        }
      }, delay, myModalityState);
    }

    protected boolean isOverflow(@NotNull Set<Object> elementsArray) {
      return elementsArray.size() >= myMaximumListSizeLimit;
    }

    private void cancel() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myProgress.cancel();
    }

  }

  @NotNull
  private String patternToLowerCase(String pattern) {
    return pattern.toLowerCase(Locale.US);
  }


  public boolean canShowListForEmptyPattern() {
    return isShowListForEmptyPattern() || isShowListAfterCompletionKeyStroke() && lastKeyStrokeIsCompletion();
  }

  protected boolean lastKeyStrokeIsCompletion() {
    return myTextField.isCompletionKeyStroke();
  }

  private static Matcher buildPatternMatcher(@NotNull String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
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

  public void setMaximumListSizeLimit(final int maximumListSizeLimit) {
    myMaximumListSizeLimit = maximumListSizeLimit;
  }

  public void setListSizeIncreasing(final int listSizeIncreasing) {
    myListSizeIncreasing = listSizeIncreasing;
  }

  public boolean isAlwaysHasMore() {
    return myAlwaysHasMore;
  }

  /**
   * Display <tt>...</tt> item at the end of the list regardless of whether it was filled up or not.
   * This option can be useful in cases, when it can't be said beforehand, that the next call to {@link ChooseByNameItemProvider}
   * won't give new items.
   */
  public void setAlwaysHasMore(boolean enabled) {
    myAlwaysHasMore = enabled;
  }

  private static final String ACTION_NAME = "Show All in View";

  private abstract class ShowFindUsagesAction extends DumbAwareAction {
    public ShowFindUsagesAction() {
      super(ACTION_NAME, ACTION_NAME, AllIcons.General.AutohideOff);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      cancelListUpdater();

      final UsageViewPresentation presentation = new UsageViewPresentation();
      final String text = getTrimmedText();
      final String prefixPattern = myFindUsagesTitle + " \'" + text + "\'";
      final String nonPrefixPattern = myFindUsagesTitle + " \'*" + text + "*\'";
      presentation.setCodeUsagesString(prefixPattern);
      presentation.setUsagesInGeneratedCodeString(prefixPattern + " in generated code");
      presentation.setDynamicUsagesString(nonPrefixPattern);
      presentation.setTabName(prefixPattern);
      presentation.setTabText(prefixPattern);
      presentation.setTargetsNodeText("Unsorted " + StringUtil.toLowerCase(patternToLowerCase(prefixPattern)));
      final Object[][] elements = getElements();
      final List<PsiElement> targets = new ArrayList<>();
      final Set<Usage> usages = new LinkedHashSet<>();
      fillUsages(Arrays.asList(elements[0]), usages, targets, false);
      fillUsages(Arrays.asList(elements[1]), usages, targets, true);
      if (myListModel.contains(EXTRA_ELEM)) { //start searching for the rest
        final boolean everywhere = myCheckBox.isSelected();
        final Set<Object> prefixMatchElementsArray = new LinkedHashSet<>();
        final Set<Object> nonPrefixMatchElementsArray = new LinkedHashSet<>();
        hideHint();
        ProgressManager.getInstance().run(new Task.Modal(myProject, prefixPattern, true) {
          private ChooseByNameBase.CalcElementsThread myCalcUsagesThread;
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            ensureNamesLoaded(everywhere);
            indicator.setIndeterminate(true);
            final TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.createFor(indicator);
            myCalcUsagesThread = new CalcElementsThread(text, everywhere, null, ModalityState.NON_MODAL) {
              @Override
              protected boolean isOverflow(@NotNull Set<Object> elementsArray) {
                tooManyUsagesStatus.pauseProcessingIfTooManyUsages();
                if (elementsArray.size() > UsageLimitUtil.USAGES_LIMIT - myMaximumListSizeLimit && tooManyUsagesStatus.switchTooManyUsagesStatus()) {
                  int usageCount = elementsArray.size() + myMaximumListSizeLimit;
                  UsageViewManagerImpl.showTooManyUsagesWarning(getProject(), tooManyUsagesStatus, indicator, presentation, usageCount, null);
                }
                return false;
              }
            };

            ApplicationManager.getApplication().runReadAction(() -> {
              boolean anyPlace = isSearchInAnyPlace();
              setSearchInAnyPlace(false);
              myCalcUsagesThread.addElementsByPattern(text, prefixMatchElementsArray, indicator, everywhere);
              setSearchInAnyPlace(anyPlace);

              if (anyPlace && !indicator.isCanceled()) {
                myCalcUsagesThread.addElementsByPattern(text, nonPrefixMatchElementsArray, indicator, everywhere);
                nonPrefixMatchElementsArray.removeAll(prefixMatchElementsArray);
              }

              indicator.setText("Prepare...");
              fillUsages(prefixMatchElementsArray, usages, targets, false);
              fillUsages(nonPrefixMatchElementsArray, usages, targets, true);
            });
          }

          @Override
          public void onSuccess() {
            showUsageView(targets, usages, presentation);
          }

          @Override
          public void onCancel() {
            myCalcUsagesThread.cancel();
          }

          @Override
          public void onError(@NotNull Exception error) {
            super.onError(error);
            myCalcUsagesThread.cancel();
          }
        });
      }
      else {
        hideHint();
        showUsageView(targets, usages, presentation);
      }
    }

    private void fillUsages(Collection<Object> matchElementsArray,
                            Collection<Usage> usages,
                            List<PsiElement> targets,
                            final boolean separateGroup) {
      for (Object o : matchElementsArray) {
        if (o instanceof PsiElement) {
          PsiElement element = (PsiElement)o;
          if (element.getTextRange() != null) {
            usages.add(new MyUsageInfo2UsageAdapter(element, separateGroup));
          }
          else {
            targets.add(element);
          }
        }
      }
    }

    private void showUsageView(@NotNull List<PsiElement> targets,
                               @NotNull Collection<Usage> usages,
                               @NotNull UsageViewPresentation presentation) {
      UsageTarget[] usageTargets = targets.isEmpty() ? UsageTarget.EMPTY_ARRAY :
                                   PsiElement2UsageTargetAdapter.convert(PsiUtilCore.toPsiElementArray(targets));
      UsageViewManager.getInstance(myProject).showUsages(usageTargets, usages.toArray(new Usage[usages.size()]), presentation);
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

  private static class MyUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter {
    private final PsiElement myElement;
    private final boolean mySeparateGroup;

    MyUsageInfo2UsageAdapter(@NotNull PsiElement element, boolean separateGroup) {
      super(new UsageInfo(element) {
        @Override
        public boolean isDynamicUsage() {
          return separateGroup || super.isDynamicUsage();
        }
      });
      myElement = element;
      mySeparateGroup = separateGroup;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MyUsageInfo2UsageAdapter)) return false;

      MyUsageInfo2UsageAdapter adapter = (MyUsageInfo2UsageAdapter)o;

      if (mySeparateGroup != adapter.mySeparateGroup) return false;
      if (!myElement.equals(adapter.myElement)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myElement.hashCode();
      result = 31 * result + (mySeparateGroup ? 1 : 0);
      return result;
    }
  }

  public JTextField getTextField() {
    return myTextField;
  }
}
