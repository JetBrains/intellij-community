/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author kir, max
 */

import com.intellij.ExtensionPoints;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

public class IdeErrorsDialog extends DialogWrapper implements MessagePoolListener, TypeSafeDataProvider {
  public static DataKey<String> CURRENT_TRACE_KEY = DataKey.create("current_stack_trace_key");

  private static final Logger LOG = Logger.getInstance("#com.intellij.diagnostic.IdeErrorsDialog");
  private JTextPane myDetailsPane;
  private List<AbstractMessage> myFatalErrors;
  private final List<ArrayList<AbstractMessage>> myModel = new ArrayList<ArrayList<AbstractMessage>>();
  private final MessagePool myMessagePool;
  private JLabel myCountLabel;
  private JLabel myBlameLabel;
  private JLabel myInfoLabel;
  private JCheckBox myImmediatePopupCheckbox;

  private int myIndex = 0;
  @NonNls public static final String IMMEDIATE_POPUP_OPTION = "IMMEDIATE_FATAL_ERROR_POPUP";

  public IdeErrorsDialog(MessagePool messagePool) {
    super(JOptionPane.getRootFrame(), false);

    myMessagePool = messagePool;

    init();
  }

  public void newEntryAdded() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        rebuildHeaders();
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

  protected Action[] createActions() {
    return new Action[]{new ShutdownAction(), new ClearFatalsAction(), new CloseAction()};
  }

  @Override
  protected Action[] createLeftSideActions() {
    if (((ApplicationEx)ApplicationManager.getApplication()).isInternal()) {
      final AnAction analyze = ActionManager.getInstance().getAction("AnalyzeStacktraceOnError");
      if (analyze != null) {
        return new Action[] {new AbstractAction(analyze.getTemplatePresentation().getText()) {
          public void actionPerformed(ActionEvent e) {
            final DataContext dataContext = ((DataManagerImpl)DataManager.getInstance()).getDataContextTest((Component)e.getSource());

            AnActionEvent event = new AnActionEvent(
              null, dataContext,
              ActionPlaces.UNKNOWN,
              analyze.getTemplatePresentation(),
              ActionManager.getInstance(),
              e.getModifiers()
            );

            final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
            setEnabled(project != null);
            if (project != null) {
              analyze.actionPerformed(event);
              doOKAction();
            }
          }
        }};
      }
    }

    return new Action[0];
  }

  public void calcData(DataKey key, DataSink sink) {
    if (CURRENT_TRACE_KEY == key) {
      final AbstractMessage msg = getMessageAt(myIndex);
      if (msg != null) {
        sink.put(CURRENT_TRACE_KEY, msg.getMessage() + msg.getThrowableText());
      }
    }
  }

  private ActionToolbar createNavigationToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();

