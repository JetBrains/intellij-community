// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEventsKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.UiInterceptors;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.*;
import java.util.stream.IntStream;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.ui.components.JBOptionButton.getDefaultShowPopupShortcut;
import static com.intellij.ui.components.JBOptionButton.getDefaultTooltip;
import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;

/**
 * The standard base class for modal dialog boxes. The dialog wrapper could be used only on event dispatch thread.
 * In case when the dialog must be created from other threads use
 * {@link EventQueue#invokeLater(Runnable)} or {@link EventQueue#invokeAndWait(Runnable)}.
 *
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/user_interface_components/dialog_wrapper.html">DialogWrapper on SDK DevGuide</a>
 */
public abstract class DialogWrapper {
  private static final Logger LOG = Logger.getInstance(DialogWrapper.class);

  private final JPanel myRoot = new JPanel();

  public enum IdeModalityType {
    IDE,
    PROJECT,
    MODELESS;

    public @NotNull Dialog.ModalityType toAwtModality() {
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

  public enum DialogStyle {NO_STYLE, COMPACT}

  @ApiStatus.Internal
  public static final @NotNull String IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY = "isVisualPaddingCompensatedOnComponentLevel";

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
   * If you use your own custom exit codes you have to start them with this constant.
   */
  public static final int NEXT_USER_EXIT_CODE = 2;

  /**
   * If your action returned by {@code createActions} method has non
   * {@code null} value for this key, then the button that corresponds to the action will be the
   * default button for the dialog. It's true if you don't change this behaviour
   * of {@code createJButtonForAction(Action)} method.
   */
  public static final String DEFAULT_ACTION = "DefaultAction";

  public static final String FOCUSED_ACTION = "FocusedAction";

  public static final Object DIALOG_CONTENT_PANEL_PROPERTY = new Object();

  /**
   * @deprecated use {@link UIUtil#getErrorForeground()}
   */
  @Deprecated
  public static final Color ERROR_FOREGROUND_COLOR = UIUtil.getErrorForeground();

  /**
   * The shared instance of default border for dialog's content pane.
   */
  public static @NotNull Border createDefaultBorder() {
    return new JBEmptyBorder(UIUtil.getRegularPanelInsets());
  }

  private static final String NO_AUTO_RESIZE = "NoAutoResizeAndFit";

  protected final @NotNull Disposable myDisposable = new Disposable() {
    @Override
    public @NonNls String toString() {
      return DialogWrapper.this.toString();
    }

    @Override
    public void dispose() {
      DialogWrapper.this.dispose();
    }
  };

  private final @NotNull DialogWrapperPeer myPeer;
  private final Map<Action, JButton> myButtonMap = new LinkedHashMap<>();
  private final boolean myCreateSouthSection;
  private final List<JBOptionButton> myOptionsButtons = new ArrayList<>();
  private final Alarm myValidationAlarm = new Alarm(getValidationThreadToUse(), myDisposable);

  private JComponent centerPanel;
  private boolean myClosed;
  private boolean myDisposed;
  private int myExitCode = CANCEL_EXIT_CODE;
  private float myHorizontalStretch = 1.0f;
  private float myVerticalStretch = 1.0f;
  private int myButtonAlignment = SwingConstants.RIGHT;
  private boolean myCrossClosesWindow = true;
  private JComponent myPreferredFocusedComponentFromPanel;
  private Computable<? extends Point> myInitialLocationCallback;
  private final Rectangle myUserBounds = new Rectangle();
  private boolean myUserLocationSet;
  private boolean myUserSizeSet;
  private Dimension  myActualSize;
  private List<ValidationInfo> myInfo = List.of();
  private @Nullable com.intellij.openapi.ui.DoNotAskOption myDoNotAsk;
  private Action myYesAction;
  private Action myNoAction;
  private int myCurrentOptionsButtonIndex = -1;
  private boolean myResizeInProgress;
  private ComponentAdapter myResizeListener;
  private DialogPanel myDialogPanel;
  private ErrorText myErrorText;
  private int myValidationDelay = 300;
  private boolean myValidationStarted;

  protected Action myOKAction;
  protected Action myCancelAction;
  protected Action myHelpAction;
  protected boolean myPerformAction;
  protected JCheckBox myCheckBoxDoNotShowDialog;
  protected JComponent myPreferredFocusedComponent;

  /**
   * Creates modal {@code DialogWrapper}. The currently active window will be the dialog's parent.
   *
   * @param project     parent window for the dialog will be calculated based on focused window for the
   *                    specified {@code project}. This parameter can be {@code null}. In this case parent window
   *                    will be suggested based on current focused window.
   * @param canBeParent specifies whether the dialog can be a parent for other windows. This parameter is used
   *                    by {@code WindowManager}.
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  protected DialogWrapper(@Nullable Project project, boolean canBeParent) {
    this(project, canBeParent, IdeModalityType.IDE);
  }

  protected DialogWrapper(@Nullable Project project, boolean canBeParent, @NotNull IdeModalityType ideModalityType) {
    this(project, null, canBeParent, ideModalityType);
  }

  protected DialogWrapper(@Nullable Project project,
                          @Nullable Component parentComponent,
                          boolean canBeParent,
                          @NotNull IdeModalityType ideModalityType) {
    this(project, parentComponent, canBeParent, ideModalityType, true);
  }

  protected DialogWrapper(@Nullable Project project,
                          @Nullable Component parentComponent,
                          boolean canBeParent,
                          @NotNull IdeModalityType ideModalityType,
                          boolean createSouth) {
    myPeer = parentComponent == null ? createPeer(project, canBeParent, project == null ? IdeModalityType.IDE : ideModalityType)
                                     : createPeer(parentComponent, canBeParent);
    myCreateSouthSection = createSouth;
    initResizeListener();
    createDefaultActions();
  }

  protected final void initResizeListener() {
    Window window = myPeer.getWindow();
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
  }

  /**
   * Creates modal {@code DialogWrapper} that can be a parent for other windows.
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
   * @param canBeParent specifies whether the dialog can be a parent for other windows. This parameter is used
   *                    by {@code WindowManager}.
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  protected DialogWrapper(boolean canBeParent) {
    this((Project)null, canBeParent);
  }

  /**
   * Typically, we should set a parent explicitly. Use {@link WindowManager#suggestParentWindow}
   * method to find out the best parent for your dialog. Exceptions are cases
   * when we do not have a project to figure out which window
   * is more suitable as an owner for the dialog.
   * <p/>
   * @deprecated use {@link DialogWrapper#DialogWrapper(Project, boolean, boolean)}
   */
  @Deprecated
  protected DialogWrapper(boolean canBeParent, boolean applicationModalIfPossible) {
    this(null, canBeParent, applicationModalIfPossible);
  }

  protected DialogWrapper(Project project, boolean canBeParent, boolean applicationModalIfPossible) {
    ensureEventDispatchThread();
    Window owner = null;
    if (ApplicationManager.getApplication() != null) {
      owner = project != null ? WindowManager.getInstance().suggestParentWindow(project) : WindowManager.getInstance().findVisibleFrame();
    }
    myPeer = createPeer(owner, canBeParent, applicationModalIfPossible);
    myCreateSouthSection = true;
    createDefaultActions();
  }

  /**
   * @param parent      parent component which is used to calculate heavyweight window ancestor.
   *                    {@code parent} cannot be {@code null} and must be showing.
   * @param canBeParent can be a parent
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  protected DialogWrapper(@NotNull Component parent, boolean canBeParent) {
    ensureEventDispatchThread();
    myCreateSouthSection = true;
    myPeer = createPeer(parent, canBeParent);
    createDefaultActions();
  }

  protected DialogWrapper(@NotNull PeerFactory peerFactory) {
    myPeer = peerFactory.createPeer(this);
    myCreateSouthSection = false;
    createDefaultActions();
  }

  public interface PeerFactory {
    @NotNull DialogWrapperPeer createPeer(@NotNull DialogWrapper dialogWrapper);
  }

  protected @NotNull @NlsContexts.Checkbox String getDoNotShowMessage() {
    return UIBundle.message("dialog.options.do.not.show");
  }

  public void setDoNotAskOption(@Nullable com.intellij.openapi.ui.DoNotAskOption doNotAsk) {
    myDoNotAsk = doNotAsk;
  }

  /**
   * @deprecated Please use setDoNotAskOption(com.intellij.openapi.ui.DoNotAskOption) instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  public void setDoNotAskOption(@Nullable DoNotAskOption doNotAsk) {
    myDoNotAsk = doNotAsk;
  }

  protected @NotNull Alarm.ThreadToUse getValidationThreadToUse() {
    return Alarm.ThreadToUse.SWING_THREAD;
  }

  /**
   * Allows postponing first start of validation
   *
   * @return {@code false} if start validation in {@code init()} method
   */
  protected boolean postponeValidation() {
    return true;
  }

  /**
   * Allow disabling continuous validation.
   * When disabled {@link #initValidation()} needs to be invoked after every change of the dialog to validate.
   *
   * @return {@code false} to disable continuous validation
   */
  protected boolean continuousValidation() {
    return true;
  }

  /**
   * Validates user input and returns {@code null} if everything is fine
   * or validation description with component where problem has been found.
   *
   * @return {@code null} if everything is OK or validation descriptor
   *
   * @see <a href="https://jetbrains.design/intellij/principles/validation_errors/">Validation errors guidelines</a>
   */
  protected @Nullable ValidationInfo doValidate() {
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
   *
   * @see <a href="https://jetbrains.design/intellij/principles/validation_errors/">Validation errors guidelines</a>
   */
  protected @NotNull List<ValidationInfo> doValidateAll() {
    List<ValidationInfo> result = new ArrayList<>();
    ValidationInfo vi = doValidate();
    if (vi != null) {
      result.add(vi);
    }
    for (Function0<ValidationInfo> callback : getValidateCallbacks()) {
      ValidationInfo callbackInfo = callback.invoke();
      if (callbackInfo != null) {
        result.add(callbackInfo);
      }
    }
    return result;
  }

  public void setValidationDelay(int delay) {
    myValidationDelay = delay;
  }

  protected void updateErrorInfo(@NotNull List<ValidationInfo> info) {
    if (!myInfo.equals(info)) {
      SwingUtilities.invokeLater(() -> {
        if (myDisposed) return;
        setErrorInfoAll(info);
        getOKAction().setEnabled(ContainerUtil.all(info, info1 -> info1.okEnabled));
      });
    }
  }

  protected void createDefaultActions() {
    myOKAction = new OkAction();
    myCancelAction = new CancelAction();
    myHelpAction = new HelpAction(this::doHelpAction);
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
   * Creates border for dialog's content pane. By default, content
   * pane has an empty border with {@code (8,12,8,12)} insets. Subclasses can
   * return {@code null} for no border.
   *
   * @return content pane border
   */
  protected @Nullable Border createContentPaneBorder() {
    if (getStyle() == DialogStyle.COMPACT) {
      if (/*(SystemInfoRt.isMac && Registry.is("ide.mac.transparentTitleBarAppearance", true)) ||*/
          (SystemInfoRt.isWindows && SystemInfo.isJetBrainsJvm)) {
        return JBUI.Borders.customLineTop(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
      }
      return JBUI.Borders.empty();
    }
    return createDefaultBorder();
  }

  protected static boolean isMoveHelpButtonLeft() {
    return true;
  }

  private static boolean isRemoveHelpButton() {
    return !ApplicationInfo.contextHelpAvailable() ||
           Registry.is("ide.remove.help.button.from.dialogs", false);
  }

  /**
   * Creates panel located at the south of the content pane. By default, that
   * panel contains dialog's buttons. This default implementation uses {@code createActions()}
   * and {@code createJButtonForAction(Action)} methods to construct the panel.
   *
   * @return south panel
   */
  protected JComponent createSouthPanel() {
    List<Action> actions = ContainerUtil.filter(createActions(), Conditions.notNull());
    List<Action> leftSideActions = ContainerUtil.filter(createLeftSideActions(), Conditions.notNull());

    Action helpAction = getHelpAction();
    boolean addHelpToLeftSide = false;
    if (isRemoveHelpButton()) {
      actions.remove(helpAction);
    }
    else if (!actions.contains(helpAction) && getHelpId() == null) {
      helpAction.setEnabled(false);
    }
    else if (isMoveHelpButtonLeft() && actions.remove(helpAction) && !leftSideActions.contains(helpAction)) {
      addHelpToLeftSide = true;
    }

    if (SystemInfoRt.isMac) {
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

      // move cancel action to the left of OK action, if present, and to the leftmost position otherwise
      int cancelNdx = actions.indexOf(getCancelAction());
      if (cancelNdx > 0) {
        actions.remove(getCancelAction());
        actions.add(okNdx < 0 ? 0 : actions.size() - 1, getCancelAction());
      }
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

    JComponent result = createSouthPanel(leftSideButtons, rightSideButtons, addHelpToLeftSide);
    if (ApplicationManager.getApplication() != null) {
      Touchbar.setButtonActions(result, leftSideButtons, rightSideButtons, null);
    }
    return result;
  }

  protected @NotNull JButton createHelpButton(@NotNull Insets insets) {
    JButton helpButton = createHelpButton(getHelpAction());
    setHelpTooltip(helpButton);
    helpButton.setMargin(insets);
    return helpButton;
  }

  public static @NotNull JButton createHelpButton(@NotNull Action action) {
    JButton helpButton = new JButton(action);
    helpButton.putClientProperty("JButton.buttonType", "help");
    helpButton.setText("");
    helpButton.addPropertyChangeListener("ancestor", evt -> {
      if (evt.getNewValue() == null) {
        HelpTooltip.dispose((JComponent)evt.getSource());
      }
    });
    helpButton.getAccessibleContext().setAccessibleName(UIBundle.message("dialog.options.help.button.accessible.name"));
    helpButton.getAccessibleContext().setAccessibleDescription(ActionsBundle.message("action.HelpTopics.description"));
    return helpButton;
  }

  protected void setHelpTooltip(@NotNull JButton helpButton) {
    helpButton.setToolTipText(ActionsBundle.actionDescription("HelpTopics"));
  }

  private static @NotNull List<Action> flattenOptionsActions(@NotNull List<? extends Action> actions) {
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

  protected @NotNull DialogStyle getStyle() {
    return DialogStyle.NO_STYLE;
  }

  protected boolean toBeShown() {
    return !myCheckBoxDoNotShowDialog.isSelected();
  }

  private @NotNull List<JButton> createButtons(@NotNull List<? extends Action> actions) {
    List<JButton> buttons = new ArrayList<>();
    for (Action action : actions) {
      buttons.add(createJButtonForAction(action));
    }
    return buttons;
  }

  private @NotNull JPanel createSouthPanel(@NotNull List<? extends JButton> leftSideButtons,
                                           @NotNull List<? extends JButton> rightSideButtons,
                                           boolean addHelpToLeftSide) {
    JPanel panel = new SouthPanel(getStyle());

    if (myDoNotAsk != null) {
      myCheckBoxDoNotShowDialog = new JCheckBox(myDoNotAsk.getDoNotShowMessage());
      myCheckBoxDoNotShowDialog.setVisible(myDoNotAsk.canBeHidden());
      myCheckBoxDoNotShowDialog.setSelected(!myDoNotAsk.isToBeShown());
      DialogUtil.registerMnemonic(myCheckBoxDoNotShowDialog, '&');
    }
    JComponent doNotAskCheckbox = createDoNotAskCheckbox();

    JPanel lrButtonsPanel = new NonOpaquePanel(new GridBagLayout());
    Insets insets = JBInsets.emptyInsets();

    if (!rightSideButtons.isEmpty() || !leftSideButtons.isEmpty()) {
      GridBag bag = new GridBag().setDefaultInsets(insets);

      if (!leftSideButtons.isEmpty()) {
        JPanel buttonsPanel = createButtonsPanel(leftSideButtons);
        if (!rightSideButtons.isEmpty()) {
          buttonsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));  // leave some space between button groups
        }
        lrButtonsPanel.add(buttonsPanel, bag.next());
      }
      lrButtonsPanel.add(Box.createHorizontalGlue(), bag.next().weightx(1).fillCellHorizontally());   // left strut
      if (!rightSideButtons.isEmpty()) {
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
    if (addHelpToLeftSide) {
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
      Color color = UIManager.getColor("DialogWrapper.southPanelDivider");
      Border line = new CustomLineBorder(color != null ? color : OnePixelDivider.BACKGROUND, 1, 0, 0, 0);
      panel.setBorder(new CompoundBorder(line, JBUI.Borders.empty(8, 12)));
    }
    else {
      panel.setBorder(JBUI.Borders.emptyTop(8));
    }

    return panel;
  }

  protected @Nullable JComponent createDoNotAskCheckbox() {
    return myCheckBoxDoNotShowDialog != null && myCheckBoxDoNotShowDialog.isVisible() ? myCheckBoxDoNotShowDialog : null;
  }

  private static final JBValue BASE_BUTTON_GAP = new JBValue.Float(UIUtil.isUnderWin10LookAndFeel() ? 8 : 12);

  protected @NotNull JPanel createButtonsPanel(@NotNull List<? extends JButton> buttons) {
    return layoutButtonsPanel(buttons);
  }

  public static @NotNull JPanel layoutButtonsPanel(@NotNull List<? extends JButton> buttons) {
    JPanel buttonsPanel = new NonOpaquePanel();
    buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));

    for (int i = 0; i < buttons.size(); i++) {
      JComponent button = buttons.get(i);
      Insets insets = button.getInsets();

      buttonsPanel.add(button);
      if (i < buttons.size() - 1) {
        int gap = StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF() ? BASE_BUTTON_GAP.get() - insets.left - insets.right : JBUIScale
          .scale(8);
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
  protected @Nullable JPanel createSouthAdditionalPanel() {
    return null;
  }

  /**
   *
   * @param action should be registered to find corresponding JButton
   * @return button for specified action or null if it's not found
   */
  protected @Nullable JButton getButton(@NotNull Action action) {
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
    JButton button = createJButtonForAction(action, getRootPane());

    int mnemonic = button.getMnemonic();
    Object name = action.getValue(Action.NAME);
    if (mnemonic == KeyEvent.VK_Y && "Yes".equals(name)) {
      myYesAction = action;
    }
    else if (mnemonic == KeyEvent.VK_N && "No".equals(name)) {
      myNoAction = action;
    }

    if (action.getValue(FOCUSED_ACTION) != null) {
      myPreferredFocusedComponent = button;
    }

    return button;
  }

  public static @NotNull JButton createJButtonForAction(@NotNull Action action, @Nullable JRootPane rootPane) {
    JButton button;
    if (action instanceof OptionAction && UISettings.getShadowInstance().getAllowMergeButtons()) {
      button = createJOptionsButton((OptionAction)action);
    }
    else {
      button = new JButton(action);
    }

    if (SystemInfoRt.isMac) {
      button.putClientProperty("JButton.buttonType", "text");
    }

    Pair<Integer, @Nls String> pair = extractMnemonic(button.getText());
    button.setText(pair.second);
    int mnemonic = pair.first;

    Object value = action.getValue(Action.MNEMONIC_KEY);
    if (value instanceof Integer) {
      mnemonic = (Integer)value;
    }
    button.setMnemonic(mnemonic);

    if (action.getValue(DEFAULT_ACTION) != null) {
      if (rootPane != null) {
        rootPane.setDefaultButton(button);
      }
    }

    return button;
  }

  private static @NotNull JButton createJOptionsButton(@NotNull OptionAction action) {
    JBOptionButton optionButton = new JBOptionButton(action, action.getOptions());
    optionButton.setOptionTooltipText(getDefaultTooltip());
    return optionButton;
  }

  public static @NotNull Pair<Integer, @Nls String> extractMnemonic(@Nullable @Nls String text) {
    if (text == null) return pair(0, null);

    int mnemonic = 0;
    @Nls StringBuilder plainText = new StringBuilder();
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
          // Mnemonic is case-insensitive
          int vk = ch;
          if (vk >= 'a' && vk <= 'z') {
            vk -= 'a' - 'A';
          }
          mnemonic = vk;
        }
      }
      plainText.append(ch);
    }
    return pair(mnemonic, plainText.toString());
  }

  protected @NotNull DialogWrapperPeer createPeer(@NotNull Component parent, boolean canBeParent) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, parent, canBeParent);
  }

  protected @NotNull DialogWrapperPeer createPeer(Window owner, boolean canBeParent, IdeModalityType ideModalityType) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, owner, canBeParent, ideModalityType);
  }

