/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.errorreport.error.UpdateAvailableException;
import com.intellij.errorreport.itn.ITNProxy;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.idea.IdeaLogger;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class ITNReporter extends ErrorReportSubmitter {
  private static int previousExceptionThreadId = 0;

  @Override
  public String getReportActionText() {
    return DiagnosticBundle.message("error.report.to.jetbrains.action");
  }

  @Override
  public boolean submit(@NotNull IdeaLoggingEvent[] events,
                        String additionalInfo,
                        @NotNull Component parentComponent,
                        @NotNull Consumer<SubmittedReportInfo> consumer) {
    ErrorBean errorBean = new ErrorBean(events[0].getThrowable(), IdeaLogger.ourLastActionId);
    return doSubmit(events[0], parentComponent, consumer, errorBean, additionalInfo);
  }

  /**
   * Used to enable error reporting even in release versions.
   */
  public boolean showErrorInRelease(IdeaLoggingEvent event) {
    return false;
  }

  private static boolean doSubmit(final IdeaLoggingEvent event,
                                  final Component parentComponent,
                                  final Consumer<SubmittedReportInfo> callback,
                                  final ErrorBean errorBean,
                                  final String description) {
    final DataContext dataContext = DataManager.getInstance().getDataContext(parentComponent);
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    ErrorReportConfigurable settings = ErrorReportConfigurable.getInstance();
    if (!settings.KEEP_ITN_PASSWORD && !StringUtil.isEmpty(settings.ITN_LOGIN) && StringUtil.isEmpty(settings.getPlainItnPassword())) {
      JetBrainsAccountDialog dlg = new JetBrainsAccountDialog(parentComponent);
      if (!dlg.showAndGet()) {
        return false;
      }
    }

    errorBean.setDescription(description);
    errorBean.setMessage(event.getMessage());

    if (previousExceptionThreadId != 0) {
      errorBean.setPreviousException(previousExceptionThreadId);
    }

    Throwable t = event.getThrowable();
    if (t != null) {
      final PluginId pluginId = IdeErrorsDialog.findPluginId(t);
      if (pluginId != null) {
        final IdeaPluginDescriptor ideaPluginDescriptor = PluginManager.getPlugin(pluginId);
        if (ideaPluginDescriptor != null && (!ideaPluginDescriptor.isBundled() || ideaPluginDescriptor.allowBundledUpdate())) {
          errorBean.setPluginName(ideaPluginDescriptor.getName());
          errorBean.setPluginVersion(ideaPluginDescriptor.getVersion());
        }
      }
    }

    Object data = event.getData();
    if (data instanceof AbstractMessage) {
      errorBean.setAssigneeId(((AbstractMessage)data).getAssigneeId());
      errorBean.setAttachments(((AbstractMessage)data).getAttachments());
    }

    String login = settings.ITN_LOGIN;
    String password = settings.getPlainItnPassword();
    if (StringUtil.isEmptyOrSpaces(login) && StringUtil.isEmptyOrSpaces(password)) {
      login = "idea_anonymous";
      password = "guest";
    }

    ITNProxy.sendError(project, login, password, errorBean, new Consumer<Integer>() {
      @Override
      public void consume(Integer threadId) {
        updatePreviousThreadId(threadId);
        String url = ITNProxy.getBrowseUrl(threadId);
        String linkText = String.valueOf(threadId);
        final SubmittedReportInfo reportInfo = new SubmittedReportInfo(url, linkText, SubmittedReportInfo.SubmissionStatus.NEW_ISSUE);
        callback.consume(reportInfo);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            StringBuilder text = new StringBuilder();
            IdeErrorsDialog.appendSubmissionInformation(reportInfo, text);
            text.append('.').append("<br/>").append(DiagnosticBundle.message("error.report.gratitude"));
            String content = XmlStringUtil.wrapInHtml(text);
            ReportMessages.GROUP
              .createNotification(ReportMessages.ERROR_REPORT, content, NotificationType.INFORMATION, NotificationListener.URL_OPENING_LISTENER)
              .setImportant(false)
              .notify(project);
          }
        });
      }
    }, new Consumer<Exception>() {
      @Override
      public void consume(final Exception e) {
        Logger.getInstance(ITNReporter.class).info("reporting failed: " + e);
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
            if (e instanceof UpdateAvailableException) {
              String message = DiagnosticBundle.message("error.report.new.eap.build.message", e.getMessage());
              showMessageDialog(parentComponent, project, message, CommonBundle.getWarningTitle(), Messages.getWarningIcon());
              callback.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED));
            }
            else if (showYesNoDialog(parentComponent, project, msg, ReportMessages.ERROR_REPORT, Messages.getErrorIcon()) != Messages.YES) {
              callback.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED));
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
    return true;
  }

  private static void updatePreviousThreadId(Integer threadId) {
    previousExceptionThreadId = threadId;
  }

  private static void showMessageDialog(Component parentComponent, Project project, String message, String title, Icon icon) {
    if (parentComponent.isShowing()) {
      Messages.showMessageDialog(parentComponent, message, title, icon);
    }
    else {
      Messages.showMessageDialog(project, message, title, icon);
    }
  }

  @Messages.YesNoResult
  private static int showYesNoDialog(Component parentComponent, Project project, String message, String title, Icon icon) {
    if (parentComponent.isShowing()) {
      return Messages.showYesNoDialog(parentComponent, message, title, icon);
    }
    else {
      return Messages.showYesNoDialog(project, message, title, icon);
    }
  }
}
