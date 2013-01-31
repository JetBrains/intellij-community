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

/*
 * @author: Eugene Zhuravlev
 * Date: Jan 22, 2003
 * Time: 2:25:31 PM
 */
package com.intellij.compiler.progress;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.compiler.impl.CompilerErrorTreeView;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.ide.errorTreeView.impl.ErrorTreeViewConfiguration;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.pom.Navigatable;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompilerTask extends Task.Backgroundable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.progress.CompilerProgressIndicator");
  private static final Key<Key<?>> CONTENT_ID_KEY = Key.create("CONTENT_ID");
  private static final String APP_ICON_ID = "compiler";
  private Key<Key<?>> myContentIdKey = CONTENT_ID_KEY;
  private final Key<Key<?>> myContentId = Key.create("compile_content");
  private NewErrorTreeViewPanel myErrorTreeView;
  private final Object myMessageViewLock = new Object();
  private final String myContentName;
  private final boolean myHeadlessMode;
  private final boolean myForceAsyncExecution;
  private int myErrorCount = 0;
  private int myWarningCount = 0;
  private boolean myMessagesAutoActivated = false;

  private volatile ProgressIndicator myIndicator = new EmptyProgressIndicator();
  private Runnable myCompileWork;
  private final AtomicBoolean myMessageViewWasPrepared = new AtomicBoolean(false);
  private Runnable myRestartWork;

  public CompilerTask(@NotNull Project project, String contentName, final boolean headlessMode, boolean forceAsync) {
    super(project, contentName);
    myContentName = contentName;
    myHeadlessMode = headlessMode;
    myForceAsyncExecution = forceAsync;
  }

  public void setContentIdKey(Key<Key<?>> contentIdKey) {
    myContentIdKey = contentIdKey != null? contentIdKey : CONTENT_ID_KEY;
  }

  public String getProcessId() {
    return "compilation";
  }

  @Override
  public DumbModeAction getDumbModeAction() {
    return DumbModeAction.WAIT;
  }

  public boolean shouldStartInBackground() {
    return true;
  }

  public ProgressIndicator getIndicator() {
    return myIndicator;
  }

  @Nullable
  public NotificationInfo getNotificationInfo() {
    return new NotificationInfo(myErrorCount > 0? "Compiler (errors)" : "Compiler (success)", "Compilation Finished", myErrorCount + " Errors, " + myWarningCount + " Warnings", true);
  }

  private CloseListener myCloseListener;

  public void run(@NotNull final ProgressIndicator indicator) {
    myIndicator = indicator;

    final ProjectManager projectManager = ProjectManager.getInstance();
    projectManager.addProjectManagerListener(myProject, myCloseListener = new CloseListener());

    final Semaphore semaphore = ((CompilerManagerImpl)CompilerManager.getInstance(myProject)).getCompilationSemaphore();
    boolean acquired = false;
    try {

      try {
        final Application app = ApplicationManager.getApplication();
        while (!acquired) {
          acquired = semaphore.tryAcquire(500, TimeUnit.MILLISECONDS);
          if (indicator.isCanceled()) {
            // give up obtaining the semaphore,
            // let compile work begin in order to stop gracefuly on cancel event
            break;
          }
          if (app.isDispatchThread()) {
            UIUtil.dispatchAllInvocationEvents();
          }
        }
      }
      catch (InterruptedException ignored) {
      }

      if (!isHeadless()) {
        addIndicatorDelegate();
      }
      myCompileWork.run();
    }
    finally {
      try {
        indicator.stop();
        projectManager.removeProjectManagerListener(myProject, myCloseListener);
      }
      finally {
        if (acquired) {
          semaphore.release();
        }
      }
    }
  }

  private void prepareMessageView() {
    if (!myIndicator.isRunning()) {
      return;
    }
    if (myMessageViewWasPrepared.getAndSet(true)) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        synchronized (myMessageViewLock) {
          // clear messages from the previous compilation
          if (myErrorTreeView == null) {
            // if message view != null, the contents has already been cleared
            removeAllContents(myProject, null);
          }
        }
      }
    });
  }

  private void addIndicatorDelegate() {
    ProgressIndicator indicator = myIndicator;
    if (!(indicator instanceof ProgressIndicatorEx)) return;
    ((ProgressIndicatorEx)indicator).addStateDelegate(new ProgressIndicatorBase() {

      public void cancel() {
        super.cancel();
        closeUI();
        stopAppIconProgress();
      }

      public void stop() {
        super.stop();
        if (!isCanceled()) {
          closeUI();
        }
        stopAppIconProgress();
      }

      private void stopAppIconProgress() {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            AppIcon appIcon = AppIcon.getInstance();
            if (appIcon.hideProgress(myProject, APP_ICON_ID)) {
              if (myErrorCount > 0) {
                appIcon.setErrorBadge(myProject, String.valueOf(myErrorCount));
                appIcon.requestAttention(myProject, true);
              } else {
                appIcon.setOkBadge(myProject, true);
                appIcon.requestAttention(myProject, false);
              }
            }
          }
        });
      }

      public void setText(final String text) {
        super.setText(text);
        updateProgressText();
      }

      public void setText2(final String text) {
        super.setText2(text);
        updateProgressText();
      }

      public void setFraction(final double fraction) {
        super.setFraction(fraction);
        updateProgressText();
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            AppIcon.getInstance().setProgress(myProject, APP_ICON_ID, AppIconScheme.Progress.BUILD, fraction, true);
          }
        });
      }

      protected void onProgressChange() {
        prepareMessageView();
      }
    });
  }

  public void cancel() {
    if (!myIndicator.isCanceled()) {
      myIndicator.cancel();
    }
  }

  public void addMessage(final CompilerMessage message) {
    prepareMessageView();

    final CompilerMessageCategory messageCategory = message.getCategory();
    if (CompilerMessageCategory.WARNING.equals(messageCategory)) {
      myWarningCount += 1;
    }
    else if (CompilerMessageCategory.ERROR.equals(messageCategory)) {
      myErrorCount += 1;
      informWolf(message);
    }

    if (ApplicationManager.getApplication().isDispatchThread()) {
      openMessageView();
      doAddMessage(message);
    }
    else {
      final Window window = getWindow();
      final ModalityState modalityState = window != null ? ModalityState.stateForComponent(window) : ModalityState.NON_MODAL;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (!myProject.isDisposed()) {
            openMessageView();
            doAddMessage(message);
          }
        }
      }, modalityState);
    }
  }

  private void informWolf(final CompilerMessage message) {
    WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(myProject);
    VirtualFile file = getVirtualFile(message);
    wolf.queue(file);
  }

  private void doAddMessage(final CompilerMessage message) {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        final Navigatable navigatable = message.getNavigatable();
        final VirtualFile file = message.getVirtualFile();
        final CompilerMessageCategory category = message.getCategory();
        final int type = translateCategory(category);
        final String[] text = convertMessage(message);
        if (navigatable != null) {
          final String groupName = file != null? file.getPresentableUrl() : category.getPresentableText();
          myErrorTreeView.addMessage(type, text, groupName, navigatable, message.getExportTextPrefix(), message.getRenderTextPrefix(), message.getVirtualFile());
        }
        else {
          myErrorTreeView.addMessage(type, text, file, -1, -1, message.getVirtualFile());
        }

        final boolean shouldAutoActivate =
          !myMessagesAutoActivated &&
          (
            CompilerMessageCategory.ERROR.equals(category) ||
            (CompilerMessageCategory.WARNING.equals(category) && !ErrorTreeViewConfiguration.getInstance(myProject).isHideWarnings())
          );
        if (shouldAutoActivate) {
          myMessagesAutoActivated = true;
          activateMessageView();
        }
      }
    }
  }

  private static String[] convertMessage(final CompilerMessage message) {
    String text = message.getMessage();
    if (!text.contains("\n")) {
      return new String[]{text};
    }
    ArrayList<String> lines = new ArrayList<String>();
    StringTokenizer tokenizer = new StringTokenizer(text, "\n", false);
    while (tokenizer.hasMoreTokens()) {
      lines.add(tokenizer.nextToken());
    }
    return ArrayUtil.toStringArray(lines);
  }

  public static int translateCategory(CompilerMessageCategory category) {
    if (CompilerMessageCategory.ERROR.equals(category)) {
      return MessageCategory.ERROR;
    }
    if (CompilerMessageCategory.WARNING.equals(category)) {
      return MessageCategory.WARNING;
    }
    if (CompilerMessageCategory.STATISTICS.equals(category)) {
      return MessageCategory.STATISTICS;
    }
    if (CompilerMessageCategory.INFORMATION.equals(category)) {
      return MessageCategory.INFORMATION;
    }
    LOG.error("Unknown message category: " + category);
    return 0;
  }

  public void start(Runnable compileWork, Runnable restartWork) {
    myCompileWork = compileWork;
    myRestartWork = restartWork;
    queue();
  }

  private void updateProgressText() {
    if (isHeadlessMode()) {
      return;
    }
  }

  // error tree view initialization must be invoked from event dispatch thread
  private void openMessageView() {
    if (isHeadlessMode()) {
      return;
    }
    if (myIndicator.isCanceled()) {
      return;
    }
    
    final JComponent component;
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        return;
      }
      myErrorTreeView = new CompilerErrorTreeView(
          myProject,
          myRestartWork
      );
      
      myErrorTreeView.setProcessController(new NewErrorTreeViewPanel.ProcessController() {
        public void stopProcess() {
          cancel();
        }

        public boolean isProcessStopped() {
          return !myIndicator.isRunning();
        }
      });
      component = myErrorTreeView.getComponent();
    }
    
    final MessageView messageView = MessageView.SERVICE.getInstance(myProject);
    final Content content = ContentFactory.SERVICE.getInstance().createContent(component, myContentName, true);
    content.putUserData(myContentIdKey, myContentId);
    messageView.getContentManager().addContent(content);
    myCloseListener.setContent(content, messageView.getContentManager());
    removeAllContents(myProject, content);
    messageView.getContentManager().setSelectedContent(content);
  }

  public void showCompilerContent() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        final MessageView messageView = MessageView.SERVICE.getInstance(myProject);
        Content[] contents = messageView.getContentManager().getContents();
        for (Content content : contents) {
          if (content.getUserData(myContentIdKey) != null) {
            messageView.getContentManager().setSelectedContent(content);
            return;
          }
        }
      }
    }
  }

  private void removeAllContents(Project project, Content notRemove) {
    final MessageView messageView = MessageView.SERVICE.getInstance(project);
    Content[] contents = messageView.getContentManager().getContents();
    for (Content content : contents) {
      if (content.isPinned()) {
        continue;
      }
      if (content == notRemove) {
        continue;
      }
      if (content.getUserData(myContentIdKey) != null) { // the content was added by me
        messageView.getContentManager().removeContent(content, true);
      }
    }
  }

  private void activateMessageView() {
    synchronized (myMessageViewLock) {
      if (myErrorTreeView != null) {
        final ToolWindow tw = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MESSAGES_WINDOW);
        if (tw != null) {
          tw.activate(null, false);
        }
      }
    }
  }

  private void closeUI() {
    if (isHeadlessMode()) {
      return;
    }
    Window window = getWindow();
    ModalityState modalityState = window != null ? ModalityState.stateForComponent(window) : ModalityState.NON_MODAL;
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {
      public void run() {
        synchronized (myMessageViewLock) {
          if (myErrorTreeView != null) {
            final boolean shouldRetainView = myErrorCount > 0 || myWarningCount > 0 && !myErrorTreeView.isHideWarnings();
            if (shouldRetainView) {
              addMessage(new CompilerMessageImpl(myProject, CompilerMessageCategory.STATISTICS, CompilerBundle.message("statistics.error.count", myErrorCount)));
              addMessage(new CompilerMessageImpl(myProject, CompilerMessageCategory.STATISTICS, CompilerBundle.message("statistics.warnings.count", myWarningCount)));
              //activateMessageView();
              myErrorTreeView.selectFirstMessage();
            }
            else {
              removeAllContents(myProject, null);
            }
          }
        }
      }
    }, modalityState);
  }

  public Window getWindow(){
    return null;
  }

  public boolean isHeadless() {
    return myHeadlessMode && !myForceAsyncExecution;
  }

  private boolean isHeadlessMode() {
    return myHeadlessMode;
  }

  private static VirtualFile getVirtualFile(final CompilerMessage message) {
    VirtualFile virtualFile = message.getVirtualFile();
    if (virtualFile == null) {
      Navigatable navigatable = message.getNavigatable();
      if (navigatable instanceof OpenFileDescriptor) {
        virtualFile = ((OpenFileDescriptor)navigatable).getFile();
      }
    }
    return virtualFile;
  }

  public static TextRange getTextRange(final CompilerMessage message) {
    Navigatable navigatable = message.getNavigatable();
    if (navigatable instanceof OpenFileDescriptor) {
      int offset = ((OpenFileDescriptor)navigatable).getOffset();
      return new TextRange(offset, offset);
    }
    return TextRange.EMPTY_RANGE;
  }

  private class CloseListener extends ContentManagerAdapter implements ProjectManagerListener {
    private Content myContent;
    private ContentManager myContentManager;
    private boolean myIsApplicationExitingOrProjectClosing = false;
    private boolean myUserAcceptedCancel = false;

    public boolean canCloseProject(final Project project) {
      assert project != null;
      if (!project.equals(myProject)) {
        return true;
      }
      if (shouldAskUser()) {
        int result = Messages.showOkCancelDialog(
          myProject,
          CompilerBundle.message("warning.compiler.running.on.project.close"),
          CompilerBundle.message("compiler.running.dialog.title"),
          Messages.getQuestionIcon()
        );
        if (result != 0) {
          return false; // veto closing
        }
        myUserAcceptedCancel = true;

        final MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusAdapter() {
          public void compilationFinished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
            connection.disconnect();
            ProjectUtil.closeAndDispose(project);
          }
        });
        cancel();
        return false; // cancel compiler and let it finish, after compilation close the project, but currently - veto closing
      }
      return !myIndicator.isRunning();
    }

    public void setContent(Content content, ContentManager contentManager) {
      myContent = content;
      myContentManager = contentManager;
      contentManager.addContentManagerListener(this);
    }

    public void contentRemoved(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        synchronized (myMessageViewLock) {
          if (myErrorTreeView != null) {
            myErrorTreeView.dispose();
            myErrorTreeView = null;
            if (myIndicator.isRunning()) {
              cancel();
            }
            if (AppIcon.getInstance().hideProgress(myProject, "compiler")) {
              AppIcon.getInstance().setErrorBadge(myProject, null);
            }
          }
        }
        myContentManager.removeContentManagerListener(this);
        myContent.release();
        myContent = null;
      }
    }

    public void contentRemoveQuery(ContentManagerEvent event) {
      if (event.getContent() == myContent) {
        if (!myIndicator.isCanceled() && shouldAskUser()) {
          int result = Messages.showOkCancelDialog(
            myProject,
            CompilerBundle.message("warning.compiler.running.on.toolwindow.close"),
            CompilerBundle.message("compiler.running.dialog.title"),
            Messages.getQuestionIcon()
          );
          if (result != 0) {
            event.consume(); // veto closing
          }
          myUserAcceptedCancel = true;
        }
      }
    }

    private boolean shouldAskUser() {
      // do not ask second time if user already accepted closing
      return !myUserAcceptedCancel && !myIsApplicationExitingOrProjectClosing && myIndicator.isRunning();
    }

    public void projectOpened(Project project) {
    }

    public void projectClosed(Project project) {
      if (project.equals(myProject) && myContent != null) {
        myContentManager.removeContent(myContent, true);
      }
    }

    public void projectClosing(Project project) {
      if (project.equals(myProject)) {
        myIsApplicationExitingOrProjectClosing = true;
      }
    }
  }
}
                                      