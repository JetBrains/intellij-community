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
package com.intellij.errorreport;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.errorreport.itn.ITNProxy;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 22, 2003
 * Time: 8:57:19 PM
 * To change this template use Options | File Templates.
 */
public class ErrorReportSender {
  @NonNls public static final String PREPARE_URL = "http://www.intellij.net/";

  private ErrorReportSender() {
  }

  static class SendTask {
    private final Project myProject;
    private String myLogin;
    private String myPassword;
    private ErrorBean errorBean;

    public SendTask(final Project project, ErrorBean errorBean) {
      myProject = project;
      this.errorBean = errorBean;
    }

    public void setCredentials(String login, String password) {
      myLogin = login;
      myPassword = password;
    }

    public void sendReport(final Consumer<Integer> callback, final Consumer<Exception> errback) {
      Task.Backgroundable task = new Task.Backgroundable(myProject, DiagnosticBundle.message("title.submitting.error.report")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            HttpConfigurable.getInstance().prepareURL(PREPARE_URL);

            if (!StringUtil.isEmpty(myLogin)) {
              int threadId = ITNProxy.postNewThread(
                myLogin,
                myPassword,
                errorBean,
                IdeaLogger.getOurCompilationTimestamp());
              callback.consume(threadId);
            }
          }
          catch (Exception ex) {
            errback.consume(ex);
          }
        }
      };
      if (myProject == null) {
        task.run(new EmptyProgressIndicator());
      }
      else {
        ProgressManager.getInstance().run(task);
      }
    }
  }

  public static void sendError(Project project, String login, String password, ErrorBean error,
                               Consumer<Integer> callback, Consumer<Exception> errback) {
    SendTask sendTask = new SendTask(project, error);
    sendTask.setCredentials(login, password);
    sendTask.sendReport(callback, errback);
  }
}
