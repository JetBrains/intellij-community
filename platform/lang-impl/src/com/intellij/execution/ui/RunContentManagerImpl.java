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
package com.intellij.execution.ui;

import com.intellij.execution.*;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.layout.impl.DockableGridContainerFactory;
import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.*;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class RunContentManagerImpl implements RunContentManager, Disposable {
  public static final Key<Boolean> ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY = Key.create("ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY");
  private static final Logger LOG = Logger.getInstance(RunContentManagerImpl.class);
  private static final Key<Executor> EXECUTOR_KEY = Key.create("Executor");

  private final Project myProject;
  private final Map<String, ContentManager> myToolwindowIdToContentManagerMap = new THashMap<>();
  private final Map<String, Icon> myToolwindowIdToBaseIconMap = new THashMap<>();
  private final LinkedList<String> myToolwindowIdZBuffer = new LinkedList<>();

  public RunContentManagerImpl(@NotNull Project project, @NotNull DockManager dockManager) {
    myProject = project;
    DockableGridContainerFactory containerFactory = new DockableGridContainerFactory();
    dockManager.register(DockableGridContainerFactory.TYPE, containerFactory);
    Disposer.register(myProject, containerFactory);

    AppUIUtil.invokeOnEdt(() -> init(), myProject.getDisposed());
  }

  // must be called on EDT
  private void init() {
    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(myProject);
    if (toolWindowManager == null) {
      return;
    }

    for (Executor executor : ExecutorRegistry.getInstance().getRegisteredExecutors()) {
      registerToolWindow(executor, toolWindowManager);
    }

    RunDashboardManager dashboardManager = RunDashboardManager.getInstance(myProject);
    initToolWindow(null, dashboardManager.getToolWindowId(), dashboardManager.getToolWindowIcon(),
                   dashboardManager.getDashboardContentManager());

    toolWindowManager.addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      @Override
      public void stateChanged() {
        if (myProject.isDisposed()) {
          return;
        }

        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        Set<String> currentWindows = new THashSet<>();
        ContainerUtil.addAll(currentWindows, toolWindowManager.getToolWindowIds());
        myToolwindowIdZBuffer.retainAll(currentWindows);

        final String activeToolWindowId = toolWindowManager.getActiveToolWindowId();
        if (activeToolWindowId != null) {
          if (myToolwindowIdZBuffer.remove(activeToolWindowId)) {
            myToolwindowIdZBuffer.addFirst(activeToolWindowId);
          }
        }
      }
    });
  }

  @Override
  public void dispose() {
  }

  private void registerToolWindow(@NotNull final Executor executor, @NotNull ToolWindowManagerEx toolWindowManager) {
    final String toolWindowId = executor.getToolWindowId();
    if (toolWindowManager.getToolWindow(toolWindowId) != null) {
      return;
    }

    final ToolWindow toolWindow = toolWindowManager.registerToolWindow(toolWindowId, true, ToolWindowAnchor.BOTTOM, this, true);
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addDataProvider(new DataProvider() {
      private int myInsideGetData = 0;

      @Override
      public Object getData(String dataId) {
        myInsideGetData++;
        try {
          if (PlatformDataKeys.HELP_ID.is(dataId)) {
            return executor.getHelpId();
          }
          else {
            return myInsideGetData == 1 ? DataManager.getInstance().getDataContext(contentManager.getComponent()).getData(dataId) : null;
          }
        }
        finally {
          myInsideGetData--;
        }
      }
    });

    toolWindow.setIcon(executor.getToolWindowIcon());
    new ContentManagerWatcher(toolWindow, contentManager);
    initToolWindow(executor, toolWindowId, executor.getToolWindowIcon(), contentManager);
  }

  private void initToolWindow(@Nullable final Executor executor, String toolWindowId, Icon toolWindowIcon, ContentManager contentManager) {
    myToolwindowIdToBaseIconMap.put(toolWindowId, toolWindowIcon);
    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(final ContentManagerEvent event) {
        if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
          Content content = event.getContent();
          Executor contentExecutor = executor;
          if (contentExecutor == null) {
            // Content manager contains contents related with different executors.
            // Try to get executor from content.
            contentExecutor = getExecutorByContent(content);
            // Must contain this user data since all content is added by this class.
            LOG.assertTrue(contentExecutor != null);
          }
          getSyncPublisher().contentSelected(getRunContentDescriptorByContent(content), contentExecutor);
        }
      }
    });
    myToolwindowIdToContentManagerMap.put(toolWindowId, contentManager);
    Disposer.register(contentManager, new Disposable() {
      @Override
      public void dispose() {
        myToolwindowIdToContentManagerMap.remove(toolWindowId).removeAllContents(true);
        myToolwindowIdZBuffer.remove(toolWindowId);
        myToolwindowIdToBaseIconMap.remove(toolWindowId);
      }
    });
    myToolwindowIdZBuffer.addLast(toolWindowId);
  }

  private RunContentWithExecutorListener getSyncPublisher() {
    return myProject.getMessageBus().syncPublisher(TOPIC);
  }

  @Override
  public void toFrontRunContent(final Executor requestor, final ProcessHandler handler) {
    final RunContentDescriptor descriptor = getDescriptorBy(handler, requestor);
    if (descriptor == null) {
      return;
    }
    toFrontRunContent(requestor, descriptor);
  }

  @Override
  public void toFrontRunContent(final Executor requestor, final RunContentDescriptor descriptor) {
    ApplicationManager.getApplication().invokeLater(() -> {
      ContentManager contentManager = getContentManagerForRunner(requestor, descriptor);
      Content content = getRunContentByDescriptor(contentManager, descriptor);
      if (content != null) {
        contentManager.setSelectedContent(content);
        ToolWindowManager.getInstance(myProject).getToolWindow(getToolWindowIdForRunner(requestor, descriptor)).show(null);
      }
    }, myProject.getDisposed());
  }

  @Override
  public void hideRunContent(@NotNull final Executor executor, final RunContentDescriptor descriptor) {
    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(getToolWindowIdForRunner(executor, descriptor));
      if (toolWindow != null) {
        toolWindow.hide(null);
      }
    }, myProject.getDisposed());
  }

  @Override
  @Nullable
  public RunContentDescriptor getSelectedContent(final Executor executor) {
    final Content selectedContent = getContentManagerForRunner(executor, null).getSelectedContent();
    return selectedContent != null ? getRunContentDescriptorByContent(selectedContent) : null;
  }

  @Override
  @Nullable
  public RunContentDescriptor getSelectedContent() {
    for (String activeWindow : myToolwindowIdZBuffer) {
      final ContentManager contentManager = myToolwindowIdToContentManagerMap.get(activeWindow);
      if (contentManager == null) {
        continue;
      }

      final Content selectedContent = contentManager.getSelectedContent();
      if (selectedContent == null) {
        if (contentManager.getContentCount() == 0) {
          // continue to the next window if the content manager is empty
          continue;
        }
        else {
          // stop iteration over windows because there is some content in the window and the window is the last used one
          break;
        }
      }
      // here we have selected content
      return getRunContentDescriptorByContent(selectedContent);
    }

    return null;
  }

  @Override
  public boolean removeRunContent(@NotNull final Executor executor, final RunContentDescriptor descriptor) {
    final ContentManager contentManager = getContentManagerForRunner(executor, descriptor);
    final Content content = getRunContentByDescriptor(contentManager, descriptor);
    return content != null && contentManager.removeContent(content, true);
  }

  @Override
  public void showRunContent(@NotNull Executor executor, @NotNull RunContentDescriptor descriptor) {
    showRunContent(executor, descriptor, descriptor.getExecutionId());
  }

  private void showRunContent(@NotNull final Executor executor, @NotNull final RunContentDescriptor descriptor, final long executionId) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    final ContentManager contentManager = getContentManagerForRunner(executor, descriptor);
    RunContentDescriptor oldDescriptor = chooseReuseContentForDescriptor(contentManager, descriptor, executionId, descriptor.getDisplayName());
    final Content content;
    if (oldDescriptor == null) {
      content = createNewContent(descriptor, executor);
    }
    else {
      content = oldDescriptor.getAttachedContent();
      LOG.assertTrue(content != null);
      getSyncPublisher().contentRemoved(oldDescriptor, executor);
      Disposer.dispose(oldDescriptor); // is of the same category, can be reused
    }

    content.setExecutionId(executionId);
    content.setComponent(descriptor.getComponent());
    content.setPreferredFocusedComponent(descriptor.getPreferredFocusComputable());
    content.putUserData(RunContentDescriptor.DESCRIPTOR_KEY, descriptor);
    content.putUserData(EXECUTOR_KEY, executor);
    content.setDisplayName(descriptor.getDisplayName());
    descriptor.setAttachedContent(content);

    String toolWindowId = getToolWindowIdForRunner(executor, descriptor);
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
    final ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null) {
      final ProcessAdapter processAdapter = new ProcessAdapter() {
        @Override
        public void startNotified(@NotNull final ProcessEvent event) {
          UIUtil.invokeLaterIfNeeded(() -> {
            content.setIcon(ExecutionUtil.getLiveIndicator(descriptor.getIcon()));
            toolWindow.setIcon(ExecutionUtil.getLiveIndicator(myToolwindowIdToBaseIconMap.get(toolWindowId)));
          });
        }

        @Override
        public void processTerminated(@NotNull final ProcessEvent event) {
          ApplicationManager.getApplication().invokeLater(() -> {
            boolean alive = false;
            ContentManager manager = myToolwindowIdToContentManagerMap.get(toolWindowId);
            if (manager == null) return;
            for (Content content1 : manager.getContents()) {
              RunContentDescriptor descriptor1 = getRunContentDescriptorByContent(content1);
              if (descriptor1 != null) {
                ProcessHandler handler = descriptor1.getProcessHandler();
                if (handler != null && !handler.isProcessTerminated()) {
                  alive = true;
                  break;
                }
              }
            }
            Icon base = myToolwindowIdToBaseIconMap.get(toolWindowId);
            toolWindow.setIcon(alive ? ExecutionUtil.getLiveIndicator(base) : base);

            Icon icon = descriptor.getIcon();
            content.setIcon(icon == null ? executor.getDisabledIcon() : IconLoader.getTransparentIcon(icon));
          });
        }
      };
      processHandler.addProcessListener(processAdapter);
      final Disposable disposer = content.getDisposer();
      if (disposer != null) {
        Disposer.register(disposer, new Disposable() {
          @Override
          public void dispose() {
            processHandler.removeProcessListener(processAdapter);
          }
        });
      }
    }

    if (oldDescriptor == null) {
      contentManager.addContent(content);
      new CloseListener(content, executor);
    }
    content.getManager().setSelectedContent(content);

    if (!descriptor.isActivateToolWindowWhenAdded()) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
      // let's activate tool window, but don't move focus
      //
      // window.show() isn't valid here, because it will not
      // mark the window as "last activated" windows and thus
      // some action like navigation up/down in stacktrace wont
      // work correctly
      window.activate(descriptor.getActivationCallback(), descriptor.isAutoFocusContent(), descriptor.isAutoFocusContent());
    }, myProject.getDisposed());
  }

  @Nullable
  @Override
  public RunContentDescriptor getReuseContent(@NotNull ExecutionEnvironment executionEnvironment) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    RunContentDescriptor contentToReuse = executionEnvironment.getContentToReuse();
    if (contentToReuse != null) {
      return contentToReuse;
    }

    // TODO [konstantin.aleev] Should content be reused in case of dashboard?
    String toolWindowId = getContentDescriptorToolWindowId(executionEnvironment.getRunnerAndConfigurationSettings());
    final ContentManager contentManager = toolWindowId == null ?
                                          getContentManagerForRunner(executionEnvironment.getExecutor(), null) :
                                          myToolwindowIdToContentManagerMap.get(toolWindowId);
    return chooseReuseContentForDescriptor(contentManager, null, executionEnvironment.getExecutionId(),
                                           executionEnvironment.toString());
  }

  @Override
  public RunContentDescriptor findContentDescriptor(final Executor requestor, final ProcessHandler handler) {
    return getDescriptorBy(handler, requestor);
  }

  @Override
  public void showRunContent(@NotNull Executor info, @NotNull RunContentDescriptor descriptor, @Nullable RunContentDescriptor contentToReuse) {
    copyContentAndBehavior(descriptor, contentToReuse);
    showRunContent(info, descriptor, descriptor.getExecutionId());
  }

  public static void copyContentAndBehavior(@NotNull RunContentDescriptor descriptor, @Nullable RunContentDescriptor contentToReuse) {
    if (contentToReuse != null) {
      Content attachedContent = contentToReuse.getAttachedContent();
      if (attachedContent != null && attachedContent.isValid()) {
        descriptor.setAttachedContent(attachedContent);
      }
      if (contentToReuse.isReuseToolWindowActivation()) {
        descriptor.setActivateToolWindowWhenAdded(contentToReuse.isActivateToolWindowWhenAdded());
      }
      descriptor.setContentToolWindowId(contentToReuse.getContentToolWindowId());
    }
  }

  @Nullable
  private static RunContentDescriptor chooseReuseContentForDescriptor(@NotNull ContentManager contentManager,
                                                                      @Nullable RunContentDescriptor descriptor,
                                                                      long executionId,
                                                                      @Nullable String preferredName) {
    Content content = null;
    if (descriptor != null) {
      //Stage one: some specific descriptors (like AnalyzeStacktrace) cannot be reused at all
      if (descriptor.isContentReuseProhibited()) {
        return null;
      }
      //Stage two: try to get content from descriptor itself
      final Content attachedContent = descriptor.getAttachedContent();

      if (attachedContent != null
          && attachedContent.isValid()
          && contentManager.getIndexOfContent(attachedContent) != -1
          && (Comparing.equal(descriptor.getDisplayName(), attachedContent.getDisplayName()) || !attachedContent.isPinned())) {
        content = attachedContent;
      }
    }
    //Stage three: choose the content with name we prefer
    if (content == null) {
      content = getContentFromManager(contentManager, preferredName, executionId);
    }
    if (content == null || !isTerminated(content) || (content.getExecutionId() == executionId && executionId != 0)) {
      return null;
    }
    final RunContentDescriptor oldDescriptor = getRunContentDescriptorByContent(content);
    if (oldDescriptor != null && !oldDescriptor.isContentReuseProhibited() ) {
      //content.setExecutionId(executionId);
      return oldDescriptor;
    }

    return null;
  }

  @Nullable
  private static Content getContentFromManager(ContentManager contentManager, @Nullable String preferredName, long executionId) {
    ArrayList<Content> contents = new ArrayList<>(Arrays.asList(contentManager.getContents()));
    Content first = contentManager.getSelectedContent();
    if (first != null && contents.remove(first)) {//selected content should be checked first
      contents.add(0, first);
    }
    if (preferredName != null) {//try to match content with specified preferred name
      for (Content c : contents) {
        if (canReuseContent(c, executionId) && preferredName.equals(c.getDisplayName())) {
          return c;
        }
      }
    }
    for (Content c : contents) {//return first "good" content
      if (canReuseContent(c, executionId)) {
        return c;
      }
    }
    return null;
  }

  private static boolean canReuseContent(Content c, long executionId) {
    return c != null && !c.isPinned() && isTerminated(c) && !(c.getExecutionId() == executionId && executionId != 0);
  }

  @NotNull
  private ContentManager getContentManagerForRunner(final Executor executor, final RunContentDescriptor descriptor) {
    final ContentManager contentManager = myToolwindowIdToContentManagerMap.get(getToolWindowIdForRunner(executor, descriptor));
    if (contentManager == null) {
      LOG.error("Runner " + executor.getId() + " is not registered");
    }
    //noinspection ConstantConditions
    return contentManager;
  }

  private static String getToolWindowIdForRunner(final Executor executor, final RunContentDescriptor descriptor) {
    if (descriptor != null && descriptor.getContentToolWindowId() != null) {
      return descriptor.getContentToolWindowId();
    }
    return  executor.getToolWindowId();
  }

  private static Content createNewContent(final RunContentDescriptor descriptor, Executor executor) {
    final String processDisplayName = descriptor.getDisplayName();
    final Content content = ContentFactory.SERVICE.getInstance().createContent(descriptor.getComponent(), processDisplayName, true);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    Icon icon = descriptor.getIcon();
    content.setIcon(icon == null ? executor.getToolWindowIcon() : icon);
    return content;
  }

  public static boolean isTerminated(@NotNull Content content) {
    RunContentDescriptor descriptor = getRunContentDescriptorByContent(content);
    ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();
    return processHandler == null || processHandler.isProcessTerminated();
  }

  @Nullable
  public static RunContentDescriptor getRunContentDescriptorByContent(@NotNull Content content) {
    return content.getUserData(RunContentDescriptor.DESCRIPTOR_KEY);
  }

  @Nullable
  public static Executor getExecutorByContent(@NotNull Content content) {
    return content.getUserData(EXECUTOR_KEY);
  }

  @Override
  @Nullable
  public ToolWindow getToolWindowByDescriptor(@NotNull RunContentDescriptor descriptor) {
    for (Map.Entry<String, ContentManager> entry : myToolwindowIdToContentManagerMap.entrySet()) {
      if (getRunContentByDescriptor(entry.getValue(), descriptor) != null) {
        return ToolWindowManager.getInstance(myProject).getToolWindow(entry.getKey());
      }
    }
    return null;
  }

  @Nullable
  private static Content getRunContentByDescriptor(@NotNull ContentManager contentManager, @NotNull RunContentDescriptor descriptor) {
    for (Content content : contentManager.getContents()) {
      if (descriptor.equals(getRunContentDescriptorByContent(content))) {
        return content;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public List<RunContentDescriptor> getAllDescriptors() {
    if (myToolwindowIdToContentManagerMap.isEmpty()) {
      return Collections.emptyList();
    }

    List<RunContentDescriptor> descriptors = new SmartList<>();
    for (String id : myToolwindowIdToContentManagerMap.keySet()) {
      for (Content content : myToolwindowIdToContentManagerMap.get(id).getContents()) {
        RunContentDescriptor descriptor = getRunContentDescriptorByContent(content);
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
      }
    }
    return descriptors;
  }

  @Override
  public void selectRunContent(@NotNull RunContentDescriptor descriptor) {
    for (Map.Entry<String, ContentManager> entry : myToolwindowIdToContentManagerMap.entrySet()) {
      Content content = getRunContentByDescriptor(entry.getValue(), descriptor);
      if (content != null) {
        entry.getValue().setSelectedContent(content);
      }
    }
  }

  @Override
  @Nullable
  public String getContentDescriptorToolWindowId(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings != null) {
      RunDashboardManager runDashboardManager = RunDashboardManager.getInstance(myProject);
      if (runDashboardManager.isShowInDashboard(settings.getConfiguration())) {
        return runDashboardManager.getToolWindowId();
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getToolWindowIdByEnvironment(@NotNull ExecutionEnvironment executionEnvironment) {
    // TODO [konstantin.aleev] Check remaining places where Executor.getToolWindowId() is used
    // Also there are some places where ToolWindowId.RUN or ToolWindowId.DEBUG are used directly.
    // For example, HotSwapProgressImpl.NOTIFICATION_GROUP. All notifications for this group is shown in Debug tool window,
    // however such notifications should be shown in Run Dashboard tool window, if run content is redirected to Run Dashboard tool window.
    String toolWindowId = getContentDescriptorToolWindowId(executionEnvironment.getRunnerAndConfigurationSettings());
    return toolWindowId != null ? toolWindowId : executionEnvironment.getExecutor().getToolWindowId();
  }

  @Nullable
  private RunContentDescriptor getDescriptorBy(ProcessHandler handler, Executor runnerInfo) {
    List<Content> contents = new ArrayList<>();
    ContainerUtil.addAll(contents, getContentManagerForRunner(runnerInfo, null).getContents());
    ContainerUtil.addAll(contents,
                         myToolwindowIdToContentManagerMap.get(RunDashboardManager.getInstance(myProject).getToolWindowId()).getContents());
    for (Content content : contents) {
      RunContentDescriptor runContentDescriptor = getRunContentDescriptorByContent(content);
      assert runContentDescriptor != null;
      if (runContentDescriptor.getProcessHandler() == handler) {
        return runContentDescriptor;
      }
    }
    return null;
  }

  private class CloseListener extends ContentManagerAdapter implements VetoableProjectManagerListener, Disposable {
    private Content myContent;
    private final Executor myExecutor;

    private CloseListener(@NotNull final Content content, @NotNull Executor executor) {
      myContent = content;
      content.getManager().addContentManagerListener(this);
      ProjectManager.getInstance().addProjectManagerListener(myProject, this);
      myExecutor = executor;
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
      try {
        RunContentDescriptor descriptor = getRunContentDescriptorByContent(content);
        getSyncPublisher().contentRemoved(descriptor, myExecutor);
        if (descriptor != null) {
          Disposer.dispose(descriptor);
        }
      }
      finally {
        content.getManager().removeContentManagerListener(this);
        ProjectManager.getInstance().removeProjectManagerListener(myProject, this);
        content.release(); // don't invoke myContent.release() because myContent becomes null after destroyProcess()
        myContent = null;
      }
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
        myContent.getManager().removeContent(myContent, true);
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
        myContent.getManager().removeContent(myContent, true);
        myContent = null;
      }
      return canClose;
    }

    private boolean closeQuery(boolean modal) {
      final RunContentDescriptor descriptor = getRunContentDescriptorByContent(myContent);
      if (descriptor == null) {
        return true;
      }

      final ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler == null || processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) {
        return true;
      }
      GeneralSettings.ProcessCloseConfirmation rc = TerminateRemoteProcessDialog.show(
        myProject, descriptor.getDisplayName(), processHandler);
      if (rc == null) { // cancel
        return false;
      }
      boolean destroyProcess = rc == GeneralSettings.ProcessCloseConfirmation.TERMINATE;
      if (destroyProcess) {
        processHandler.destroyProcess();
      }
      else {
        processHandler.detachProcess();
      }
      waitForProcess(descriptor, modal);
      return true;
    }
  }

  private void waitForProcess(final RunContentDescriptor descriptor, final boolean modal) {
    final ProcessHandler processHandler = descriptor.getProcessHandler();
    final boolean killable = !modal && (processHandler instanceof KillableProcess) && ((KillableProcess)processHandler).canKillProcess();

    String title = ExecutionBundle.message("terminating.process.progress.title", descriptor.getDisplayName());
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, true) {

      {
        if (killable) {
          String cancelText= ExecutionBundle.message("terminating.process.progress.kill");
          setCancelText(cancelText);
          setCancelTooltipText(cancelText);
        }
      }

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
          final ProcessHandler processHandler1 = descriptor.getProcessHandler();
          try {
            if (processHandler1 != null) {
              processHandler1.waitFor();
            }
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
        if (killable && !processHandler.isProcessTerminated()) {
          ((KillableProcess)processHandler).killProcess();
        }
      }
    });
  }
}
