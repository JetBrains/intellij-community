package com.intellij.openapi.externalSystem.service;

import com.intellij.execution.rmi.RemoteServer;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.remote.*;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 12:51 PM
 */
public class ExternalSystemFacadeImpl<S extends ExternalSystemExecutionSettings> extends RemoteServer
  implements RemoteExternalSystemFacade<S>
{

  private static final long DEFAULT_REMOTE_GRADLE_PROCESS_TTL_IN_MS = TimeUnit.MILLISECONDS.convert(3, TimeUnit.MINUTES);

  private final ConcurrentMap<Class<?>, RemoteExternalSystemService<S>> myRemotes = ContainerUtil.newConcurrentMap();

  private final AtomicReference<S> mySettings              = new AtomicReference<S>();
  private final AtomicLong         myTtlMs                 = new AtomicLong(DEFAULT_REMOTE_GRADLE_PROCESS_TTL_IN_MS);
  private final Alarm              myShutdownAlarm         = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private final AtomicInteger      myCallsInProgressNumber = new AtomicInteger();

  private final AtomicReference<ExternalSystemTaskNotificationListener> myNotificationListener =
    new AtomicReference<ExternalSystemTaskNotificationListener>(new ExternalSystemTaskNotificationListenerAdapter() {});

  @NotNull private final RemoteExternalSystemProjectResolverImpl<S> myProjectResolver;
  @NotNull private final RemoteExternalSystemTaskManagerImpl<S>     myTaskManager;
  private volatile boolean myStdOutputConfigured;

  public ExternalSystemFacadeImpl(@NotNull Class<ExternalSystemProjectResolver<S>> projectResolverClass,
                                  @NotNull Class<ExternalSystemTaskManager<S>> buildManagerClass)
    throws IllegalAccessException, InstantiationException
  {
    myProjectResolver = new RemoteExternalSystemProjectResolverImpl<S>(projectResolverClass.newInstance());
    myTaskManager = new RemoteExternalSystemTaskManagerImpl<S>(buildManagerClass.newInstance());
    updateAutoShutdownTime();
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      throw new IllegalArgumentException(
        "Can't create external system facade. Reason: given arguments don't contain information about external system resolver to use");
    }
    final Class<ExternalSystemProjectResolver<?>> resolverClass = (Class<ExternalSystemProjectResolver<?>>)Class.forName(args[0]);
    if (!ExternalSystemProjectResolver.class.isAssignableFrom(resolverClass)) {
      throw new IllegalArgumentException(String.format(
        "Can't create external system facade. Reason: given external system resolver class (%s) must be IS-A '%s'",
        resolverClass,
        ExternalSystemProjectResolver.class));
    }

    if (args.length < 2) {
      throw new IllegalArgumentException(
        "Can't create external system facade. Reason: given arguments don't contain information about external system build manager to use"
      );
    }
    final Class<ExternalSystemTaskManager<?>> buildManagerClass = (Class<ExternalSystemTaskManager<?>>)Class.forName(args[1]);
    if (!ExternalSystemProjectResolver.class.isAssignableFrom(resolverClass)) {
      throw new IllegalArgumentException(String.format(
        "Can't create external system facade. Reason: given external system build manager (%s) must be IS-A '%s'",
        buildManagerClass, ExternalSystemTaskManager.class
      ));
    }
    
    ExternalSystemFacadeImpl facade = new ExternalSystemFacadeImpl(resolverClass, buildManagerClass);
    facade.init();
    start(facade);
  }

  private void init() throws RemoteException {
    applyProgressManager(RemoteExternalSystemProgressNotificationManager.NULL_OBJECT);
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public RemoteExternalSystemProjectResolver<S> getResolver() throws RemoteException, IllegalStateException {
    try {
      return getRemote(RemoteExternalSystemProjectResolver.class, myProjectResolver);
    }
    catch (Exception e) {
      throw new IllegalStateException(String.format("Can't create '%s' service", RemoteExternalSystemProjectResolverImpl.class.getName()),
                                      e);
    }
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public RemoteExternalSystemTaskManager<S> getTaskManager() throws RemoteException {
    try {
      return getRemote(RemoteExternalSystemTaskManager.class, myTaskManager);
    }
    catch (Exception e) {
      throw new IllegalStateException(String.format("Can't create '%s' service", ExternalSystemTaskManager.class.getName()), e);
    }
  }

  /**
   * Generic method to retrieve exposed implementations of the target interface.
   * <p/>
   * Uses cached value if it's found; creates new and caches it otherwise.
   *
   * @param interfaceClass  target service interface class
   * @param impl            service implementation
   * @param <I>             service interface class
   * @param <C>             service implementation
   * @return implementation of the target service
   * @throws IllegalAccessException   in case of incorrect assumptions about server class interface
   * @throws InstantiationException   in case of incorrect assumptions about server class interface
   * @throws ClassNotFoundException   in case of incorrect assumptions about server class interface
   * @throws RemoteException
   */
  @SuppressWarnings({"unchecked", "IOResourceOpenedButNotSafelyClosed", "UseOfSystemOutOrSystemErr"})
  private <I extends RemoteExternalSystemService<S>, C extends I> I getRemote(@NotNull Class<I> interfaceClass,
                                                                              @NotNull final C impl)
    throws ClassNotFoundException, IllegalAccessException, InstantiationException, RemoteException
  {
    Object cachedResult = myRemotes.get(interfaceClass);
    if (cachedResult != null) {
      return (I)cachedResult;
    }

    if (!myStdOutputConfigured) {
      myStdOutputConfigured = true;
      System.setOut(new LineAwarePrintStream(System.out));
      System.setErr(new LineAwarePrintStream(System.err));
    }
    
    S settings = mySettings.get();
    if (settings != null) {
      impl.setNotificationListener(myNotificationListener.get());
      impl.setSettings(settings);
    }
    impl.setNotificationListener(myNotificationListener.get());
    I proxy = (I)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { interfaceClass }, new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        myCallsInProgressNumber.incrementAndGet();
        try {
          return method.invoke(impl, args);
        }
        finally {
          myCallsInProgressNumber.decrementAndGet();
          updateAutoShutdownTime();
        }
      }
    });
    try {
      I stub = (I)UnicastRemoteObject.exportObject(proxy, 0);
      I stored = (I)myRemotes.putIfAbsent(interfaceClass, stub);
      return stored == null ? stub : stored;
    }
    catch (RemoteException e) {
      Object raceResult = myRemotes.get(interfaceClass);
      if (raceResult != null) {
        // Race condition occurred
        return (I)raceResult;
      }
      else {
        throw new IllegalStateException(
          String.format("Can't prepare remote service for interface '%s', implementation '%s'", interfaceClass, impl),
          e
        );
      }
    }
  }

  @Override
  public boolean isTaskInProgress(@NotNull ExternalSystemTaskId id) throws RemoteException {
    for (RemoteExternalSystemService service : myRemotes.values()) {
      if (service.isTaskInProgress(id)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> getTasksInProgress() throws RemoteException {
    Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> result = null;
    for (RemoteExternalSystemService service : myRemotes.values()) {
      final Map<ExternalSystemTaskType, Set<ExternalSystemTaskId>> tasks = service.getTasksInProgress();
      if (tasks.isEmpty()) {
        continue;
      }
      if (result == null) {
        result = new HashMap<ExternalSystemTaskType, Set<ExternalSystemTaskId>>();
      }
      for (Map.Entry<ExternalSystemTaskType, Set<ExternalSystemTaskId>> entry : tasks.entrySet()) {
        Set<ExternalSystemTaskId> ids = result.get(entry.getKey());
        if (ids == null) {
          result.put(entry.getKey(), ids = new HashSet<ExternalSystemTaskId>());
        }
        ids.addAll(entry.getValue());
      }
    }
    if (result == null) {
      result = Collections.emptyMap();
    }
    return result;
  }

  @Override
  public void applySettings(@NotNull S settings) throws RemoteException {
    mySettings.set(settings);
    long ttl = settings.getRemoteProcessIdleTtlInMs();
    if (ttl > 0) {
      myTtlMs.set(ttl);
    }
    List<RemoteExternalSystemService<S>> services = ContainerUtilRt.newArrayList(myRemotes.values());
    for (RemoteExternalSystemService<S> service : services) {
      service.setSettings(settings);
    }
  }

  @Override
  public void applyProgressManager(@NotNull RemoteExternalSystemProgressNotificationManager progressManager) throws RemoteException {
    ExternalSystemTaskNotificationListener listener = new SwallowingNotificationListener(progressManager);
    myNotificationListener.set(listener);
    myProjectResolver.setNotificationListener(listener);
    myTaskManager.setNotificationListener(listener);
  }
  
  /**
   * Schedules automatic process termination in {@code #REMOTE_GRADLE_PROCESS_TTL_IN_MS} milliseconds.
   * <p/>
   * Rationale: it's possible that IJ user performs gradle related activity (e.g. import from gradle) when the works purely
   * at IJ. We don't want to keep remote process that communicates with the gradle api then.
   */
  private void updateAutoShutdownTime() {
    myShutdownAlarm.cancelAllRequests();
    myShutdownAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myCallsInProgressNumber.get() > 0) {
          updateAutoShutdownTime();
          return;
        }
        System.exit(0);
      }
    }, (int)myTtlMs.get());
  }
  
  private static class SwallowingNotificationListener implements ExternalSystemTaskNotificationListener {

    @NotNull private final RemoteExternalSystemProgressNotificationManager myManager;

    SwallowingNotificationListener(@NotNull RemoteExternalSystemProgressNotificationManager manager) {
      myManager = manager;
    }

    @Override
    public void onQueued(@NotNull ExternalSystemTaskId id) {
    }

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id) {
      try {
        myManager.onStart(id);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
      try {
        myManager.onStatusChange(event);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
      try {
        myManager.onTaskOutput(id, text, stdOut);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {
      try {
        myManager.onEnd(id);
      }
      catch (RemoteException e) {
        // Ignore
      }
    }
  }
  
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static class LineAwarePrintStream extends PrintStream {
    private LineAwarePrintStream(@NotNull final PrintStream delegate) {
      super(new OutputStream() {

        @NotNull private final StringBuilder myBuffer = new StringBuilder();
        
        @Override
        public void write(int b) throws IOException {
          char c = (char)b;
          myBuffer.append(Character.toString(c));
          if (c == '\n') {
            doFlush();
          }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
          int start = off;
          int maxOffset = off + len;
          for (int i = off; i < maxOffset; i++) {
            if (b[i] == '\n') {
              myBuffer.append(new String(b, start, i - start + 1));
              doFlush();
              start = i + 1;
            }
          }

          if (start < maxOffset) {
            myBuffer.append(new String(b, start, maxOffset - start));
          }
        }
        
        private void doFlush() {
          delegate.print(myBuffer.toString());
          delegate.flush();
          myBuffer.setLength(0);
        }
      });
    }
  }
}
