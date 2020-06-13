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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class DiffViewerBase implements DiffViewer, DataProvider {
  protected static final Logger LOG = Logger.getInstance(DiffViewerBase.class);

  @NotNull private final List<DiffViewerListener> myListeners = new SmartList<>();

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
  @Override
  public final FrameDiffTool.ToolbarComponents init() {
    if (LOG.isDebugEnabled() && !ApplicationManager.getApplication().isHeadlessEnvironment() &&
        (getComponent().getWidth() <= 0 || getComponent().getHeight() <= 0)) {
      LOG.warn("Diff shown for a hidden component, initial scroll position might be invalid", new Throwable());
    }

    processContextHints();
    onInit();

    FrameDiffTool.ToolbarComponents components = new FrameDiffTool.ToolbarComponents();
    components.toolbarActions = createToolbarActions();
    components.popupActions = createPopupActions();
    components.statusPanel = getStatusPanel();

    fireEvent(EventType.INIT);

    new UiNotifyConnector(getComponent(), new Activatable() {
      private boolean wasNotShownYet = true;

      @Override
      public void showNotify() {
        rediff(wasNotShownYet);
        wasNotShownYet = false;
      }

      @Override
      public void hideNotify() {
        abortRediff();
      }
    });

    return components;
  }

  @Override
  @CalledInAwt
  public final void dispose() {
    if (myDisposed) return;
    if (!ApplicationManager.getApplication().isDispatchThread()) LOG.warn(new Throwable("dispose() not from EDT"));

    UIUtil.invokeLaterIfNeeded(() -> {
      if (myDisposed) return;
      myDisposed = true;

      abortRediff();
      updateContextHints();

      fireEvent(EventType.DISPOSE);

      onDispose();
    });
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

    if (getComponent().isShowing()) {
      myTaskAlarm.addRequest(this::rediff, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    }
  }

  @CalledInAwt
  public final void abortRediff() {
    myTaskExecutor.abort();
    myTaskAlarm.cancelAllRequests();
    fireEvent(EventType.REDIFF_ABORTED);
  }

  @CalledInAwt
  public final void rediff() {
    rediff(false);
  }

  @CalledInAwt
  public void rediff(boolean trySync) {
    if (isDisposed()) return;
    abortRediff();

    fireEvent(EventType.BEFORE_REDIFF);
    onBeforeRediff();

    boolean forceEDT = forceRediffSynchronously();
    int waitMillis = trySync || tryRediffSynchronously() ? ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS : 0;

    myTaskExecutor.executeAndTryWait(
      indicator -> {
        final Runnable callback = performRediff(indicator);
        return () -> {
          callback.run();
          onAfterRediff();
          fireEvent(EventType.AFTER_REDIFF);
        };
      },
      this::onSlowRediff, waitMillis, forceEDT
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

  @CalledInAwt
  protected boolean forceRediffSynchronously() {
    // most of performRediff implementations take ReadLock inside. If EDT is holding write lock - this will never happen,
    // and diff will not be calculated. This could happen for diff from FileDocumentManager.
    return ApplicationManager.getApplication().isWriteAccessAllowed();
  }

  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>();
    group.add(ActionManager.getInstance().getAction(IdeActions.DIFF_VIEWER_TOOLBAR));
    return group;
  }

  protected List<AnAction> createPopupActions() {
    List<AnAction> group = new ArrayList<>();
    group.add(ActionManager.getInstance().getAction(IdeActions.DIFF_VIEWER_POPUP));
    return group;
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

  @CalledInAwt
  protected void onAfterRediff() {
  }

  @CalledInBackground
  @NotNull
  protected abstract Runnable performRediff(@NotNull ProgressIndicator indicator);

  @CalledInAwt
  protected void onDispose() {
    Disposer.dispose(myTaskAlarm);
  }

  @Nullable
  protected Navigatable getNavigatable() {
    return null;
  }

  //
  // Listeners
  //

  @CalledInAwt
  public void addListener(@NotNull DiffViewerListener listener) {
    myListeners.add(listener);
  }

  @CalledInAwt
  public void removeListener(@NotNull DiffViewerListener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  @CalledInAwt
  protected List<DiffViewerListener> getListeners() {
    return myListeners;
  }

  @CalledInAwt
  private void fireEvent(@NotNull EventType type) {
    for (DiffViewerListener listener : myListeners) {
      switch (type) {
        case INIT:
          listener.onInit();
          break;
        case DISPOSE:
          listener.onDispose();
          break;
        case BEFORE_REDIFF:
          listener.onBeforeRediff();
          break;
        case AFTER_REDIFF:
          listener.onAfterRediff();
          break;
        case REDIFF_ABORTED:
          listener.onRediffAborted();
          break;
      }
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (DiffDataKeys.NAVIGATABLE.is(dataId)) {
      return getNavigatable();
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else {
      return null;
    }
  }

  private enum EventType {
    INIT, DISPOSE, BEFORE_REDIFF, AFTER_REDIFF, REDIFF_ABORTED,
  }
}
