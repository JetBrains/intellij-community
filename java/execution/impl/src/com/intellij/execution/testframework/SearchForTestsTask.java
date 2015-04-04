/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class SearchForTestsTask extends Task.Backgroundable {

  private static final Logger LOG = Logger.getInstance("#" + SearchForTestsTask.class.getName());
  protected Socket mySocket;
  private ServerSocket myServerSocket;

  public SearchForTestsTask(@Nullable final Project project,
                            @NotNull  final ServerSocket socket) {
    super(project, ExecutionBundle.message("searching.test.progress.title"), true);
    myServerSocket = socket;
  }


  protected abstract void search() throws CantRunException;
  protected abstract void onFound();

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      mySocket = myServerSocket.accept();
      final CantRunException[] ex = new CantRunException[1];
      DumbService.getInstance(getProject()).repeatUntilPassesInSmartMode(new Runnable() {
        @Override
        public void run() {
          try {
            search();
          }
          catch (CantRunException e) {
            ex[0] = e;
          }
        }
      });
      if (ex[0] != null) throw ex[0];
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (IOException e) {
      LOG.info(e);
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  @Override
  public void onCancel() {
    finish();
  }

  @Override
  public void onSuccess() {
    DumbService.getInstance(getProject()).runWhenSmart(new Runnable() {
      @Override
      public void run() {
        onFound();
        finish();
        startListening();
      }
    });
  }

  public void finish() {
    DataOutputStream os = null;
    try {
      if (mySocket == null || mySocket.isClosed()) return;
      os = new DataOutputStream(mySocket.getOutputStream());
      os.writeBoolean(true);
    }
    catch (Throwable e) {
      LOG.info(e);
    }
    finally {
      try {
        if (os != null) os.close();
      }
      catch (Throwable e) {
        LOG.info(e);
      }

      try {
        if (!myServerSocket.isClosed()) {
          myServerSocket.close();
        }
      }
      catch (Throwable e) {
        LOG.info(e);
      }
    }
  }

  protected void startListening() {}
}
