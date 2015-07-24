/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.ide.actions.GotoFileAction;
import com.intellij.ide.actions.WindowAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.TooManyUsagesStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
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

public abstract class ChooseByNameBase extends ChooseByNameViewModel {

  protected Component myPreviouslyFocusedComponent;

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
  protected final JList myList = new JBList(myListModel);

  protected JBPopup myTextPopup;
  protected JBPopup myDropdownPopup;

  private ShortcutSet myCheckBoxShortcut;

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
    super(project, model, provider, initialText, initialIndex);
    myProvider = provider;
    myTextField.setText(myInitialText);
  }

  public void setShowListAfterCompletionKeyStroke(boolean showListAfterCompletionKeyStroke) {
    myShowListAfterCompletionKeyStroke = showListAfterCompletionKeyStroke;
  }

  /**
   * Set tool area. The method may be called only before invoke.
   *
   * @param toolArea a tool area component
   */
  @Override
  public void setToolArea(JComponent toolArea) {
    if (myToolArea != null) {
      throw new IllegalStateException("Tool area is modifiable only before invoke()");
    }
    myToolArea = toolArea;
  }

  @Override
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

  @Override
  public String getEnteredText() {
    return myTextField.getText();
  }

  @Override
  public int getSelectedIndex() {
    return myList.getSelectedIndex();
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
      if (UIUtil.isUnderAquaLookAndFeel()) {
        label.setBorder(new CompoundBorder(new EmptyBorder(0, 9, 0, 0), label.getBorder()));
      }
      label.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
      caption2Tools.add(label, BorderLayout.WEST);
    }

    caption2Tools.add(hBox, BorderLayout.EAST);

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
    showCardImpl(CHECK_BOX_CARD);

    if (isCheckboxVisible()) {
      hBox.add(myCardContainer);
    }


    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ShowFindUsagesAction() {
      @Override
      public PsiElement[][] getElements() {
        final Object[] objects = myListModel.toArray();
        final List<PsiElement> prefixMatchElements = new ArrayList<PsiElement>(objects.length);
        final List<PsiElement> nonPrefixMatchElements = new ArrayList<PsiElement>(objects.length);
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
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    boolean presentationMode = UISettings.getInstance().PRESENTATION_MODE;
    int size = presentationMode ? UISettings.getInstance().PRESENTATION_MODE_FONT_SIZE - 4 : scheme.getEditorFontSize();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, size);
    myTextField.setFont(editorFont);

    if (checkBoxName != null) {
      if (myCheckBox != null && myCheckBoxShortcut != null) {
        new AnAction("change goto check box", null, null) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            myCheckBox.setSelected(!isCheckboxSelected());
          }
        }.registerCustomShortcutSet(myCheckBoxShortcut, myTextField);
      }
    }

    if (isCloseByFocusLost()) {
      myTextField.addFocusListener(new FocusAdapter() {
        @Override
        public void focusLost(@NotNull final FocusEvent e) {
          cancelListUpdater(); // cancel thread as early as possible
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

                if (oppositeComponent != null && myProject != null && !myProject.isDisposed()) {
                  ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
                  ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowManager.getActiveToolWindowId());
                  if (toolWindow != null) {
                    JComponent toolWindowComponent = toolWindow.getComponent();
                    if (SwingUtilities.isDescendingFrom(oppositeComponent, toolWindowComponent)) {
                      return; // Allow toolwindows to gain focus (used by QuickDoc shown in a toolwindow)
                    }
                  }
                }

                EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
                if (queue instanceof IdeEventQueue) {
                  if (!((IdeEventQueue)queue).wasRootRecentlyClicked(oppositeComponent)) {
                    Component root = SwingUtilities.getRoot(myTextField);
                    if (root != null && root.isShowing()) {
                      IdeFocusManager.getInstance(myProject).requestFocus(myTextField, true);
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
              myMaximumListSizeLimit += myListSizeIncreasing;
              rebuildList(myList.getSelectedIndex(), myRebuildDelay, ModalityState.current(), null);
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

    myTextFieldPanel.setBorder(new EmptyBorder(2, 2, 2, 2));

    showTextFieldPanel();

    myInitialized = true;

    if (modalityState != null) {
      rebuildList(myInitialIndex, 0, modalityState, null);
    }
  }

  @Override
  protected void showCardImpl(String card) {
    myCard.show(myCardContainer, card);
  }

  private void addCard(JComponent comp, String cardId) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(comp, BorderLayout.EAST);
    myCardContainer.add(wrapper, cardId);
  }

  @Override
  public void setCheckBoxShortcut(ShortcutSet shortcutSet) {
    myCheckBoxShortcut = shortcutSet;
  }

  @Override
  protected void hideHint() {
    if (!myTextFieldPanel.focusRequested()) {
      doClose(false);
      doHideHint();
    }
  }

  @Override
  protected void doHideHint() {
    myTextFieldPanel.hideHint();
  }

  /**
   * Default rebuild list. It uses {@link #myRebuildDelay} and current modality state.
   */
  @Override
  public void rebuildList(boolean initial) {
    // TODO this method is public, because the chooser does not listed for the model.
    rebuildList(initial ? myInitialIndex : 0, myRebuildDelay, ModalityState.current(), null);
  }

  @Override
  protected void updateDocumentation() {
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

  @Override
  @NotNull public String getTrimmedText() {
    return StringUtil.trimLeading(StringUtil.notNullize(myTextField.getText()));
  }

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

  @Override
  @NotNull
  protected ModalityState getModalityStateForTextBox() {
    return ModalityState.stateForComponent(myTextField);
  }

  @Override
  protected boolean isCheckboxSelected() {
    return myCheckBox.isSelected();
  }

  @Override
  protected void configureListRenderer() {
    ListCellRenderer cellRenderer = myList.getCellRenderer();
    if (cellRenderer instanceof ExpandedItemListCellRendererWrapper) {
      cellRenderer = ((ExpandedItemListCellRendererWrapper)cellRenderer).getWrappee();
    }
    if (cellRenderer instanceof MatcherHolder) {
      final String pattern = transformPattern(getTrimmedText());
      final Matcher matcher = buildPatternMatcher(isSearchInAnyPlace() ? "*" + pattern : pattern);
      ((MatcherHolder)cellRenderer).setPatternMatcher(matcher);
    }
  }

  @Override
  protected void backgroundCalculationFinished(Collection<?> result, int toSelect) {
    myCalcElementsThread = null;
    setElementsToList(toSelect, result);
    myList.repaint();
    chosenElementMightChange();

    if (result.isEmpty()) {
      doHideHint();
    }
  }

  @Override
  protected void setHasResults(boolean b) {
    myTextField.setForeground(b ? UIUtil.getTextFieldForeground() : JBColor.red);
  }

  @Override
  protected void selectItem(int selectionPos) {
    if (!myListModel.isEmpty()) {
      int pos = selectionPos <= 0 ? detectBestStatisticalPosition() : selectionPos;
      ListScrollingUtil.selectItem(myList, Math.min(pos, myListModel.size() - 1));
    }
  }

  @Override
  protected void repositionHint() {
    myTextFieldPanel.repositionHint();
  }

  @Override
  protected void updateVisibleRowCount() {
    myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
  }

  @Override
  @Nullable
  public Object getChosenElement() {
    final List<Object> elements = getChosenElements();
    return elements != null && elements.size() == 1 ? elements.get(0) : null;
  }

  @Override
  protected List<Object> getChosenElements() {
    return ContainerUtil.filter(myList.getSelectedValues(), new Condition<Object>() {
      @Override
      public boolean value(Object o) {
        return o != EXTRA_ELEM && o != NON_PREFIX_SEPARATOR;
      }
    });
  }

  protected final class MyTextField extends JTextField implements PopupOwner, TypeSafeDataProvider {
    private final KeyStroke myCompletionKeyStroke;
    private final KeyStroke forwardStroke;
    private final KeyStroke backStroke;

    private boolean completionKeyStrokeHappened = false;

    private MyTextField() {
      super(40);
      if (!UIUtil.isUnderGTKLookAndFeel()) {
        if (!(getUI() instanceof DarculaTextFieldUI)) {
          setUI(DarculaTextFieldUI.createUI(this));
        }
        setBorder(new DarculaTextBorder());
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
        final Runnable postRunnable = new Runnable() {
          @Override
          public void run() {
            fillInCommonPrefix(pattern);
          }
        };
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
      if (StringUtil.isEmpty(pattern) && !canShowListForEmptyPattern()) {
        return;
      }

      final List<String> list = myProvider.filterNames(ChooseByNameBase.this, getNames(isCheckboxSelected()), pattern);

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

  @Override
  protected void handlePaste(String str) {
    if (!myInitIsDone) return;
    if (myModel instanceof GotoClassModel2 && isFileName(str)) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          final GotoFileAction gotoFile = new GotoFileAction();
          AnActionEvent event = new AnActionEvent(null,
                                                  DataManager.getInstance().getDataContext(myTextField),
                                                  ActionPlaces.UNKNOWN,
                                                  gotoFile.getTemplatePresentation(),
                                                  ActionManager.getInstance(),
                                                  0);
          event.setInjectedContext(gotoFile.isInInjectedContext());
          gotoFile.actionPerformed(event);
        }
      });
    }
  }

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


  @Override
  protected boolean lastKeyStrokeIsCompletion() {
    return myTextField.isCompletionKeyStroke();
  }

  protected static class HintLabel extends JLabel {
    protected HintLabel(String text) {
      super(text, RIGHT);
      setForeground(Color.darkGray);
    }
  }

  @Override
  protected void doShowCard(final CalcElementsThread t, final String card, int delay) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    t.myShowCardAlarm.cancelAllRequests();
    t.myShowCardAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (!t.myProgress.isCanceled()) {
          showCardImpl(card);
        }
      }
    }, delay, t.myModalityState);
  }

  private abstract class ShowFindUsagesAction extends AnAction {
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
      presentation.setTargetsNodeText("Unsorted " + StringUtil.toLowerCase(prefixPattern.toLowerCase()));
      final Object[][] elements = getElements();
      final List<PsiElement> targets = new ArrayList<PsiElement>();
      final List<Usage> usages = new ArrayList<Usage>();
      fillUsages(Arrays.asList(elements[0]), usages, targets, false);
      fillUsages(Arrays.asList(elements[1]), usages, targets, true);
      if (myListModel.contains(EXTRA_ELEM)) { //start searching for the rest
        final boolean everywhere = isCheckboxSelected();
        final Set<Object> prefixMatchElementsArray = new LinkedHashSet<Object>();
        final Set<Object> nonPrefixMatchElementsArray = new LinkedHashSet<Object>();
        hideHint();
        ProgressManager.getInstance().run(new Task.Modal(myProject, prefixPattern, true) {
          private ChooseByNameBase.CalcElementsThread myCalcUsagesThread;
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            ensureNamesLoaded(everywhere);
            indicator.setIndeterminate(true);
            final TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.createFor(indicator);
            myCalcUsagesThread = new CalcElementsThread(text, everywhere, null, ModalityState.NON_MODAL, false) {
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

            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
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
              }
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

  @Override
  public JTextField getTextField() {
    return myTextField;
  }
}
