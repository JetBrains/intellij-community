// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.MessageException;
import com.intellij.ui.mac.MacMessages;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.*;

/**
 * Provides several default notification dialogs ("OK|Cancel") as well as simple input dialogs.
 */
@ApiStatus.NonExtendable
@SuppressWarnings("DeprecatedIsStillUsed")
public class Messages {
  public static final int OK = 0;
  public static final int YES = 0;
  public static final int NO = 1;
  public static final int CANCEL = 2;

  @MagicConstant(intValues = {YES, NO})
  public @interface YesNoResult { }

  @MagicConstant(intValues = {OK, CANCEL})
  public @interface OkCancelResult { }

  @MagicConstant(intValues = {YES, NO, CANCEL})
  public @interface YesNoCancelResult { }

  /** @deprecated Use {@link #getOkButton()} instead */
  @SuppressWarnings("HardCodedStringLiteral") @Deprecated
  public static final String OK_BUTTON = "OK";

  /** @deprecated Use {@link #getYesButton()} instead */
  @SuppressWarnings("HardCodedStringLiteral") @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static final String YES_BUTTON = "&Yes";

  /** @deprecated Use {@link #getNoButton()} instead */
  @SuppressWarnings("HardCodedStringLiteral") @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static final String NO_BUTTON = "&No";

  /** @deprecated Use {@link #getCancelButton()} instead */
  @SuppressWarnings("HardCodedStringLiteral") @Deprecated
  public static final String CANCEL_BUTTON = "Cancel";

  public static @Nls String getOkButton() { return CommonBundle.getOkButtonText(); }
  public static String getYesButton() { return CommonBundle.getYesButtonText(); }
  public static String getNoButton() { return CommonBundle.getNoButtonText(); }
  public static @Nls String getCancelButton() { return CommonBundle.getCancelButtonText(); }

  private static final Logger LOG = Logger.getInstance(Messages.class);

  public static @NotNull Icon getErrorIcon() {
    return UIUtil.getErrorIcon();
  }

  public static @NotNull Icon getInformationIcon() {
    return UIUtil.getInformationIcon();
  }

  public static @NotNull Icon getWarningIcon() {
    return UIUtil.getWarningIcon();
  }

  public static @NotNull Icon getQuestionIcon() {
    return UIUtil.getQuestionIcon();
  }

