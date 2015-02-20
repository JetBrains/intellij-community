package org.jetbrains.builtInWebServer;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.AsyncValueLoader;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.io.IOException;

public abstract class NetService implements Disposable {
  protected static final Logger LOG = Logger.getInstance(NetService.class);

  protected final Project project;
  private final ConsoleManager consoleManager;

  protected final AsyncValueLoader<OSProcessHandler> processHandler = new AsyncValueLoader<OSProcessHandler>() {
    @Override
    protected boolean isCancelOnReject() {
      return true;
    }

    @Nullable
    private OSProcessHandler doGetProcessHandler(int port) {
      try {
        return createProcessHandler(project, port);
      }
      catch (ExecutionException e) {
        LOG.error(e);
        return null;
      }
    }

    @NotNull
    @Override
    protected Promise<OSProcessHandler> load(@NotNull final AsyncPromise<OSProcessHandler> promise) throws IOException {
      final int port = NetUtils.findAvailableSocketPort();
      final OSProcessHandler processHandler = doGetProcessHandler(port);
      if (processHandler == null) {
        promise.setError(Promise.createError("rejected"));
        return promise;
      }

      promise.rejected(new Consumer<Throwable>() {
        @Override
        public void consume(Throwable error) {
          processHandler.destroyProcess();
          if (!(error instanceof Promise.MessageError)) {
            LOG.error(error);
          }
        }
      });

      final MyProcessAdapter processListener = new MyProcessAdapter();
      processHandler.addProcessListener(processListener);
      processHandler.startNotify();

      if (promise.getState() == Promise.State.REJECTED) {
        return promise;
      }

      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          if (promise.getState() != Promise.State.REJECTED) {
            try {
              connectToProcess(promise, port, processHandler, processListener);
            }
            catch (Throwable e) {
              if (!promise.setError(e)) {
                LOG.error(e);
              }
            }
          }
        }
      });
      return promise;
    }

    @Override
    protected void disposeResult(@NotNull OSProcessHandler processHandler) {
      try {
        closeProcessConnections();
      }
      finally {
        processHandler.destroyProcess();
      }
    }
  };

  protected NetService(@NotNull Project project) {
    this(project, new ConsoleManager());
  }

  protected NetService(@NotNull Project project, @NotNull ConsoleManager consoleManager) {
    this.project = project;
    this.consoleManager = consoleManager;
  }

  @Nullable
  protected abstract OSProcessHandler createProcessHandler(@NotNull Project project, int port) throws ExecutionException;

  protected void connectToProcess(@NotNull AsyncPromise<OSProcessHandler> promise,
                                  int port,
                                  @NotNull OSProcessHandler processHandler,
                                  @NotNull Consumer<String> errorOutputConsumer) {
    promise.setResult(processHandler);
  }

  protected abstract void closeProcessConnections();

  @Override
  public void dispose() {
    processHandler.reset();
  }

  protected void configureConsole(@NotNull TextConsoleBuilder consoleBuilder) {
  }

  @NotNull
  protected abstract String getConsoleToolWindowId();

  @NotNull
  protected abstract Icon getConsoleToolWindowIcon();

  @NotNull
  public ActionGroup getConsoleToolWindowActions() {
    return new DefaultActionGroup();
  }

  private final class MyProcessAdapter extends ProcessAdapter implements Consumer<String> {
    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      print(event.getText(), ConsoleViewContentType.getConsoleViewType(outputType));
    }

    private void print(String text, ConsoleViewContentType contentType) {
      consoleManager.getConsole(NetService.this).print(text, contentType);
    }

    @Override
    public void processTerminated(ProcessEvent event) {
      processHandler.reset();
      print(getConsoleToolWindowId() + " terminated\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    @Override
    public void consume(String message) {
      print(message, ConsoleViewContentType.ERROR_OUTPUT);
    }
  }
}