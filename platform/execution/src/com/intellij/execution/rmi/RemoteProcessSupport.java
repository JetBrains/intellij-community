// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rmi;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Gregory.Shrago
 */
public abstract class RemoteProcessSupport<Target, EntryPoint, Parameters> {
  public static final Logger LOG = Logger.getInstance(RemoteProcessSupport.class);

  private final Class<EntryPoint> myValueClass;
  private final AtomicReference<Heartbeat> myHeartbeatRef = new AtomicReference<>();

  private final AtomicReference<Future> myShutdownFuture = new AtomicReference<>();
  private final Map<Pair<Target, Parameters>, Info> myProcMap = new HashMap<>();
  private final Map<Pair<Target, Parameters>, InProcessInfo<EntryPoint>> myInProcMap = new HashMap<>();

  static {
    RemoteServer.setupRMI(true);
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
    CompletableFuture<?> finishFuture = new CompletableFuture<>();
    if (myShutdownFuture.compareAndSet(null, finishFuture)) {
      final List<Info> infos = new ArrayList<>();
      synchronized (myProcMap) {
        for (Info o : myProcMap.values()) {
          if (o.handler != null) infos.add(o);
        }
      }
      if (infos.isEmpty()) {
        finishFuture.complete(null);
        return;
      }
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          destroyProcessesImpl(infos);
          if (wait) {
            for (Info o : infos) {
              o.handler.waitFor();
            }
          }
          finishFuture.complete(null);
        }
        catch (Throwable e) {
          finishFuture.completeExceptionally(e);
        }
      });
    }

    if (wait) {
      try {
        myShutdownFuture.get().get();
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
    if (RemoteObject.IN_PROCESS) {
      synchronized (myInProcMap) {
        for (Pair<Target, Parameters> pair : myInProcMap.keySet()) {
          if (pair.first == target) {
            result.add(pair.second);
          }
        }
      }
    }
    return result;
  }

  public Set<Pair<Target, Parameters>> getActiveConfigurations() {
    HashSet<Pair<Target, Parameters>> configurations;
    synchronized (myProcMap) {
      configurations = new HashSet<>(myProcMap.keySet());
    }
    if (RemoteObject.IN_PROCESS) {
      synchronized (myInProcMap) {
        configurations.addAll(myInProcMap.keySet());
      }
    }
    return configurations;
  }

  /**
   * @deprecated use acquire(Target, Parameters, ProgressIndicator)
   */
  @ApiStatus.Internal
  @Deprecated
  public EntryPoint acquire(@NotNull Target target, @NotNull Parameters configuration) throws Exception {
    return acquire(target, configuration, null);
  }

  @RequiresBackgroundThread
  public EntryPoint acquire(@NotNull Target target, @NotNull Parameters configuration, @Nullable ProgressIndicator indicator)
    throws Exception {

    EntryPoint inProcess = acquireInProcess(target, configuration);
    if (inProcess != null) return inProcess;

    Ref<RunningInfo> ref = Ref.create(null);
    Pair<Target, Parameters> key = Pair.create(target, configuration);
    boolean created = false;
    if (!getExistingInfo(ref, key)) {
      startProcess(target, configuration, key);
      if (ref.isNull()) {
        try {
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
          synchronized (ref) {
            while (ref.isNull()) {
              ref.wait(1000);
              checkIndicator(indicator);
            }
          }
        }
        catch (InterruptedException e) {
          checkIndicator(indicator);
        }
      }
      created = true;
    }
    RunningInfo info = ref.get();
    if (info instanceof FailedInfo o) {
      String message = o.cause != null && StringUtil.isEmptyOrSpaces(o.stderr) ? o.cause.getMessage() : o.stderr;
      throw new ExecutionException(message, o.cause);
    }
    else if (info == null || info.handler == null) {
      throw new ExecutionException(ExecutionBundle.message("dialog.remote.process.unable.to.acquire.remote.proxy.for", getName(target)));
    }
    EntryPoint result = acquire(info);
    if (created) {
      onCreated(target, configuration, result);
    }
    return result;
  }

  protected void onCreated(@NotNull Target target, @NotNull Parameters configuration, @NotNull EntryPoint result) {
  }

  private static void checkIndicator(@Nullable ProgressIndicator indicator) {
    if (indicator != null) {
      indicator.checkCanceled();
    }
    else {
      ProgressManager.checkCanceled();
    }
  }

  protected int publishPort(int port) {
    return port;
  }

  @NotNull
  public Future<?> release(@NotNull Target target, @Nullable Parameters configuration) {
    List<Info> infos = new ArrayList<>();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> key : myProcMap.keySet()) {
        if (key.first == target && (configuration == null || key.second == configuration)) {
          Info o = myProcMap.get(key);
          if (o.handler != null) infos.add(o);
        }
      }
    }
    if (RemoteObject.IN_PROCESS) {
      synchronized (myInProcMap) {
        for (Iterator<Pair<Target, Parameters>> it = myInProcMap.keySet().iterator(); it.hasNext(); ) {
          Pair<Target, Parameters> key = it.next();
          if (key.first == target && (configuration == null || key.second == configuration)) {
            it.remove();
          }
        }
      }
    }
    return infos.isEmpty() ? CompletableFuture.completedFuture(null) : ApplicationManager.getApplication().executeOnPooledThread(() -> {
      destroyProcessesImpl(infos);
      fireModificationCountChanged();
      for (Info o : infos) {
        o.handler.waitFor();
      }
    });
  }

  private static void destroyProcessesImpl(@NotNull List<? extends Info> infos) {
    for (Info o : infos) {
      LOG.info("Terminating: " + o);
      o.handler.destroyProcess();
    }
  }

  private void startProcess(@NotNull Target target, @NotNull Parameters configuration, @NotNull Pair<Target, Parameters> key) {
    ProgramRunner<?> runner = new ProgramRunner<>() {
      @Override
      @NotNull
      public String getRunnerId() {
        return "MyRunner";
      }

      @Override
      public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
        ExecutionManager.getInstance(environment.getProject()).startRunProfile(environment, state -> {
          return DefaultProgramRunnerKt.executeState(state, environment, this);
        });
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

  protected abstract RunProfileState getRunProfileState(@NotNull Target target,
                                                        @NotNull Parameters configuration,
                                                        @NotNull Executor executor)
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
        LOG.info("Starring remote process. Existing info not found, creating PendingInfo:" + key);
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

  private EntryPoint acquire(final RunningInfo info) throws Exception {
    EntryPoint result = RemoteUtil.executeWithClassLoader(() -> {
      Registry registry = LocateRegistry.getRegistry(info.host, info.port, getClientSocketFactory());
      Remote remote = Objects.requireNonNull(registry.lookup(info.name));

      if (myValueClass.isInstance(remote)) {
        EntryPoint entryPoint = myValueClass.cast(remote);
        return RemoteUtil.substituteClassLoader(entryPoint, myValueClass.getClassLoader());
      }
      else {
        return RemoteUtil.castToLocal(remote, myValueClass);
      }
    }, getClass().getClassLoader()); // should be the loader of client plugin
    // init hard ref that will keep it from DGC and thus preventing from System.exit
    info.entryPointHardRef = result;
    return result;
  }

  /**
   * Override this method to use custom client socket factory.
   * <p>
   * Default implementation returns null and uses {@link RMISocketFactory#getSocketFactory()}
   *
   * @return client socket factory to be used by this remote process support.
   */
  protected RMIClientSocketFactory getClientSocketFactory() {
    return null;
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
            LOG.info("Staring remote process, startNotified received, creating PendingInfo: " + key);
            myProcMap.put(key, new PendingInfo(((PendingInfo)o).ref, processHandler));
          }
        }
        sendDataAfterStart(processHandler);
      }

      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        LOG.info("Remote process terminated with code: " + event.getExitCode() + " message = " + event.getText());
        if (dropProcessInfo(key, null, event.getProcessHandler())) {
          fireModificationCountChanged();
        }
        onProcessTerminated(event);
      }

      @Override
      public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
        LOG.info("Remote process will terminate: " + event.getText() + " message = " + event.getText());
        if (dropProcessInfo(key, null, event.getProcessHandler())) {
          fireModificationCountChanged();
        }
        Heartbeat heartbeat = myHeartbeatRef.get();
        if (heartbeat != null) {
          heartbeat.stopBeat();
        }
      }

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {

        if (LOG.isDebugEnabled()) {
          String out = "";
          if (outputType == ProcessOutputTypes.STDOUT) {
            out = "stdout";
          }
          if (outputType == ProcessOutputTypes.STDERR) {
            out = "stderr";
          }
          if (outputType == ProcessOutputTypes.SYSTEM) {
            out = "system";
          }
          LOG.debug("Remote process " + out + ":" + event.getText());
        }

        String text = StringUtil.notNullize(event.getText());
        logText(key.second, event, outputType);
        RunningInfo result = null;
        PendingInfo info;
        synchronized (myProcMap) {
          Info o = myProcMap.get(key);
          if (o instanceof PendingInfo) {
            info = (PendingInfo)o;
            if (outputType == ProcessOutputTypes.STDOUT) {
              String prefix = "Port/ServicesPort/ID:";
              if (text.startsWith(prefix)) {
                List<String> data = StringUtil.split(text.substring(prefix.length()).trim(), "/");
                int port = Integer.parseInt(data.get(0));
                int servicesPort = Integer.parseInt(data.get(1));
                String id = data.get(2);
                String host = getRemoteHost();
                LOG.info("Started remote process on: " + host + ":" + port + "with service port " + servicesPort + " and id = " + id);
                result = new RunningInfo(info.handler, host, publishPort(port), id, publishPort(servicesPort));
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
            Heartbeat heartbeat = new Heartbeat(result.host, result.port, getClientSocketFactory());
            heartbeat.startBeat();
            myHeartbeatRef.set(heartbeat);
          }
          catch (Throwable e) {
            LOG.warn("The cook failed to start due to " + ExceptionUtil.getRootCause(e));
          }
        }
      }
    };
  }

  protected void onProcessTerminated(ProcessEvent event) { }

  protected void sendDataAfterStart(ProcessHandler handler) { }

  protected String getRemoteHost() {
    return ObjectUtils.notNull(System.getProperty(RemoteServer.SERVER_HOSTNAME), "127.0.0.1");
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
    if (info instanceof PendingInfo pendingInfo) {
      LOG.warn("Dropping process info for pending process: stder = " + pendingInfo.stderr, error);

      if (error != null || !pendingInfo.stderr.isEmpty() || pendingInfo.ref.isNull()) {
        pendingInfo.ref.set(new FailedInfo(error, pendingInfo.stderr.toString()));
      }
      synchronized (pendingInfo.ref) {
        pendingInfo.ref.notifyAll();
      }
    }
    return info != null;
  }

  @Nullable
  private EntryPoint acquireInProcess(@NotNull Target target, @NotNull Parameters configuration) throws Exception {
    if (!RemoteObject.IN_PROCESS) return null;
    Pair<Target, Parameters> key = Pair.create(target, configuration);
    InProcessInfo<EntryPoint> info;
    boolean created = false;
    synchronized (myInProcMap) {
      info = myInProcMap.get(key);
      if (info == null) {
        LOG.info("Running remote service in process: " + key);
        info = new InProcessInfo<>(acquireInProcessFactory(target, configuration));
        myInProcMap.put(key, info);
        created = true;
      }
    }
    EntryPoint result = info.factory.compute();
    if (created) {
      onCreated(target, configuration, result);
    }
    return result;
  }

  @NotNull
  protected ThrowableComputable<@Nullable EntryPoint, Exception> acquireInProcessFactory(Target target, Parameters configuration)
    throws Exception {
    return () -> null;
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
    final String host;
    final int port;
    final String name;
    final int servicesPort; // port number when was exported with RemoteServer.start(knownPort=true), 0 otherwise
    Object entryPointHardRef;

    RunningInfo(ProcessHandler handler, String host, int port, String name) {
      this(handler, host, port, name, 0);
    }

    RunningInfo(ProcessHandler handler, String host, int port, String name, int servicesPort) {
      super(handler);
      this.host = host;
      this.port = port;
      this.name = name;
      this.servicesPort = servicesPort;
    }

    @Override
    public String toString() {
      return host + ":" + port + "/" + name;
    }
  }

  private static class FailedInfo extends RunningInfo {
    final Throwable cause;
    final @NlsSafe String stderr;

    FailedInfo(Throwable cause, String stderr) {
      super(null, null, -1, null);
      this.cause = cause;
      this.stderr = stderr;
    }

    @Override
    public String toString() {
      return "FailedInfo{" + cause + '}';
    }
  }

  private static class InProcessInfo<EntryPoint> extends Info {

    final ThrowableComputable<EntryPoint, Exception> factory;

    InProcessInfo(ThrowableComputable<EntryPoint, Exception> factory) {
      super(null);
      this.factory = factory;
    }

    @Override
    public String toString() {
      return "InProcessInfo{" + Integer.toHexString(hashCode()) + '}';
    }
  }

  public static class Heartbeat {
    private final Registry myRegistry;
    private boolean live = true;
    private ScheduledFuture<?> myFuture = null;

    Heartbeat(String host, int port, RMIClientSocketFactory clientSocketFactory) throws RemoteException {
      myRegistry = LocateRegistry.getRegistry(host, port, clientSocketFactory);
    }

    void stopBeat() {
      if (myFuture != null) {
        myFuture.cancel(false);
        myFuture = null;
      }
    }

    void startBeat() throws RemoteException, NotBoundException {
      var watchdog = getWatchdog();
      var pulseTimeoutMillis = watchdog.getPulseTimeoutMillis();
      myFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
        beat();
      }, pulseTimeoutMillis, pulseTimeoutMillis, TimeUnit.MILLISECONDS);
      //noinspection TestOnlyProblems
      Job contextJob = Cancellation.currentJob();
      if (contextJob != null) {
        // The spawned process is bound to the container that invoked it
        // Using Application as a default container is a bad guess, as it does not allow
        // proper disposal of the closing of the actual container
        // which may lead to project leaks, in this particular case.
        contextJob.invokeOnCompletion((__) -> {
          stopBeat();
          return null;
        });
      }
      else {
        Disposer.register(ApplicationManager.getApplication(), () -> stopBeat());
      }
    }

    public boolean beat() {
      try {
        if (live) {
          IdeaWatchdog watchdog = getWatchdog();
          return watchdog.ping();
        }
      }
      catch (Exception ignore) {
        live = false;
        stopBeat();
      }
      catch (Throwable t) {
        live = false;
        stopBeat();
        LOG.error(t);
      }
      return false;
    }

    @TestOnly
    public void kill(int exitCode) {
      try {
        getWatchdog().dieNowTestOnly(exitCode);
      }
      catch (RemoteException | NotBoundException ignore) {
      }
    }

    private IdeaWatchdog getWatchdog() throws RemoteException, NotBoundException {
      Remote remote = myRegistry.lookup(IdeaWatchdog.BINDING_NAME);
      if (remote instanceof IdeaWatchdog) {
        return (IdeaWatchdog)remote;
      }
      else {
        return RemoteUtil.castToLocal(remote, IdeaWatchdog.class);
      }
    }
  }
}
