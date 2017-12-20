/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.build;

import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.TerminateRemoteProcessDialog;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.*;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.ContentUtilEx.getFullName;

/**
 * @author Vladislav.Soroka
 */
public class BuildContentManagerImpl implements BuildContentManager {

  public static final String Build = "Build";
  public static final String Sync = "Sync";
  public static final String Run = "Run";
  public static final String Debug = "Debug";
  private static final String[] ourPresetOrder = {Build, Sync, Run, Debug};
  private static final Key<Map<Object, CloseListener>> CONTENT_CLOSE_LISTENERS = Key.create("CONTENT_CLOSE_LISTENERS");

  private Project myProject;
  private ToolWindow myToolWindow;
  private final List<Runnable> myPostponedRunnables = new ArrayList<>();
  private Map<Content, Pair<Icon, AtomicInteger>> liveContentsMap = ContainerUtil.newConcurrentMap();

  public BuildContentManagerImpl(Project project) {
    init(project);
  }

  private void init(Project project) {
    myProject = project;
    if (project.isDefault()) return;

    StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project)
        .registerToolWindow(ToolWindowId.BUILD, true, ToolWindowAnchor.BOTTOM, project, true);
      JComponent component = toolWindow.getComponent();
      if (component != null) {
        component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
      }
      toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowBuild);
      toolWindow.setAvailable(true, null);
      toolWindow.hide(null);
      myToolWindow = toolWindow;
      ContentManager contentManager = myToolWindow.getContentManager();
      contentManager.addDataProvider(new DataProvider() {
        private int myInsideGetData = 0;

        @Override
        public Object getData(String dataId) {
          myInsideGetData++;
          try {
            return myInsideGetData == 1 ? DataManager.getInstance().getDataContext(contentManager.getComponent()).getData(dataId) : null;
          }
          finally {
            myInsideGetData--;
          }
        }
      });
      new ContentManagerWatcher(toolWindow, contentManager);

      for (Runnable postponedRunnable : myPostponedRunnables) {
        postponedRunnable.run();
      }
      myPostponedRunnables.clear();
    });
  }

  public Promise<Void> runWhenInitialized(final Runnable runnable) {
    if (myToolWindow != null) {
      runnable.run();
      return Promises.resolvedPromise(null);
    }
    else {
      final AsyncPromise<Void> promise = new AsyncPromise<>();
      myPostponedRunnables.add(() -> {
        if (!myProject.isDisposed()) {
          runnable.run();
          promise.setResult(null);
        }
      });
      return promise;
    }
  }

  @Override
  public void addContent(Content content) {
    runWhenInitialized(() -> {
      if (!myToolWindow.isAvailable()) {
        myToolWindow.setAvailable(true, null);
      }
      ContentManager contentManager = myToolWindow.getContentManager();
      final String name = content.getTabName();
      final String category = StringUtil.trimEnd(StringUtil.split(name, " ").get(0), ':');
      int idx = -1;
      for (int i = 0; i < ourPresetOrder.length; i++) {
        final String s = ourPresetOrder[i];
        if (s.equals(category)) {
          idx = i;
          break;
        }
      }
      final Content[] existingContents = contentManager.getContents();
      if (idx != -1) {
        final MultiMap<String, String> existingCategoriesNames = MultiMap.createSmart();
        for (Content existingContent : existingContents) {
          String tabName = existingContent.getTabName();
          existingCategoriesNames.putValue(StringUtil.trimEnd(StringUtil.split(tabName, " ").get(0), ':'), tabName);
        }

        int place = 0;
        for (int i = 0; i < idx; i++) {
          String key = ourPresetOrder[i];
          Collection<String> tabNames = existingCategoriesNames.get(key);
          if (!key.equals(category)) {
            place += tabNames.size();
          }
        }
        contentManager.addContent(content, place);
      }
      else {
        contentManager.addContent(content);
      }

      for (Content existingContent : existingContents) {
        existingContent.setDisplayName(existingContent.getTabName());
      }
      String tabName = content.getTabName();
      updateTabDisplayName(content, tabName);
    });
  }

  public void updateTabDisplayName(Content content, String tabName) {
    runWhenInitialized(() -> {
      String displayName;
      ContentManager contentManager = myToolWindow.getContentManager();
      Content firstContent = contentManager.getContent(0);
      assert firstContent != null;
      if (!Build.equals(firstContent.getTabName())) {
        if (contentManager.getContentCount() > 1) {
          setIdLabelHidden(false);
          displayName = tabName;
        }
        else {
          displayName = Build + ": " + tabName;
        }
      }
      else {
        displayName = tabName;
        setIdLabelHidden(true);
      }

      if (!displayName.equals(content.getDisplayName())) {
        // we are going to adjust display name, so we need to ensure tab name is not retrieved based on display name
        content.setTabName(tabName);
        content.setDisplayName(displayName);
      }
    });
  }

  @Override
  public void removeContent(Content content) {
    runWhenInitialized(() -> {
      ContentManager contentManager = myToolWindow.getContentManager();
      if (contentManager != null && (!contentManager.isDisposed())) {
        contentManager.removeContent(content, true);
      }
    });
  }

  @Override
  public ActionCallback setSelectedContent(@NotNull final Content content,
                                           final boolean requestFocus,
                                           final boolean forcedFocus,
                                           boolean activate,
                                           @Nullable Runnable activationCallback) {
    final ActionCallback actionCallback = new ActionCallback();
    Disposer.register(content, actionCallback);
    runWhenInitialized(() -> {
      if (!myToolWindow.isAvailable()) {
        actionCallback.setRejected();
        return;
      }
      if (activate) {
        myToolWindow.show(activationCallback);
      }
      ActionCallback callback = myToolWindow.getContentManager().setSelectedContent(content, requestFocus, forcedFocus, false);
      callback.notify(actionCallback);
    });
    return actionCallback;
  }

  @Override
  public Content addTabbedContent(@NotNull JComponent contentComponent,
                                  @NotNull String groupPrefix,
                                  @NotNull String tabName,
                                  @Nullable Icon icon,
                                  @Nullable Disposable childDisposable) {
    ContentManager contentManager = myToolWindow.getContentManager();
    ContentUtilEx.addTabbedContent(contentManager, contentComponent, groupPrefix, tabName, false, childDisposable);
    Content content = contentManager.findContent(getFullName(groupPrefix, tabName));
    if (icon != null) {
      TabbedContent tabbedContent = ContentUtilEx.findTabbedContent(contentManager, groupPrefix);
      if (tabbedContent != null) {
        tabbedContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
        tabbedContent.setIcon(icon);
      }
    }
    return content;
  }

  public void startBuildNotified(@NotNull BuildDescriptor buildDescriptor,
                                 @NotNull Content content,
                                 @Nullable BuildProcessHandler processHandler) {
    if (processHandler != null) {
      Map<Object, CloseListener> closeListenerMap = content.getUserData(CONTENT_CLOSE_LISTENERS);
      if (closeListenerMap == null) {
        closeListenerMap = ContainerUtil.newHashMap();
        content.putUserData(CONTENT_CLOSE_LISTENERS, closeListenerMap);
      }
      closeListenerMap.put(buildDescriptor.getId(), new CloseListener(content, processHandler));
    }
    runWhenInitialized(() -> {
      Pair<Icon, AtomicInteger> pair = liveContentsMap.computeIfAbsent(content, c -> Pair.pair(c.getIcon(), new AtomicInteger(0)));
      pair.second.incrementAndGet();
      content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
      content.setIcon(ExecutionUtil.getLiveIndicator(pair.first));
      JComponent component = content.getComponent();
      if (component != null) {
        component.invalidate();
      }
      myToolWindow.setIcon(ExecutionUtil.getLiveIndicator(AllIcons.Toolwindows.ToolWindowBuild));
    });
  }

  public void finishBuildNotified(@NotNull BuildDescriptor buildDescriptor, @NotNull Content content) {
    Map<Object, CloseListener> closeListenerMap = content.getUserData(CONTENT_CLOSE_LISTENERS);
    if (closeListenerMap != null) {
      CloseListener closeListener = closeListenerMap.remove(buildDescriptor.getId());
      if (closeListener != null) {
        Disposer.dispose(closeListener);
        if (closeListenerMap.isEmpty()) {
          content.putUserData(CONTENT_CLOSE_LISTENERS, null);
        }
      }
    }
    runWhenInitialized(() -> {
      Pair<Icon, AtomicInteger> pair = liveContentsMap.get(content);
      if (pair != null && pair.second.decrementAndGet() == 0) {
        content.setIcon(pair.first);
        if (pair.first == null) {
          content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.FALSE);
        }
        liveContentsMap.remove(content);
        if (liveContentsMap.isEmpty()) {
          myToolWindow.setIcon(AllIcons.Toolwindows.ToolWindowBuild);
        }
      }
    });
  }

  private void setIdLabelHidden(boolean hide) {
    JComponent component = myToolWindow.getComponent();
    Object oldValue = component.getClientProperty(ToolWindowContentUi.HIDE_ID_LABEL);
    Object newValue = hide ? "true" : null;
    component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, newValue);
    if (myToolWindow instanceof ToolWindowImpl) {
      ((ToolWindowImpl)myToolWindow).getContentUI()
        .propertyChange(new PropertyChangeEvent(this, ToolWindowContentUi.HIDE_ID_LABEL, oldValue, newValue));
    }
  }

  private class CloseListener extends ContentManagerAdapter implements VetoableProjectManagerListener, Disposable {
    @Nullable
    private Content myContent;
    @Nullable
    private BuildProcessHandler myProcessHandler;

    private CloseListener(@NotNull final Content content, @NotNull BuildProcessHandler processHandler) {
      myContent = content;
      ContentManager contentManager = content.getManager();
      if (contentManager != null) {
        contentManager.addContentManagerListener(this);
      }
      ProjectManager.getInstance().addProjectManagerListener(myProject, this);
      myProcessHandler = processHandler;
    }

    @Override
    public void contentRemoved(final ContentManagerEvent event) {
      final Content content = event.getContent();
      if (content == myContent) {
        Disposer.dispose(this);
      }
    }

    @Override
    public void dispose() {
      if (myContent == null) return;

      final Content content = myContent;
      ContentManager contentManager = content.getManager();
      if(contentManager != null) {
        contentManager.removeContentManagerListener(this);
      }
      ProjectManager.getInstance().removeProjectManagerListener(myProject, this);
      myContent = null;
      if (myProcessHandler instanceof Disposable) {
        Disposer.dispose((Disposable)myProcessHandler);
      }
      myProcessHandler = null;
    }

    @Override
    public void contentRemoveQuery(final ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        final boolean canClose = closeQuery(false);
        if (!canClose) {
          event.consume();
        }
      }
    }

    @Override
    public void projectClosed(final Project project) {
      if (myContent != null && project == myProject) {
        ContentManager contentManager = myContent.getManager();
        if (contentManager != null) {
          contentManager.removeContent(myContent, true);
        }
        Disposer.dispose(this); // Dispose content even if content manager refused to.
      }
    }

    @Override
    public boolean canClose(@NotNull Project project) {
      if (project != myProject) return true;

      if (myContent == null) return true;

      final boolean canClose = closeQuery(true);
      // Content could be removed during close query
      if (canClose && myContent != null) {
        ContentManager contentManager = myContent.getManager();
        if (contentManager != null) contentManager.removeContent(myContent, true);
        myContent = null;
      }
      return canClose;
    }

    private boolean closeQuery(boolean modal) {
      if (myProcessHandler == null || myProcessHandler.isProcessTerminated() || myProcessHandler.isProcessTerminating()) {
        return true;
      }
      myProcessHandler.putUserData(RunContentManagerImpl.ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY, Boolean.TRUE);
      GeneralSettings.ProcessCloseConfirmation rc =
        TerminateRemoteProcessDialog.show(myProject, myProcessHandler.getExecutionName(), myProcessHandler);
      if (rc == null) { // cancel
        return false;
      }
      boolean destroyProcess = rc == GeneralSettings.ProcessCloseConfirmation.TERMINATE;
      if (destroyProcess) {
        myProcessHandler.destroyProcess();
      }
      else {
        myProcessHandler.detachProcess();
      }
      waitForProcess(modal, myProcessHandler);
      return true;
    }
  }

  private void waitForProcess(final boolean modal, BuildProcessHandler processHandler) {
    String title = ExecutionBundle.message("terminating.process.progress.title", processHandler.getExecutionName());
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, true) {

      @Override
      public boolean isConditionalModal() {
        return modal;
      }

      @Override
      public boolean shouldStartInBackground() {
        return !modal;
      }

      @Override
      public void run(@NotNull final ProgressIndicator progressIndicator) {
        final Semaphore semaphore = new Semaphore();
        semaphore.down();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          try {
            processHandler.waitFor();
          }
          finally {
            semaphore.up();
          }
        });

        progressIndicator.setText(ExecutionBundle.message("waiting.for.vm.detach.progress.text"));
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            while (true) {
              if (progressIndicator.isCanceled() || !progressIndicator.isRunning()) {
                semaphore.up();
                break;
              }
              try {
                //noinspection SynchronizeOnThis
                synchronized (this) {
                  //noinspection SynchronizeOnThis
                  wait(2000L);
                }
              }
              catch (InterruptedException ignore) {
              }
            }
          }
        });
        semaphore.waitFor();
      }

      @Override
      public void onCancel() {
        // stop waiting for the process
        processHandler.forceProcessDetach();
      }
    });
  }
}
