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
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.mac.MacMessages;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Messages {
  public static final int OK = 0;
  public static final int YES = 0;
  public static final int NO = 1;
  public static final int CANCEL = 2;

  public static final String OK_BUTTON = CommonBundle.getOkButtonText();
  public static final String YES_BUTTON = CommonBundle.getYesButtonText();
  public static final String NO_BUTTON = CommonBundle.getNoButtonText();
  public static final String CANCEL_BUTTON = CommonBundle.getCancelButtonText();

  private static TestDialog ourTestImplementation = TestDialog.DEFAULT;
  private static TestInputDialog ourTestInputImplementation = TestInputDialog.DEFAULT;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.Messages");

  @TestOnly
  public static TestDialog setTestDialog(TestDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      LOG.assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
    TestDialog oldValue = ourTestImplementation;
    ourTestImplementation = newValue;
    return oldValue;
  }

  @TestOnly
  public static TestInputDialog setTestInputDialog(TestInputDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      LOG.assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
    TestInputDialog oldValue = ourTestInputImplementation;
    ourTestInputImplementation = newValue;
    return oldValue;
  }

  @NotNull
  public static Icon getErrorIcon() {
    return UIUtil.getErrorIcon();
  }

  @NotNull
  public static Icon getInformationIcon() {
    return UIUtil.getInformationIcon();
  }

  @NotNull
  public static Icon getWarningIcon() {
    return UIUtil.getWarningIcon();
  }

  @NotNull
  public static Icon getQuestionIcon() {
    return UIUtil.getQuestionIcon();
  }

  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project,
                               String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon) {
    return showDialog(project, message, title, options, defaultOptionIndex, icon, null);
  }

  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project,
                               String message,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }

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
    catch (Exception reportThis) {LOG.error(reportThis);}

    return showIdeaMessageDialog(project, message, title, options, defaultOptionIndex, icon, doNotAskOption);
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showIdeaMessageDialog(@Nullable Project project,
                                          String message,
                                          @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull String[] options,
                                          int defaultOptionIndex,
                                          @Nullable Icon icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    MessageDialog dialog = new MessageDialog(project, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption, false);
    dialog.show();
    return dialog.getExitCode();
  }

  public static boolean canShowMacSheetPanel() {
    return SystemInfo.isMac
           && !isApplicationInUnitTestOrHeadless()
           && Registry.is("ide.mac.message.dialogs.as.sheets");
           //&& !DialogWrapper.isMultipleModalDialogs();
  }

  public static boolean isMacSheetEmulation() {
    return SystemInfo.isMac && Registry.is("ide.mac.message.dialogs.as.sheets") && Registry.is("ide.mac.message.sheets.java.emulation");
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(Project project,
                               String message,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @Nullable String moreInfo,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }

    try {
      if (canShowMacSheetPanel() && moreInfo == null) {
        return MacMessages.getInstance()
          .showMessageDialog(title, message, options, false, WindowManager.getInstance().suggestParentWindow(project), defaultOptionIndex,
                             focusedOptionIndex, null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    MessageDialog dialog = new MoreInfoMessageDialog(project, message, title, moreInfo, options, defaultOptionIndex, focusedOptionIndex, icon);
    dialog.show();
    return dialog.getExitCode();
  }

  static boolean isApplicationInUnitTestOrHeadless(){
    final Application application = ApplicationManager.getApplication();
    return application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment());
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@NotNull Component parent, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, @Nullable Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      try {
        if (canShowMacSheetPanel()) {
          return MacMessages.getInstance().showMessageDialog(title, message, options, false, SwingUtilities.getWindowAncestor(parent),
                                                             defaultOptionIndex, defaultOptionIndex, null);
        }
      }
      catch (MessageException ignored) {/*rollback the message and show a dialog*/}
      catch (Exception reportThis) {LOG.error(reportThis);}

      MessageDialog dialog = new MessageDialog(parent, message, title, options, defaultOptionIndex, defaultOptionIndex, icon, false);
      dialog.show();
      return dialog.getExitCode();
    }
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showDialog(Project, String, String, String[], int, Icon, DialogWrapper.DoNotAskOption)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(String message,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showMessageDialog(title, message, options, false, null, defaultOptionIndex, focusedOptionIndex,
                                                           doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    //what's it? if (application.isUnitTestMode()) throw new RuntimeException(message);
    MessageDialog dialog = new MessageDialog(message, title, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    dialog.show();
    return dialog.getExitCode();
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, @Nullable Icon icon, @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return showDialog(message, title, options, defaultOptionIndex, defaultOptionIndex, icon, doNotAskOption);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, @Nullable Icon icon) {
    return showDialog(message, title, options, defaultOptionIndex, icon, null);
  }

  /**
   * @see DialogWrapper#DialogWrapper(Project,boolean)
   */
  public static void showMessageDialog(@Nullable Project project, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  public static void showMessageDialog(@NotNull Component parent, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(parent));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(parent, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showMessageDialog(Project, String, String, Icon)
   * @see #showMessageDialog(Component, String, String, Icon)
   */
  public static void showMessageDialog(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  @MagicConstant(intValues = {YES, NO})
  public @interface YesNoResult {
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String yesText, @NotNull String noText, @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, yesText, noText, WindowManager.getInstance().suggestParentWindow(project));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int result = showDialog(project, message, title, new String[]{yesText, noText}, 0, icon) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @NotNull String yesText,
                                    @NotNull String noText,
                                    @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, yesText, noText, WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int result = showDialog(project, message, title, new String[]{yesText, noText}, 0, icon, doNotAskOption) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON,
                                                         WindowManager.getInstance().suggestParentWindow(project));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int result = showYesNoDialog(project, message, title, YES_BUTTON, NO_BUTTON, icon);

    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON,
                                                         WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int result = showYesNoDialog(project, message, title, YES_BUTTON, NO_BUTTON, icon, doNotAskOption);

    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }


  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@NotNull Component parent, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, SwingUtilities.getWindowAncestor(parent));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int result = showDialog(parent, message, title, new String[]{YES_BUTTON, NO_BUTTON}, 0, icon) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoDialog(Project, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, Icon)
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String yesText, @NotNull String noText, @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, null, doNotAskOption);
      }
    }
    catch (MessageException messageException) {
      // just show a dialog instead
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    int result = showDialog(message, title, new String[]{yesText, noText}, 0, icon, doNotAskOption) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoDialog(Project, String, String, String, String, Icon)
   * @see #showYesNoDialog(Component, String, String, Icon)
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, String yesText, String noText, @Nullable Icon icon) {
    return showYesNoDialog(message, title, yesText, noText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoDialog(Project, String, String, Icon)
   * @see #showYesNoDialog(Component, String, String, Icon)
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int result = showYesNoDialog(message, title, YES_BUTTON, NO_BUTTON, icon);
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  @MagicConstant(intValues = {OK, CANCEL})
  public @interface OkCancelResult {
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project,
                                       String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @NotNull String okText,
                                       @NotNull String cancelText,
                                       Icon icon,
                                       DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance()
          .showYesNoDialog(title, message, okText, cancelText, WindowManager.getInstance().suggestParentWindow(project),
                           doNotAskOption);
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    return showDialog(project, message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption) == 0 ? OK : CANCEL;
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String okText, @NotNull String cancelText, Icon icon) {
    return showOkCancelDialog(project, message, title, okText, cancelText, icon, null);
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon) {
    return showOkCancelDialog(project, message, title, OK_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(@NotNull Component parent, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String okText, @NotNull String cancelText, Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        int result =
          MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, SwingUtilities.getWindowAncestor(parent));
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    return showDialog(parent, message, title, new String[]{okText, cancelText}, 0, icon) == 0 ? OK : CANCEL;
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(@NotNull Component parent, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon) {
    return showOkCancelDialog(parent, message, title, OK_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showOkCancelDialog(Project, String, String, Icon)
   * @see #showOkCancelDialog(Component, String, String, Icon)
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon) {
    return showOkCancelDialog(message, title, OK_BUTTON, CANCEL_BUTTON, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, String okText, String cancelText, Icon icon) {
    return showOkCancelDialog(message, title, okText, cancelText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon, DialogWrapper.DoNotAskOption)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String okText, @NotNull String cancelText, Icon icon, @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, null, doNotAskOption);
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    return showDialog(message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption) == 0 ? OK : CANCEL;
  }

  public static int showCheckboxOkCancelDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, String checkboxText, final boolean checked,
                                               final int defaultOptionIndex, final int focusedOptionIndex, Icon icon) {
    return showCheckboxMessageDialog(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, checkboxText, checked, defaultOptionIndex,
                                     focusedOptionIndex, icon,
                                     (exitCode, cb) -> exitCode == -1 ? CANCEL : exitCode + (cb.isSelected() ? 1 : 0));
  }

  public static int showCheckboxMessageDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, String checkboxText, final boolean checked,
                                              final int defaultOptionIndex, final int focusedOptionIndex, Icon icon,
                                              @Nullable final PairFunction<Integer, JCheckBox, Integer> exitFunc) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      TwoStepConfirmationDialog dialog = new TwoStepConfirmationDialog(message, title, options, checkboxText, checked, defaultOptionIndex,
                                                                       focusedOptionIndex, icon, exitFunc);
      dialog.show();
      return dialog.getExitCode();
    }
  }


  public static int showTwoStepConfirmationDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, String checkboxText, Icon icon) {
    return showCheckboxMessageDialog(message, title, new String[]{OK_BUTTON}, checkboxText, true, -1, -1, icon, null);
  }

  public static void showErrorDialog(@Nullable Project project, @Nls String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(@NotNull Component component, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(@NotNull Component component, String message) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(CommonBundle.getErrorTitle(), message, OK_BUTTON, SwingUtilities.getWindowAncestor(
          component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(component, message, CommonBundle.getErrorTitle(), new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showErrorDialog(Project, String, String)
   * @see #showErrorDialog(Component, String, String)
   */
  public static void showErrorDialog(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showWarningDialog(@Nullable Project project, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  public static void showWarningDialog(@NotNull Component component, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showWarningDialog(Project, String, String)
   * @see #showWarningDialog(Component, String, String)
   */
  public static void showWarningDialog(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showDialog(message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  @MagicConstant(intValues = {YES, NO, CANCEL})
  public @interface YesNoCancelResult {
  }


  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(Project project,
                                          String message,
                                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull String yes,
                                          @NotNull String no,
                                          @NotNull String cancel,
                                          @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel,
                                                               WindowManager.getInstance().suggestParentWindow(project), null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int buttonNumber = showDialog(project, message, title, new String[]{yes, no, cancel}, 0, icon);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(Project project, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon) {
    return showYesNoCancelDialog(project, message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@NotNull Component parent,
                                          String message,
                                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull String yes,
                                          @NotNull String no,
                                          @NotNull String cancel,
                                          Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel,
                                                               SwingUtilities.getWindowAncestor(parent), null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int buttonNumber = showDialog(parent, message, title, new String[]{yes, no, cancel}, 0, icon);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@NotNull Component parent, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon) {
    return showYesNoCancelDialog(parent, message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }


  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Icon)
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message,
                                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull String yes,
                                          @NotNull String no,
                                          @NotNull String cancel,
                                          Icon icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel, null, doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    int buttonNumber = showDialog(message, title, new String[]{yes, no, cancel}, 0, icon, doNotAskOption);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Icon)
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, String yes, String no, String cancel, Icon icon) {
    return showYesNoCancelDialog(message, title, yes, no, cancel, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoCancelDialog(Project, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, Icon)
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon) {
    return showYesNoCancelDialog(message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nls String message, @Nls(capitalization = Nls.Capitalization.Title) String title) {
    return showPasswordDialog(null, message, title, null, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(Project project, @Nls String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    return showPasswordDialog(project, message, title, icon, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nullable Project project,
                                          @Nls String message,
                                          @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @Nullable Icon icon,
                                          @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message, validator);
    }

    final InputDialog dialog = project != null ? new PasswordInputDialog(project, message, title, icon, validator)
                                               : new PasswordInputDialog(message, title, icon, validator);
    dialog.show();
    return dialog.getInputString();
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(@Nullable Project project, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    return showInputDialog(project, message, title, icon, null, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(@NotNull Component parent, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    return showInputDialog(parent, message, title, icon, null, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon)
   * @see #showInputDialog(Component, String, String, Icon)
   */
  @Nullable
  public static String showInputDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    return showInputDialog(message, title, icon, null, null);
  }

  @Nullable
  public static String showInputDialog(@Nullable Project project,
                                       @Nls String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message, validator);
    }
    else {
      InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator);
      dialog.show();
      return dialog.getInputString();
    }
  }

  @Nullable
  public static String showInputDialog(Project project,
                                       @Nls String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator,
                                       @Nullable TextRange selection) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message, validator);
    }
    else {
      InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator);

      final JTextComponent field = dialog.getTextField();
      if (selection != null) {
        // set custom selection
        field.select(selection.getStartOffset(),
                     selection.getEndOffset());
      } else {
        // reset selection
        final int length = field.getDocument().getLength();
        field.select(length, length);
      }
      field.putClientProperty(DialogWrapperPeer.HAVE_INITIAL_SELECTION, true);

      dialog.show();
      return dialog.getInputString();
    }
  }

  @Nullable
  public static String showInputDialog(@NotNull Component parent,
                                       String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message, validator);
    }
    else {

      InputDialog dialog = new InputDialog(parent, message, title, icon, initialValue, validator);
      dialog.show();
      return dialog.getInputString();
    }
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Icon, String, InputValidator)
   */
  @Nullable
  public static String showInputDialog(String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message, validator);
    }
    else {
      InputDialog dialog = new InputDialog(message, title, icon, initialValue, validator);
      dialog.show();
      return dialog.getInputString();
    }
  }

  @Nullable
  public static String showMultilineInputDialog(Project project,
                                                String message,
                                                @Nls(capitalization = Nls.Capitalization.Title) String title,
                                                @Nullable String initialValue,
                                                @Nullable Icon icon,
                                                @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message, validator);
    }
    InputDialog dialog = new MultilineInputDialog(project, message, title, icon, initialValue, validator, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    dialog.show();
    return dialog.getInputString();
  }

  @NotNull
  public static Pair<String, Boolean> showInputDialogWithCheckBox(String message,
                                                                  @Nls(capitalization = Nls.Capitalization.Title) String title,
                                                                  String checkboxText,
                                                                  boolean checked,
                                                                  boolean checkboxEnabled,
                                                                  @Nullable Icon icon,
                                                                  @NonNls String initialValue,
                                                                  @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return new Pair<>(ourTestInputImplementation.show(message, validator), checked);
    }
    else {
      InputDialogWithCheckbox dialog = new InputDialogWithCheckbox(message, title, checkboxText, checked, checkboxEnabled, icon, initialValue, validator);
      dialog.show();
      return Pair.create(dialog.getInputString(), dialog.isChecked());
    }
  }

  @Nullable
  public static String showEditableChooseDialog(String message,
                                                @Nls(capitalization = Nls.Capitalization.Title) String title,
                                                @Nullable Icon icon,
                                                String[] values,
                                                String initialValue,
                                                @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message, validator);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
      dialog.setValidator(validator);
      dialog.getComboBox().setEditable(true);
      dialog.getComboBox().getEditor().setItem(initialValue);
      dialog.getComboBox().setSelectedItem(initialValue);
      dialog.show();
      return dialog.getInputString();
    }
  }

  /** @deprecated It looks awful! */
  @Deprecated
  public static int showChooseDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, String[] values, String initialValue, @Nullable Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
      dialog.show();
      return dialog.getSelectedIndex();
    }
  }

  /** @deprecated It looks awful! */
  @Deprecated
  public static int showChooseDialog(@NotNull Component parent, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, String[] values, String initialValue, Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(parent, message, title, icon, values, initialValue);
      dialog.show();
      return dialog.getSelectedIndex();
    }
  }

  /**
   * @deprecated It looks awful!
   * @see DialogWrapper#DialogWrapper(Project,boolean)
   */
  @Deprecated
  public static int showChooseDialog(Project project, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon, String[] values, String initialValue) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(project, message, title, icon, values, initialValue);
      dialog.show();
      return dialog.getSelectedIndex();
    }
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(Component component, String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showMessageDialog(component, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(@Nullable Project project, @Nls String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showMessageDialog(project, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   *
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Icon, String, InputValidator)
   */
  public static void showInfoMessage(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {LOG.error(reportThis);}

    showMessageDialog(message, title, getInformationIcon());
  }

  /**
   * Shows dialog with text area to edit long strings that don't fit in text field.
   */
  public static void showTextAreaDialog(final JTextField textField,
                                        final @Nls(capitalization = Nls.Capitalization.Title) String title,
                                        @NonNls final String dimensionServiceKey,
                                        final Function<String, List<String>> parser,
                                        final Function<List<String>, String> lineJoiner) {
    if (isApplicationInUnitTestOrHeadless()) {
      ourTestImplementation.show(title);
    }
    else {
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

  public static void showTextAreaDialog(final JTextField textField, final @Nls(capitalization = Nls.Capitalization.Title) String title, @NonNls final String dimensionServiceKey) {
    showTextAreaDialog(textField, title, dimensionServiceKey, ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  private static class MoreInfoMessageDialog extends MessageDialog {
    @Nullable private final String myInfoText;

    public MoreInfoMessageDialog(Project project,
                                 String message,
                                 @Nls(capitalization = Nls.Capitalization.Title) String title,
                                 @Nullable String moreInfo,
                                 @NotNull String[] options,
                                 int defaultOptionIndex, int focusedOptionIndex, Icon icon) {
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

    public MessageDialog(@Nullable Project project, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, @Nullable Icon icon, boolean canBeParent) {
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

    public MessageDialog(@Nullable Project project, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Icon icon,
                         boolean canBeParent) {
      super(project, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    public MessageDialog(@NotNull Component parent, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, @Nullable Icon icon) {
      this(parent, message, title, options, defaultOptionIndex, icon, false);
    }

    public MessageDialog(@NotNull Component parent, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, @Nullable Icon icon, boolean canBeParent) {
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

    public MessageDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, @Nullable Icon icon) {
      this(message, title, options, defaultOptionIndex, icon, false);
    }

    public MessageDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, @Nullable Icon icon, boolean canBeParent) {
      super(canBeParent);
      _init(title, message, options, defaultOptionIndex, -1, icon, null);
    }

    public MessageDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Icon icon, @Nullable DoNotAskOption doNotAskOption) {
      super(false);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    }

    public MessageDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, int defaultOptionIndex, Icon icon, DoNotAskOption doNotAskOption) {
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
      if (isMacSheetEmulation()) {
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
      if (isMacSheetEmulation()) {
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
      return isMacSheetEmulation() ? myLayout = new MyBorderLayout() : super.createRootLayout();
    }

    @Override
    protected void dispose() {
      if (isMacSheetEmulation()) {
        animate();
      } else {
        super.dispose();
      }
    }

    @Override
    public void show() {
      if (isMacSheetEmulation()) {
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
        JTextPane messageComponent = createMessageComponent(myMessage);
        panel.add(wrapToScrollPaneIfNeeded(messageComponent, 100, 10), BorderLayout.CENTER);
      }
      return panel;
    }

    protected static JTextPane createMessageComponent(final String message) {
      final JTextPane messageComponent = new JTextPane();
      return configureMessagePaneUi(messageComponent, message);
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

  public static void installHyperlinkSupport(JTextPane messageComponent) {
    configureMessagePaneUi(messageComponent, "<html></html>");
  }

  @NotNull
  public static JTextPane configureMessagePaneUi(JTextPane messageComponent, String message) {
    JTextPane pane = configureMessagePaneUi(messageComponent, message, null);
    if (UIUtil.HTML_MIME.equals(pane.getContentType())) {
      pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    }
    return pane;
  }

  @NotNull
  public static JTextPane configureMessagePaneUi(@NotNull JTextPane messageComponent,
                                                 @Nullable String message,
                                                 @Nullable UIUtil.FontSize fontSize) {
    UIUtil.FontSize fixedFontSize = fontSize == null ? UIUtil.FontSize.NORMAL : fontSize;
    messageComponent.setFont(UIUtil.getLabelFont(fixedFontSize));
    if (BasicHTML.isHTMLString(message)) {
      HTMLEditorKit editorKit = new HTMLEditorKit();
      Font font = UIUtil.getLabelFont(fixedFontSize);
      editorKit.getStyleSheet().addRule(UIUtil.displayPropertiesToCSS(font, UIUtil.getLabelForeground()));
      messageComponent.setEditorKit(editorKit);
      messageComponent.setContentType(UIUtil.HTML_MIME);
    }
    messageComponent.setText(message);
    messageComponent.setEditable(false);
    if (messageComponent.getCaret() != null) {
      messageComponent.setCaretPosition(0);
    }

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      messageComponent.setOpaque(false);
      messageComponent.setBackground(UIUtil.TRANSPARENT_COLOR);
    }
    else {
      messageComponent.setBackground(UIUtil.getOptionPaneBackground());
    }

    messageComponent.setForeground(UIUtil.getLabelForeground());
    return messageComponent;
  }

  @NotNull
  public static JComponent wrapToScrollPaneIfNeeded(@NotNull JComponent comp, int columns, int lines) {
    float fontSize = comp.getFont().getSize2D();
    Dimension maxDim = new Dimension((int)(fontSize * columns), (int)(fontSize * lines));
    Dimension prefDim = comp.getPreferredSize();
    if (prefDim.width <= maxDim.width && prefDim.height <= maxDim.height) return comp;

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(comp);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    int barWidth = UIUtil.getScrollBarWidth();
    Dimension preferredSize = new Dimension(
      Math.min(prefDim.width, maxDim.width) + barWidth,
      Math.min(prefDim.height, maxDim.height) + barWidth);
    if (prefDim.width > maxDim.width) { //Too wide single-line message should be wrapped
      preferredSize.height = Math.max(preferredSize.height, (int)(4 * fontSize) + barWidth);
    }
    scrollPane.setPreferredSize(preferredSize);
    return scrollPane;
  }

  protected static class TwoStepConfirmationDialog extends MessageDialog {
    private JCheckBox myCheckBox;
    private final String myCheckboxText;
    private final boolean myChecked;
    private final PairFunction<Integer, JCheckBox, Integer> myExitFunc;

    public TwoStepConfirmationDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull String[] options, String checkboxText, boolean checked, final int defaultOptionIndexed,
                                     final int focusedOptionIndex, Icon icon, @Nullable final PairFunction<Integer, JCheckBox, Integer> exitFunc) {
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

  protected static class InputDialog extends MessageDialog {
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
                setErrorText(((InputValidatorEx) myValidator).getErrorText(text));
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
      if (myValidator == null ||
          myValidator.checkInput(inputString) &&
          myValidator.canClose(inputString)) {
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

  protected static class MultilineInputDialog extends InputDialog {
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

  protected static class PasswordInputDialog extends InputDialog {
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

  protected static class InputDialogWithCheckbox extends InputDialog {
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

  /** It looks awful! */
  @Deprecated
  protected static class ChooseDialog extends MessageDialog {
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

    public ChooseDialog(Project project, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon, String[] values, String initialValue) {
      this(project, message, title, icon, values, initialValue, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public ChooseDialog(@NotNull Component parent, String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon, String[] values, String initialValue) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon, String[] values, String initialValue) {
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
          myComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim()));
            }
          });
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
      if (myValidator == null ||
          myValidator.checkInput(inputString) &&
          myValidator.canClose(inputString)) {
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