  /**
   * Please, use {@link MessageDialogBuilder#yesNo} or {@link MessageDialogBuilder#yesNoCancel} if possible (these dialogs implements native OS behavior)!
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project,
                               @DialogMessage String message,
                               @DialogTitle String title,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon) {
    return showDialog(project, message, title, options, defaultOptionIndex, icon, null);
  }

  public static boolean isApplicationInUnitTestOrHeadless() {
    final Application application = ApplicationManager.getApplication();
    return application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment());
  }

  public static @NotNull Runnable createMessageDialogRemover(@Nullable Project project) {
    Window projectWindow = project == null ? null : WindowManager.getInstance().suggestParentWindow(project);
    return () -> SwingUtilities.invokeLater(() -> {
      makeCurrentMessageDialogGoAway(projectWindow == null ? Window.getWindows() : projectWindow.getOwnedWindows());
    });
  }

  private static void makeCurrentMessageDialogGoAway(Window @NotNull [] checkWindows) {
    for (Window w : checkWindows) {
      JDialog dialog = w instanceof JDialog ? (JDialog)w : null;
      if (dialog == null || !dialog.isModal()) {
        continue;
      }
      JButton cancelButton = UIUtil.uiTraverser(dialog.getRootPane())
        .filter(JButton.class)
        .filter(b -> CommonBundle.getCancelButtonText().equals(b.getText()))
        .first();
      if (cancelButton != null) {
        cancelButton.doClick();
      }
    }
  }

  /**
   * Please use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project,
                               @DialogMessage String message,
                               @NotNull @DialogTitle String title,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DoNotAskOption doNotAskOption) {
    return MessagesService.getInstance()
      .showMessageDialog(project, null, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption, false, null);
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showIdeaMessageDialog(@Nullable Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          String @NotNull @NlsContexts.Button [] options,
                                          int defaultOptionIndex,
                                          @Nullable Icon icon,
                                          @Nullable DoNotAskOption doNotAskOption) {
    return MessagesService.getInstance()
      .showMessageDialog(project, null, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption, true, null);
  }

  public static boolean canShowMacSheetPanel() {
    if (!SystemInfoRt.isMac || MessagesService.getInstance().isAlertEnabled()) {
      return false;
    }

    Application app = ApplicationManager.getApplication();
    return app != null && !app.isUnitTestMode() && !app.isHeadlessEnvironment() && Registry.is("ide.mac.message.dialogs.as.sheets", true);
  }

  public static boolean isMacSheetEmulation() {
    return SystemInfoRt.isMac && Registry.is("ide.mac.message.dialogs.as.sheets", true) && Registry.is("ide.mac.message.sheets.java.emulation", false);
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(Project project,
                               @DialogMessage String message,
                               @NotNull @DialogTitle String title,
                               @Nullable String moreInfo,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               Icon icon) {
    return MessagesService.getInstance()
      .showMoreInfoMessageDialog(project, message, title, moreInfo, options, defaultOptionIndex, focusedOptionIndex, icon);
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@NotNull Component parent,
                               @DialogMessage String message,
                               @NotNull @DialogTitle String title,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon) {
    return MessagesService.getInstance().showMessageDialog(null, parent, message, title, options, defaultOptionIndex, -1, icon, null, false,
                                                           null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Icon, DoNotAskOption)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(@DialogMessage String message,
                               @NotNull @DialogTitle String title,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DoNotAskOption doNotAskOption) {
    return MessagesService.getInstance()
      .showMessageDialog(null, null, message, title, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption, false, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(@DialogMessage String message,
                               @DialogTitle String title,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DoNotAskOption doNotAskOption) {
    return showDialog(message, title, options, defaultOptionIndex, defaultOptionIndex, icon, doNotAskOption);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(@DialogMessage String message,
                               @DialogTitle String title,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon) {
    return showDialog(message, title, options, defaultOptionIndex, icon, null);
  }

  /**
   * @see DialogWrapper#DialogWrapper(Project, boolean)
   */
  public static void showMessageDialog(@Nullable Project project,
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, getOkButton(), WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(project, message, title, new String[]{getOkButton()}, 0, icon);
  }

  public static void showMessageDialog(@NotNull Component parent,
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, getOkButton(), SwingUtilities.getWindowAncestor(parent));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(parent, message, title, new String[]{getOkButton()}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showMessageDialog(Project, String, String, Icon)
   * @see #showMessageDialog(Component, String, String, Icon)
   */
  public static void showMessageDialog(@DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, getOkButton(), null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(message, title, new String[]{getOkButton()}, 0, icon);
  }

  /** @deprecated Use {@link MessageDialogBuilder#yesNo} */
  @Deprecated
  public static int showConfirmationDialog(@NotNull JComponent parent,
                                           @NotNull @DialogMessage String message,
                                           @NotNull @DialogTitle String title,
                                           @NotNull @NlsContexts.Button String yesText,
                                           @NotNull @NlsContexts.Button String noText) {
    return MessageDialogBuilder.yesNo(title, message)
      .yesText(yesText)
      .noText(noText)
      .ask(parent) ? YES : NO;
  }

  /** Use {@link MessageDialogBuilder#yesNo} */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    @NotNull @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @NotNull @NlsContexts.Button String yesText,
                                    @NotNull @NlsContexts.Button String noText,
                                    @Nullable Icon icon) {
    return MessageDialogBuilder.yesNo(title, message)
      .yesText(yesText)
      .noText(noText)
      .icon(icon)
      .ask(project) ? YES : NO;
  }

