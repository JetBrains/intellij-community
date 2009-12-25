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

import com.intellij.CommonBundle;
import com.intellij.errorreport.ErrorReportSender;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.bean.NotifierBean;
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NewBuildException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * @author max
 */
public class ITNReporter extends ErrorReportSubmitter {
  private static int previousExceptionThreadId = 0;
  private static boolean wasException = false;
  @NonNls private static final String URL_HEADER = "http://www.intellij.net/tracker/idea/viewSCR?publicId=";

  public String getReportActionText() {
    return DiagnosticBundle.message("error.report.to.jetbrains.action");
  }

  public SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parentComponent) {
    return sendError(events[0], parentComponent);
  }

  /**
     * @noinspection ThrowablePrintStackTrace
     */
  private static SubmittedReportInfo sendError(IdeaLoggingEvent event, Component parentComponent) {
    NotifierBean notifierBean = new NotifierBean();
    ErrorBean errorBean = new ErrorBean();
    errorBean.autoInit();
    errorBean.setLastAction(IdeaLogger.ourLastActionId);

    int threadId = 0;
    SubmittedReportInfo.SubmissionStatus submissionStatus = SubmittedReportInfo.SubmissionStatus.FAILED;

    final DataContext dataContext = DataManager.getInstance().getDataContext(parentComponent);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    String description = "";
    do {
      // prepare
      try {
        ErrorReportSender sender = ErrorReportSender.getInstance();

        sender.prepareError(project, event.getThrowable());

        EAPSendErrorDialog dlg = new EAPSendErrorDialog();
        dlg.setErrorDescription(description);
        dlg.show();

        @NonNls String itnLogin = ErrorReportConfigurable.getInstance().ITN_LOGIN;
        @NonNls String itnPassword = ErrorReportConfigurable.getInstance().getPlainItnPassword();
        if (itnLogin.trim().length() == 0 && itnPassword.trim().length() == 0) {
          itnLogin = "idea_anonymous";
          itnPassword = "guest";
        }
        notifierBean.setItnLogin(itnLogin);
        notifierBean.setItnPassword(itnPassword);

        description = dlg.getErrorDescription();
        String message = event.getMessage();

        @NonNls StringBuilder descBuilder = new StringBuilder();
        if (description.length() > 0) {
          descBuilder.append("User description: ").append(description).append("\n");
        }
        if (message != null) {
          descBuilder.append("Error message: ").append(message).append("\n");
        }

        Throwable t = event.getThrowable();
        if (t != null) {
          final PluginId pluginId = IdeErrorsDialog.findPluginId(t);
          if (pluginId != null) {
            final IdeaPluginDescriptor ideaPluginDescriptor = ApplicationManager.getApplication().getPlugin(pluginId);
            if (ideaPluginDescriptor != null && !ideaPluginDescriptor.isBundled()) {
              descBuilder.append("Plugin ").append(ideaPluginDescriptor.getName()).append(" version: ").append(ideaPluginDescriptor.getVersion()).append("\n");
            }
          }
        }

        if (previousExceptionThreadId != 0) {
          descBuilder.append("Previous exception is: ").append(URL_HEADER).append(previousExceptionThreadId).append("\n");
        }
        if (wasException) {
          descBuilder.append("There was at least one exception before this one.\n");
        }

        errorBean.setDescription(descBuilder.toString());

        if (dlg.isShouldSend()) {
          threadId = sender.sendError(notifierBean, errorBean);
          previousExceptionThreadId = threadId;
          wasException = true;
          submissionStatus = SubmittedReportInfo.SubmissionStatus.NEW_ISSUE;

          Messages.showInfoMessage(parentComponent,
                                   DiagnosticBundle.message("error.report.confirmation"),
                                   ReportMessages.ERROR_REPORT);
          break;
        }
        else {
          break;
        }

      }
      catch (NoSuchEAPUserException e) {
        if (Messages.showYesNoDialog(parentComponent, DiagnosticBundle.message("error.report.authentication.failed"),
                                     ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
          break;
        }
      }
      catch (InternalEAPException e) {
        if (Messages.showYesNoDialog(parentComponent, DiagnosticBundle.message("error.report.posting.failed", e.getMessage()),
                                     ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
          break;
        }
      }
      catch (IOException e) {
        if (!IOExceptionDialog.showErrorDialog(DiagnosticBundle.message("error.report.exception.title"),
                                               DiagnosticBundle.message("error.report.failure.message"))) {
          break;
        }
      }
      catch (NewBuildException e) {
        Messages.showMessageDialog(parentComponent,
                                   DiagnosticBundle.message("error.report.new.eap.build.message", e.getMessage()), CommonBundle.getWarningTitle(),
                                   Messages.getWarningIcon());
        break;
      }
      catch (Exception e) {
        if (Messages.showYesNoDialog(JOptionPane.getRootFrame(), DiagnosticBundle.message("error.report.sending.failure"),
                                     ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
          break;
        }
      }

    }
    while (true);

    return new SubmittedReportInfo(submissionStatus != SubmittedReportInfo.SubmissionStatus.FAILED ? URL_HEADER + threadId : null,
                                   String.valueOf(threadId),
                                   submissionStatus);
  }
}
