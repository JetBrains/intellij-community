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
import com.intellij.errorreport.error.InternalEAPException;
import com.intellij.errorreport.error.NoSuchEAPUserException;
import com.intellij.errorreport.itn.ITNProxy;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.BuildInfo;
import com.intellij.openapi.updateSettings.impl.UpdateChannel;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 22, 2003
 * Time: 8:57:19 PM
 * To change this template use Options | File Templates.
 */
public class ErrorReportSender {
  @NonNls public static final String PREPARE_URL = "http://www.intellij.net/";
  //public static String REPORT_URL = "http://unit-038:8080/error/report?sender=i";

  @Nullable
  public static String checkNewBuild() {
    BuildInfo newVersion = null;
    try {
      UpdateChannel channel = UpdateChecker.checkForUpdates();
      newVersion = channel != null ? channel.getLatestBuild() : null;
    }
    catch (ConnectionException e) {
      // ignore
    }
    return newVersion != null ? newVersion.getNumber().asString() : null;
  }

  static class SendTask {
    private final Project myProject;
    private String myLogin;
    private String myPassword;
    private ErrorBean errorBean;
    private int myThreadId;

    public SendTask(final Project project, ErrorBean errorBean) {
      myProject = project;
      this.errorBean = errorBean;
    }

    public int getThreadId () {
      return myThreadId;
    }

    public void setCredentials(String login, String password) {
      myLogin = login;
      myPassword = password;
    }

    public void sendReport() throws Exception {
      final Ref<Exception> err = new Ref<Exception>();
      Runnable runnable = new Runnable() {
        public void run() {
          try {
            HttpConfigurable.getInstance().prepareURL(PREPARE_URL);

            if (!StringUtil.isEmpty(myLogin)) {
              myThreadId = ITNProxy.postNewThread(
                myLogin,
                myPassword,
                errorBean,
                IdeaLogger.getOurCompilationTimestamp());
            }
          }
          catch (Exception ex) {
            err.set(ex);
          }
        }
      };
      if (myProject == null) {
        runnable.run();
      }
      else {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable,
                                                                          DiagnosticBundle.message("title.submitting.error.report"),
                                                                          false, myProject);
      }
      if (!err.isNull()) {
        throw err.get();
      }
    }
  }

  public static int sendError(Project project, String login, String password, ErrorBean error)
    throws IOException, NoSuchEAPUserException, InternalEAPException {

    SendTask sendTask = new SendTask (project, error);
    sendTask.setCredentials(login, password);

    try {
      sendTask.sendReport();
      return sendTask.getThreadId();
    } catch (IOException e) {
      throw e;
    } catch (NoSuchEAPUserException e) {
      throw e;
    } catch (InternalEAPException e) {
      throw e;
    } catch (Throwable e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }
}
