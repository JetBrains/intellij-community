/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.CommonBundle;
import com.intellij.ExtensionPoints;
import com.intellij.diagnostic.errordialog.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.HeaderlessTabbedPane;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

public class IdeErrorsDialog extends DialogWrapper implements MessagePoolListener, TypeSafeDataProvider {
  private static final Logger LOG = Logger.getInstance(IdeErrorsDialog.class.getName());
  private final boolean myInternalMode;
  @NonNls private static final String ACTIVE_TAB_OPTION = IdeErrorsDialog.class.getName() + "activeTab";
  public static DataKey<String> CURRENT_TRACE_KEY = DataKey.create("current_stack_trace_key");
  public static final int COMPONENTS_WIDTH = 670;
  public static Collection<Developer> ourDevelopersList = Collections.emptyList();

  private JPanel myContentPane;
  private JPanel myBackButtonPanel;
  private HyperlinkLabel.Croppable myInfoLabel;
  private JPanel myNextButtonPanel;
  private JPanel myTabsPanel;
  private JLabel myCountLabel;
  private HyperlinkLabel.Croppable myForeignPluginWarningLabel;
  private HyperlinkLabel.Croppable myDisableLink;
  private JPanel myCredentialsPanel;
  private HyperlinkLabel myCredentialsLabel;
  private JPanel myForeignPluginWarningPanel;
  private JPanel myAttachmentWarningPanel;
  private HyperlinkLabel myAttachmentWarningLabel;

  private int myIndex = 0;
  private final List<ArrayList<AbstractMessage>> myMergedMessages = new ArrayList<ArrayList<AbstractMessage>>();
  private List<AbstractMessage> myRawMessages;
  private final MessagePool myMessagePool;
  private HeaderlessTabbedPane myTabs;
  @Nullable
  private CommentsTabForm myCommentsTabForm;
  private DetailsTabForm myDetailsTabForm;
  private AttachmentsTabForm myAttachmentsTabForm;

  private ClearFatalsAction myClearAction = new ClearFatalsAction();
  private BlameAction myBlameAction;
  @Nullable
  private AnalyzeAction myAnalyzeAction;
  private boolean myMute;

  public IdeErrorsDialog(MessagePool messagePool, @Nullable LogMessage defaultMessage) {
    super(JOptionPane.getRootFrame(), false);
    myMessagePool = messagePool;
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    myInternalMode = app != null && app.isInternal();
    setTitle(DiagnosticBundle.message("error.list.title"));
    init();
    rebuildHeaders();
    if (defaultMessage == null || !moveSelectionToMessage(defaultMessage)) {
      moveSelectionToEarliestMessage();
    }
    setCancelButtonText(CommonBundle.message("close.action.name"));
    setModal(false);
    if (myInternalMode) {
      if (ourDevelopersList.isEmpty()) {
        loadDevelopersAsynchronously();
      } else {
        myDetailsTabForm.setDevelopers(ourDevelopersList);
      }
    }
  }

  private void loadDevelopersAsynchronously() {
    Task.Backgroundable task = new Task.Backgroundable(null, "Loading developers list", true) {
      private final Collection[] myDevelopers = new Collection[]{Collections.emptyList()};

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          myDevelopers[0] = DevelopersLoader.fetchDevelopers(indicator);
        } catch (IOException e) {
          //Notifications.Bus.register("Error reporter", NotificationDisplayType.BALLOON);
          //Notifications.Bus.notify(new Notification("Error reporter", "Communication error",
          //                                          "Unable to load developers list from server.", NotificationType.WARNING));
        }
      }

