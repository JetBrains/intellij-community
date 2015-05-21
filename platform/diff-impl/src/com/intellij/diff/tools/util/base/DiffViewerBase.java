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
package com.intellij.diff.tools.util.base;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.util.DiffTaskQueue;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

public abstract class DiffViewerBase implements DiffViewer, DataProvider {
  protected static final Logger LOG = Logger.getInstance(DiffViewerBase.class);

  @Nullable protected final Project myProject;
  @NotNull protected final DiffContext myContext;
  @NotNull protected final ContentDiffRequest myRequest;

  @NotNull private final DiffTaskQueue myTaskExecutor = new DiffTaskQueue();
  @NotNull private final Alarm myTaskAlarm = new Alarm();
  private volatile boolean myDisposed;

  public DiffViewerBase(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    myProject = context.getProject();
    myContext = context;
    myRequest = request;
  }

  @NotNull
  public final FrameDiffTool.ToolbarComponents init() {
    processContextHints();
    onInit();

    FrameDiffTool.ToolbarComponents components = new FrameDiffTool.ToolbarComponents();
    components.toolbarActions = createToolbarActions();
    components.popupActions = createPopupActions();
    components.statusPanel = getStatusPanel();

    rediff(true);
    return components;
  }

  @Override
  @CalledInAwt
  public final void dispose() {
    if (myDisposed) return;

    Runnable doDispose = new Runnable() {
      @Override
      public void run() {
        if (myDisposed) return;
        myDisposed = true;

        abortRediff();
        updateContextHints();

        onDispose();
      }
    };

    if (!ApplicationManager.getApplication().isDispatchThread()) LOG.warn(new Throwable("dispose() not from EDT"));
    UIUtil.invokeLaterIfNeeded(doDispose);
  }

  @CalledInAwt
  protected void processContextHints() {
  }

  @CalledInAwt
  protected void updateContextHints() {
  }

  @CalledInAwt
  public final void scheduleRediff() {
    if (isDisposed()) return;

    abortRediff();
    myTaskAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        rediff();
      }
    }, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
  }

  @CalledInAwt
  public final void abortRediff() {
    myTaskExecutor.abort();
    myTaskAlarm.cancelAllRequests();
  }

  @CalledInAwt
  public final void rediff() {
    rediff(false);
  }

  @CalledInAwt
  public void rediff(boolean trySync) {
    if (isDisposed()) return;
    abortRediff();

    onBeforeRediff();

    // most of performRediff implementations take ReadLock inside. If EDT is holding write lock - this will never happen,
    // and diff will not be calculated. This could happen for diff from FileDocumentManager.
    boolean forceEDT = ApplicationManager.getApplication().isWriteAccessAllowed();

    int waitMillis = trySync || tryRediffSynchronously() ? ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS : 0;

    myTaskExecutor.executeAndTryWait(
      new Function<ProgressIndicator, Runnable>() {
        @Override
        public Runnable fun(ProgressIndicator indicator) {
          return performRediff(indicator);
        }
      },
      new Runnable() {
        @Override
        public void run() {
          onSlowRediff();
        }
      },
      waitMillis, forceEDT
    );
  }

  //
  // Getters
  //

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public ContentDiffRequest getRequest() {
    return myRequest;
  }

  @NotNull
  public DiffContext getContext() {
    return myContext;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected boolean tryRediffSynchronously() {
    return myContext.isWindowFocused();
  }

  @Nullable
  protected List<AnAction> createToolbarActions() {
    return null;
  }

  @Nullable
  protected List<AnAction> createPopupActions() {
    return null;
  }

  @Nullable
  protected JComponent getStatusPanel() {
    return null;
  }

  @CalledInAwt
  protected void onInit() {
  }

  @CalledInAwt
  protected void onSlowRediff() {
  }

  @CalledInAwt
  protected void onBeforeRediff() {
  }

  @CalledInBackground
  @NotNull
  protected abstract Runnable performRediff(@NotNull ProgressIndicator indicator);

  @CalledInAwt
  protected void onDispose() {
    Disposer.dispose(myTaskAlarm);
  }

  @Nullable
  protected OpenFileDescriptor getOpenFileDescriptor() {
    return null;
  }


  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return getOpenFileDescriptor();
    }
    else if (DiffDataKeys.OPEN_FILE_DESCRIPTOR.is(dataId)) {
      return getOpenFileDescriptor();
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else {
      return null;
    }
  }
}
