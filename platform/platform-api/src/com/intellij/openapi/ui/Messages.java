// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.messages.MessageDialog;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.MessageException;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.mac.MacMessages;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.*;

/**
 * Provides several default notification dialogs ("OK|Cancel") as well as simple input dialogs.
 */
public class Messages {
  public static final int OK = 0;
  public static final int YES = 0;
  public static final int NO = 1;
  public static final int CANCEL = 2;

  /**
   * @deprecated Use {@link #getOkButton()} instead
   */
  @Deprecated
  public static final String OK_BUTTON = "OK";

  /**
   * @deprecated Use {@link #getYesButton()} instead
   */
  @Deprecated
  public static final String YES_BUTTON = "&Yes";

  /**
   * @deprecated Use {@link #getNoButton()} instead
   */
  @Deprecated
  public static final String NO_BUTTON = "&No";

  /**
   * @deprecated Use {@link #getCancelButton()} instead
   */
  @Deprecated
  public static final String CANCEL_BUTTON = "Cancel";

  public static @Nls String getOkButton() { return CommonBundle.getOkButtonText(); }
  public static String getYesButton() { return CommonBundle.getYesButtonText(); }
  public static String getNoButton() { return CommonBundle.getNoButtonText(); }
  public static @Nls String getCancelButton() { return CommonBundle.getCancelButtonText(); }

  private static TestDialog ourTestImplementation = TestDialog.DEFAULT;
  private static TestInputDialog ourTestInputImplementation = TestInputDialog.DEFAULT;
  private static final Logger LOG = Logger.getInstance(Messages.class);

