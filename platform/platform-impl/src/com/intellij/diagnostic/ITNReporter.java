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
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.idea.IdeaLogger;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

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
    // obsolete API
    return new SubmittedReportInfo(null, "0", SubmittedReportInfo.SubmissionStatus.FAILED);
  }

  @Override
  public void submitAsync(IdeaLoggingEvent[] events,
                          String additionalInfo,
                          Component parentComponent,
                          Consumer<SubmittedReportInfo> consumer) {
    sendError(events [0], additionalInfo, parentComponent, consumer);
  }

  /**
     * @noinspection ThrowablePrintStackTrace
     */
  private static void sendError(IdeaLoggingEvent event,
                                String additionalInfo,
                                final Component parentComponent,
                                final Consumer<SubmittedReportInfo> callback) {
    String newBuild = ErrorReportSender.checkNewBuild();
    if (newBuild != null) {
      Messages.showMessageDialog(parentComponent,
                                 DiagnosticBundle.message("error.report.new.eap.build.message", newBuild), CommonBundle.getWarningTitle(),
                                 Messages.getWarningIcon());
      callback.consume(new SubmittedReportInfo(null, "0", SubmittedReportInfo.SubmissionStatus.FAILED));
    }

    ErrorBean errorBean = new ErrorBean(event.getThrowable(), IdeaLogger.ourLastActionId);

    doSubmit(event, parentComponent, callback, errorBean, additionalInfo);
  }

  private static void doSubmit(final IdeaLoggingEvent event,
                               final Component parentComponent,
                               final Consumer<SubmittedReportInfo> callback,
                               final ErrorBean errorBean,
                               final String description) {
    final DataContext dataContext = DataManager.getInstance().getDataContext(parentComponent);
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    final ErrorReportConfigurable errorReportConfigurable = ErrorReportConfigurable.getInstance();
    if (!errorReportConfigurable.KEEP_ITN_PASSWORD &&
        !StringUtil.isEmpty(errorReportConfigurable.ITN_LOGIN) &&
        StringUtil.isEmpty(errorReportConfigurable.getPlainItnPassword())) {
      final JetBrainsAccountDialog dlg = new JetBrainsAccountDialog(parentComponent);
      dlg.show();
      if (!dlg.isOK()) {
        return;
      }
    }

    @NonNls String login = errorReportConfigurable.ITN_LOGIN;
    @NonNls String password = errorReportConfigurable.getPlainItnPassword();
    if (login.trim().length() == 0 && password.trim().length() == 0) {
      login = "idea_anonymous";
      password = "guest";
    }

    errorBean.setDescription(buildDescription(event, description));

    ErrorReportSender.sendError(project, login, password, errorBean, new Consumer<Integer>() {
      @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
      @Override
      public void consume(Integer threadId) {
        previousExceptionThreadId = threadId;
        wasException = true;
        callback.consume(new SubmittedReportInfo(URL_HEADER + threadId, String.valueOf(threadId),
                                                 SubmittedReportInfo.SubmissionStatus.NEW_ISSUE));
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            ReportMessages.GROUP.createNotification(ReportMessages.ERROR_REPORT,
                                                         DiagnosticBundle.message("error.report.confirmation"),
                                                         NotificationType.INFORMATION, null).notify(project);
          }
        });
      }
    }, new Consumer<Exception>() {
      @Override
      public void consume(final Exception e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            String msg;
            if (e instanceof NoSuchEAPUserException) {
              msg = DiagnosticBundle.message("error.report.authentication.failed");
            }
            else if (e instanceof InternalEAPException) {
              msg = DiagnosticBundle.message("error.report.posting.failed", e.getMessage());
            }
            else {
              msg = DiagnosticBundle.message("error.report.sending.failure");
            }
            if (Messages.showYesNoDialog(project, msg,
                                         ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != 0) {
              callback.consume(new SubmittedReportInfo(null, "0", SubmittedReportInfo.SubmissionStatus.FAILED));
            }
            else {
              if (e instanceof NoSuchEAPUserException) {
                final JetBrainsAccountDialog dialog;
                if (parentComponent.isShowing()) {
                  dialog = new JetBrainsAccountDialog(parentComponent);
                }
                else {
                  dialog = new JetBrainsAccountDialog(project);
                }
                dialog.show();
              }
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  doSubmit(event, parentComponent, callback, errorBean, description);
                }
              });
            }
          }
        });
      }
    });
  }

  private static String buildDescription(IdeaLoggingEvent event, String description) {
    String message = event.getMessage();

    @NonNls StringBuilder descBuilder = new StringBuilder();
    if (!StringUtil.isEmpty(description)) {
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

    if (IdeaLogger.ourLastActionId != null) {
      descBuilder.append("Last action: ").append(IdeaLogger.ourLastActionId);
    }

    if (previousExceptionThreadId != 0) {
      descBuilder.append("Previous exception is: ").append(URL_HEADER).append(previousExceptionThreadId).append("\n");
    }
    if (wasException) {
      descBuilder.append("There was at least one exception before this one.\n");
    }
    return descBuilder.toString();
  }
}
