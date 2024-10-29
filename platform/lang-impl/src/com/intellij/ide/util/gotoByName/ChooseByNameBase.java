// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.gotoByName;

import com.google.common.primitives.Ints;
import com.intellij.Patches;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.ide.actions.GotoFileAction;
import com.intellij.ide.impl.DataValidators;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
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
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageViewManagerImpl;
import com.intellij.util.*;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

public abstract class ChooseByNameBase implements ChooseByNameViewModel {
  public static final String TEMPORARILY_FOCUSABLE_COMPONENT_KEY = "ChooseByNameBase.TemporarilyFocusableComponent";

  private static final Logger LOG = Logger.getInstance(ChooseByNameBase.class);

  protected final @Nullable Project myProject;
  protected final ChooseByNameModel myModel;
  protected @NotNull ChooseByNameItemProvider myProvider;
  final @NlsSafe String myInitialText;
  private boolean mySearchInAnyPlace;

  Component myPreviouslyFocusedComponent;
  private boolean myInitialized;

  final JPanelProvider myTextFieldPanel = new JPanelProvider();// Located in the layered pane
  protected final MyTextField myTextField = new MyTextField();
  private final CardLayout myCard = new CardLayout();
  private final JPanel myCardContainer = new JPanel(myCard);
  protected final JCheckBox myCheckBox = new JCheckBox();
  /**
   * the tool area of the popup, it is just after card box
   */
  private JComponent myToolArea;

  JScrollPane myListScrollPane; // Located in the layered pane
  private final SmartPointerListModel<Object> myListModel = new SmartPointerListModel<>();
  protected final JList<Object> myList = new JBList<>(myListModel);
  private final List<Pair<@NlsSafe String, Integer>> myHistory = new ArrayList<>();
  private final List<Pair<@NlsSafe String, Integer>> myFuture = new ArrayList<>();

  protected ChooseByNamePopupComponent.Callback myActionListener;

  protected final Alarm myAlarm = new Alarm();

  private boolean myDisposedFlag;

  private final String[][] myNames = new String[2][];
  private volatile CalcElementsThread myCalcElementsThread;
  private int myListSizeIncreasing = 30;
  private int myMaximumListSizeLimit = 30;
  private static final @NonNls String NOT_FOUND_IN_PROJECT_CARD = "syslib";
  private static final @NonNls String NOT_FOUND_CARD = "nfound";
  private static final @NonNls String CHECK_BOX_CARD = "chkbox";
  private static final @NonNls String SEARCHING_CARD = "searching";
  private final int myRebuildDelay;

  private final Alarm myHideAlarm = new Alarm();
  private static final boolean myShowListAfterCompletionKeyStroke = false;
  JBPopup myTextPopup;
  protected JBPopup myDropdownPopup;

  private boolean myClosedByShiftEnter;
  final int myInitialIndex;

  private Function<Set<Object>, Object> myInitialSelection;
  private @Nls String myFindUsagesTitle;
  private ShortcutSet myCheckBoxShortcut;
  private final boolean myInitIsDone;
  private boolean myAlwaysHasMore;
  private Point myFocusPoint;
  @Nullable SelectionSnapshot currentChosenInfo;

  public boolean checkDisposed() {
    return myDisposedFlag;
  }

  public void setDisposed(boolean disposedFlag) {
    myDisposedFlag = disposedFlag;
    if (disposedFlag) {
      setNamesSync(true, null);
      setNamesSync(false, null);
    }
  }

  private void setNamesSync(boolean checkboxState, String @Nullable [] value) {
    synchronized (myNames) {
      myNames[checkboxState ? 1 : 0] = value;
    }
  }