  protected @NotNull DialogWrapperPeer createPeer(Window owner, boolean canBeParent, boolean applicationModalIfPossible) {
    return createPeer(owner, canBeParent, applicationModalIfPossible ? IdeModalityType.IDE : IdeModalityType.PROJECT);
  }

  protected @NotNull DialogWrapperPeer createPeer(@Nullable Project project, boolean canBeParent, @NotNull IdeModalityType ideModalityType) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, project, canBeParent, ideModalityType);
  }

  protected @NotNull DialogWrapperPeer createPeer(@Nullable Project project, boolean canBeParent) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, project, canBeParent);
  }

  protected @Nullable JComponent createTitlePane() {
    return null;
  }

  /**
   * Factory method. It creates the panel located at the
   * north of the dialog's content pane. The implementation can return {@code null}
   * value. In this case there will be no input panel.
   *
   * @return north panel
   */
  protected @Nullable JComponent createNorthPanel() {
    return null;
  }

  /**
   * Factory method. It creates panel with dialog options. Options panel is located at the
   * center of the dialog's content pane. The implementation can return {@code null}
   * value. In this case there will be no options panel.
   */
  protected abstract @Nullable JComponent createCenterPanel();

  /** @see Window#toFront() */
  public void toFront() {
    myPeer.toFront();
  }

  /** @see Window#toBack() */
  @SuppressWarnings("unused")
  public void toBack() {
    myPeer.toBack();
  }

  @SuppressWarnings("UnusedReturnValue")
  protected boolean setAutoAdjustable(boolean autoAdjustable) {
    JRootPane rootPane = getRootPane();
    if (rootPane == null) return false;
    rootPane.putClientProperty(NO_AUTO_RESIZE, autoAdjustable ? null : Boolean.TRUE);
    return true;
  }

  /* true by default */
  public boolean isAutoAdjustable() {
    JRootPane rootPane = getRootPane();
    return rootPane == null || rootPane.getClientProperty(NO_AUTO_RESIZE) == null;
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
    myValidationAlarm.cancelAllRequests();
    myDisposed = true;

    for (JButton button : myButtonMap.values()) {
      button.setAction(null); // avoid memory leak via KeyboardManager
    }
    myButtonMap.clear();

    JRootPane rootPane = getRootPane();
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
    clearOwnFields(rootPane, field -> {
      String clazz = field.getDeclaringClass().getName();
      // keep AWT and Swing fields intact, except some
      if (!clazz.startsWith("java.") && !clazz.startsWith("javax.")) return true;
      String name = field.getName();
      return "clientProperties".equals(name);
    });
  }

  public static void unregisterKeyboardActions(@Nullable Component rootPane) {
    int[] flags = {JComponent.WHEN_FOCUSED, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, WHEN_IN_FOCUSED_WINDOW};
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
   * Calls doOKAction. This is intended for internal use.
   */
  @ApiStatus.Internal
  public final void performOKAction() {
    doOKAction();
  }

  @ApiStatus.Internal
  public final @NotNull List<ValidationInfo> performValidateAll() {
    return doValidateAll();
  }

  /**
   * This method is invoked by default implementation of "OK" action. It just closes dialog
   * with {@code OK_EXIT_CODE}. This is convenient place to override functionality of "OK" action.
   * Note that the method does nothing if "OK" action isn't enabled.
   */
  protected void doOKAction() {
    if (getOKAction().isEnabled()) {
      if (myDialogPanel != null) {
        myDialogPanel.apply();
      }
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
   * By default, "OK" and "Cancel" actions are returned. The "Help" action is automatically added if
   * {@link #getHelpId()} returns non-null value.
   * <p/>
   * Each action is represented by {@code JButton} created by {@link #createJButtonForAction(Action)}.
   * These buttons are then placed into {@link #createSouthPanel() south panel} of dialog.
   *
   * @return dialog actions
   * @see #createSouthPanel
   * @see #createJButtonForAction
   */
  protected Action @NotNull [] createActions() {
    Action helpAction = getHelpAction();
    return helpAction == myHelpAction && getHelpId() == null ?
           new Action[]{getOKAction(), getCancelAction()} :
           new Action[]{getOKAction(), getCancelAction(), helpAction};
  }

  protected Action @NotNull [] createLeftSideActions() {
    return new Action[0];
  }

  /**
   * @return default implementation of "OK" action. This action just invokes
   * {@code doOKAction()} method.
   * @see #doOKAction
   */
  protected @NotNull Action getOKAction() {
    return myOKAction;
  }

  /**
   * @return default implementation of "Cancel" action. This action just invokes
   * {@code doCancelAction()} method.
   * @see #doCancelAction
   */
  protected @NotNull Action getCancelAction() {
    return myCancelAction;
  }

  /**
   * @return default implementation of "Help" action. This action just invokes
   * {@code doHelpAction()} method.
   * @see #doHelpAction
   */
  protected @NotNull Action getHelpAction() {
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
  protected @NonNls @Nullable String getDimensionServiceKey() {
    return null;
  }

  public final @NonNls @Nullable String getDimensionKey() {
    return getDimensionServiceKey();
  }

  /**
   * @see #OK_EXIT_CODE
   * @see #CANCEL_EXIT_CODE
   * @see #CLOSE_EXIT_CODE
   */
  public int getExitCode() {
    return myExitCode;
  }

  /**
   * @return component which should be focused when the dialog appears
   * on the screen.
   */
  public @Nullable JComponent getPreferredFocusedComponent() {
    if (myPreferredFocusedComponentFromPanel != null) {
      return myPreferredFocusedComponentFromPanel;
    }
    return SystemInfoRt.isMac ? myPreferredFocusedComponent : null;
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
  public @NlsContexts.DialogTitle String getTitle() {
    return myPeer.getTitle();
  }

  protected void init() {
    ensureEventDispatchThread();
    myErrorText = new ErrorText(createContentPaneBorder(), getErrorTextAlignment());
    myErrorText.setVisible(false);
    ComponentAdapter resizeListener = new ComponentAdapter() {
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
    Disposer.register(myDisposable, () -> myErrorText.myLabel.removeComponentListener(resizeListener));

    myRoot.setLayout(createRootLayout());
    myPeer.setContentPane(myRoot);

    AnAction toggleShowOptions = DumbAwareAction.create(e -> expandNextOptionButton());
    toggleShowOptions.registerCustomShortcutSet(getDefaultShowPopupShortcut(), myRoot, myDisposable);

    JComponent titlePane = createTitlePane();
    if (titlePane != null) {
      JPanel northSection = new JPanel(new BorderLayout());
      myRoot.add(northSection, BorderLayout.NORTH);

      northSection.add(titlePane, BorderLayout.CENTER);
    }

    JComponent centerSection = new JPanel(new BorderLayout());
    myRoot.add(centerSection, BorderLayout.CENTER);

    JComponent n = createNorthPanel();
    if (n != null) {
      centerSection.add(n, BorderLayout.NORTH);
    }

    centerPanel = createCenterPanel();
    if (centerPanel != null) {
      centerPanel.putClientProperty(DIALOG_CONTENT_PANEL_PROPERTY, true);
      centerSection.add(centerPanel, BorderLayout.CENTER);
      if (centerPanel instanceof DialogPanel) {
        DialogPanel dialogPanel = (DialogPanel)centerPanel;
        myPreferredFocusedComponentFromPanel = dialogPanel.getPreferredFocusedComponent();
        dialogPanel.registerValidators(myDisposable, map -> {
          setOKActionEnabled(map.isEmpty());
          return Unit.INSTANCE;
        });
        myDialogPanel = dialogPanel;
      }
    }

    boolean isVisualPaddingCompensatedOnComponentLevel =
      centerPanel == null || centerPanel.getClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY) == null;
    if (isVisualPaddingCompensatedOnComponentLevel) {
      // see comment about visual paddings in the MigLayoutBuilder.build
      myRoot.setBorder(createContentPaneBorder());
    }

    if (myCreateSouthSection) {
      JPanel southSection = new JPanel(new BorderLayout());
      if (!isVisualPaddingCompensatedOnComponentLevel) {
        southSection.setBorder(JBUI.Borders.empty(0, 12, 8, 12));
      }
      myRoot.add(southSection, BorderLayout.SOUTH);

      southSection.add(myErrorText, BorderLayout.CENTER);
      JComponent south = createSouthPanel();
      if (south != null) {
        southSection.add(south, BorderLayout.SOUTH);
      }
    }

    MnemonicHelper.init(myRoot);
    if (!postponeValidation()) {
      startTrackingValidation();
    }
    if (SystemInfoRt.isWindows || (SystemInfoRt.isLinux && Registry.is("ide.linux.enter.on.dialog.triggers.focused.button", true))) {
      installEnterHook(myRoot, myDisposable);
    }
  }

  protected int getErrorTextAlignment() {
    return SwingConstants.LEADING;
  }

  protected @NotNull LayoutManager createRootLayout() {
    return new BorderLayout();
  }

  private static void installEnterHook(JComponent root, Disposable disposable) {
    new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner instanceof JButton && owner.isEnabled()) {
          ((JButton)owner).doClick();
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
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
    if (!continuousValidation()) return;
    SwingUtilities.invokeLater(() -> {
      if (!myValidationStarted) {
        myValidationStarted = true;
        initValidation();
      }
    });
  }

  protected final void initValidation() {
    if (myDisposed) return;
    myValidationAlarm.cancelAllRequests();
    Runnable validateRequest = () -> {
      if (myDisposed) return;
      updateErrorInfo(doValidateAll());

      if (continuousValidation()) {
        initValidation();
      }
    };

    if (getValidationThreadToUse() == Alarm.ThreadToUse.SWING_THREAD) {
      // null if headless
      JRootPane rootPane = getRootPane();
      myValidationAlarm.addRequest(validateRequest, myValidationDelay,
                                   ApplicationManager.getApplication() == null ? null :
                                   rootPane != null ? ModalityState.stateForComponent(rootPane) :
                                   ModalityState.current());
    }
    else {
      myValidationAlarm.addRequest(validateRequest, myValidationDelay);
    }
  }

  protected @NotNull JComponent createContentPane() {
    return new JPanel();
  }

  /**
   * @see Window#pack
   */
  public void pack() {
    myPeer.pack();
  }

  /**
   * Override to set default initial size of the window
   *
   * @return initial window size
   */
  public @Nullable Dimension getInitialSize() {
    if (myUserSizeSet) return myUserBounds.getSize();
    return null;
  }

  public Dimension getPreferredSize() {
    return myPeer.getPreferredSize();
  }

  /** @deprecated Dialog action buttons should be right-aligned. */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  protected final void setButtonsAlignment(@MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.RIGHT}) int alignment) {
    if (SwingConstants.CENTER != alignment && SwingConstants.RIGHT != alignment) {
      throw new IllegalArgumentException("unknown alignment: " + alignment);
    }
    myButtonAlignment = alignment;
  }

  public final void setCrossClosesWindow(boolean crossClosesWindow) {
    myCrossClosesWindow = crossClosesWindow;
  }

  protected final void setCancelButtonText(@NlsContexts.Button @NotNull String text) {
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
    myOKAction.putValue(Action.SMALL_ICON, icon);
  }

  /**
   * @param text action without mnemonic. If mnemonic is set, presentation would be shifted by one to the left
   *             {@link AbstractButton#setText(String)}
   *             {@link AbstractButton#updateDisplayedMnemonicIndex(String, int)}
   */
  protected final void setOKButtonText(@NlsContexts.Button @NotNull String text) {
    myOKAction.putValue(Action.NAME, text);
  }

  protected final void setOKButtonMnemonic(int c) {
    myOKAction.putValue(Action.MNEMONIC_KEY, c);
  }

  protected final void setOKButtonTooltip(@NlsContexts.Tooltip String text) {
    myOKAction.putValue(Action.SHORT_DESCRIPTION, text);
  }

  /** Returns the help identifier, or {@code null} if no help is available. */
  protected @NonNls @Nullable String getHelpId() {
    return null;
  }

  protected void doHelpAction() {
    if (myHelpAction.isEnabled()) {
      logClickOnHelpDialogEvent();
      String helpId = getHelpId();
      if (helpId != null) {
        HelpManager.getInstance().invokeHelp(helpId);
      }
      else {
        LOG.error("null topic; dialog=" + getClass() + "; action=" + getHelpAction().getClass());
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
    myUserBounds.setSize(width, height);
    myUserSizeSet = true;
    myPeer.setSize(width, height);
  }

  /**
   * @param title title
   * @see JDialog#setTitle
   */
  public void setTitle(@NlsContexts.DialogTitle String title) {
    myPeer.setTitle(title);
  }

  /**
   * @see JDialog#isResizable
   */
  public boolean isResizable() {
    return myPeer.isResizable();
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
  public @NotNull Point getLocation() {
    return myPeer.getLocation();
  }

  /**
   * @param p new dialog location
   * @see JDialog#setLocation(Point)
   */
  public void setLocation(@NotNull Point p) {
    myUserBounds.setLocation(p);
    myUserLocationSet = true;
    myPeer.setLocation(p);
  }

  /**
   * @param x x
   * @param y y
   * @see JDialog#setLocation(int, int)
   */
  public void setLocation(int x, int y) {
    myUserBounds.setLocation(x, y);
    myUserLocationSet = true;
    myPeer.setLocation(x, y);
  }

  /**
   * Called to fit window bounds of dialog to a screen
   * @param rect the suggested window bounds. This rect should be modified to change resulting bounds.
   */
  @ApiStatus.Internal
  public void fitToScreen(Rectangle rect) {
    ScreenUtil.fitToScreen(rect);
  }

  @SuppressWarnings("unused")
  public void centerRelativeToParent() {
    myPeer.centerInParent();
  }

  /**
   * Show the dialog.
   *
   * @throws IllegalStateException if the method is invoked not on the event dispatch thread
   * @see #showAndGet()
   */
  public void show() {
    logShowDialogEvent();
    doShow();
  }

  /**
   * Show the modal dialog and check if it was closed with OK.
   *
   * @return true if the {@link #getExitCode() exit code} is {@link #OK_EXIT_CODE}.
   * @throws IllegalStateException if the dialog is non-modal, or if the method is invoked not on the EDT.
   * @see #show()
   */
  public boolean showAndGet() {
    if (!isModal()) {
      throw new IllegalStateException("The showAndGet() method is for modal dialogs only");
    }
    show();
    return isOK();
  }

  /** @deprecated override {@link #doOKAction()} / {@link #doCancelAction()} or hook on {@link #myDisposable} */
  @Deprecated
  public @NotNull AsyncResult<Boolean> showAndGetOk() {
    if (isModal()) {
      throw new IllegalStateException("The showAndGetOk() method is for modeless dialogs only");
    }

    AsyncResult<Boolean> result = new AsyncResult<>();
    Disposer.register(myDisposable, () -> result.setDone(isOK()));
    doShow();
    return result;
  }

  private void doShow() {
    if (UiInterceptors.tryIntercept(this)) return;

    ensureEventDispatchThread();
    registerKeyboardShortcuts();

    Disposable uiParent = ApplicationManager.getApplication();
    if (uiParent != null) { // may be null if no app yet (license agreement)
      Disposer.register(uiParent, myDisposable); // ensure everything is disposed on app quit
    }

    myPeer.show();
  }

  /**
   * @return Location in absolute coordinates which is used when dialog has no dimension service key or no position was stored yet.
   * Can return null. In that case dialog will be centered relative to its owner.
   */
  public @Nullable Point getInitialLocation() {
    return myInitialLocationCallback != null
           ? myInitialLocationCallback.compute()
           : myUserLocationSet
             ? myUserBounds.getLocation()
             : null;
  }

  public void setInitialLocationCallback(@NotNull Computable<? extends Point> callback) {
    myInitialLocationCallback = callback;
  }

  private void registerKeyboardShortcuts() {
    JRootPane rootPane = getRootPane();
    if (rootPane == null) return;

    ActionListener cancelKeyboardAction = createCancelAction();
    if (cancelKeyboardAction != null) {
      rootPane.registerKeyboardAction(cancelKeyboardAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW);
      ActionUtil.registerForEveryKeyboardShortcut(getRootPane(), cancelKeyboardAction, CommonShortcuts.getCloseActiveWindow());
    }

    if (!(isRemoveHelpButton() || isProgressDialog())) {
      ActionListener helpAction = e -> doHelpAction();
      ActionUtil.registerForEveryKeyboardShortcut(getRootPane(), helpAction, CommonShortcuts.getContextHelp());
      rootPane.registerKeyboardAction(helpAction, KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0), WHEN_IN_FOCUSED_WINDOW);
    }

    rootPane.registerKeyboardAction(e -> focusButton(false), KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    rootPane.registerKeyboardAction(e -> focusButton(true), KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    if (myYesAction != null) {
      rootPane.registerKeyboardAction(myYesAction, KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0), WHEN_IN_FOCUSED_WINDOW);
    }

    if (myNoAction != null) {
      rootPane.registerKeyboardAction(myNoAction, KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), WHEN_IN_FOCUSED_WINDOW);
    }
  }

  /**
   * Return {@code null} if we should ignore <Esc> for window closing.
   */
  protected @Nullable ActionListener createCancelAction() {
    return e -> {
      if (!PopupUtil.handleEscKeyEvent()) {
        doCancelAction(e);
      }
    };
  }

  protected @NotNull Map<Action, JButton> getButtonMap() {
    return myButtonMap;
  }

  private void focusButton(boolean next) {
    List<JButton> buttons = new ArrayList<>(myButtonMap.values());
    int focusedIndex = ContainerUtil.indexOf(buttons, (Condition<? super Component>)Component::hasFocus);

    if (focusedIndex >= 0) {
      getEnabledIndexCyclic(buttons, focusedIndex, next).ifPresent(i -> buttons.get(i).requestFocus());
    }
  }

  private static @NotNull OptionalInt getEnabledIndexCyclic(@NotNull List<? extends Component> components, int currentIndex, boolean next) {
    assert -1 <= currentIndex && currentIndex <= components.size();
    int start = !next && currentIndex == -1 ? components.size() : currentIndex;

    return IntStream.range(0, components.size())
      .map(i -> (next ? start + i + 1 : start + components.size() - i - 1) % components.size())
      .filter(i -> components.get(i).isEnabled())
      .findFirst();
  }

  private void logCloseDialogEvent(int exitCode) {
    boolean canRecord = canRecordDialogId();
    if (canRecord) {
      String dialogId = getClass().getName();
      if (StringUtil.isNotEmpty(dialogId)) {
        FeatureUsageUiEventsKt.getUiEventLogger().logCloseDialog(dialogId, exitCode, getClass());
      }
    }
  }

  private void logShowDialogEvent() {
    boolean canRecord = canRecordDialogId();
    if (canRecord) {
      String dialogId = getClass().getName();
      if (StringUtil.isNotEmpty(dialogId)) {
        FeatureUsageUiEventsKt.getUiEventLogger().logShowDialog(dialogId, getClass());
      }
    }
  }

  private void logClickOnHelpDialogEvent() {
    if (!canRecordDialogId()) return;
    String dialogId = getClass().getName();
    if (StringUtil.isNotEmpty(dialogId)) {
      FeatureUsageUiEventsKt.getUiEventLogger().logClickOnHelpDialog(dialogId, getClass());
    }
  }

  /**
   * If dialog open/close events should be recorded in user event log, it can be used to understand how often this dialog is used.
   */
  protected boolean canRecordDialogId() {
    return true;
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
    protected DialogWrapperAction(@NotNull @NlsContexts.Button String name) {
      super(name);
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

  private final PropertyChangeListener myRepaintOnNameChangeListener = evt -> {
    if (Action.NAME.equals(evt.getPropertyName())) {
      repaint();
    }
  };

  protected class OkAction extends DialogWrapperAction {
    protected OkAction() {
      super(CommonBundle.getOkButtonText());
      addPropertyChangeListener(myRepaintOnNameChangeListener);
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    protected void doAction(ActionEvent e) {
      recordAction("DialogOkAction", EventQueue.getCurrentEvent());
      List<ValidationInfo> infoList = doValidateAll();
      if (!infoList.isEmpty()) {
        ValidationInfo info = infoList.get(0);
        if (info.component != null && info.component.isVisible()) {
          IdeFocusManager.getInstance(null).requestFocus(info.component, true);
        }

        updateErrorInfo(infoList);
        startTrackingValidation();
        if (ContainerUtil.exists(infoList, info1 -> !info1.okEnabled)) {
          return;
        }
      }
      doOKAction();
    }
  }

  private void recordAction(String name, AWTEvent event) {
    if (event instanceof KeyEvent && ApplicationManager.getApplication() != null) {
      //noinspection deprecation
      ActionsCollector.getInstance().record(name, (KeyEvent)event, getClass());
    }
  }

  protected final class CancelAction extends DialogWrapperAction {
    private CancelAction() {
      super(CommonBundle.getCancelButtonText());
      addPropertyChangeListener(myRepaintOnNameChangeListener);
    }

    @Override
    protected void doAction(ActionEvent e) {
      doCancelAction(e);
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
    public DialogWrapperExitAction(@NlsContexts.Button String name, int exitCode) {
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

  public static final class HelpAction extends AbstractAction {
    private final @NotNull Runnable myHelpActionPerformed;

    public HelpAction(@NotNull Runnable helpActionPerformed) {
      super(CommonBundle.getHelpButtonText());
      myHelpActionPerformed = helpActionPerformed;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myHelpActionPerformed.run();
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
  protected void setErrorText(@NlsContexts.DialogMessage @Nullable String text) {
    setErrorText(text, null);
  }

  protected void setErrorText(@NlsContexts.DialogMessage @Nullable String text, @Nullable JComponent component) {
    setErrorInfoAll(text == null ? List.of() : List.of(new ValidationInfo(text, component)));
  }

  protected final void setErrorInfoAll(@NotNull List<ValidationInfo> info) {
    if (myInfo.equals(info)) return;

    updateErrorText(info);
    updateComponentErrors(info);

    myInfo = info;
  }

  private void updateComponentErrors(@NotNull List<ValidationInfo> info) {
    // clear current component errors
    myInfo.stream()
      .filter(vi -> !info.contains(vi))
      .filter(vi -> vi.component != null)
      .map(vi -> ComponentValidator.getInstance(vi.component))
      .forEach(c -> c.ifPresent(vi -> vi.updateInfo(null)));

    // show current errors
    for (ValidationInfo vi : info) {
      JComponent component = vi.component;
      if (component == null) continue;

      ComponentValidator validator = ComponentValidator.getInstance(component)
        .orElseGet(() -> new ComponentValidator(getDisposable()).installOn(component));
      validator.updateInfo(vi);
    }
  }

  private void updateErrorText(@NotNull List<ValidationInfo> infos) {
    //init was not called - there's nothing to update
    ErrorText errorText = myErrorText;
    if (errorText == null) return;

    Runnable updateRunnable = () -> {
      if (!myDisposed) doUpdateErrorText(errorText, infos);
    };

    Application application = ApplicationManager.getApplication();
    boolean headless = application != null && application.isHeadlessEnvironment();
    if (headless) {
      updateRunnable.run();
    }
    else {
      SwingUtilities.invokeLater(updateRunnable);
    }
  }

  private void doUpdateErrorText(@NotNull ErrorText errorText, @NotNull List<ValidationInfo> infos) {
    HtmlBuilder htmlBuilder = new HtmlBuilder();
    for (ValidationInfo info : infos) {
      if (info.component != null || StringUtil.isEmptyOrSpaces(info.message)) continue;

      Color color = info.warning ? MessageType.WARNING.getTitleForeground() : UIUtil.getErrorForeground();
      htmlBuilder
        .append(
          HtmlChunk.raw(info.message)
            .wrapWith("left")
            .wrapWith(HtmlChunk.font(ColorUtil.toHex(color)))
        )
        .br();
    }

    // avoid updating size unnecessarily
    boolean needsSizeUpdate = true;
    if (htmlBuilder.isEmpty()) {
      needsSizeUpdate = errorText.clear();
    }
    else {
      errorText.setText(htmlBuilder.wrapWithHtmlBody().toString());
    }
    if (needsSizeUpdate) updateSize();
  }

  /**
   * Check if component is in error state validation-wise
   */
  protected boolean hasErrors(@NotNull JComponent component) {
    return ContainerUtil.exists(myInfo, i -> component.equals(i.component) && !i.warning);
  }

  private void updateSize() {
    if (myActualSize == null && !myErrorText.isVisible()) {
      myActualSize = getSize();
    }
  }

  public static @Nullable DialogWrapper findInstance(Component c) {
    while (c != null) {
      if (c instanceof DialogWrapperDialog) {
        return ((DialogWrapperDialog)c).getDialogWrapper();
      }
      c = c.getParent();
    }
    return null;
  }

  public static @Nullable DialogWrapper findInstanceFromFocus() {
    return findInstance(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
  }

  private static final class ErrorText extends JPanel {
    private final JLabel myLabel = new JLabel();

    private ErrorText(@Nullable Border contentBorder, int horizontalAlignment) {
      setLayout(new BorderLayout());
      myLabel.setBorder(createErrorTextBorder(contentBorder));
      myLabel.setHorizontalAlignment(horizontalAlignment);
      JBScrollPane pane =
        new JBScrollPane(myLabel, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setBorder(JBUI.Borders.empty());
      pane.setBackground(null);
      pane.getViewport().setBackground(null);
      pane.setOpaque(false);
      add(pane, BorderLayout.CENTER);
    }

    public void setText(@NotNull @Nls String errorText) {
      myLabel.setText(errorText);
      setVisible(true);
    }

    public boolean clear() {
      boolean isCurrentlyVisible = isVisible();
      myLabel.setBounds(0, 0, 0, 0);
      myLabel.setText("");
      setVisible(false);
      return isCurrentlyVisible;
    }

    private static @NotNull Border createErrorTextBorder(@Nullable Border contentBorder) {
      Insets contentInsets = contentBorder != null ? contentBorder.getBorderInsets(null) : JBInsets.emptyInsets();
      Insets baseInsets = JBInsets.create(16, 13);

      //noinspection UseDPIAwareBorders: Insets are already scaled, so use raw version.
      return new EmptyBorder(baseInsets.top,
                             baseInsets.left > contentInsets.left ? baseInsets.left - contentInsets.left : 0,
                             baseInsets.bottom > contentInsets.bottom ? baseInsets.bottom - contentInsets.bottom : 0,
                             baseInsets.right > contentInsets.right ? baseInsets.right - contentInsets.right : 0);
    }
  }

  public final @NotNull DialogWrapperPeer getPeer() {
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

  public final @NotNull Disposable getDisposable() {
    return myDisposable;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public void disposeIfNeeded() {
    if (isDisposed()) return;
    Disposer.dispose(getDisposable());
  }

  private static void clearOwnFields(@Nullable Object object, @NotNull Condition<? super Field> selectCondition) {
    if (object == null) return;
    for (Field each : ReflectionUtil.collectFields(object.getClass())) {
      if ((each.getModifiers() & (Modifier.FINAL | Modifier.STATIC)) > 0) continue;
      if (!selectCondition.value(each)) continue;
      try {
        ReflectionUtil.resetField(object, each);
      }
      catch (Exception ignore) { }
    }
  }

  private static class SouthPanel extends JPanel {
    private final DialogStyle myStyle;

    SouthPanel(@NotNull DialogStyle style) {
      super(new BorderLayout());
      myStyle = style;
    }

    @Override
    public Color getBackground() {
      Color bg = UIManager.getColor("DialogWrapper.southPanelBackground");
      if (myStyle == DialogStyle.COMPACT && bg != null) {
        return bg;
      }
      return super.getBackground();
    }
  }

  /**
   * @deprecated Please use com.intellij.openapi.ui.DoNotAskOption instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  public interface DoNotAskOption extends com.intellij.openapi.ui.DoNotAskOption {
    abstract class Adapter extends com.intellij.openapi.ui.DoNotAskOption.Adapter implements DoNotAskOption {}
  }

  private List<Function0<ValidationInfo>> getValidateCallbacks() {
    return centerPanel != null && centerPanel instanceof DialogPanel ?
           ((DialogPanel) centerPanel).getValidateCallbacks() : Collections.emptyList();
  }
}