    BackAction back = new BackAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), getRootPane());
    group.add(back);

    ForwardAction forward = new ForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), getRootPane());
    group.add(forward);


    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
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
    updateBlameLabel();
    updateInfoLabel();
    updateDetailsPane();
  }

  private void updateInfoLabel() {
    final AbstractMessage message = getMessageAt(myIndex);
    if (message != null) {
      StringBuffer txt = new StringBuffer();
      txt.append(DiagnosticBundle.message("error.list.message.info",
                 DateFormatUtil.formatDate(new Date(), message.getDate()), myModel.get(myIndex).size()));

      if (message.isSubmitted()) {
        final SubmittedReportInfo info = message.getSubmissionInfo();
        if (info.getStatus() == SubmittedReportInfo.SubmissionStatus.FAILED) {
          txt.append(DiagnosticBundle.message("error.list.message.submission.failed"));
        }
        else {
          if (info.getLinkText() != null) {
            txt.append(DiagnosticBundle.message("error.list.message.submitted.as.link", info.getLinkText()));
            if (info.getStatus() == SubmittedReportInfo.SubmissionStatus.DUPLICATE) {
              txt.append(DiagnosticBundle.message("error.list.message.duplicate"));
            }
          }
          else {
            txt.append(DiagnosticBundle.message("error.list.message.submitted"));
          }
        }
        txt.append(". ");
      }
      else if (!message.isRead()) {
        txt.append(DiagnosticBundle.message("error.list.message.unread"));
      }
      myInfoLabel.setText(txt.toString());
    }
    else {
      myInfoLabel.setText("");
    }
  }

  private void updateBlameLabel() {
    final AbstractMessage message = getMessageAt(myIndex);
    if (message != null && !(message.getThrowable() instanceof MessagePool.TooManyErrorsException)) {
      final PluginId pluginId = findPluginId(message.getThrowable());
      if (pluginId == null) {
        if (message.getThrowable() instanceof AbstractMethodError) {
          myBlameLabel.setText(DiagnosticBundle.message("error.list.message.blame.unknown.plugin"));
        }
        else {
          myBlameLabel.setText(DiagnosticBundle.message("error.list.message.blame.core",
                                                        ApplicationNamesInfo.getInstance().getProductName()));
        }
      }
      else {
        final Application app = ApplicationManager.getApplication();
        myBlameLabel.setText(DiagnosticBundle.message("error.list.message.blame.plugin", app.getPlugin(pluginId).getName()));
      }
    }
    else {
      myBlameLabel.setText("");
    }
  }

  private void updateDetailsPane() {
    final AbstractMessage message = getMessageAt(myIndex);
    if (message != null) {
      showMessageDetails(message);
    }
    else {
      hideMessageDetails();
    }
  }

  private void updateCountLabel() {
    myCountLabel.setText(DiagnosticBundle.message("error.list.message.index.count", Integer.toString(myIndex + 1), myModel.size()));
  }

  private class BackAction extends AnAction implements DumbAware {
    public BackAction() {
      super(DiagnosticBundle.message("error.list.back.action"), null, IconLoader.getIcon("/actions/back.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      goBack();
    }


    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex > 0);
    }
  }

  private class ForwardAction extends AnAction implements DumbAware {
    public ForwardAction() {
      super(DiagnosticBundle.message("error.list.forward.action"), null, IconLoader.getIcon("/actions/forward.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      goForward();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex < myModel.size() - 1);
    }
  }

  protected JComponent createCenterPanel() {
    setTitle(DiagnosticBundle.message("error.list.title"));

    JPanel root = new JPanel(new BorderLayout());
    JPanel top = new JPanel(new BorderLayout());
    JPanel toolbar = new JPanel(new FlowLayout());

    myImmediatePopupCheckbox = new JCheckBox(DiagnosticBundle.message("error.list.popup.immediately.checkbox"));
    myImmediatePopupCheckbox.setSelected(PropertiesComponent.getInstance().isTrueValue(IMMEDIATE_POPUP_OPTION));

    myCountLabel = new JLabel();
    myBlameLabel = new JLabel();
    myInfoLabel = new JLabel();
    ActionToolbar navToolbar = createNavigationToolbar();
    toolbar.add(navToolbar.getComponent());
    toolbar.add(myCountLabel);
    top.add(toolbar, BorderLayout.WEST);

    JPanel blamePanel = new JPanel(new FlowLayout());
    blamePanel.add(myBlameLabel);
    final ActionToolbar blameToolbar = createBlameToolbar();
    blamePanel.add(blameToolbar.getComponent());
    top.add(blamePanel, BorderLayout.EAST);

    root.add(top, BorderLayout.NORTH);

    myDetailsPane = new JTextPane();
    myDetailsPane.setEditable(false);
    JPanel infoPanel = new JPanel(new BorderLayout());
    JPanel gapPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
    gapPanel.add(myInfoLabel);
    infoPanel.add(gapPanel, BorderLayout.NORTH);
    infoPanel.add(new JBScrollPane(myDetailsPane), BorderLayout.CENTER);
    root.add(infoPanel, BorderLayout.CENTER);
    root.add(myImmediatePopupCheckbox, BorderLayout.SOUTH);

    root.setPreferredSize(new Dimension(600, 550));

    rebuildHeaders();
    moveSelectionToEarliestMessage();
    updateControls();
    return root;
  }

  private ActionToolbar createBlameToolbar() {
    DefaultActionGroup blameGroup = new DefaultActionGroup();
    final BlameAction blameAction = new BlameAction();
    blameGroup.add(blameAction);
    blameAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), getRootPane());

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, blameGroup, true);
  }

  private AbstractMessage getMessageAt(int idx) {
    if (idx < 0 || idx >= myModel.size()) return null;
    return myModel.get(idx).get(0);
  }

  private void moveSelectionToEarliestMessage() {
    myIndex = 0;
    for (int i = 0; i < myModel.size(); i++) {
      final AbstractMessage each = getMessageAt(i);
      if (!each.isRead()) {
        myIndex = i;
        break;
      }
    }

    updateControls();
  }

  private void rebuildHeaders() {
    myModel.clear();
    myFatalErrors = myMessagePool.getFatalErrors(true, true);

    Map<String, ArrayList<AbstractMessage>> hash2Messages = buildHashcode2MessageListMap(myFatalErrors);

    for (final ArrayList<AbstractMessage> abstractMessages : hash2Messages.values()) {
      myModel.add(abstractMessages);
    }

    updateControls();
  }

  private static Map<String, ArrayList<AbstractMessage>> buildHashcode2MessageListMap(List<AbstractMessage> aErrors) {
    Map<String, ArrayList<AbstractMessage>> hash2Messages = new LinkedHashMap<String, ArrayList<AbstractMessage>>();
    for (final AbstractMessage each : aErrors) {
      final String hashcode = getThrowableHashCode(each.getThrowable());
      ArrayList<AbstractMessage> list;
      if (hash2Messages.containsKey(hashcode)) {
        list = hash2Messages.get(hashcode);
      }
      else {
        list = new ArrayList<AbstractMessage>();
        hash2Messages.put(hashcode, list);
      }
      list.add(0, each);
    }
    return hash2Messages;
  }

  private void showMessageDetails(AbstractMessage aMessage) {
    if (aMessage.getThrowable() instanceof MessagePool.TooManyErrorsException) {
      myDetailsPane.setText(aMessage.getThrowable().getMessage());
    }
    else {
      myDetailsPane.setText(new StringBuffer().append(aMessage.getMessage()).append("\n").append(aMessage.getThrowableText()).toString());
    }
    if (myDetailsPane.getCaret() != null) { // Upon some strange circumstances caret may be missing from the text component making the following line fail with NPE.
      myDetailsPane.setCaretPosition(0);
    }
  }

  private void hideMessageDetails() {
    myDetailsPane.setText("");
  }

  @Nullable
  public static PluginId findPluginId(Throwable t) {
    if (t instanceof PluginException) {
      return ((PluginException)t).getPluginId();
    }

    StackTraceElement[] elements = t.getStackTrace();
    for (StackTraceElement element : elements) {
      String className = element.getClassName();
      if (PluginManager.isPluginClass(className)) {
        return PluginManager.getPluginByClassName(className);
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

  private class ShutdownAction extends AbstractAction {
    public ShutdownAction() {
      super(DiagnosticBundle.message("error.list.shutdown.action"));
    }

    public void actionPerformed(ActionEvent e) {
      myMessagePool.setJvmIsShuttingDown();
      LaterInvocator.invokeLater(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().exit();
        }
      }, ModalityState.NON_MODAL);
      doOKAction();
    }
  }

  private class ClearFatalsAction extends AbstractAction {
    public ClearFatalsAction() {
      super(DiagnosticBundle.message("error.list.clear.action"));
    }

    public void actionPerformed(ActionEvent e) {
      myMessagePool.clearFatals();
      doOKAction();
    }
  }

  private class BlameAction extends AnAction implements DumbAware {
    public BlameAction() {
      super(DiagnosticBundle.message("error.list.submit.action"),
            DiagnosticBundle.message("error.list.submit.action.description"), IconLoader.getIcon("/actions/startDebugger.png"));
    }

    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final AbstractMessage logMessage = getMessageAt(myIndex);
      if (logMessage == null) {
        presentation.setEnabled(false);
        return;
      }

      final ErrorReportSubmitter submitter = getSubmitter(logMessage.getThrowable());
      if (logMessage.isSubmitted() || submitter == null) {
        presentation.setEnabled(false);
        return;
      }
      presentation.setEnabled(true);
      presentation.setDescription(submitter.getReportActionText());
    }

    public void actionPerformed(AnActionEvent e) {
      final AbstractMessage logMessage = getMessageAt(myIndex);
      reportMessage(logMessage);
      rebuildHeaders();
      updateControls();
    }

    private void reportMessage(final AbstractMessage logMessage) {
      ErrorReportSubmitter submitter = getSubmitter(logMessage.getThrowable());

      if (submitter != null) {
        logMessage.setSubmitted(submitter.submit(getEvents(logMessage), getContentPane()));
      }
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
      return new IdeaLoggingEvent(logMessage.getMessage(), logMessage.getThrowable());
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
    ErrorReportSubmitter submitter = null;
    for (ErrorReportSubmitter reporter : reporters) {
      final PluginDescriptor descriptor = reporter.getPluginDescriptor();
      if (pluginId == null && (descriptor == null || PluginId.getId("com.intellij") == descriptor.getPluginId())
          || descriptor != null && Comparing.equal(pluginId, descriptor.getPluginId())) {
        submitter = reporter;
      }
    }
    return submitter;
  }

  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue(IMMEDIATE_POPUP_OPTION, String.valueOf(myImmediatePopupCheckbox.isSelected()));
    markAllAsRead();
    super.doOKAction();
  }

  private void markAllAsRead() {
    for (AbstractMessage each : myFatalErrors) {
      each.setRead(true);
    }
  }

  public void doCancelAction() {
    PropertiesComponent.getInstance().setValue(IMMEDIATE_POPUP_OPTION, String.valueOf(myImmediatePopupCheckbox.isSelected()));
    markAllAsRead();
    super.doCancelAction();
  }

  protected class CloseAction extends AbstractAction {
    public CloseAction() {
      putValue(Action.NAME, DiagnosticBundle.message("error.list.close.action"));
    }

    public void actionPerformed(ActionEvent e) {
      doOKAction();
    }
  }

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

  private static String md5 (String buffer, @NonNls String key) throws NoSuchAlgorithmException {
    MessageDigest md5 = MessageDigest.getInstance("MD5");
    md5.update(buffer.getBytes());
    byte [] code = md5.digest(key.getBytes());
    BigInteger bi = new BigInteger(code).abs();
    return bi.abs().toString(16);
  }
}
