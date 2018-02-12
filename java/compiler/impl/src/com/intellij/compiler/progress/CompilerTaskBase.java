package com.intellij.compiler.progress;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The point of this class is not to depend on Swing to be available in headless environments.
 * Please do not add JComponent references here.
 */
public abstract class CompilerTaskBase extends Task.Backgroundable {
  protected final boolean myWaitForPreviousSession;
  @NotNull protected final Object myContentId = new IDObject("content_id");
  @NotNull protected Object mySessionId; // by default sessionID should be unique, just as content ID
  protected volatile ProgressIndicator myIndicator = new EmptyProgressIndicator();
  protected Runnable myCompileWork;
  protected Runnable myRestartWork;

  public CompilerTaskBase(Project project, String title, boolean waitForPreviousSession) {
    super(project, title);
    mySessionId = myContentId;
    myWaitForPreviousSession = waitForPreviousSession;
  }

  @NotNull
  public Object getSessionId() {
    return mySessionId;
  }

  public void setSessionId(@NotNull Object sessionId) {
    mySessionId = sessionId;
  }

  public ProgressIndicator getIndicator() {
    return myIndicator;
  }

  @Override
  public void run(@NotNull final ProgressIndicator indicator) {
    myIndicator = indicator;
    myIndicator.setIndeterminate(false);
    beforeRun();

    final Semaphore semaphore = ((CompilerManagerImpl)CompilerManager.getInstance(myProject)).getCompilationSemaphore();
    boolean acquired = false;
    try {

      try {
        while (!acquired) {
          acquired = semaphore.tryAcquire(300, TimeUnit.MILLISECONDS);
          if (!acquired && !myWaitForPreviousSession) {
            return;
          }
          if (indicator.isCanceled()) {
            // give up obtaining the semaphore,
            // let compile work begin in order to stop gracefuly on cancel event
            break;
          }
        }
      }
      catch (InterruptedException ignored) {
      }

      associateProgress(myIndicator);

      myCompileWork.run();
    }
    catch (ProcessCanceledException ignored) {
    }
    finally {
      try {
        indicator.stop();
        afterRun();
      }
      finally {
        if (acquired) {
          semaphore.release();
        }
      }
    }
  }

  protected void afterRun() {}

  protected void beforeRun() {}

  protected void associateProgress(ProgressIndicator indicator) {}

  public void start(Runnable compileWork, Runnable restartWork) {
    myCompileWork = compileWork;
    myRestartWork = restartWork;
    queue();
  }

  public abstract void addMessage(CompilerMessage message);

  public abstract void registerCloseAction(Runnable runnable);

  @NotNull
  public Object getContentId() {
    return myContentId;
  }

  public static final class IDObject {
    private final String myDisplayName;

    public IDObject(@NotNull String displayName) {
      myDisplayName = displayName;
    }

    @Override
    public String toString() {
      return myDisplayName;
    }
  }
}
