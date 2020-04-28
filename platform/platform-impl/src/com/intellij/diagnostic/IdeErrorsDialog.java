// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.CommonBundle;
import com.intellij.ExtensionPoints;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.plugins.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.*;
import com.intellij.ui.components.ComponentsKt;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.BooleanFunction;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.util.List;
import java.util.*;
import java.util.zip.CRC32;

import static com.intellij.openapi.util.Pair.pair;
import static java.awt.GridBagConstraints.*;

public class IdeErrorsDialog extends DialogWrapper implements MessagePoolListener, DataProvider {
  private static final Logger LOG = Logger.getInstance(IdeErrorsDialog.class);

  public static final DataKey<String> CURRENT_TRACE_KEY = DataKey.create("current_stack_trace_key");

  private static final String STACKTRACE_ATTACHMENT = "stacktrace.txt";
  private static final String ACCEPTED_NOTICES_KEY = "exception.accepted.notices";
  private static final String ACCEPTED_NOTICES_SEPARATOR = ":";
  private static final String DISABLE_PLUGIN_URL = "#disable";
  private static final String EA_PLUGIN_ID = "com.intellij.sisyphus";

  private final MessagePool myMessagePool;
  private final Project myProject;
  private final boolean myAssigneeVisible;
  private final Set<String> myAcceptedNotices;
  private final List<MessageCluster> myMessageClusters = new ArrayList<>();  // exceptions with the same stacktrace
  private int myIndex, myLastIndex = -1;
  private Long myDevelopersTimestamp;

  private JLabel myCountLabel;
  private JTextComponent myInfoLabel;
  private JLabel myDetailsLabel;
  private JTextComponent myForeignPluginWarningLabel;
  private JBTextArea myCommentArea;
  private AttachmentsList myAttachmentsList;
  private JTextArea myAttachmentArea;
  private JPanel myAssigneePanel;
  private PrivacyNoticeComponent myPrivacyNotice;
  private ComboBox<Developer> myAssigneeCombo;
  private JTextComponent myCredentialsLabel;

  IdeErrorsDialog(@NotNull MessagePool messagePool, @Nullable Project project, @Nullable LogMessage defaultMessage) {
    super(project, true);
    myMessagePool = messagePool;
    myProject = project;
    myAssigneeVisible = ApplicationManager.getApplication().isInternal() || PluginManagerCore.isPluginInstalled(PluginId.getId(EA_PLUGIN_ID));

    setTitle(DiagnosticBundle.message("error.list.title"));
    setModal(false);
    getOKAction().putValue(FOCUSED_ACTION, Boolean.TRUE);
    init();
    setCancelButtonText(CommonBundle.message("close.action.name"));

    if (myAssigneeVisible) {
      loadDevelopersList();
    }

    String rawValue = PropertiesComponent.getInstance().getValue(ACCEPTED_NOTICES_KEY, "");
    myAcceptedNotices = new LinkedHashSet<>(StringUtil.split(rawValue, ACCEPTED_NOTICES_SEPARATOR));

    updateMessages();
    myIndex = selectMessage(defaultMessage);
    updateControls();

    messagePool.addListener(this);
  }

