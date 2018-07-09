// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.CommonBundle;
import com.intellij.ExtensionPoints;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.zip.CRC32;

import static com.intellij.openapi.util.Pair.pair;
import static java.awt.GridBagConstraints.*;

public class IdeErrorsDialog extends DialogWrapper implements MessagePoolListener, DataProvider {
  private static final Logger LOG = Logger.getInstance(IdeErrorsDialog.class);

  public static final DataKey<String> CURRENT_TRACE_KEY = DataKey.create("current_stack_trace_key");

  private static final String STACKTRACE_ATTACHMENT = "stacktrace.txt";
  private static final String ACCEPTED_NOTICES_KEY = "exception.accepted.notices";
  private static final String ACCEPTED_NOTICES_SEPARATOR = ":";
  private static List<Developer> ourDevelopersList = Collections.emptyList();

  private final MessagePool myMessagePool;
  private final Project myProject;
  private final boolean myInternalMode;
  private final Set<String> myAcceptedNotices;
  private final List<MessageCluster> myMessageClusters = new ArrayList<>();  // exceptions with the same stacktrace
  private int myIndex;

  private JLabel myCountLabel;
  private HyperlinkLabel.Croppable myInfoLabel;
  private HyperlinkLabel.Croppable myDisableLink;
  private HyperlinkLabel.Croppable myForeignPluginWarningLabel;
  private JTextArea myCommentArea;
  private AttachmentsList myAttachmentsList;
  private JTextArea myAttachmentArea;
  private JPanel myAssigneePanel;
  private JPanel myNoticePanel;
  private HideableDecorator myNoticeDecorator;
  private JEditorPane myNoticeArea;
  private ComboBox<Developer> myAssigneeCombo;
  private HyperlinkLabel myCredentialsLabel;

  public IdeErrorsDialog(@NotNull MessagePool messagePool, @Nullable Project project, @Nullable LogMessage defaultMessage) {
    super(project, true);
    myMessagePool = messagePool;
    myProject = project;
    myInternalMode = ApplicationManager.getApplication().isInternal();

    setTitle(DiagnosticBundle.message("error.list.title"));
    setModal(false);
    init();
    setCancelButtonText(CommonBundle.message("close.action.name"));

    if (myInternalMode) {
      loadDevelopersList();
    }

    String rawValue = PropertiesComponent.getInstance().getValue(ACCEPTED_NOTICES_KEY, "");
    myAcceptedNotices = ContainerUtil.newLinkedHashSet(StringUtil.split(rawValue, ACCEPTED_NOTICES_SEPARATOR));

    updateMessages();
    selectMessage(defaultMessage);
    updateControls();

    messagePool.addListener(this);
  }

