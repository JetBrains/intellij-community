/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.mac.MacMessages;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class Messages {
  public static final int OK = 0;
  public static final int YES = 0;
  public static final int NO = 1;
  public static final int CANCEL = 2;

  private static TestDialog ourTestImplementation = TestDialog.DEFAULT;
  private static TestInputDialog ourTestInputImplementation = TestInputDialog.DEFAULT;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.Messages");
  protected static final String OK_BUTTON = CommonBundle.getOkButtonText();
  protected static final String YES_BUTTON = CommonBundle.getYesButtonText();
  protected static final String NO_BUTTON = CommonBundle.getNoButtonText();
  protected static final String CANCEL_BUTTON = CommonBundle.getCancelButtonText();

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

  public static Icon getErrorIcon() {
    return UIUtil.getErrorIcon();
  }

  public static Icon getInformationIcon() {
    return UIUtil.getInformationIcon();
  }

  public static Icon getWarningIcon() {
    return UIUtil.getWarningIcon();
  }

  public static Icon getQuestionIcon() {
    return UIUtil.getQuestionIcon();
  }

  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!  
   */
  public static int showDialog(@Nullable Project project, String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon) {
    return showDialog(project, message, title, options, defaultOptionIndex, icon, null);
  }

  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!  
   */
  public static int showDialog(@Nullable Project project, String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }

    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance()
        .showMessageDialog(title, message, options, false, WindowManager.getInstance().suggestParentWindow(project), defaultOptionIndex, defaultOptionIndex, doNotAskOption);
    }

    return showIdeaMessageDialog(project, message, title, options, defaultOptionIndex, icon, doNotAskOption);
  }

  public static int showIdeaMessageDialog(@Nullable Project project, String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    MessageDialog dialog = new MessageDialog(project, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption, false);
    dialog.show();
    return dialog.getExitCode();
  }

  public static boolean canShowMacSheetPanel() {
    return SystemInfo.isMac
           && !isApplicationInUnitTestOrHeadless()
           && Registry.is("ide.mac.message.dialogs.as.sheets")
           && !DialogWrapper.isMultipleModalDialogs();
  }

  public static int showDialog(Project project, String message, String title, String moreInfo, String[] options, int defaultOptionIndex, int focusedOptionIndex, Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }

    if (canShowMacSheetPanel() && moreInfo == null) {
      return MacMessages.getInstance()
        .showMessageDialog(title, message, options, false, WindowManager.getInstance().suggestParentWindow(project), defaultOptionIndex, focusedOptionIndex, null);
    }

    MessageDialog dialog = new MoreInfoMessageDialog(project, message, title, moreInfo, options, defaultOptionIndex, focusedOptionIndex, icon);
    dialog.show();
    return dialog.getExitCode();
  }

  private static boolean isApplicationInUnitTestOrHeadless(){
    final Application application = ApplicationManager.getApplication();
    return (application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment()));
  }

  public static int showDialog(Component parent, String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showMessageDialog(title, message, options, false, SwingUtilities.getWindowAncestor(parent), defaultOptionIndex, defaultOptionIndex, null);
      }
  
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
   */
  public static int showDialog(String message, String title, String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Icon icon, @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showMessageDialog(title, message, options, false, null, defaultOptionIndex, focusedOptionIndex, doNotAskOption);
      }
  
      //what's it? if (application.isUnitTestMode()) throw new RuntimeException(message);
      MessageDialog dialog = new MessageDialog(message, title, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
      dialog.show();
      return dialog.getExitCode();
    }
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon, @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return showDialog(message, title, options, defaultOptionIndex, defaultOptionIndex, icon, doNotAskOption);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon) {
    return showDialog(message, title, options, defaultOptionIndex, icon, null);
  }

  /**
   * @see com.intellij.openapi.ui.DialogWrapper#DialogWrapper(Project,boolean)
   */
  public static void showMessageDialog(@Nullable Project project, String message, String title, @Nullable Icon icon) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
      return;
    }
    
    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  public static void showMessageDialog(Component parent, String message, String title, @Nullable Icon icon) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(parent));
      return;
    }
    
    showDialog(parent, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showMessageDialog(Project, String, String, Icon)
   * @see #showMessageDialog(Component, String, String, Icon)
   */
  public static void showMessageDialog(String message, String title, @Nullable Icon icon) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON);
      return;
    }
    
    showDialog(message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  /**
   * @return <code>0</code> if user pressed "Yes" and returns <code>1</code> if user pressed "No" button.
   */
  public static int showYesNoDialog(@Nullable Project project, String message, String title, String yesText, String noText, @Nullable Icon icon) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, WindowManager.getInstance().suggestParentWindow(project));
    }
    
    return showDialog(project, message, title, new String[]{yesText, noText}, 0, icon);
  }

  /**
   * @return <code>0</code> if user pressed "Yes" and returns <code>1</code> if user pressed "No" button.
   */
  public static int showYesNoDialog(@Nullable Project project, String message, String title, @Nullable Icon icon) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
    }
    
    return showYesNoDialog(project, message, title, YES_BUTTON, NO_BUTTON, icon);
  }

  /**
   * @return <code>0</code> if user pressed "Yes" and returns <code>1</code> if user pressed "No" button.
   */
  public static int showYesNoDialog(Component parent, String message, String title, @Nullable Icon icon) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, SwingUtilities.getWindowAncestor(parent));
    }
    
    return showDialog(parent, message, title, new String[]{YES_BUTTON, NO_BUTTON}, 0, icon);
  }


  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoDialog(Project, ...)
   * @see #showYesNoDialog(Component, ...)
   */
  public static int showYesNoDialog(String message, String title, String yesText, String noText, @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, null, doNotAskOption);
    }
    
    return showDialog(message, title, new String[]{yesText, noText}, 0, icon, doNotAskOption);
  }
  
  /**
   * Use this method only if you do not know project or component
   *
   * @return <code>0</code> if user pressed "Yes" and returns <code>1</code> if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, String, String, Icon)
   * @see #showYesNoDialog(Component, ...)
   */
  public static int showYesNoDialog(String message, String title, String yesText, String noText, @Nullable Icon icon) {
    return showYesNoDialog(message, title, yesText, noText, icon, null);
  }
  
  /**
   * Use this method only if you do not know project or component
   *
   * @return <code>0</code> if user pressed "Yes" and returns <code>1</code> if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, Icon)
   * @see #showYesNoDialog(Component, String, String, Icon)
   */
  public static int showYesNoDialog(String message, String title, @Nullable Icon icon) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, null);
    }

    return showYesNoDialog(message, title, YES_BUTTON, NO_BUTTON, icon);
  }

  public static int showOkCancelDialog(Project project, String message, String title, String okText, String cancelText, Icon icon,
                                       DialogWrapper.DoNotAskOption doNotAskOption) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, WindowManager.getInstance().suggestParentWindow(project),
                                         doNotAskOption);
    }
    
    return showDialog(project, message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption);
  }

  public static int showOkCancelDialog(Project project, String message, String title, String okText, String cancelText, Icon icon) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, WindowManager.getInstance().suggestParentWindow(project));
    }
    
    return showDialog(project, message, title, new String[]{okText, cancelText}, 0, icon);
  }

  public static int showOkCancelDialog(Project project, String message, String title, Icon icon) {
    return showOkCancelDialog(project, message, title, OK_BUTTON, CANCEL_BUTTON, icon);
  }

  public static int showOkCancelDialog(Component parent, String message, String title, String okText, String cancelText, Icon icon) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, SwingUtilities.getWindowAncestor(parent));
    }


    return showDialog(parent, message, title, new String[]{okText, cancelText}, 0, icon);
  }

  public static int showOkCancelDialog(Component parent, String message, String title, Icon icon) {
    return showOkCancelDialog(parent, message, title, OK_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showOkCancelDialog(Project, String, String, Icon)
   * @see #showOkCancelDialog(Component, String, String, Icon)
   */
  public static int showOkCancelDialog(String message, String title, Icon icon) {
    return showOkCancelDialog(message, title, OK_BUTTON, CANCEL_BUTTON, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   */
  public static int showOkCancelDialog(String message, String title, String okText, String cancelText, Icon icon) {
    return showOkCancelDialog(message, title, okText, cancelText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon, DialogWrapper.DoNotAskOption)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   */
  public static int showOkCancelDialog(String message, String title, String okText, String cancelText, Icon icon, @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, null, doNotAskOption);
    }
    
    return showDialog(message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption);
  }

  public static int showCheckboxOkCancelDialog(String message, String title, String checkboxText, final boolean checked,
                                                  final int defaultOptionIndex, final int focusedOptionIndex, Icon icon) {
    return showCheckboxMessageDialog(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, checkboxText, checked, defaultOptionIndex,
                                     focusedOptionIndex, icon,
                                     new PairFunction<Integer, JCheckBox, Integer>() {
                                       @Override
                                       public Integer fun(final Integer exitCode, final JCheckBox cb) {
                                         return exitCode == -1 ? CANCEL : exitCode + (cb.isSelected() ? 1 : 0);
                                       }
                                     });
  }

  public static int showCheckboxMessageDialog(String message, String title, String[] options, String checkboxText, final boolean checked,
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


  public static int showTwoStepConfirmationDialog(String message, String title, String checkboxText, Icon icon) {
    return showCheckboxMessageDialog(message, title, new String[]{OK_BUTTON}, checkboxText, true, -1, -1, icon, null);
  }

  public static void showErrorDialog(@Nullable Project project, @Nls String message, @Nls String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
      return;
    }

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(Component component, String message, @Nls String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
      return;
    }

    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(Component component, String message) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showErrorDialog(CommonBundle.getErrorTitle(), message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
      return;
    }
    
    showDialog(component, message, CommonBundle.getErrorTitle(), new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showErrorDialog(Project, String, String)
   * @see #showErrorDialog(Component, String, String)
   */
  public static void showErrorDialog(String message, String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showErrorDialog(CommonBundle.getErrorTitle(), message, OK_BUTTON, null);
      return;
    }

    showDialog(message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showWarningDialog(Project project, String message, String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showErrorDialog(CommonBundle.getWarningTitle(), message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
      return;
    }
    
    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  public static void showWarningDialog(Component component, String message, String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showErrorDialog(CommonBundle.getWarningTitle(), message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
      return;
    }

    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showWarningDialog(Project, String, String)
   * @see #showWarningDialog(Component, String, String)
   */
  public static void showWarningDialog(String message, String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showErrorDialog(CommonBundle.getWarningTitle(), message, OK_BUTTON, null);
      return;
    }
    
    showDialog(message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  public static int showYesNoCancelDialog(Project project, String message, String title, String yes, String no, String cancel, @Nullable Icon icon) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel,
                                               WindowManager.getInstance().suggestParentWindow(project), null);
    }

    return showDialog(project, message, title, new String[]{yes, no, cancel}, 0, icon);
  }


  public static int showYesNoCancelDialog(Project project, String message, String title, Icon icon) {
    return showYesNoCancelDialog(project, message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }

  public static int showYesNoCancelDialog(Component parent, String message, String title, String yes, String no, String cancel, Icon icon) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel,
                                               SwingUtilities.getWindowAncestor(parent), null);
    }

    return showDialog(parent, message, title, new String[]{yes, no, cancel}, 0, icon);
  }

  public static int showYesNoCancelDialog(Component parent, String message, String title, Icon icon) {
    return showYesNoCancelDialog(parent, message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }


  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Icon)
   */
  public static int showYesNoCancelDialog(String message, String title, String yes, String no, String cancel, Icon icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel, null, doNotAskOption);
    }
    
    return showDialog(message, title, new String[]{yes, no, cancel}, 0, icon, doNotAskOption);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Icon)
   */
  public static int showYesNoCancelDialog(String message, String title, String yes, String no, String cancel, Icon icon) {
    return showYesNoCancelDialog(message, title, yes, no, cancel, icon, null);
  }
  
  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoCancelDialog(Project, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, Icon)
   */
  public static int showYesNoCancelDialog(String message, String title, Icon icon) {
    return showYesNoCancelDialog(message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nls String message, @Nls String title) {
    return showPasswordDialog(null, message, title, null, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(Project project, @Nls String message, @Nls String title, @Nullable Icon icon) {
    return showPasswordDialog(project, message, title, icon, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nullable Project project,
                                          @Nls String message,
                                          @Nls String title,
                                          @Nullable Icon icon,
                                          @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
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
  public static String showInputDialog(@Nullable Project project, String message, String title, @Nullable Icon icon) {
    return showInputDialog(project, message, title, icon, null, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(Component parent, String message, String title, @Nullable Icon icon) {
    return showInputDialog(parent, message, title, icon, null, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon)
   * @see #showInputDialog(Component, String, String, Icon)
   */
  @Nullable
  public static String showInputDialog(String message, String title, @Nullable Icon icon) {
    return showInputDialog(message, title, icon, null, null);
  }

  @Nullable
  public static String showInputDialog(@Nullable Project project,
                                       @Nls String message,
                                       @Nls String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
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
                                       @Nls String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator,
                                       @Nullable TextRange selection) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
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
  public static String showInputDialog(Component parent,
                                       String message,
                                       String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
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
                                       String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
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
                                                String title,
                                                @Nullable String initialValue,
                                                @Nullable Icon icon,
                                                @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    InputDialog dialog = new MultilineInputDialog(project, message, title, icon, initialValue, validator, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    dialog.show();
    return dialog.getInputString();
  }

  @NotNull
  public static Pair<String, Boolean> showInputDialogWithCheckBox(String message,
                                                   String title,
                                                   String checkboxText,
                                                   boolean checked,
                                                   boolean checkboxEnabled,
                                                   @Nullable Icon icon,
                                                   @NonNls String initialValue,
                                                   @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return new Pair<String, Boolean>(ourTestInputImplementation.show(message), checked);
    }
    else {
      InputDialogWithCheckbox dialog = new InputDialogWithCheckbox(message, title, checkboxText, checked, checkboxEnabled, icon, initialValue, validator);
      dialog.show();
      return new Pair<String, Boolean>(dialog.getInputString(), dialog.isChecked());
    }
  }

  @Nullable
  public static String showEditableChooseDialog(String message,
                                                String title,
                                                @Nullable Icon icon,
                                                String[] values,
                                                String initialValue,
                                                @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
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

  public static int showChooseDialog(String message, String title, String[] values, String initialValue, @Nullable Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
      dialog.show();
      return dialog.getSelectedIndex();
    }
  }

  public static int showChooseDialog(Component parent, String message, String title, String[] values, String initialValue, Icon icon) {
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
   * @see com.intellij.openapi.ui.DialogWrapper#DialogWrapper(Project,boolean)
   */
  public static int showChooseDialog(Project project, String message, String title, Icon icon, String[] values, String initialValue) {
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
  public static void showInfoMessage(Component component, String message, String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
      return;
    }
    
    showMessageDialog(component, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(@Nullable Project project, @Nls String message, @Nls String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
      return;
    }

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
  public static void showInfoMessage(String message, String title) {
    if (canShowMacSheetPanel()) {
      MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, null);
      return;
    }

    showMessageDialog(message, title, getInformationIcon());
  }

  /**
   * Shows dialog with text area to edit long strings that don't fit in text field 
   */
  public static void showTextAreaDialog(final JTextField textField, final String title, @NonNls final String dimensionServiceKey) {
    if (isApplicationInUnitTestOrHeadless()) {
      ourTestImplementation.show(title);
    }
    else {
      final JTextArea textArea = new JTextArea(10, 50);
      textArea.setWrapStyleWord(true);
      textArea.setLineWrap(true);
      String s = textField.getText().replaceAll("[ ]*=[ ]*", "=").replaceAll("=\\-", "=\\ \\-");
      List<String> lines = StringUtil.splitHonorQuotes(s, ' ');
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
      builder.setOkOperation(new Runnable() {
        @Override
        public void run() {
          textField.setText(textArea.getText());
          builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        }
      });
      builder.addDisposable(new TextComponentUndoProvider(textArea));
      builder.show();
    }
  }

  private static class MoreInfoMessageDialog extends MessageDialog {
    private final String myInfoText;

    public MoreInfoMessageDialog(Project project,
                                 String message,
                                 String title,
                                 String moreInfo,
                                 String[] options,
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

    public MessageDialog(@Nullable Project project, String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon, boolean canBeParent) {
      this(project, message, title, options, defaultOptionIndex, -1, icon, canBeParent);
    }

    public MessageDialog(@Nullable Project project, String message, String title, String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Icon icon,
                         @Nullable DoNotAskOption doNotAskOption, boolean canBeParent) {
      super(project, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    }

    public MessageDialog(@Nullable Project project, String message, String title, String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Icon icon,
                         boolean canBeParent) {
      super(project, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    public MessageDialog(Component parent, String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon) {
      this(parent, message, title, options, defaultOptionIndex, icon, false);
    }
    
    public MessageDialog(Component parent, String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon, boolean canBeParent) {
      this(parent, message, title, options, defaultOptionIndex, -1, icon, canBeParent);
    }

    public MessageDialog(Component parent, String message, String title, String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Icon icon,
                         boolean canBeParent) {
      super(parent, canBeParent);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
    }

    public MessageDialog(String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon) {
      this(message, title, options, defaultOptionIndex, icon, false);
    }
    
    public MessageDialog(String message, String title, String[] options, int defaultOptionIndex, @Nullable Icon icon, boolean canBeParent) {
      super(canBeParent);
      _init(title, message, options, defaultOptionIndex, -1, icon, null);
    }

    public MessageDialog(String message, String title, String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Icon icon, @Nullable DoNotAskOption doNotAskOption) {
      super(false);
      _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
    }

    public MessageDialog(String message, String title, String[] options, int defaultOptionIndex, Icon icon, DoNotAskOption doNotAskOption) {
      this(message, title, options, defaultOptionIndex, -1, icon, doNotAskOption);
    }

    protected MessageDialog() {
      super(false);
    }

    protected MessageDialog(Project project) {
      super(project, false);
    }

    protected void _init(String title, String message, String[] options, int defaultOptionIndex, int focusedOptionIndex, @Nullable Icon icon, @Nullable DoNotAskOption doNotAskOption) {
      setTitle(title);
      myMessage = message;
      myOptions = options;
      myDefaultOptionIndex = defaultOptionIndex;
      myFocusedOptionIndex = focusedOptionIndex;
      myIcon = icon;
      setButtonsAlignment(SwingConstants.CENTER);
      setDoNotAskOption(doNotAskOption);
      init();
    }

    protected Action[] createActions() {
      Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        actions[i] = new AbstractAction(UIUtil.replaceMnemonicAmpersand(option)) {
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

        assignMnemonic(option, actions[i]);

      }
      return actions;
    }

    private static void assignMnemonic(String option, Action action) {
      int mnemoPos = option.indexOf("&");
      if (mnemoPos >= 0 && mnemoPos < option.length() - 2) {
        String mnemoChar = option.substring(mnemoPos + 1, mnemoPos + 2).trim();
        if (mnemoChar.length() == 1) {
          action.putValue(Action.MNEMONIC_KEY, new Integer(mnemoChar.charAt(0)));
        }
      }
    }

    public void doCancelAction() {
      close(-1);
    }

    protected JComponent createCenterPanel() {
      return doCreateCenterPanel();
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

        final Dimension screenSize = messageComponent.getToolkit().getScreenSize();
        final Dimension textSize = messageComponent.getPreferredSize();
        if (myMessage.length() > 100) {
          final JScrollPane pane = ScrollPaneFactory.createScrollPane(messageComponent);
          pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
          pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
          pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
          final int scrollSize = (int)new JScrollBar(Adjustable.VERTICAL).getPreferredSize().getWidth();
          final Dimension preferredSize =
            new Dimension(Math.min(textSize.width, screenSize.width / 2) + scrollSize,
                          Math.min(textSize.height, screenSize.height / 3) + scrollSize);
          pane.setPreferredSize(preferredSize);
          panel.add(pane, BorderLayout.CENTER);
        }
        else {
          panel.add(messageComponent, BorderLayout.CENTER);
        }
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

  public static void installHyperlinkSupport(JTextPane messageComponent) {
    configureMessagePaneUi(messageComponent, "<html></html>");
  }

  public static JTextPane configureMessagePaneUi(JTextPane messageComponent, String message) {
    return configureMessagePaneUi(messageComponent, message, true);
  }

  public static JTextPane configureMessagePaneUi(JTextPane messageComponent,
                                                 String message,
                                                 final boolean addBrowserHyperlinkListener) {
    messageComponent.setFont(UIUtil.getLabelFont());
    if (BasicHTML.isHTMLString(message)) {
      final HTMLEditorKit editorKit = new HTMLEditorKit();
      editorKit.getStyleSheet().addRule(UIUtil.displayPropertiesToCSS(UIUtil.getLabelFont(), UIUtil.getLabelForeground()));
      messageComponent.setEditorKit(editorKit);
      messageComponent.setContentType(UIUtil.HTML_MIME);
      if (addBrowserHyperlinkListener) {
        messageComponent.addHyperlinkListener(new BrowserHyperlinkListener());
      }
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

  protected static class TwoStepConfirmationDialog extends MessageDialog {
    private JCheckBox myCheckBox;
    private final String myCheckboxText;
    private boolean myChecked;
    private PairFunction<Integer, JCheckBox, Integer> myExitFunc;

    public TwoStepConfirmationDialog(String message, String title, String[] options, String checkboxText, boolean checked, final int defaultOptionInxed,
                                     final int focusedOptionIndex, Icon icon, @Nullable final PairFunction<Integer, JCheckBox, Integer> exitFunc) {
      myCheckboxText = checkboxText;
      myChecked = checked;
      myExitFunc = exitFunc;

      _init(title, message, options, defaultOptionInxed, focusedOptionIndex, icon, null);
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

      return exitCode == OK_EXIT_CODE ? myCheckBox.isSelected() ? OK_EXIT_CODE : CANCEL_EXIT_CODE : CANCEL_EXIT_CODE;
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
                       String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator,
                       String[] options,
                       int defaultOption) {
      super(project, message, title, options, defaultOption, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
    }

    public InputDialog(@Nullable Project project,
                       String message,
                       String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      this(project, message, title, icon, initialValue, validator, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public InputDialog(Component parent,
                       String message,
                       String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
    }

    public InputDialog(String message,
                       String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon, true);
      myValidator = validator;
      myField.setText(initialValue);
    }

    protected Action[] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == 0) { // "OK" is default button. It has index 0.
          actions[i] = getOKAction();
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myField.getDocument().addDocumentListener(new DocumentAdapter() {
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

    protected JComponent createCenterPanel() {
      return null;
    }

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

    public JComponent getPreferredFocusedComponent() {
      return myField;
    }

    @Nullable
    public String getInputString() {
      if (getExitCode() == 0) {
        return myField.getText().trim();
      }
      else {
        return null;
      }
    }
  }

  protected static class MultilineInputDialog extends InputDialog {
    public MultilineInputDialog(Project project,
                                String message,
                                String title,
                                @Nullable Icon icon,
                                @Nullable String initialValue,
                                @Nullable InputValidator validator,
                                String[] options,
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
                               String title,
                               @Nullable Icon icon,
                               @Nullable InputValidator validator) {
      super(message, title, icon, null, validator);
    }

    public PasswordInputDialog(Project project,
                               String message,
                               String title,
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
                                   String title,
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

  protected static class ChooseDialog extends MessageDialog {
    private ComboBox myComboBox;
    private InputValidator myValidator;

    public ChooseDialog(Project project,
                        String message,
                        String title,
                        @Nullable Icon icon,
                        String[] values,
                        String initialValue,
                        String[] options,
                        int defaultOption) {
      super(project, message, title, options, defaultOption, icon, true);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(Project project, String message, String title, @Nullable Icon icon, String[] values, String initialValue) {
      this(project, message, title, icon, values, initialValue, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public ChooseDialog(Component parent, String message, String title, @Nullable Icon icon, String[] values, String initialValue) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(String message, String title, @Nullable Icon icon, String[] values, String initialValue) {
      super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    protected Action[] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == myDefaultOptionIndex) {
          actions[i] = new AbstractAction(option) {
            public void actionPerformed(ActionEvent e) {
              if (myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim())) {
                close(exitCode);
              }
            }
          };
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myComboBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim()));
            }
          });
          final JTextField textField = (JTextField)myComboBox.getEditor().getEditorComponent();
          textField.getDocument().addDocumentListener(new DocumentAdapter() {
            public void textChanged(DocumentEvent event) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(textField.getText().trim()));
            }
          });
        }
        else { // "Cancel" action
          actions[i] = new AbstractAction(option) {
            public void actionPerformed(ActionEvent e) {
              close(exitCode);
            }
          };
        }
      }
      return actions;
    }

    protected JComponent createCenterPanel() {
      return null;
    }

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

    protected void doOKAction() {
      String inputString = myComboBox.getSelectedItem().toString().trim();
      if (myValidator == null ||
          myValidator.checkInput(inputString) &&
          myValidator.canClose(inputString)) {
        super.doOKAction();
      }
    }

    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    @Nullable
    public String getInputString() {
      if (getExitCode() == 0) {
        return myComboBox.getSelectedItem().toString();
      }
      else {
        return null;
      }
    }

    public int getSelectedIndex() {
      if (getExitCode() == 0) {
        return myComboBox.getSelectedIndex();
      }
      else {
        return -1;
      }
    }

    public JComboBox getComboBox() {
      return myComboBox;
    }

    public void setValidator(@Nullable InputValidator validator) {
      myValidator = validator;
    }
  }
}