  /**
   * @param initialText initial text which will be in the lookup text field
   */
  protected ChooseByNameBase(Project project, @NotNull ChooseByNameModel model, String initialText, PsiElement context) {
    this(project, model, ChooseByNameModelEx.getItemProvider(model, context), initialText, 0);
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
  protected ChooseByNameBase(@Nullable Project project,
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

  @Override
  public @Nullable Project getProject() {
    return myProject;
  }

  @Override
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
  public void setToolArea(@NotNull JComponent toolArea) {
    if (myToolArea != null) {
      throw new IllegalStateException("Tool area is modifiable only before invoke()");
    }
    myToolArea = toolArea;
  }

  public void setFindUsagesTitle(@Nullable @Nls String findUsagesTitle) {
    myFindUsagesTitle = findUsagesTitle;
  }

  public void invoke(final ChooseByNamePopupComponent.Callback callback,
                     final ModalityState modalityState,
                     boolean allowMultipleSelection) {
    initUI(callback, modalityState, allowMultipleSelection);
  }

  @Override
  public @NotNull ChooseByNameModel getModel() {
    return myModel;
  }

  public void setInitialSelection(Function<Set<Object>, Object> initialSelection) {
    myInitialSelection = initialSelection;
  }

  public final class JPanelProvider extends JPanel implements UiDataProvider, QuickSearchComponent {
    private JBPopup myHint;
    private boolean myFocusRequested;

    JPanelProvider() {
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      List<Object> selection = getChosenElements();
      sink.set(PlatformCoreDataKeys.HELP_ID, myModel.getHelpId());
      sink.set(PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE, getBounds());

      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
        if (myCalcElementsThread != null) return null;
        return getElement(ContainerUtil.getOnlyItem(selection));
      });
      sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
        if (myCalcElementsThread != null) return null;
        List<PsiElement> result = ContainerUtil.mapNotNull(
          selection, o -> getElement(o));
        return PsiUtilCore.toPsiElementArray(result);
      });
    }

    private static @Nullable PsiElement getElement(Object element) {
      if (element instanceof PsiElement o) {
        return o;
      }
      if (element instanceof DataProvider o) {
        PsiElement data = CommonDataKeys.PSI_ELEMENT.getData(o);
        return data == null ? null : (PsiElement)DataValidators.validOrNull(
          data, CommonDataKeys.PSI_ELEMENT.getName(), element);
      }
      return null;
    }

    @Override
    public void registerHint(@NotNull JBPopup h) {
      if (myHint != null && myHint.isVisible() && myHint != h) {
        myHint.cancel();
      }
      myHint = h;
    }

    boolean focusRequested() {
      boolean focusRequested = myFocusRequested;

      myFocusRequested = false;

      return focusRequested;
    }

    @Override
    public void requestFocus() {
      myFocusRequested = true;
    }

    @Override
    public void unregisterHint() {
      myHint = null;
    }

    public void hideHint() {
      if (myHint != null) {
        myHint.cancel();
      }
    }

    public @Nullable JBPopup getHint() {
      return myHint;
    }

    void updateHint(PsiElement element) {
      if (myHint == null || !myHint.isVisible()) return;
      final PopupUpdateProcessor updateProcessor = myHint.getUserData(PopupUpdateProcessor.class);
      if (updateProcessor != null) {
        updateProcessor.updatePopup(element);
      }
    }

    void repositionHint() {
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

    String promptText = myModel.getPromptText();
    if (promptText != null) {
      JLabel label = new JLabel(promptText);
      label.setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD));
      caption2Tools.add(label, BorderLayout.WEST);
    }

    if (promptText != null || isCheckboxVisible()) {
      caption2Tools.add(hBox, BorderLayout.EAST);
    }

    String checkBoxName = myModel.getCheckBoxName();
    Color fg = UIUtil.getLabelDisabledForeground();
    Color color = StartupUiUtil.isUnderDarcula() ? ColorUtil.shift(fg, 1.2) : ColorUtil.shift(fg, 0.7);
    String text;
    if (checkBoxName == null) {
      text = "";
    }
    else {
      HtmlBuilder builder = new HtmlBuilder().append(checkBoxName);
      if (myCheckBoxShortcut != null && myCheckBoxShortcut.hasShortcuts()) {
        builder.append(" ")
          .append(HtmlChunk.tag("b")
                    .attr("color", ColorUtil.toHex(color)).addText(KeymapUtil.getShortcutsText(myCheckBoxShortcut.getShortcuts())));
      }
      text = builder.wrapWith("html").toString();
    }
    myCheckBox.setText(text);
    myCheckBox.setAlignmentX(SwingConstants.RIGHT);
    myCheckBox.setBorder(null);

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
      public PsiElement @NotNull [] getElements() {
        List<Object> objects = myListModel.getItems();
        List<PsiElement> elements = new ArrayList<>(objects.size());
        for (Object object : objects) {
          if (object instanceof PsiElement) {
            elements.add((PsiElement)object);
          }
          else if (object instanceof DataProvider) {
            ContainerUtil.addIfNotNull(elements, CommonDataKeys.PSI_ELEMENT.getData((DataProvider)object));
          }
        }
        return PsiUtilCore.toPsiElementArray(elements);
      }
    });
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("ChooseByNameBase", group, true);
    actionToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    final JComponent toolbarComponent = actionToolbar.getComponent();
    actionToolbar.setTargetComponent(toolbarComponent);
    toolbarComponent.setBorder(null);

    if (myToolArea == null) {
      myToolArea = new JLabel(JBUIScale.scaleIcon(EmptyIcon.create(1, 24)));
    }
    else {
      myToolArea.setBorder(JBUI.Borders.emptyLeft(6)); // space between checkbox and filter/show all in view buttons
    }
    hBox.add(myToolArea);
    hBox.add(toolbarComponent);

    myTextFieldPanel.add(caption2Tools);

    new MyCopyReferenceAction()
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY).getShortcutSet(), myTextField);

    myTextFieldPanel.add(myTextField);
    Font editorFont = EditorUtil.getEditorFont();
    myTextField.setFont(editorFont);
    myTextField.putClientProperty("caretWidth", JBUIScale.scale(EditorUtil.getDefaultCaretWidth()));

    if (checkBoxName != null) {
      if (myCheckBoxShortcut != null) {
        new DumbAwareAction(IdeBundle.messagePointer("action.AnActionButton.text.change.goto.check.box")) {
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
        public void focusLost(final @NotNull FocusEvent e) {
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
              popup.addListener(new JBPopupListener() {
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

              if (UIUtil.haveCommonOwner(oppositeComponent, e.getComponent()))
              {
                return;
              }

              hideHint();
            }
          }, 5);
        }
      });
    }

    myCheckBox.addItemListener(__ -> rebuildList(false));
    myCheckBox.setFocusable(false);

    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        SelectionPolicy toSelect = currentChosenInfo != null && currentChosenInfo.hasSamePattern(ChooseByNameBase.this)
                                   ? PreserveSelection.INSTANCE : SelectMostRelevant.INSTANCE;
        rebuildList(toSelect, myRebuildDelay, ModalityState.current(), null);
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
          case KeyEvent.VK_DOWN -> ScrollingUtil.moveDown(myList, e.getModifiersEx());
          case KeyEvent.VK_UP -> ScrollingUtil.moveUp(myList, e.getModifiersEx());
          case KeyEvent.VK_PAGE_UP -> ScrollingUtil.movePageUp(myList);
          case KeyEvent.VK_PAGE_DOWN -> ScrollingUtil.movePageDown(myList);
          case KeyEvent.VK_TAB -> close(true);
          case KeyEvent.VK_ENTER -> {
            if (myList.getSelectedValue() == EXTRA_ELEM) {
              myMaximumListSizeLimit += myListSizeIncreasing;
              rebuildList(new SelectIndex(myList.getSelectedIndex()), myRebuildDelay, ModalityState.current(), null);
              e.consume();
            }
          }
        }
      }
    });

    myTextField.addActionListener(__ -> {
      if (!getChosenElements().isEmpty()) {
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
              rebuildList(new SelectIndex(selectedIndex), myRebuildDelay, ModalityState.current(), null);
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

    ListCellRenderer modelRenderer = myModel.getListCellRenderer();
    //noinspection unchecked
    myList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> AstLoadingFilter.disallowTreeLoading(
      () -> modelRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    ));
    myList.setVisibleRowCount(16);
    myList.setFont(editorFont);

    myList.addListSelectionListener(__ -> {
      if (checkDisposed()) {
        return;
      }

      chosenElementMightChange();
      updateDocumentation();

      List<Object> chosenElements = getChosenElements();
      if (!chosenElements.isEmpty()) {
        currentChosenInfo = new SelectionSnapshot(getTrimmedText(), new HashSet<>(chosenElements));
      }
    });

    myListScrollPane = ScrollPaneFactory.createScrollPane(myList, true);

    myTextFieldPanel.setBorder(JBUI.Borders.empty(5));

    showTextFieldPanel();

    myInitialized = true;

    if (modalityState != null) {
      rebuildList(SelectionPolicyKt.fromIndex(myInitialIndex), 0, modalityState, null);
    }
  }

  private boolean isDescendingFromTemporarilyFocusableToolWindow(@Nullable Component component) {
    if (component == null || myProject == null || myProject.isDisposed()) return false;

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    String activeToolWindowId = toolWindowManager.getActiveToolWindowId();
    ToolWindow toolWindow = activeToolWindowId == null ? null : toolWindowManager.getToolWindow(activeToolWindowId);
    JComponent toolWindowComponent = toolWindow != null ? toolWindow.getComponent() : null;
    return toolWindowComponent != null &&
           toolWindowComponent.getClientProperty(TEMPORARILY_FOCUSABLE_COMPONENT_KEY) != null &&
           SwingUtilities.isDescendingFrom(component, toolWindowComponent);
  }

  private void addCard(@NotNull JComponent comp, @NotNull @NonNls String cardId) {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(comp, BorderLayout.EAST);
    myCardContainer.add(wrapper, cardId);
  }

  public void setCheckBoxShortcut(@NotNull ShortcutSet shortcutSet) {
    myCheckBoxShortcut = shortcutSet;
  }

  private static @NotNull Set<KeyStroke> getShortcuts(@NotNull String actionId) {
    Set<KeyStroke> result = new HashSet<>();
    for (Shortcut shortcut : KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts()) {
      if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
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
    rebuildList(initial ? SelectionPolicyKt.fromIndex(myInitialIndex) : SelectMostRelevant.INSTANCE, myRebuildDelay, ModalityState.current(), null);
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

  @Override
  public @NotNull String transformPattern(@NotNull String pattern) {
    return pattern;
  }

  protected void doClose(final boolean ok) {
    if (checkDisposed()) return;

    if (closeForbidden(ok)) return;

    cancelListUpdater();
    close(ok);

    myListModel.removeAll();
  }

  protected boolean closeForbidden(boolean ok) {
    return false;
  }

  void cancelListUpdater() {
    ThreadingAssertions.assertEventDispatchThread();
    if (checkDisposed()) return;

    final CalcElementsThread calcElementsThread = myCalcElementsThread;
    if (calcElementsThread != null) {
      calcElementsThread.cancel();
      myCalcElementsThread = null;
    }
  }

  public @NotNull @NlsSafe String getTrimmedText() {
    return StringUtil.trimLeading(StringUtil.notNullize(myTextField.getText()));
  }

  private synchronized String @NotNull [] ensureNamesLoaded(boolean checkboxState) {
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

  public String @NotNull [] getNames(boolean checkboxState) {
    setNamesSync(checkboxState, null);
    return ensureNamesLoaded(checkboxState);
  }

  private String[] getNamesSync(boolean checkboxState) {
    synchronized (myNames) {
      return myNames[checkboxState ? 1 : 0];
    }
  }

  protected @NotNull Set<Object> filter(@NotNull Set<Object> elements) {
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

    Dimension size = new Dimension(preferredTextFieldPanelSize.width + 20, preferredTextFieldPanelSize.height);
    myTextPopup = builder.createPopup();
    myTextPopup.setSize(size);

    if (myProject != null && !myProject.isDefault()) {
      DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(myTextPopup);
    }

    Disposer.register(myTextPopup, () -> cancelListUpdater());
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);

    RelativePoint location = new RelativePoint(layeredPane, new Point(x, y));
    myTextPopup.show(location);
  }

  private JLayeredPane getLayeredPane() {
    JLayeredPane layeredPane;
    final Window window = WindowManager.getInstance().suggestParentWindow(myProject);

    if (window instanceof JFrame) {
      layeredPane = ((JFrame)window).getLayeredPane();
    }
    else if (window instanceof JDialog) {
      layeredPane = ((JDialog)window).getLayeredPane();
    }
    else if (window instanceof JWindow) {
      layeredPane = ((JWindow)window).getLayeredPane();
    }
    else {
      throw new IllegalStateException("cannot find parent window: project=" + myProject +
                                      (myProject != null ? "; open=" + myProject.isOpen() : "") +
                                      "; window=" + window);
    }
    return layeredPane;
  }

  void rebuildList(@NotNull SelectionPolicy pos,
                   final int delay,
                   final @NotNull ModalityState modalityState,
                   final @Nullable Runnable postRunnable) {
    ThreadingAssertions.assertEventDispatchThread();
    if (!myInitialized) {
      return;
    }

    myAlarm.cancelAllRequests();

    if (delay > 0) {
      myAlarm.addRequest(() -> rebuildList(pos, 0, modalityState, postRunnable), delay, ModalityState.stateForComponent(myTextField));
      return;
    }

    final CalcElementsThread calcElementsThread = myCalcElementsThread;
    if (calcElementsThread != null) {
      calcElementsThread.cancel();
    }

    final String text = getTrimmedText();
    if (!canShowListForEmptyPattern() && text.isEmpty()) {
      myListModel.removeAll();
      hideList();
      myTextFieldPanel.hideHint();
      myCard.show(myCardContainer, CHECK_BOX_CARD);
      return;
    }

    ListCellRenderer cellRenderer = myList.getCellRenderer();
    if (cellRenderer instanceof ExpandedItemListCellRendererWrapper) {
      cellRenderer = ((ExpandedItemListCellRendererWrapper<?>)cellRenderer).getWrappee();
    }
    final String pattern = patternToLowerCase(transformPattern(text));
    final Matcher matcher = buildPatternMatcher(isSearchInAnyPlace() ? "*" + pattern : pattern);
    MatcherHolder.associateMatcher(myList, matcher);

    scheduleCalcElements(text, myCheckBox.isSelected(), modalityState, pos, elements -> {
      ThreadingAssertions.assertEventDispatchThread();

      if (postRunnable != null) {
        postRunnable.run();
      }
    });
  }

  private void backgroundCalculationFinished(@NotNull Collection<?> result, @NotNull SelectionPolicy toSelect) {
    myCalcElementsThread = null;
    setElementsToList(toSelect, result);
    myList.repaint();
    chosenElementMightChange();

    if (result.isEmpty()) {
      myTextFieldPanel.hideHint();
    }
  }

  public void scheduleCalcElements(@NotNull String text,
                                   boolean checkboxState,
                                   @NotNull ModalityState modalityState,
                                   @NotNull SelectionPolicy policy,
                                   @NotNull Consumer<? super Set<?>> callback) {
    new CalcElementsThread(text, checkboxState, modalityState, policy, callback).scheduleThread();
  }

  private static boolean isShowListAfterCompletionKeyStroke() {
    return myShowListAfterCompletionKeyStroke;
  }

  private void setElementsToList(@NotNull SelectionPolicy pos, @NotNull Collection<?> elements) {
    if (checkDisposed()) return;
    if (isCloseByFocusLost() && Registry.is("focus.follows.mouse.workarounds")) {
      PointerInfo pointerInfo = MouseInfo.getPointerInfo();
      if (pointerInfo != null) {
        myFocusPoint = pointerInfo.getLocation();
      }
    }
    if (elements.isEmpty()) {
      myListModel.removeAll();
      myTextField.setForeground(JBColor.red);
      hideList();
      return;
    }

    Object[] oldElements = myListModel.getItems().toArray();
    Object[] newElements = elements.toArray();
    if (ArrayUtil.contains(null, newElements)) {
      LOG.error("Null after filtering elements by " + this);
    }
    List<ModelDiff.Cmd> commands = ModelDiff.createDiffCmds(myListModel, oldElements, newElements);

    myTextField.setForeground(UIUtil.getTextFieldForeground());
    if (commands == null || commands.isEmpty()) {
      applySelection(pos);
      showList();
      myTextFieldPanel.repositionHint();
    }
    else {
      appendToModel(commands, pos);
    }
  }

  @VisibleForTesting
  public int calcSelectedIndex(Object @NotNull [] modelElements, @NotNull String trimmedText) {
    if (myModel instanceof Comparator) {
      return 0;
    }

    Matcher matcher = buildPatternMatcher(transformPattern(trimmedText));
    final String statContext = statisticsContext();
    Comparator<Object> itemComparator = Comparator.
      comparing(e -> trimmedText.equalsIgnoreCase(myModel.getElementName(e))).
      thenComparing(e -> matchingDegree(matcher, e)).
      thenComparing(e -> getUseCount(statContext, e)).
      reversed();

    int bestPosition = 0;
    while (bestPosition < modelElements.length - 1 && isSpecialElement(modelElements[bestPosition])) bestPosition++;

    for (int i = 1; i < modelElements.length; i++) {
      final Object modelElement = modelElements[i];
      if (isSpecialElement(modelElement)) continue;

      if (itemComparator.compare(modelElement, modelElements[bestPosition]) < 0) {
        bestPosition = i;
      }
    }

    return bestPosition;
  }

  private static boolean isSpecialElement(@NotNull Object modelElement) {
    return EXTRA_ELEM.equals(modelElement);
  }

  private int getUseCount(@NotNull String statContext, @NotNull Object modelElement) {
    String text = myModel.getFullName(modelElement);
    return text == null ? Integer.MIN_VALUE : StatisticsManager.getInstance().getUseCount(new StatisticsInfo(statContext, text));
  }

  private int matchingDegree(@NotNull Matcher matcher, @NotNull Object modelElement) {
    String name = myModel.getElementName(modelElement);
    return name != null && matcher instanceof MinusculeMatcher ? ((MinusculeMatcher)matcher).matchingDegree(name) : Integer.MIN_VALUE;
  }

  @NotNull
  @NonNls
  String statisticsContext() {
    return "choose_by_name#" + myModel.getPromptText() + "#" + myCheckBox.isSelected() + "#" + getTrimmedText();
  }

  private void appendToModel(@NotNull List<? extends ModelDiff.Cmd> commands, @NotNull SelectionPolicy selection) {
    for (ModelDiff.Cmd command : commands) {
      command.apply();
    }
    showList();

    myTextFieldPanel.repositionHint();

    if (!myListModel.isEmpty()) {
      applySelection(selection);
    }
  }

  private void applySelection(@NotNull SelectionPolicy selection) {
    List<Integer> indices = selection.performSelection(this, myListModel);
    myList.setSelectedIndices(Ints.toArray(indices));
    if (!indices.isEmpty()) {
      ScrollingUtil.ensureIndexIsVisible(myList, indices.get(0).intValue(), 0);
    }
  }

  protected abstract void showList();

  protected abstract void hideList();

  protected abstract void close(boolean isOk);

  public @Nullable Object getChosenElement() {
    final List<Object> elements = getChosenElements();
    return elements.size() == 1 ? elements.get(0) : null;
  }

  protected @NotNull List<Object> getChosenElements() {
    return ContainerUtil.filter(myList.getSelectedValuesList(), o -> o != null && !isSpecialElement(o));
  }

  protected void chosenElementMightChange() {
  }

  protected final class MyTextField extends JTextField implements PopupOwner, UiDataProvider {
    private final KeyStroke myCompletionKeyStroke;
    private final KeyStroke forwardStroke;
    private final KeyStroke backStroke;

    private boolean completionKeyStrokeHappened;

    private MyTextField() {
      super(40);
      // Set UI and border for Darcula and all except Win10, Mac and GTK
      if (!UIUtil.isUnderDefaultMacTheme() && !UIUtil.isUnderWin10LookAndFeel()) {
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

    private static @Nullable KeyStroke getShortcut(@NotNull String actionCodeCompletion) {
      final Shortcut[] shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionCodeCompletion).getShortcuts();
      for (final Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          return ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        }
      }
      return null;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(LangDataKeys.POSITION_ADJUSTER_POPUP,
               myDropdownPopup != null && myDropdownPopup.isVisible() ? myDropdownPopup : null);
      sink.set(LangDataKeys.PARENT_POPUP,
               myTextPopup != null && myTextPopup.isVisible() ? myTextPopup : null);
    }

    @Override
    protected void processKeyEvent(@NotNull KeyEvent e) {
      final KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);

      if (keyStroke.equals(myCompletionKeyStroke)) {
        completionKeyStrokeHappened = true;
        e.consume();
        final String pattern = getTrimmedText();
        final int oldPos = myList.getSelectedIndex();
        myHistory.add(Pair.create(pattern, oldPos));
        final Runnable postRunnable = () -> fillInCommonPrefix(pattern);
        rebuildList(SelectMostRelevant.INSTANCE, 0, ModalityState.current(), postRunnable);
        return;
      }
      if (keyStroke.equals(backStroke)) {
        e.consume();
        if (!myHistory.isEmpty()) {
          final String oldText = getTrimmedText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> last = myHistory.remove(myHistory.size() - 1);
          myTextField.setText(last.first);
          myFuture.add(Pair.create(oldText, oldPos));
          rebuildList(SelectMostRelevant.INSTANCE, 0, ModalityState.current(), null);
        }
        return;
      }
      if (keyStroke.equals(forwardStroke)) {
        e.consume();
        if (!myFuture.isEmpty()) {
          final String oldText = getTrimmedText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> next = myFuture.remove(myFuture.size() - 1);
          myTextField.setText(next.first);
          myHistory.add(Pair.create(oldText, oldPos));
          rebuildList(SelectMostRelevant.INSTANCE, 0, ModalityState.current(), null);
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

    private void fillInCommonPrefix(final @NotNull String pattern) {
      final List<String> list = myProvider.filterNames(ChooseByNameBase.this, getNames(myCheckBox.isSelected()), pattern);
      if (list.isEmpty()) return;

      if (isComplexPattern(pattern)) return; //TODO: support '*'
      final String oldText = getTrimmedText();
      final int oldPos = myList.getSelectedIndex();

      String commonPrefix = null;
      if (!list.isEmpty()) {
        for (String name : list) {
          final String string = StringUtil.toLowerCase(name);
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
            commonPrefix = StringUtil.toLowerCase(commonPrefix);
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

    private boolean isComplexPattern(final @NotNull String pattern) {
      if (pattern.indexOf('*') >= 0) return true;
      for (String s : myModel.getSeparators()) {
        if (pattern.contains(s)) return true;
      }

      return false;
    }

    @Override
    public @NotNull Point getBestPopupPosition() {
      return new Point(myTextFieldPanel.getWidth(), getHeight());
    }

    @Override
    protected void paintComponent(final @NotNull Graphics g) {
      GraphicsUtil.setupAntialiasing(g);
      super.paintComponent(g);
    }

    boolean isCompletionKeyStroke() {
      return completionKeyStrokeHappened;
    }
  }

  public @NotNull ChooseByNameItemProvider getProvider() {
    return myProvider;
  }

  private void handlePaste(@NotNull String str) {
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

  private static boolean isFileName(@NotNull String name) {
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

  private class CalcElementsThread extends ReadTask {
    private final @NotNull String myPattern;
    private final boolean myCheckboxState;
    private final @NotNull Consumer<? super Set<?>> myCallback;
    private final ModalityState myModalityState;
    private @NotNull SelectionPolicy mySelectionPolicy;

    private final ProgressIndicator myProgress = new ProgressIndicatorBase();

    CalcElementsThread(@NotNull String pattern,
                       boolean checkboxState,
                       @NotNull ModalityState modalityState,
                       @NotNull SelectionPolicy policy,
                       @NotNull Consumer<? super Set<?>> callback) {
      myPattern = pattern;
      myCheckboxState = checkboxState;
      myCallback = callback;
      myModalityState = modalityState;
      mySelectionPolicy = policy;
    }

    private final Alarm myShowCardAlarm = new Alarm();
    private final Alarm myUpdateListAlarm = new Alarm();

    void scheduleThread() {
      ThreadingAssertions.assertEventDispatchThread();
      myCalcElementsThread = this;
      showCard(SEARCHING_CARD, 200);
      ProgressIndicatorUtils.scheduleWithWriteActionPriority(myProgress, this);
    }

    @Override
    public Continuation runBackgroundProcess(final @NotNull ProgressIndicator indicator) {
      if (myProject == null || DumbService.isDumbAware(myModel)) return super.runBackgroundProcess(indicator);

      return DumbService.getInstance(myProject).runReadActionInSmartMode(() -> performInReadAction(indicator));
    }

    @Override
    public @Nullable Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
      if (isProjectDisposed()) return null;

      Set<Object> elements = Collections.synchronizedSet(new LinkedHashSet<>());
      scheduleIncrementalListUpdate(elements, 0);

      boolean scopeExpanded =
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> populateElements(elements));
      final String cardToShow = elements.isEmpty() ? NOT_FOUND_CARD : scopeExpanded ? NOT_FOUND_IN_PROJECT_CARD : CHECK_BOX_CARD;

      Set<Object> objects = filter(elements);
      Object selected;
      if (myInitialSelection != null) {
        selected = myInitialSelection.fun(objects);
        myInitialSelection = null;
      }
      else {
        selected = null;
      }
      AnchoredSet resultSet = new AnchoredSet(objects);
      return new Continuation(() -> {
        if (!checkDisposed() && !myProgress.isCanceled()) {
          CalcElementsThread currentBgProcess = myCalcElementsThread;
          LOG.assertTrue(currentBgProcess == this, currentBgProcess);

          showCard(cardToShow, 0);

          Set<Object> filtered = resultSet.getElements();
          backgroundCalculationFinished(filtered, selected == null ? mySelectionPolicy : new SelectObject(selected));
          myCallback.consume(filtered);
        }
      }, myModalityState);
    }

    private void scheduleIncrementalListUpdate(@NotNull Set<Object> elements, int lastCount) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      myUpdateListAlarm.addRequest(() -> {
        if (myCalcElementsThread != this || !myProgress.isRunning()) return;

        int count = elements.size();
        if (count > lastCount) {
          setElementsToList(mySelectionPolicy, new ArrayList<>(elements));
          if (currentChosenInfo != null) {
            mySelectionPolicy = PreserveSelection.INSTANCE;
          }
        }
        scheduleIncrementalListUpdate(elements, count);
      }, 200);
    }

    private boolean populateElements(@NotNull Set<Object> elements) {
      boolean scopeExpanded = false;
      try {
        scopeExpanded = fillWithScopeExpansion(elements, myPattern);

        String lowerCased = patternToLowerCase(myPattern);
        if (elements.isEmpty() && !lowerCased.equals(myPattern)) {
          scopeExpanded = fillWithScopeExpansion(elements, lowerCased);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
      return scopeExpanded;
    }

    private boolean fillWithScopeExpansion(@NotNull Set<Object> elements, @NotNull String pattern) {
      addElementsByPattern(pattern, elements, myProgress, myCheckboxState);

      if (elements.isEmpty() && !myCheckboxState) {
        addElementsByPattern(pattern, elements, myProgress, true);
        return true;
      }
      return false;
    }

    @Override
    public void onCanceled(@NotNull ProgressIndicator indicator) {
      LOG.assertTrue(myCalcElementsThread == this, myCalcElementsThread);

      if (!isProjectDisposed() && !checkDisposed()) {
        new CalcElementsThread(myPattern, myCheckboxState, myModalityState, mySelectionPolicy, myCallback).scheduleThread();
      }
    }

    private void addElementsByPattern(@NotNull String pattern,
                                      final @NotNull Set<Object> elements,
                                      final @NotNull ProgressIndicator indicator,
                                      boolean everywhere) {
      long start = System.currentTimeMillis();
      myProvider.filterElements(
        ChooseByNameBase.this, pattern, everywhere,
        indicator,
        o -> {
          if (indicator.isCanceled()) return false;
          if (o == null) {
            LOG.error("Null returned from " + myProvider + " with " + myModel + " in " + ChooseByNameBase.this);
            return true;
          }
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

    private void showCard(@NotNull String card, final int delay) {
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
      ThreadingAssertions.assertEventDispatchThread();
      myProgress.cancel();
    }

  }

  private boolean isProjectDisposed() {
    return myProject != null && myProject.isDisposed();
  }

  private static @NotNull @NlsSafe String patternToLowerCase(@NotNull @NlsSafe String pattern) {
    return StringUtil.toLowerCase(pattern);
  }

  @Override
  public boolean canShowListForEmptyPattern() {
    return isShowListForEmptyPattern() || isShowListAfterCompletionKeyStroke() && lastKeyStrokeIsCompletion();
  }

  private boolean lastKeyStrokeIsCompletion() {
    return myTextField.isCompletionKeyStroke();
  }

  private static @NotNull Matcher buildPatternMatcher(@NotNull String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
  }

  private static final class HintLabel extends JLabel {
    private HintLabel(@NlsContexts.Label @NotNull String text) {
      super(text, RIGHT);
      setForeground(JBColor.DARK_GRAY);
    }
  }

  @Override
  public int getMaximumListSizeLimit() {
    return myMaximumListSizeLimit;
  }

  public void setMaximumListSizeLimit(final int maximumListSizeLimit) {
    myMaximumListSizeLimit = maximumListSizeLimit;
  }

  public void setListSizeIncreasing(final int listSizeIncreasing) {
    myListSizeIncreasing = listSizeIncreasing;
  }

  /**
   * Display <tt>...</tt> item at the end of the list regardless of whether it was filled up or not.
   * This option can be useful in cases, when it can't be said beforehand, that the next call to {@link ChooseByNameItemProvider}
   * won't give new items.
   */
  public void setAlwaysHasMore(boolean enabled) {
    myAlwaysHasMore = enabled;
  }

  private abstract class ShowFindUsagesAction extends DumbAwareAction {
    ShowFindUsagesAction() {
      super(LangBundle.messagePointer("action.show.all.in.view.text"), AllIcons.General.Pin_tab);
    }

    @Override
    public void actionPerformed(final @NotNull AnActionEvent e) {
      cancelListUpdater();

      final UsageViewPresentation presentation = new UsageViewPresentation();
      final String text = getTrimmedText();
      final String prefixPattern = myFindUsagesTitle + " '" + text + "'";
      presentation.setCodeUsagesString(prefixPattern);
      presentation.setTabName(prefixPattern);
      presentation.setTabText(prefixPattern);
      presentation.setTargetsNodeText(LangBundle.message("list.item.unsorted", StringUtil.toLowerCase(patternToLowerCase(prefixPattern))));
      PsiElement[] elements = getElements();
      final List<PsiElement> targets = new ArrayList<>();
      final Set<Usage> usages = new LinkedHashSet<>();
      fillUsages(Arrays.asList(elements), usages, targets);
      if (myListModel.contains(EXTRA_ELEM)) { //start searching for the rest
        final boolean everywhere = myCheckBox.isSelected();
        hideHint();
        final Set<Object> collected = new LinkedHashSet<>();
        ProgressManager.getInstance().run(new Task.Modal(myProject, prefixPattern, true) {
          private ChooseByNameBase.CalcElementsThread myCalcUsagesThread;
          @Override
          public void run(final @NotNull ProgressIndicator indicator) {
            ensureNamesLoaded(everywhere);
            indicator.setIndeterminate(true);
            final TooManyUsagesStatus tooManyUsagesStatus = TooManyUsagesStatus.createFor(indicator);
            myCalcUsagesThread = new CalcElementsThread(text, everywhere, ModalityState.nonModal(), PreserveSelection.INSTANCE, __->{}) {
              @Override
              protected boolean isOverflow(@NotNull Set<Object> elementsArray) {
                tooManyUsagesStatus.pauseProcessingIfTooManyUsages();
                if (elementsArray.size() > UsageLimitUtil.USAGES_LIMIT - myMaximumListSizeLimit && tooManyUsagesStatus.switchTooManyUsagesStatus()) {
                  UsageViewManagerImpl.showTooManyUsagesWarningLater(getProject(), tooManyUsagesStatus, indicator, null,
                                                                     () -> UsageViewBundle.message("find.excessive.usage.count.prompt"),
                                                                     null);
                }
                return false;
              }
            };

            ApplicationManager.getApplication().runReadAction(() -> {
              myCalcUsagesThread.addElementsByPattern(text, collected, indicator, everywhere);

              indicator.setText(LangBundle.message("progress.text.prepare"));
              fillUsages(collected, usages, targets);
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
          public void onThrowable(@NotNull Throwable error) {
            super.onThrowable(error);
            myCalcUsagesThread.cancel();
          }
        });
      }
      else {
        hideHint();
        showUsageView(targets, usages, presentation);
      }
    }

    private static void fillUsages(@NotNull Collection<Object> matchElementsArray,
                                   @NotNull Collection<? super Usage> usages,
                                   @NotNull List<? super PsiElement> targets) {
      for (Object o : matchElementsArray) {
        if (o instanceof PsiElement element) {
          if (element.getTextRange() != null) {
            usages.add(new MyUsageInfo2UsageAdapter(element, false));
          }
          else {
            targets.add(element);
          }
        }
      }
    }

    private void showUsageView(@NotNull List<? extends PsiElement> targets,
                               @NotNull Collection<? extends Usage> usages,
                               @NotNull UsageViewPresentation presentation) {
      UsageTarget[] usageTargets = targets.isEmpty() ? UsageTarget.EMPTY_ARRAY :
                                   PsiElement2UsageTargetAdapter.convert(PsiUtilCore.toPsiElementArray(targets));
      UsageViewManager.getInstance(myProject).showUsages(usageTargets, usages.toArray(Usage.EMPTY_ARRAY), presentation);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myFindUsagesTitle == null || myProject == null) {
        e.getPresentation().setVisible(false);
        return;
      }
      PsiElement[] elements = getElements();
      e.getPresentation().setEnabled(elements.length > 0);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    public abstract PsiElement @NotNull [] getElements();
  }

  private static final class MyUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter {
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
      if (!(o instanceof MyUsageInfo2UsageAdapter adapter)) return false;

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

  public @NotNull JTextField getTextField() {
    return myTextField;
  }

  private final class MyCopyReferenceAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myTextField.getSelectedText() == null && getChosenElement() instanceof PsiElement);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      CopyReferenceAction.doCopy((PsiElement)getChosenElement(), myProject);
    }
  }
}