      @Override
      public void onSuccess() {
        Collection<Developer> developers = myDevelopers[0];
        myDetailsTabForm.setDevelopers(developers);
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourDevelopersList = developers;
      }
    };
    ProgressManager.getInstance().run(task);
  }

  private boolean moveSelectionToMessage(LogMessage defaultMessage) {
    int index = -1;
    for (int i = 0; i < myMergedMessages.size(); i++) {
      final AbstractMessage each = getMessageAt(i);
      if (each == defaultMessage) {
        index = i;
        break;
      }
    }

    if (index >= 0) {
      myIndex = index;
      updateControls();
      return true;
    }
    else {
      return false;
    }
  }

  public void newEntryAdded() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        rebuildHeaders();
        updateControls();
      }
    });
  }

  public void poolCleared() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        doOKAction();
      }
    });
  }

  @Override
  public void entryWasRead() {
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    if (SystemInfo.isMac) {
      return new Action[]{getCancelAction(), myClearAction, myBlameAction};
    }
    else {
      return new Action[]{myClearAction, myBlameAction, getCancelAction()};
    }
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myBlameAction = new BlameAction();
    myBlameAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
  }

  private class ForwardAction extends AnAction implements DumbAware {
    public ForwardAction() {
      super("Next", null, AllIcons.Actions.Forward);
      AnAction forward = ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB);
      if (forward != null) {
        registerCustomShortcutSet(forward.getShortcutSet(), getRootPane(), getDisposable());
      }

    }

    public void actionPerformed(AnActionEvent e) {
      goForward();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex < myMergedMessages.size() - 1);
    }
  }

  private class BackAction extends AnAction implements DumbAware {
    public BackAction() {
      super("Previous", null, AllIcons.Actions.Back);
      AnAction back = ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB);
      if (back != null) {
        registerCustomShortcutSet(back.getShortcutSet(), getRootPane(), getDisposable());
      }
    }

    public void actionPerformed(AnActionEvent e) {
      goBack();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex > 0);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    DefaultActionGroup goBack = new DefaultActionGroup();
    BackAction back = new BackAction();
    goBack.add(back);
    ActionToolbar backToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, goBack, true);
    backToolbar.getComponent().setBorder(IdeBorderFactory.createEmptyBorder());
    backToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myBackButtonPanel.add(backToolbar.getComponent(), BorderLayout.CENTER);

    DefaultActionGroup goForward = new DefaultActionGroup();
    ForwardAction forward = new ForwardAction();
    goForward.add(forward);
    ActionToolbar forwardToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, goForward, true);
    forwardToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    forwardToolbar.getComponent().setBorder(IdeBorderFactory.createEmptyBorder());
    myNextButtonPanel.add(forwardToolbar.getComponent(), BorderLayout.CENTER);

    myTabs = new HeaderlessTabbedPane(getDisposable());
    final LabeledTextComponent.TextListener commentsListener = new LabeledTextComponent.TextListener() {
      @Override
      public void textChanged(String newText) {
        if (myMute) {
          return;
        }

        AbstractMessage message = getSelectedMessage();
        if (message != null) {
          message.setAdditionalInfo(newText);
        }
      }
    };
    if (!myInternalMode) {
      myDetailsTabForm = new DetailsTabForm(null, myInternalMode);
      myCommentsTabForm = new CommentsTabForm();
      myCommentsTabForm.addCommentsListener(commentsListener);
      myTabs.addTab(DiagnosticBundle.message("error.comments.tab.title"), myCommentsTabForm.getContentPane());
      myDetailsTabForm.setCommentsAreaVisible(false);
    }
    else {
      final AnAction analyzePlatformAction = ActionManager.getInstance().getAction("AnalyzeStacktraceOnError");
      if (analyzePlatformAction != null) {
        myAnalyzeAction = new AnalyzeAction(analyzePlatformAction);
      }
      myDetailsTabForm = new DetailsTabForm(myAnalyzeAction, myInternalMode);
      myDetailsTabForm.setCommentsAreaVisible(true);
      myDetailsTabForm.addCommentsListener(commentsListener);
    }

    myTabs.addTab(DiagnosticBundle.message("error.details.tab.title"), myDetailsTabForm.getContentPane());

    myAttachmentsTabForm = new AttachmentsTabForm();
    myAttachmentsTabForm.addInclusionListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        updateAttachmentWarning(getSelectedMessage());
      }
    });

    int activeTabIndex = Integer.parseInt(PropertiesComponent.getInstance().getValue(ACTIVE_TAB_OPTION, "0"));
    if (activeTabIndex >= myTabs.getTabCount() || activeTabIndex < 0) {
      activeTabIndex = 0; // may happen if myInternalMode changed since last open
    }

    myTabs.setSelectedIndex(activeTabIndex);

    myTabs.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        final JComponent c = getPreferredFocusedComponent();
        if (c != null) {
          IdeFocusManager.findInstanceByComponent(myContentPane).requestFocus(c, true);
        }
      }
    });

    myTabsPanel.add(myTabs, BorderLayout.CENTER);

    myDisableLink.setHyperlinkText(UIUtil.removeMnemonic(DiagnosticBundle.message("error.list.disable.plugin")));
    myDisableLink.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          disablePlugin();
        }
      }
    });

    myCredentialsLabel.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          new JetBrainsAccountDialog(getRootPane()).show();
          updateCredentialsPane(getSelectedMessage());
        }
      }
    });

    myAttachmentWarningLabel.setIcon(UIUtil.getBalloonWarningIcon());
    myAttachmentWarningLabel.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          myTabs.setSelectedIndex(myTabs.indexOfComponent(myAttachmentsTabForm.getContentPane()));
          myAttachmentsTabForm.selectFirstIncludedAttachment();
        }
      }
    });

    myDetailsTabForm.addAssigneeListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myMute) return;

        AbstractMessage message = getSelectedMessage();
        if (message != null) {
          message.setAssigneeId(myDetailsTabForm.getAssigneeId());
        }
      }
    });

    return myContentPane;
  }

  private void moveSelectionToEarliestMessage() {
    myIndex = 0;
    for (int i = 0; i < myMergedMessages.size(); i++) {
      final AbstractMessage each = getMessageAt(i);
      if (!each.isRead()) {
        myIndex = i;
        break;
      }
    }

    updateControls();
  }

  private void disablePlugin() {
    final PluginId pluginId = findPluginId(getSelectedMessage().getThrowable());
    if (pluginId == null) {
      return;
    }

    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    final Ref<Boolean> hasDependants = new Ref<Boolean>(false);
    PluginManager.checkDependants(plugin, new Function<PluginId, IdeaPluginDescriptor>() {
                                    @Override
                                    public IdeaPluginDescriptor fun(PluginId pluginId) {
                                      return PluginManager.getPlugin(pluginId);
                                    }
                                  }, new Condition<PluginId>() {
      @Override
      public boolean value(PluginId pluginId) {
        if (PluginManagerCore.CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
          return true;
        }
        hasDependants.set(true);
        return false;
      }
    }
    );

    Application app = ApplicationManager.getApplication();
    DisablePluginWarningDialog d =
      new DisablePluginWarningDialog(getRootPane(), plugin.getName(), hasDependants.get(), app.isRestartCapable());
    d.show();
    switch (d.getExitCode()) {
      case CANCEL_EXIT_CODE:
        return;
      case DisablePluginWarningDialog.DISABLE_EXIT_CODE:
        PluginManager.disablePlugin(pluginId.getIdString());
        break;
      case DisablePluginWarningDialog.DISABLE_AND_RESTART_EXIT_CODE:
        PluginManager.disablePlugin(pluginId.getIdString());
        app.restart();
        break;
    }
  }

  private void goBack() {
    myIndex--;
    updateControls();
  }

  private void goForward() {
    myIndex++;
    updateControls();
  }

  private void updateControls() {
    updateCountLabel();
    final AbstractMessage message = getSelectedMessage();
    updateInfoLabel(message);
    updateCredentialsPane(message);
    updateAssigneePane(message);
    updateAttachmentWarning(message);
    myDisableLink.setVisible(canDisablePlugin(message));
    updateForeignPluginLabel(message != null ? message : null);
    updateTabs();

    myClearAction.update();
    myBlameAction.update();
    if (myAnalyzeAction != null) {
      myAnalyzeAction.update();
    }
  }

  private void updateAttachmentWarning(final AbstractMessage message) {
    if (message == null) return;
    final List<Attachment> includedAttachments = ContainerUtil.filter(message.getAttachments(), new Condition<Attachment>() {
      public boolean value(final Attachment attachment) {
        return attachment.isIncluded();
      }
    });
    if (!includedAttachments.isEmpty()) {
      myAttachmentWarningPanel.setVisible(true);
      if (includedAttachments.size() == 1) {
        myAttachmentWarningLabel.setHtmlText(
          DiagnosticBundle.message("diagnostic.error.report.include.attachment.warning", includedAttachments.get(0).getName()));
      }
      else {
        myAttachmentWarningLabel.setHtmlText(
          DiagnosticBundle.message("diagnostic.error.report.include.attachments.warning", includedAttachments.size()));
      }
    }
    else {
      myAttachmentWarningPanel.setVisible(false);
    }
  }

  private static boolean canDisablePlugin(AbstractMessage message) {
    if (message == null) {
      return false;
    }

    PluginId pluginId = findPluginId(message.getThrowable());
    return pluginId != null && !ApplicationInfoEx.getInstanceEx().isEssentialPlugin(pluginId.getIdString());
  }

  private void updateCountLabel() {
    if (myMergedMessages.isEmpty()) {
      myCountLabel.setText(DiagnosticBundle.message("error.list.empty"));
    }
    else {
      myCountLabel
        .setText(DiagnosticBundle.message("error.list.message.index.count", Integer.toString(myIndex + 1), myMergedMessages.size()));
    }
  }


  private void updateCredentialsPane(AbstractMessage message) {
    if (message != null) {
      final ErrorReportSubmitter submitter = getSubmitter(message.getThrowable());
      if (submitter instanceof ITNReporter) {
        myCredentialsPanel.setVisible(true);
        String userName = ErrorReportConfigurable.getInstance().ITN_LOGIN;
        if (StringUtil.isEmpty(userName)) {
          myCredentialsLabel.setHtmlText(DiagnosticBundle.message("diagnostic.error.report.submit.error.anonymously"));
        }
        else {
          myCredentialsLabel.setHtmlText(DiagnosticBundle.message("diagnostic.error.report.submit.report.as", userName));
        }
        return;
      }
    }
    myCredentialsPanel.setVisible(false);
  }

  private void updateAssigneePane(AbstractMessage message) {
    final ErrorReportSubmitter submitter = message != null ? getSubmitter(message.getThrowable()) : null;
    myDetailsTabForm.setAssigneeVisible(submitter instanceof ITNReporter && myInternalMode);
  }

  private void updateInfoLabel(AbstractMessage message) {
    if (message == null) {
      myInfoLabel.setText("");
      return;
    }
    final Throwable throwable = message.getThrowable();
    if (throwable instanceof MessagePool.TooManyErrorsException) {
      myInfoLabel.setText("");
      return;
    }

    StringBuilder text = new StringBuilder();
    PluginId pluginId = findPluginId(throwable);
    if (pluginId == null) {
      if (throwable instanceof AbstractMethodError) {
        text.append(DiagnosticBundle.message("error.list.message.blame.unknown.plugin"));
      }
      else {
        text.append(DiagnosticBundle.message("error.list.message.blame.core", ApplicationNamesInfo.getInstance().getProductName()));
      }
    }
    else {
      text.append(DiagnosticBundle.message("error.list.message.blame.plugin", PluginManager.getPlugin(pluginId).getName()));
    }
    text.append(" ").append(DiagnosticBundle.message("error.list.message.info",
                                                     DateFormatUtil.formatPrettyDateTime(message.getDate()),
                                                     myMergedMessages.get(myIndex).size()));

    String url = null;
    if (message.isSubmitted()) {
      SubmittedReportInfo info = message.getSubmissionInfo();
      url = info.getURL();
      appendSubmissionInformation(info, text);
      text.append(". ");
    }
    else if (message.isSubmitting()) {
      text.append(" Submitting...");
    }
    else if (!message.isRead()) {
      text.append(" ").append(DiagnosticBundle.message("error.list.message.unread"));
    }
    myInfoLabel.setHtmlText(XmlStringUtil.wrapInHtml(text));
    myInfoLabel.setHyperlinkTarget(url);
  }

  public static void appendSubmissionInformation(SubmittedReportInfo info, StringBuilder out) {
    if (info.getStatus() == SubmittedReportInfo.SubmissionStatus.FAILED) {
      out.append(" ").append(DiagnosticBundle.message("error.list.message.submission.failed"));
    }
    else if (info.getURL() != null && info.getLinkText() != null) {
      out.append(" ").append(DiagnosticBundle.message("error.list.message.submitted.as.link", info.getURL(), info.getLinkText()));
      if (info.getStatus() == SubmittedReportInfo.SubmissionStatus.DUPLICATE) {
        out.append(" ").append(DiagnosticBundle.message("error.list.message.duplicate"));
      }
    }
    else {
      out.append(DiagnosticBundle.message("error.list.message.submitted"));
    }
  }

  private void updateForeignPluginLabel(AbstractMessage message) {
    if (message != null) {
      final Throwable throwable = message.getThrowable();
      ErrorReportSubmitter submitter = getSubmitter(throwable);
      if (submitter == null) {
        PluginId pluginId = findPluginId(throwable);
        IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
        if (plugin == null || PluginManagerMain.isJetBrainsPlugin(plugin)) {
          myForeignPluginWarningPanel.setVisible(false);
          return;
        }

        myForeignPluginWarningPanel.setVisible(true);
        String vendor = plugin.getVendor();
        String contactInfo = plugin.getVendorUrl();
        if (StringUtil.isEmpty(contactInfo)) {
          contactInfo = plugin.getVendorEmail();
        }
        if (StringUtil.isEmpty(vendor)) {
          if (StringUtil.isEmpty(contactInfo)) {
            myForeignPluginWarningLabel.setText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning.text"));
          }
          else {
            myForeignPluginWarningLabel
              .setHyperlinkText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning.text.vendor") + " ",
                                contactInfo, ".");
            myForeignPluginWarningLabel.setHyperlinkTarget(contactInfo);
          }
        }
        else {
          if (StringUtil.isEmpty(contactInfo)) {
            myForeignPluginWarningLabel.setText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning.text.vendor") +
                                                " " + vendor + ".");
          }
          else {
            myForeignPluginWarningLabel
              .setHyperlinkText(
                DiagnosticBundle.message("error.dialog.foreign.plugin.warning.text.vendor") + " " + vendor + " (",
                contactInfo, ").");
            myForeignPluginWarningLabel.setHyperlinkTarget(contactInfo);
          }
        }
        myForeignPluginWarningPanel.setVisible(true);
        return;
      }
    }
    myForeignPluginWarningPanel.setVisible(false);
  }

  private void updateTabs() {
    myMute = true;
    try {
      if (myInternalMode) {
        boolean hasAttachment = false;
        for (ArrayList<AbstractMessage> merged : myMergedMessages) {
          final AbstractMessage message = merged.get(0);
          if (!message.getAttachments().isEmpty()) {
            hasAttachment = true;
            break;
          }
        }
        myTabs.setHeaderVisible(hasAttachment);
      }

      final AbstractMessage message = getSelectedMessage();
      if (myCommentsTabForm != null) {
        if (message != null) {
          String msg = message.getMessage();
          int i = msg.indexOf("\n");
          if (i != -1) {
            // take first line
            msg = msg.substring(0, i);
          }
          myCommentsTabForm.setErrorText(msg);
        }
        else {
          myCommentsTabForm.setErrorText(null);
        }
        if (message != null) {
          myCommentsTabForm.setCommentText(message.getAdditionalInfo());
          myCommentsTabForm.setCommentsTextEnabled(true);
        }
        else {
          myCommentsTabForm.setCommentText(null);
          myCommentsTabForm.setCommentsTextEnabled(false);
        }
      }

      myDetailsTabForm.setDetailsText(message != null ? getDetailsText(message) : null);
      if (message != null) {
        myDetailsTabForm.setCommentsText(message.getAdditionalInfo());
        myDetailsTabForm.setCommentsTextEnabled(true);
      }
      else {
        myDetailsTabForm.setCommentsText(null);
        myDetailsTabForm.setCommentsTextEnabled(false);
      }

      myDetailsTabForm.setAssigneeId(message == null ? null : message.getAssigneeId());

      List<Attachment> attachments = message != null ? message.getAttachments() : Collections.<Attachment>emptyList();
      if (!attachments.isEmpty()) {
        if (myTabs.indexOfComponent(myAttachmentsTabForm.getContentPane()) == -1) {
          myTabs.addTab(DiagnosticBundle.message("error.attachments.tab.title"), myAttachmentsTabForm.getContentPane());
        }
        myAttachmentsTabForm.setAttachments(attachments);
      }
      else {
        int index = myTabs.indexOfComponent(myAttachmentsTabForm.getContentPane());
        if (index != -1) {
          myTabs.removeTabAt(index);
        }
      }
    }
    finally {
      myMute = false;
    }
  }

  private static String getDetailsText(AbstractMessage message) {
    final Throwable throwable = message.getThrowable();
    if (throwable instanceof MessagePool.TooManyErrorsException) {
      return throwable.getMessage();
    }
    else {
      return new StringBuffer().append(message.getMessage()).append("\n").append(message.getThrowableText()).toString();
    }
  }

  private void rebuildHeaders() {
    myMergedMessages.clear();
    myRawMessages = myMessagePool.getFatalErrors(true, true);

    Map<String, ArrayList<AbstractMessage>> hash2Messages = mergeMessages(myRawMessages);

    for (final ArrayList<AbstractMessage> abstractMessages : hash2Messages.values()) {
      myMergedMessages.add(abstractMessages);
    }
  }

  private void markAllAsRead() {
    for (AbstractMessage each : myRawMessages) {
      each.setRead(true);
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final int selectedIndex = myTabs.getSelectedIndex();
    JComponent result;
    if (selectedIndex == 0) {
      result = myInternalMode ? myDetailsTabForm.getPreferredFocusedComponent() : myCommentsTabForm.getPreferredFocusedComponent();
    }
    else if (selectedIndex == 1) {
      result = myInternalMode ? myAttachmentsTabForm.getPreferredFocusedComponent() : myDetailsTabForm.getPreferredFocusedComponent();
    }
    else {
      result = myAttachmentsTabForm.getPreferredFocusedComponent();
    }
    return result != null ? result : super.getPreferredFocusedComponent();
  }

  private static Map<String, ArrayList<AbstractMessage>> mergeMessages(List<AbstractMessage> aErrors) {
    Map<String, ArrayList<AbstractMessage>> hash2Messages = new LinkedHashMap<String, ArrayList<AbstractMessage>>();
    for (final AbstractMessage each : aErrors) {
      final String hashCode = getThrowableHashCode(each.getThrowable());
      ArrayList<AbstractMessage> list;
      if (hash2Messages.containsKey(hashCode)) {
        list = hash2Messages.get(hashCode);
      }
      else {
        list = new ArrayList<AbstractMessage>();
        hash2Messages.put(hashCode, list);
      }
      list.add(0, each);
    }
    return hash2Messages;
  }

  private AbstractMessage getSelectedMessage() {
    return getMessageAt(myIndex);
  }

  private AbstractMessage getMessageAt(int idx) {
    if (idx < 0 || idx >= myMergedMessages.size()) return null;
    return myMergedMessages.get(idx).get(0);
  }

  @Nullable
  public static PluginId findPluginId(Throwable t) {
    if (t instanceof PluginException) {
      return ((PluginException)t).getPluginId();
    }

    Set<String> visitedClassNames = ContainerUtil.newHashSet();
    for (StackTraceElement element : t.getStackTrace()) {
      if (element != null) {
        String className = element.getClassName();
        if (visitedClassNames.add(className) && PluginManagerCore.isPluginClass(className)) {
          PluginId id = PluginManagerCore.getPluginByClassName(className);
          if (LOG.isDebugEnabled()) {
            LOG.debug(diagnosePluginDetection(className, id));
          }
          return id;
        }
      }
    }

    if (t instanceof NoSuchMethodException) {
      // check is method called from plugin classes
      if (t.getMessage() != null) {
        String className = "";
        StringTokenizer tok = new StringTokenizer(t.getMessage(), ".");
        while (tok.hasMoreTokens()) {
          String token = tok.nextToken();
          if (token.length() > 0 && Character.isJavaIdentifierStart(token.charAt(0))) {
            className += token;
          }
        }

        if (PluginManager.isPluginClass(className)) {
          return PluginManager.getPluginByClassName(className);
        }
      }
    }
    else if (t instanceof ClassNotFoundException) {
      // check is class from plugin classes
      if (t.getMessage() != null) {
        String className = t.getMessage();

        if (PluginManager.isPluginClass(className)) {
          return PluginManager.getPluginByClassName(className);
        }
      }
    }
    else if (t instanceof AbstractMethodError && t.getMessage() != null) {
      String s = t.getMessage();
      // org.antlr.works.plugin.intellij.PIFileType.getHighlighter(Lcom/intellij/openapi/project/Project;Lcom/intellij/openapi/vfs/VirtualFile;)Lcom/intellij/openapi/fileTypes/SyntaxHighlighter;
      int pos = s.indexOf('(');
      if (pos >= 0) {
        s = s.substring(0, pos);
        pos = s.lastIndexOf('.');
        if (pos >= 0) {
          s = s.substring(0, pos);
          if (PluginManager.isPluginClass(s)) {
            return PluginManager.getPluginByClassName(s);
          }
        }
      }
    }

    else if (t instanceof ExtensionException) {
      String className = ((ExtensionException)t).getExtensionClass().getName();
      if (PluginManager.isPluginClass(className)) {
        return PluginManager.getPluginByClassName(className);
      }
    }

    return null;
  }

  @NotNull
  private static String diagnosePluginDetection(String className, PluginId id) {
    String msg = "Detected plugin " + id + " by class " + className;
    IdeaPluginDescriptor descriptor = PluginManager.getPlugin(id);
    if (descriptor != null) {
      msg += "; ideaLoader=" + descriptor.getUseIdeaClassLoader();
      
      ClassLoader loader = descriptor.getPluginClassLoader();
      msg += "; loader=" + loader;
      if (loader instanceof PluginClassLoader) {
        msg += "; loaded class: " + ((PluginClassLoader)loader).hasLoadedClass(className);
      }
    }
    return msg;
  }

  private class ClearFatalsAction extends AbstractAction {
    protected ClearFatalsAction() {
      super(DiagnosticBundle.message("error.dialog.clear.action"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myMessagePool.clearFatals();
      doOKAction();
    }

    public void update() {
      putValue(NAME, DiagnosticBundle.message(myMergedMessages.size() > 1 ? "error.dialog.clear.all.action" : "error.dialog.clear.action"));
      setEnabled(!myMergedMessages.isEmpty());
    }
  }

  private class BlameAction extends AbstractAction {
    protected BlameAction() {
      super(DiagnosticBundle.message("error.report.to.jetbrains.action"));
    }

    public void update() {
      AbstractMessage logMessage = getSelectedMessage();
      if (logMessage != null) {
        ErrorReportSubmitter submitter = getSubmitter(logMessage.getThrowable());
        if (submitter != null) {
          putValue(NAME, submitter.getReportActionText());
          setEnabled(!(logMessage.isSubmitting() || logMessage.isSubmitted()));
          return;
        }
      }
      putValue(NAME, DiagnosticBundle.message("error.report.to.jetbrains.action"));
      setEnabled(false);
    }

    public void actionPerformed(ActionEvent e) {
      boolean closeDialog = myMergedMessages.size() == 1;
      final AbstractMessage logMessage = getSelectedMessage();
      boolean reportingStarted = reportMessage(logMessage, closeDialog);
      if (closeDialog) {
        if (reportingStarted) {
          doOKAction();
        }
      }
      else {
        rebuildHeaders();
        updateControls();
      }
    }

    private boolean reportMessage(final AbstractMessage logMessage, final boolean dialogClosed) {
      final ErrorReportSubmitter submitter = getSubmitter(logMessage.getThrowable());
      if (submitter == null) return false;

      logMessage.setSubmitting(true);
      if (!dialogClosed) {
        updateControls();
      }
      Container parentComponent;
      if (dialogClosed) {
        IdeFrame ideFrame = UIUtil.getParentOfType(IdeFrame.class, getContentPane());
        parentComponent = ideFrame.getComponent();
      }
      else {
        parentComponent = getContentPane();
      }

      return submitter.submit(
        getEvents(logMessage), logMessage.getAdditionalInfo(), parentComponent, new Consumer<SubmittedReportInfo>() {
          @Override
          public void consume(final SubmittedReportInfo submittedReportInfo) {
            logMessage.setSubmitting(false);
            logMessage.setSubmitted(submittedReportInfo);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (!dialogClosed) {
                  updateOnSubmit();
                }
              }
            });
          }
        });
    }

    private IdeaLoggingEvent[] getEvents(final AbstractMessage logMessage) {
      if (logMessage instanceof GroupedLogMessage) {
        final List<AbstractMessage> messages = ((GroupedLogMessage)logMessage).getMessages();
        IdeaLoggingEvent[] res = new IdeaLoggingEvent[messages.size()];
        for (int i = 0; i < res.length; i++) {
          res[i] = getEvent(messages.get(i));
        }
        return res;
      }
      return new IdeaLoggingEvent[]{getEvent(logMessage)};
    }

    private IdeaLoggingEvent getEvent(final AbstractMessage logMessage) {
      if (logMessage instanceof LogMessageEx) {
        return ((LogMessageEx)logMessage).toEvent();
      }
      return new IdeaLoggingEvent(logMessage.getMessage(), logMessage.getThrowable()) {
        @Override
        public AbstractMessage getData() {
          return logMessage;
        }
      };
    }
  }

  protected void updateOnSubmit() {
    updateControls();
  }

  public void calcData(DataKey key, DataSink sink) {
    if (CURRENT_TRACE_KEY == key) {
      final AbstractMessage message = getSelectedMessage();
      if (message != null) {
        sink.put(CURRENT_TRACE_KEY, getDetailsText(message));
      }
    }
  }

  @Nullable
  public static ErrorReportSubmitter getSubmitter(final Throwable throwable) {
    if (throwable instanceof MessagePool.TooManyErrorsException || throwable instanceof AbstractMethodError) {
      return null;
    }
    final PluginId pluginId = findPluginId(throwable);
    final ErrorReportSubmitter[] reporters;
    try {
      reporters = Extensions.getExtensions(ExtensionPoints.ERROR_HANDLER_EP);
    }
    catch (Throwable t) {
      return null;
    }
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    if (plugin == null || PluginManagerMain.isJetBrainsPlugin(plugin)) {
      return getCorePluginSubmitter(reporters);
    }
    for (ErrorReportSubmitter reporter : reporters) {
      final PluginDescriptor descriptor = reporter.getPluginDescriptor();
      if (descriptor != null && Comparing.equal(pluginId, descriptor.getPluginId())) {
        return reporter;
      }
    }
    return null;
  }

  @Nullable
  private static ErrorReportSubmitter getCorePluginSubmitter(ErrorReportSubmitter[] reporters) {
    for (ErrorReportSubmitter reporter : reporters) {
      final PluginDescriptor descriptor = reporter.getPluginDescriptor();
      if (descriptor == null || PluginId.getId(PluginManagerCore.CORE_PLUGIN_ID) == descriptor.getPluginId()) {
        return reporter;
      }
    }
    return null;
  }

  @Override
  public void doOKAction() {
    onClose();
    super.doOKAction();
  }

  private void onClose() {
    markAllAsRead();
    PropertiesComponent.getInstance().setValue(ACTIVE_TAB_OPTION, String.valueOf(myTabs.getSelectedIndex()));
  }

  @Override
  public void doCancelAction() {
    onClose();
    super.doCancelAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "IdeErrosDialog";
  }

  private static String getThrowableHashCode(Throwable exception) {
    try {
      return md5(StringUtil.getThrowableText(exception), "stack-trace");
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error(e);
      return "";
    }
  }

  private static String md5(String buffer, @NonNls String key) throws NoSuchAlgorithmException {
    MessageDigest md5 = MessageDigest.getInstance("MD5");
    md5.update(buffer.getBytes());
    byte[] code = md5.digest(key.getBytes());
    BigInteger bi = new BigInteger(code).abs();
    return bi.abs().toString(16);
  }

  private class AnalyzeAction extends AbstractAction {
    private final AnAction myAnalyze;

    public AnalyzeAction(AnAction analyze) {
      super(analyze.getTemplatePresentation().getText());
      putValue(Action.MNEMONIC_KEY, analyze.getTemplatePresentation().getMnemonic());
      myAnalyze = analyze;
    }

    public void update() {
      setEnabled(getSelectedMessage() != null);
    }

    public void actionPerformed(ActionEvent e) {
      final DataContext dataContext = ((DataManagerImpl)DataManager.getInstance()).getDataContextTest((Component)e.getSource());

      AnActionEvent event = new AnActionEvent(
        null, dataContext,
        ActionPlaces.UNKNOWN,
        myAnalyze.getTemplatePresentation(),
        ActionManager.getInstance(),
        e.getModifiers()
      );

      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        myAnalyze.actionPerformed(event);
        doOKAction();
      }
    }
  }
}
