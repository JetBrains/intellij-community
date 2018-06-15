// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Alarm;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.openapi.keymap.KeymapUtil.getKeystrokeText;
import static com.intellij.util.containers.ContainerUtil.indexOf;
import static com.intellij.util.containers.ContainerUtil.newArrayList;

/**
 * The standard base class for modal dialog boxes. The dialog wrapper could be used only on event dispatch thread.
 * In case when the dialog must be created from other threads use
 * {@link EventQueue#invokeLater(Runnable)} or {@link EventQueue#invokeAndWait(Runnable)}.
 * <p/>
 * See also http://www.jetbrains.org/intellij/sdk/docs/user_interface_components/dialog_wrapper.html.
 */
@SuppressWarnings({"SSBasedInspection", "MethodMayBeStatic"})
public abstract class DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.DialogWrapper");

  public enum IdeModalityType {
    IDE,
    PROJECT,
    MODELESS;

    @NotNull
    public Dialog.ModalityType toAwtModality() {
      switch (this) {
        case IDE:
          return Dialog.ModalityType.APPLICATION_MODAL;
        case PROJECT:
          return Dialog.ModalityType.DOCUMENT_MODAL;
        case MODELESS:
          return Dialog.ModalityType.MODELESS;
      }
      throw new IllegalStateException(toString());
    }
  }

  /**
   * The default exit code for "OK" action.
   */
  public static final int OK_EXIT_CODE = 0;
  /**
   * The default exit code for "Cancel" action.
   */
  public static final int CANCEL_EXIT_CODE = 1;
  /**
   * The default exit code for "Close" action. Equal to cancel.
   */
  public static final int CLOSE_EXIT_CODE = CANCEL_EXIT_CODE;
  /**
   * If you use your own custom exit codes you have to start them with
   * this constant.
   */
  public static final int NEXT_USER_EXIT_CODE = 2;

  /**
   * If your action returned by {@code createActions} method has non
   * {@code null} value for this key, then the button that corresponds to the action will be the
   * default button for the dialog. It's true if you don't change this behaviour
   * of {@code createJButtonForAction(Action)} method.
   */
  @NonNls public static final String DEFAULT_ACTION = "DefaultAction";

  @NonNls public static final String FOCUSED_ACTION = "FocusedAction";

  @NonNls private static final String NO_AUTORESIZE = "NoAutoResizeAndFit";

  private static final KeyStroke SHOW_OPTION_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                                                                                InputEvent.ALT_MASK | InputEvent.SHIFT_MASK);

  @NotNull
  private final DialogWrapperPeer myPeer;
  private int myExitCode = CANCEL_EXIT_CODE;

  /**
   * The shared instance of default border for dialog's content pane.
   */
  public static final Border ourDefaultBorder = new EmptyBorder(UIUtil.PANEL_REGULAR_INSETS);

  private float myHorizontalStretch = 1.0f;
  private float myVerticalStretch = 1.0f;
  /**
   * Defines horizontal alignment of buttons.
   */
  private int myButtonAlignment = SwingConstants.RIGHT;
  private boolean myCrossClosesWindow = true;

  protected Action myOKAction;
  protected Action myCancelAction;
  protected Action myHelpAction;
  private final Map<Action, JButton> myButtonMap = new LinkedHashMap<>();

  private boolean myClosed = false;

  protected boolean myPerformAction = false;

  private Action myYesAction = null;
  private Action myNoAction = null;

  protected JCheckBox myCheckBoxDoNotShowDialog;
  @Nullable
  private DoNotAskOption myDoNotAsk;

  protected JComponent myPreferredFocusedComponent;
  private Computable<Point> myInitialLocationCallback;

  private Dimension  myActualSize = null;

  private List<ValidationInfo> myInfo = Collections.emptyList();

  @NotNull
  protected final Disposable myDisposable = new Disposable() {
    @Override
    public String toString() {
      return DialogWrapper.this.toString();
    }

    @Override
    public void dispose() {
      DialogWrapper.this.dispose();
    }
  };
  private final List<JBOptionButton> myOptionsButtons = new ArrayList<>();
  private int myCurrentOptionsButtonIndex = -1;
  private boolean myResizeInProgress = false;
  private ComponentAdapter myResizeListener;

  @NotNull
  protected String getDoNotShowMessage() {
    return CommonBundle.message("dialog.options.do.not.show");
  }

  public void setDoNotAskOption(@Nullable DoNotAskOption doNotAsk) {
    myDoNotAsk = doNotAsk;
  }

  private ErrorText myErrorText;

  private final Alarm myErrorTextAlarm = new Alarm();

  public static final Color BALLOON_ERROR_BORDER = new JBColor(new Color(0xe0a8a9), new Color(0x73454b));
  public static final Color BALLOON_ERROR_BACKGROUND = new JBColor(new Color(0xf5e6e7), new Color(0x593d41));

  public static final Color BALLOON_WARNING_BORDER = new JBColor(new Color(0xcca869), new Color(0x4e452f));
  public static final Color BALLOON_WARNING_BACKGROUND = new JBColor(new Color(0xf9f4ee), new Color(0x594e32));

  /**
   * Creates modal {@code DialogWrapper}. The currently active window will be the dialog's parent.
   *
   * @param project     parent window for the dialog will be calculated based on focused window for the
   *                    specified {@code project}. This parameter can be {@code null}. In this case parent window
   *                    will be suggested based on current focused window.
   * @param canBeParent specifies whether the dialog can be parent for other windows. This parameter is used
   *                    by {@code WindowManager}.
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  protected DialogWrapper(@Nullable Project project, boolean canBeParent) {
    this(project, canBeParent, IdeModalityType.IDE);
  }

  protected DialogWrapper(@Nullable Project project, boolean canBeParent, @NotNull IdeModalityType ideModalityType) {
    this(project, null, canBeParent, ideModalityType);
  }

  protected DialogWrapper(@Nullable Project project, @Nullable Component parentComponent, boolean canBeParent, @NotNull IdeModalityType ideModalityType) {
    myPeer = parentComponent == null ? createPeer(project, canBeParent, project == null ? IdeModalityType.IDE : ideModalityType) : createPeer(parentComponent, canBeParent);
    final Window window = myPeer.getWindow();
    if (window != null) {
      myResizeListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          if (!myResizeInProgress) {
            myActualSize = myPeer.getSize();
            if (myErrorText != null && myErrorText.isVisible()) {
              myActualSize.height -= myErrorText.getMinimumSize().height;
            }
          }
        }
      };
      window.addComponentListener(myResizeListener);
    }
    createDefaultActions();
  }

  /**
   * Creates modal {@code DialogWrapper} that can be parent for other windows.
   * The currently active window will be the dialog's parent.
   *
   * @param project parent window for the dialog will be calculated based on focused window for the
   *                specified {@code project}. This parameter can be {@code null}. In this case parent window
   *                will be suggested based on current focused window.
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   * @see DialogWrapper#DialogWrapper(Project, boolean)
   */
  protected DialogWrapper(@Nullable Project project) {
    this(project, true);
  }

  /**
   * Creates modal {@code DialogWrapper}. The currently active window will be the dialog's parent.
   *
   * @param canBeParent specifies whether the dialog can be parent for other windows. This parameter is used
   *                    by {@code WindowManager}.
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  protected DialogWrapper(boolean canBeParent) {
    this((Project)null, canBeParent);
  }

  /**
   * Typically, we should set a parent explicitly. Use WindowManager#suggestParentWindow
   * method to find out the best parent for your dialog. Exceptions are cases
   * when we do not have a project to figure out which window
   * is more suitable as an owner for the dialog.
   * <p/>
   * Instead, use {@link DialogWrapper#DialogWrapper(Project, boolean, boolean)}
   */
  @Deprecated
  protected DialogWrapper(boolean canBeParent, boolean applicationModalIfPossible) {
    this(null, canBeParent, applicationModalIfPossible);
  }

  protected DialogWrapper(Project project, boolean canBeParent, boolean applicationModalIfPossible) {
    ensureEventDispatchThread();
    if (ApplicationManager.getApplication() != null) {
      myPeer = createPeer(
        project != null ? WindowManager.getInstance().suggestParentWindow(project) : WindowManager.getInstance().findVisibleFrame()
        , canBeParent, applicationModalIfPossible);
    }
    else {
      myPeer = createPeer(null, canBeParent, applicationModalIfPossible);
    }
    createDefaultActions();
  }

  /**
   * @param parent      parent component which is used to calculate heavy weight window ancestor.
   *                    {@code parent} cannot be {@code null} and must be showing.
   * @param canBeParent can be parent
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  protected DialogWrapper(@NotNull Component parent, boolean canBeParent) {
    ensureEventDispatchThread();
    myPeer = createPeer(parent, canBeParent);
    createDefaultActions();
  }

  //validation
  private final Alarm myValidationAlarm = new Alarm(getValidationThreadToUse(), myDisposable);

  @NotNull
  protected Alarm.ThreadToUse getValidationThreadToUse() {
    return Alarm.ThreadToUse.SWING_THREAD;
  }

  private int myValidationDelay = 300;
  private boolean myDisposed = false;
  private boolean myValidationStarted = false;
  private final ErrorPainter myErrorPainter = new ErrorPainter();
  private boolean myErrorPainterInstalled = false;

  /**
   * Allows to postpone first start of validation
   *
   * @return {@code false} if start validation in {@code init()} method
   */
  protected boolean postponeValidation() {
    return true;
  }

  /**
   * Validates user input and returns {@code null} if everything is fine
   * or validation description with component where problem has been found.
   *
   * @return {@code null} if everything is OK or validation descriptor
   */
  @Nullable
  protected ValidationInfo doValidate() {
    return null;
  }

  /**
   * Validates user input and returns {@code List<ValidationInfo>}.
   * If everything is fine the returned list is empty otherwise
   * the list contains all invalid fields with error messages.
   * This method should preferably be used when validating forms with multiply
   * fields that require validation.
   *
   * @return {@code List<ValidationInfo>} of invalid fields. List
   * is empty if no errors found.
   */
  @NotNull
  protected List<ValidationInfo> doValidateAll() {
    ValidationInfo vi = doValidate();
    return vi != null ? Collections.singletonList(vi) : Collections.EMPTY_LIST;
  }

  public void setValidationDelay(int delay) {
    myValidationDelay = delay;
  }

  private void installErrorPainter() {
    if (myErrorPainterInstalled) return;
    myErrorPainterInstalled = true;
    UIUtil.invokeLaterIfNeeded(() -> IdeGlassPaneUtil.installPainter(getContentPanel(), myErrorPainter, myDisposable));
  }

  protected void updateErrorInfo(@NotNull List<ValidationInfo> info) {
    boolean updateNeeded = Registry.is("ide.inplace.errors.balloon") ?
                           !myInfo.equals(info) : !myErrorText.isTextSet(info);

    if (updateNeeded) {
      SwingUtilities.invokeLater(() -> {
        if (myDisposed) return;
        setErrorInfoAll(info);
        myPeer.getRootPane().getGlassPane().repaint();
        getOKAction().setEnabled(info.isEmpty() || info.stream().noneMatch(info1 -> info1.disableOk));
      });
    }
  }

  protected void createDefaultActions() {
    myOKAction = new OkAction();
    myCancelAction = new CancelAction();
    myHelpAction = new HelpAction();
  }

  public void setUndecorated(boolean undecorated) {
    myPeer.setUndecorated(undecorated);
  }

  public final void addMouseListener(@NotNull MouseListener listener) {
    myPeer.addMouseListener(listener);
  }

  public final void addMouseListener(@NotNull MouseMotionListener listener) {
    myPeer.addMouseListener(listener);
  }

  public final void addKeyListener(@NotNull KeyListener listener) {
    myPeer.addKeyListener(listener);
  }

  /**
   * Closes and disposes the dialog and sets the specified exit code.
   *
   * @param exitCode exit code
   * @param isOk     is OK
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  public final void close(int exitCode, boolean isOk) {
    ensureEventDispatchThread();
    if (myClosed) return;
    myClosed = true;
    myExitCode = exitCode;
    Window window = getWindow();
    if (window != null && myResizeListener != null) {
      window.removeComponentListener(myResizeListener);
      myResizeListener = null;
    }

    if (isOk) {
      processDoNotAskOnOk(exitCode);
    }
    else {
      processDoNotAskOnCancel();
    }

    Disposer.dispose(myDisposable);
  }

  public final void close(int exitCode) {
    logCloseDialogEvent(exitCode);
    close(exitCode, exitCode != CANCEL_EXIT_CODE);
  }

  /**
   * Creates border for dialog's content pane. By default content
   * pane has has empty border with {@code (8,12,8,12)} insets. Subclasses can
   * return {@code null} for no border.
   *
   * @return content pane border
   */
  @Nullable
  protected Border createContentPaneBorder() {
    if (getStyle() == DialogStyle.COMPACT) {
      return JBUI.Borders.empty();
    }
    return ourDefaultBorder;
  }

  protected static boolean isMoveHelpButtonLeft() {
    return UIUtil.isUnderAquaBasedLookAndFeel() || UIUtil.isUnderDarcula() || UIUtil.isUnderWin10LookAndFeel();
  }

  private static boolean isRemoveHelpButton() {
    return SystemInfo.isWindows && (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) && Registry.is("ide.win.frame.decoration") ||
           Registry.is("ide.remove.help.button.from.dialogs");
  }

  /**
   * Creates panel located at the south of the content pane. By default that
   * panel contains dialog's buttons. This default implementation uses {@code createActions()}
   * and {@code createJButtonForAction(Action)} methods to construct the panel.
   *
   * @return south panel
   */
  protected JComponent createSouthPanel() {
    List<Action> actions = ContainerUtil.filter(createActions(), Condition.NOT_NULL);
    List<Action> leftSideActions = newArrayList(createLeftSideActions());

    if (!ApplicationInfo.contextHelpAvailable()) {
      actions.remove(getHelpAction());
    }

    boolean hasHelpToMoveToLeftSide = false;
    if (isRemoveHelpButton()) {
      actions.remove(getHelpAction());
    }
    else if (isMoveHelpButtonLeft() && actions.contains(getHelpAction())) {
      hasHelpToMoveToLeftSide = true;
      actions.remove(getHelpAction());
    }

    if (SystemInfo.isMac) {
      Action macOtherAction = ContainerUtil.find(actions, MacOtherAction.class::isInstance);
      if (macOtherAction != null) {
        leftSideActions.add(macOtherAction);
        actions.remove(macOtherAction);
      }

      // move ok action to the right
      int okNdx = actions.indexOf(getOKAction());
      if (okNdx >= 0 && okNdx != actions.size() - 1) {
        actions.remove(getOKAction());
        actions.add(getOKAction());
      }

      // move cancel action to the left
      int cancelNdx = actions.indexOf(getCancelAction());
      if (cancelNdx > 0) {
        actions.remove(getCancelAction());
        actions.add(0, getCancelAction());
      }
    }
    else if (UIUtil.isUnderGTKLookAndFeel() && actions.contains(getHelpAction())) {
      leftSideActions.add(getHelpAction());
      actions.remove(getHelpAction());
    }

    if (!UISettings.getShadowInstance().getAllowMergeButtons()) {
      actions = flattenOptionsActions(actions);
      leftSideActions = flattenOptionsActions(leftSideActions);
    }


    List<JButton> leftSideButtons = createButtons(leftSideActions);
    List<JButton> rightSideButtons = createButtons(actions);

    myButtonMap.clear();
    for (JButton button : ContainerUtil.concat(leftSideButtons, rightSideButtons)) {
      myButtonMap.put(button.getAction(), button);
      if (button instanceof JBOptionButton) {
        myOptionsButtons.add((JBOptionButton)button);
      }
    }

    myPeer.setTouchBarButtons(rightSideButtons);

    return createSouthPanel(leftSideButtons, rightSideButtons, hasHelpToMoveToLeftSide);
  }

  @NotNull
  protected JButton createHelpButton(Insets insets) {
    JButton helpButton = new JButton(getHelpAction());
    helpButton.putClientProperty("JButton.buttonType", "help");
    helpButton.setText("");
    helpButton.setMargin(insets);
    setHelpTooltip(helpButton);
    return helpButton;
  }

  protected void setHelpTooltip(JButton helpButton) {
    helpButton.setToolTipText(ActionsBundle.actionDescription("HelpTopics"));
  }

  @NotNull
  private static List<Action> flattenOptionsActions(@NotNull List<Action> actions) {
    List<Action> newActions = new ArrayList<>();
    for (Action action : actions) {
      newActions.add(action);
      if (action instanceof OptionAction) {
        ContainerUtil.addAll(newActions, ((OptionAction)action).getOptions());
      }
    }
    return newActions;
  }

  protected boolean shouldAddErrorNearButtons() {
    return false;
  }

  @NotNull
  protected DialogStyle getStyle() {
    return DialogStyle.NO_STYLE;
  }

  protected boolean toBeShown() {
    return !myCheckBoxDoNotShowDialog.isSelected();
  }

  public boolean isTypeAheadEnabled() {
    return false;
  }

  @NotNull
  private List<JButton> createButtons(@NotNull List<Action> actions) {
    List<JButton> buttons = new ArrayList<>();
    for (Action action : actions) {
      buttons.add(createJButtonForAction(action));
    }
    return buttons;
  }

  @NotNull
  private JPanel createSouthPanel(@NotNull List<JButton> leftSideButtons,
                                  @NotNull List<JButton> rightSideButtons,
                                  boolean hasHelpToMoveToLeftSide) {
    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public Color getBackground() {
        final Color bg = UIManager.getColor("DialogWrapper.southPanelBackground");
        if (getStyle() == DialogStyle.COMPACT && bg != null) {
          return bg;
        }
        return super.getBackground();
      }
    };

    if (myDoNotAsk != null) {
      myCheckBoxDoNotShowDialog = new JCheckBox(myDoNotAsk.getDoNotShowMessage());
      myCheckBoxDoNotShowDialog.setVisible(myDoNotAsk.canBeHidden());
      myCheckBoxDoNotShowDialog.setSelected(!myDoNotAsk.isToBeShown());
      DialogUtil.registerMnemonic(myCheckBoxDoNotShowDialog, '&');
    }
    JComponent doNotAskCheckbox = createDoNotAskCheckbox();

    JPanel lrButtonsPanel = new NonOpaquePanel(new GridBagLayout());
    Insets insets = SystemInfo.isMacOSLeopard && UIUtil.isUnderIntelliJLaF() ? JBUI.insets(0, 8) : JBUI.emptyInsets();

    if (rightSideButtons.size() > 0 || leftSideButtons.size() > 0) {
      GridBag bag = new GridBag().setDefaultInsets(insets);

      if (leftSideButtons.size() > 0) {
        JPanel buttonsPanel = createButtonsPanel(leftSideButtons);
        if (rightSideButtons.size() > 0) {
          buttonsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));  // leave some space between button groups
        }
        lrButtonsPanel.add(buttonsPanel, bag.next());
      }
      lrButtonsPanel.add(Box.createHorizontalGlue(), bag.next().weightx(1).fillCellHorizontally());   // left strut
      if (rightSideButtons.size() > 0) {
        JPanel buttonsPanel = createButtonsPanel(rightSideButtons);
        if (shouldAddErrorNearButtons()) {
          lrButtonsPanel.add(myErrorText, bag.next());
          lrButtonsPanel.add(Box.createHorizontalStrut(10), bag.next());
        }
        lrButtonsPanel.add(buttonsPanel, bag.next());
      }
      if (SwingConstants.CENTER == myButtonAlignment && doNotAskCheckbox == null) {
        lrButtonsPanel.add(Box.createHorizontalGlue(), bag.next().weightx(1).fillCellHorizontally());    // right strut
      }
    }

    JComponent helpButton = null;
    if (hasHelpToMoveToLeftSide) {
      helpButton = createHelpButton(insets);
    }

    JPanel eastPanel = createSouthAdditionalPanel();

    if (helpButton != null || doNotAskCheckbox != null || eastPanel != null) {
      JPanel leftPanel = new JPanel(new BorderLayout());

      if (helpButton != null) leftPanel.add(helpButton, BorderLayout.WEST);

      if (doNotAskCheckbox != null) {
        doNotAskCheckbox.setBorder(JBUI.Borders.emptyRight(20));
        leftPanel.add(doNotAskCheckbox, BorderLayout.CENTER);
      }


      if(eastPanel != null) {
        leftPanel.add(eastPanel, BorderLayout.EAST);
      }

      panel.add(leftPanel, BorderLayout.WEST);
    }
    panel.add(lrButtonsPanel, BorderLayout.CENTER);

    if (getStyle() == DialogStyle.COMPACT) {
      final Color color = UIManager.getColor("DialogWrapper.southPanelDivider");
      Border line = new CustomLineBorder(color != null ? color : OnePixelDivider.BACKGROUND, 1, 0, 0, 0);
      panel.setBorder(new CompoundBorder(line, JBUI.Borders.empty(8, 12)));
    }
    else {
      panel.setBorder(JBUI.Borders.emptyTop(8));
    }

    return panel;
  }

  @Nullable
  protected JComponent createDoNotAskCheckbox() {
    return myCheckBoxDoNotShowDialog != null && myCheckBoxDoNotShowDialog.isVisible() ? myCheckBoxDoNotShowDialog : null;
  }

  private final JBValue BASE_BUTTON_GAP = new JBValue.Float(UIUtil.isUnderWin10LookAndFeel() ? 8 : 12);

  @NotNull
  protected JPanel createButtonsPanel(@NotNull List<JButton> buttons) {
    JPanel buttonsPanel = new NonOpaquePanel();
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));

    for (int i = 0; i < buttons.size(); i++) {
      JComponent button = buttons.get(i);
      Insets insets = button.getInsets();

      buttonsPanel.add(button);
      if (i < buttons.size() - 1) {
        int gap = UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF() ? BASE_BUTTON_GAP.get() - insets.left - insets.right : JBUI.scale(8);
        buttonsPanel.add(Box.createRigidArea(new Dimension(gap, 0)));
      }
    }

    return buttonsPanel;
  }

  /**
   * Additional panel in the lower left part of dialog to the left from additional buttons
   *
   * @return panel to be shown or null if it's not required
   */
  @Nullable
  protected JPanel createSouthAdditionalPanel() {
    return null;
  }

  /**
   *
   * @param action should be registered to find corresponding JButton
   * @return button for specified action or null if it's not found
   */
  @Nullable
  protected JButton getButton(@NotNull Action action) {
    return myButtonMap.get(action);
  }

  /**
   * Creates {@code JButton} for the specified action. If the button has not {@code null}
   * value for {@code DialogWrapper.DEFAULT_ACTION} key then the created button will be the
   * default one for the dialog.
   *
   * @param action action for the button
   * @return button with action specified
   * @see DialogWrapper#DEFAULT_ACTION
   */
  protected JButton createJButtonForAction(Action action) {
    JButton button;
    if (action instanceof OptionAction && UISettings.getShadowInstance().getAllowMergeButtons()) {
      button = createJOptionsButton((OptionAction)action);
    }
    else {
      button = new JButton(action);
    }

    if (SystemInfo.isMac) {
      button.putClientProperty("JButton.buttonType", "text");
    }

    Pair<Integer, String> pair = extractMnemonic(button.getText());
    button.setText(pair.second);
    int mnemonic = pair.first;

    final Object value = action.getValue(Action.MNEMONIC_KEY);
    if (value instanceof Integer) {
      mnemonic = (Integer)value;
    }
    button.setMnemonic(mnemonic);

    final Object name = action.getValue(Action.NAME);
    if (mnemonic == KeyEvent.VK_Y && "Yes".equals(name)) {
      myYesAction = action;
    }
    else if (mnemonic == KeyEvent.VK_N && "No".equals(name)) {
      myNoAction = action;
    }

    if (action.getValue(DEFAULT_ACTION) != null) {
      if (!myPeer.isHeadless()) {
        getRootPane().setDefaultButton(button);
      }
    }

    if (action.getValue(FOCUSED_ACTION) != null) {
      myPreferredFocusedComponent = button;
    }

    return button;
  }

  @NotNull
  private JButton createJOptionsButton(@NotNull OptionAction action) {
    JBOptionButton optionButton = new JBOptionButton(action, action.getOptions());
    String tooltip = String.format("Show drop-down menu (%s)", getKeystrokeText(SHOW_OPTION_KEYSTROKE));
    optionButton.setOptionTooltipText(tooltip);
    optionButton.setOkToProcessDefaultMnemonics(false);

    return optionButton;
  }

  @NotNull
  public static Pair<Integer, String> extractMnemonic(@Nullable String text) {
    if (text == null) return Pair.create(0, null);

    int mnemonic = 0;
    StringBuilder plainText = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == '_' || ch == '&') {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i >= text.length()) {
          break;
        }
        ch = text.charAt(i);
        if (ch != '_' && ch != '&') {
          // Mnemonic is case insensitive.
          int vk = ch;
          if (vk >= 'a' && vk <= 'z') {
            vk -= 'a' - 'A';
          }
          mnemonic = vk;
        }
      }
      plainText.append(ch);
    }
    return Pair.create(mnemonic, plainText.toString());
  }

  @NotNull
  protected DialogWrapperPeer createPeer(@NotNull Component parent, final boolean canBeParent) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, parent, canBeParent);
  }

  /**
   * Dialogs with no parents are discouraged.
   * Instead, use e.g. {@link DialogWrapper#createPeer(Window, boolean, boolean)}
   */
  @Deprecated
  @NotNull
  protected DialogWrapperPeer createPeer(boolean canBeParent, boolean applicationModalIfPossible) {
    return createPeer(null, canBeParent, applicationModalIfPossible);
  }

  @NotNull
  protected DialogWrapperPeer createPeer(final Window owner, final boolean canBeParent, final IdeModalityType ideModalityType) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, owner, canBeParent, ideModalityType);
  }

  @Deprecated
  @NotNull
  protected DialogWrapperPeer createPeer(final Window owner, final boolean canBeParent, final boolean applicationModalIfPossible) {
    return DialogWrapperPeerFactory.getInstance()
      .createPeer(this, owner, canBeParent, applicationModalIfPossible ? IdeModalityType.IDE : IdeModalityType.PROJECT);
  }

  @NotNull
  protected DialogWrapperPeer createPeer(@Nullable final Project project,
                                         final boolean canBeParent,
                                         @NotNull IdeModalityType ideModalityType) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, project, canBeParent, ideModalityType);
  }

  @NotNull
  protected DialogWrapperPeer createPeer(@Nullable final Project project, final boolean canBeParent) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, project, canBeParent);
  }

  @Nullable
  protected JComponent createTitlePane() {
    return null;
  }

  /**
   * Factory method. It creates the panel located at the
   * north of the dialog's content pane. The implementation can return {@code null}
   * value. In this case there will be no input panel.
   *
   * @return north panel
   */
  @Nullable
  protected JComponent createNorthPanel() {
    return null;
  }

  /**
   * Factory method. It creates panel with dialog options. Options panel is located at the
   * center of the dialog's content pane. The implementation can return {@code null}
   * value. In this case there will be no options panel.
   *
   * @return center panel
   */
  @Nullable
  protected abstract JComponent createCenterPanel();

  /**
   * @see Window#toFront()
   */
  public void toFront() {
    myPeer.toFront();
  }

  /**
   * @see Window#toBack()
   */
  public void toBack() {
    myPeer.toBack();
  }

  protected boolean setAutoAdjustable(boolean autoAdjustable) {
    JRootPane rootPane = getRootPane();
    if (rootPane == null) return false;
    rootPane.putClientProperty(NO_AUTORESIZE, autoAdjustable ? null : Boolean.TRUE);
    return true;
  }

  //true by default
  public boolean isAutoAdjustable() {
    JRootPane rootPane = getRootPane();
    return rootPane == null || rootPane.getClientProperty(NO_AUTORESIZE) == null;
  }

  /**
   * Dispose the wrapped and releases all resources allocated be the wrapper to help
   * more efficient garbage collection. You should never invoke this method twice or
   * invoke any method of the wrapper after invocation of {@code dispose}.
   *
   * @throws IllegalStateException if the dialog is disposed not on the event dispatch thread
   */
  protected void dispose() {
    ensureEventDispatchThread();
    myErrorTextAlarm.cancelAllRequests();
    myValidationAlarm.cancelAllRequests();
    myDisposed = true;

    for (JButton button : myButtonMap.values()) {
      button.setAction(null); // avoid memory leak via KeyboardManager
    }
    myButtonMap.clear();

    final JRootPane rootPane = getRootPane();
    // if rootPane = null, dialog has already been disposed
    if (rootPane != null) {
      unregisterKeyboardActions(rootPane);
      if (myActualSize != null && isAutoAdjustable()) {
        setSize(myActualSize.width, myActualSize.height);
      }
      myPeer.dispose();
    }
  }

  public static void cleanupRootPane(@Nullable JRootPane rootPane) {
    if (rootPane == null) return;
    // Must be preserved:
    //   Component#appContext, Component#appContext, Container#component
    //   JRootPane#contentPane due to popup recycling & our border styling
    // Must be cleared:
    //   JComponent#clientProperties, contentPane children
    RepaintManager.currentManager(rootPane).removeInvalidComponent(rootPane);
    unregisterKeyboardActions(rootPane);
    Container contentPane = rootPane.getContentPane();
    if (contentPane != null) contentPane.removeAll();
    Disposer.clearOwnFields(rootPane, field -> {
      String clazz = field.getDeclaringClass().getName();
      // keep AWT and Swing fields intact, except some
      if (!clazz.startsWith("java.") && !clazz.startsWith("javax.")) return true;
      String name = field.getName();
      return "clientProperties".equals(name);
    });
  }

  public static void unregisterKeyboardActions(@Nullable Component rootPane) {
    int[] flags = {JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, JComponent.WHEN_IN_FOCUSED_WINDOW};
    for (JComponent eachComp : UIUtil.uiTraverser(rootPane).traverse().filter(JComponent.class)) {
      ActionMap actionMap = eachComp.getActionMap();
      if (actionMap == null) continue;
      for (KeyStroke eachStroke : eachComp.getRegisteredKeyStrokes()) {
        boolean remove = true;
        for (int i : flags) {
          Object key = eachComp.getInputMap(i).get(eachStroke);
          Action action = key == null ? null : actionMap.get(key);
          if (action instanceof UIResource) remove = false;
        }
        if (remove) eachComp.unregisterKeyboardAction(eachStroke);
      }
    }
  }

  public static void cleanupWindowListeners(@Nullable Window window) {
    if (window == null) return;
    SwingUtilities.invokeLater(() -> {
      for (WindowListener listener : window.getWindowListeners()) {
        if (listener.getClass().getName().startsWith("com.intellij.")) {
          //LOG.warn("Stale listener: " + listener);
          window.removeWindowListener(listener);
        }
      }
    });
  }


  /**
   * This method is invoked by default implementation of "Cancel" action. It just closes dialog
   * with {@code CANCEL_EXIT_CODE}. This is convenient place to override functionality of "Cancel" action.
   * Note that the method does nothing if "Cancel" action isn't enabled.
   */
  public void doCancelAction() {
    if (getCancelAction().isEnabled()) {
      close(CANCEL_EXIT_CODE);
    }
  }

  private void processDoNotAskOnCancel() {
    if (myDoNotAsk != null) {
      if (myDoNotAsk.shouldSaveOptionsOnCancel() && myDoNotAsk.canBeHidden()) {
        myDoNotAsk.setToBeShown(toBeShown(), CANCEL_EXIT_CODE);
      }
    }
  }

  /**
   * You can use this method if you want to know by which event this actions got triggered. It is called only if
   * the cancel action was triggered by some input event, {@code doCancelAction} is called otherwise.
   *
   * @param source AWT event
   * @see #doCancelAction
   */
  public void doCancelAction(AWTEvent source) {
    recordAction("DialogCancelAction", source);
    doCancelAction();
  }

  /**
   * Programmatically perform a "click" of default dialog's button. The method does
   * nothing if the dialog has no default button.
   */
  public void clickDefaultButton() {
    JButton button = getRootPane().getDefaultButton();
    if (button != null) {
      button.doClick();
    }
  }

  /**
   * This method is invoked by default implementation of "OK" action. It just closes dialog
   * with {@code OK_EXIT_CODE}. This is convenient place to override functionality of "OK" action.
   * Note that the method does nothing if "OK" action isn't enabled.
   */
  protected void doOKAction() {
    if (getOKAction().isEnabled()) {
      close(OK_EXIT_CODE);
    }
  }

  protected void processDoNotAskOnOk(int exitCode) {
    if (myDoNotAsk != null) {
      if (myDoNotAsk.canBeHidden()) {
        myDoNotAsk.setToBeShown(toBeShown(), exitCode);
      }
    }
  }

  /**
   * @return whether the native window cross button closes the window or not.
   * {@code true} means that cross performs hide or dispose of the dialog.
   */
  public boolean shouldCloseOnCross() {
    return myCrossClosesWindow;
  }

  /**
   * Creates actions for dialog.
   * <p/>
   * By default "OK" and "Cancel" actions are returned. The "Help" action is automatically added if
   * {@link #getHelpId()} returns non-null value.
   * <p/>
   * Each action is represented by {@code JButton} created by {@link #createJButtonForAction(Action)}.
   * These buttons are then placed into {@link #createSouthPanel() south panel} of dialog.
   *
   * @return dialog actions
   * @see #createSouthPanel
   * @see #createJButtonForAction
   */
  @NotNull
  protected Action[] createActions() {
    Action helpAction = getHelpAction();

    return helpAction == myHelpAction && getHelpId() == null
           ? new Action[]{getOKAction(), getCancelAction()}
           : new Action[]{getOKAction(), getCancelAction(), helpAction};
  }

  @NotNull
  protected Action[] createLeftSideActions() {
    return new Action[0];
  }

  /**
   * @return default implementation of "OK" action. This action just invokes
   * {@code doOKAction()} method.
   * @see #doOKAction
   */
  @NotNull
  protected Action getOKAction() {
    return myOKAction;
  }

  /**
   * @return default implementation of "Cancel" action. This action just invokes
   * {@code doCancelAction()} method.
   * @see #doCancelAction
   */
  @NotNull
  protected Action getCancelAction() {
    return myCancelAction;
  }

  /**
   * @return default implementation of "Help" action. This action just invokes
   * {@code doHelpAction()} method.
   * @see #doHelpAction
   */
  @NotNull
  protected Action getHelpAction() {
    return myHelpAction;
  }

  protected boolean isProgressDialog() {
    return false;
  }

  public final boolean isModalProgress() {
    return isProgressDialog();
  }

  /**
   * Returns content pane
   *
   * @return content pane
   * @see JDialog#getContentPane
   */
  public Container getContentPane() {
    return myPeer.getContentPane();
  }

  /**
   * @see JDialog#validate
   */
  public void validate() {
    myPeer.validate();
  }

  /**
   * @see JDialog#repaint
   */
  public void repaint() {
    myPeer.repaint();
  }

  /**
   * Returns key for persisting dialog dimensions.
   * <p/>
   * Default implementation returns {@code null} (no persisting).
   *
   * @return dimension service key
   */
  @Nullable
  @NonNls
  protected String getDimensionServiceKey() {
    return null;
  }

  @Nullable
  public final String getDimensionKey() {
    return getDimensionServiceKey();
  }

  public int getExitCode() {
    return myExitCode;
  }

  /**
   * @return component which should be focused when the dialog appears
   * on the screen.
   */
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return SystemInfo.isMac ? myPreferredFocusedComponent : null;
  }

  /**
   * @return horizontal stretch of the dialog. It means that the dialog's horizontal size is
   * the product of horizontal stretch by horizontal size of packed dialog. The default value
   * is {@code 1.0f}
   */
  public final float getHorizontalStretch() {
    return myHorizontalStretch;
  }

  /**
   * @return vertical stretch of the dialog. It means that the dialog's vertical size is
   * the product of vertical stretch by vertical size of packed dialog. The default value
   * is {@code 1.0f}
   */
  public final float getVerticalStretch() {
    return myVerticalStretch;
  }

  protected final void setHorizontalStretch(float hStretch) {
    myHorizontalStretch = hStretch;
  }

  protected final void setVerticalStretch(float vStretch) {
    myVerticalStretch = vStretch;
  }

  /**
   * @return window owner
   * @see Window#getOwner
   */
  public Window getOwner() {
    return myPeer.getOwner();
  }

  public Window getWindow() {
    return myPeer.getWindow();
  }

  public JComponent getContentPanel() {
    return (JComponent)myPeer.getContentPane();
  }

  /**
   * @return root pane
   * @see JDialog#getRootPane
   */
  public JRootPane getRootPane() {
    return myPeer.getRootPane();
  }

  /**
   * @return dialog size
   * @see Window#getSize
   */
  public Dimension getSize() {
    return myPeer.getSize();
  }

  /**
   * @return dialog title
   * @see Dialog#getTitle
   */
  public String getTitle() {
    return myPeer.getTitle();
  }

  protected void init() {
    ensureEventDispatchThread();
    myErrorText = new ErrorText(getErrorTextAlignment());
    myErrorText.setVisible(false);
    final ComponentAdapter resizeListener = new ComponentAdapter() {
      private int myHeight;

      @Override
      public void componentResized(ComponentEvent event) {
        int height = !myErrorText.isVisible() ? 0 : event.getComponent().getHeight();
        if (height != myHeight) {
          myHeight = height;
          myResizeInProgress = true;
          myErrorText.setMinimumSize(new Dimension(0, height));
          JRootPane root = myPeer.getRootPane();
          if (root != null) {
            root.validate();
          }
          if (myActualSize != null && !shouldAddErrorNearButtons()) {
            myPeer.setSize(myActualSize.width, myActualSize.height + height);
          }
          myErrorText.revalidate();
          myResizeInProgress = false;
        }
      }
    };
    myErrorText.myLabel.addComponentListener(resizeListener);
    Disposer.register(myDisposable, new Disposable() {
      @Override
      public void dispose() {
        myErrorText.myLabel.removeComponentListener(resizeListener);
      }
    });

    final JPanel root = new JPanel(createRootLayout());
    //{
    //  @Override
    //  public void paint(Graphics g) {
    //    if (ApplicationManager.getApplication() != null) {
    //      UISettings.setupAntialiasing(g);
    //    }
    //    super.paint(g);
    //  }
    //};
    myPeer.setContentPane(root);

    final CustomShortcutSet sc = new CustomShortcutSet(SHOW_OPTION_KEYSTROKE);
    AnAction toggleShowOptions = DumbAwareAction.create(e -> expandNextOptionButton());
    toggleShowOptions.registerCustomShortcutSet(sc, root, myDisposable);

    JComponent titlePane = createTitlePane();
    if (titlePane != null) {
      JPanel northSection = new JPanel(new BorderLayout());
      root.add(northSection, BorderLayout.NORTH);

      northSection.add(titlePane, BorderLayout.CENTER);
    }

    JComponent centerSection = new JPanel(new BorderLayout());
    root.add(centerSection, BorderLayout.CENTER);

    final JComponent n = createNorthPanel();
    if (n != null) {
      centerSection.add(n, BorderLayout.NORTH);
    }

    final JComponent centerPanel = createCenterPanel();
    if (centerPanel != null) {
      centerSection.add(centerPanel, BorderLayout.CENTER);
    }

    boolean isVisualPaddingCompensatedOnComponentLevel = centerPanel == null || centerPanel.getClientProperty("isVisualPaddingCompensatedOnComponentLevel") == null;
    if (isVisualPaddingCompensatedOnComponentLevel) {
      // see comment about visual paddings in the MigLayoutBuilder.build
      root.setBorder(createContentPaneBorder());
    }

    final JPanel southSection = new JPanel(new BorderLayout());
    root.add(southSection, BorderLayout.SOUTH);

    southSection.add(myErrorText, BorderLayout.CENTER);
    final JComponent south = createSouthPanel();
    if (south != null) {
      if (!isVisualPaddingCompensatedOnComponentLevel) {
        south.setBorder(JBUI.Borders.empty(0, 12, 8, 12));
      }
      southSection.add(south, BorderLayout.SOUTH);
    }

    MnemonicHelper.init(root);
    if (!postponeValidation()) {
      startTrackingValidation();
    }
    if (SystemInfo.isWindows) {
      installEnterHook(root, myDisposable);
    }
    myErrorTextAlarm.setActivationComponent(root);
  }

  protected int getErrorTextAlignment() {
    return SwingConstants.LEADING;
  }

  @NotNull
  protected LayoutManager createRootLayout() {
    return new BorderLayout();
  }

  private static void installEnterHook(JComponent root, Disposable disposable) {
    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner instanceof JButton && owner.isEnabled()) {
          ((JButton)owner).doClick();
        }
      }

      @Override
      public void update(AnActionEvent e) {
        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        e.getPresentation().setEnabled(owner instanceof JButton && owner.isEnabled());
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), root, disposable);
  }

  private void expandNextOptionButton() {
    if (myCurrentOptionsButtonIndex >= 0) {
      myOptionsButtons.get(myCurrentOptionsButtonIndex).closePopup();
    }
    myCurrentOptionsButtonIndex = getEnabledIndexCyclic(myOptionsButtons, myCurrentOptionsButtonIndex, true).orElse(-1);
    if (myCurrentOptionsButtonIndex >= 0) {
      myOptionsButtons.get(myCurrentOptionsButtonIndex).showPopup(null, true);
    }
  }

  protected void startTrackingValidation() {
    SwingUtilities.invokeLater(() -> {
      if (!myValidationStarted && !myDisposed) {
        myValidationStarted = true;
        initValidation();
      }
    });
  }

  protected final void initValidation() {
    myValidationAlarm.cancelAllRequests();
    final Runnable validateRequest = () -> {
      if (myDisposed) return;
      List<ValidationInfo> result = doValidateAll();
      if (!result.isEmpty()) {
        installErrorPainter();
      }
      myErrorPainter.setValidationInfo(result);
      updateErrorInfo(result);

      if (!myDisposed) {
        initValidation();
      }
    };

    if (getValidationThreadToUse() == Alarm.ThreadToUse.SWING_THREAD) {
      // null if headless
      JRootPane rootPane = getRootPane();
      myValidationAlarm.addRequest(validateRequest, myValidationDelay,
                                   ApplicationManager.getApplication() == null
                                   ? null
                                   : rootPane == null ? ModalityState.current() : ModalityState.stateForComponent(rootPane));
    }
    else {
      myValidationAlarm.addRequest(validateRequest, myValidationDelay);
    }
  }

  protected boolean isNorthStrictedToPreferredSize() {
    return true;
  }

  protected boolean isCenterStrictedToPreferredSize() {
    return false;
  }

  protected boolean isSouthStrictedToPreferredSize() {
    return true;
  }

  @NotNull
  protected JComponent createContentPane() {
    return new JPanel();
  }

  /**
   * @see Window#pack
   */
  public void pack() {
    myPeer.pack();
  }

  public Dimension getPreferredSize() {
    return myPeer.getPreferredSize();
  }

  /**
   * Sets horizontal alignment of dialog's buttons.
   *
   * @param alignment alignment of the buttons. Acceptable values are
   *                  {@code SwingConstants.CENTER} and {@code SwingConstants.RIGHT}.
   *                  The {@code SwingConstants.RIGHT} is the default value.
   * @throws IllegalArgumentException if {@code alignment} isn't acceptable
   */
  protected final void setButtonsAlignment(@MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.RIGHT}) int alignment) {
    if (SwingConstants.CENTER != alignment && SwingConstants.RIGHT != alignment) {
      throw new IllegalArgumentException("unknown alignment: " + alignment);
    }
    myButtonAlignment = alignment;
  }

  /**
   * Sets margin for command buttons ("OK", "Cancel", "Help").
   *
   * @Deprecated Button margins aren't used anymore. Button style is standardized.
   * @param insets buttons margin
   */
  @Deprecated
  public final void setButtonsMargin(@Nullable Insets insets) {}

  public final void setCrossClosesWindow(boolean crossClosesWindow) {
    myCrossClosesWindow = crossClosesWindow;
  }

  protected final void setCancelButtonIcon(Icon icon) {
    // Setting icons causes buttons be 'square' style instead of
    // 'rounded', which is expected by apple users.
    if (!SystemInfo.isMac) {
      myCancelAction.putValue(Action.SMALL_ICON, icon);
    }
  }

  protected final void setCancelButtonText(String text) {
    myCancelAction.putValue(Action.NAME, text);
  }

  public void setModal(boolean modal) {
    myPeer.setModal(modal);
  }

  public boolean isModal() {
    return myPeer.isModal();
  }

  public boolean isOKActionEnabled() {
    return myOKAction.isEnabled();
  }

  public void setOKActionEnabled(boolean isEnabled) {
    myOKAction.setEnabled(isEnabled);
  }

  protected final void setOKButtonIcon(Icon icon) {
    // Setting icons causes buttons be 'square' style instead of
    // 'rounded', which is expected by apple users.
    if (!SystemInfo.isMac) {
      myOKAction.putValue(Action.SMALL_ICON, icon);
    }
  }

  /**
   * @param text action without mnemonic. If mnemonic is set, presentation would be shifted by one to the left
   *             {@link AbstractButton#setText(String)}
   *             {@link AbstractButton#updateDisplayedMnemonicIndex(String, int)}
   */
  protected final void setOKButtonText(String text) {
    myOKAction.putValue(Action.NAME, text);
  }

  protected final void setOKButtonMnemonic(int c) {
    myOKAction.putValue(Action.MNEMONIC_KEY, c);
  }

  protected final void setOKButtonTooltip(String text) {
    myOKAction.putValue(Action.SHORT_DESCRIPTION, text);
  }

  /**
   * @return the help identifier or null if no help is available.
   */
  @Nullable @NonNls
  protected String getHelpId() {
    return null;
  }

  /**
   * Invoked by default implementation of "Help" action.
   * Note that the method does nothing if "Help" action isn't enabled.
   * <p/>
   * The default implementation shows the help page with id returned
   * by {@link #getHelpId()}. If that method returns null,
   * a message box with message "no help available" is shown.
   */
  protected void doHelpAction() {
    if (myHelpAction.isEnabled()) {
      String helpId = getHelpId();
      if (helpId != null) {
        HelpManager.getInstance().invokeHelp(helpId);
      }
      else {
        Messages.showMessageDialog(getContentPane(), UIBundle.message("there.is.no.help.for.this.dialog.error.message"),
                                   UIBundle.message("no.help.available.dialog.title"), Messages.getInformationIcon());
      }
    }
  }

  public boolean isOK() {
    return getExitCode() == OK_EXIT_CODE;
  }

  /**
   * @return {@code true} if and only if visible
   * @see Component#isVisible
   */
  public boolean isVisible() {
    return myPeer.isVisible();
  }

  /**
   * @return {@code true} if and only if showing
   * @see Window#isShowing
   */
  public boolean isShowing() {
    return myPeer.isShowing();
  }

  /**
   * @param width  width
   * @param height height
   * @see JDialog#setSize
   */
  public void setSize(int width, int height) {
    myPeer.setSize(width, height);
  }

  /**
   * @param title title
   * @see JDialog#setTitle
   */
  public void setTitle(@Nls(capitalization = Nls.Capitalization.Title) String title) {
    myPeer.setTitle(title);
  }

  /**
   * @see JDialog#isResizable
   */
  public void isResizable() {
    myPeer.isResizable();
  }

  /**
   * @param resizable is resizable
   * @see JDialog#setResizable
   */
  public void setResizable(boolean resizable) {
    myPeer.setResizable(resizable);
  }

  /**
   * @return dialog location
   * @see JDialog#getLocation
   */
  @NotNull
  public Point getLocation() {
    return myPeer.getLocation();
  }

  /**
   * @param p new dialog location
   * @see JDialog#setLocation(Point)
   */
  public void setLocation(@NotNull Point p) {
    myPeer.setLocation(p);
  }

  /**
   * @param x x
   * @param y y
   * @see JDialog#setLocation(int, int)
   */
  public void setLocation(int x, int y) {
    myPeer.setLocation(x, y);
  }

  public void centerRelativeToParent() {
    myPeer.centerInParent();
  }

  /**
   * Show the dialog.
   *
   * @throws IllegalStateException if the method is invoked not on the event dispatch thread
   * @see #showAndGet()
   * @see #showAndGetOk()
   */
  public void show() {
    logShowDialogEvent();
    invokeShow();
  }

  /**
   * Show the modal dialog and check if it was closed with OK.
   *
   * @return true if the {@link #getExitCode() exit code} is {@link #OK_EXIT_CODE}.
   * @throws IllegalStateException if the dialog is non-modal, or if the method is invoked not on the EDT.
   * @see #show()
   * @see #showAndGetOk()
   */
  public boolean showAndGet() {
    if (!isModal()) {
      throw new IllegalStateException("The showAndGet() method is for modal dialogs only");
    }
    show();
    return isOK();
  }

  /**
   * You need this method ONLY for NON-MODAL dialogs. Otherwise, use {@link #show()} or {@link #showAndGet()}.
   *
   * @return result callback which set to "Done" on dialog close, and then its {@code getResult()} will contain {@code isOK()}
   */
  @NotNull
  public AsyncResult<Boolean> showAndGetOk() {
    if (isModal()) {
      throw new IllegalStateException("The showAndGetOk() method is for modeless dialogs only");
    }
    return invokeShow();
  }

  @NotNull
  private AsyncResult<Boolean> invokeShow() {
    final AsyncResult<Boolean> result = new AsyncResult<>();

    ensureEventDispatchThread();
    registerKeyboardShortcuts();

    final Disposable uiParent = Disposer.get("ui");
    if (uiParent != null) { // may be null if no app yet (license agreement)
      Disposer.register(uiParent, myDisposable); // ensure everything is disposed on app quit
    }

    Disposer.register(myDisposable, new Disposable() {
      @Override
      public void dispose() {
        result.setDone(isOK());
      }
    });

    myPeer.show();

    return result;
  }

  /**
   * @return Location in absolute coordinates which is used when dialog has no dimension service key or no position was stored yet.
   * Can return null. In that case dialog will be centered relative to its owner.
   */
  @Nullable
  public Point getInitialLocation() {
    return myInitialLocationCallback == null ? null : myInitialLocationCallback.compute();
  }

  public void setInitialLocationCallback(@NotNull Computable<Point> callback) {
    myInitialLocationCallback = callback;
  }

  private void registerKeyboardShortcuts() {
    final JRootPane rootPane = getRootPane();

    if (rootPane == null) return;

    ActionListener cancelKeyboardAction = createCancelAction();
    if (cancelKeyboardAction != null) {
      rootPane
        .registerKeyboardAction(cancelKeyboardAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
      ActionUtil.registerForEveryKeyboardShortcut(getRootPane(), cancelKeyboardAction, CommonShortcuts.getCloseActiveWindow());
    }

    if (ApplicationInfo.contextHelpAvailable() && !isProgressDialog()) {
      ActionListener helpAction = e -> doHelpAction();
      ActionUtil.registerForEveryKeyboardShortcut(getRootPane(), helpAction, CommonShortcuts.getContextHelp());
      rootPane.registerKeyboardAction(helpAction, KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    rootPane.registerKeyboardAction(e -> focusButton(false), KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
                                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    rootPane.registerKeyboardAction(e -> focusButton(true), KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
                                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    if (myYesAction != null) {
      rootPane.registerKeyboardAction(myYesAction, KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    if (myNoAction != null) {
      rootPane.registerKeyboardAction(myNoAction, KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
  }

  /**
   *
   * @return null if we should ignore <Esc> for window closing
   */
  @Nullable
  protected ActionListener createCancelAction() {
    return new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (!PopupUtil.handleEscKeyEvent()) {
            doCancelAction(e);
          }
        }
      };
  }

  private void focusButton(boolean next) {
    List<JButton> buttons = newArrayList(myButtonMap.values());
    int focusedIndex = indexOf(buttons, (Condition<? super Component>)Component::hasFocus);

    if (focusedIndex >= 0) {
      getEnabledIndexCyclic(buttons, focusedIndex, next).ifPresent(i -> buttons.get(i).requestFocus());
    }
  }

  @NotNull
  private static OptionalInt getEnabledIndexCyclic(@NotNull List<? extends Component> components, int currentIndex, boolean next) {
    assert -1 <= currentIndex && currentIndex <= components.size();
    int start = !next && currentIndex == -1 ? components.size() : currentIndex;

    return IntStream.range(0, components.size())
      .map(i -> (next ? start + i + 1 : start + components.size() - i - 1) % components.size())
      .filter(i -> components.get(i).isEnabled())
      .findFirst();
  }

  public long getTypeAheadTimeoutMs() {
    return 0L;
  }

  public boolean isToDispatchTypeAhead() {
    return isOK();
  }

  public static boolean isMultipleModalDialogs() {
    final Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (c != null) {
      final DialogWrapper wrapper = findInstance(c);
      return wrapper != null && wrapper.getPeer().getCurrentModalEntities().length > 1;
    }
    return false;
  }

  private void logCloseDialogEvent(int exitCode) {
    final String dialogId = getLoggedDialogId();
    if (StringUtil.isNotEmpty(dialogId)) {
      FeatureUsageUiEvents.INSTANCE.logCloseDialog(dialogId, exitCode);
    }
  }

  private void logShowDialogEvent() {
    final String dialogId = getLoggedDialogId();
    if (StringUtil.isNotEmpty(dialogId)) {
      FeatureUsageUiEvents.INSTANCE.logShowDialog(dialogId);
    }
  }

  /**
   * The ID will be recorded in user event log, it can be used to understand how often this dialog is used.
   *
   * @return null if we shouldn't record the dialog.
   */
  @Nullable
  protected String getLoggedDialogId() {
    return getClass().getName();
  }

  /**
   * Base class for dialog wrapper actions that need to ensure that only
   * one action for the dialog is running.
   */
  protected abstract class DialogWrapperAction extends AbstractAction {
    /**
     * The constructor
     *
     * @param name the action name (see {@link Action#NAME})
     */
    protected DialogWrapperAction(@NotNull String name) {
      putValue(NAME, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
      if (myClosed) return;
      if (myPerformAction) return;
      try {
        myPerformAction = true;
        doAction(e);
      }
      finally {
        myPerformAction = false;
      }
    }

    /**
     * Do actual work for the action. This method is called only if no other action
     * is performed in parallel (checked using {@link DialogWrapper#myPerformAction}),
     * and dialog is active (checked using {@link DialogWrapper#myClosed})
     *
     * @param e action
     */
    protected abstract void doAction(ActionEvent e);
  }

  protected class OkAction extends DialogWrapperAction {
    protected OkAction() {
      super(CommonBundle.getOkButtonText());
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    protected void doAction(ActionEvent e) {
      recordAction("DialogOkAction");
      List<ValidationInfo> infoList = doValidateAll();
      if (!infoList.isEmpty()) {
        ValidationInfo info = infoList.get(0);
        if (info.component != null && info.component.isVisible()) {
          IdeFocusManager.getInstance(null).requestFocus(info.component, true);
        }

        if (!Registry.is("ide.inplace.errors.balloon")) {
          DialogEarthquakeShaker.shake(getPeer().getWindow());
        }

        startTrackingValidation();
        if(infoList.stream().anyMatch(info1 -> info1.disableOk)) return;
      }
      doOKAction();
    }
  }

  private void recordAction(String name) {
    recordAction(name, EventQueue.getCurrentEvent());
  }

  private void recordAction(String name, AWTEvent event) {
    if (event instanceof KeyEvent && ApplicationManager.getApplication() != null) {
      String shortcut = getKeystrokeText(KeyStroke.getKeyStrokeForEvent((KeyEvent)event));
      ActionsCollector.getInstance().record(name + " " + shortcut);
    }
  }

  protected class CancelAction extends DialogWrapperAction {
    private CancelAction() {
      super(CommonBundle.getCancelButtonText());
    }

    @Override
    protected void doAction(ActionEvent e) {
      doCancelAction();
    }
  }

  /**
   * The action that just closes dialog with the specified exit code
   * (like the default behavior of the actions "Ok" and "Cancel").
   */
  protected class DialogWrapperExitAction extends DialogWrapperAction {
    /**
     * The exit code for the action
     */
    protected final int myExitCode;

    /**
     * The constructor
     *
     * @param name     the action name
     * @param exitCode the exit code for dialog
     */
    public DialogWrapperExitAction(String name, int exitCode) {
      super(name);
      myExitCode = exitCode;
    }

    @Override
    protected void doAction(ActionEvent e) {
      if (isEnabled()) {
        close(myExitCode);
      }
    }
  }

  private class HelpAction extends AbstractAction {
    private HelpAction() {
      putValue(NAME, CommonBundle.getHelpButtonText());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      doHelpAction();
    }
  }

  /**
   * Don't override this method. It is not final for the API compatibility.
   * It will not be called by the DialogWrapper's validator.
   * Use this method only in circumstances when the exact invalid component is hard to
   * detect or the valid status is based on several fields. In other cases use
   * <code>{@link #setErrorText(String, JComponent)}</code> method.
   * @param text the error text to display
   */
  protected void setErrorText(@Nullable final String text) {
    setErrorText(text, null);
  }

  protected void setErrorText(@Nullable final String text, @Nullable final JComponent component) {
    setErrorInfoAll((text == null) ?
                 Collections.EMPTY_LIST :
                 Collections.singletonList(new ValidationInfo(text, component)));
  }

  protected void setErrorInfoAll(@NotNull List<ValidationInfo> info) {
    if (myInfo.equals(info)) return;

    myErrorTextAlarm.cancelAllRequests();
    SwingUtilities.invokeLater(() -> {
      if (myErrorText != null) {
        myErrorText.clearError();
      }
    });

    List<ValidationInfo> corrected = myInfo.stream().filter((vi) -> !info.contains(vi)).collect(Collectors.toList());
    if (Registry.is("ide.inplace.errors.outline")) {
      corrected.stream().filter(vi -> (vi.component != null && vi.component.getBorder() instanceof ErrorBorderCapable)).
            forEach(vi -> vi.component.putClientProperty("JComponent.outline", null));
    }

    if (Registry.is("ide.inplace.errors.balloon")) {
      corrected.stream().filter(vi -> vi.component != null).forEach(vi -> {
        vi.component.putClientProperty("JComponent.error.balloon.builder", null);

        Balloon balloon = (Balloon)vi.component.getClientProperty("JComponent.error.balloon");
        if (balloon != null && !balloon.isDisposed()) {
          balloon.hide();
        }

        Component fc = getFocusable(vi.component);
        if (fc != null) {
          for (FocusListener fl : fc.getFocusListeners()) {
            if (fl instanceof ErrorFocusListener) {
              fc.removeFocusListener(fl);
            }
          }
        }
      });
    }

    myInfo = info;

    if (Registry.is("ide.inplace.errors.outline")) {
      myInfo.stream().filter(vi -> (vi.component != null && vi.component.getBorder() instanceof ErrorBorderCapable)).
        forEach(vi -> vi.component.putClientProperty("JComponent.outline", "error"));
    }

    if (Registry.is("ide.inplace.errors.balloon") && !myInfo.isEmpty()) {
      for (ValidationInfo vi : myInfo) {
        Component fc = getFocusable(vi.component);
        if (fc != null && fc.isFocusable()) {
          if (vi.component.getClientProperty("JComponent.error.balloon.builder") == null) {
            JLabel label = new JLabel();
            label.setHorizontalAlignment(SwingConstants.LEADING);
            setErrorTipText(vi.component, label, vi.message);

            BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(label)
              .setDisposable(getDisposable())
              .setBorderInsets(UIManager.getInsets("Balloon.error.textInsets"))
              .setPointerSize(new JBDimension(17, 6))
              .setCornerToPointerDistance(JBUI.scale(30))
              .setHideOnKeyOutside(false)
              .setHideOnClickOutside(false)
              .setHideOnAction(false)
              .setBorderColor(BALLOON_ERROR_BORDER)
              .setFillColor(BALLOON_ERROR_BACKGROUND)
              .setHideOnFrameResize(false)
              .setRequestFocus(false)
              .setAnimationCycle(100)
              .setShadow(true);

            vi.component.putClientProperty("JComponent.error.balloon.builder", balloonBuilder);

            ErrorFocusListener fl = new ErrorFocusListener(label, vi.message, vi.component);
            if (fc.hasFocus()) {
              fl.showErrorTip();
            }

            fc.addFocusListener(fl);
            Disposer.register(getDisposable(), () -> fc.removeFocusListener(fl));
          }
        } else {
          SwingUtilities.invokeLater(() -> myErrorText.appendError(vi.message));
        }
      }
    } else if (!myInfo.isEmpty()) {
      myErrorTextAlarm.addRequest(() -> {
        for (ValidationInfo vi : myInfo) {
          myErrorText.appendError(vi.message);
        }
      }, 300, null);
    }
  }

  /**
   * Check if component is in error state validation-wise
   */
  protected boolean hasErrors(@NotNull JComponent component) {
    return myInfo.stream().anyMatch(i -> component.equals(i.component));
  }

  private void setErrorTipText(JComponent component, JLabel label, String text) {
    Insets insets = UIManager.getInsets("Balloon.error.textInsets");
    int oneLineWidth = SwingUtilities2.stringWidth(label, label.getFontMetrics(label.getFont()), text);
    int textWidth = getRootPane().getWidth() - component.getX() - insets.left - insets.right - JBUI.scale(30);
    if (textWidth < JBUI.scale(90)) textWidth = JBUI.scale(90);
    if (textWidth > oneLineWidth) textWidth = oneLineWidth;

    String htmlText = String.format("<html><div width=%d>%s</div></html>", textWidth, text);
    label.setText(htmlText);
  }

  private Component getFocusable(Component source) {
    return source instanceof JComboBox && !((JComboBox)source).isEditable() ?
           source :
           UIUtil.uiTraverser(source).filter(c -> c instanceof JTextComponent && c.isFocusable()).toList().stream().findFirst().orElse(null);
  }

  private void updateSize() {
    if (myActualSize == null && !myErrorText.isVisible()) {
      myActualSize = getSize();
    }
  }

  @Nullable
  public static DialogWrapper findInstance(Component c) {
    while (c != null) {
      if (c instanceof DialogWrapperDialog) {
        return ((DialogWrapperDialog)c).getDialogWrapper();
      }
      c = c.getParent();
    }
    return null;
  }

  @Nullable
  public static DialogWrapper findInstanceFromFocus() {
    return findInstance(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
  }

  private void resizeWithAnimation(@NotNull final Dimension size) {
    //todo[kb]: fix this PITA
    myResizeInProgress = true;
    if (!Registry.is("enable.animation.on.dialogs")) {
      setSize(size.width, size.height);
      myResizeInProgress = false;
      return;
    }

    new Thread("DialogWrapper resizer") {
      int time = 200;
      int steps = 7;

      @Override
      public void run() {
        int step = 0;
        final Dimension cur = getSize();
        int h = (size.height - cur.height) / steps;
        int w = (size.width - cur.width) / steps;
        while (step++ < steps) {
          setSize(cur.width + w * step, cur.height + h * step);
          TimeoutUtil.sleep(time / steps);
        }
        setSize(size.width, size.height);
        //repaint();
        if (myErrorText.shouldBeVisible()) {
          myErrorText.setVisible(true);
        }
        myResizeInProgress = false;
      }
    }.start();
  }

  private class ErrorText extends JPanel {
    private final JLabel myLabel = new JLabel();
    private final List<String> errors = new ArrayList<>();

    private ErrorText(int horizontalAlignment) {
      setLayout(new BorderLayout());
      myLabel.setBorder(JBUI.Borders.empty(16, 13));
      myLabel.setHorizontalAlignment(horizontalAlignment);
      JBScrollPane pane =
        new JBScrollPane(myLabel, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setBorder(JBUI.Borders.empty());
      pane.setBackground(null);
      pane.getViewport().setBackground(null);
      pane.setOpaque(false);
      add(pane, BorderLayout.CENTER);
    }

    private void clearError() {
      errors.clear();
      myLabel.setBounds(0, 0, 0, 0);
      myLabel.setText("");
      setVisible(false);
      updateSize();
    }

    private void appendError(String text) {
      errors.add(text);
      myLabel.setBounds(0, 0, 0, 0);
      StringBuilder sb = new StringBuilder("<html><font color='#" + ColorUtil.toHex(JBColor.RED) + "'>");
      errors.forEach(error -> sb.append("<left>").append(error).append("</left><br/>"));
      sb.append("</font></html>");
      myLabel.setText(sb.toString());
      setVisible(true);
      updateSize();
    }

    private boolean shouldBeVisible() {
      return !errors.isEmpty();
    }

    private boolean isTextSet(@NotNull List<ValidationInfo> info) {
      if (info.isEmpty()) {
        return errors.isEmpty();
      } else if (errors.size() == info.size()){
        return errors.equals(info.stream().map(i -> i.message).collect(Collectors.toList()));
      } else {
        return false;
      }
    }
  }

  @NotNull
  public final DialogWrapperPeer getPeer() {
    return myPeer;
  }

  /**
   * Ensure that dialog is used from even dispatch thread.
   *
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  private static void ensureEventDispatchThread() {
    if (!EventQueue.isDispatchThread()) {
      throw new IllegalStateException("The DialogWrapper can only be used in event dispatch thread. Current thread: "+Thread.currentThread());
    }
  }

  @NotNull
  public final Disposable getDisposable() {
    return myDisposable;
  }

  /**
   * @see Adapter
   */
  public interface DoNotAskOption {

    abstract class Adapter implements DoNotAskOption {

      /**
       * Save the state of the checkbox in the settings, or perform some other related action.
       * This method is called right after the dialog is {@link #close(int) closed}.
       * <br/>
       * Note that this method won't be called in the case when the dialog is closed by {@link #CANCEL_EXIT_CODE Cancel}
       * if {@link #shouldSaveOptionsOnCancel() saving the choice on cancel is disabled} (which is by default).
       *
       * @param isSelected true if user selected "don't show again".
       * @param exitCode   the {@link #getExitCode() exit code} of the dialog.
       * @see #shouldSaveOptionsOnCancel()
       */
      public abstract void rememberChoice(boolean isSelected, int exitCode);

      /**
       * Tells whether the checkbox should be selected by default or not.
       *
       * @return true if the checkbox should be selected by default.
       */
      public boolean isSelectedByDefault() {
        return false;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return false;
      }

      @NotNull
      @Override
      public String getDoNotShowMessage() {
        return CommonBundle.message("dialog.options.do.not.ask");
      }

      @Override
      public final boolean isToBeShown() {
        return !isSelectedByDefault();
      }

      @Override
      public final void setToBeShown(boolean toBeShown, int exitCode) {
        rememberChoice(!toBeShown, exitCode);
      }

      @Override
      public final boolean canBeHidden() {
        return true;
      }
    }

    /**
     * @return default selection state of checkbox (false -> checkbox selected)
     */
    boolean isToBeShown();

    /**
     * @param toBeShown - if dialog should be shown next time (checkbox selected -> false)
     * @param exitCode of corresponding DialogWrapper
     */
    void setToBeShown(boolean toBeShown, int exitCode);

    /**
     * @return true if checkbox should be shown
     */
    boolean canBeHidden();

    boolean shouldSaveOptionsOnCancel();

    @NotNull
    String getDoNotShowMessage();
  }

  @NotNull
  private ErrorPaintingType getErrorPaintingType() {
    return ErrorPaintingType.SIGN;
  }

  private class ErrorPainter extends AbstractPainter {
    private List<ValidationInfo> info;

    @Override
    public void executePaint(Component component, Graphics2D g) {
      for (ValidationInfo i : info) {
        if (i.component != null && !(Registry.is("ide.inplace.errors.outline"))) {
          int w = i.component.getWidth();
          int h = i.component.getHeight();
          Point p;
          switch (getErrorPaintingType()) {
            case DOT:
              p = SwingUtilities.convertPoint(i.component, 2, h / 2, component);
              AllIcons.Ide.ErrorPoint.paintIcon(component, g, p.x, p.y);
              break;
            case SIGN:
              p = SwingUtilities.convertPoint(i.component, w, 0, component);
              AllIcons.General.Error.paintIcon(component, g, p.x - 8, p.y - 8);
              break;
            case LINE:
              p = SwingUtilities.convertPoint(i.component, 0, h, component);
              Graphics g2 = g.create();
              try {
                //noinspection UseJBColor
                g2.setColor(new Color(255, 0, 0, 100));
                g2.fillRoundRect(p.x, p.y - 2, w, 4, 2, 2);
              } finally {
                g2.dispose();
              }
              break;
          }
        }
      }
    }

    @Override
    public boolean needsRepaint() {
      return true;
    }

    private void setValidationInfo(@NotNull List<ValidationInfo> info) {
      this.info = info;
    }
  }

  private static class ErrorTipTracker extends PositionTracker<Balloon> {
    private final int y;

    private ErrorTipTracker(JComponent component, int y) {
      super(component);
      this.y = y;
    }

    @Override public RelativePoint recalculateLocation(Balloon balloon) {
      int width = getComponent().getWidth();
      int delta = width < JBUI.scale(120) ? width / 2 : JBUI.scale(60);
      return new RelativePoint(getComponent(), new Point(delta, y));
    }
  }

  private class ErrorFocusListener implements FocusListener {
    private final JLabel     label;
    private final String     text;
    private final JComponent component;

    private ErrorFocusListener(JLabel label, String text, JComponent component) {
      this.label = label;
      this.text = text;
      this.component = component;
    }

    @Override public void focusGained(FocusEvent e) {
      Balloon b = (Balloon)component.getClientProperty("JComponent.error.balloon");
      if (b == null || b.isDisposed()) {
        showErrorTip();
      }
    }

    @Override public void focusLost(FocusEvent e) {
      Balloon b = (Balloon)component.getClientProperty("JComponent.error.balloon");
      if (b != null && !b.isDisposed()) {
        b.hide();
      }
    }

    private void showErrorTip() {
      BalloonBuilder balloonBuilder = (BalloonBuilder)component.getClientProperty("JComponent.error.balloon.builder");
      if (balloonBuilder == null) return;

      Balloon balloon = balloonBuilder.createBalloon();

      ComponentListener rl = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) {
          if (!balloon.isDisposed()) {
            setErrorTipText(component, label, text);
            balloon.revalidate();
          }
        }
      };

      balloon.addListener(new JBPopupListener() {
        @Override public void onClosed(LightweightWindowEvent event) {
          JRootPane rootPane = getRootPane();
          if (rootPane != null) {
            rootPane.removeComponentListener(rl);
          }

          if (component.getClientProperty("JComponent.error.balloon") == event.asBalloon()) {
            component.putClientProperty("JComponent.error.balloon", null);
          }
        }
      });

      getRootPane().addComponentListener(rl);

      Point componentPos = SwingUtilities.convertPoint(component, 0, 0, getRootPane().getLayeredPane());
      Dimension bSize = balloon.getPreferredSize();

      Insets cInsets = component.getInsets();
      int top =  cInsets != null ? cInsets.top : 0;
      if (componentPos.y >= bSize.height + top) {
        balloon.show(new ErrorTipTracker(component, 0), Balloon.Position.above);
      } else {
        balloon.show(new ErrorTipTracker(component, component.getHeight()), Balloon.Position.below);
      }
      component.putClientProperty("JComponent.error.balloon", balloon);
    }
  }

  private enum ErrorPaintingType {DOT, SIGN, LINE}

  public enum DialogStyle {NO_STYLE, COMPACT}

}
