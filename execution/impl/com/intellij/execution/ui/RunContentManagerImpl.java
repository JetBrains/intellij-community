/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.TerminateRemoteProcessDialog;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.content.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.Semaphore;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;
import java.util.List;

public class RunContentManagerImpl implements RunContentManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.ui.RunContentManagerImpl");
  public static final Key DESCRIPTOR_KEY = new Key("Descriptor");

  private Project myProject;
  private final Map<String, ContentManager> myToolwindowIdToContentManagerMap = new HashMap<String, ContentManager>();

  private final Map<RunContentListener, MyRunContentListener> myListeners = new com.intellij.util.containers.HashMap<RunContentListener, MyRunContentListener>();
  private final EventDispatcher<MyRunContentListener>         myEventDispatcher;
  private final LinkedList<String> myToolwindowIdZbuffer = new LinkedList<String>();


  public RunContentManagerImpl(Project project) {
    myProject = project;
    myEventDispatcher = EventDispatcher.create(MyRunContentListener.class);
  }

  public void init() {
    final JavaProgramRunner[] registeredRunners = ExecutionRegistry.getInstance().getRegisteredRunners();
    for (JavaProgramRunner registeredRunner : registeredRunners) {
      registerToolwindow(registeredRunner);
    }

    if (ToolWindowManager.getInstance(myProject) == null) return;

    // To ensure ToolwindowManager had already initialized in its projectOpened.
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
          public void stateChanged() {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);

            Set<String> currentWindows = new HashSet<String>();
            String[] toolWindowIds = toolWindowManager.getToolWindowIds();

            for (int i = 0; i < toolWindowIds.length; i++) {
              currentWindows.add(toolWindowIds[i]);
            }
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

  public void dispose() {
    final String[] ids = myToolwindowIdToContentManagerMap.keySet().toArray(new String[myToolwindowIdToContentManagerMap.size()]);
    for (int idx = 0; idx < ids.length; idx++) {
      unregisterToolwindow(ids[idx]);
    }
  }

  private void unregisterToolwindow(final String id) {
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    if (windowManager.getToolWindow(id) == null) return;
    windowManager.unregisterToolWindow(id);
    final ContentManager manager = myToolwindowIdToContentManagerMap.get(id);
    manager.removeAllContents();
    myToolwindowIdToContentManagerMap.remove(id);
    myToolwindowIdZbuffer.remove(id);
  }

  private void registerToolwindow(final JavaProgramRunner runner) {
    final String toolWindowId = runner.getInfo().getToolWindowId();
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager == null) return; //headless environment
    if (toolWindowManager.getToolWindow(toolWindowId) != null) {
      return;
    }

    final ContentManager contentManager = PeerFactory.getInstance().getContentFactory().createContentManager(new TabbedPaneContentUI(), true, myProject);

    class MyDataProvider extends JPanel implements DataProvider {
      private int myInsideGetData = 0;

      MyDataProvider () {
        super(new BorderLayout());
        add(contentManager.getComponent());
      }

      public Object getData(String dataId) {
        myInsideGetData ++;
        try {
          if(DataConstants.HELP_ID.equals(dataId)) {
            return runner.getInfo().getHelpId();
          }
          else {
            return myInsideGetData == 1 ? DataManager.getInstance().getDataContext(contentManager.getComponent()).getData(dataId) : null;
          }
        } finally {
          myInsideGetData--;
        }
      }
    }
    final ToolWindow toolWindow = toolWindowManager.registerToolWindow(toolWindowId, new MyDataProvider(),
                                                                       ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(runner.getInfo().getToolWindowIcon());
    new ContentManagerWatcher(toolWindow, contentManager);
    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      public void selectionChanged(final ContentManagerEvent event) {
        final Content content = event.getContent();
        final RunContentDescriptor descriptor = content != null ? getRunContentDescriptorByContent(content) : null;
        myEventDispatcher.getMulticaster().contentSelected(descriptor, toolWindowId);
      }
    });
    ((ToolWindowManagerImpl)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      public void stateChanged() {

      }
    });
    ListenerUtil.addFocusListener(contentManager.getComponent(), new FocusAdapter() {
      public void focusGained(final FocusEvent e) {
      }
    });
    myToolwindowIdToContentManagerMap.put(toolWindowId, contentManager);
    myToolwindowIdZbuffer.addLast(toolWindowId);
  }

  public void toFrontRunContent(final RunnerInfo requestor, final ProcessHandler handler) {
    final RunContentDescriptor descriptor = getDescriptorBy(handler, requestor);
    if (descriptor == null) {
      return;
    }
    toFrontRunContent(requestor, descriptor);

  }


  private void toFrontRunContent(final RunnerInfo requestor, final RunContentDescriptor descriptor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final ContentManager contentManager = getContentManagerForRunner(requestor);

        final Content content = getRunContentByDescriptor(contentManager, descriptor);

        if (contentManager != null) {
          contentManager.setSelectedContent(content);

          if (content != null) {
            final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(requestor.getToolWindowId());
            toolWindow.show(null);
          }
        }
      }
    });
  }

  public void toFrontRunContent(final JavaProgramRunner requestor, final RunContentDescriptor descriptor) {
    toFrontRunContent(requestor.getInfo(), descriptor);
  }

  public void hideRunContent(final JavaProgramRunner requestor, final RunContentDescriptor descriptor) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        final String toolWindowId = requestor.getInfo().getToolWindowId();
        final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
        if (toolWindow == null) return;
        toolWindow.show(null);
      }
    });
  }

  public RunContentDescriptor getSelectedContent(final RunnerInfo info) {
    final ContentManager contentManager = getContentManagerForRunner(info);
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

  public RunContentDescriptor getSelectedContent() {
    final String activeWindow = myToolwindowIdZbuffer.isEmpty() ? null : myToolwindowIdZbuffer.getFirst();

    if (activeWindow != null) {
      final ContentManager contentManager = myToolwindowIdToContentManagerMap.get(activeWindow);
      if (contentManager != null) {
        final Content selectedContent = contentManager.getSelectedContent();
        if (selectedContent != null) {
          final RunContentDescriptor runContentDescriptorByContent = getRunContentDescriptorByContent(selectedContent);
          if (runContentDescriptorByContent != null) {
            return runContentDescriptorByContent;
          }
        }
      }
    }
    return null;
  }

  public boolean removeRunContent(final JavaProgramRunner requestor, final RunContentDescriptor descriptor) {
    final ContentManager contentManager = getContentManagerForRunner(requestor.getInfo());
    final Content content = getRunContentByDescriptor(contentManager, descriptor);
    if (content != null) {
      return contentManager.removeContent(content);
    }
    return false;
  }

  public void showRunContent(final JavaProgramRunner requestor, final RunContentDescriptor descriptor) {
    if(ApplicationManager.getApplication().isUnitTestMode()) return;

    final RunnerInfo runnerInfo = requestor.getInfo();
    LOG.assertTrue(runnerInfo != null);
    final ContentManager contentManager = getContentManagerForRunner(runnerInfo);
    RunContentDescriptor oldDescriptor = chooseReuseContentForDescriptor(contentManager, descriptor);

    Content content;

    if(oldDescriptor != null) {
      content = (Content)oldDescriptor.getAttachedContent();
      myEventDispatcher.getMulticaster().contentRemoved(oldDescriptor, runnerInfo.getToolWindowId());
      oldDescriptor.dispose(); // is of the same category, can be reused
    }
    else {
      content = createNewContent(contentManager, descriptor, runnerInfo.getToolWindowId());
    }

    content.setComponent(descriptor.getComponent());
    content.putUserData(DESCRIPTOR_KEY, descriptor);
    content.setDisplayName(descriptor.getDisplayName());
    descriptor.setAttachedContent(content);
    content.getManager().setSelectedContent(content);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(runnerInfo.getToolWindowId());
        toolWindow.activate(null);
      }
    });
  }

  public RunContentDescriptor getReuseContent(final JavaProgramRunner requestor, DataContext dataContext) {
    if(ApplicationManager.getApplication().isUnitTestMode()) return null;
    RunContentDescriptor runContentDescriptor = (RunContentDescriptor)dataContext.getData(RunStrategy.CONTENT_TO_REUSE);

    if(runContentDescriptor != null) return runContentDescriptor;

    final RunnerInfo runnerInfo = requestor.getInfo();
    LOG.assertTrue(runnerInfo != null);
    final ContentManager contentManager = getContentManagerForRunner(runnerInfo);

    return chooseReuseContentForDescriptor(contentManager, runContentDescriptor);
  }

  public void showRunContent(JavaProgramRunner requestor, RunContentDescriptor descriptor, RunContentDescriptor contentToReuse) {
    if(contentToReuse != null) {
      descriptor.setAttachedContent(contentToReuse.getAttachedContent());
    }
    showRunContent(requestor, descriptor);
  }

  private RunContentDescriptor chooseReuseContentForDescriptor(final ContentManager contentManager, final RunContentDescriptor descriptor) {
    Content content = null;
    if (descriptor != null) {
      final Content attachedContent = descriptor.getAttachedContent();
      if (attachedContent != null && attachedContent.isValid()) content = attachedContent;
    }
    if (content == null) {
      content = contentManager.getSelectedContent();
      if (content != null && content.isPinned()) content = null;
    }
    if (content == null || !isTerminated(content)) {
      return null;
    }
    final RunContentDescriptor oldDescriptor = getRunContentDescriptorByContent(content);
    if (oldDescriptor != null && !oldDescriptor.isContentReuseProhibited()) {
      return oldDescriptor;
    }

    return null;
  }

  private ContentManager getContentManagerForRunner(final RunnerInfo runnerInfo) {
    final ContentManager contentManager = myToolwindowIdToContentManagerMap.get(runnerInfo.getToolWindowId());
    if (contentManager == null) {
      LOG.error("Runner " + runnerInfo.getId() + " is not registered");
    }
    return contentManager;
  }

  private Content createNewContent(final ContentManager contentManager, final RunContentDescriptor descriptor, String toolWindowId) {
    final String processDisplayName = descriptor.getDisplayName();
    final Content content = PeerFactory.getInstance().getContentFactory().createContent(descriptor.getComponent(), processDisplayName, true);
    content.putUserData(DESCRIPTOR_KEY, descriptor);
    contentManager.addContent(content);
    new CloseListener(content, myProject, processDisplayName, toolWindowId);
    return content;
  }

  private boolean isTerminated(final Content content) {
    final RunContentDescriptor descriptor = getRunContentDescriptorByContent(content);
    if (descriptor == null) {
      return true;
    }
    else {
      final ProcessHandler processHandler = descriptor.getProcessHandler();
      return processHandler != null ? processHandler.isProcessTerminated() : true;
    }
  }

  private RunContentDescriptor getRunContentDescriptorByContent(final Content content) {
    return (RunContentDescriptor)content.getUserData(DESCRIPTOR_KEY);
  }

  private Content getRunContentByDescriptor(final ContentManager contentManager, final RunContentDescriptor descriptor) {
    final Content[] contents = contentManager.getContents();
    for (int idx = 0; idx < contents.length; idx++) {
      final Content content = contents[idx];
      if (descriptor.equals(content.getUserData(DESCRIPTOR_KEY))) {
        return content;
      }
    }
    return null;
  }

  public void addRunContentListener(final RunContentListener listener, final RunnerInfo runnerInfo) {
    myEventDispatcher.addListener(new MyRunContentListener() {
      public void contentSelected(RunContentDescriptor descriptor, String toolwindowId) {
        if(toolwindowId.equals(runnerInfo.getToolWindowId())) {
          listener.contentSelected(descriptor);
        }
      }

      public void contentRemoved(RunContentDescriptor descriptor, String toolwindowId) {
        if(toolwindowId.equals(runnerInfo.getToolWindowId())) {
          listener.contentRemoved(descriptor);
        }
      }
    });
  }

  public void addRunContentListener(final RunContentListener listener) {
    MyRunContentListener windowedListener = new MyRunContentListener() {
      public void contentSelected(RunContentDescriptor descriptor, String ToolwindowId) {
        listener.contentSelected(descriptor);
      }

      public void contentRemoved(RunContentDescriptor descriptor, String ToolwindowId) {
        listener.contentRemoved(descriptor);
      }
    };
    myEventDispatcher.addListener(windowedListener);
    myListeners.put(listener, windowedListener);
  }

  public RunContentDescriptor[] getAllDescriptors() {
    final List<RunContentDescriptor> descriptors = new ArrayList<RunContentDescriptor>();
    final String[] ids = myToolwindowIdToContentManagerMap.keySet().toArray(new String[myToolwindowIdToContentManagerMap.size()]);
    for (int i = 0; i < ids.length; i++) {
      final ContentManager contentManager = myToolwindowIdToContentManagerMap.get(ids[i]);
      final Content[] contents = contentManager.getContents();
      for (int idx = 0; idx < contents.length; idx++) {
        final Content content = contents[idx];
        final RunContentDescriptor descriptor = getRunContentDescriptorByContent(content);
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
      }
    }

    return descriptors.toArray(new RunContentDescriptor[descriptors.size()]);
  }

  public void removeRunContentListener(final RunContentListener listener) {
    MyRunContentListener contentListener = myListeners.remove(listener);
    myEventDispatcher.removeListener(contentListener);
  }

  private RunContentDescriptor getDescriptorBy(ProcessHandler handler, RunnerInfo runnerInfo) {
    ContentManager contentManager = getContentManagerForRunner(runnerInfo);
    Content[] contents = contentManager.getContents();
    for (int i = 0; i < contents.length; i++) {
      Content content = contents[i];
      RunContentDescriptor runContentDescriptor = (RunContentDescriptor)content.getUserData(DESCRIPTOR_KEY);
      if (runContentDescriptor.getProcessHandler() == handler) {
        return runContentDescriptor;
      }
    }
    return null;
  }

  private class CloseListener extends ContentManagerAdapter implements ProjectManagerListener {
    private final Project myProject;
    private Content myContent;
    private final String myProcessDisplayName;
    private final String myToolwindowId;

    public CloseListener(final Content content, final Project project, final String processDisplayName, String toolWindowId) {
      myContent = content;
      myProject = project;
      content.getManager().addContentManagerListener(this);
      ProjectManager.getInstance().addProjectManagerListener(this);
      myProcessDisplayName = processDisplayName;
      myToolwindowId = toolWindowId;
    }

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

        myEventDispatcher.getMulticaster().contentRemoved(descriptor, myToolwindowId);

        descriptor.dispose();
      }
      finally {
        content.getManager().removeContentManagerListener(this);
        ProjectManager.getInstance().removeProjectManagerListener(this);
        content.release(); // don't invoke myContent.release() because myContent becomes null after destroyProcess()
        myContent = null;
      }
    }

    public void contentRemoveQuery(final ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        final boolean canClose = closeQuery();
        if (!canClose) {
          event.consume();
        }
      }
    }

    public void projectOpened(final Project project) {
    }

    public void projectClosed(final Project project) {
      if (myContent != null && project == myProject) {
        myContent.getManager().removeContent(myContent);
        dispose(); // Dispose content even if content manager refused to.
      }
    }

    public boolean canCloseProject(final Project project) {
      if (project != myProject) return true;

      if (myContent == null) return true;

      final boolean canClose = closeQuery();
      if (canClose) {
        final Content content = myContent;
        content.getManager().removeContent(content);
        myContent = null;
      }
      return canClose;
    }

    public void projectClosing(final Project project) {
    }

    private boolean closeQuery() {
      final RunContentDescriptor descriptor = getRunContentDescriptorByContent(myContent);

      if (descriptor == null) {
        return true;
      }

      final ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler == null || processHandler.isProcessTerminated()) {
        return true;
      }

      final TerminateRemoteProcessDialog terminateDialog = new TerminateRemoteProcessDialog(myProject, myProcessDisplayName,
                                                                                            processHandler.detachIsDefault());
      terminateDialog.show();

      if (terminateDialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        if (terminateDialog.forceTermination()) {
          processHandler.destroyProcess();
        }
        else {
          processHandler.detachProcess();
        }
        waitForProcess(descriptor);
        return true;
      }

      return false;
    }
  }

  public void waitForProcess(final RunContentDescriptor descriptor) {
    String progressTitle =  ExecutionBundle.message("terminating.process.progress.title", descriptor.getDisplayName());

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      private ProgressIndicator myProgressIndicator;
      private Semaphore mySemaphore = new Semaphore();

      private Thread myWaitThread = new Thread() {
        public void run() {
          descriptor.getProcessHandler().waitFor();
          mySemaphore.up();
        }
      };

      private Thread myCancelListener = new Thread() {
        public void run() {
          for(;;) {
            if(myProgressIndicator.isCanceled() || !myProgressIndicator.isRunning()) {
              mySemaphore.up();
              break;
            }
            try {
              synchronized (this) {
                wait(2000);
              }
            }
            catch (InterruptedException e) {
            }
          }
        }
      };

      public void run() {
        myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (myProgressIndicator != null) {
          myProgressIndicator.setText(ExecutionBundle.message("waiting.for.vm.detach.progress.text"));
        }

        myWaitThread.start();
        myCancelListener.start();

        mySemaphore.down();
        mySemaphore.waitFor();
      }
    }, progressTitle, true, myProject);
  }

  public static interface MyRunContentListener extends EventListener {
    void contentSelected(RunContentDescriptor descriptor, String toolwindowId);
    void contentRemoved (RunContentDescriptor descriptor, String toolwindowId);
  }
}