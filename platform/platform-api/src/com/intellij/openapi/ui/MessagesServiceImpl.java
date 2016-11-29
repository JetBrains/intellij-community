package com.intellij.openapi.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.MessageException;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.mac.MacMessages;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.ui.Messages.CANCEL_BUTTON;
import static com.intellij.openapi.ui.Messages.OK_BUTTON;

public class MessagesServiceImpl implements MessagesService {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.MessagesServiceImpl");

  @Override
  public int showMessageDialog(@Nullable Project project,
                               String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {

    try {
      if (canShowMacSheetPanel()) {
        WindowManager windowManager = WindowManager.getInstance();
        if (windowManager != null) {
          Window parentWindow = windowManager.suggestParentWindow(project);
          return MacMessages.getInstance()
            .showMessageDialog(title, message, options, false, parentWindow, defaultOptionIndex, defaultOptionIndex, doNotAskOption);
        }
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    return showIdeaMessageDialog(project, message, title, options, defaultOptionIndex, defaultOptionIndex, icon, doNotAskOption);
  }

  @Override
  public int showIdeaMessageDialog(Project project,
                                   String message,
                                   String title,
                                   String[] options,
                                   int defaultOptionIndex,
                                   int i,
                                   Icon icon,
                                   DialogWrapper.DoNotAskOption doNotAskOption) {
    MessageDialog dialog = new MessageDialog(project, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption, false);
    dialog.show();
    return dialog.getExitCode();
  }

  static boolean isApplicationInUnitTestOrHeadless() {
    final Application application = ApplicationManager.getApplication();
    return application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment());
  }


  @Override
  public boolean canShowMacSheetPanel() {
    return SystemInfo.isMac && !isApplicationInUnitTestOrHeadless() && Registry.is("ide.mac.message.dialogs.as.sheets");
  }

  @Override
  public boolean isMacSheetEmulation() {
    return SystemInfo.isMac && Registry.is("ide.mac.message.dialogs.as.sheets") && Registry.is("ide.mac.message.sheets.java.emulation");
  }

  @Override
  public int showDialog(String message,
                        String title,
                        String[] options,
                        int defaultOptionIndex,
                        int focusedOptionIndex,
                        Icon icon,
                        DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showMessageDialog(title, message, options, false, null, defaultOptionIndex, focusedOptionIndex, doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    //what's it? if (application.isUnitTestMode()) throw new RuntimeException(message);
    MessageDialog dialog = new MessageDialog(message, title, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    dialog.show();
    return dialog.getExitCode();
  }

  @Override
  public int showDialog2(Project project,
                         String message,
                         String title,
                         String moreInfo,
                         String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         Icon icon) {
    try {
      if (canShowMacSheetPanel() && moreInfo == null) {
        return MacMessages.getInstance()
          .showMessageDialog(title, message, options, false, WindowManager.getInstance().suggestParentWindow(project), defaultOptionIndex,
                             focusedOptionIndex, null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    MessageDialog dialog =
      new MoreInfoMessageDialog(project, message, title, moreInfo, options, defaultOptionIndex, focusedOptionIndex, icon);
    dialog.show();
    return dialog.getExitCode();
  }

  @Override
  public int showDialog3(Component parent, String message, String title, String[] options, int defaultOptionIndex, Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showMessageDialog(title, message, options, false, SwingUtilities.getWindowAncestor(parent), defaultOptionIndex,
                             defaultOptionIndex, null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    MessageDialog dialog = new MessageDialog(parent, message, title, options, defaultOptionIndex, defaultOptionIndex, icon, false);
    dialog.show();
    return dialog.getExitCode();
  }

  @Override
  public int showTwoStepConfirmationDialog(String message,
                                           String title,
                                           String[] options,
                                           String checkboxText,
                                           boolean checked,
                                           int defaultOptionIndex,
                                           int focusedOptionIndex,
                                           Icon icon,
                                           PairFunction<Integer, JCheckBox, Integer> exitFunc) {
    TwoStepConfirmationDialog dialog =
      new TwoStepConfirmationDialog(message, title, options, checkboxText, checked, defaultOptionIndex, focusedOptionIndex, icon, exitFunc);
    dialog.show();
    return dialog.getExitCode();
  }

  @Override
  public String showPasswordDialog(Project project, String message, String title, Icon icon, InputValidator validator) {
    final InputDialog dialog = project != null
                               ? new PasswordInputDialog(project, message, title, icon, validator)
                               : new PasswordInputDialog(message, title, icon, validator);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  public String showInputDialog(Project project, String message, String title, Icon icon, String initialValue, InputValidator validator) {
    InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  public String showInputDialog2(Project project,
                                 String message,
                                 String title,
                                 Icon icon,
                                 String initialValue,
                                 InputValidator validator,
                                 TextRange selection) {
    InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator);

    final JTextComponent field = dialog.getTextField();
    if (selection != null) {
      // set custom selection
      field.select(selection.getStartOffset(), selection.getEndOffset());
    }
    else {
      // reset selection
      final int length = field.getDocument().getLength();
      field.select(length, length);
    }
    field.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true);

    dialog.show();
    return dialog.getInputString();
  }

  @Override
  public String showInputDialog3(Component parent, String message, String title, Icon icon, String initialValue, InputValidator validator) {
    InputDialog dialog = new InputDialog(parent, message, title, icon, initialValue, validator);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  public String showInputDialog4(String message, String title, Icon icon, String initialValue, InputValidator validator) {
    InputDialog dialog = new InputDialog(message, title, icon, initialValue, validator);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  public String showMultilineInputDialog(Project project,
                                         String message,
                                         String title,
                                         String initialValue,
                                         Icon icon,
                                         InputValidator validator) {
    InputDialog dialog = new MultilineInputDialog(project, message, title, icon, initialValue, validator,
                                                  new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  public Pair<String, Boolean> showInputDialogWithCheckBox(String message,
                                                           String title,
                                                           String checkboxText,
                                                           boolean checked,
                                                           boolean checkboxEnabled,
                                                           Icon icon,
                                                           String initialValue,
                                                           InputValidator validator) {
    InputDialogWithCheckbox dialog =
      new InputDialogWithCheckbox(message, title, checkboxText, checked, checkboxEnabled, icon, initialValue, validator);
    dialog.show();
    return Pair.create(dialog.getInputString(), dialog.isChecked());
  }

  @Override
  public String showEditableChooseDialog(String message,
                                         String title,
                                         Icon icon,
                                         String[] values,
                                         String initialValue,
                                         InputValidator validator) {
    ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
    dialog.setValidator(validator);
    dialog.getComboBox().setEditable(true);
    dialog.getComboBox().getEditor().setItem(initialValue);
    dialog.getComboBox().setSelectedItem(initialValue);
    dialog.show();
    return dialog.getInputString();
  }

  @Override
  public int showChooseDialog(String message, String title, String[] values, String initialValue, Icon icon) {
    ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
    dialog.show();
    return dialog.getSelectedIndex();
  }

  @Override
  public int showChooseDialog2(Component parent, String message, String title, String[] values, String initialValue, Icon icon) {
    ChooseDialog dialog = new ChooseDialog(parent, message, title, icon, values, initialValue);
    dialog.show();
    return dialog.getSelectedIndex();
  }

  @Override
  public int showChooseDialog3(Project project, String message, String title, Icon icon, String[] values, String initialValue) {
    ChooseDialog dialog = new ChooseDialog(project, message, title, icon, values, initialValue);
    dialog.show();
    return dialog.getSelectedIndex();
  }

  @Override
  public void showTextAreaDialog(final JTextField textField,
                                 String title,
                                 String dimensionServiceKey,
                                 Function<String, java.util.List<String>> parser,
                                 final Function<java.util.List<String>, String> lineJoiner) {
    final JTextArea textArea = new JTextArea(10, 50);
    UIUtil.addUndoRedoActions(textArea);
    textArea.setWrapStyleWord(true);
    textArea.setLineWrap(true);
    List<String> lines = parser.fun(textField.getText());
    textArea.setText(StringUtil.join(lines, "\n"));
    InsertPathAction.copyFromTo(textField, textArea);
    final DialogBuilder builder = new DialogBuilder(textField);
    builder.setDimensionServiceKey(dimensionServiceKey);
    builder.setCenterPanel(ScrollPaneFactory.createScrollPane(textArea));
    builder.setPreferredFocusComponent(textArea);
    String rawText = title;
    if (StringUtil.endsWithChar(rawText, ':')) {
      rawText = rawText.substring(0, rawText.length() - 1);
    }
    builder.setTitle(rawText);
    builder.addOkAction();
    builder.addCancelAction();
    builder.setOkOperation(() -> {
      textField.setText(lineJoiner.fun(Arrays.asList(StringUtil.splitByLines(textArea.getText()))));
      builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
    });
    builder.show();
  }

  private class MoreInfoMessageDialog extends MessageDialog {
    @Nullable private final String myInfoText;

    public MoreInfoMessageDialog(Project project,
                                 String message,
                                 @Nls(capitalization = Nls.Capitalization.Title) String title,
                                 @Nullable String moreInfo,
                                 @NotNull String[] options,
                                 int defaultOptionIndex,
                                 int focusedOptionIndex,
                                 Icon icon) {
      super(project);
      myInfoText = moreInfo;
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    @Override
    protected JComponent createNorthPanel() {
      return doCreateCenterPanel();
    }

    @Override
    protected JComponent createCenterPanel() {
      if (myInfoText == null) {
        return null;
      }
      final JPanel panel = new JPanel(new BorderLayout());
      final JTextArea area = new JTextArea(myInfoText);
      area.setEditable(false);
      final JBScrollPane scrollPane = new JBScrollPane(area) {
        @Override
        public Dimension getPreferredSize() {
          final Dimension preferredSize = super.getPreferredSize();
          final Container parent = getParent();
          if (parent != null) {
            return new Dimension(preferredSize.width, Math.min(150, preferredSize.height));
          }
          return preferredSize;
        }
      };
      panel.add(scrollPane);
      return panel;
    }
  }

  private static class MessageDialog extends DialogWrapper {
    protected String myMessage;
    protected String[] myOptions;
    protected int myDefaultOptionIndex;
    protected int myFocusedOptionIndex;
    protected Icon myIcon;
    private MyBorderLayout myLayout;

    public MessageDialog(@Nullable Project project,
                         String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         @Nullable Icon icon,
                         boolean canBeParent) {
      this(project, message, title, options, defaultOptionIndex, -1, icon, canBeParent);
    }

    public MessageDialog(@Nullable Project project,
                         String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         @Nullable Icon icon,
                         @Nullable DoNotAskOption doNotAskOption,
                         boolean canBeParent) {
      super(project, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    }

    public MessageDialog(@Nullable Project project,
                         String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         @Nullable Icon icon,
                         boolean canBeParent) {
      super(project, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    public MessageDialog(@NotNull Component parent,
                         String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         @Nullable Icon icon) {
      this(parent, message, title, options, defaultOptionIndex, icon, false);
    }

    public MessageDialog(@NotNull Component parent,
                         String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         @Nullable Icon icon,
                         boolean canBeParent) {
      this(parent, message, title, options, defaultOptionIndex, -1, icon, canBeParent);
    }

    public MessageDialog(@NotNull Component parent,
                         String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         @Nullable Icon icon,
                         boolean canBeParent) {
      super(parent, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    public MessageDialog(String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         @Nullable Icon icon) {
      this(message, title, options, defaultOptionIndex, icon, false);
    }

    public MessageDialog(String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         @Nullable Icon icon,
                         boolean canBeParent) {
      super(canBeParent);
      _init(title, message, options, defaultOptionIndex, -1, icon, null);
    }

    public MessageDialog(String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         @Nullable Icon icon,
                         @Nullable DoNotAskOption doNotAskOption) {
      super(false);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    }

    public MessageDialog(String message,
                         @Nls(capitalization = Nls.Capitalization.Title) String title,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         Icon icon,
                         DoNotAskOption doNotAskOption) {
      this(message, title, options, defaultOptionIndex, -1, icon, doNotAskOption);
    }

    protected MessageDialog() {
      super(false);
    }

    protected MessageDialog(Project project) {
      super(project, false);
    }

    protected void _init(@Nls(capitalization = Nls.Capitalization.Title) String title,
                         String message,
                         @NotNull String[] options,
                         int defaultOptionIndex,
                         int focusedOptionIndex,
                         @Nullable Icon icon,
                         @Nullable DoNotAskOption doNotAskOption) {
      setTitle(title);
      if (Messages.isMacSheetEmulation()) {
        setUndecorated(true);
      }
      myMessage = message;
      myOptions = options;
      myDefaultOptionIndex = defaultOptionIndex;
      myFocusedOptionIndex = focusedOptionIndex;
      myIcon = icon;
      if (!SystemInfo.isMac) {
        setButtonsAlignment(SwingConstants.CENTER);
      }
      setDoNotAskOption(doNotAskOption);
      init();
      if (Messages.isMacSheetEmulation()) {
        MacUtil.adjustFocusTraversal(myDisposable);
      }
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        actions[i] = new AbstractAction(UIUtil.replaceMnemonicAmpersand(option)) {
          @Override
          public void actionPerformed(ActionEvent e) {
            close(exitCode, true);
          }
        };

        if (i == myDefaultOptionIndex) {
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
        }

        if (i == myFocusedOptionIndex) {
          actions[i].putValue(FOCUSED_ACTION, Boolean.TRUE);
        }

        UIUtil.assignMnemonic(option, actions[i]);

      }
      return actions;
    }

    @Override
    public void doCancelAction() {
      close(-1);
    }

    @Override
    protected JComponent createCenterPanel() {
      return doCreateCenterPanel();
    }

    @NotNull
    @Override
    LayoutManager createRootLayout() {
      return Messages.isMacSheetEmulation() ? myLayout = new MyBorderLayout() : super.createRootLayout();
    }

    @Override
    protected void dispose() {
      if (Messages.isMacSheetEmulation()) {
        animate();
      }
      else {
        super.dispose();
      }
    }

    @Override
    public void show() {
      if (Messages.isMacSheetEmulation()) {
        setInitialLocationCallback(() -> {
          JRootPane rootPane = SwingUtilities.getRootPane(getWindow().getParent());
          if (rootPane == null) {
            rootPane = SwingUtilities.getRootPane(getWindow().getOwner());
          }

          Point p = rootPane.getLocationOnScreen();
          p.x += (rootPane.getWidth() - getWindow().getWidth()) / 2;
          return p;
        });
        animate();
        if (SystemInfo.isJavaVersionAtLeast("1.7")) {
          try {
            Method method = Class.forName("java.awt.Window").getDeclaredMethod("setOpacity", float.class);
            if (method != null) method.invoke(getPeer().getWindow(), .8f);
          }
          catch (Exception exception) {
          }
        }
        setAutoAdjustable(false);
        setSize(getPreferredSize().width, 0);//initial state before animation, zero height
      }
      super.show();
    }

    private void animate() {
      final int height = getPreferredSize().height;
      final int frameCount = 10;
      final boolean toClose = isShowing();


      final AtomicInteger i = new AtomicInteger(-1);
      final Alarm animator = new Alarm(myDisposable);
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          int state = i.addAndGet(1);

          double linearProgress = (double)state / frameCount;
          if (toClose) {
            linearProgress = 1 - linearProgress;
          }
          myLayout.myPhase = (1 - Math.cos(Math.PI * linearProgress)) / 2;
          Window window = getPeer().getWindow();
          Rectangle bounds = window.getBounds();
          bounds.height = (int)(height * myLayout.myPhase);

          window.setBounds(bounds);

          if (state == 0 && !toClose && window.getOwner() instanceof IdeFrame) {
            WindowManager.getInstance().requestUserAttention((IdeFrame)window.getOwner(), true);
          }

          if (state < frameCount) {
            animator.addRequest(this, 10);
          }
          else if (toClose) {
            MessageDialog.super.dispose();
          }
        }
      };
      animator.addRequest(runnable, 10, ModalityState.stateForComponent(getRootPane()));
    }

    protected JComponent doCreateCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myIcon != null) {
        JLabel iconLabel = new JLabel(myIcon);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }
      if (myMessage != null) {
        final JTextPane messageComponent = createMessageComponent(myMessage);
        panel.add(Messages.wrapToScrollPaneIfNeeded(messageComponent, 100, 10), BorderLayout.CENTER);
      }
      return panel;
    }

    protected JTextPane createMessageComponent(final String message) {
      final JTextPane messageComponent = new JTextPane();
      return Messages.configureMessagePaneUi(messageComponent, message);
    }

    @Override
    protected void doHelpAction() {
      // do nothing
    }
  }

  private static class MyBorderLayout extends BorderLayout {
    private double myPhase = 0;//it varies from 0 (hidden state) to 1 (fully visible)

    private MyBorderLayout() {
    }

    @Override
    public void layoutContainer(Container target) {
      final Dimension realSize = target.getSize();
      target.setSize(target.getPreferredSize());

      super.layoutContainer(target);

      target.setSize(realSize);

      synchronized (target.getTreeLock()) {
        int yShift = (int)((1 - myPhase) * target.getPreferredSize().height);
        Component[] components = target.getComponents();
        for (Component component : components) {
          Point point = component.getLocation();
          point.y -= yShift;
          component.setLocation(point);
        }
      }
    }
  }

  protected class TwoStepConfirmationDialog extends MessageDialog {
    private JCheckBox myCheckBox;
    private final String myCheckboxText;
    private final boolean myChecked;
    private final PairFunction<Integer, JCheckBox, Integer> myExitFunc;

    public TwoStepConfirmationDialog(String message,
                                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                                     @NotNull String[] options,
                                     String checkboxText,
                                     boolean checked,
                                     final int defaultOptionIndexed,
                                     final int focusedOptionIndex,
                                     Icon icon,
                                     @Nullable final PairFunction<Integer, JCheckBox, Integer> exitFunc) {
      myCheckboxText = checkboxText;
      myChecked = checked;
      myExitFunc = exitFunc;

      _init(title, message, options, defaultOptionIndexed, focusedOptionIndex, icon, null);
    }

    @Override
    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myIcon != null) {
        JLabel iconLabel = new JLabel(myIcon);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JLabel textLabel = new JLabel(myMessage);
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        textLabel.setUI(new MultiLineLabelUI());
        messagePanel.add(textLabel, BorderLayout.NORTH);
      }

      final JPanel checkboxPanel = new JPanel();
      checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.X_AXIS));

      myCheckBox = new JCheckBox(myCheckboxText);
      myCheckBox.setSelected(myChecked);
      messagePanel.add(myCheckBox, BorderLayout.SOUTH);
      panel.add(messagePanel, BorderLayout.CENTER);

      return panel;
    }

    @Override
    public int getExitCode() {
      final int exitCode = super.getExitCode();
      if (myExitFunc != null) {
        return myExitFunc.fun(exitCode, myCheckBox);
      }

      boolean checkBoxSelected = (myCheckBox != null && myCheckBox.isSelected());

      boolean okExitCode = (exitCode == OK_EXIT_CODE);

      return checkBoxSelected && okExitCode ? OK_EXIT_CODE : CANCEL_EXIT_CODE;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myDefaultOptionIndex == -1 ? myCheckBox : super.getPreferredFocusedComponent();
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }
  }

  public static class InputDialog extends MessageDialog {
    protected JTextComponent myField;
    private final InputValidator myValidator;

    public InputDialog(@Nullable Project project,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator,
                       @NotNull String[] options,
                       int defaultOption) {
      super(project, message, title, options, defaultOption, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
      enableOkAction();
    }

    public InputDialog(@Nullable Project project,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      this(project, message, title, icon, initialValue, validator, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public InputDialog(@NotNull Component parent,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
      enableOkAction();
    }

    public InputDialog(String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
      enableOkAction();
    }

    private void enableOkAction() {
      getOKAction().setEnabled(myValidator == null || myValidator.checkInput(myField.getText().trim()));
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == 0) { // "OK" is default button. It has index 0.
          actions[i] = getOKAction();
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(DocumentEvent event) {
              final String text = myField.getText().trim();
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(text));
              if (myValidator instanceof InputValidatorEx) {
                setErrorText(((InputValidatorEx)myValidator).getErrorText(text));
              }
            }
          });
        }
        else {
          actions[i] = new AbstractAction(option) {
            @Override
            public void actionPerformed(ActionEvent e) {
              close(exitCode);
            }
          };
        }
      }
      return actions;
    }

    @Override
    protected void doOKAction() {
      String inputString = myField.getText().trim();
      if (myValidator == null || myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
        close(0);
      }
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }

    @Override
    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myIcon != null) {
        JLabel iconLabel = new JLabel(myIcon);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      JPanel messagePanel = createMessagePanel();
      panel.add(messagePanel, BorderLayout.CENTER);

      return panel;
    }

    protected JPanel createMessagePanel() {
      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JComponent textComponent = createTextComponent();
        messagePanel.add(textComponent, BorderLayout.NORTH);
      }

      myField = createTextFieldComponent();
      messagePanel.add(myField, BorderLayout.SOUTH);

      return messagePanel;
    }

    protected JComponent createTextComponent() {
      JComponent textComponent;
      if (BasicHTML.isHTMLString(myMessage)) {
        textComponent = createMessageComponent(myMessage);
      }
      else {
        JLabel textLabel = new JLabel(myMessage);
        textLabel.setUI(new MultiLineLabelUI());
        textComponent = textLabel;
      }
      textComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      return textComponent;
    }

    public JTextComponent getTextField() {
      return myField;
    }

    protected JTextComponent createTextFieldComponent() {
      return new JTextField(30);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myField;
    }

    @Nullable
    public String getInputString() {
      if (getExitCode() == 0) {
        return myField.getText().trim();
      }
      return null;
    }
  }

  protected class MultilineInputDialog extends InputDialog {
    public MultilineInputDialog(Project project,
                                String message,
                                @Nls(capitalization = Nls.Capitalization.Title) String title,
                                @Nullable Icon icon,
                                @Nullable String initialValue,
                                @Nullable InputValidator validator,
                                @NotNull String[] options,
                                int defaultOption) {
      super(project, message, title, icon, initialValue, validator, options, defaultOption);
    }

    @Override
    protected JTextComponent createTextFieldComponent() {
      return new JTextArea(7, 50);
    }
  }

  protected class PasswordInputDialog extends InputDialog {
    public PasswordInputDialog(String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @Nullable Icon icon,
                               @Nullable InputValidator validator) {
      super(message, title, icon, null, validator);
    }

    public PasswordInputDialog(Project project,
                               String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @Nullable Icon icon,
                               @Nullable InputValidator validator) {
      super(project, message, title, icon, null, validator);
    }

    @Override
    protected JTextComponent createTextFieldComponent() {
      return new JPasswordField(30);
    }
  }

  protected class InputDialogWithCheckbox extends InputDialog {
    private JCheckBox myCheckBox;

    public InputDialogWithCheckbox(String message,
                                   @Nls(capitalization = Nls.Capitalization.Title) String title,
                                   String checkboxText,
                                   boolean checked,
                                   boolean checkboxEnabled,
                                   @Nullable Icon icon,
                                   @Nullable String initialValue,
                                   @Nullable InputValidator validator) {
      super(message, title, icon, initialValue, validator);
      myCheckBox.setText(checkboxText);
      myCheckBox.setSelected(checked);
      myCheckBox.setEnabled(checkboxEnabled);
    }

    @Override
    protected JPanel createMessagePanel() {
      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JComponent textComponent = createTextComponent();
        messagePanel.add(textComponent, BorderLayout.NORTH);
      }

      myField = createTextFieldComponent();
      messagePanel.add(myField, BorderLayout.CENTER);

      myCheckBox = new JCheckBox();
      messagePanel.add(myCheckBox, BorderLayout.SOUTH);

      return messagePanel;
    }

    public Boolean isChecked() {
      return myCheckBox.isSelected();
    }
  }

  /**
   * It looks awful!
   */
  @Deprecated
  public static class ChooseDialog extends MessageDialog {
    private ComboBox myComboBox;
    private InputValidator myValidator;

    public ChooseDialog(Project project,
                        String message,
                        @Nls(capitalization = Nls.Capitalization.Title) String title,
                        @Nullable Icon icon,
                        String[] values,
                        String initialValue,
                        @NotNull String[] options,
                        int defaultOption) {
      super(project, message, title, options, defaultOption, icon, true);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(Project project,
                        String message,
                        @Nls(capitalization = Nls.Capitalization.Title) String title,
                        @Nullable Icon icon,
                        String[] values,
                        String initialValue) {
      this(project, message, title, icon, values, initialValue, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public ChooseDialog(@NotNull Component parent,
                        String message,
                        @Nls(capitalization = Nls.Capitalization.Title) String title,
                        @Nullable Icon icon,
                        String[] values,
                        String initialValue) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(String message,
                        @Nls(capitalization = Nls.Capitalization.Title) String title,
                        @Nullable Icon icon,
                        String[] values,
                        String initialValue) {
      super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == myDefaultOptionIndex) {
          actions[i] = new AbstractAction(option) {
            @Override
            public void actionPerformed(ActionEvent e) {
              if (myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim())) {
                close(exitCode);
              }
            }
          };
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myComboBox.addItemListener(e -> actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim())));
          final JTextField textField = (JTextField)myComboBox.getEditor().getEditorComponent();
          textField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(DocumentEvent event) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(textField.getText().trim()));
            }
          });
        }
        else { // "Cancel" action
          actions[i] = new AbstractAction(option) {
            @Override
            public void actionPerformed(ActionEvent e) {
              close(exitCode);
            }
          };
        }
      }
      return actions;
    }

    @Override
    protected JComponent createCenterPanel() {
      return null;
    }

    @Override
    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myIcon != null) {
        JLabel iconLabel = new JLabel(myIcon);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JLabel textLabel = new JLabel(myMessage);
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        textLabel.setUI(new MultiLineLabelUI());
        messagePanel.add(textLabel, BorderLayout.NORTH);
      }

      myComboBox = new ComboBox(220);
      messagePanel.add(myComboBox, BorderLayout.SOUTH);
      panel.add(messagePanel, BorderLayout.CENTER);
      return panel;
    }

    @Override
    protected void doOKAction() {
      String inputString = myComboBox.getSelectedItem().toString().trim();
      if (myValidator == null || myValidator.checkInput(inputString) && myValidator.canClose(inputString)) {
        super.doOKAction();
      }
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    @Nullable
    public String getInputString() {
      if (getExitCode() == 0) {
        return myComboBox.getSelectedItem().toString();
      }
      return null;
    }

    public int getSelectedIndex() {
      if (getExitCode() == 0) {
        return myComboBox.getSelectedIndex();
      }
      return -1;
    }

    public JComboBox getComboBox() {
      return myComboBox;
    }

    public void setValidator(@Nullable InputValidator validator) {
      myValidator = validator;
    }
  }

}