  /** @deprecated Use {@link MessageDialogBuilder#yesNo} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static int showYesNoDialog(@Nullable Project project,
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @NotNull @NlsContexts.Button String yesText,
                                    @NotNull @NlsContexts.Button String noText,
                                    @Nullable Icon icon,
                                    @Nullable DoNotAskOption doNotAskOption) {
    return MessageDialogBuilder.yesNo(title, message)
      .icon(icon)
      .doNotAsk(doNotAskOption)
      .yesText(yesText)
      .noText(noText)
      .ask(project) ? YES : NO;
  }

  /** Use {@link MessageDialogBuilder#yesNo} */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nullable Icon icon) {
    return MessageDialogBuilder.yesNo(title, message).icon(icon).ask(project) ? YES : NO;
  }

  /** @deprecated Use {@link MessageDialogBuilder#yesNo} */
  @Deprecated
  public static int showYesNoDialog(@Nullable Project project,
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nullable Icon icon,
                                    @Nullable DoNotAskOption doNotAskOption) {
    return MessageDialogBuilder.yesNo(title, message)
      .icon(icon)
      .doNotAsk(doNotAskOption)
      .ask(project) ? YES : NO;
  }

  /** Use {@link MessageDialogBuilder#yesNo} */
  @YesNoResult
  public static int showYesNoDialog(@NotNull Component parent,
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nullable Icon icon) {
    return MessageDialogBuilder.yesNo(title, message).icon(icon).ask(parent) ? YES : NO;
  }

  /** @deprecated Use {@link MessageDialogBuilder#yesNo} */
  @Deprecated
  public static int showYesNoDialog(@DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nls @NotNull @NlsContexts.Button String yesText,
                                    @Nls @NotNull @NlsContexts.Button String noText,
                                    @Nullable Icon icon,
                                    @Nullable DoNotAskOption doNotAskOption) {
    return MessageDialogBuilder.yesNo(title, message)
      .yesText(yesText)
      .noText(noText)
      .icon(icon)
      .doNotAsk(doNotAskOption)
      .guessWindowAndAsk() ? YES : NO;
  }

  /** Use {@link MessageDialogBuilder#yesNo} */
  @YesNoResult
  public static int showYesNoDialog(@DialogMessage String message,
                                    @DialogTitle String title,
                                    @NlsContexts.Button String yesText,
                                    @NlsContexts.Button String noText,
                                    @Nullable Icon icon) {
    return MessageDialogBuilder.yesNo(title, message)
      .yesText(yesText)
      .noText(noText)
      .icon(icon)
      .guessWindowAndAsk() ? YES : NO;
  }

  /** Use {@link MessageDialogBuilder#yesNo} */
  @YesNoResult
  public static int showYesNoDialog(@DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nullable Icon icon) {
    return MessageDialogBuilder.yesNo(title, message).icon(icon).guessWindowAndAsk() ? YES : NO;
  }

  /** Use {@link MessageDialogBuilder#yesNo} */
  @OkCancelResult
  public static int showOkCancelDialog(@Nullable Project project,
                                       @NotNull @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @NlsContexts.Button String okText,
                                       @NotNull @NlsContexts.Button String cancelText,
                                       @Nullable Icon icon,
                                       @Nullable DoNotAskOption doNotAskOption) {
    return MessageDialogBuilder.okCancel(title, message)
      .yesText(okText)
      .noText(cancelText)
      .icon(icon)
      .doNotAsk(doNotAskOption)
      .ask(project) ? OK : CANCEL;
  }

  /** Use {@link MessageDialogBuilder#yesNo} */
  @OkCancelResult
  public static int showOkCancelDialog(Project project,
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @NlsContexts.Button String okText,
                                       @NotNull @NlsContexts.Button String cancelText,
                                       @Nullable Icon icon) {
    return MessageDialogBuilder.okCancel(title, message)
      .yesText(okText)
      .noText(cancelText)
      .icon(icon)
      .ask(project) ? OK : CANCEL;
  }

  /** @deprecated Please provide meaningful action names via {@link #showOkCancelDialog(Project, String, String, String, String, Icon)} instead */
  @OkCancelResult
  @Deprecated
  public static int showOkCancelDialog(Project project,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       Icon icon) {
    return MessageDialogBuilder.okCancel(title, message).icon(icon).ask(project) ? OK : CANCEL;
  }

  /** Use {@link MessageDialogBuilder#yesNo} */
  @OkCancelResult
  public static int showOkCancelDialog(@NotNull Component parent,
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @NlsContexts.Button String okText,
                                       @NotNull @NlsContexts.Button String cancelText,
                                       Icon icon) {
    return MessageDialogBuilder.okCancel(title, message)
      .yesText(okText)
      .noText(cancelText)
      .icon(icon)
      .ask(parent) ? OK : CANCEL;
  }

  /** @deprecated Please provide meaningful action names via {@link #showOkCancelDialog(Component, String, String, String, String, Icon)} instead */
  @OkCancelResult
  @Deprecated
  public static int showOkCancelDialog(@NotNull Component parent,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon) {
    return MessageDialogBuilder.okCancel(title, message).icon(icon).ask(parent) ? OK : CANCEL;
  }

  /** @deprecated Please provide meaningful action names via {@link #showOkCancelDialog(String, String, String, String, Icon)} instead */
  @OkCancelResult
  @Deprecated
  public static int showOkCancelDialog(@DialogMessage String message, @DialogTitle String title, Icon icon) {
    return MessageDialogBuilder.okCancel(title, message).icon(icon).guessWindowAndAsk() ? OK : CANCEL;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   */
  @OkCancelResult
  public static int showOkCancelDialog(@DialogMessage String message,
                                       @DialogTitle String title,
                                       @NlsContexts.Button String okText,
                                       @NlsContexts.Button String cancelText,
                                       Icon icon) {
    return MessageDialogBuilder.okCancel(title, message).yesText(okText).noText(cancelText).icon(icon).guessWindowAndAsk() ? OK : CANCEL;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon, DoNotAskOption)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   */
  @OkCancelResult
  public static int showOkCancelDialog(@DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @NlsContexts.Button String okText,
                                       @NotNull @NlsContexts.Button String cancelText,
                                       Icon icon,
                                       @Nullable DoNotAskOption doNotAskOption) {
    return MessageDialogBuilder.okCancel(title, message)
      .yesText(okText)
      .noText(cancelText)
      .icon(icon)
      .doNotAsk(doNotAskOption)
      .guessWindowAndAsk() ? OK : CANCEL;
  }

  public static int showCheckboxOkCancelDialog(@DialogMessage String message,
                                               @DialogTitle String title,
                                               @NlsContexts.Checkbox String checkboxText,
                                               final boolean checked,
                                               final int defaultOptionIndex,
                                               final int focusedOptionIndex,
                                               Icon icon) {
    return showCheckboxMessageDialog(message, title, new String[]{getOkButton(), getCancelButton()}, checkboxText, checked, defaultOptionIndex,
                                     focusedOptionIndex, icon,
                                     (exitCode, cb) -> exitCode == -1 ? CANCEL : exitCode + (cb.isSelected() ? 1 : 0));
  }

  public static int showCheckboxMessageDialog(@DialogMessage String message,
                                              @DialogTitle String title,
                                              String @NotNull @NlsContexts.Button [] options,
                                              @NlsContexts.Checkbox String checkboxText,
                                              final boolean checked,
                                              final int defaultOptionIndex,
                                              final int focusedOptionIndex,
                                              Icon icon,
                                              final @Nullable PairFunction<? super Integer, ? super JCheckBox, Integer> exitFunc) {
    return MessagesService.getInstance()
      .showTwoStepConfirmationDialog(message, title, options, checkboxText, checked, defaultOptionIndex, focusedOptionIndex, icon,
                                     exitFunc);
  }

  public static int showTwoStepConfirmationDialog(@DialogMessage String message,
                                                  @DialogTitle String title,
                                                  @NlsContexts.Checkbox String checkboxText,
                                                  Icon icon) {
    return showCheckboxMessageDialog(message, title, new String[]{getOkButton()}, checkboxText, true, -1, -1, icon, null);
  }

  public static void showErrorDialog(@Nullable Project project,
                                     @DialogMessage String message,
                                     @NotNull @DialogTitle String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, getOkButton(), WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(project, message, title, new String[]{getOkButton()}, 0, getErrorIcon());
  }

  public static void showErrorDialog(@Nullable Component component,
                                     @DialogMessage String message,
                                     @NotNull @DialogTitle String title) {
    MessagesService.getInstance().showMessageDialog(null, component, message, title, new String[]{getOkButton()}, 0, 0, getErrorIcon(), null, false,
                                                    null);
  }

  public static void showErrorDialog(@NotNull Component component, @DialogMessage String message) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance()
          .showErrorDialog(CommonBundle.getErrorTitle(), message, getOkButton(), SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(component, message, CommonBundle.getErrorTitle(), new String[]{getOkButton()}, 0, getErrorIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showErrorDialog(Project, String, String)
   * @see #showErrorDialog(Component, String, String)
   */
  public static void showErrorDialog(@DialogMessage String message, @NotNull @DialogTitle String title) {
    showErrorDialog((Component)null, message, title);
  }

  public static void showWarningDialog(@Nullable Project project,
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, getOkButton(), WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(project, message, title, new String[]{getOkButton()}, 0, getWarningIcon());
  }

  public static void showWarningDialog(@NotNull Component component,
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, getOkButton(), SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(component, message, title, new String[]{getOkButton()}, 0, getWarningIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showWarningDialog(Project, String, String)
   * @see #showWarningDialog(Component, String, String)
   */
  public static void showWarningDialog(@DialogMessage String message,
                                       @NotNull @DialogTitle String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, getOkButton(), null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(message, title, new String[]{getOkButton()}, 0, getWarningIcon());
  }

  /**
   * Use {@link MessageDialogBuilder#yesNoCancel}
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@Nullable Project project,
                                          @DialogMessage String message,
                                          @NotNull @DialogTitle String title,
                                          @NotNull @NlsContexts.Button String yes,
                                          @NotNull @NlsContexts.Button String no,
                                          @NotNull @NlsContexts.Button String cancel,
                                          @Nullable Icon icon) {
    return MessageDialogBuilder.yesNoCancel(title, message)
      .yesText(yes)
      .noText(no)
      .cancelText(cancel)
      .icon(icon)
      .show(project);
  }

  /**
   * Use {@link MessageDialogBuilder#yesNoCancel}
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@Nullable Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          @Nullable Icon icon) {
    return MessageDialogBuilder.yesNoCancel(title, message).icon(icon).show(project);
  }

  /**
   * Use {@link MessageDialogBuilder#yesNoCancel}
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@NotNull Component parent,
                                          @DialogMessage String message,
                                          @NotNull @DialogTitle String title,
                                          @NotNull @NlsContexts.Button String yes,
                                          @NotNull @NlsContexts.Button String no,
                                          @NotNull @NlsContexts.Button String cancel,
                                          @Nullable Icon icon) {
    return MessageDialogBuilder.yesNoCancel(title, message)
      .yesText(yes)
      .noText(no)
      .cancelText(cancel)
      .icon(icon)
      .show(parent);
  }

  /**
   * Use {@link MessageDialogBuilder#yesNoCancel}
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@NotNull Component parent,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          Icon icon) {
    return MessageDialogBuilder.yesNoCancel(title, message).icon(icon).show(parent);
  }

  /**
   * Use {@link MessageDialogBuilder#yesNoCancel}
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@DialogMessage String message,
                                          @NotNull @DialogTitle String title,
                                          @NotNull @NlsContexts.Button String yes,
                                          @NotNull @NlsContexts.Button String no,
                                          @NotNull @NlsContexts.Button String cancel,
                                          Icon icon,
                                          @Nullable DoNotAskOption doNotAskOption) {
    return MessageDialogBuilder.yesNoCancel(title, message)
      .yesText(yes)
      .noText(no)
      .cancelText(cancel)
      .icon(icon)
      .doNotAsk(doNotAskOption)
      .guessWindowAndAsk();
  }

  /**
   * Use {@link MessageDialogBuilder#yesNoCancel}
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@DialogMessage String message,
                                          @DialogTitle String title,
                                          @NlsContexts.Button String yes,
                                          @NlsContexts.Button String no,
                                          @NlsContexts.Button String cancel,
                                          Icon icon) {
    return MessageDialogBuilder.yesNoCancel(title, message)
      .yesText(yes)
      .noText(no)
      .cancelText(cancel)
      .icon(icon)
      .guessWindowAndAsk();
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, Icon)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@DialogMessage String message,
                                          @DialogTitle String title,
                                          @Nullable Icon icon) {
    return MessageDialogBuilder.yesNoCancel(title, message).icon(icon).guessWindowAndAsk();
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  public static @Nullable String showPasswordDialog(@DialogMessage String message, @DialogTitle String title) {
    return showPasswordDialog(null, message, title, null, null);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  public static @Nullable String showPasswordDialog(Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          @Nullable Icon icon) {
    return showPasswordDialog(project, message, title, icon, null);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  public static @Nullable String showPasswordDialog(@Nullable Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          @Nullable Icon icon,
                                          @Nullable InputValidator validator) {
    return MessagesService.getInstance().showPasswordDialog(project, message, title, icon, validator);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  public static @Nullable @NlsSafe String showInputDialog(@Nullable Project project,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon) {
    return showInputDialog(project, message, title, icon, null, null);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  public static @Nullable @NlsSafe String showInputDialog(@NotNull Component parent,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon) {
    return showInputDialog(parent, message, title, icon, null, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon)
   * @see #showInputDialog(Component, String, String, Icon)
   */
  public static @Nullable @NlsSafe String showInputDialog(@DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon) {
    return showInputDialog(message, title, icon, null, null);
  }

  public static @Nullable @NlsSafe String showInputDialog(@Nullable Project project,
                                                          @DialogMessage String message,
                                                          @DialogTitle String title,
                                                          @Nullable Icon icon,
                                                          @Nullable @NonNls String initialValue,
                                                          @Nullable InputValidator validator) {
    return MessagesService.getInstance().showInputDialog(project, null, message, title, icon, initialValue, validator, null, null);
  }

  public static @Nullable @NlsSafe String showInputDialog(Project project,
                                                          @DialogMessage String message,
                                                          @DialogTitle String title,
                                                          @Nullable Icon icon,
                                                          @Nullable @NonNls String initialValue,
                                                          @Nullable InputValidator validator,
                                                          @Nullable TextRange selection) {
    return showInputDialog(project, message, title, icon, initialValue, validator, selection, null);

  }

  public static @Nullable @NlsSafe String showInputDialog(Project project,
                                                          @DialogMessage String message,
                                                          @DialogTitle String title,
                                                          @Nullable Icon icon,
                                                          @Nullable @NonNls String initialValue,
                                                          @Nullable InputValidator validator,
                                                          @Nullable TextRange selection,
                                                          @Nullable @DetailedDescription String comment) {
    return MessagesService.getInstance().showInputDialog(project, null, message, title, icon, initialValue, validator, selection, comment);
  }

  public static @Nullable @NlsSafe String showInputDialog(@NotNull Component parent,
                                                          @DialogMessage String message,
                                                          @DialogTitle String title,
                                                          @Nullable Icon icon,
                                                          @Nullable @NonNls String initialValue,
                                                          @Nullable InputValidator validator) {
    return MessagesService.getInstance().showInputDialog(null, parent, message, title, icon, initialValue, validator, null, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Icon, String, InputValidator)
   */
  public static @Nullable @NlsSafe String showInputDialog(@DialogMessage String message,
                                                @DialogTitle String title,
                                                @Nullable Icon icon,
                                                @Nullable @NonNls String initialValue,
                                                @Nullable InputValidator validator) {
    return MessagesService.getInstance().showInputDialog(null, null, message, title, icon, initialValue, validator, null, null);
  }

  public static @Nullable @NlsSafe String showMultilineInputDialog(Project project,
                                                                   @DialogMessage String message,
                                                                   @DialogTitle String title,
                                                                   @Nullable @NonNls String initialValue,
                                                                   @Nullable Icon icon,
                                                                   @Nullable InputValidator validator) {
    return MessagesService.getInstance().showMultilineInputDialog(project, message, title, initialValue, icon, validator);
  }

  public static @NotNull Pair<String, Boolean> showInputDialogWithCheckBox(@DialogMessage String message,
                                                                           @DialogTitle String title,
                                                                           @NlsContexts.Checkbox String checkboxText,
                                                                           boolean checked,
                                                                           boolean checkboxEnabled,
                                                                           @Nullable Icon icon,
                                                                           @Nullable @NonNls String initialValue,
                                                                           @Nullable InputValidator validator) {
    return MessagesService.getInstance()
      .showInputDialogWithCheckBox(message, title, checkboxText, checked, checkboxEnabled, icon, initialValue, validator);
  }

  public static @Nullable String showEditableChooseDialog(@DialogMessage String message,
                                                          @DialogTitle String title,
                                                          @Nullable Icon icon,
                                                          String[] values,
                                                          String initialValue,
                                                          @Nullable InputValidator validator) {
    return MessagesService.getInstance().showEditableChooseDialog(message, title, icon, values, initialValue, validator);
  }

  /**
   * @deprecated It looks awful!
   */
  @Deprecated
  public static int showChooseDialog(@DialogMessage String message,
                                     @DialogTitle String title,
                                     String[] values,
                                     String initialValue,
                                     @Nullable Icon icon) {
    return MessagesService.getInstance().showChooseDialog(null, null, message, title, values, initialValue, icon);
  }

  /**
   * @deprecated It looks awful!
   */
  @Deprecated
  public static int showChooseDialog(Project project,
                                     @DialogMessage String message,
                                     @DialogTitle String title,
                                     Icon icon,
                                     String[] values,
                                     String initialValue) {
    return MessagesService.getInstance().showChooseDialog(project, null, message, title, values, initialValue, icon);
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(Component component,
                                     @DialogMessage String message,
                                     @NotNull @DialogTitle String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, getOkButton(), SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showMessageDialog(component, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(@Nullable Project project,
                                     @DialogMessage String message,
                                     @NotNull @DialogTitle String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, getOkButton(), WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showMessageDialog(project, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   * <p/>
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Icon, String, InputValidator)
   */
  public static void showInfoMessage(@DialogMessage String message,
                                     @NotNull @DialogTitle String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, getOkButton(), null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showMessageDialog(message, title, getInformationIcon());
  }

  /**
   * Shows dialog with text area to edit long strings that don't fit in text field.
   */
  public static void showTextAreaDialog(final JTextField textField,
                                        final @DialogTitle String title,
                                        final @NonNls String dimensionServiceKey,
                                        final Function<? super String, ? extends List<String>> parser,
                                        final Function<? super List<String>, String> lineJoiner) {
    MessagesService.getInstance().showTextAreaDialog(textField, title, dimensionServiceKey, parser, lineJoiner);
  }

  public static void showTextAreaDialog(final JTextField textField,
                                        final @DialogTitle String title,
                                        final @NonNls String dimensionServiceKey) {
    showTextAreaDialog(textField, title, dimensionServiceKey, ParametersListUtil.DEFAULT_LINE_PARSER,
                       ParametersListUtil.DEFAULT_LINE_JOINER);
  }
}