  private void loadDevelopersList() {
    if (!ourDevelopersList.isEmpty()) {
      myAssigneeCombo.setModel(new CollectionComboBoxModel<>(ourDevelopersList));
    }
    else {
      new Task.Backgroundable(null, "Loading Developers List", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            List<Developer> developers = ITNProxy.fetchDevelopers(indicator);
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            ourDevelopersList = developers;
            UIUtil.invokeLaterIfNeeded(() -> {
              if (isShowing()) {
                myAssigneeCombo.setModel(new CollectionComboBoxModel<>(developers));
              }
            });
          }
          catch (IOException e) {
            LOG.warn(e);
          }
        }
      }.queue();
    }
  }

  private void selectMessage(@Nullable LogMessage defaultMessage) {
    for (int i = 0; i < myMessageClusters.size(); i++) {
      for (AbstractMessage message : myMessageClusters.get(i).messages) {
        if (defaultMessage != null && message == defaultMessage || defaultMessage == null && !message.isRead()) {
          myIndex = i;
          return;
        }
      }
    }
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    myCountLabel = new JBLabel();
    myInfoLabel = new HyperlinkLabel.Croppable();

    myDisableLink = new HyperlinkLabel.Croppable();
    myDisableLink.setHyperlinkText(UIUtil.removeMnemonic(DiagnosticBundle.message("error.list.disable.plugin")));
    myDisableLink.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        disablePlugin();
      }
    });

    myForeignPluginWarningLabel = new HyperlinkLabel.Croppable();

    JPanel controls = new JPanel(new BorderLayout());
    controls.add(actionToolbar("IdeErrorsBack", new BackAction()), BorderLayout.WEST);
    controls.add(myCountLabel, BorderLayout.CENTER);
    controls.add(actionToolbar("IdeErrorsForward", new ForwardAction()), BorderLayout.EAST);

    JPanel panel = new JPanel(new GridBagLayout());
    panel.add(controls, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, CENTER, NONE, JBUI.insets(2), 0, 0));
    panel.add(myInfoLabel, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.emptyInsets(), 0, 0));
    panel.add(myDisableLink, new GridBagConstraints(2, 0, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.emptyInsets(), 0, 0));
    panel.add(new JPanel(), new GridBagConstraints(3, 0, 1, 1, 1.0, 0.0, CENTER, BOTH, JBUI.emptyInsets(), 0, 0));  // expander
    panel.add(myForeignPluginWarningLabel, new GridBagConstraints(1, 1, 3, 1, 0.0, 0.0, WEST, NONE, JBUI.emptyInsets(), 0, 0));
    return panel;
  }

  private static JComponent actionToolbar(String id, AnAction action) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(id, new DefaultActionGroup(action), true);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    toolbar.getComponent().setBorder(JBUI.Borders.empty());
    return toolbar.getComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    JBLabel commentLabel = new JBLabel(DiagnosticBundle.message("error.dialog.comment.prompt"));

    myCommentArea = new JTextArea(5, 0);
    myCommentArea.setMargin(JBUI.insets(2));
    myCommentArea.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        selectedMessage().setAdditionalInfo(myCommentArea.getText().trim());
      }
    });

    JBLabel attachmentsLabel = new JBLabel(DiagnosticBundle.message("error.dialog.attachments.prompt"));

    myAttachmentsList = new AttachmentsList();
    myAttachmentsList.addListSelectionListener(e -> {
      int index = myAttachmentsList.getSelectedIndex();
      if (index < 0) {
        myAttachmentArea.setText("");
        myAttachmentArea.setEditable(false);
      }
      else if (index == 0) {
        MessageCluster cluster = selectedCluster();
        myAttachmentArea.setText(cluster.detailsText);
        myAttachmentArea.setEditable(cluster.isUnsent());
      }
      else {
        myAttachmentArea.setText(selectedMessage().getAllAttachments().get(index - 1).getDisplayText());
        myAttachmentArea.setEditable(false);
      }
      myAttachmentArea.setCaretPosition(0);
    });
    myAttachmentsList.setCheckBoxListListener((index, value) -> {
      if (index > 0) {
        selectedMessage().getAllAttachments().get(index - 1).setIncluded(value);
      }
    });

    myAttachmentArea = new JTextArea();
    myAttachmentArea.setMargin(JBUI.insets(2));
    myAttachmentArea.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myAttachmentsList.getSelectedIndex() == 0) {
          String detailsText = myAttachmentArea.getText();
          MessageCluster cluster = selectedCluster();
          cluster.detailsText = detailsText;
          setOKActionEnabled(cluster.canSubmit() && !StringUtil.isEmptyOrSpaces(detailsText));
        }
      }
    });

    if (myInternalMode) {
      myAssigneeCombo = new ComboBox<>();
      myAssigneeCombo.setRenderer(new ListCellRendererWrapper<Developer>() {
        @Override
        public void customize(JList list, Developer value, int index, boolean selected, boolean hasFocus) {
          setText(value == null ? "<none>" : value.getDisplayText());
        }
      });
      myAssigneeCombo.setPrototypeDisplayValue(new Developer(0, StringUtil.repeatSymbol('-', 30)));
      myAssigneeCombo.addItemListener(e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          Developer developer = (Developer)e.getItem();
          selectedMessage().setAssigneeId(developer == null ? null : developer.getId());
        }
      });
      new ComboboxSpeedSearch(myAssigneeCombo) {
        @Override
        protected String getElementText(Object element) {
          return element == null ? "" : ((Developer)element).getDisplayText();
        }
      };

      myAssigneePanel = new JPanel();
      myAssigneePanel.add(new JBLabel("Assignee:"));
      myAssigneePanel.add(myAssigneeCombo);
    }

    myCredentialsLabel = new HyperlinkLabel();
    myCredentialsLabel.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        JetBrainsAccountDialogKt.showJetBrainsAccountDialog(getRootPane()).show();
        updateControls();
      }
    });

    myNoticeArea = new JEditorPane();
    myNoticeArea.setEditable(false);
    myNoticeArea.setFocusable(false);
    myNoticeArea.setBackground(UIUtil.getPanelBackground());
    myNoticeArea.setEditorKit(UIUtil.getHTMLEditorKit());
    myNoticeArea.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    JPanel decoratorPanel = new JPanel(new BorderLayout());
    myNoticeDecorator = new NoticeDecorator(decoratorPanel);
    myNoticeDecorator.setContentComponent(myNoticeArea);

    JPanel commentPanel = new JPanel(new BorderLayout());
    commentPanel.setBorder(JBUI.Borders.emptyTop(5));
    commentPanel.add(commentLabel, BorderLayout.NORTH);
    commentPanel.add(scrollPane(myCommentArea, 0, 0), BorderLayout.CENTER);

    JPanel attachmentsPanel = new JPanel(new BorderLayout(JBUI.scale(5), 0));
    attachmentsPanel.setBorder(JBUI.Borders.emptyTop(5));
    attachmentsPanel.add(attachmentsLabel, BorderLayout.NORTH);
    attachmentsPanel.add(scrollPane(myAttachmentsList, 150, 350), BorderLayout.WEST);
    attachmentsPanel.add(scrollPane(myAttachmentArea, 500, 350), BorderLayout.CENTER);

    JPanel accountRow = new JPanel(new BorderLayout());
    if (myInternalMode) accountRow.add(myAssigneePanel, BorderLayout.WEST);
    accountRow.add(myCredentialsLabel, BorderLayout.EAST);
    myNoticePanel = new JPanel(new GridBagLayout());
    myNoticePanel.add(new JBLabel(UIUtil.getBalloonWarningIcon()), new GridBagConstraints(0, 0, 1, 1, 0, 0, NORTH, NONE, JBUI.insets(7, 0, 0, 5), 0, 0));
    myNoticePanel.add(decoratorPanel, new GridBagConstraints(1, 0, 1, 1, 1.0, 0, CENTER, HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    JPanel bottomRow = new JPanel(new BorderLayout());
    bottomRow.add(accountRow, BorderLayout.NORTH);
    bottomRow.add(myNoticePanel, BorderLayout.CENTER);

    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.setPreferredSize(JBUI.size(800, 400));
    rootPanel.add(commentPanel, BorderLayout.NORTH);
    rootPanel.add(attachmentsPanel, BorderLayout.CENTER);
    rootPanel.add(bottomRow, BorderLayout.SOUTH);
    return rootPanel;
  }

  private static JScrollPane scrollPane(JComponent component, int width, int height) {
    JScrollPane scrollPane = new JBScrollPane(component);
    if (width > 0 && height > 0) {
      scrollPane.setMinimumSize(JBUI.size(width, height));
    }
    return scrollPane;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    List<Action> actions = new ArrayList<>();
    if (myInternalMode && myProject != null && !myProject.isDefault()) {
      AnAction action = ActionManager.getInstance().getAction("AnalyzeStacktraceOnError");
      if (action != null) {
        actions.add(new AnalyzeAction(action));
      }
    }
    actions.add(new ClearErrorsAction());
    actions.add(getOKAction());
    actions.add(getCancelAction());
    return actions.toArray(new Action[0]);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCommentArea;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "IDE.errors.dialog";
  }

  @Override
  public void doOKAction() {
    if (getOKAction().isEnabled()) {
      boolean closeDialog = myMessageClusters.size() == 1;
      boolean reportingStarted = reportMessage(selectedCluster(), closeDialog);
      if (!closeDialog) {
        updateControls();
      }
      else if (reportingStarted) {
        super.doOKAction();
      }
    }
  }

  @Override
  protected void dispose() {
    myMessagePool.removeListener(this);
    super.dispose();
  }

  private MessageCluster selectedCluster() {
    return myMessageClusters.get(myIndex);
  }

  private AbstractMessage selectedMessage() {
    return selectedCluster().first;
  }

  private void updateMessages() {
    List<AbstractMessage> rawMessages = myMessagePool.getFatalErrors(true, true);
    Map<Long, MessageCluster> clusters = new LinkedHashMap<>();
    for (AbstractMessage raw : rawMessages) {
      AbstractMessage message = raw instanceof GroupedLogMessage ? ((GroupedLogMessage)raw).getProxyMessage() : raw;
      CRC32 digest = new CRC32();
      digest.update(ExceptionUtil.getThrowableText(message.getThrowable()).getBytes(StandardCharsets.UTF_8));
      clusters.computeIfAbsent(digest.getValue(), k -> new MessageCluster(message)).messages.add(message);
    }
    myMessageClusters.clear();
    myMessageClusters.addAll(clusters.values());
  }

  private void updateControls() {
    MessageCluster cluster = selectedCluster();
    ErrorReportSubmitter submitter = cluster.submitter;

    cluster.messages.forEach(m -> m.setRead(true));

    updateLabels(cluster);
    updateDetails(cluster);
    if (myInternalMode) {
      updateAssigneePanel(cluster);
    }
    updateCredentialsPanel(submitter);

    setOKActionEnabled(cluster.canSubmit());
    setOKButtonText(submitter != null ? submitter.getReportActionText() : DiagnosticBundle.message("error.report.impossible.action"));
    setOKButtonTooltip(submitter != null ? null : DiagnosticBundle.message("error.report.impossible.tooltip"));
  }

  private void updateLabels(MessageCluster cluster) {
    AbstractMessage message = cluster.first;

    myCountLabel.setText(DiagnosticBundle.message("error.list.message.index.count", myIndex + 1, myMessageClusters.size()));

    Throwable t = message.getThrowable();
    if (t instanceof MessagePool.TooManyErrorsException) {
      myInfoLabel.setText(t.getMessage());
      myDisableLink.setVisible(false);
      myForeignPluginWarningLabel.setVisible(false);
      myNoticePanel.setVisible(false);
      return;
    }

    PluginId pluginId = cluster.pluginId;
    IdeaPluginDescriptor plugin = cluster.plugin;

    StringBuilder info = new StringBuilder();
    String url = null;

    if (pluginId != null) {
      info.append(DiagnosticBundle.message("error.list.message.blame.plugin", plugin != null ? plugin.getName() : pluginId));
    }
    else if (t instanceof AbstractMethodError) {
      info.append(DiagnosticBundle.message("error.list.message.blame.unknown.plugin"));
    }
    else {
      info.append(DiagnosticBundle.message("error.list.message.blame.core", ApplicationNamesInfo.getInstance().getProductName()));
    }

    String date = DateFormatUtil.formatPrettyDateTime(message.getDate());
    int count = cluster.messages.size();
    info.append(' ').append(DiagnosticBundle.message("error.list.message.info", date, count));

    if (message.isSubmitted()) {
      SubmittedReportInfo submissionInfo = message.getSubmissionInfo();
      appendSubmissionInformation(submissionInfo, info);
      info.append('.');
      url = submissionInfo.getURL();
    }
    else if (message.isSubmitting()) {
      info.append(' ').append(DiagnosticBundle.message("error.list.message.submitting"));
    }

    myInfoLabel.setHtmlText(XmlStringUtil.wrapInHtml(info));
    myInfoLabel.setHyperlinkTarget(url);
    myInfoLabel.setToolTipText(url);

    myDisableLink.setVisible(pluginId != null && !ApplicationInfoEx.getInstanceEx().isEssentialPlugin(pluginId.getIdString()));

    ErrorReportSubmitter submitter = cluster.submitter;
    if (submitter == null && plugin != null && !PluginManagerMain.isDevelopedByJetBrains(plugin)) {
      myForeignPluginWarningLabel.setVisible(true);
      String vendor = plugin.getVendor();
      String contactUrl = plugin.getVendorUrl();
      String contactEmail = plugin.getVendorEmail();
      if (!StringUtil.isEmpty(vendor) && !StringUtil.isEmpty(contactUrl)) {
        myForeignPluginWarningLabel.setHtmlText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning.vendor", vendor));
        myForeignPluginWarningLabel.setHyperlinkTarget(contactUrl);
      }
      else if (!StringUtil.isEmpty(contactUrl)) {
        myForeignPluginWarningLabel.setHtmlText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning.unknown"));
        myForeignPluginWarningLabel.setHyperlinkTarget(contactUrl);
      }
      else if (!StringUtil.isEmpty(contactEmail)) {
        contactEmail = StringUtil.trimStart(contactEmail, "mailto:");
        myForeignPluginWarningLabel.setHtmlText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning.vendor", contactEmail));
        myForeignPluginWarningLabel.setHyperlinkTarget("mailto:" + contactEmail);
      }
      else {
        myForeignPluginWarningLabel.setHtmlText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning"));
        myForeignPluginWarningLabel.setHyperlinkTarget(null);
      }
      myForeignPluginWarningLabel.setToolTipText(contactUrl);
    }
    else {
      myForeignPluginWarningLabel.setVisible(false);
    }

    String notice = submitter != null ? submitter.getPrivacyNoticeText() : null;
    if (notice != null) {
      myNoticePanel.setVisible(true);
      String hash = Integer.toHexString(StringUtil.stringHashCodeIgnoreWhitespaces(notice));
      myNoticeDecorator.setOn(!myAcceptedNotices.contains(hash));
      myNoticeArea.setText(notice);
    }
    else {
      myNoticePanel.setVisible(false);
    }
  }

  private void updateDetails(MessageCluster cluster) {
    AbstractMessage message = cluster.first;
    boolean canReport = cluster.canSubmit();

    myCommentArea.setText(message.getAdditionalInfo());
    myCommentArea.setCaretPosition(0);
    myCommentArea.setEditable(canReport);

    myAttachmentsList.clear();
    myAttachmentsList.addItem(STACKTRACE_ATTACHMENT, true);
    for (Attachment attachment : message.getAllAttachments()) {
      myAttachmentsList.addItem(attachment.getName(), attachment.isIncluded());
    }
    myAttachmentsList.setSelectedIndex(0);
    myAttachmentsList.setEditable(canReport);
  }

  private void updateAssigneePanel(MessageCluster cluster) {
    if (cluster.submitter instanceof ITNReporter) {
      myAssigneePanel.setVisible(true);
      myAssigneeCombo.setEnabled(cluster.isUnsent());
      Integer assignee = cluster.first.getAssigneeId();
      if (assignee == null) {
        myAssigneeCombo.setSelectedIndex(-1);
      }
      else {
        Condition<Developer> lookup = d -> Objects.equals(assignee, d.getId());
        myAssigneeCombo.setSelectedIndex(ContainerUtil.indexOf(ourDevelopersList, lookup));
      }
    }
    else {
      myAssigneePanel.setVisible(false);
    }
  }

  private void updateCredentialsPanel(ErrorReportSubmitter submitter) {
    if (submitter instanceof ITNReporter) {
      myCredentialsLabel.setVisible(true);
      Credentials credentials = ErrorReportConfigurable.getCredentials();
      if (CredentialAttributesKt.isFulfilled(credentials)) {
        myCredentialsLabel.setHtmlText(DiagnosticBundle.message("error.dialog.submit.named", credentials.getUserName()));
      }
      else {
        myCredentialsLabel.setHtmlText(DiagnosticBundle.message("error.dialog.submit.anonymous"));
      }
    }
    else {
      myCredentialsLabel.setVisible(false);
    }
  }

  private boolean reportMessage(MessageCluster cluster, boolean dialogClosed) {
    ErrorReportSubmitter submitter = cluster.submitter;
    if (submitter == null) return false;
    AbstractMessage message = cluster.first;

    message.setSubmitting(true);

    String notice = submitter.getPrivacyNoticeText();
    if (notice != null) {
      String hash = Integer.toHexString(StringUtil.stringHashCodeIgnoreWhitespaces(notice));
      if (myAcceptedNotices.add(hash)) {
        PropertiesComponent.getInstance().setValue(ACCEPTED_NOTICES_KEY, StringUtil.join(myAcceptedNotices, ACCEPTED_NOTICES_SEPARATOR));
      }
    }

    Pair<String, String> pair = cluster.decouple();
    IdeaLoggingEvent[] events = {new IdeaReportingEvent(message, pair.first, pair.second, cluster.plugin)};

    Container parentComponent = getRootPane();
    if (dialogClosed) {
      IdeFrame frame = UIUtil.getParentOfType(IdeFrame.class, parentComponent);
      parentComponent = frame != null ? frame.getComponent() : WindowManager.getInstance().findVisibleFrame();
    }

    return submitter.submit(events, message.getAdditionalInfo(), parentComponent, reportInfo -> {
      message.setSubmitting(false);
      message.setSubmitted(reportInfo);
      UIUtil.invokeLaterIfNeeded(() -> updateOnSubmit());
    });
  }

  private void disablePlugin() {
    IdeaPluginDescriptor plugin = selectedCluster().plugin;
    if (plugin != null) {
      Ref<Boolean> hasDependants = new Ref<>(false);
      PluginManagerCore.checkDependants(plugin, PluginManager::getPlugin, dependantId -> {
        if (PluginManagerCore.CORE_PLUGIN_ID.equals(dependantId.getIdString())) {
          return true;
        }
        else {
          hasDependants.set(true);
          return false;
        }
      });
      boolean canRestart = ApplicationManager.getApplication().isRestartCapable();

      String message =
        "<html>" +
        DiagnosticBundle.message("error.dialog.disable.prompt", plugin.getName()) + "<br/>" +
        DiagnosticBundle.message(hasDependants.get() ? "error.dialog.disable.prompt.deps" : "error.dialog.disable.prompt.lone") + "<br/><br/>" +
        DiagnosticBundle.message(canRestart ? "error.dialog.disable.plugin.can.restart" : "error.dialog.disable.plugin.no.restart") +
        "</html>";
      String title = DiagnosticBundle.message("error.dialog.disable.plugin.title");
      String disable = DiagnosticBundle.message("error.dialog.disable.plugin.action.disable");
      String cancel = IdeBundle.message("button.cancel");

      boolean doDisable, doRestart;
      if (canRestart) {
        String restart = DiagnosticBundle.message("error.dialog.disable.plugin.action.disableAndRestart");
        int result = Messages.showYesNoCancelDialog(myProject, message, title, disable, restart, cancel, Messages.getQuestionIcon());
        doDisable = result == Messages.YES || result == Messages.NO;
        doRestart = result == Messages.NO;
      }
      else {
        int result = Messages.showYesNoDialog(myProject, message, title, disable, cancel, Messages.getQuestionIcon());
        doDisable = result == Messages.YES;
        doRestart = false;
      }

      if (doDisable) {
        PluginManagerCore.disablePlugin(plugin.getPluginId().getIdString());
        if (doRestart) {
          ApplicationManager.getApplication().restart();
        }
      }
    }
  }

  protected void updateOnSubmit() {
    if (isShowing()) {
      updateControls();
    }
  }

  /* UI components */

  private class BackAction extends AnAction implements DumbAware {
    public BackAction() {
      super("Previous", null, AllIcons.Actions.Back);
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB);
      if (action != null) {
        registerCustomShortcutSet(action.getShortcutSet(), getRootPane(), getDisposable());
      }
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myIndex > 0);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myIndex--;
      updateControls();
    }
  }

  private class ForwardAction extends AnAction implements DumbAware {
    public ForwardAction() {
      super("Next", null, AllIcons.Actions.Forward);
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB);
      if (action != null) {
        registerCustomShortcutSet(action.getShortcutSet(), getRootPane(), getDisposable());
      }
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myIndex < myMessageClusters.size() - 1);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myIndex++;
      updateControls();
    }
  }

  private class ClearErrorsAction extends AbstractAction {
    private ClearErrorsAction() {
      super(DiagnosticBundle.message("error.dialog.clear.all.action"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myMessagePool.clearErrors();
      doCancelAction();
    }
  }

  private class AnalyzeAction extends AbstractAction {
    private final AnAction myAnalyze;

    private AnalyzeAction(AnAction analyze) {
      super(analyze.getTemplatePresentation().getText());
      putValue(Action.MNEMONIC_KEY, analyze.getTemplatePresentation().getMnemonic());
      myAnalyze = analyze;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      DataContext ctx = DataManager.getInstance().getDataContext((Component)e.getSource());
      AnActionEvent event = AnActionEvent.createFromAnAction(myAnalyze, null, ActionPlaces.UNKNOWN, ctx);
      myAnalyze.actionPerformed(event);
      doCancelAction();
    }
  }

  private static class AttachmentsList extends CheckBoxList<String> {
    private boolean myEditable = true;

    private void addItem(String item, boolean selected) {
      super.addItem(item, item + "  ", selected);
    }

    public void setEditable(boolean editable) {
      myEditable = editable;
    }

    @Override
    protected boolean isEnabled(int index) {
      return myEditable && index > 0;
    }
  }

  private static class NoticeDecorator extends HideableDecorator {
    private NoticeDecorator(JPanel panel) {
      super(panel, "...", false);
    }

    @Override
    protected void on() {
      super.on();
      setTitle(DiagnosticBundle.message("error.dialog.notice.label.expanded"));
    }

    @Override
    protected void off() {
      super.off();
      setTitle(DiagnosticBundle.message("error.dialog.notice.label"));
    }
  }

  /* interfaces */

  @Override
  public void newEntryAdded() {
    UIUtil.invokeLaterIfNeeded(() -> {
      updateMessages();
      updateControls();
    });
  }

  @Override
  public void poolCleared() {
    UIUtil.invokeLaterIfNeeded(() -> doCancelAction());
  }

  @Override
  public void entryWasRead() { }

  @Override
  public Object getData(String dataId) {
    return CURRENT_TRACE_KEY.is(dataId) ? selectedMessage().getThrowableText() : null;
  }

  /* helpers */

  private static class MessageCluster {
    private final AbstractMessage first;
    private final @Nullable PluginId pluginId;
    private final @Nullable IdeaPluginDescriptor plugin;
    private final @Nullable ErrorReportSubmitter submitter;
    private String detailsText;
    private final List<AbstractMessage> messages = new ArrayList<>();

    private MessageCluster(AbstractMessage message) {
      first = message;
      pluginId = findPluginId(message.getThrowable());
      plugin = PluginManager.getPlugin(pluginId);
      submitter = getSubmitter(message.getThrowable(), pluginId, plugin);
      detailsText = detailsText();
    }

    private String detailsText() {
      AbstractMessage message = first;
      if (message instanceof GroupedLogMessage) {
        message = ((GroupedLogMessage)message).getMessages().get(0);
      }

      Throwable t = message.getThrowable();
      if (t instanceof MessagePool.TooManyErrorsException) {
        return t.getMessage();
      }

      String userMessage = message.getMessage(), stacktrace = message.getThrowableText();
      return StringUtil.isEmptyOrSpaces(userMessage) ? stacktrace : userMessage + "\n\n" + stacktrace;
    }

    private boolean isUnsent() {
      return !(first.isSubmitted() || first.isSubmitting());
    }

    private boolean canSubmit() {
      return submitter != null && isUnsent();
    }

    private Pair<String, String> decouple() {
      @SuppressWarnings("ThrowableNotThrown") String className = first.getThrowable().getClass().getName();
      int p = detailsText.indexOf(className);
      if (p == 0) {
        return pair(null, detailsText);
      }
      else if (p > 0 && detailsText.charAt(p - 1) == '\n') {
        return pair(detailsText.substring(0, p).trim(), detailsText.substring(p));
      }
      else {
        return pair("*** exception class was changed or removed", detailsText);
      }
    }
  }

  public static @Nullable Pair<String, String> getPluginInfo(@NotNull IdeaLoggingEvent event) {
    IdeaPluginDescriptor plugin = null;
    if (event instanceof IdeaReportingEvent) {
      plugin = ((IdeaReportingEvent)event).getPlugin();
    }
    else {
      Throwable t = event.getThrowable();
      if (t != null) {
        plugin = PluginManager.getPlugin(findPluginId(t));
      }
    }
    return plugin != null && (!plugin.isBundled() || plugin.allowBundledUpdate()) ? pair(plugin.getName(), plugin.getVersion()) : null;
  }

  public static @Nullable PluginId findPluginId(@NotNull Throwable t) {
    if (t instanceof PluginException) {
      return ((PluginException)t).getPluginId();
    }

    Set<String> visitedClassNames = ContainerUtil.newHashSet();
    for (StackTraceElement element : t.getStackTrace()) {
      if (element != null) {
        String className = element.getClassName();
        if (visitedClassNames.add(className) && PluginManagerCore.isPluginClass(className)) {
          PluginId id = PluginManagerCore.getPluginByClassName(className);
          logPluginDetection(className, id);
          return id;
        }
      }
    }

    if (t instanceof NoSuchMethodException) {
      // check is method called from plugin classes
      if (t.getMessage() != null) {
        StringBuilder className = new StringBuilder();
        StringTokenizer tok = new StringTokenizer(t.getMessage(), ".");
        while (tok.hasMoreTokens()) {
          String token = tok.nextToken();
          if (!token.isEmpty() && Character.isJavaIdentifierStart(token.charAt(0))) {
            className.append(token);
          }
        }

        PluginId pluginId = PluginManagerCore.getPluginByClassName(className.toString());
        if (pluginId != null) {
          return pluginId;
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

  private static void logPluginDetection(String className, PluginId id) {
    if (LOG.isDebugEnabled()) {
      String message = "Detected plugin " + id + " by class " + className;
      IdeaPluginDescriptor descriptor = PluginManager.getPlugin(id);
      if (descriptor != null) {
        ClassLoader loader = descriptor.getPluginClassLoader();
        message += "; loader=" + loader + '/' + loader.getClass();
        if (loader instanceof PluginClassLoader) {
          message += "; loaded class: " + ((PluginClassLoader)loader).hasLoadedClass(className);
        }
      }
      LOG.debug(message);
    }
  }

  static @Nullable ErrorReportSubmitter getSubmitter(@NotNull Throwable t) {
    PluginId pluginId = findPluginId(t);
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    return getSubmitter(t, pluginId, plugin);
  }

  private static ErrorReportSubmitter getSubmitter(Throwable t, PluginId pluginId, IdeaPluginDescriptor plugin) {
    if (t instanceof MessagePool.TooManyErrorsException || t instanceof AbstractMethodError) {
      return null;
    }

    ErrorReportSubmitter[] reporters;
    try {
      reporters = Extensions.getExtensions(ExtensionPoints.ERROR_HANDLER_EP);
    }
    catch (Throwable ignored) {
      return null;
    }

    if (plugin != null) {
      for (ErrorReportSubmitter reporter : reporters) {
        PluginDescriptor descriptor = reporter.getPluginDescriptor();
        if (descriptor != null && Comparing.equal(pluginId, descriptor.getPluginId())) {
          return reporter;
        }
      }
    }

    if (plugin == null || PluginManagerMain.isDevelopedByJetBrains(plugin)) {
      for (ErrorReportSubmitter reporter : reporters) {
        PluginDescriptor descriptor = reporter.getPluginDescriptor();
        if (descriptor == null || PluginId.getId(PluginManagerCore.CORE_PLUGIN_ID) == descriptor.getPluginId()) {
          return reporter;
        }
      }
    }

    return null;
  }

  public static void appendSubmissionInformation(@NotNull SubmittedReportInfo info, @NotNull StringBuilder out) {
    if (info.getStatus() == SubmittedReportInfo.SubmissionStatus.FAILED) {
      out.append(' ').append(DiagnosticBundle.message("error.list.message.submission.failed"));
    }
    else if (info.getURL() != null && info.getLinkText() != null) {
      out.append(' ').append(DiagnosticBundle.message("error.list.message.submitted.as.link", info.getURL(), info.getLinkText()));
      if (info.getStatus() == SubmittedReportInfo.SubmissionStatus.DUPLICATE) {
        out.append(' ').append(DiagnosticBundle.message("error.list.message.duplicate"));
      }
    }
    else {
      out.append(' ').append(DiagnosticBundle.message("error.list.message.submitted"));
    }
  }
}