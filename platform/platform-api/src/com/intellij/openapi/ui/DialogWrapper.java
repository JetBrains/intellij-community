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
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.AwtVisitor;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The standard base class for modal dialog boxes. The dialog wrapper could be used only on event dispatch thread.
 * In case when the dialog must be created from other threads use
 * {@link EventQueue#invokeLater(Runnable)} or {@link EventQueue#invokeAndWait(Runnable)}.
 *
 * See also http://confluence.jetbrains.net/display/IDEADEV/IntelliJ+IDEA+DialogWrapper.
 */
@SuppressWarnings({"SSBasedInspection", "MethodMayBeStatic", "UnusedDeclaration"})
public abstract class DialogWrapper {
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
   * If you use your custom exit codes you have have to start them with
   * this constant.
   */
  public static final int NEXT_USER_EXIT_CODE = 2;

  /**
   * If your action returned by <code>createActions</code> method has non
   * <code>null</code> value for this key, then the button that corresponds to the action will be the
   * default button for the dialog. It's true if you don't change this behaviour
   * of <code>createJButtonForAction(Action)</code> method.
   */
  @NonNls public static final String DEFAULT_ACTION = "DefaultAction";

  @NonNls public static final String FOCUSED_ACTION = "FocusedAction";
  
  private static final KeyStroke SHOW_OPTION_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                                                                                InputEvent.ALT_MASK | InputEvent.SHIFT_MASK);

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
  private Insets myButtonMargins = new Insets(2, 16, 2, 16);

  protected Action myOKAction;
  protected Action myCancelAction;
  protected Action myHelpAction;
  private JButton[] myButtons;

  private boolean myClosed = false;

  protected boolean myPerformAction = false;

  private Action myYesAction = null;
  private Action myNoAction = null;

  protected JCheckBox myCheckBoxDoNotShowDialog;
  @Nullable
  private DoNotAskOption myDoNotAsk;

  private JComponent myPreferredFocusedComponent;
  private Computable<Point> myInitialLocationCallback;

  protected final Disposable myDisposable = new Disposable() {
    public String toString() {
      return DialogWrapper.this.toString();
    }

    public void dispose() {
      DialogWrapper.this.dispose();
    }
  };
  private List<JBOptionButton> myOptionsButtons = new ArrayList<JBOptionButton>();
  private int myCurrentOptionsButtonIndex = -1;
  private boolean myResizeInProgress = false;
  private ComponentAdapter myResizeListener;

  protected String getDoNotShowMessage() {
    return CommonBundle.message("dialog.options.do.not.show");
  }

  public void setDoNotAskOption(@Nullable DoNotAskOption doNotAsk) {
    myDoNotAsk = doNotAsk;
  }
  private ErrorText myErrorText;
  private int myMaxErrorTextLength;

  private final Alarm myErrorTextAlarm = new Alarm();

  /**
   * Creates modal <code>DialogWrapper</code>. The currently active window will be the dialog's parent.
   *
   * @param project     parent window for the dialog will be calculated based on focused window for the
   *                    specified <code>project</code>. This parameter can be <code>null</code>. In this case parent window
   *                    will be suggested based on current focused window.
   * @param canBeParent specifies whether the dialog can be parent for other windows. This parameter is used
   *                    by <code>WindowManager</code>.
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  protected DialogWrapper(@Nullable Project project, boolean canBeParent) {
    myPeer = createPeer(project, canBeParent);
    final Window window = myPeer.getWindow();
    if (window != null) {
      myResizeListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          if (!myResizeInProgress) {
            myActualSize = myPeer.getSize();
            if (myErrorText.isVisible()) {
              myActualSize.height -= myErrorText.getHeight() + 10;
            }
          }
        }
      };
      window.addComponentListener(myResizeListener);
    }
    createDefaultActions();
  }

  /**
   * Creates modal <code>DialogWrapper</code> that can be parent for other windows.
   * The currently active window will be the dialog's parent.
   *
   * @param project     parent window for the dialog will be calculated based on focused window for the
   *                    specified <code>project</code>. This parameter can be <code>null</code>. In this case parent window
   *                    will be suggested based on current focused window.
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   * @see com.intellij.openapi.ui.DialogWrapper#DialogWrapper(com.intellij.openapi.project.Project, boolean)
   */
  protected DialogWrapper(@Nullable Project project) {
    this(project, true);
  }

  /**
   * Creates modal <code>DialogWrapper</code>. The currently active window will be the dialog's parent.
   *
   * @param canBeParent specifies whether the dialog can be parent for other windows. This parameter is used
   *                    by <code>WindowManager</code>.
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  protected DialogWrapper(boolean canBeParent) {
    this((Project)null, canBeParent);
  }

  protected DialogWrapper(boolean canBeParent, boolean toolkitModalIfPossible) {
    ensureEventDispatchThread();
    myPeer = createPeer(canBeParent, toolkitModalIfPossible);
    createDefaultActions();
  }

  /**
   * @param parent parent component which is used to calculate heavy weight window ancestor.
   *               <code>parent</code> cannot be <code>null</code> and must be showing.
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

  protected Alarm.ThreadToUse getValidationThreadToUse() {
    return Alarm.ThreadToUse.SWING_THREAD;
  }

  private int myValidationDelay = 300;
  private boolean myDisposed = false;
  private boolean myValidationStarted = false;
  private final ErrorPainter myErrorPainter = new ErrorPainter();
  private JComponent myErrorPane;
  private boolean myErrorPainterInstalled = false;

  /**
   * Allows to postpone first start of validation
   *
   * @return <code>false</code> if start validation in <code>init()</code> method
   */
  protected boolean postponeValidation() {
    return true;
  }

  /**
   * Validates user input and returns <code>null</code> if everything is fine
   * or validation description with component where problem has been found.
   *
   * @return <code>null</code> if everything is OK or validation descriptor
   */
  @Nullable
  protected ValidationInfo doValidate() {
    return null;
  }

  public void setValidationDelay(int delay) {
    myValidationDelay = delay;
  }

  private void reportProblem(final ValidationInfo info) {
    installErrorPainter();

    myErrorPainter.setValidationInfo(info);
    if (! myErrorText.isTextSet(info.message)) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myDisposed) return;
        setErrorText(info.message);
        myPeer.getRootPane().getGlassPane().repaint();
        getOKAction().setEnabled(false);
      }
    });
  }
  }

  private void installErrorPainter() {
    if (myErrorPainterInstalled) return;
    myErrorPainterInstalled = true;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        IdeGlassPaneUtil.installPainter(myErrorPane, myErrorPainter, myDisposable);
      }
    });
  }

  private void clearProblems() {
    myErrorPainter.setValidationInfo(null);
    if (! myErrorText.isTextSet(null)) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myDisposed) return;
          setErrorText(null);
          myPeer.getRootPane().getGlassPane().repaint();
          getOKAction().setEnabled(true);
        }
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

  public final void addMouseListener(MouseListener listener) {
    myPeer.addMouseListener(listener);
  }

  public final void addMouseListener(MouseMotionListener listener) {
    myPeer.addMouseListener(listener);
  }

  public final void addKeyListener(KeyListener listener) {
    myPeer.addKeyListener(listener);
  }

  /**
   * Closes and disposes the dialog and sets the specified exit code.
   *
   * @param exitCode exit code
   * @param isOk is OK
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
    } else {
      processDoNotAskOnCancel();
    }

    Disposer.dispose(myDisposable);
  }

  public final void close(int exitCode) {
    close(exitCode, exitCode != CANCEL_EXIT_CODE);
  }

  /**
   * Creates border for dialog's content pane. By default content
   * pane has has empty border with <code>(8,12,8,12)</code> insets. Subclasses can
   * return <code>null</code> for no border.
   *
   * @return content pane border
   */
  @Nullable
  protected Border createContentPaneBorder() {
    return ourDefaultBorder;
  }

  /**
   * Creates panel located at the south of the content pane. By default that
   * panel contains dialog's buttons. This default implementation uses <code>createActions()</code>
   * and <code>createJButtonForAction(Action)</code> methods to construct the panel.
   *
   * @return south panel
   */
  @Nullable
  protected JComponent createSouthPanel() {
    Action[] actions = filter(createActions());
    Action[] leftSideActions = createLeftSideActions();
    List<JButton> buttons = new ArrayList<JButton>();

    boolean hasHelpToMoveToLeftSide = false;
    if (UIUtil.isUnderAquaLookAndFeel() && Arrays.asList(actions).contains(getHelpAction())) {
      hasHelpToMoveToLeftSide = true;
      actions = ArrayUtil.remove(actions, getHelpAction());
    }
    
    if (SystemInfo.isMac) {
      for (Action action : actions) {
        if (action instanceof MacOtherAction) {
          leftSideActions = ArrayUtil.append(leftSideActions, action);
          actions = ArrayUtil.remove(actions, action);
          break;
        }
      }
    }
    else if (UIUtil.isUnderGTKLookAndFeel() && Arrays.asList(actions).contains(getHelpAction())) {
      leftSideActions = ArrayUtil.append(leftSideActions, getHelpAction());
      actions = ArrayUtil.remove(actions, getHelpAction());
    }

    JPanel panel = new JPanel(new BorderLayout());
    final JPanel lrButtonsPanel = new JPanel(new GridBagLayout());
    final Insets insets = SystemInfo.isMacOSLeopard ? new Insets(0, 0, 0, 0) : new Insets(8, 0, 0, 0);

    if (actions.length > 0 || leftSideActions.length > 0) {
      int gridX = 0;
      if (leftSideActions.length > 0) {
        JPanel buttonsPanel = createButtons(leftSideActions, buttons);
        if (actions.length > 0) {
          buttonsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));  // leave some space between button groups
        }
        lrButtonsPanel.add(buttonsPanel,
                           new GridBagConstraints(gridX++, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, insets, 0, 0));

      }
      lrButtonsPanel.add(Box.createHorizontalGlue(),    // left strut
                         new GridBagConstraints(gridX++, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, insets, 0, 0));
      if (actions.length > 0) {
        if (SystemInfo.isMac) {
          // move ok action to the right
          int okNdx = ArrayUtil.indexOf(actions, getOKAction());
          if (okNdx >= 0 && okNdx != actions.length - 1) {
            actions = ArrayUtil.append(ArrayUtil.remove(actions, getOKAction()), getOKAction());
          }

          // move cancel action to the left
          int cancelNdx = ArrayUtil.indexOf(actions, getCancelAction());
          if (cancelNdx > 0) {
            actions = ArrayUtil.mergeArrays(new Action[] {getCancelAction()}, ArrayUtil.remove(actions, getCancelAction()));
          }

          /*if (!hasFocusedAction(actions)) {
            int ndx = ArrayUtil.find(actions, getCancelAction());
            if (ndx >= 0) {
              actions[ndx].putValue(FOCUSED_ACTION, Boolean.TRUE);
            }
          }*/
        }

        JPanel buttonsPanel = createButtons(actions, buttons);
        lrButtonsPanel.add(buttonsPanel,
                           new GridBagConstraints(gridX++, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, insets, 0, 0));
      }
      if (SwingConstants.CENTER == myButtonAlignment) {
        lrButtonsPanel.add(Box.createHorizontalGlue(),    // right strut
                           new GridBagConstraints(gridX, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, insets, 0, 0));
      }
      myButtons = buttons.toArray(new JButton[buttons.size()]);
    }

    if (hasHelpToMoveToLeftSide) {
      JButton helpButton = new JButton(getHelpAction());
      if (!UIUtil.isUnderDarcula()) {
        helpButton.putClientProperty("JButton.buttonType", "help");
        helpButton.setText("");
      }
      helpButton.setMargin(insets);
      helpButton.setToolTipText(ActionsBundle.actionDescription("HelpTopics"));
      panel.add(helpButton, BorderLayout.WEST);
    }


    panel.add(lrButtonsPanel, BorderLayout.CENTER);

    final DoNotAskOption askOption = myDoNotAsk;
    if (askOption != null) {
      myCheckBoxDoNotShowDialog = new JCheckBox(askOption.getDoNotShowMessage());
      JComponent southPanel = panel;

      if (!askOption.canBeHidden()) {
        return southPanel;
      }

      final JPanel withCB = addDoNotShowCheckBox(southPanel, myCheckBoxDoNotShowDialog);
      myCheckBoxDoNotShowDialog.setSelected(!askOption.isToBeShown());
      DialogUtil.registerMnemonic(myCheckBoxDoNotShowDialog, '&');

      panel = withCB;
    }

    panel.setBorder(IdeBorderFactory.createEmptyBorder(new Insets(8, 0, 0, 0)));

    return panel;
  }

  @NotNull
  private Action[] filter(@NotNull Action[] actions) {
    ArrayList<Action> answer = new ArrayList<Action>();
    for (Action action : actions) {
      if (action != null && (ApplicationInfo.contextHelpAvailable() || action != getHelpAction())) {
        answer.add(action);
      }
    }
    return answer.toArray(new Action[answer.size()]);
  }


  protected boolean toBeShown() {
    return !myCheckBoxDoNotShowDialog.isSelected();
  }

  public boolean isTypeAheadEnabled() {
    return false;
  }

  public static JPanel addDoNotShowCheckBox(JComponent southPanel, JCheckBox checkBox) {
    final JPanel panel = new JPanel(new BorderLayout());

    JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(checkBox);

    panel.add(wrapper, BorderLayout.WEST);
    panel.add(southPanel, BorderLayout.EAST);
    checkBox.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));

    return panel;
  }

  private boolean hasFocusedAction(Action[] actions) {
    for (Action action : actions) {
      if (action.getValue(FOCUSED_ACTION) != null && (Boolean)action.getValue(FOCUSED_ACTION)) {
        return true;
      }
    }
    
    return false;
  }

  private JPanel createButtons(Action[] actions, List<JButton> buttons) {
    if (!UISettings.getShadowInstance().ALLOW_MERGE_BUTTONS) {
      final List<Action> actionList = new ArrayList<Action>();
      for (Action action : actions) {
        actionList.add(action);
        if (action instanceof OptionAction) {
          final Action[] options = ((OptionAction)action).getOptions();
          actionList.addAll(Arrays.asList(options));
        }

      }
      if (actionList.size() != actions.length) {
        actions = actionList.toArray(actionList.toArray(new Action[actionList.size()]));
      }
    }
    
    JPanel buttonsPanel = new JPanel(new GridLayout(1, actions.length, SystemInfo.isMacOSLeopard ? 0 : 5, 0));
    for (final Action action : actions) {
      JButton button = createJButtonForAction(action);
      final Object value = action.getValue(Action.MNEMONIC_KEY);
      if (value instanceof Integer) {
        final int mnemonic = ((Integer)value).intValue();
        final Object name = action.getValue(Action.NAME);
        if (mnemonic == 'Y' && "Yes".equals(name)) {
          myYesAction = action;
        }
        else if (mnemonic == 'N' && "No".equals(name)) {
          myNoAction = action;
        }
        button.setMnemonic(mnemonic);
      }

      if (action.getValue(FOCUSED_ACTION) != null) {
        myPreferredFocusedComponent = button;
      }

      buttons.add(button);
      buttonsPanel.add(button);
    }
    return buttonsPanel;
  }

  /**
   * Creates <code>JButton</code> for the specified action. If the button has not <code>null</code>
   * value for <code>DialogWrapper.DEFAULT_ACTION</code> key then the created button will be the
   * default one for the dialog.
   *
   * @param action action for the button
   * @return button with action specified
   *
   * @see com.intellij.openapi.ui.DialogWrapper#DEFAULT_ACTION
   */
  protected JButton createJButtonForAction(Action action) {
    JButton button;
    if (action instanceof OptionAction && UISettings.getShadowInstance().ALLOW_MERGE_BUTTONS) {
      final Action[] options = ((OptionAction)action).getOptions();
      button = new JBOptionButton(action, options);
      final JBOptionButton eachOptionsButton = (JBOptionButton)button;
      eachOptionsButton.setOkToProcessDefaultMnemonics(false);
      eachOptionsButton.setOptionTooltipText(
        "Press " + KeymapUtil.getKeystrokeText(SHOW_OPTION_KEYSTROKE) + " to expand or use a mnemonic of a contained action");
      myOptionsButtons.add(eachOptionsButton);

      final Set<JBOptionButton.OptionInfo> infos = eachOptionsButton.getOptionInfos();
      for (final JBOptionButton.OptionInfo eachInfo : infos) {
        if (eachInfo.getMnemonic() >=0) {
          final CustomShortcutSet sc =
            new CustomShortcutSet(KeyStroke.getKeyStroke("alt pressed " + Character.valueOf((char)eachInfo.getMnemonic())));
          
          new AnAction() {
            @Override
            public void actionPerformed(AnActionEvent e) {
              final JBOptionButton buttonToActivate = eachInfo.getButton();
              buttonToActivate.showPopup(eachInfo.getAction(), true);
            }
          }.registerCustomShortcutSet(sc, getPeer().getRootPane());
        }
      }

    } else {
      button = new JButton(action);
    }

    String text = button.getText();

    if (SystemInfo.isMac) {
      button.putClientProperty("JButton.buttonType", "text");
    }

    if (text != null) {
      int mnemonic = 0;
      StringBuilder plainText = new StringBuilder();
      for (int i = 0; i < text.length(); i++) {
        char ch = text.charAt(i);
        if (ch == '_' || ch == '&') {
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
      button.setText(plainText.toString());
      final Object name = action.getValue(Action.NAME);
      if (mnemonic == KeyEvent.VK_Y && "Yes".equals(name)) {
        myYesAction = action;
      }
      else if (mnemonic == KeyEvent.VK_N && "No".equals(name)) {
        myNoAction = action;
      }

      button.setMnemonic(mnemonic);
    }
    setMargin(button);
    if (action.getValue(DEFAULT_ACTION) != null) {
      if (myPeer != null && !myPeer.isHeadless()) {
        getRootPane().setDefaultButton(button);
      }
    }
    return button;
  }

  private void setMargin(JButton button) {
    // Aqua LnF does a good job of setting proper margin between buttons. Setting them specifically causes them be 'square' style instead of
    // 'rounded', which is expected by apple users.
    if (!SystemInfo.isMac) {
      if (myButtonMargins == null) {
        return;
      }
      button.setMargin(myButtonMargins);
    }
  }

  protected DialogWrapperPeer createPeer(final Component parent, final boolean canBeParent) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, parent, canBeParent);
  }

  protected DialogWrapperPeer createPeer(boolean canBeParent, boolean toolkitModalIfPossible) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, canBeParent, toolkitModalIfPossible);
  }

  protected DialogWrapperPeer createPeer(@Nullable final Project project, final boolean canBeParent) {
    return DialogWrapperPeerFactory.getInstance().createPeer(this, project, canBeParent);
  }

  @Nullable
  protected JComponent createTitlePane() {
    return null;
  }

  /**
   * Factory method. It creates the panel located at the
   * north of the dialog's content pane. The implementation can return <code>null</code>
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
   * center of the dialog's content pane. The implementation can return <code>null</code>
   * value. In this case there will be no options panel.
   *
   * @return center panel
   */
  @Nullable
  protected abstract JComponent createCenterPanel();

  /**
   * @see java.awt.Window#toFront()
   */
  public void toFront() {
    myPeer.toFront();
  }

  /**
   * @see java.awt.Window#toBack()
   */
  public void toBack() {
    myPeer.toBack();
  }

  /**
   * Dispose the wrapped and releases all resources allocated be the wrapper to help
   * more effecient garbage collection. You should never invoke this method twice or
   * invoke any method of the wrapper after invocation of <code>dispose</code>.
   *
   * @throws IllegalStateException if the dialog is disposed not on the event dispatch thread
   */
  protected void dispose() {
    ensureEventDispatchThread();
    myErrorTextAlarm.cancelAllRequests();
    myValidationAlarm.cancelAllRequests();
    myDisposed = true;
    if (myButtons != null) {
      for (JButton button : myButtons) {
        button.setAction(null); // avoid memory leak via KeyboardManager
      }
    }

    final JRootPane rootPane = getRootPane();
    // if rootPane = null, dialog has already been disposed
    if (rootPane != null) {
      unregisterKeyboardActions(rootPane);
      if (myActualSize != null) {
        setSize(myActualSize.width, myActualSize.height);
      }
      myPeer.dispose();
    }
  }

  public static void unregisterKeyboardActions(final JRootPane rootPane) {
    new AwtVisitor(rootPane) {
      public boolean visit(final Component component) {
        if (component instanceof JComponent) {
          final JComponent eachComp = (JComponent)component;
          final ActionMap actionMap = eachComp.getActionMap();
          final KeyStroke[] strokes = eachComp.getRegisteredKeyStrokes();
          for (KeyStroke eachStroke : strokes) {
            boolean remove = true;
            if (actionMap != null) {
              for (int i = 0; i < 3; i++) {
                final InputMap inputMap = eachComp.getInputMap(i);
                final Object key = inputMap.get(eachStroke);
                if (key != null) {
                  final Action action = actionMap.get(key);
                  if (action instanceof UIResource) remove = false;
                }
              }
            }

            if (remove) eachComp.unregisterKeyboardAction(eachStroke);
          }
        }
        return false;
      }
    };
  }


  /**
   * This method is invoked by default implementation of "Cancel" action. It just closes dialog
   * with <code>CANCEL_EXIT_CODE</code>. This is convenient place to override functionality of "Cancel" action.
   * Note that the method does nothing if "Cancel" action isn't enabled.
   */
  public void doCancelAction() {
    processDoNotAskOnCancel();

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
   * the cancel action was triggered by some input event, <code>doCancelAction</code> is called otherwise.
   *
   * @param source AWT event
   * @see #doCancelAction
   */
  public void doCancelAction(AWTEvent source) {
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
   * with <code>OK_EXIT_CODE</code>. This is convenient place to override functionality of "OK" action.
   * Note that the method does nothing if "OK" action isn't enabled.
   */
  protected void doOKAction() {
    processDoNotAskOnOk(OK_EXIT_CODE);

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
   *         <code>true</code> means that cross performs hide or dispose of the dialog.
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
   * Each action is represented by <code>JButton</code> created by {@link #createJButtonForAction(javax.swing.Action)}.
   * These buttons are then placed into {@link #createSouthPanel() south panel} of dialog.
   *
   * @return dialog actions
   *
   * @see #createSouthPanel
   * @see #createJButtonForAction
   */
  @NotNull
  protected Action[] createActions() {
    if (getHelpId() == null) {
      if (SystemInfo.isMac) {
        return new Action[]{getCancelAction(), getOKAction()}; 
      }
      
      return new Action[]{getOKAction(), getCancelAction()};
    }
    else {
      if (SystemInfo.isMac) {
        return new Action[]{getHelpAction(), getCancelAction(), getOKAction()};
      }
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }
  }

  protected Action[] createLeftSideActions() {
    return new Action[0];
  }

  /**
   * @return default implementation of "OK" action. This action just invokes
   *         <code>doOKAction()</code> method.
   * @see #doOKAction
   */
  protected Action getOKAction() {
    return myOKAction;
  }

  /**
   * @return default implementation of "Cancel" action. This action just invokes
   *         <code>doCancelAction()</code> method.
   * @see #doCancelAction
   */
  protected Action getCancelAction() {
    return myCancelAction;
  }

  /**
   * @return default implementation of "Help" action. This action just invokes
   *         <code>doHelpAction()</code> method.
   * @see #doHelpAction
   */
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
   * @see javax.swing.JDialog#getContentPane
   */
  public Container getContentPane() {
    assert myPeer != null;
    return myPeer.getContentPane();
  }

  /**
   * @see javax.swing.JDialog#validate
   */
  public void validate() {
    myPeer.validate();
  }

  /**
   * @see javax.swing.JDialog#repaint
   */
  public void repaint() {
    myPeer.repaint();
  }

  /**
   * Returns key for persisting dialog dimensions.
   * <p/>
   * Default implementation returns <code>null</code> (no persisting).
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
   *         on the screen.
   */
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return SystemInfo.isMac ? myPreferredFocusedComponent : null;
  }

  /**
   * @return horizontal stretch of the dialog. It means that the dialog's horizontal size is
   *         the product of horizontal stretch by horizontal size of packed dialog. The default value
   *         is <code>1.0f</code>
   */
  public final float getHorizontalStretch() {
    return myHorizontalStretch;
  }

  /**
   * @return vertical stretch of the dialog. It means that the dialog's vertical size is
   *         the product of vertical stretch by vertical size of packed dialog. The default value
   *         is <code>1.0f</code>
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
   * @see java.awt.Window#getOwner
   * @return window owner
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
   * @see javax.swing.JDialog#getRootPane
   * @return root pane
   */
  public JRootPane getRootPane() {
    return myPeer.getRootPane();
  }

  /**
   * @see java.awt.Window#getSize
   * @return dialog size
   */
  public Dimension getSize() {
    return myPeer.getSize();
  }

  /**
   * @see java.awt.Dialog#getTitle
   * @return dialog title
   */
  public String getTitle() {
    return myPeer.getTitle();
  }

  protected void init() {
    myErrorText = new ErrorText();
    myErrorText.setVisible(false);

    final JPanel root = new JPanel(new BorderLayout());
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
    final AnAction toggleShowOptions = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        expandNextOptionButton();
      }
    };
    toggleShowOptions.registerCustomShortcutSet(sc, root);

    final JPanel northSection = new JPanel(new BorderLayout());
    root.add(northSection, BorderLayout.NORTH);

    JComponent titlePane = createTitlePane();
    if (titlePane != null) {
      northSection.add(titlePane, BorderLayout.CENTER);
    }

    JComponent centerSection = new JPanel(new BorderLayout());
    root.add(centerSection, BorderLayout.CENTER);

    root.setBorder(createContentPaneBorder());

    final JComponent n = createNorthPanel();
    if (n != null) {
      centerSection.add(n, BorderLayout.NORTH);
    }

    final JComponent c = createCenterPanel();
    if (c != null) {
      centerSection.add(c, BorderLayout.CENTER);
      myErrorPane = c;
    }
    if (myErrorPane == null) {
      myErrorPane = root;
    }

    final JPanel southSection = new JPanel(new BorderLayout());
    root.add(southSection, BorderLayout.SOUTH);

    southSection.add(myErrorText, BorderLayout.CENTER);
    final JComponent south = createSouthPanel();
    if (south != null) {
      southSection.add(south, BorderLayout.SOUTH);
    }

    new MnemonicHelper().register(root);
    if (!postponeValidation()) {
      startTrackingValidation();
    }
    if (SystemInfo.isWindows) {
      installEnterHook(root);
    }
  }

  private static void installEnterHook(JComponent root) {
    new AnAction() {
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
        e.getPresentation().setEnabled((owner instanceof JButton && owner.isEnabled()));
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), root);
  }

  private void expandNextOptionButton() {
    if (myCurrentOptionsButtonIndex > 0) {
      myOptionsButtons.get(myCurrentOptionsButtonIndex).closePopup();
      myCurrentOptionsButtonIndex++;
    } else if (myOptionsButtons.size() > 0) {
      myCurrentOptionsButtonIndex = 0;
    }

    if (myCurrentOptionsButtonIndex >= 0 && myCurrentOptionsButtonIndex < myOptionsButtons.size()) {
      myOptionsButtons.get(myCurrentOptionsButtonIndex).showPopup(null, true);
    }

  }

  void startTrackingValidation() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (!myValidationStarted && !myDisposed) {
          myValidationStarted = true;
          initValidation();
        }
      }
    });
  }

  protected final void initValidation() {
    myValidationAlarm.cancelAllRequests();
    final Runnable validateRequest = new Runnable() {
      public void run() {
        if (myDisposed) return;
        final ValidationInfo result = doValidate();
        if (result == null) {
          clearProblems();
        }
        else {
          reportProblem(result);
        }

        if (!myDisposed) {
          initValidation();
        }
      }
    };

    if (getValidationThreadToUse() == Alarm.ThreadToUse.SWING_THREAD) {
      myValidationAlarm.addRequest(validateRequest, myValidationDelay, ModalityState.current());
    } else {
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

  protected JComponent createContentPane() {
    return new JPanel();
  }

  /**
   * @see java.awt.Window#pack
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
   *                  <code>SwingConstants.CENTER</code> and <code>SwingConstants.RIGHT</code>.
   *                  The <code>SwingConstants.RIGHT</code> is the default value.
   * @throws java.lang.IllegalArgumentException
   *          if <code>alignment</code> isn't acceptable
   */
  protected final void setButtonsAlignment(@MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.RIGHT}) int alignment) {
    if (SwingConstants.CENTER != alignment && SwingConstants.RIGHT != alignment) {
      throw new IllegalArgumentException("unknown alignment: " + alignment);
    }
    myButtonAlignment = alignment;
  }

  /**
   * Sets margin for command buttons ("OK", "Cancel", "Help").
   * @param insets buttons margin
   */
  public final void setButtonsMargin(@Nullable Insets insets) {
    myButtonMargins = insets;
  }

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

  protected void setOKActionEnabled(boolean isEnabled) {
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
   *             {@link javax.swing.AbstractButton#setText(java.lang.String)}
   *             {@link javax.swing.AbstractButton#updateDisplayedMnemonicIndex(java.lang.String, int)}
   */
  protected final void setOKButtonText(String text) {
    myOKAction.putValue(Action.NAME, text);
  }

  protected final void setOKButtonMnemonic(int c) {
    myOKAction.putValue(Action.MNEMONIC_KEY, c);
  }

  /**
   * @return the help identifier or null if no help is available.
   */
  @Nullable
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

  public boolean isOKActionEnabled() {
    return myOKAction.isEnabled();
  }

  /**
   * @see java.awt.Component#isVisible
   * @return <code>true</code> if and only if visible
   */
  public boolean isVisible() {
    return myPeer.isVisible();
  }

  /**
   * @see java.awt.Window#isShowing
   * @return <code>true</code> if and only if showing
   */
  public boolean isShowing() {
    return myPeer.isShowing();
  }

  /**
   * @param width width
   * @param height height
   * @see javax.swing.JDialog#setSize
   */
  public void setSize(int width, int height) {
    myPeer.setSize(width, height);
  }

  /**
   * @param title title
   * @see javax.swing.JDialog#setTitle
   */
  public void setTitle(String title) {
    myPeer.setTitle(title);
  }

  /**
   * @see javax.swing.JDialog#isResizable
   */
  public void isResizable() {
    myPeer.isResizable();
  }

  /**
   * @param resizable is resizable
   * @see javax.swing.JDialog#setResizable
   */
  public void setResizable(boolean resizable) {
    myPeer.setResizable(resizable);
  }

  /**
   * @see javax.swing.JDialog#getLocation
   * @return dialog location
   */
  public Point getLocation() {
    return myPeer.getLocation();
  }

  /**
   * @param p new dialog location
   * @see javax.swing.JDialog#setLocation(Point)
   */
  public void setLocation(Point p) {
    myPeer.setLocation(p);
  }

  /**
   * @param x x
   * @param y y
   * @see javax.swing.JDialog#setLocation(int,int)
   */
  public void setLocation(int x, int y) {
    myPeer.setLocation(x, y);
  }

  public void centerRelativeToParent() {
    myPeer.centerInParent();
  }

  /**
   * Show the dialog
   *
   * @throws IllegalStateException if the dialog is invoked not on the event dispatch thread
   */
  public void show() {
    showAndGetOk();
  }

  public boolean showAndGet() {
    show();
    return isOK();
  }

  /**
   * You need this method ONLY for NON-MODAL dialogs. Otherwise, use {@link #show()} or {@link #showAndGet()}.
   * @return result callback
   */
  public AsyncResult<Boolean> showAndGetOk() {
    final AsyncResult<Boolean> result = new AsyncResult<Boolean>();

    ensureEventDispatchThread();
    registerKeyboardShortcuts();


    final Disposable uiParent = Disposer.get("ui");
    if (uiParent != null) { // may be null if no app yet (license agreement)
      Disposer.register(uiParent, myDisposable); // ensure everything is disposed on app quit
    }

    myPeer.show().doWhenProcessed(new Runnable() {
      public void run() {
        result.setDone(isOK());
      }
    });

    return result;
  }

  /**
   * @return Location in absolute coordinates which is used when dialog has no dimension service key or no position was stored yet.
   *         Can return null. In that case dialog will be centered relative to its owner.
   */
  @Nullable
  public Point getInitialLocation() {
    return myInitialLocationCallback == null ? null : myInitialLocationCallback.compute();
  }

  public void setInitialLocationCallback(Computable<Point> callback) {
    myInitialLocationCallback = callback;
  }

  private void registerKeyboardShortcuts() {
    ActionListener cancelKeyboardAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
        MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
        if (selectedPath.length > 0) { // hide popup menu if any
          menuSelectionManager.clearSelectedPath();
        }
        else {
          final StackingPopupDispatcher popupDispatcher = StackingPopupDispatcher.getInstance();
          if (ApplicationManager.getApplication() == null || (popupDispatcher != null && !popupDispatcher.isPopupFocused())) {
            doCancelAction(e);
          }
        }
      }
    };

    final JRootPane rootPane = getRootPane();

    if (rootPane == null) return;

    rootPane.registerKeyboardAction(cancelKeyboardAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    registerForEveryKeyboardShortcut(cancelKeyboardAction, CommonShortcuts.getCloseActiveWindow());

    if (ApplicationInfo.contextHelpAvailable()) {
      ActionListener helpAction = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doHelpAction();
        }
      };

      registerForEveryKeyboardShortcut(helpAction, CommonShortcuts.getContextHelp());
      rootPane.registerKeyboardAction(helpAction, KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    if (myButtons != null) {
      rootPane.registerKeyboardAction(new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          focusPreviousButton();
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      rootPane.registerKeyboardAction(new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          focusNextButton();
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    if (myYesAction != null) {
      rootPane.registerKeyboardAction(myYesAction, KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    if (myNoAction != null) {
      rootPane.registerKeyboardAction(myNoAction, KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
  }

  private void registerForEveryKeyboardShortcut(ActionListener action, ShortcutSet shortcuts) {
    for (Shortcut shortcut : shortcuts.getShortcuts()){
      if (shortcut instanceof KeyboardShortcut) {
        KeyboardShortcut ks = (KeyboardShortcut)shortcut;
        KeyStroke first = ks.getFirstKeyStroke();
        KeyStroke second = ks.getSecondKeyStroke();
        if (second == null) {
          getRootPane().registerKeyboardAction(action, first, JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
      }
    }
  }

  private void focusPreviousButton() {
    for (int i = 0; i < myButtons.length; i++) {
      if (myButtons[i].hasFocus()) {
        if (i == 0) {
          myButtons[myButtons.length - 1].requestFocus();
          return;
        }
        myButtons[i - 1].requestFocus();
        return;
      }
    }
  }

  private void focusNextButton() {
    for (int i = 0; i < myButtons.length; i++) {
      if (myButtons[i].hasFocus()) {
        if (i == myButtons.length - 1) {
          myButtons[0].requestFocus();
          return;
        }
        myButtons[i + 1].requestFocus();
        return;
      }
    }
  }

  public long getTypeAheadTimeoutMs() {
    return 0l;
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
    protected DialogWrapperAction(String name) {
      putValue(NAME, name);
    }

    /**
     * {@inheritDoc}
     */
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
     * is performed in parallel (checked using {@link com.intellij.openapi.ui.DialogWrapper#myPerformAction}),
     * and dialog is active (checked using {@link com.intellij.openapi.ui.DialogWrapper#myClosed})
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
      ValidationInfo info = doValidate();
      if (info != null) {
        if (info.component != null && info.component.isVisible()) {
          IdeFocusManager.getInstance(null).requestFocus(info.component, true);
        }
        startTrackingValidation();
        return;
      }
      doOKAction();
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

    public void actionPerformed(ActionEvent e) {
      doHelpAction();
    }
  }

  private Dimension myActualSize = null;
  private String myLastErrorText = null;
  
  protected final void setErrorText(@Nullable final String text) {
    if (Comparing.equal(myLastErrorText, text)) {
      return;
    }
    myLastErrorText = text;
    if (myActualSize == null && !StringUtil.isEmpty(text)) {
      myActualSize = getSize();
    }

    myErrorTextAlarm.cancelAllRequests();
    myErrorTextAlarm.addRequest(new Runnable() {
      public void run() {
        final String text = myLastErrorText;
        myErrorText.setError(text);
        if (text != null && text.length() > myMaxErrorTextLength) {
          // during the first update, resize only for growing. during a subsequent update,
          // if error text becomes longer, the min size calculation may not calculate enough size,
          // so we pack() even though it could cause the dialog to become smaller.
          if (myMaxErrorTextLength == 0) {
            updateHeightForErrorText();
          }
          else {
            //if (getRootPane() != null) myPeer.pack();
          }
          myMaxErrorTextLength = text.length();
          updateHeightForErrorText();
        }
        myErrorText.repaint();
        if (StringUtil.isEmpty(text) && myActualSize != null) {
          resizeWithAnimation(myActualSize);
          myMaxErrorTextLength = 0;
        }
      }
    }, 300, null);
  }

  @Nullable
  public static DialogWrapper findInstance(Component c) {
    while (c != null){
      if (c instanceof DialogWrapperDialog) {
        return ((DialogWrapperDialog)c).getDialogWrapper();
      }
      c = c.getParent();
    }
    return null;
  }

  private void resizeWithAnimation(final Dimension size) {
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
          setSize(cur.width + w * step, cur.height + h*step);
          try {
            //noinspection BusyWait
            sleep(time / steps);
          } catch (InterruptedException ignore) {}
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

  private void updateHeightForErrorText() {
    Dimension errorSize = myErrorText.getPreferredSize();
    resizeWithAnimation(new Dimension(Math.max(myActualSize.width, errorSize.width + 40), myActualSize.height + errorSize.height + 10));
  }

  private static class ErrorText extends JPanel {
    private final JLabel myLabel = new JLabel();
    private Dimension myPrefSize;
    private String myText;

    private ErrorText() {
      setLayout(new BorderLayout());
      JBScrollPane pane =
        new JBScrollPane(myLabel, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setBorder(IdeBorderFactory.createEmptyBorder(0));
      pane.setBackground(null);
      pane.getViewport().setBackground(null);
      pane.setOpaque(false);
      add(pane, BorderLayout.CENTER);
    }

    public void setError(String text) {
      final Dimension oldSize = getPreferredSize();
      myText = text;

      if (text == null) {
        myLabel.setText("");
        myLabel.setIcon(null);
        setVisible(false);
        setBorder(null);
      }
      else {
        myLabel.setText("<html><body><font color=red><left>" + text + "</left></b></font></body></html>");
        myLabel.setIcon(AllIcons.Actions.Lightning);
        myLabel.setBorder(new EmptyBorder(4, 10, 0, 2));
        setVisible(true);
      }

      final Dimension size = getPreferredSize();
      if (oldSize.height < size.height) {
        revalidate();
      }
    }

    public boolean shouldBeVisible() {
      return !StringUtil.isEmpty(myText);
    }

    public boolean isTextSet(@Nullable String text) {
      return StringUtil.equals(text, myText);
    }

    public Dimension getPreferredSize() {
      return myPrefSize == null ? myLabel.getPreferredSize() : myPrefSize;
    }
  }

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
      throw new IllegalStateException("The DialogWrapper can be used only on event dispatch thread.");
    }
  }

  public final Disposable getDisposable() {
    return myDisposable;
  }

  public interface DoNotAskOption {

    boolean isToBeShown();

    void setToBeShown(boolean value, int exitCode);

    /**
     * Should be 'true' for checkbox to be visible. 
     */
    boolean canBeHidden();

    boolean shouldSaveOptionsOnCancel();

    String getDoNotShowMessage();
  }

  private ErrorPaintingType getErrorPaintingType() {
    return ErrorPaintingType.SIGN;
  }

  private class ErrorPainter extends AbstractPainter {
    private ValidationInfo myInfo;

    @Override
    public void executePaint(Component component, Graphics2D g) {
      if (myInfo != null &&  myInfo.component != null) {
        final JComponent comp = myInfo.component;
        final int w = comp.getWidth();
        final int h = comp.getHeight();
        Point p;
        switch (getErrorPaintingType()) {
          case DOT:
            p = SwingUtilities.convertPoint(comp, 2,  h/2 , component);
            AllIcons.Ide.ErrorPoint.paintIcon(component, g, p.x, p.y);
            break;
          case SIGN:
            p = SwingUtilities.convertPoint(comp, w,  0, component);
            AllIcons.General.Error.paintIcon(component, g, p.x - 8, p.y - 8);
            break;
          case LINE:
            p = SwingUtilities.convertPoint(comp, 0,  h, component);
            final GraphicsConfig config = new GraphicsConfig(g);
            g.setColor(new Color(255, 0, 0 , 100));
            g.fillRoundRect(p.x, p.y-2, w, 4, 2, 2);
            config.restore();
            break;
        }
      }
    }

    @Override
    public boolean needsRepaint() {
      return true;
    }

    public void setValidationInfo(@Nullable ValidationInfo info) {
      myInfo = info;
    }
  }

  private static enum ErrorPaintingType {DOT, SIGN, LINE}
}
