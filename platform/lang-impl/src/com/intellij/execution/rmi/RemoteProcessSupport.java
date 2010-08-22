package com.intellij.execution.rmi;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.rmi.PortableRemoteObject;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class RemoteProcessSupport<Target, EntryPoint, Parameters> {
  public static final Logger LOG = Logger.getInstance("#" + RemoteProcessSupport.class);

  private final Class<EntryPoint> myValueClass;
  private final HashMap<Pair<Target, Parameters>, Object> myProcMap =
    new HashMap<Pair<Target, Parameters>, Object>();

  public RemoteProcessSupport(Class<EntryPoint> valueClass) {
    myValueClass = valueClass;
  }

  protected abstract void fireModificationCountChanged();

  protected abstract String getName(Target target);

  protected void logText(Parameters configuration, ProcessEvent event, Key outputType, Object info) {
  }

  public void stopAll() {
    final ArrayList<ProcessHandler> allHandlers = new ArrayList<ProcessHandler>();
    synchronized (myProcMap) {
      for (Object o : myProcMap.values()) {
        final ProcessHandler handler = o instanceof PendingInfo ? ((PendingInfo)o).handler : o instanceof Info ? ((Info)o).handler : null;
        ContainerUtil.addIfNotNull(handler, allHandlers);
      }
      myProcMap.clear();
    }
    for (ProcessHandler handler : allHandlers) {
      handler.destroyProcess();
    }
  }

  public List<Parameters> getActiveConfigurations(@NotNull Target target) {
    final ArrayList<Parameters> result = new ArrayList<Parameters>();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> pair : myProcMap.keySet()) {
        if (pair.first == target) {
          result.add(pair.second);
        }
      }
    }
    return result;
  }

  public EntryPoint acquire(@NotNull final Target target, @NotNull final Parameters configuration) throws Exception {
    ApplicationManagerEx.getApplicationEx().assertTimeConsuming();

    final Ref<Info> ref = Ref.create(null);
    final Pair<Target, Parameters> key = Pair.create(target, configuration);
    if (!getExistingInfo(ref, key)) {
      startProcess(target, configuration, key);
      if (ref.isNull()) {
        try {
          synchronized (ref) {
            while (ref.isNull()) {
              ref.wait(1000);
              ProgressManager.checkCanceled();
            }
          }
        }
        catch (InterruptedException e) {
          ProgressManager.checkCanceled();
        }
      }
    }
    if (ref.isNull()) throw new RuntimeException("Unable to acquire remote proxy for: " + getName(target));
    final Info info = ref.get();
    if (info.handler == null) throw new RuntimeException(info.name);
    return acquire(info);
  }

  public void release(@NotNull Target target, @Nullable Parameters configuration) {
    final ArrayList<ProcessHandler> handlersToStop = new ArrayList<ProcessHandler>();
    final ArrayList<Pair<Target, Parameters>> keysToRemove = new ArrayList<Pair<Target, Parameters>>();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> pair : myProcMap.keySet()) {
        if (pair.first == target && (configuration == null || pair.second == configuration)) {
          final Object o = myProcMap.get(pair);
          final ProcessHandler handler = o instanceof PendingInfo ? ((PendingInfo)o).handler : o instanceof Info ? ((Info)o).handler : null;
          if (handler != null) {
            handlersToStop.add(handler);
            keysToRemove.add(pair);
          }
          else {
            // todo what to do???
          }
        }
      }
      myProcMap.keySet().removeAll(keysToRemove);
    }
    for (ProcessHandler handler : handlersToStop) {
      handler.destroyProcess();
    }
    fireModificationCountChanged();
  }

  private void startProcess(Target target, Parameters configuration, Pair<Target, Parameters> key) {
    final ProgramRunner runner = new DefaultProgramRunner() {
      @NotNull
      public String getRunnerId() {
        return "MyRunner";
      }

      public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return true;
      }
    };
    try {
      final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      final RunProfileState state = getRunProfileState(target, configuration, executor);
      final ExecutionResult result = state.execute(executor, runner);
      final ProcessHandler processHandler = result.getProcessHandler();
      processHandler.addProcessListener(getProcessListener(key));
      processHandler.startNotify();
    }
    catch (ExecutionException e) {
      handleProcessTerminated(key, e.getMessage());
    }
  }

  protected abstract RunProfileState getRunProfileState(Target target, Parameters configuration, Executor executor)
    throws ExecutionException;

  private boolean getExistingInfo(Ref<Info> ref, final Pair<Target, Parameters> key) {
    Object info;
    synchronized (myProcMap) {
      info = myProcMap.get(key);
      try {
        while (info != null &&
               (!(info instanceof Info) || ((Info)info).handler.isProcessTerminating() || ((Info)info).handler.isProcessTerminated())) {
          myProcMap.wait(1000);
          ProgressManager.checkCanceled();
          info = myProcMap.get(key);
        }
      }
      catch (InterruptedException e) {
        ProgressManager.checkCanceled();
      }
      if (info == null) {
        myProcMap.put(key, new PendingInfo(ref, null));
      }
    }
    if (info != null) {
      if (info instanceof Info) {
        synchronized (ref) {
          ref.set((Info)info);
          ref.notifyAll();
        }
      }
      return true;
    }
    return false;
  }

  private EntryPoint acquire(final Info port) throws Exception {
    final EntryPoint result = RemoteUtil.executeWithClassLoader(new ThrowableComputable<EntryPoint, Exception>() {
      public EntryPoint compute() throws Exception {
        final Registry registry = LocateRegistry.getRegistry(port.port);
        final Remote remote = registry.lookup(port.name);
        if (Remote.class.isAssignableFrom(myValueClass)) {
          return RemoteUtil.substituteClassLoader(narrowImpl(remote, myValueClass), myValueClass.getClassLoader());
        }
        else {
          return RemoteUtil.castToLocal(remote, myValueClass);
        }
      }
    }, getClass().getClassLoader()); // should be the loader of client plugin
    // init hard ref that will keep it from DGC and thus preventing from System.exit
    port.entryPointHardRef = result;
    return result;
  }

  private static <T> T narrowImpl(Remote remote, Class<T> to) {
    return (T)(to.isInstance(remote) ? remote : PortableRemoteObject.narrow(remote, to));
  }

  private ProcessListener getProcessListener(final Pair<Target, Parameters> key) {
    return new ProcessListener() {
      public void startNotified(ProcessEvent event) {
        final ProcessHandler processHandler = event.getProcessHandler();
        processHandler.putUserData(ProcessHandler.SILENTLY_DESTROY_ON_CLOSE, Boolean.TRUE);
        final Object o;
        synchronized (myProcMap) {
          o = myProcMap.get(key);
          if (o instanceof PendingInfo) {
            myProcMap.put(key, new PendingInfo(((PendingInfo)o).ref, processHandler));
          }
        }
      }

      public void processTerminated(ProcessEvent event) {
        handleProcessTerminated(key, null);
        fireModificationCountChanged();
      }

      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
      }

      public void onTextAvailable(ProcessEvent event, Key outputType) {
        final String text = StringUtil.notNullize(event.getText());
        if (outputType == ProcessOutputTypes.STDERR) {
          LOG.warn(text.trim());
        }
        else {
          LOG.info(text.trim());
        }

        Info result = null;
        final PendingInfo info;
        synchronized (myProcMap) {
          final Object o = myProcMap.get(key);
          logText(key.second, event, outputType, o);
          if (o instanceof PendingInfo) {
            info = (PendingInfo)o;
            if (outputType == ProcessOutputTypes.STDOUT) {
              final String prefix = "Port/ID:";
              if (text != null && text.startsWith(prefix)) {
                final String pair = text.substring(prefix.length()).trim();
                final int idx = pair.indexOf("/");
                result = new Info(info.handler, Integer.parseInt(pair.substring(0, idx)), pair.substring(idx + 1));
                myProcMap.put(key, result);
                myProcMap.notifyAll();
              }
            }
            else if (outputType == ProcessOutputTypes.STDERR) {
              info.stderr.append(text);
            }
          }
          else {
            info = null;
          }
        }
        if (result != null) {
          synchronized (info.ref) {
            info.ref.set(result);
            info.ref.notifyAll();
          }
          fireModificationCountChanged();
        }
      }
    };
  }

  private void handleProcessTerminated(Pair<Target, Parameters> key, String errorMessage) {
    Object o;
    final PendingInfo pendingInfo;
    synchronized (myProcMap) {
      o = myProcMap.remove(key);
      pendingInfo = o instanceof PendingInfo ? (PendingInfo)o : null;
      if (pendingInfo != null && (pendingInfo.stderr.length() > 0 || pendingInfo.ref.isNull())) {
        if (errorMessage != null) pendingInfo.stderr.append(errorMessage);
        pendingInfo.ref.set(new Info(null, -1, pendingInfo.stderr.toString()));
      }
      myProcMap.notifyAll();
    }
    if (pendingInfo != null) {
      synchronized (pendingInfo.ref) {
        pendingInfo.ref.notifyAll();
      }
    }
  }

  private static class PendingInfo {
    final Ref<Info> ref;
    final ProcessHandler handler;
    final StringBuilder stderr = new StringBuilder();

    private PendingInfo(Ref<Info> ref, ProcessHandler handler) {
      this.ref = ref;
      this.handler = handler;
    }
  }

  private static class Info {
    final ProcessHandler handler;
    final int port;
    final String name;
    Object entryPointHardRef;

    private Info(ProcessHandler handler, int port, String name) {
      this.handler = handler;
      this.port = port;
      this.name = name;
    }
  }

}
