/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.HeaderlessTabbedPane;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashSet;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

public class IdeErrorsDialog extends DialogWrapper implements MessagePoolListener, DataProvider {
  private static final Logger LOG = Logger.getInstance(IdeErrorsDialog.class.getName());
  private final boolean myInternalMode;
  @NonNls private static final String ACTIVE_TAB_OPTION = IdeErrorsDialog.class.getName() + "activeTab";
  public static final DataKey<String> CURRENT_TRACE_KEY = DataKey.create("current_stack_trace_key");
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
  private JPanel myAttachments;

  private int myIndex = 0;
  private final List<ArrayList<AbstractMessage>> myMergedMessages = new ArrayList<>();
  private List<AbstractMessage> myRawMessages;
  private final MessagePool myMessagePool;
  private final Set<AbstractMessage> myMessagesWithIncludedAttachments = new THashSet<>(1);

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
    Task.Backgroundable task = new Task.Backgroundable(null, "Loading Developers List", true) {
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

  @Override
  public void newEntryAdded() {
    SwingUtilities.invokeLater(() -> {
      rebuildHeaders();
      updateControls();
    });
  }

  @Override
  public void poolCleared() {
    SwingUtilities.invokeLater(() -> doOKAction());
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

    @Override
    public void actionPerformed(AnActionEvent e) {
      goForward();
    }

    @Override
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

    @Override
    public void actionPerformed(AnActionEvent e) {
      goBack();
    }

    @Override
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
    ActionToolbar backToolbar = ActionManager.getInstance().createActionToolbar("IdeErrorsBack", goBack, true);
    backToolbar.getComponent().setBorder(JBUI.Borders.empty());
    backToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myBackButtonPanel.add(backToolbar.getComponent(), BorderLayout.CENTER);

    DefaultActionGroup goForward = new DefaultActionGroup();
    ForwardAction forward = new ForwardAction();
    goForward.add(forward);
    ActionToolbar forwardToolbar = ActionManager.getInstance().createActionToolbar("IdeErrorsForward", goForward, true);
    forwardToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    forwardToolbar.getComponent().setBorder(JBUI.Borders.empty());
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
      myDetailsTabForm = new DetailsTabForm(null);
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
      myDetailsTabForm = new DetailsTabForm(myAnalyzeAction);
      myDetailsTabForm.setCommentsAreaVisible(true);
      myDetailsTabForm.addCommentsListener(commentsListener);
    }

    myTabs.addTab(DiagnosticBundle.message("error.details.tab.title"), myDetailsTabForm.getContentPane());

    myAttachmentsTabForm = new AttachmentsTabForm();
    myAttachmentsTabForm.addInclusionListener(new ChangeListener() {
      @Override
      public void stateChanged(final ChangeEvent e) {
        updateAttachmentWarning(getSelectedMessage());
      }
    });
    myAttachments.add(myAttachmentsTabForm.getContentPane(), BorderLayout.CENTER);

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
          JetBrainsAccountDialogKt.showJetBrainsAccountDialog(getRootPane()).show();
          updateCredentialsPane(getSelectedMessage());
        }
      }
    });

    myAttachmentWarningLabel.setIcon(UIUtil.getBalloonWarningIcon());
    myAttachmentWarningLabel.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
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
    if (pluginId != null) {
      DisablePluginWarningDialog.disablePlugin(pluginId, getRootPane());
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

    if (!myMessagesWithIncludedAttachments.contains(message) &&
        (Registry.is("ide.diagnostics.suggest.sending.all.attachments") || myInternalMode)) {
      for(Attachment attachment:message.getAllAttachments()) attachment.setIncluded(true);
      myMessagesWithIncludedAttachments.add(message);
    }
    
    final List<Attachment> includedAttachments = message.getIncludedAttachments();
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
      ErrorReportSubmitter submitter = getSubmitter(message.getThrowable());
      if (submitter instanceof ITNReporter) {
        myCredentialsPanel.setVisible(true);
        Credentials credentials = ErrorReportConfigurable.getCredentials();
        if (CredentialAttributesKt.isFulfilled(credentials)) {
          assert credentials != null;
          myCredentialsLabel.setHtmlText(DiagnosticBundle.message("diagnostic.error.report.submit.report.as", credentials.getUserName()));
        }
        else {
          myCredentialsLabel.setHtmlText(DiagnosticBundle.message("diagnostic.error.report.submit.error.anonymously"));
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
        if (plugin == null || PluginManagerMain.isDevelopedByJetBrains(plugin)) {
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
            final String target = (StringUtil.equals(contactInfo, plugin.getVendorEmail()) ? "mailto:" : "") + contactInfo;
            myForeignPluginWarningLabel.setHyperlinkTarget(target);
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
          if (!message.getAllAttachments().isEmpty()) {
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

      List<Attachment> attachments = message != null ? message.getAllAttachments() : Collections.emptyList();
      myAttachmentsTabForm.getContentPane().setVisible(!attachments.isEmpty());
      myAttachmentsTabForm.setAttachments(attachments);
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
      return message.getMessage() + "\n" + message.getThrowableText();
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
    JComponent result = null;
    if (selectedIndex == 0) {
      result = myInternalMode ? myDetailsTabForm.getPreferredFocusedComponent() : myCommentsTabForm.getPreferredFocusedComponent();
    }
    else if (selectedIndex == 1 && !myInternalMode) {
      result = myDetailsTabForm.getPreferredFocusedComponent();
    }

    return result != null ? result : super.getPreferredFocusedComponent();
  }

  private static Map<String, ArrayList<AbstractMessage>> mergeMessages(List<AbstractMessage> aErrors) {
    Map<String, ArrayList<AbstractMessage>> hash2Messages = new LinkedHashMap<>();
    for (final AbstractMessage each : aErrors) {
      final String hashCode = getThrowableHashCode(each.getThrowable());
      ArrayList<AbstractMessage> list;
      if (hash2Messages.containsKey(hashCode)) {
        list = hash2Messages.get(hashCode);
      }
      else {
        list = new ArrayList<>();
        hash2Messages.put(hashCode, list);
      }
      list.add(0, each);
    }
    return hash2Messages;
  }

  @Nullable
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

        if (PluginManagerCore.isPluginClass(className)) {
          return PluginManagerCore.getPluginByClassName(className);
        }
      }
    }
    else if (t instanceof ClassNotFoundException) {
      // check is class from plugin classes
      if (t.getMessage() != null) {
        String className = t.getMessage();

        if (PluginManagerCore.isPluginClass(className)) {
          return PluginManagerCore.getPluginByClassName(className);
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
          if (PluginManagerCore.isPluginClass(s)) {
            return PluginManagerCore.getPluginByClassName(s);
          }
        }
      }
    }

    else if (t instanceof ExtensionException) {
      String className = ((ExtensionException)t).getExtensionClass().getName();
      if (PluginManagerCore.isPluginClass(className)) {
        return PluginManagerCore.getPluginByClassName(className);
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

    @Override
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
        parentComponent = ideFrame != null ? ideFrame.getComponent() : WindowManager.getInstance().findVisibleFrame();
      }
      else {
        parentComponent = getContentPane();
      }

      return submitter.submit(
        getEvents(logMessage), logMessage.getAdditionalInfo(), parentComponent, submittedReportInfo -> {
          logMessage.setSubmitting(false);
          logMessage.setSubmitted(submittedReportInfo);
          ApplicationManager.getApplication().invokeLater(() -> {
            if (!dialogClosed) {
              updateOnSubmit();
            }
          });
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

  @Override
  public Object getData(String dataId) {
    if (CURRENT_TRACE_KEY.is(dataId)) {
      final AbstractMessage message = getSelectedMessage();
      if (message != null) {
        return getDetailsText(message);
      }
    }
    return null;
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
    if (plugin == null) {
      return getCorePluginSubmitter(reporters);
    }
    for (ErrorReportSubmitter reporter : reporters) {
      final PluginDescriptor descriptor = reporter.getPluginDescriptor();
      if (descriptor != null && Comparing.equal(pluginId, descriptor.getPluginId())) {
        return reporter;
      }
    }
    if (PluginManagerMain.isDevelopedByJetBrains(plugin)) {
      return getCorePluginSubmitter(reporters);
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
    md5.update(buffer.getBytes(StandardCharsets.UTF_8));
    byte[] code = md5.digest(key.getBytes(StandardCharsets.UTF_8));
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

    @Override
    public void actionPerformed(ActionEvent e) {
      DataContext dataContext = ((DataManagerImpl)DataManager.getInstance()).getDataContextTest((Component)e.getSource());
      AnActionEvent event = AnActionEvent.createFromAnAction(myAnalyze, null, ActionPlaces.UNKNOWN, dataContext);
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null) {
        myAnalyze.actionPerformed(event);
        doOKAction();
      }
    }
  }
}