  @TestOnly
  public static TestDialog setTestDialog(TestDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      LOG.assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
    if (newValue == null) {
      ourTestImplementation = TestDialog.DEFAULT;
      throw new IllegalArgumentException("Attempt to set TestDialog to null: default implementation was restored instead");
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
    if (newValue == null) {
      ourTestInputImplementation = TestInputDialog.DEFAULT;
      throw new IllegalArgumentException("Attempt to set TestInputDialog to null: default implementation was restored instead");
    }
    TestInputDialog oldValue = ourTestInputImplementation;
    ourTestInputImplementation = newValue;
    return oldValue;
  }

  public static TestDialog getTestImplementation() {
    return ourTestImplementation;
  }

  public static TestInputDialog getTestInputImplementation() {
    return ourTestInputImplementation;
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

  public static void installHyperlinkSupport(JTextPane messageComponent) {
    configureMessagePaneUi(messageComponent, "<html></html>");
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
    Dimension preferredSize =
      new Dimension(Math.min(prefDim.width, maxDim.width) + barWidth,
                    Math.min(prefDim.height, maxDim.height) + barWidth);
    if (prefDim.width > maxDim.width) { //Too wide single-line message should be wrapped
      preferredSize.height = Math.max(preferredSize.height, (int)(4 * fontSize) + barWidth);
    }
    scrollPane.setPreferredSize(preferredSize);
    return scrollPane;
  }

  @NotNull
  public static JTextPane configureMessagePaneUi(JTextPane messageComponent, @DialogMessage String message) {
    JTextPane pane = configureMessagePaneUi(messageComponent, message, null);
    if (UIUtil.HTML_MIME.equals(pane.getContentType())) {
      pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    }
    return pane;
  }

  @NotNull
  public static JTextPane configureMessagePaneUi(@NotNull JTextPane messageComponent,
                                                 @Nullable @DialogMessage String message,
                                                 @Nullable UIUtil.FontSize fontSize) {
    UIUtil.FontSize fixedFontSize = fontSize == null ? UIUtil.FontSize.NORMAL : fontSize;
    messageComponent.setFont(UIUtil.getLabelFont(fixedFontSize));
    if (BasicHTML.isHTMLString(message)) {
      messageComponent.setEditorKit(UIUtil.getHTMLEditorKit());
    }
    messageComponent.setText(message);
    messageComponent.setEditable(false);
    if (messageComponent.getCaret() != null) {
      messageComponent.setCaretPosition(0);
    }

    messageComponent.setBackground(UIUtil.getOptionPaneBackground());
    messageComponent.setForeground(UIUtil.getLabelForeground());
    return messageComponent;
  }

  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
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

  @NotNull
  public static Runnable createMessageDialogRemover(@Nullable Project project) {
    Window projectWindow = project == null ? null : WindowManager.getInstance().suggestParentWindow(project);
    //noinspection SSBasedInspection
    return () -> SwingUtilities.invokeLater(() -> makeCurrentMessageDialogGoAway(
      projectWindow != null ? projectWindow.getOwnedWindows() : Window.getWindows()));
  }

  private static void makeCurrentMessageDialogGoAway(Window @NotNull [] checkWindows) {
    for (Window w : checkWindows) {
      JDialog dialog = w instanceof JDialog ? (JDialog)w : null;
      if (dialog == null || !dialog.isModal()) continue;
      JButton cancelButton = UIUtil.uiTraverser(dialog.getRootPane()).filter(JButton.class)
        .filter(b -> CommonBundle.getCancelButtonText().equals(b.getText()))
        .first();
      if (cancelButton != null) cancelButton.doClick();
    }
  }


  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project,
                               @DialogMessage String message,
                               @NotNull @DialogTitle String title,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return MessagesService.getInstance()
      .showMessageDialog(project, null, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption, false);
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
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return MessagesService.getInstance()
      .showMessageDialog(project, null, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption, true);
  }

  public static boolean canShowMacSheetPanel() {
    return SystemInfo.isMac && ApplicationManager.getApplication() != null && !isApplicationInUnitTestOrHeadless() && Registry.is("ide.mac.message.dialogs.as.sheets");
  }

  public static boolean isMacSheetEmulation() {
    return SystemInfo.isMac
           && Registry.is("ide.mac.message.dialogs.as.sheets", true)
           && Registry.is("ide.mac.message.sheets.java.emulation", false);
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
    return MessagesService.getInstance().showMessageDialog(null, parent, message, title, options, defaultOptionIndex, -1, icon, null, false);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Icon, DialogWrapper.DoNotAskOption)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(@DialogMessage String message,
                               @NotNull @DialogTitle String title,
                               String @NotNull @NlsContexts.Button [] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return MessagesService.getInstance()
      .showMessageDialog(null, null, message, title, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption, false);
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
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
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
        MacMessages.getInstance().showOkMessageDialog(title, message, getOkButton());
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(message, title, new String[]{getOkButton()}, 0, icon);
  }

  @MagicConstant(intValues = {YES, NO})
  public @interface YesNoResult {
  }

  /**
   * Shows confirmation dialog with specified confirmation options. In MacSheet the {@param message} is shown in the title field, and title is not shown at all.
   *
   * @return {@link #YES} if user pressed button with {@param yesText} or {@link #NO} if user pressed button with {@param noText}.
   */
  @YesNoResult
  public static int showConfirmationDialog(@NotNull JComponent parent,
                                           @NotNull @DialogMessage String message,
                                           @NotNull @DialogTitle String title,
                                           @NotNull @NlsContexts.Button String yesText,
                                           @NotNull @NlsContexts.Button String noText) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(message, "", yesText, noText, SwingUtilities.getWindowAncestor(parent));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showDialog(parent, message, title, new String[]{yesText, noText}, 0, getQuestionIcon()) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @NotNull @NlsContexts.Button String yesText,
                                    @NotNull @NlsContexts.Button String noText,
                                    @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, yesText, noText, WindowManager.getInstance().suggestParentWindow(project));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

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
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @NotNull @NlsContexts.Button String yesText,
                                    @NotNull @NlsContexts.Button String noText,
                                    @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, yesText, noText, WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showDialog(project, message, title, new String[]{yesText, noText}, 0, icon, doNotAskOption) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, getYesButton(), getNoButton(), WindowManager.getInstance().suggestParentWindow(project));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showYesNoDialog(project, message, title, getYesButton(), getNoButton(), icon);

    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, getYesButton(), getNoButton(), WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showYesNoDialog(project, message, title, getYesButton(), getNoButton(), icon, doNotAskOption);

    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }


  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@NotNull Component parent,
                                    @DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, getYesButton(), getNoButton(), SwingUtilities.getWindowAncestor(parent));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showDialog(parent, message, title, new String[]{getYesButton(), getNoButton()}, 0, icon) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, Icon)
   */
  @YesNoResult
  public static int showYesNoDialog(@DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nls @NotNull @NlsContexts.Button String yesText,
                                    @Nls @NotNull @NlsContexts.Button String noText,
                                    @Nullable Icon icon,
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
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, String, String, Icon)
   * @see #showYesNoDialog(Component, String, String, Icon)
   */
  @YesNoResult
  public static int showYesNoDialog(@DialogMessage String message,
                                    @DialogTitle String title,
                                    @NlsContexts.Button String yesText,
                                    @NlsContexts.Button String noText,
                                    @Nullable Icon icon) {
    return showYesNoDialog(message, title, yesText, noText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, Icon)
   * @see #showYesNoDialog(Component, String, String, Icon)
   */
  @YesNoResult
  public static int showYesNoDialog(@DialogMessage String message,
                                    @NotNull @DialogTitle String title,
                                    @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, getYesButton(), getNoButton(), null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showYesNoDialog(message, title, getYesButton(), getNoButton(), icon);
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
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @NlsContexts.Button String okText,
                                       @NotNull @NlsContexts.Button String cancelText,
                                       Icon icon,
                                       DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance()
          .showYesNoDialog(title, message, okText, cancelText, WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    return showDialog(project, message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption) == 0 ? OK : CANCEL;
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project,
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @NlsContexts.Button String okText,
                                       @NotNull @NlsContexts.Button String cancelText,
                                       Icon icon) {
    return showOkCancelDialog(project, message, title, okText, cancelText, icon, null);
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @deprecated Please provide meaningful action names via {@link #showOkCancelDialog(Project, String, String, String, String, Icon)} instead
   */
  @OkCancelResult
  @Deprecated
  public static int showOkCancelDialog(Project project,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       Icon icon) {
    return showOkCancelDialog(project, message, title, getOkButton(), getCancelButton(), icon);
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(@NotNull Component parent,
                                       @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @NlsContexts.Button String okText,
                                       @NotNull @NlsContexts.Button String cancelText,
                                       Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        int result =
          MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, SwingUtilities.getWindowAncestor(parent));
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    return showDialog(parent, message, title, new String[]{okText, cancelText}, 0, icon) == 0 ? OK : CANCEL;
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @deprecated Please provide meaningful action names via {@link #showOkCancelDialog(Component, String, String, String, String, Icon)} instead
   */
  @OkCancelResult
  @Deprecated
  public static int showOkCancelDialog(@NotNull Component parent,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       Icon icon) {
    return showOkCancelDialog(parent, message, title, getOkButton(), getCancelButton(), icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @deprecated Please provide meaningful action names via {@link #showOkCancelDialog(String, String, String, String, Icon)} instead
   */
  @OkCancelResult
  @Deprecated
  public static int showOkCancelDialog(@DialogMessage String message, @DialogTitle String title, Icon icon) {
    return showOkCancelDialog(message, title, getOkButton(), getCancelButton(), icon, null);
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
    return showOkCancelDialog(message, title, okText, cancelText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon, DialogWrapper.DoNotAskOption)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   */
  @OkCancelResult
  public static int showOkCancelDialog(@DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @NlsContexts.Button String okText,
                                       @NotNull @NlsContexts.Button String cancelText,
                                       Icon icon,
                                       @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, null, doNotAskOption);
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    return showDialog(message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption) == 0 ? OK : CANCEL;
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
                                              @Nullable final PairFunction<? super Integer, ? super JCheckBox, Integer> exitFunc) {
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
    MessagesService.getInstance().showMessageDialog(null, component, message, title,
      /* options = */ new String[]{getOkButton()},
      /* defaultOptionIndex = */ 0, /* focusedOptionIndex = */ 0, getErrorIcon(), null, false);
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

  @MagicConstant(intValues = {YES, NO, CANCEL})
  public @interface YesNoCancelResult {
  }


  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(Project project,
                                          @DialogMessage String message,
                                          @NotNull @DialogTitle String title,
                                          @NotNull @NlsContexts.Button String yes,
                                          @NotNull @NlsContexts.Button String no,
                                          @NotNull @NlsContexts.Button String cancel,
                                          @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoCancelDialog(title, message, yes, no, cancel, WindowManager.getInstance().suggestParentWindow(project), null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int buttonNumber = showDialog(project, message, title, new String[]{yes, no, cancel}, 0, icon);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          Icon icon) {
    return showYesNoCancelDialog(project, message, title, getYesButton(), getNoButton(), getCancelButton(), icon);
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@NotNull Component parent,
                                          @DialogMessage String message,
                                          @NotNull @DialogTitle String title,
                                          @NotNull @NlsContexts.Button String yes,
                                          @NotNull @NlsContexts.Button String no,
                                          @NotNull @NlsContexts.Button String cancel,
                                          Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoCancelDialog(title, message, yes, no, cancel, SwingUtilities.getWindowAncestor(parent), null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int buttonNumber = showDialog(parent, message, title, new String[]{yes, no, cancel}, 0, icon);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@NotNull Component parent,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          Icon icon) {
    return showYesNoCancelDialog(parent, message, title, getYesButton(), getNoButton(), getCancelButton(), icon);
  }


  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Icon)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@DialogMessage String message,
                                          @NotNull @DialogTitle String title,
                                          @NotNull @NlsContexts.Button String yes,
                                          @NotNull @NlsContexts.Button String no,
                                          @NotNull @NlsContexts.Button String cancel,
                                          Icon icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel, null, doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int buttonNumber = showDialog(message, title, new String[]{yes, no, cancel}, 0, icon, doNotAskOption);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Icon)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@DialogMessage String message,
                                          @DialogTitle String title,
                                          @NlsContexts.Button String yes,
                                          @NlsContexts.Button String no,
                                          @NlsContexts.Button String cancel,
                                          Icon icon) {
    return showYesNoCancelDialog(message, title, yes, no, cancel, icon, null);
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
                                          Icon icon) {
    return showYesNoCancelDialog(message, title, getYesButton(), getNoButton(), getCancelButton(), icon);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@DialogMessage String message, @DialogTitle String title) {
    return showPasswordDialog(null, message, title, null, null);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          @Nullable Icon icon) {
    return showPasswordDialog(project, message, title, icon, null);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nullable Project project,
                                          @DialogMessage String message,
                                          @DialogTitle String title,
                                          @Nullable Icon icon,
                                          @Nullable InputValidator validator) {
    return MessagesService.getInstance().showPasswordDialog(project, message, title, icon, validator);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(@Nullable Project project,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon) {
    return showInputDialog(project, message, title, icon, null, null);
  }

  /**
   * @return trimmed input string or {@code null} if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(@NotNull Component parent,
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
  @Nullable
  public static String showInputDialog(@DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon) {
    return showInputDialog(message, title, icon, null, null);
  }

  @Nullable
  public static String showInputDialog(@Nullable Project project,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    return MessagesService.getInstance().showInputDialog(project, null, message, title, icon, initialValue, validator, null, null);
  }

  @Nullable
  public static String showInputDialog(Project project,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator,
                                       @Nullable TextRange selection) {
    return showInputDialog(project, message, title, icon, initialValue, validator, selection, null);

  }

  @Nullable
  public static String showInputDialog(Project project,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator,
                                       @Nullable TextRange selection,
                                       @Nullable @DetailedDescription String comment) {
    return MessagesService.getInstance().showInputDialog(project, null, message, title, icon, initialValue, validator, selection, comment);
  }

  @Nullable
  public static String showInputDialog(@NotNull Component parent,
                                       @DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    return MessagesService.getInstance().showInputDialog(null, parent, message, title, icon, initialValue, validator, null, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Icon, String, InputValidator)
   */
  @Nullable
  public static String showInputDialog(@DialogMessage String message,
                                       @DialogTitle String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    return MessagesService.getInstance().showInputDialog(null, null, message, title, icon, initialValue, validator, null, null);
  }

  @Nullable
  public static String showMultilineInputDialog(Project project,
                                                @DialogMessage String message,
                                                @DialogTitle String title,
                                                @Nullable String initialValue,
                                                @Nullable Icon icon,
                                                @Nullable InputValidator validator) {
    return MessagesService.getInstance().showMultilineInputDialog(project, message, title, initialValue, icon, validator);
  }

  @NotNull
  public static Pair<String, Boolean> showInputDialogWithCheckBox(@DialogMessage String message,
                                                                  @DialogTitle String title,
                                                                  @NlsContexts.Checkbox String checkboxText,
                                                                  boolean checked,
                                                                  boolean checkboxEnabled,
                                                                  @Nullable Icon icon,
                                                                  String initialValue,
                                                                  @Nullable InputValidator validator) {
    return MessagesService.getInstance()
      .showInputDialogWithCheckBox(message, title, checkboxText, checked, checkboxEnabled, icon, initialValue, validator);
  }

  @Nullable
  public static String showEditableChooseDialog(@DialogMessage String message,
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
  public static int showChooseDialog(@NotNull Component parent,
                                     @DialogMessage String message,
                                     @DialogTitle String title,
                                     String[] values,
                                     String initialValue,
                                     Icon icon) {
    return MessagesService.getInstance().showChooseDialog(null, parent, message, title, values, initialValue, icon);
  }

  /**
   * @see DialogWrapper#DialogWrapper(Project, boolean)
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
                                        @DialogTitle final String title,
                                        @NonNls final String dimensionServiceKey,
                                        final Function<? super String, ? extends List<String>> parser,
                                        final Function<? super List<String>, String> lineJoiner) {
    MessagesService.getInstance().showTextAreaDialog(textField, title, dimensionServiceKey, parser, lineJoiner);
  }

  public static void showTextAreaDialog(final JTextField textField,
                                        final @DialogTitle String title,
                                        @NonNls final String dimensionServiceKey) {
    showTextAreaDialog(textField, title, dimensionServiceKey, ParametersListUtil.DEFAULT_LINE_PARSER,
                       ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  public static class InputDialog extends MessageDialog {
    protected JTextComponent myField;
    private final InputValidator myValidator;
    private final String myComment;

    public InputDialog(@Nullable Project project,
                       @DialogMessage String message,
                       @DialogTitle String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator,
                       String @NotNull @NlsContexts.Button [] options,
                       int defaultOption,
                       @Nullable @DetailedDescription String comment) {
      super(project, true);
      myComment = comment;
      myValidator = validator;
      _init(title, message, options, defaultOption, -1, icon, null);
      myField.setText(initialValue);
      enableOkAction();
    }

    public InputDialog(@Nullable Project project,
                       @DialogMessage String message,
                       @DialogTitle String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator,
                       String @NotNull @NlsContexts.Button [] options,
                       int defaultOption) {
      this(project, message, title, icon, initialValue, validator, options, defaultOption, null);
    }

    public InputDialog(@Nullable Project project,
                       @DialogMessage String message,
                       @DialogTitle String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      this(project, message, title, icon, initialValue, validator, new String[]{getOkButton(), getCancelButton()}, 0);
    }

    public InputDialog(@NotNull Component parent,
                       @DialogMessage String message,
                       @DialogTitle String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      super(null, parent, message, title, new String[]{getOkButton(), getCancelButton()}, -1, 0, icon, null, true);
      myValidator = validator;
      myComment = null;
      myField.setText(initialValue);
      enableOkAction();
    }

    public InputDialog(@DialogMessage String message,
                       @DialogTitle String title,
                       @Nullable Icon icon,
                       @Nullable String initialValue,
                       @Nullable InputValidator validator) {
      super(null, null, message, title, new String[]{getOkButton(), getCancelButton()}, 0, -1, icon, null, true);
      myValidator = validator;
      myComment = null;
      myField.setText(initialValue);
      enableOkAction();
    }

    private void enableOkAction() {
      getOKAction().setEnabled(myValidator == null || myValidator.checkInput(myField.getText().trim()));
    }

    @Override
    protected Action @NotNull [] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == 0) { // "OK" is default button. It has index 0.
          actions[0] = getOKAction();
          actions[0].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(@NotNull DocumentEvent event) {
              final String text = myField.getText().trim();
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(text));
              if (myValidator instanceof InputValidatorEx) {
                setErrorText(((InputValidatorEx) myValidator).getErrorText(text), myField);
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
      JPanel panel = createIconPanel();

      JPanel messagePanel = createMessagePanel();
      panel.add(messagePanel, BorderLayout.CENTER);

      if (myComment != null) {
        return UI.PanelFactory.panel(panel).withComment(myComment).createPanel();
      }
      else {
        return panel;
      }
    }

    @Override
    @NotNull
    protected JPanel createMessagePanel() {
      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JComponent textComponent = createTextComponent();
        messagePanel.add(textComponent, BorderLayout.NORTH);
      }

      myField = createTextFieldComponent();
      messagePanel.add(createScrollableTextComponent(), BorderLayout.SOUTH);

      return messagePanel;
    }

    protected JComponent createScrollableTextComponent() {
      return myField;
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
      textComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 20));
      return textComponent;
    }

    public JTextComponent getTextField() {
      return myField;
    }

    protected JTextComponent createTextFieldComponent() {
      JTextField field = new JTextField(30);
      field.setMargin(JBInsets.create(0, 5));
      return field;
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

  public static class MultilineInputDialog extends InputDialog {
    public MultilineInputDialog(Project project,
                                @DialogMessage String message,
                                @DialogTitle String title,
                                @Nullable Icon icon,
                                @Nullable String initialValue,
                                @Nullable InputValidator validator,
                                String @NotNull @NlsContexts.Button [] options,
                                int defaultOption) {
      super(project, message, title, icon, initialValue, validator, options, defaultOption);
    }

    @Override
    protected JTextComponent createTextFieldComponent() {
      return new JTextArea(7, 50);
    }

    @Override
    protected JComponent createScrollableTextComponent() {
      return new JBScrollPane(myField);
    }

    @Override
    protected JComponent createNorthPanel() {
      return null;
    }

    @Override
    protected JComponent createCenterPanel() {
      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JComponent textComponent = createTextComponent();
        messagePanel.add(textComponent, BorderLayout.NORTH);
      }

      myField = createTextFieldComponent();
      messagePanel.add(createScrollableTextComponent(), BorderLayout.CENTER);
      return messagePanel;
    }
  }

}
