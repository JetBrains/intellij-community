/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.layout.impl.DockableGridContainerFactory;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.content.*;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class RunContentManagerImpl implements RunContentManager, Disposable {
  public static final Topic<RunContentWithExecutorListener> RUN_CONTENT_TOPIC =
    Topic.create("Run Content", RunContentWithExecutorListener.class);
  public static final Key<Boolean> ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY = Key.create("ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY");
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ui.RunContentManagerImpl");
  private static final Key<RunContentDescriptor> DESCRIPTOR_KEY = new Key<RunContentDescriptor>("Descriptor");

  private final Project myProject;
  private DockableGridContainerFactory myContentFactory;
  private final Map<String, ContentManager> myToolwindowIdToContentManagerMap = new HashMap<String, ContentManager>();

  private final Map<RunContentListener, Disposable> myListeners = new HashMap<RunContentListener, Disposable>();
  private final LinkedList<String> myToolwindowIdZbuffer = new LinkedList<String>();

  public RunContentManagerImpl(Project project, DockManager dockManager) {
    myProject = project;
    myContentFactory = new DockableGridContainerFactory();
    dockManager.register(DockableGridContainerFactory.TYPE, myContentFactory);
    Disposer.register(myProject, myContentFactory);
  }

  public void init() {
    final Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
    for (Executor executor : executors) {
      registerToolwindow(executor);
    }

    if (ToolWindowManager.getInstance(myProject) == null) return;

    // To ensure ToolwindowManager had already initialized in its projectOpened.
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myProject.isDisposed()) return;
        ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
          @Override
          public void stateChanged() {
            if (myProject.isDisposed()) return;

            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);

            Set<String> currentWindows = new HashSet<String>();
            String[] toolWindowIds = toolWindowManager.getToolWindowIds();

            ContainerUtil.addAll(currentWindows, toolWindowIds);
            myToolwindowIdZbuffer.retainAll(currentWindows);

            final String activeToolWindowId = toolWindowManager.getActiveToolWindowId();
            if (activeToolWindowId != null) {
              if (myToolwindowIdZbuffer.remove(activeToolWindowId)) {
                myToolwindowIdZbuffer.addFirst(activeToolWindowId);
              }
            }
          }
        });
      }
    });
  }

  @Override
  public void dispose() {
  }

  private void unregisterToolwindow(final String id) {
    final ContentManager manager = myToolwindowIdToContentManagerMap.get(id);
    manager.removeAllContents(true);
    myToolwindowIdToContentManagerMap.remove(id);
    myToolwindowIdZbuffer.remove(id);
  }

  private void registerToolwindow(@NotNull final Executor executor) {
    final String toolWindowId = executor.getToolWindowId();
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager == null) return; //headless environment
    if (toolWindowManager.getToolWindow(toolWindowId) != null) {
      return;
    }

    final ToolWindow toolWindow = toolWindowManager.registerToolWindow(toolWindowId, true, ToolWindowAnchor.BOTTOM, this, true);

    final ContentManager contentManager = toolWindow.getContentManager();
    class MyDataProvider implements DataProvider {
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
    }
    contentManager.addDataProvider(new MyDataProvider());

    toolWindow.setIcon(executor.getToolWindowIcon());
    new ContentManagerWatcher(toolWindow, contentManager);
    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(final ContentManagerEvent event) {
        final Content content = event.getContent();
        final RunContentDescriptor descriptor = content != null ? getRunContentDescriptorByContent(content) : null;
        getSyncPublisher().contentSelected(descriptor, executor);
      }
    });
    myToolwindowIdToContentManagerMap.put(toolWindowId, contentManager);
    Disposer.register(contentManager, new Disposable() {
      @Override
      public void dispose() {
        unregisterToolwindow(toolWindowId);
      }
    });
    myToolwindowIdZbuffer.addLast(toolWindowId);
  }

  private RunContentWithExecutorListener getSyncPublisher() {
    return myProject.getMessageBus().syncPublisher(RUN_CONTENT_TOPIC);
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
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final ContentManager contentManager = getContentManagerForRunner(requestor);

        final Content content = getRunContentByDescriptor(contentManager, descriptor);

        if (contentManager != null && content != null) {
          contentManager.setSelectedContent(content);

          final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(requestor.getToolWindowId());
          toolWindow.show(null);
        }
      }
    });
  }

  @Override
  public void hideRunContent(@NotNull final Executor executor, final RunContentDescriptor descriptor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!myProject.isDisposed()) {
          final String toolWindowId = executor.getToolWindowId();
          final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
          if (toolWindow != null) {
            toolWindow.hide(null);
          }
        }
      }
    });
  }

  @Override
  @Nullable
  public RunContentDescriptor getSelectedContent(final Executor executor) {
    final ContentManager contentManager = getContentManagerForRunner(executor);
    if (contentManager != null) {
      final Content selectedContent = contentManager.getSelectedContent();
      if (selectedContent != null) {
        final RunContentDescriptor runContentDescriptorByContent = getRunContentDescriptorByContent(selectedContent);
        if (runContentDescriptorByContent != null) {
          return runContentDescriptorByContent;
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public RunContentDescriptor getSelectedContent() {
    for (String activeWindow : myToolwindowIdZbuffer) {
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
    final ContentManager contentManager = getContentManagerForRunner(executor);
    final Content content = getRunContentByDescriptor(contentManager, descriptor);
    return content != null && contentManager.removeContent(content, true);
  }

  @Override
  public void showRunContent(@NotNull final Executor executor, final RunContentDescriptor descriptor) {
    showRunContent(executor, descriptor, descriptor != null ? descriptor.getExecutionId() : 0L);
  }

  public void showRunContent(@NotNull final Executor executor, final RunContentDescriptor descriptor, long executionId) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    final ContentManager contentManager = getContentManagerForRunner(executor);
    RunContentDescriptor oldDescriptor =
      chooseReuseContentForDescriptor(contentManager, descriptor, executionId, descriptor != null ? descriptor.getDisplayName() : null);

    final Content content;

    Content oldAttachedContent = oldDescriptor != null ? oldDescriptor.getAttachedContent() : null;
    if (oldDescriptor != null) {
      content = oldAttachedContent;
      getSyncPublisher().contentRemoved(oldDescriptor, executor);
      Disposer.dispose(oldDescriptor); // is of the same category, can be reused
    }
    else if (oldAttachedContent == null || !oldAttachedContent.isValid() /*|| oldAttachedContent.getUserData(MARKED_TO_BE_REUSED) != null */) {
      content = createNewContent(contentManager, descriptor, executor);
      final Icon icon = descriptor.getIcon();
      content.setIcon(icon == null ? executor.getToolWindowIcon() : icon);
    } else {
      content = oldAttachedContent;
    }
    content.setExecutionId(executionId);
    content.setComponent(descriptor.getComponent());
    content.setPreferredFocusedComponent(descriptor.getPreferredFocusComputable());
    content.putUserData(DESCRIPTOR_KEY, descriptor);
    final ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null) {
      final ProcessAdapter processAdapter = new ProcessAdapter() {
        @Override
        public void startNotified(final ProcessEvent event) {
          LaterInvocator.invokeLater(new Runnable() {
            @Override
            public void run() {
              final Icon icon = descriptor.getIcon();
              content.setIcon(icon == null ? executor.getToolWindowIcon() : icon);
            }
          });
        }

        @Override
        public void processTerminated(final ProcessEvent event) {
          LaterInvocator.invokeLater(new Runnable() {
            @Override
            public void run() {
              final Icon icon = descriptor.getIcon();
              content.setIcon(icon == null ? executor.getDisabledIcon() : IconLoader.getTransparentIcon(icon));
            }
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
    content.setDisplayName(descriptor.getDisplayName());
    descriptor.setAttachedContent(content);
    content.getManager().setSelectedContent(content);

    if (!descriptor.isActivateToolWindowWhenAdded()) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myProject.isDisposed()) return;
        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(executor.getToolWindowId());
        // let's activate tool window, but don't move focus
        //
        // window.show() isn't valid here, because it will not
        // mark the window as "last activated" windows and thus
        // some action like navigation up/down in stacktrace wont
        // work correctly
        descriptor.getPreferredFocusComputable();
        window.activate(null, descriptor.isAutoFocusContent(), descriptor.isAutoFocusContent());
      }
    });
  }

  @Override
  @Nullable
  @Deprecated
  public RunContentDescriptor getReuseContent(final Executor requestor, DataContext dataContext) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    return getReuseContent(requestor, GenericProgramRunner.CONTENT_TO_REUSE_DATA_KEY.getData(dataContext));
  }

  @Override
  @Nullable
  @Deprecated
  public RunContentDescriptor getReuseContent(Executor requestor, @Nullable RunContentDescriptor contentToReuse) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    if (contentToReuse != null) return contentToReuse;

    final ContentManager contentManager = getContentManagerForRunner(requestor);
    return chooseReuseContentForDescriptor(contentManager, contentToReuse, 0L, null);
  }

  @Nullable
  @Override
  public RunContentDescriptor getReuseContent(Executor requestor, @NotNull ExecutionEnvironment executionEnvironment) {
    return getReuseContent(executionEnvironment);
  }

  @Nullable
  @Override
  public RunContentDescriptor getReuseContent(@NotNull ExecutionEnvironment executionEnvironment) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    RunContentDescriptor contentToReuse = executionEnvironment.getContentToReuse();
    if (contentToReuse != null) return contentToReuse;

    final ContentManager contentManager = getContentManagerForRunner(executionEnvironment.getExecutor());
    return chooseReuseContentForDescriptor(contentManager, contentToReuse, executionEnvironment.getExecutionId(),
                                           executionEnvironment.toString());
  }

  @Override
  public RunContentDescriptor findContentDescriptor(final Executor requestor, final ProcessHandler handler) {
    return getDescriptorBy(handler, requestor);
  }

  @Override
  public void showRunContent(@NotNull final Executor info, RunContentDescriptor descriptor, RunContentDescriptor contentToReuse) {
    if (contentToReuse != null) {
      final Content attachedContent = contentToReuse.getAttachedContent();
      if (attachedContent.getManager() != null) {
        descriptor.setAttachedContent(attachedContent);
      }
    }
    showRunContent(info, descriptor, descriptor != null ? descriptor.getExecutionId(): 0L);
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
      if (attachedContent != null && attachedContent.isValid() && contentManager.getIndexOfContent(attachedContent) != -1) content = attachedContent;
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
    ArrayList<Content> contents = new ArrayList<Content>(Arrays.asList(contentManager.getContents()));
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
  private ContentManager getContentManagerForRunner(final Executor executor) {
    final ContentManager contentManager = myToolwindowIdToContentManagerMap.get(executor.getToolWindowId());
    if (contentManager == null) {
      LOG.error("Runner " + executor.getId() + " is not registered");
    }
    return contentManager;
  }

  private Content createNewContent(final ContentManager contentManager, final RunContentDescriptor descriptor, Executor executor) {
    final String processDisplayName = descriptor.getDisplayName();
    final Content content = ContentFactory.SERVICE.getInstance().createContent(descriptor.getComponent(), processDisplayName, true);
    content.putUserData(DESCRIPTOR_KEY, descriptor);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    contentManager.addContent(content);
    new CloseListener(content, executor);
    return content;
  }

  private static boolean isTerminated(@NotNull final Content content) {
    final RunContentDescriptor descriptor = getRunContentDescriptorByContent(content);
    if (descriptor == null) {
      return true;
    }
    else {
      final ProcessHandler processHandler = descriptor.getProcessHandler();
      return processHandler == null || processHandler.isProcessTerminated();
    }
  }

  @Nullable
  public static RunContentDescriptor getRunContentDescriptorByContent(@NotNull final Content content) {
    return content.getUserData(DESCRIPTOR_KEY);
  }


  @Override
  @Nullable
  public ToolWindow getToolWindowByDescriptor(@NotNull final RunContentDescriptor descriptor) {
    for (Map.Entry<String, ContentManager> entry : myToolwindowIdToContentManagerMap.entrySet()) {
      if (getRunContentByDescriptor(entry.getValue(), descriptor) != null) {
        return ToolWindowManager.getInstance(myProject).getToolWindow(entry.getKey());
      }
    }
    return null;
  }

  @Nullable
  private static Content getRunContentByDescriptor(final ContentManager contentManager, final RunContentDescriptor descriptor) {
    final Content[] contents = contentManager.getContents();
    for (final Content content : contents) {
      if (descriptor.equals(content.getUserData(DESCRIPTOR_KEY))) {
        return content;
      }
    }
    return null;
  }

  @Override
  public void addRunContentListener(final RunContentListener listener, final Executor executor) {
    final Disposable disposable = Disposer.newDisposable();
    myProject.getMessageBus().connect(disposable).subscribe(RUN_CONTENT_TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(RunContentDescriptor descriptor, @NotNull Executor executor2) {
        if (executor2.equals(executor)) {
          listener.contentSelected(descriptor);
        }
      }

      @Override
      public void contentRemoved(RunContentDescriptor descriptor, @NotNull Executor executor2) {
        if (executor2.equals(executor)) {
          listener.contentRemoved(descriptor);
        }
      }
    });
    myListeners.put(listener, disposable);
  }

  @Override
  public void addRunContentListener(final RunContentListener listener) {
    final Disposable disposable = Disposer.newDisposable();
    myProject.getMessageBus().connect(disposable).subscribe(RUN_CONTENT_TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(RunContentDescriptor descriptor, @NotNull Executor executor) {
        listener.contentSelected(descriptor);
      }

      @Override
      public void contentRemoved(RunContentDescriptor descriptor, @NotNull Executor executor) {
        listener.contentRemoved(descriptor);
      }
    });
    myListeners.put(listener, disposable);
  }

  @Override
  @NotNull
  public List<RunContentDescriptor> getAllDescriptors() {
    if (myToolwindowIdToContentManagerMap.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] ids = myToolwindowIdToContentManagerMap.keySet().toArray(new String[myToolwindowIdToContentManagerMap.size()]);
    final List<RunContentDescriptor> descriptors = new ArrayList<RunContentDescriptor>();
    for (String id : ids) {
      final ContentManager contentManager = myToolwindowIdToContentManagerMap.get(id);
      for (final Content content : contentManager.getContents()) {
        final RunContentDescriptor descriptor = getRunContentDescriptorByContent(content);
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
      }
    }

    return descriptors;
  }

  @Override
  public void removeRunContentListener(final RunContentListener listener) {
    Disposable disposable = myListeners.remove(listener);
    if (disposable != null) {
      Disposer.dispose(disposable);
    }
  }

  @Nullable
  private RunContentDescriptor getDescriptorBy(ProcessHandler handler, Executor runnerInfo) {
    ContentManager contentManager = getContentManagerForRunner(runnerInfo);
    Content[] contents = contentManager.getContents();
    for (Content content : contents) {
      RunContentDescriptor runContentDescriptor = content.getUserData(DESCRIPTOR_KEY);
      if (runContentDescriptor.getProcessHandler() == handler) {
        return runContentDescriptor;
      }
    }
    return null;
  }

  private class CloseListener extends ContentManagerAdapter implements ProjectManagerListener {
    private Content myContent;
    private final Executor myExecutor;

    private CloseListener(@NotNull final Content content, @NotNull Executor executor) {
      myContent = content;
      content.getManager().addContentManagerListener(this);
      ProjectManager.getInstance().addProjectManagerListener(this);
      myExecutor = executor;
    }

    @Override
    public void contentRemoved(final ContentManagerEvent event) {
      final Content content = event.getContent();
      if (content == myContent) {
        dispose();
      }
    }

    private void dispose() {
      if (myContent == null) return;

      final Content content = myContent;
      try {
        final RunContentDescriptor descriptor = getRunContentDescriptorByContent(content);

        getSyncPublisher().contentRemoved(descriptor, myExecutor);

        if (descriptor != null)
          Disposer.dispose(descriptor);
      }
      finally {
        content.getManager().removeContentManagerListener(this);
        ProjectManager.getInstance().removeProjectManagerListener(this);
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
    public void projectOpened(final Project project) {
    }

    @Override
    public void projectClosed(final Project project) {
      if (myContent != null && project == myProject) {
        myContent.getManager().removeContent(myContent, true);
        dispose(); // Dispose content even if content manager refused to.
      }
    }

    @Override
    public boolean canCloseProject(final Project project) {
      if (project != myProject) return true;

      if (myContent == null) return true;

      final boolean canClose = closeQuery(true);
      if (canClose) {
        myContent.getManager().removeContent(myContent, true);
        myContent = null;
      }
      return canClose;
    }

    @Override
    public void projectClosing(final Project project) {
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
      final boolean destroyProcess;
      if (processHandler.isSilentlyDestroyOnClose() || Boolean.TRUE.equals(processHandler.getUserData(ProcessHandler.SILENTLY_DESTROY_ON_CLOSE))) {
        destroyProcess = true;
      }
      else {
        //todo[nik] this is a temporary solution for the following problem: some configurations should not allow user to choose between 'terminating' and 'detaching'
        final boolean useDefault = Boolean.TRUE.equals(processHandler.getUserData(ALWAYS_USE_DEFAULT_STOPPING_BEHAVIOUR_KEY));
        final TerminateRemoteProcessDialog.TerminateOption option = new TerminateRemoteProcessDialog.TerminateOption(processHandler.detachIsDefault(), useDefault);
        final int rc = TerminateRemoteProcessDialog.show(myProject, descriptor.getDisplayName(), option);
        if (rc != DialogWrapper.OK_EXIT_CODE) return false;
        destroyProcess = !option.isToBeShown();
      }
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

        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            final ProcessHandler processHandler = descriptor.getProcessHandler();
            try {
              if (processHandler != null) {
                processHandler.waitFor();
              }
            }
            finally {
              semaphore.up();
            }
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
                synchronized (this) {
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
