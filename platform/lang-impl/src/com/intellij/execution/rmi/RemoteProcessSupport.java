/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.FixedFuture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.rmi.PortableRemoteObject;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * @author Gregory.Shrago
 */
public abstract class RemoteProcessSupport<Target, EntryPoint, Parameters> {
  public static final Logger LOG = Logger.getInstance(RemoteProcessSupport.class);

  private final Class<EntryPoint> myValueClass;
  private final HashMap<Pair<Target, Parameters>, Info> myProcMap = new HashMap<>();

  static {
    RemoteServer.setupRMI();
  }

  public RemoteProcessSupport(@NotNull Class<EntryPoint> valueClass) {
    myValueClass = valueClass;
  }

  protected abstract void fireModificationCountChanged();

  protected abstract String getName(@NotNull Target target);

  protected void logText(@NotNull Parameters configuration, @NotNull ProcessEvent event, @NotNull Key outputType) {
    String text = StringUtil.notNullize(event.getText());
    if (outputType == ProcessOutputTypes.STDERR) {
      LOG.warn(text.trim());
    }
    else {
      LOG.debug(text.trim());
    }
  }

  public void stopAll() {
    stopAll(false);
  }

  public void stopAll(final boolean wait) {
    final List<Info> infos = ContainerUtil.newArrayList();
    synchronized (myProcMap) {
      for (Info o : myProcMap.values()) {
        if (o.handler != null) infos.add(o);
      }
    }
    if (infos.isEmpty()) return;
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      destroyProcessesImpl(infos);
      if (wait) {
        for (Info o : infos) {
          o.handler.waitFor();
        }
      }
    });
    if (wait) {
      try {
        future.get();
      }
      catch (InterruptedException ignored) {
      }
      catch (java.util.concurrent.ExecutionException e) {
        LOG.warn(e);
      }
    }
  }

  public List<Parameters> getActiveConfigurations(@NotNull Target target) {
    ArrayList<Parameters> result = new ArrayList<>();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> pair : myProcMap.keySet()) {
        if (pair.first == target) {
          result.add(pair.second);
        }
      }
    }
    return result;
  }

  public Set<Pair<Target, Parameters>> getActiveConfigurations() {
    synchronized (myProcMap) {
      return new HashSet<>(myProcMap.keySet());
    }
  }

  public EntryPoint acquire(@NotNull Target target, @NotNull Parameters configuration) throws Exception {
    ApplicationManagerEx.getApplicationEx().assertTimeConsuming();

    Ref<RunningInfo> ref = Ref.create(null);
    Pair<Target, Parameters> key = Pair.create(target, configuration);
    if (!getExistingInfo(ref, key)) {
      startProcess(target, configuration, key);
      if (ref.isNull()) {
        try {
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
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
    RunningInfo info = ref.get();
    if (info.handler == null) {
      String message = info instanceof FailedInfo ? ((FailedInfo)info).stderr : null;
      Throwable cause = info instanceof FailedInfo ? ((FailedInfo)info).cause : null;
      throw new ExecutionException(message, cause);
    }
    return acquire(info);
  }

  @NotNull
  public Future<?> release(@NotNull Target target, @Nullable Parameters configuration) {
    List<Info> infos = ContainerUtil.newArrayList();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> key : myProcMap.keySet()) {
        if (key.first == target && (configuration == null || key.second == configuration)) {
          Info o = myProcMap.get(key);
          if (o.handler != null) infos.add(o);
        }
      }
    }
    if (infos.isEmpty()) return new FixedFuture<>(null);
    return ApplicationManager.getApplication().executeOnPooledThread(() -> {
        destroyProcessesImpl(infos);
        fireModificationCountChanged();
        for (Info o : infos) {
          o.handler.waitFor();
        }
    });
  }

  private static void destroyProcessesImpl(@NotNull List<Info> infos) {
    for (Info o : infos) {
      LOG.info("Terminating: " + o);
      o.handler.destroyProcess();
    }
  }

  private void startProcess(@NotNull Target target, @NotNull Parameters configuration, @NotNull Pair<Target, Parameters> key) {
    ProgramRunner runner = new DefaultProgramRunner() {
      @Override
      @NotNull
      public String getRunnerId() {
        return "MyRunner";
      }

      @Override
      public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return true;
      }
    };
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ProcessHandler processHandler;
    try {
      RunProfileState state = getRunProfileState(target, configuration, executor);
      ExecutionResult result = state.execute(executor, runner);
      //noinspection ConstantConditions
      processHandler = result.getProcessHandler();
    }
    catch (Throwable e) {
      dropProcessInfo(key, e, null);
      return;
    }
    processHandler.addProcessListener(getProcessListener(key));
    processHandler.startNotify();
  }

  protected abstract RunProfileState getRunProfileState(@NotNull Target target, @NotNull Parameters configuration, @NotNull Executor executor)
    throws ExecutionException;

  private boolean getExistingInfo(@NotNull Ref<RunningInfo> ref, @NotNull Pair<Target, Parameters> key) {
    Info info;
    synchronized (myProcMap) {
      info = myProcMap.get(key);
      try {
        while (info != null && (!(info instanceof RunningInfo) ||
                                info.handler.isProcessTerminating() ||
                                info.handler.isProcessTerminated())) {
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
    if (info instanceof RunningInfo) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (ref) {
        ref.set((RunningInfo)info);
        ref.notifyAll();
      }
    }
    return info != null;
  }

  private EntryPoint acquire(final RunningInfo port) throws Exception {
    EntryPoint result = RemoteUtil.executeWithClassLoader(() -> {
      Registry registry = LocateRegistry.getRegistry("localhost", port.port);
      Remote remote = ObjectUtils.assertNotNull(registry.lookup(port.name));

      if (Remote.class.isAssignableFrom(myValueClass)) {
        EntryPoint entryPoint = narrowImpl(remote, myValueClass);
        if (entryPoint == null) return null;
        return RemoteUtil.substituteClassLoader(entryPoint, myValueClass.getClassLoader());
      }
      else {
        return RemoteUtil.castToLocal(remote, myValueClass);
      }
    }, getClass().getClassLoader()); // should be the loader of client plugin
    // init hard ref that will keep it from DGC and thus preventing from System.exit
    port.entryPointHardRef = result;
    return result;
  }

  @Nullable
  private static <T> T narrowImpl(@Nullable Remote remote, @NotNull Class<T> to) {
    //noinspection unchecked
    return (T)(to.isInstance(remote) ? remote : PortableRemoteObject.narrow(remote, to));
  }

  private ProcessListener getProcessListener(@NotNull final Pair<Target, Parameters> key) {
    return new ProcessListener() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        ProcessHandler processHandler = event.getProcessHandler();
        processHandler.putUserData(ProcessHandler.SILENTLY_DESTROY_ON_CLOSE, Boolean.TRUE);
        Info o;
        synchronized (myProcMap) {
          o = myProcMap.get(key);
          if (o instanceof PendingInfo) {
            myProcMap.put(key, new PendingInfo(((PendingInfo)o).ref, processHandler));
          }
        }
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        if (dropProcessInfo(key, null, event.getProcessHandler())) {
          fireModificationCountChanged();
        }
      }

      @Override
      public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
        if (dropProcessInfo(key, null, event.getProcessHandler())) {
          fireModificationCountChanged();
        }
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        String text = StringUtil.notNullize(event.getText());
        logText(key.second, event, outputType);
        RunningInfo result = null;
        PendingInfo info;
        synchronized (myProcMap) {
          Info o = myProcMap.get(key);
          if (o instanceof PendingInfo) {
            info = (PendingInfo)o;
            if (outputType == ProcessOutputTypes.STDOUT) {
              String prefix = "Port/ID:";
              if (text.startsWith(prefix)) {
                String pair = text.substring(prefix.length()).trim();
                int idx = pair.indexOf("/");
                result = new RunningInfo(info.handler, Integer.parseInt(pair.substring(0, idx)), pair.substring(idx + 1));
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
          try {
            RemoteDeadHand.TwoMinutesTurkish.startCooking("localhost", result.port);
          }
          catch (Throwable e) {
            LOG.warn("The cook failed to start due to " + ExceptionUtil.getRootCause(e));
          }
        }
      }
    };
  }

  private boolean dropProcessInfo(Pair<Target, Parameters> key, @Nullable Throwable error, @Nullable ProcessHandler handler) {
    Info info;
    synchronized (myProcMap) {
      info = myProcMap.get(key);
      if (info != null && (handler == null || info.handler == handler)) {
        myProcMap.remove(key);
        myProcMap.notifyAll();
      }
      else {
        // different processHandler
        info = null;
      }
    }
    if (info instanceof PendingInfo) {
      PendingInfo pendingInfo = (PendingInfo)info;
      if (error != null || pendingInfo.stderr.length() > 0 || pendingInfo.ref.isNull()) {
        pendingInfo.ref.set(new FailedInfo(error, pendingInfo.stderr.toString()));
      }
      synchronized (pendingInfo.ref) {
        pendingInfo.ref.notifyAll();
      }
    }
    return info != null;
  }

  private static class Info {
    final ProcessHandler handler;

    Info(ProcessHandler handler) {
      this.handler = handler;
    }
  }

  private static class PendingInfo extends Info {
    final Ref<RunningInfo> ref;
    final StringBuilder stderr = new StringBuilder();

    PendingInfo(Ref<RunningInfo> ref, ProcessHandler handler) {
      super(handler);
      this.ref = ref;
    }

    @Override
    public String toString() {
      return "PendingInfo{" + ref.get() + '}';
    }
  }

  private static class RunningInfo extends Info {
    final int port;
    final String name;
    Object entryPointHardRef;

    RunningInfo(ProcessHandler handler, int port, String name) {
      super(handler);
      this.port = port;
      this.name = name;
    }

    @Override
    public String toString() {
      return port + "/" + name;
    }
  }

  private static class FailedInfo extends RunningInfo {
    final Throwable cause;
    final String stderr;

    FailedInfo(Throwable cause, String stderr) {
      super(null, -1, null);
      this.cause = cause;
      this.stderr = stderr;
    }

    @Override
    public String toString() {
      return "FailedInfo{" + cause + '}';
    }
  }

}
