package com.intellij.openapi.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.MessageException;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.mac.MacMessages;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

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
                                 TextRange selection,
                                 String comment) {
    InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator,
                                         new String[]{OK_BUTTON, CANCEL_BUTTON},
                                         0, comment);

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
}