  private void loadDevelopersList() {
    ErrorReportConfigurable configurable = ErrorReportConfigurable.getInstance();
    Developers developers = configurable.getDeveloper();
    if (developers != null && developers.isUpToDateAt(System.currentTimeMillis())) {
      setDevelopers(developers);
    }
    else {
      new Task.Backgroundable(null, DiagnosticBundle.message("progress.title.loading.developers.list"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            Developers updatedDevelopers = new Developers(ITNProxy.fetchDevelopers(indicator), System.currentTimeMillis());
            UIUtil.invokeLaterIfNeeded(() -> {
              configurable.setDeveloper(updatedDevelopers);
              if (isShowing()) {
                setDevelopers(updatedDevelopers);
              }
            });
          }
          catch (UnknownHostException e) {
            LOG.debug(e);
            UIUtil.invokeLaterIfNeeded(() -> {
              if (isShowing()) {
                setDevelopers(developers);
              }
            });
          }
          catch (IOException e) { LOG.warn(e); }
        }
      }.queue();
    }
  }

  private void setDevelopers(@Nullable Developers developers) {
    if (developers != null) {
      myAssigneeCombo.setModel(new CollectionComboBoxModel<>(developers.getDevelopers()));
      myDevelopersTimestamp = developers.getTimestamp();
    }
  }

  private int selectMessage(@Nullable LogMessage defaultMessage) {
    if (defaultMessage != null) {
      for (int i = 0; i < myMessageClusters.size(); i++) {
        if (myMessageClusters.get(i).messages.contains(defaultMessage)) return i;
      }
    }
    else {
      for (int i = 0; i < myMessageClusters.size(); i++) {
        if (!myMessageClusters.get(i).messages.get(0).isRead()) return i;
      }
      for (int i = 0; i < myMessageClusters.size(); i++) {
        for (AbstractMessage message : myMessageClusters.get(i).messages) {
          if (!message.isRead()) return i;
        }
      }
      for (int i = 0; i < myMessageClusters.size(); i++) {
        if (!myMessageClusters.get(i).messages.get(0).isSubmitted()) return i;
      }
    }
    return 0;
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    myCountLabel = new JBLabel();
    myInfoLabel = ComponentsKt.htmlComponent("", null, null, null, false, e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && DISABLE_PLUGIN_URL.equals(e.getDescription())) {
        disablePlugin();
      }
      else {
        BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e);
      }
    });
    myDetailsLabel = new JBLabel();
    myDetailsLabel.setForeground(UIUtil.getContextHelpForeground());
    myForeignPluginWarningLabel = ComponentsKt.htmlComponent();

    JPanel controls = new JPanel(new BorderLayout());
    controls.add(actionToolbar("IdeErrorsBack", new BackAction()), BorderLayout.WEST);
    controls.add(actionToolbar("IdeErrorsForward", new ForwardAction()), BorderLayout.EAST);

    JPanel panel = new JPanel(new GridBagLayout());
    panel.add(controls, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, NORTH, NONE, JBUI.emptyInsets(), 0, 0));
    panel.add(myCountLabel, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, NORTH, HORIZONTAL, JBUI.insets(0, 10), 0, 2));
    panel.add(myInfoLabel, new GridBagConstraints(2, 0, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    panel.add(myDetailsLabel, new GridBagConstraints(3, 0, 1, 1, 0.0, 0.0, NORTHEAST, NONE, JBUI.emptyInsets(), 0, 0));
    panel.add(myForeignPluginWarningLabel, new GridBagConstraints(1, 1, 4, 1, 1.0, 0.0, WEST, HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    return panel;
  }

  private static JComponent actionToolbar(@NonNls String id, AnAction action) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(id, new DefaultActionGroup(action), true);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    toolbar.getComponent().setBorder(JBUI.Borders.empty());
    return toolbar.getComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    myCommentArea = new JBTextArea(5, 0);
    myCommentArea.getEmptyText().setText(DiagnosticBundle.message("error.dialog.comment.prompt"));
    myCommentArea.setMargin(JBUI.insets(2));
    myCommentArea.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        selectedMessage().setAdditionalInfo(myCommentArea.getText().trim());
      }
    });

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
      protected void textChanged(@NotNull DocumentEvent e) {
        if (myAttachmentsList.getSelectedIndex() == 0) {
          String detailsText = myAttachmentArea.getText();
          MessageCluster cluster = selectedCluster();
          cluster.detailsText = detailsText;
          setOKActionEnabled(cluster.canSubmit() && !StringUtil.isEmptyOrSpaces(detailsText));
        }
      }
    });

    if (myAssigneeVisible) {
      myAssigneeCombo = new ComboBox<>();
      myAssigneeCombo.setRenderer(SimpleListCellRenderer.create("<none>", Developer::getDisplayText));
      myAssigneeCombo.setPrototypeDisplayValue(new Developer(0, StringUtil.repeatSymbol('-', 30)));
      myAssigneeCombo.addItemListener(e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          Developer developer = (Developer)e.getItem();
          selectedMessage().setAssigneeId(developer == null ? null : developer.getId());
        }
      });
      myAssigneeCombo.setSwingPopup(false);
      myAssigneePanel = new JPanel();
      myAssigneePanel.add(new JBLabel(DiagnosticBundle.message("label.assignee")));
      myAssigneePanel.add(myAssigneeCombo);
    }

    myCredentialsLabel = ComponentsKt.htmlComponent("height sample", null, null, null, false, e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        ErrorReportSubmitter submitter = selectedCluster().submitter;
        if (submitter != null) {
          submitter.changeReporterAccount(getRootPane());
          updateControls();
        }
      }
    });
    if (myAssigneeVisible) {
      int topOffset = (myAssigneePanel.getPreferredSize().height - myCredentialsLabel.getPreferredSize().height) / 2;
      myCredentialsLabel.setBorder(JBUI.Borders.emptyTop(topOffset));
    }

    myPrivacyNotice = new PrivacyNoticeComponent(DiagnosticBundle.message("error.dialog.notice.label"), DiagnosticBundle.message("error.dialog.notice.label.expanded"));

    JPanel commentPanel = new JPanel(new BorderLayout());
    commentPanel.setBorder(JBUI.Borders.emptyTop(5));
    commentPanel.add(scrollPane(myCommentArea, 0, 0), BorderLayout.CENTER);

    JPanel attachmentsPanel = new JPanel(new BorderLayout(JBUIScale.scale(5), 0));
    attachmentsPanel.setBorder(JBUI.Borders.emptyTop(5));
    attachmentsPanel.add(scrollPane(myAttachmentsList, 150, 350), BorderLayout.WEST);
    attachmentsPanel.add(scrollPane(myAttachmentArea, 500, 350), BorderLayout.CENTER);

    JPanel accountRow = new JPanel(new GridBagLayout());
    accountRow.setBorder(JBUI.Borders.empty(6, 0));
    accountRow.add(myCredentialsLabel, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    if (myAssigneeVisible) accountRow.add(myAssigneePanel, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, NORTHEAST, NONE, JBUI.emptyInsets(), 0, 0));
    JPanel bottomRow = new JPanel(new BorderLayout());
    bottomRow.add(accountRow, BorderLayout.NORTH);
    bottomRow.add(myPrivacyNotice, BorderLayout.CENTER);

    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.setPreferredSize(JBUI.size(800, 400));
    rootPanel.setMinimumSize(JBUI.size(680, 400));
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

  @Override
  protected Action @NotNull [] createActions() {
    if (SystemInfo.isWindows) {
      return new Action[]{getOKAction(), new ClearErrorsAction(), getCancelAction()};
    }
    else {
      return new Action[]{new ClearErrorsAction(), getCancelAction(), getOKAction()};
    }
  }

  @Override
  protected Action @NotNull [] createLeftSideActions() {
    if (myAssigneeVisible && myProject != null && !myProject.isDefault()) {
      AnAction action = ActionManager.getInstance().getAction("Unscramble");
      if (action != null) {
        return new Action[]{new AnalyzeAction(action)};
      }
    }
    return new Action[0];
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
    List<AbstractMessage> messages = myMessagePool.getFatalErrors(true, true);
    Map<Long, MessageCluster> clusters = new LinkedHashMap<>();
    for (AbstractMessage message : messages) {
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
    if (myAssigneeVisible) {
      updateAssigneePanel(cluster);
    }
    updateCredentialsPanel(submitter);

    setOKActionEnabled(cluster.canSubmit());
    setOKButtonText(submitter != null ? submitter.getReportActionText() : DiagnosticBundle.message("error.report.impossible.action"));
    setOKButtonTooltip(submitter != null ? null : DiagnosticBundle.message("error.report.impossible.tooltip"));
  }

  private void updateLabels(@NotNull MessageCluster cluster) {
    AbstractMessage message = cluster.first;

    myCountLabel.setText(DiagnosticBundle.message("error.list.message.index.count", myIndex + 1, myMessageClusters.size()));

    Throwable t = message.getThrowable();
    if (t instanceof MessagePool.TooManyErrorsException) {
      myInfoLabel.setText(t.getMessage());
      myDetailsLabel.setVisible(false);
      myForeignPluginWarningLabel.setVisible(false);
      myPrivacyNotice.setVisible(false);
      return;
    }

    PluginId pluginId = cluster.pluginId;
    IdeaPluginDescriptor plugin = cluster.plugin;

    StringBuilder info = new StringBuilder();

    if (pluginId != null) {
      String name = plugin != null ? plugin.getName() : pluginId.toString();
      if (plugin != null && (!plugin.isBundled() || plugin.allowBundledUpdate())) {
        info.append(DiagnosticBundle.message("error.list.message.blame.plugin.version", name, plugin.getVersion()));
      }
      else {
        info.append(DiagnosticBundle.message("error.list.message.blame.plugin", name));
      }
    }
    else if (t instanceof AbstractMethodError) {
      info.append(DiagnosticBundle.message("error.list.message.blame.unknown.plugin"));
    }
    else if (t instanceof Freeze) {
      info.append(DiagnosticBundle.message("error.list.message.blame.freeze"));
    }
    else if (t instanceof JBRCrash) {
      info.append(DiagnosticBundle.message("error.list.message.blame.jbr.crash"));
    }
    else {
      info.append(DiagnosticBundle.message("error.list.message.blame.core", ApplicationNamesInfo.getInstance().getProductName()));
    }

    if (pluginId != null && !ApplicationInfoEx.getInstanceEx().isEssentialPlugin(pluginId)) {
      info.append(' ').append("<a style=\"white-space: nowrap;\" href=\"" + DISABLE_PLUGIN_URL + "\">")
        .append(DiagnosticBundle.message("error.list.disable.plugin")).append("</a>");
    }

    if (message.isSubmitted()) {
      info.append(' ').append("<span style=\"white-space: nowrap;\">");
      appendSubmissionInformation(message.getSubmissionInfo(), info);
      info.append("</span>");
    }
    else if (message.isSubmitting()) {
      info.append(' ').append(DiagnosticBundle.message("error.list.message.submitting"));
    }

    myInfoLabel.setText(info.toString());

    int count = cluster.messages.size();
    String date = DateFormatUtil.formatPrettyDateTime(cluster.messages.get(count - 1).getDate());
    myDetailsLabel.setText(DiagnosticBundle.message("error.list.message.info", date, count));

    ErrorReportSubmitter submitter = cluster.submitter;
    if (submitter == null && plugin != null && !PluginManager.getInstance().isDevelopedByJetBrains(plugin)) {
      myForeignPluginWarningLabel.setVisible(true);
      String vendor = plugin.getVendor();
      String vendorUrl = plugin.getVendorUrl();
      if (StringUtil.isEmptyOrSpaces(vendorUrl)) {
        String vendorEmail = plugin.getVendorEmail();
        if (!StringUtil.isEmptyOrSpaces(vendorEmail)) {
          vendorUrl = "mailto:" + StringUtil.trimStart(vendorEmail, "mailto:");
        }
      }
      if (!StringUtil.isEmpty(vendor) && !StringUtil.isEmpty(vendorUrl)) {
        myForeignPluginWarningLabel.setText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning", vendor, vendorUrl));
      }
      else if (!StringUtil.isEmptyOrSpaces(vendorUrl)) {
        myForeignPluginWarningLabel.setText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning.unnamed", vendorUrl));
      }
      else {
        myForeignPluginWarningLabel.setText(DiagnosticBundle.message("error.dialog.foreign.plugin.warning.unknown"));
      }
    }
    else {
      myForeignPluginWarningLabel.setVisible(false);
    }

    String notice = submitter != null ? submitter.getPrivacyNoticeText() : null;
    if (notice != null) {
      myPrivacyNotice.setVisible(true);
      String hash = Integer.toHexString(StringUtil.stringHashCodeIgnoreWhitespaces(notice));
      myPrivacyNotice.setExpanded(!myAcceptedNotices.contains(hash));
      myPrivacyNotice.setPrivacyPolicy(notice);
    }
    else {
      myPrivacyNotice.setVisible(false);
    }
  }

  private void updateDetails(MessageCluster cluster) {
    AbstractMessage message = cluster.first;
    boolean canReport = cluster.canSubmit();

    if (myLastIndex != myIndex) {
      myCommentArea.setText(message.getAdditionalInfo());

      myAttachmentsList.clear();
      myAttachmentsList.addItem(STACKTRACE_ATTACHMENT, true);
      for (Attachment attachment : message.getAllAttachments()) {
        myAttachmentsList.addItem(attachment.getName(), attachment.isIncluded());
      }
      myAttachmentsList.setSelectedIndex(0);

      myLastIndex = myIndex;
    }

    myCommentArea.setEditable(canReport);
    myCommentArea.putClientProperty("StatusVisibleFunction", canReport ? null : (BooleanFunction<JBTextArea>) c -> false);
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
        int assigneeIndex = getAssigneeIndex(assignee);
        if (assigneeIndex != -1) {
          myAssigneeCombo.setSelectedIndex(assigneeIndex);
        }
        else {
          cluster.first.setAssigneeId(null);
        }
      }
    }
    else {
      myAssigneePanel.setVisible(false);
    }
  }

  private int getAssigneeIndex(Integer assigneeId) {
    for (int index = 0; index < myAssigneeCombo.getItemCount(); index++) {
      if (Objects.equals(assigneeId, myAssigneeCombo.getItemAt(index).getId())) {
        return index;
      }
    }

    return -1;
  }

  private void updateCredentialsPanel(@Nullable ErrorReportSubmitter submitter) {
    String account = submitter != null ? submitter.getReporterAccount() : null;
    if (account != null) {
      myCredentialsLabel.setVisible(true);
      if (!account.isEmpty()) {
        myCredentialsLabel.setText(DiagnosticBundle.message("error.dialog.submit.named", account));
      }
      else {
        myCredentialsLabel.setText(DiagnosticBundle.message("error.dialog.submit.anonymous"));
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

    message.setAssigneeVisible(myAssigneeVisible);
    message.setDevelopersTimestamp(myDevelopersTimestamp);
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
      IdeFrame frame = ComponentUtil.getParentOfType((Class<? extends IdeFrame>)IdeFrame.class, (Component)parentComponent);
      parentComponent = frame != null ? frame.getComponent() : WindowManager.getInstance().findVisibleFrame();
    }

    boolean accepted = submitter.submit(events, message.getAdditionalInfo(), parentComponent, reportInfo -> {
      message.setSubmitted(reportInfo);
      UIUtil.invokeLaterIfNeeded(this::updateOnSubmit);
    });
    if (!accepted) {
      message.setSubmitting(false);
    }
    return accepted;
  }

  private void disablePlugin() {
    IdeaPluginDescriptor plugin = selectedCluster().plugin;
    if (plugin != null) {
      confirmDisablePlugins(myProject, Collections.singleton(plugin));
    }
  }

  public static void confirmDisablePlugins(@Nullable Project project, @NotNull Set<IdeaPluginDescriptor> pluginsToDisable) {
    boolean hasDependents = morePluginsAffected(pluginsToDisable);

    boolean canRestart = ApplicationManager.getApplication().isRestartCapable();

    String message;
    if (pluginsToDisable.size() == 1) {
      IdeaPluginDescriptor plugin = pluginsToDisable.iterator().next();
      //noinspection HardCodedStringLiteral
      message = "<html>" +
                DiagnosticBundle.message("error.dialog.disable.prompt", plugin.getName()) + "<br/>" +
                DiagnosticBundle.message(hasDependents ? "error.dialog.disable.prompt.deps" : "error.dialog.disable.prompt.lone") + "<br/><br/>" +
                DiagnosticBundle.message(canRestart ? "error.dialog.disable.plugin.can.restart" : "error.dialog.disable.plugin.no.restart") +
                "</html>";
    }
    else {
      //noinspection HardCodedStringLiteral
      message = "<html>" +
                DiagnosticBundle.message("error.dialog.disable.prompt.multiple") + "<br/>" +
                DiagnosticBundle.message(hasDependents ? "error.dialog.disable.prompt.deps.multiple" : "error.dialog.disable.prompt.lone.multiple") + "<br/><br/>" +
                DiagnosticBundle.message(canRestart ? "error.dialog.disable.plugin.can.restart" : "error.dialog.disable.plugin.no.restart") +
                "</html>";
    }
    String title = DiagnosticBundle.message("error.dialog.disable.plugin.title");
    String disable = DiagnosticBundle.message("error.dialog.disable.plugin.action.disable");
    String cancel = IdeBundle.message("button.cancel");

    boolean doDisable, doRestart;
    if (canRestart) {
      String restart = DiagnosticBundle.message("error.dialog.disable.plugin.action.disableAndRestart");
      int result = Messages.showYesNoCancelDialog(project, message, title, disable, restart, cancel, Messages.getQuestionIcon());
      doDisable = result == Messages.YES || result == Messages.NO;
      doRestart = result == Messages.NO;
    }
    else {
      int result = Messages.showYesNoDialog(project, message, title, disable, cancel, Messages.getQuestionIcon());
      doDisable = result == Messages.YES;
      doRestart = false;
    }

    if (doDisable) {
      for (IdeaPluginDescriptor plugin: pluginsToDisable) {
        PluginManagerCore.disablePlugin(plugin.getPluginId());
      }
      if (doRestart) {
        ApplicationManager.getApplication().restart();
      }
    }
  }

  private static boolean morePluginsAffected(@NotNull Set<IdeaPluginDescriptor> pluginsToDisable) {
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    for (IdeaPluginDescriptor rootDescriptor : PluginManagerCore.getPlugins()) {
      if (!rootDescriptor.isEnabled() || pluginsToDisable.contains(rootDescriptor)) {
        continue;
      }

      if (!PluginManagerCore.processAllDependencies((IdeaPluginDescriptorImpl)rootDescriptor, false, pluginIdMap, descriptor -> {
        if (!descriptor.isEnabled()) {
          // if disabled, no need to process it's dependencies
          return FileVisitResult.SKIP_SUBTREE;
        }
        return pluginsToDisable.contains(descriptor) ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
      })) {
        return true;
      }
    }
    return false;
  }

  protected void updateOnSubmit() {
    if (isShowing()) {
      updateControls();
    }
  }

  /* UI components */

  private class BackAction extends AnAction implements DumbAware, LightEditCompatible {
    BackAction() {
      super(IdeBundle.message("button.previous"), null, AllIcons.Actions.Back);
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB);
      if (action != null) {
        registerCustomShortcutSet(action.getShortcutSet(), getRootPane(), getDisposable());
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myIndex > 0);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myLastIndex = myIndex--;
      updateControls();
    }
  }

  private class ForwardAction extends AnAction implements DumbAware, LightEditCompatible {
    ForwardAction() {
      super(IdeBundle.message("button.next"), null, AllIcons.Actions.Forward);
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB);
      if (action != null) {
        registerCustomShortcutSet(action.getShortcutSet(), getRootPane(), getDisposable());
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myIndex < myMessageClusters.size() - 1);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myLastIndex = myIndex++;
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
      super(ActionsBundle.actionText(ActionManager.getInstance().getId(analyze)));
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
      addItem(item, item + "  ", selected);
    }

    public void setEditable(boolean editable) {
      myEditable = editable;
    }

    @Override
    protected boolean isEnabled(int index) {
      return myEditable && index > 0;
    }
  }

  /* interfaces */

  @Override
  public void newEntryAdded() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (isShowing()) {
        updateMessages();
        updateControls();
      }
    });
  }

  @Override
  public void poolCleared() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (isShowing()) {
        doCancelAction();
      }
    });
  }

  @Override
  public Object getData(@NotNull String dataId) {
    return CURRENT_TRACE_KEY.is(dataId) ? selectedMessage().getThrowableText() : null;
  }

  /* helpers */

  private static final class MessageCluster {
    private final AbstractMessage first;
    private final @Nullable PluginId pluginId;
    private final @Nullable IdeaPluginDescriptor plugin;
    private final @Nullable ErrorReportSubmitter submitter;
    private String detailsText;
    private final List<AbstractMessage> messages = new ArrayList<>();

    private MessageCluster(AbstractMessage message) {
      first = message;
      pluginId = PluginUtil.getInstance().findPluginId(message.getThrowable());
      plugin = PluginManagerCore.getPlugin(pluginId);
      submitter = getSubmitter(message.getThrowable(), plugin);
      detailsText = detailsText();
    }

    private String detailsText() {
      Throwable t = first.getThrowable();
      if (t instanceof MessagePool.TooManyErrorsException) {
        return t.getMessage();
      }

      String userMessage = first.getMessage();
      String stacktrace = first.getThrowableText();
      return StringUtil.isEmptyOrSpaces(userMessage) ? stacktrace : userMessage + "\n\n" + stacktrace;
    }

    private boolean isUnsent() {
      return !(first.isSubmitted() || first.isSubmitting());
    }

    private boolean canSubmit() {
      return submitter != null && isUnsent();
    }

    private Pair<String, String> decouple() {
      String className = first.getThrowable().getClass().getName();
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

  /** @deprecated use {@link #getPlugin(IdeaLoggingEvent)} instead, and take the plugin name and version from the returned instance */
  @Deprecated
  public static @Nullable Pair<String, String> getPluginInfo(@NotNull IdeaLoggingEvent event) {
    IdeaPluginDescriptor plugin = getPlugin(event);
    return plugin != null && (!plugin.isBundled() || plugin.allowBundledUpdate()) ? pair(plugin.getName(), plugin.getVersion()) : null;
  }

  @Nullable
  public static IdeaPluginDescriptor getPlugin(@NotNull IdeaLoggingEvent event) {
    IdeaPluginDescriptor plugin = null;
    if (event instanceof IdeaReportingEvent) {
      plugin = ((IdeaReportingEvent)event).getPlugin();
    }
    else {
      Throwable t = event.getThrowable();
      if (t != null) {
        plugin = PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(t));
      }
    }
    return plugin;
  }

  /**
   * @deprecated use {@link PluginUtil#findPluginId}
   */
  @Nullable
  @Deprecated
  public static PluginId findPluginId(@NotNull Throwable t) {
    return PluginUtil.getInstance().findPluginId(t);
  }

  static @Nullable ErrorReportSubmitter getSubmitter(@NotNull Throwable t, @Nullable PluginId pluginId) {
    return getSubmitter(t, PluginManagerCore.getPlugin(pluginId));
  }

  private static ErrorReportSubmitter getSubmitter(Throwable t, @Nullable IdeaPluginDescriptor plugin) {
    if (t instanceof MessagePool.TooManyErrorsException || t instanceof AbstractMethodError) {
      return null;
    }

    List<ErrorReportSubmitter> reporters;
    try {
      reporters = ExtensionPoints.ERROR_HANDLER_EP.getExtensionList();
    }
    catch (Throwable ignored) {
      return null;
    }

    if (plugin != null) {
      for (ErrorReportSubmitter reporter : reporters) {
        PluginDescriptor descriptor = reporter.getPluginDescriptor();
        if (descriptor != null && plugin.getPluginId() == descriptor.getPluginId()) {
          return reporter;
        }
      }
    }

    if (plugin == null || PluginManager.getInstance().isDevelopedByJetBrains(plugin)) {
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
      out.append(DiagnosticBundle.message("error.list.message.submission.failed"));
    }
    else if (info.getURL() != null && info.getLinkText() != null) {
      out.append(DiagnosticBundle.message("error.list.message.submitted.as.link", info.getURL(), info.getLinkText()));
      if (info.getStatus() == SubmittedReportInfo.SubmissionStatus.DUPLICATE) {
        out.append(DiagnosticBundle.message("error.list.message.duplicate"));
      }
    }
    else {
      out.append(DiagnosticBundle.message("error.list.message.submitted"));
    }
  }
}