// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.Patches;
import com.intellij.ProjectTopics;
import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.debugger.*;
import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.PopFrameAction;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.engine.requests.MethodReturnValueWatcher;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.impl.attach.PidRemoteConnection;
import com.intellij.debugger.jdi.EmptyConnectorArgument;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.breakpoints.*;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.DebuggerClassFilterProvider;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.jetbrains.jdi.ClassLoaderReferenceImpl;
import com.jetbrains.jdi.MethodImpl;
import com.jetbrains.jdi.VirtualMachineManagerImpl;
import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class DebugProcessImpl extends UserDataHolderBase implements DebugProcess {
  private static final Logger LOG = Logger.getInstance(DebugProcessImpl.class);

  private final Project myProject;
  private final RequestManagerImpl myRequestManager;

  private final Deque<VirtualMachineData> myStashedVirtualMachines = new LinkedList<>();

  private volatile VirtualMachineProxyImpl myVirtualMachineProxy = null;
  protected final EventDispatcher<DebugProcessListener> myDebugProcessDispatcher = EventDispatcher.create(DebugProcessListener.class);
  protected final EventDispatcher<EvaluationListener> myEvaluationDispatcher = EventDispatcher.create(EvaluationListener.class);

  private final List<ProcessListener> myProcessListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final StringBuilder myTextBeforeStart = new StringBuilder();

  enum State {INITIAL, ATTACHED, DETACHING, DETACHED}

  protected final AtomicReference<State> myState = new AtomicReference<>(State.INITIAL);

  private volatile ExecutionResult myExecutionResult;
  private volatile RemoteConnection myConnection;
  private JavaDebugProcess myXDebugProcess;

  private volatile Map<String, Connector.Argument> myArguments;

  private final List<NodeRenderer> myRenderers = new ArrayList<>();

  // we use null key here
  private final Map<Type, Object> myNodeRenderersMap = new HashMap<>();

  private final SuspendManagerImpl mySuspendManager = new SuspendManagerImpl(this);
  protected CompoundPositionManager myPositionManager = CompoundPositionManager.EMPTY;
  private volatile @NotNull DebuggerManagerThreadImpl myDebuggerManagerThread;

  private final Semaphore myWaitFor = new Semaphore();
  private final AtomicBoolean myIsFailed = new AtomicBoolean(false);
  private final AtomicBoolean myIsStopped = new AtomicBoolean(false);
  protected DebuggerSession mySession;
  @Nullable protected MethodReturnValueWatcher myReturnValueWatcher;
  protected final Disposable myDisposable = Disposer.newDisposable();
  private final Alarm myStatusUpdateAlarm = new Alarm();

  private final ThreadBlockedMonitor myThreadBlockedMonitor = new ThreadBlockedMonitor(this, myDisposable);

  protected DebugProcessImpl(Project project) {
    myProject = project;
    myDebuggerManagerThread = new DebuggerManagerThreadImpl(myDisposable, myProject);
    myRequestManager = new RequestManagerImpl(this);
    NodeRendererSettings.getInstance().addListener(this::reloadRenderers, myDisposable);
    NodeRenderer.EP_NAME.addChangeListener(this::reloadRenderers, myDisposable);
    CompoundRendererProvider.EP_NAME.addChangeListener(this::reloadRenderers, myDisposable);
    addDebugProcessListener(new DebugProcessAdapterImpl() {
      @Override
      public void processAttached(DebugProcessImpl process) {
        reloadRenderers();
      }
    }, myDisposable);
    myDebugProcessDispatcher.addListener(new DebugProcessListener() {
      @Override
      public void paused(@NotNull SuspendContext suspendContext) {
        myThreadBlockedMonitor.stopWatching(
          suspendContext.getSuspendPolicy() != EventRequest.SUSPEND_ALL ? suspendContext.getThread() : null);
      }
    });
  }

  private void reloadRenderers() {
    getManagerThread().invoke(new DebuggerCommandImpl(PrioritizedTask.Priority.HIGH) {
      @Override
      protected void action() {
        myNodeRenderersMap.clear();
        myRenderers.clear();
        try {
          myRenderers.addAll(NodeRendererSettings.getInstance().getAllRenderers(myProject));
        }
        finally {
          DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
            final DebuggerSession session = mySession;
            if (session != null && session.isAttached()) {
              DebuggerAction.refreshViews(mySession.getXDebugSession());
            }
          });
        }
      }
    });
  }

  @Nullable
  public Pair<Method, Value> getLastExecutedMethod() {
    final MethodReturnValueWatcher watcher = myReturnValueWatcher;
    if (watcher == null) {
      return null;
    }
    final Method method = watcher.getLastExecutedMethod();
    if (method == null) {
      return null;
    }
    return Pair.create(method, watcher.getLastMethodReturnValue());
  }

  public void setWatchMethodReturnValuesEnabled(boolean enabled) {
    final MethodReturnValueWatcher watcher = myReturnValueWatcher;
    if (watcher != null) {
      watcher.setEnabled(enabled);
    }
  }

  public boolean canGetMethodReturnValue() {
    return myReturnValueWatcher != null;
  }

  /**
   * @deprecated use {@link #getAutoRendererAsync(Type)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public NodeRenderer getAutoRenderer(ValueDescriptor descriptor) {
    Type type = descriptor.getType();
    DebuggerManagerThreadImpl.assertIsManagerThread();
    // in case evaluation is not possible, force default renderer
    if (!isEvaluationPossible()) {
      return getDefaultRenderer(type);
    }

    try {
      Object res = myNodeRenderersMap.get(type);
      if (res instanceof NodeRenderer) {
        return (NodeRenderer)res;
      }
      NodeRenderer renderer = myRenderers.stream().
        filter(NodeRenderer::isEnabled).
        filter(r -> DebuggerUtilsImpl.suppressExceptions(() -> r.isApplicable(type), false, true, ClassNotPreparedException.class)).
        findFirst().orElseGet(() -> getDefaultRenderer(type));
      myNodeRenderersMap.put(type, renderer);
      return renderer;
    }
    catch (ClassNotPreparedException e) {
      LOG.info(e);
      // use default, but do not cache
      return getDefaultRenderer(type);
    }
  }

  @NotNull
  public CompletableFuture<List<NodeRenderer>> getApplicableRenderers(Type type) {
    return DebuggerUtilsImpl.getApplicableRenderers(myRenderers, type);
  }

  @NotNull
  public CompletableFuture<NodeRenderer> getAutoRendererAsync(@Nullable Type type) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    // in case evaluation is not possible, force default renderer
    if (!isEvaluationPossible()) {
      return CompletableFuture.completedFuture(getDefaultRenderer(type));
    }

    try {
      Object renderer = myNodeRenderersMap.get(type);
      if (renderer instanceof NodeRenderer) {
        return CompletableFuture.completedFuture((NodeRenderer)renderer);
      }
      else if (renderer instanceof CompletableFuture) {
        //noinspection unchecked
        CompletableFuture<NodeRenderer> future = (CompletableFuture<NodeRenderer>)renderer;
        LOG.assertTrue(!future.isDone(), "Completed future for " + type);
        return future;
      }
      List<NodeRenderer> enabledRenderers = ContainerUtil.filter(myRenderers, NodeRenderer::isEnabled);
      CompletableFuture<NodeRenderer> res = DebuggerUtilsImpl.getFirstApplicableRenderer(enabledRenderers, type)
        .handle((r, throwable) -> {
          DebuggerManagerThreadImpl.assertIsManagerThread();
          if (r == null || throwable != null) {
            r = getDefaultRenderer(type); // do not cache the fallback renderer
            myNodeRenderersMap.remove(type);
          }
          else {
            // TODO: may add a new (possibly incorrect) value after the cleanup in reloadRenderers
            myNodeRenderersMap.put(type, r);
          }
        return r;
      });
      // check if future is already done
      myNodeRenderersMap.putIfAbsent(type, res);
      return res;
    }
    catch (ClassNotPreparedException e) {
      LOG.info(e);
      // use default, but do not cache
      return CompletableFuture.completedFuture(getDefaultRenderer(type));
    }
  }

  @NotNull
  public static NodeRenderer getDefaultRenderer(Value value) {
    return getDefaultRenderer(value != null ? value.type() : null);
  }

  @NotNull
  public static NodeRenderer getDefaultRenderer(Type type) {
    final NodeRendererSettings settings = NodeRendererSettings.getInstance();

    final PrimitiveRenderer primitiveRenderer = settings.getPrimitiveRenderer();
    if(primitiveRenderer.isApplicable(type)) {
      return primitiveRenderer;
    }

    final ArrayRenderer arrayRenderer = settings.getArrayRenderer();
    if(arrayRenderer.isApplicable(type)) {
      return arrayRenderer;
    }

    final ClassRenderer classRenderer = settings.getClassRenderer();
    LOG.assertTrue(classRenderer.isApplicable(type), type.name());
    return classRenderer;
  }

  private static final int ourTraceMask;

  static {
    String traceStr = System.getProperty("idea.debugger.trace");
    int mask = 0;
    if (!StringUtil.isEmpty(traceStr)) {
      StringTokenizer tokenizer = new StringTokenizer(traceStr);
      while (tokenizer.hasMoreTokens()) {
        String token = tokenizer.nextToken();
        if ("SENDS".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_SENDS;
        }
        else if ("RAW_SENDS".compareToIgnoreCase(token) == 0) {
          mask |= 0x01000000;
        }
        else if ("RECEIVES".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_RECEIVES;
        }
        else if ("RAW_RECEIVES".compareToIgnoreCase(token) == 0) {
          mask |= 0x02000000;
        }
        else if ("EVENTS".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_EVENTS;
        }
        else if ("REFTYPES".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_REFTYPES;
        }
        else if ("OBJREFS".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_OBJREFS;
        }
        else if ("ALL".compareToIgnoreCase(token) == 0) {
          mask |= VirtualMachine.TRACE_ALL;
        }
      }
    }
    ourTraceMask = mask;
  }

  private int getTraceMask() {
    int mask = ourTraceMask;
    DebugEnvironment environment = mySession.getDebugEnvironment();
    if (environment instanceof DefaultDebugEnvironment) {
      mask |= ((DefaultDebugEnvironment)environment).getTraceMode();
    }
    return mask;
  }

  protected void commitVM(VirtualMachine vm) {
    if (!isInInitialState()) {
      LOG.error("State is invalid " + myState.get());
    }
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager = new CompoundPositionManager(new PositionManagerImpl(this));
    myProject.getMessageBus().connect(myDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull final ModuleRootEvent event) {
        DumbService.getInstance(myProject).runWhenSmart(
          () -> getManagerThread().schedule(PrioritizedTask.Priority.HIGH, () -> {
            myPositionManager.clearCache();
            DebuggerUIUtil.invokeLater(() -> mySession.refresh(true));
          }));
      }
    });
    LOG.debug("*******************VM attached******************");

    vm.setDebugTraceMode(getTraceMask());

    checkVirtualMachineVersion(vm);
    myVirtualMachineProxy = new VirtualMachineProxyImpl(this, vm);
  }

  private void stopConnecting() {
    Map<String, Connector.Argument> arguments = myArguments;
    try {
      if (arguments == null) {
        return;
      }
      if (myConnection.isServerMode()) {
        Connector connector = getConnector();
        if (connector instanceof ListeningConnector) {
          ((ListeningConnector)connector).stopListening(arguments);
        }
      }
    }
    catch (IOException | IllegalConnectorArgumentsException | IllegalArgumentException e) {
      LOG.debug(e);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }

  @Override
  public void printToConsole(final String text) {
    synchronized (myProcessListeners) {
      if (myExecutionResult == null) {
        myTextBeforeStart.append(text);
      }
      else {
        printToConsoleImpl(text);
      }
    }
  }

  private void printToConsoleImpl(String text) {
    myExecutionResult.getProcessHandler().notifyTextAvailable(text, ProcessOutputTypes.SYSTEM);
  }

  @Override
  public ProcessHandler getProcessHandler() {
    return myExecutionResult != null ? myExecutionResult.getProcessHandler() : null;
  }

  /**
   *
   * @param suspendContext
   * @param stepThread
   * @param size the step size. One of {@link StepRequest#STEP_LINE} or {@link StepRequest#STEP_MIN}
   * @param depth
   * @param hint may be null
   */
  protected void doStep(final SuspendContextImpl suspendContext, final ThreadReferenceProxyImpl stepThread, int size, int depth,
                        RequestHint hint) {
    if (stepThread == null) {
      return;
    }
    try {
      final ThreadReference stepThreadReference = stepThread.getThreadReference();
      if (LOG.isDebugEnabled()) {
        LOG.debug("DO_STEP: creating step request for " + stepThreadReference);
      }
      deleteStepRequests(stepThreadReference);
      EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
      StepRequest stepRequest = requestManager.createStepRequest(stepThreadReference, size, depth);
      if (!(hint != null && hint.isIgnoreFilters()) && !isPositionFiltered(getLocation(stepThread, suspendContext))) {
        getActiveFilters().forEach(f -> stepRequest.addClassExclusionFilter(f.getPattern()));
      }

      // suspend policy to match the suspend policy of the context:
      // if all threads were suspended, then during stepping all the threads must be suspended
      // if only event thread were suspended, then only this particular thread must be suspended during stepping
      stepRequest.setSuspendPolicy(suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD
                                   ? EventRequest.SUSPEND_EVENT_THREAD
                                   : EventRequest.SUSPEND_ALL);

      stepRequest.addCountFilter(1);

      if (hint != null) {
        //noinspection HardCodedStringLiteral
        stepRequest.putProperty("hint", hint);
      }
      DebuggerUtilsAsync.setEnabled(stepRequest, true).whenComplete((__, e) -> {
        if (DebuggerUtilsAsync.unwrap(e) instanceof IllegalThreadStateException) {
          DebuggerUtilsAsync.deleteEventRequest(requestManager, stepRequest);
        }
      });
    }
    catch (ObjectCollectedException ignored) {

    }
  }

  static boolean isPositionFiltered(Location location) {
    List<ClassFilter> activeFilters = getActiveFilters();
    if (!activeFilters.isEmpty()) {
      ReferenceType referenceType = location != null ? location.declaringType() : null;
      if (referenceType != null) {
        String currentClassName = referenceType.name();
        return currentClassName != null && DebuggerUtilsEx.isFiltered(currentClassName, activeFilters);
      }
    }
    return false;
  }

  @NotNull
  private static List<ClassFilter> getActiveFilters() {
    DebuggerSettings settings = DebuggerSettings.getInstance();
    StreamEx<ClassFilter> stream = StreamEx.of(DebuggerClassFilterProvider.EP_NAME.getExtensionList())
      .flatCollection(DebuggerClassFilterProvider::getFilters);
    if (settings.TRACING_FILTERS_ENABLED) {
      stream = stream.prepend(settings.getSteppingFilters());
    }
    return stream.filter(ClassFilter::isEnabled).toList();
  }

  void deleteStepRequests(@Nullable final ThreadReference stepThread) {
    EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
    for (StepRequest request : new ArrayList<>(requestManager.stepRequests())) { // need a copy here to avoid CME
      if (stepThread == null || stepThread.equals(request.thread())) {
        try {
          DebuggerUtilsAsync.deleteEventRequest(requestManager, request);
        }
        catch (ObjectCollectedException ignored) {
        }
        catch (VMDisconnectedException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e); // report all for now
        }
      }
    }
  }

  static int getFrameCount(@Nullable ThreadReferenceProxyImpl thread, @NotNull SuspendContextImpl suspendContext) {
    if (thread != null) {
      if (thread.equals(suspendContext.getThread())) {
        return suspendContext.frameCount();
      }
      else {
        try {
          return thread.frameCount();
        }
        catch (EvaluateException ignored) {
        }
      }
    }
    return 0;
  }

  @Nullable
  static Location getLocation(@Nullable ThreadReferenceProxyImpl thread, @NotNull SuspendContextImpl suspendContext) {
    if (thread != null) {
      if (thread.equals(suspendContext.getThread())) {
        return suspendContext.getLocation();
      }
      else {
        try {
          if (thread.frameCount() > 0) {
            StackFrameProxyImpl stackFrame = thread.frame(0);
            if (stackFrame != null) {
              return stackFrame.location();
            }
          }
        }
        catch (EvaluateException ignored) {
        }
      }
    }
    return null;
  }

  private VirtualMachine createVirtualMachineInt() throws ExecutionException {
    try {
      if (myArguments != null) {
        throw new IOException(JavaDebuggerBundle.message("error.debugger.already.listening"));
      }

      final String port = myConnection.getDebuggerAddress();

      Connector connector = getConnector();
      myArguments = connector.defaultArguments();
      if (myConnection instanceof PidRemoteConnection && !((PidRemoteConnection)myConnection).isFixedAddress()) {
        String pid = ((PidRemoteConnection)myConnection).getPid();
        if (StringUtil.isEmpty(pid)) {
          throw new CantRunException(JavaDebuggerBundle.message("error.no.pid"));
        }
        setConnectorArgument("pid", pid);
      }
      else if (myConnection.isServerMode()) {
        if (myArguments == null) {
          throw new CantRunException(JavaDebuggerBundle.message("error.no.debug.listen.port"));
        }
      }
      else { // is client mode, should attach to already running process
        if (myConnection.isUseSockets()) {
          String debuggerHostName = myConnection.getDebuggerHostName();
          if (debuggerHostName != null) {
            setConnectorArgument("hostname", debuggerHostName);
          }
          if (port == null) {
            throw new CantRunException(JavaDebuggerBundle.message("error.no.debug.attach.port"));
          }
          setConnectorArgument("port", port);
        }
        else {
          if (port == null) {
            throw new CantRunException(JavaDebuggerBundle.message("error.no.shmem.address"));
          }
          setConnectorArgument("name", port);
        }
        setConnectorArgument("timeout", "0"); // wait forever
      }
      return connector instanceof AttachingConnector
             ? attachConnector((AttachingConnector)connector)
             : connectorListen(port, (ListeningConnector)connector);
    }
    catch (IOException e) {
      throw new ExecutionException(processIOException(e, JavaDebuggerBundle.getAddressDisplayName(myConnection)), e);
    }
    catch (IllegalConnectorArgumentsException e) {
      throw new ExecutionException(processError(e), e);
    }
    finally {
      myArguments = null;
    }
  }

  private void setConnectorArgument(String name, String value) {
    Connector.Argument argument = myArguments.get(name);
    if (argument != null) {
      argument.setValue(value);
    }
  }

  @ReviseWhenPortedToJDK("13")
  private VirtualMachine connectorListen(String address, ListeningConnector connector)
    throws CantRunException, IOException, IllegalConnectorArgumentsException {
    if (address == null) {
      throw new CantRunException(JavaDebuggerBundle.message("error.no.debug.listen.port"));
    }
    // zero port number means the caller leaves to debugger to decide at which port to listen
    final Connector.Argument portArg = myConnection.isUseSockets() ? myArguments.get("port") : myArguments.get("name");
    if (portArg != null) {
      portArg.setValue(address);

      // to allow connector to listen on several auto generated addresses
      if (address.length() == 0 || address.equals("0")) {
        EmptyConnectorArgument uniqueArg = new EmptyConnectorArgument("argForUniqueness");
        myArguments.put(uniqueArg.name(), uniqueArg);
      }
    }
    if (Registry.is("debugger.jb.jdi") || Runtime.version().feature() >= 13) {
      setConnectorArgument("localAddress", "*");
    }
    setConnectorArgument("timeout", "0"); // wait forever
    try {
      String listeningAddress = connector.startListening(myArguments);
      String port = StringUtil.substringAfterLast(listeningAddress, ":");
      if (port != null) {
        listeningAddress = port;
      }
      myConnection.setDebuggerAddress(listeningAddress);
      myConnection.setApplicationAddress(listeningAddress);

      myDebugProcessDispatcher.getMulticaster().connectorIsReady();

      return connector.accept(myArguments);
    }
    catch (IllegalArgumentException e) {
      throw new CantRunException(e.getLocalizedMessage());
    }
    finally {
      if(myArguments != null) {
        try {
          connector.stopListening(myArguments);
        }
        catch (IllegalArgumentException | IllegalConnectorArgumentsException ignored) {
          // ignored
        }
      }
    }
  }

  private VirtualMachine attachConnector(AttachingConnector connector)
    throws IOException, IllegalConnectorArgumentsException, CantRunException {
    myDebugProcessDispatcher.getMulticaster().connectorIsReady();
    try {
      return connector.attach(myArguments);
    }
    catch (IllegalArgumentException e) {
      throw new CantRunException(e.getLocalizedMessage());
    }
  }

  public void showStatusText(final @Nls String text) {
    myStatusUpdateAlarm.cancelAllRequests();
    myStatusUpdateAlarm.addRequest(() -> {
      if (!myProject.isDisposed()) {
        StatusBarUtil.setStatusBarInfo(myProject, text);
      }
    }, 50);
  }

  private Connector getConnector() throws ExecutionException {
    if (myConnection instanceof PidRemoteConnection && !((PidRemoteConnection)myConnection).isFixedAddress()) {
      return ((PidRemoteConnection)myConnection).getConnector(this);
    }
    return findConnector(myConnection.isUseSockets(), myConnection.isServerMode());
  }

  @NotNull
  public static Connector findConnector(boolean useSockets, boolean listen) throws ExecutionException {
    String connectorName = (Registry.is("debugger.jb.jdi") ? "com.jetbrains.jdi." : "com.sun.jdi.") +
                           (useSockets ? "Socket" : "SharedMemory") +
                           (listen ? "Listen" : "Attach");
    return findConnector(connectorName);
  }

  @NotNull
  public static Connector findConnector(String connectorName) throws ExecutionException {
    VirtualMachineManager virtualMachineManager;
    if (connectorName.startsWith("com.jetbrains")) {
      virtualMachineManager = VirtualMachineManagerImpl.virtualMachineManager();
    }
    else {
      try {
        virtualMachineManager = Bootstrap.virtualMachineManager();
      }
      catch (Error e) {
        throw new ExecutionException(JavaDebuggerBundle.message("debugger.jdi.bootstrap.error",
                                                            e.getClass().getName() + " : " + e.getLocalizedMessage()));
      }
    }
    return StreamEx.of(virtualMachineManager.allConnectors())
      .findFirst(c -> connectorName.equals(c.name()))
      .orElseThrow(() -> new CantRunException(JavaDebuggerBundle.message("error.debug.connector.not.found", connectorName)));
  }

  private void checkVirtualMachineVersion(VirtualMachine vm) {
    final String versionString = vm.version();
    if ("1.4.0".equals(versionString)) {
      DebuggerInvocationUtil.swingInvokeLater(myProject, () -> Messages.showMessageDialog(
        myProject,
        JavaDebuggerBundle.message("warning.jdk140.unstable"), JavaDebuggerBundle.message("title.jdk140.unstable"), Messages.getWarningIcon()
      ));
    }
    if (getSession().getAlternativeJre() == null) {
      Sdk runjre = getSession().getRunJre();
      JavaVersion version = JavaVersion.tryParse(versionString);
      if (version != null && (runjre == null || runjre.getSdkType() instanceof JavaSdkType) && !versionMatch(runjre, version)) {
        Arrays.stream(ProjectJdkTable.getInstance().getAllJdks())
          .filter(sdk -> versionMatch(sdk, version))
          .findFirst().ifPresent(sdk -> {
          XDebuggerManagerImpl.getNotificationGroup().createNotification(
            JavaDebuggerBundle.message("message.remote.jre.version.mismatch",
                                       versionString,
                                   runjre != null ? runjre.getVersionString() : "unknown",
                                       sdk.getName())
            , MessageType.INFO).notify(myProject);
          getSession().setAlternativeJre(sdk);
        });
      }
    }
  }

  private static boolean versionMatch(@Nullable Sdk sdk, @NotNull JavaVersion version) {
    if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
      JavaVersion v = JavaVersion.tryParse(sdk.getVersionString());
      if (v != null) {
        return version.feature == v.feature && version.minor == v.minor && version.update == v.update;
      }
    }
    return false;
  }

  /*Event dispatching*/
  public void addEvaluationListener(EvaluationListener evaluationListener) {
    myEvaluationDispatcher.addListener(evaluationListener);
  }

  public void removeEvaluationListener(EvaluationListener evaluationListener) {
    myEvaluationDispatcher.removeListener(evaluationListener);
  }


  @Override
  public void addDebugProcessListener(DebugProcessListener listener, Disposable parentDisposable) {
    myDebugProcessDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void addDebugProcessListener(DebugProcessListener listener) {
    myDebugProcessDispatcher.addListener(listener);
  }

  @Override
  public void removeDebugProcessListener(DebugProcessListener listener) {
    myDebugProcessDispatcher.removeListener(listener);
  }

  public void addProcessListener(ProcessListener processListener) {
    synchronized(myProcessListeners) {
      if(getProcessHandler() != null) {
        getProcessHandler().addProcessListener(processListener);
      }
      else {
        myProcessListeners.add(processListener);
      }
    }
  }

  public void removeProcessListener(ProcessListener processListener) {
    synchronized (myProcessListeners) {
      if(getProcessHandler() != null) {
        getProcessHandler().removeProcessListener(processListener);
      }
      else {
        myProcessListeners.remove(processListener);
      }
    }
  }

  /* getters */
  public RemoteConnection getConnection() {
    return myConnection;
  }

  @Override
  public ExecutionResult getExecutionResult() {
    return myExecutionResult;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  public boolean canRedefineClasses() {
    final VirtualMachineProxyImpl vm = myVirtualMachineProxy;
    return vm != null && vm.canRedefineClasses();
  }

  public boolean canWatchFieldModification() {
    final VirtualMachineProxyImpl vm = myVirtualMachineProxy;
    return vm != null && vm.canWatchFieldModification();
  }

  public boolean isInInitialState() {
    return myState.get() == State.INITIAL;
  }

  @Override
  public boolean isAttached() {
    return myState.get() == State.ATTACHED;
  }

  @Override
  public boolean isDetached() {
    return myState.get() == State.DETACHED;
  }

  @Override
  public boolean isDetaching() {
    return myState.get() == State.DETACHING;
  }

  @Override
  public RequestManagerImpl getRequestsManager() {
    return myRequestManager;
  }

  @NotNull
  @Override
  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final VirtualMachineProxyImpl vm = myVirtualMachineProxy;
    if (vm == null) {
      if (isInInitialState()) {
        throw new IllegalStateException("Virtual machine is not initialized yet");
      }
      else {
        throw new VMDisconnectedException();
      }
    }
    return vm;
  }

  @Override
  public void appendPositionManager(final PositionManager positionManager) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager.appendPositionManager(positionManager);
  }

  private volatile SteppingBreakpoint mySteppingBreakpoint;

  public void setSteppingBreakpoint(@Nullable SteppingBreakpoint breakpoint) {
    mySteppingBreakpoint = breakpoint;
  }

  public void cancelRunToCursorBreakpoint() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final SteppingBreakpoint runToCursorBreakpoint = mySteppingBreakpoint;
    if (runToCursorBreakpoint != null) {
      setSteppingBreakpoint(null);
      getRequestsManager().deleteRequest(runToCursorBreakpoint);
      if (runToCursorBreakpoint.isRestoreBreakpoints()) {
        DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().enableBreakpoints(this);
      }
    }
  }

  public static void prepareAndSetSteppingBreakpoint(SuspendContextImpl context,
                                                     @NotNull SteppingBreakpoint breakpoint,
                                                     RequestHint hint,
                                                     boolean resetThreadFilter) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    if (resetThreadFilter) {
      BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(debugProcess.getProject()).getBreakpointManager();
      breakpointManager.applyThreadFilter(debugProcess, null); // clear the filter on resume
    }
    breakpoint.setSuspendPolicy(
      context.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD ? DebuggerSettings.SUSPEND_THREAD : DebuggerSettings.SUSPEND_ALL);
    breakpoint.createRequest(debugProcess);
    breakpoint.setRequestHint(hint);
    debugProcess.setSteppingBreakpoint(breakpoint);
  }

  public void resetIgnoreSteppingFilters(@Nullable Location location, @Nullable RequestHint hint) {
    if (hint != null && hint.isResetIgnoreFilters() && location != null && !isPositionFiltered(location)) {
      getSession().resetIgnoreStepFiltersFlag();
    }
  }

  private void detachProcess(boolean closedByUser, boolean keepManagerThread, Consumer<@Nullable VirtualMachineData> callback) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if (myState.compareAndSet(State.INITIAL, State.DETACHING) || myState.compareAndSet(State.ATTACHED, State.DETACHING)) {
      try {
        if (!keepManagerThread) {
          getManagerThread().close();
        }
      }
      finally {
        if (!(myConnection instanceof RemoteConnectionStub)) {
          VirtualMachineData vmData = new VirtualMachineData(myVirtualMachineProxy, myConnection, myDebuggerManagerThread);
          myVirtualMachineProxy = null;
          myPositionManager = CompoundPositionManager.EMPTY;
          myReturnValueWatcher = null;
          myNodeRenderersMap.clear();
          myRenderers.clear();
          DebuggerUtils.cleanupAfterProcessFinish(this);
          myState.compareAndSet(State.DETACHING, State.DETACHED);
          try {
            myDebugProcessDispatcher.getMulticaster().processDetached(this, closedByUser);
          }
          finally {
            callback.accept(vmData);
          }
        }
      }
    }
  }

  protected void closeProcess(boolean closedByUser) {
    detachProcess(closedByUser, false, vmData -> {
      //if (DebuggerSettings.getInstance().UNMUTE_ON_STOP) {
      //  XDebugSession session = mySession.getXDebugSession();
      //  if (session != null) {
      //    session.setBreakpointMuted(false);
      //  }
      //}
      if (vmData != null && vmData.vm != null) {
        try {
          vmData.vm.dispose(); // to be on the safe side ensure that VM mirror, if present, is disposed and invalidated
        }
        catch (Throwable ignored) {
        }
      }
      myWaitFor.up();

      unstashAndReattach();
    });
  }

  @Contract(pure = true)
  private static String formatMessage(String message) {
    final int lineLength = 90;
    StringBuilder buf = new StringBuilder(message.length());
    int index = 0;
    while (index < message.length()) {
      buf.append(message, index, Math.min(index + lineLength, message.length())).append('\n');
      index += lineLength;
    }
    return buf.toString();
  }

  public static @NlsContexts.DialogMessage String processError(Exception e) {
    String message;

    if (e instanceof VMStartException) {
      VMStartException e1 = (VMStartException)e;
      message = e1.getLocalizedMessage();
    }
    else if (e instanceof IllegalConnectorArgumentsException) {
      IllegalConnectorArgumentsException e1 = (IllegalConnectorArgumentsException)e;
      final List<String> invalidArgumentNames = e1.argumentNames();
      message = formatMessage(
        JavaDebuggerBundle.message("error.invalid.argument", invalidArgumentNames.size()) + ": " + e1.getLocalizedMessage()) + invalidArgumentNames;
      LOG.debug(e1);
    }
    else if (e instanceof CantRunException) {
      message = e.getLocalizedMessage();
    }
    else if (e instanceof VMDisconnectedException) {
      message = JavaDebuggerBundle.message("error.vm.disconnected");
    }
    else if (e instanceof IOException) {
      message = processIOException((IOException)e, null);
    }
    else if (e instanceof ExecutionException) {
      message = e.getLocalizedMessage();
    }
    else {
      message = JavaDebuggerBundle.message("error.exception.while.connecting", e.getClass().getName(), e.getLocalizedMessage());
      LOG.debug(e);
    }
    return message;
  }

  @NotNull
  public static @NlsContexts.DialogMessage String processIOException(@NotNull IOException e, @Nullable String address) {
    if (e instanceof UnknownHostException) {
      if (address != null) {
        return JavaDebuggerBundle.message("error.unknown.host.with.address", address) + ":\n" + e.getLocalizedMessage();
      }
      return JavaDebuggerBundle.message("error.unknown.host") + ":\n" + e.getLocalizedMessage();
    }

    // Failed SA attach
    Throwable cause = e.getCause();
    if (cause instanceof InvocationTargetException) {
      if (cause.getCause() != null) {
        return cause.getCause().getLocalizedMessage();
      }
    }

    @Nls StringBuilder buf = new StringBuilder();
    if (address != null) {
      buf.append(JavaDebuggerBundle.message("error.cannot.open.debugger.port"));
      buf.append(" (").append(address).append("): ");
    }
    buf.append(e.getClass().getName()).append(" ");
    if (!StringUtil.isEmpty(e.getLocalizedMessage())) {
      buf.append('"').append(e.getLocalizedMessage()).append('"');
    }
    if (cause != null && !StringUtil.isEmpty(cause.getLocalizedMessage())) {
      buf.append(" (").append(cause.getLocalizedMessage()).append(')');
    }
    LOG.debug(e);
    return buf.toString();
  }

  public void dispose() {
    Disposer.dispose(myDisposable);
    myRequestManager.setFilterThread(null);
  }

  @Override
  public @NotNull DebuggerManagerThreadImpl getManagerThread() {
    return myDebuggerManagerThread;
  }

  public boolean isCurrentVirtualMachine(@NotNull VirtualMachineProxyImpl vmProxy) {
    VirtualMachine vm = ObjectUtils.doIfNotNull(myVirtualMachineProxy, it -> it.getVirtualMachine());
    return vmProxy.getVirtualMachine() == vm;
  }

  private static int getInvokePolicy(SuspendContext suspendContext) {
    if (suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD ||
        isResumeOnlyCurrentThread() ||
        ThreadBlockedMonitor.getSingleThreadedEvaluationThreshold() > 0) {
      return ObjectReference.INVOKE_SINGLE_THREADED;
    }
    return 0;
  }

  @Override
  public void waitFor() {
    LOG.assertTrue(!DebuggerManagerThreadImpl.isManagerThread());
    myWaitFor.waitFor();
  }

  @Override
  public void waitFor(long timeout) {
    LOG.assertTrue(!DebuggerManagerThreadImpl.isManagerThread());
    myWaitFor.waitFor(timeout);
  }

  private abstract class InvokeCommand <E extends Value> {
    private final Method myMethod;
    private final List<Value> myArgs;

    protected InvokeCommand(@NotNull Method method, @NotNull List<? extends Value> args) {
      myMethod = method;
      myArgs = new ArrayList<>(args);
    }

    public String toString() {
      return "INVOKE: " + super.toString();
    }

    protected abstract E invokeMethod(int invokePolicy, Method method, List<? extends Value> args) throws InvocationException,
                                                                                ClassNotLoadedException,
                                                                                IncompatibleThreadStateException,
                                                                                InvalidTypeException;


    E start(EvaluationContextImpl evaluationContext, boolean internalEvaluate) throws EvaluateException {
      while (true) {
        try {
          return startInternal(evaluationContext, internalEvaluate);
        }
        catch (ClassNotLoadedException e) {
          ReferenceType loadedClass = null;
          try {
            if (evaluationContext.isAutoLoadClasses()) {
              loadedClass = loadClass(evaluationContext, e.className(), evaluationContext.getClassLoader());
            }
          }
          catch (Exception ignored) {
            loadedClass = null;
          }
          if (loadedClass == null) {
            throw EvaluateExceptionUtil.createEvaluateException(e);
          }
        }
      }
    }

    E startInternal(EvaluationContextImpl evaluationContext, boolean internalEvaluate)
      throws EvaluateException, ClassNotLoadedException {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      SuspendContextImpl suspendContext = evaluationContext.getSuspendContext();
      SuspendManagerUtil.assertSuspendContext(suspendContext);

      ThreadReferenceProxyImpl invokeThread = suspendContext.getThread();

      if (SuspendManagerUtil.isEvaluating(getSuspendManager(), invokeThread)) {
        throw EvaluateExceptionUtil.NESTED_EVALUATION_ERROR;
      }

      if (!suspendContext.suspends(invokeThread)) {
        throw EvaluateExceptionUtil.THREAD_WAS_RESUMED;
      }

      Set<SuspendContextImpl> suspendingContexts = SuspendManagerUtil.getSuspendingContexts(getSuspendManager(), invokeThread);
      final ThreadReference invokeThreadRef = invokeThread.getThreadReference();

      myEvaluationDispatcher.getMulticaster().evaluationStarted(suspendContext);
      beforeMethodInvocation(suspendContext, myMethod, internalEvaluate);

      Object resumeData = null;
      try {
        for (SuspendContextImpl suspendingContext : suspendingContexts) {
          final ThreadReferenceProxyImpl suspendContextThread = suspendingContext.getThread();
          if (suspendContextThread != invokeThread) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Resuming " + invokeThread + " that is paused by " + suspendContextThread);
            }
            LOG.assertTrue(suspendContextThread == null || !invokeThreadRef.equals(suspendContextThread.getThreadReference()));
            getSuspendManager().resumeThread(suspendingContext, invokeThread);
          }
        }

        resumeData = SuspendManagerUtil.prepareForResume(suspendContext);
        suspendContext.setIsEvaluating(evaluationContext);

        getVirtualMachineProxy().clearCaches();

        return invokeMethodAndFork(suspendContext);
      }
      catch (InvocationException | InternalException | UnsupportedOperationException | ObjectCollectedException |
        InvalidTypeException | IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      finally {
        suspendContext.setIsEvaluating(null);
        if (resumeData != null) {
          SuspendManagerUtil.restoreAfterResume(suspendContext, resumeData);
        }
        for (SuspendContextImpl suspendingContext : mySuspendManager.getEventContexts()) {
          if (suspendingContexts.contains(suspendingContext) &&
              !suspendingContext.isEvaluating() &&
              !suspendingContext.suspends(invokeThread)) {
            mySuspendManager.suspendThread(suspendingContext, invokeThread);
          }
        }

        LOG.debug("getVirtualMachine().clearCaches()");
        getVirtualMachineProxy().clearCaches();
        afterMethodInvocation(suspendContext, internalEvaluate);

        myEvaluationDispatcher.getMulticaster().evaluationFinished(suspendContext);
      }
    }

    private E invokeMethodAndFork(final SuspendContextImpl context) throws InvocationException,
                                                                           ClassNotLoadedException,
                                                                           IncompatibleThreadStateException,
                                                                           InvalidTypeException {
      Ref<Exception> exception = Ref.create();
      Ref<E> result = Ref.create();
      getManagerThread().startLongProcessAndFork(() -> {
        ThreadReferenceProxyImpl thread = context.getThread();
        ThreadBlockedMonitor.InvocationWatcher invocationWatcher = null;
        try {
          try {
            if (LOG.isDebugEnabled()) {
              final VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachineProxy();
              virtualMachineProxy.logThreads();
              LOG.debug("Invoke in " + thread.name());
              assertThreadSuspended(thread, context);
            }

            if (myMethod.isVarArgs()) {
              // See IDEA-63581
              // if vararg parameter array is of interface type and Object[] is expected, JDI wrap it into another array,
              // in this case we have to unroll the array manually and pass its elements to the method instead of array object
              int lastIndex = myMethod.argumentTypeNames().size() - 1;
              if (lastIndex >= 0 && myArgs.size() > lastIndex) { // at least one varargs param
                Object firstVararg = myArgs.get(lastIndex);
                if (myArgs.size() == lastIndex + 1) { // only one vararg param
                  if (firstVararg instanceof ArrayReference) {
                    ArrayReference arrayRef = (ArrayReference)firstVararg;
                    if (((ArrayType)arrayRef.referenceType()).componentType() instanceof InterfaceType) {
                      List<String> argTypes = myMethod.argumentTypeNames();
                      if (argTypes.size() > lastIndex && argTypes.get(lastIndex).startsWith(CommonClassNames.JAVA_LANG_OBJECT)) {
                        // unwrap array of interfaces for vararg param
                        myArgs.remove(lastIndex);
                        myArgs.addAll(arrayRef.getValues());
                      }
                    }
                  }
                }
              }
            }

            if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
              // ensure args are not collected
              StreamEx.of(myArgs).select(ObjectReference.class).forEach(DebuggerUtilsEx::disableCollection);
            }

            if (Patches.JDK_BUG_ID_21275177 && (ourTraceMask & VirtualMachine.TRACE_SENDS) != 0) {
              //noinspection ResultOfMethodCallIgnored
              myArgs.forEach(Object::toString);
            }

            // workaround for jdi hang in trace mode, see IDEA-183387
            if (Patches.JDK_BUG_WITH_TRACE_SEND && (getTraceMask() & VirtualMachine.TRACE_SENDS) != 0) {
              StreamEx.of(myArgs).findAny(ThreadReference.class::isInstance).ifPresent(t -> {
                //noinspection UseOfSystemOutOrSystemErr
                System.err.println("[JDI: workaround for invocation of " + myMethod + "]");
                myMethod.virtualMachine().setDebugTraceMode(getTraceMask() & ~VirtualMachine.TRACE_SENDS);
              });
            }

            int invokePolicy = getInvokePolicy(context);
            invocationWatcher = myThreadBlockedMonitor.startInvokeWatching(invokePolicy, thread, context);

            result.set(invokeMethod(invokePolicy, myMethod, myArgs));
          }
          finally {
            if (invocationWatcher != null) {
              invocationWatcher.invocationFinished();
            }
            if (Patches.JDK_BUG_WITH_TRACE_SEND && (getTraceMask() & VirtualMachine.TRACE_SENDS) != 0) {
              myMethod.virtualMachine().setDebugTraceMode(getTraceMask());
            }
            //  assertThreadSuspended(thread, context);
            if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
              // ensure args are not collected
              StreamEx.of(myArgs).select(ObjectReference.class).forEach(DebuggerUtilsEx::enableCollection);
            }
          }
        }
        catch (Exception e) {
          exception.set(e);
        }
      });

      Exception ex = exception.get();
      if (ex != null) {
        if (ex instanceof InvocationException) {
          throw (InvocationException)ex;
        }
        else if (ex instanceof ClassNotLoadedException) {
          throw (ClassNotLoadedException)ex;
        }
        else if (ex instanceof IncompatibleThreadStateException) {
          throw (IncompatibleThreadStateException)ex;
        }
        else if (ex instanceof InvalidTypeException) {
          throw (InvalidTypeException)ex;
        }
        else if (ex instanceof RuntimeException) {
          throw (RuntimeException)ex;
        }
        else {
          LOG.error("Unexpected exception", new Throwable(ex));
        }
      }

      return result.get();
    }

    private void assertThreadSuspended(final ThreadReferenceProxyImpl thread, final SuspendContextImpl context) {
      LOG.assertTrue(context.isEvaluating());
      try {
        final boolean isSuspended = thread.isSuspended();
        LOG.assertTrue(isSuspended, thread);
      }
      catch (ObjectCollectedException ignored) {
      }
    }
  }

  @Override
  public Value invokeMethod(@NotNull EvaluationContext evaluationContext,
                            @NotNull ObjectReference objRef,
                            @NotNull Method method,
                            @NotNull List<? extends Value> args) throws EvaluateException {
    return invokeInstanceMethod(evaluationContext, objRef, method, args, 0);
  }

  @Override
  public Value invokeInstanceMethod(@NotNull EvaluationContext evaluationContext,
                                    @NotNull final ObjectReference objRef,
                                    @NotNull Method method,
                                    @NotNull List<? extends Value> args,
                                    final int invocationOptions) throws EvaluateException {
    return invokeInstanceMethod(evaluationContext, objRef, method, args, invocationOptions, false);
  }

  public Value invokeInstanceMethod(@NotNull EvaluationContext evaluationContext,
                                    @NotNull final ObjectReference objRef,
                                    @NotNull Method method,
                                    @NotNull List<? extends Value> args,
                                    final int invocationOptions,
                                    boolean internalEvaluate) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    return new InvokeCommand<Value>(method, args) {
      @Override
      protected Value invokeMethod(int invokePolicy, Method method, List<? extends Value> args) throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoking " + objRef.type().name() + "." + method.name());
        }
        return objRef.invokeMethod(thread, method, args, invokePolicy | invocationOptions);
      }
    }.start((EvaluationContextImpl)evaluationContext, internalEvaluate);
  }

  private static ThreadReference getEvaluationThread(final EvaluationContext evaluationContext) throws EvaluateException {
    ThreadReferenceProxy evaluationThread = evaluationContext.getSuspendContext().getThread();
    if(evaluationThread == null) {
      throw EvaluateExceptionUtil.NULL_STACK_FRAME;
    }
    return evaluationThread.getThreadReference();
  }

  @Override
  public Value invokeMethod(EvaluationContext evaluationContext,
                            ClassType classType,
                            Method method,
                            List<? extends Value> args) throws EvaluateException {
    return invokeMethod(evaluationContext, classType, method, args, 0,false);
  }

  public Value invokeMethod(@NotNull EvaluationContext evaluationContext,
                            @NotNull final ClassType classType,
                            @NotNull Method method,
                            @NotNull List<? extends Value> args,
                            boolean internalEvaluate) throws EvaluateException {
    return invokeMethod(evaluationContext, classType, method, args, 0, internalEvaluate);
  }

  public Value invokeMethod(@NotNull EvaluationContext evaluationContext,
                            @NotNull final ClassType classType,
                            @NotNull Method method,
                            @NotNull List<? extends Value> args,
                            int extraInvocationOptions,
                            boolean internalEvaluate) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    return new InvokeCommand<Value>(method, args) {
      @Override
      protected Value invokeMethod(int invokePolicy, Method method, List<? extends Value> args) throws InvocationException,
                                                                             ClassNotLoadedException,
                                                                             IncompatibleThreadStateException,
                                                                             InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoking " + classType.name() + "." + method.name());
        }
        return classType.invokeMethod(thread, method, args, invokePolicy | extraInvocationOptions);
      }
    }.start((EvaluationContextImpl)evaluationContext, internalEvaluate);
  }

  public Value invokeMethod(EvaluationContext evaluationContext,
                            InterfaceType interfaceType,
                            Method method,
                            List<? extends Value> args) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    return new InvokeCommand<Value>(method, args) {
      @Override
      protected Value invokeMethod(int invokePolicy, Method method, List<? extends Value> args) throws InvocationException,
                                                                                      ClassNotLoadedException,
                                                                                      IncompatibleThreadStateException,
                                                                                      InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoking " + interfaceType.name() + "." + method.name());
        }

        try {
          return interfaceType.invokeMethod(thread, method, args, invokePolicy);
        }
        catch (LinkageError e) {
          throw new IllegalStateException("Interface method invocation is not supported in JVM " +
                                          SystemInfo.JAVA_VERSION +
                                          ". Use JVM 1.8.0_45 or higher to run " +
                                          ApplicationNamesInfo.getInstance().getFullProductName());
        }
      }
    }.start((EvaluationContextImpl)evaluationContext, false);
  }


  @Override
  public ArrayReference newInstance(final ArrayType arrayType,
                                    final int dimension)
    throws EvaluateException {
    try {
      return arrayType.newInstance(dimension);
    }
    catch (Exception e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  @Override
  public ObjectReference newInstance(@NotNull final EvaluationContext evaluationContext,
                                     @NotNull final ClassType classType,
                                     @NotNull Method method,
                                     @NotNull List<? extends Value> args) throws EvaluateException {
    return newInstance(evaluationContext, classType, method, args, 0, false);
  }

  public ObjectReference newInstance(@NotNull final EvaluationContext evaluationContext,
                                     @NotNull final ClassType classType,
                                     @NotNull Method method,
                                     @NotNull List<? extends Value> args,
                                     final int invocationOptions,
                                     boolean internalEvaluate) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    InvokeCommand<ObjectReference> invokeCommand = new InvokeCommand<>(method, args) {
      @Override
      protected ObjectReference invokeMethod(int invokePolicy, Method method, List<? extends Value> args)
        throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("New instance " + classType.name() + "." + method.name());
        }
        return classType.newInstance(thread, method, args, invokePolicy | invocationOptions);
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, internalEvaluate);
  }

  public void clearCashes(@MagicConstant(flagsFromClass = EventRequest.class) int suspendPolicy) {
    if (!isAttached()) return;
    switch (suspendPolicy) {
      case EventRequest.SUSPEND_ALL:
        getVirtualMachineProxy().clearCaches();
        break;
      case EventRequest.SUSPEND_EVENT_THREAD:
        getVirtualMachineProxy().clearCaches();
        //suspendContext.getThread().clearAll();
        break;
    }
  }

  protected void beforeSuspend(SuspendContextImpl suspendContext) {
    clearCashes(suspendContext.getSuspendPolicy());
  }

  private void beforeMethodInvocation(SuspendContextImpl suspendContext, Method method, boolean internalEvaluate) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "before invocation in  thread " + suspendContext.getThread().name() + " method " + (method == null ? "null" : method.name()));
    }

    if (!internalEvaluate) {
      if (method != null) {
        showStatusText(JavaDebuggerBundle.message("progress.evaluating", DebuggerUtilsEx.methodName(method)));
      }
      else {
        showStatusText(JavaDebuggerBundle.message("title.evaluating"));
      }
    }
  }

  private void afterMethodInvocation(SuspendContextImpl suspendContext, boolean internalEvaluate) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("after invocation in  thread " + suspendContext.getThread().name());
    }
    if (!internalEvaluate) {
      showStatusText("");
    }
  }

  @Override
  public ReferenceType findClass(@Nullable EvaluationContext evaluationContext, String className,
                                 ClassLoaderReference classLoader) throws EvaluateException {
    try {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      ReferenceType result;
      List<ReferenceType> types = ContainerUtil.filter(getVirtualMachineProxy().classesByName(className), ReferenceType::isPrepared);
      // first try to quickly find the equal classloader only
      result = ContainerUtil.find(types, refType -> Objects.equals(classLoader, refType.classLoader()));
      // now do the full visibility check
      if (result == null && classLoader != null) {
        result = ContainerUtil.find(types, refType -> isVisibleFromClassLoader(classLoader, refType));
      }
      if (result == null && evaluationContext != null) {
        EvaluationContextImpl evalContext = (EvaluationContextImpl)evaluationContext;
        if (evalContext.isAutoLoadClasses()) {
          return loadClass(evalContext, className, classLoader);
        }
      }
      return result;
    }
    catch (InvocationException | InvalidTypeException | IncompatibleThreadStateException | ClassNotLoadedException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  private static boolean isVisibleFromClassLoader(@NotNull ClassLoaderReference fromLoader, final ReferenceType refType) {
    // IMPORTANT! Even if the refType is already loaded by some parent or bootstrap loader, it may not be visible from the given loader.
    // For example because there were no accesses yet from this loader to this class. So the loader is not in the list of "initialing" loaders
    // for this refType and the refType is not visible to the loader.
    // Attempt to evaluate method with this refType will yield ClassNotLoadedException.
    // The only way to say for sure whether the class is _visible_ to the given loader, is to use the following API call
    if (fromLoader instanceof ClassLoaderReferenceImpl) {
      return ((ClassLoaderReferenceImpl)fromLoader).isVisible(refType);
    }
    return fromLoader.visibleClasses().contains(refType);
  }

  private static String reformatArrayName(String className) {
    if (className.indexOf('[') == -1) return className;

    int dims = 0;
    while (className.endsWith("[]")) {
      className = className.substring(0, className.length() - 2);
      dims++;
    }

    StringBuilder buffer = new StringBuilder();
    StringUtil.repeatSymbol(buffer, '[', dims);
    String primitiveSignature = JVMNameUtil.getPrimitiveSignature(className);
    if(primitiveSignature != null) {
      buffer.append(primitiveSignature);
    }
    else {
      buffer.append('L');
      buffer.append(className);
      buffer.append(';');
    }
    return buffer.toString();
  }

  @SuppressWarnings({"SpellCheckingInspection"})
  public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String qName, ClassLoaderReference classLoader)
    throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException, EvaluateException {

    DebuggerManagerThreadImpl.assertIsManagerThread();
    qName = reformatArrayName(qName);
    VirtualMachineProxyImpl virtualMachine = getVirtualMachineProxy();
    ClassType classClassType = (ClassType)ContainerUtil.getFirstItem(virtualMachine.classesByName(CommonClassNames.JAVA_LANG_CLASS));
    if (classClassType == null) {
      LOG.error("Unable to find loaded class " + CommonClassNames.JAVA_LANG_CLASS);
      return null;
    }
    final Method forNameMethod;
    List<Value> args = new ArrayList<>(); // do not use unmodifiable lists because the list is modified by JPDA
    args.add(virtualMachine.mirrorOf(qName));
    if (classLoader != null) {
      //forNameMethod = classClassType.concreteMethodByName("forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
      forNameMethod = DebuggerUtils.findMethod(classClassType, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
      args.add(virtualMachine.mirrorOf(true));
      args.add(classLoader);
    }
    else {
      //forNameMethod = classClassType.concreteMethodByName("forName", "(Ljava/lang/String;)Ljava/lang/Class;");
      forNameMethod = DebuggerUtils.findMethod(classClassType, "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
    }
    if (forNameMethod == null) {
      LOG.error("Unable to find forName method in " + classClassType);
      return null;
    }
    Value classReference = invokeMethod(evaluationContext, classClassType, forNameMethod, args, MethodImpl.SKIP_ASSIGNABLE_CHECK, true);
    if (classReference instanceof ClassObjectReference) {
      ReferenceType refType = ((ClassObjectReference)classReference).reflectedType();
      if (classLoader instanceof ClassLoaderReferenceImpl) {
        ((ClassLoaderReferenceImpl)classLoader).addVisible(refType);
      }
      return refType;
    }
    return null;
  }

  public void logThreads() {
    if (LOG.isDebugEnabled()) {
      try {
        Collection<ThreadReferenceProxyImpl> allThreads = getVirtualMachineProxy().allThreads();
        for (ThreadReferenceProxyImpl threadReferenceProxy : allThreads) {
          LOG.debug("Thread name=" + threadReferenceProxy.name() + " suspendCount()=" + threadReferenceProxy.getSuspendCount());
        }
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }
  }

  public void onHotSwapFinished() {
    getPositionManager().clearCache();
    StackCapturingLineBreakpoint.clearCaches(this);
  }

  @NotNull
  public SuspendManager getSuspendManager() {
    return mySuspendManager;
  }

  @NotNull
  @Override
  public CompoundPositionManager getPositionManager() {
    return myPositionManager;
  }
  //ManagerCommands

  @Override
  public void stop(boolean forceTerminate) {
    myIsStopped.set(true);
    stopConnecting(); // does this first place in case debugger manager hanged accepting debugger connection (forever)
    getManagerThread().terminateAndInvoke(createStopCommand(forceTerminate), ApplicationManager.getApplication().isUnitTestMode() ? 0 : DebuggerManagerThreadImpl.COMMAND_TIMEOUT);
  }

  @NotNull
  public StopCommand createStopCommand(boolean forceTerminate) {
    return new StopCommand(forceTerminate);
  }

  protected class StopCommand extends DebuggerCommandImpl {
    private final boolean myIsTerminateTargetVM;

    public StopCommand(boolean isTerminateTargetVM) {
      myIsTerminateTargetVM = isTerminateTargetVM;
    }

    @Override
    public Priority getPriority() {
      return Priority.HIGH;
    }

    @Override
    protected void action() {
      if (isAttached()) {
        VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachineProxy();

        if (!virtualMachineProxy.canBeModified()) {
          closeProcess(false);
          return;
        }

        if (myIsTerminateTargetVM) {
          virtualMachineProxy.exit(-1);
        }
        else {
          // some VMs (like IBM VM 1.4.2 bundled with WebSphere) does not resume threads on dispose() like it should
          try {
            virtualMachineProxy.resume();
          }
          finally {
            virtualMachineProxy.dispose();
          }
        }
      }
      else {
        try {
          stopConnecting();
        }
        finally {
          closeProcess(true);
        }
      }
    }
  }

  public class StepOutCommand extends StepCommand {
    private final int myStepSize;

    public StepOutCommand(SuspendContextImpl suspendContext, int stepSize) {
      super(suspendContext, null);
      myStepSize = stepSize;
    }

    @Override
    public void contextAction() {
      showStatusText(JavaDebuggerBundle.message("status.step.out"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl thread = getContextThread();
      RequestHint hint = getHint(suspendContext, thread, null);
      applyThreadFilter(thread);
      startWatchingMethodReturn(thread);
      step(suspendContext, thread, hint);
      super.contextAction();
    }

    @Override
    @NotNull
    public RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl thread, @Nullable RequestHint parentHint) {
      RequestHint hint = new RequestHint(thread, suspendContext, StepRequest.STEP_LINE, StepRequest.STEP_OUT, null, parentHint);
      hint.setIgnoreFilters(mySession.shouldIgnoreSteppingFilters());
      return hint;
    }

    @Override
    public void step(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, RequestHint hint) {
      doStep(suspendContext, stepThread, myStepSize, StepRequest.STEP_OUT, hint);
    }
  }

  public class StepIntoCommand extends StepCommand {
    private final boolean myForcedIgnoreFilters;
    @Nullable
    private final StepIntoBreakpoint myBreakpoint;
    private final int myStepSize;

    public StepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, @Nullable final MethodFilter methodFilter,
                           int stepSize) {
      super(suspendContext, methodFilter);
      myForcedIgnoreFilters = ignoreFilters || methodFilter != null;
      myBreakpoint = methodFilter instanceof BreakpointStepMethodFilter ?
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addStepIntoBreakpoint(((BreakpointStepMethodFilter)methodFilter)) :
        null;
      myStepSize = stepSize;
    }

    @Override
    public void contextAction() {
      showStatusText(JavaDebuggerBundle.message("status.step.into"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl stepThread = getContextThread();
      final RequestHint hint = getHint(suspendContext, stepThread, null);
      if (myForcedIgnoreFilters) {
        mySession.setIgnoreStepFiltersFlag(getFrameCount(stepThread, suspendContext));
      }
      hint.setIgnoreFilters(myForcedIgnoreFilters || mySession.shouldIgnoreSteppingFilters());
      applyThreadFilter(stepThread);
      if (myBreakpoint != null) {
        prepareAndSetSteppingBreakpoint(suspendContext, myBreakpoint, hint, false);
      }
      step(suspendContext, stepThread, hint);
      super.contextAction();
    }

    @Override
    @NotNull
    public RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
      final RequestHint hint = new RequestHint(stepThread, suspendContext, StepRequest.STEP_LINE, StepRequest.STEP_INTO, myMethodFilter, parentHint);
      hint.setResetIgnoreFilters(myMethodFilter != null && !mySession.shouldIgnoreSteppingFilters());
      return hint;
    }

    @Override
    public void step(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, RequestHint hint) {
      doStep(suspendContext, stepThread, myStepSize, StepRequest.STEP_INTO, hint);
    }
  }

  public class StepOverCommand extends StepCommand {
    private final boolean myIsIgnoreBreakpoints;
    private final int myStepSize;

    public StepOverCommand(SuspendContextImpl suspendContext,
                           boolean ignoreBreakpoints,
                           @Nullable MethodFilter methodFilter,
                           int stepSize) {
      super(suspendContext, methodFilter);
      myIsIgnoreBreakpoints = ignoreBreakpoints;
      myStepSize = stepSize;
    }

    public StepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints, int stepSize) {
      this(suspendContext, ignoreBreakpoints, null, stepSize);
    }

    protected int getStepDepth() {
      return StepRequest.STEP_OVER;
    }

    @NotNull
    protected @Nls String getStatusText() {
      return JavaDebuggerBundle.message("status.step.over");
    }

    @Override
    @NotNull
    public RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
      // need this hint while stepping over for JSR45 support:
      // several lines of generated java code may correspond to a single line in the source file,
      // from which the java code was generated
      RequestHint hint = new RequestHint(stepThread, suspendContext, StepRequest.STEP_LINE, StepRequest.STEP_OVER, myMethodFilter, parentHint);
      hint.setRestoreBreakpoints(myIsIgnoreBreakpoints);
      hint.setIgnoreFilters(myIsIgnoreBreakpoints || mySession.shouldIgnoreSteppingFilters());
      return hint;
    }

    @Override
    public void contextAction() {
      showStatusText(getStatusText());

      SuspendContextImpl suspendContext = getSuspendContext();
      ThreadReferenceProxyImpl stepThread = getContextThread();

      RequestHint hint = getHint(suspendContext, stepThread, null);

      applyThreadFilter(stepThread);

      startWatchingMethodReturn(stepThread);

      step(suspendContext, stepThread, hint);

      if (myIsIgnoreBreakpoints) {
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().disableBreakpoints(DebugProcessImpl.this);
      }
      super.contextAction();
    }

    @Override
    public void step(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, RequestHint hint) {
      doStep(suspendContext, stepThread, myStepSize, getStepDepth(), hint);
    }
  }

  private final class RunToCursorCommand extends StepCommand {
    private final RunToCursorBreakpoint myRunToCursorBreakpoint;
    private final boolean myIgnoreBreakpoints;

    private RunToCursorCommand(SuspendContextImpl suspendContext, @NotNull XSourcePosition position, final boolean ignoreBreakpoints) {
      super(suspendContext, null);
      myIgnoreBreakpoints = ignoreBreakpoints;
      myRunToCursorBreakpoint =
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addRunToCursorBreakpoint(position, ignoreBreakpoints);
    }

    @Override
    public void contextAction() {
      showStatusText(JavaDebuggerBundle.message("status.run.to.cursor"));
      cancelRunToCursorBreakpoint();
      if (myRunToCursorBreakpoint == null) {
        return;
      }
      if (myIgnoreBreakpoints) {
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().disableBreakpoints(DebugProcessImpl.this);
      }
      applyThreadFilter(getContextThread());
      final SuspendContextImpl context = getSuspendContext();
      prepareAndSetSteppingBreakpoint(context, myRunToCursorBreakpoint, null, false);
      final DebugProcessImpl debugProcess = context.getDebugProcess();

      if (debugProcess.getRequestsManager().getWarning(myRunToCursorBreakpoint) == null) {
        super.contextAction();
      }
      else {
        DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
          Messages.showErrorDialog(
            JavaDebuggerBundle.message("error.running.to.cursor.no.executable.code",
                                       myRunToCursorBreakpoint.getSourcePosition().getFile().getName(),
                                   myRunToCursorBreakpoint.getLineIndex() + 1),
            UIUtil.removeMnemonic(ActionsBundle.actionText(XDebuggerActions.RUN_TO_CURSOR)));
          DebuggerSession session = debugProcess.getSession();
          session.getContextManager().setState(DebuggerContextUtil.createDebuggerContext(session, context),
                                               DebuggerSession.State.PAUSED, DebuggerSession.Event.CONTEXT, null);
        });
      }
    }
  }

  public abstract class StepCommand extends ResumeCommand {
    @Nullable
    protected final MethodFilter myMethodFilter;

    StepCommand(SuspendContextImpl suspendContext, @Nullable MethodFilter filter) {
      super(suspendContext);
      myMethodFilter = filter;
    }

    @Override
    protected void resumeAction() {
      SuspendContextImpl context = getSuspendContext();
      if (context != null &&
          (context.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD || isResumeOnlyCurrentThread())) {
        myThreadBlockedMonitor.startWatching(myContextThread);
      }
      if (context != null
          && isResumeOnlyCurrentThread()
          && context.getSuspendPolicy() == EventRequest.SUSPEND_ALL
          && myContextThread != null) {
        getSuspendManager().resumeThread(context, myContextThread);
      }
      else {
        super.resumeAction();
      }
    }

    public void step(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, RequestHint hint) {
    }

    @Nullable
    public RequestHint getHint(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, @Nullable RequestHint parentHint) {
      return null;
    }
  }

  public abstract class ResumeCommand extends SuspendContextCommandImpl {
    @Nullable protected final ThreadReferenceProxyImpl myContextThread;

    public ResumeCommand(SuspendContextImpl suspendContext) {
      super(suspendContext);
      final ThreadReferenceProxyImpl contextThread = getDebuggerContext().getThreadProxy();
      myContextThread = contextThread != null ? contextThread : (suspendContext != null? suspendContext.getThread() : null);
    }

    @Override
    public Priority getPriority() {
      return Priority.HIGH;
    }

    @Override
    public void contextAction() {
      showStatusText(JavaDebuggerBundle.message("status.process.resumed"));
      resumeAction();
      myDebugProcessDispatcher.getMulticaster().resumed(getSuspendContext());
    }

    protected void resumeAction() {
      getSuspendManager().resume(getSuspendContext());
    }

    @Nullable
    public ThreadReferenceProxyImpl getContextThread() {
      return myContextThread;
    }

    protected void applyThreadFilter(ThreadReferenceProxy thread) {
      if (getSuspendContext().getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
        // there could be explicit resume as a result of call to voteSuspend()
        // e.g. when breakpoint was considered invalid, in that case the filter will be applied _after_
        // resuming and all breakpoints in other threads will be ignored.
        // As resume() implicitly cleares the filter, the filter must be always applied _before_ any resume() action happens
        final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager();
        breakpointManager.applyThreadFilter(DebugProcessImpl.this, thread.getThreadReference());
      }
    }
  }

  private class PauseCommand extends DebuggerCommandImpl {
    PauseCommand() {
    }

    @Override
    public void action() {
      if (!isAttached() || getVirtualMachineProxy().isPausePressed()) {
        return;
      }
      logThreads();
      getVirtualMachineProxy().suspend();
      logThreads();
      SuspendContextImpl suspendContext = mySuspendManager.pushSuspendContext(EventRequest.SUSPEND_ALL, 0);
      myDebugProcessDispatcher.getMulticaster().paused(suspendContext);
    }
  }

  private class ResumeThreadCommand extends SuspendContextCommandImpl {
    private final ThreadReferenceProxyImpl myThread;

    ResumeThreadCommand(SuspendContextImpl suspendContext, @NotNull ThreadReferenceProxyImpl thread) {
      super(suspendContext);
      myThread = thread;
    }

    @Override
    public void contextAction() {
      // handle unfreeze through the regular context resume
      if (false && getSuspendManager().isFrozen(myThread)) {
        getSuspendManager().unfreezeThread(myThread);
        return;
      }

      final Set<SuspendContextImpl> suspendingContexts = SuspendManagerUtil.getSuspendingContexts(getSuspendManager(), myThread);
      for (SuspendContextImpl suspendContext : suspendingContexts) {
        if (suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD && suspendContext.getThread() == myThread) {
          getSession().getXDebugSession().sessionResumed();
          getManagerThread().invoke(createResumeCommand(suspendContext));
        }
        else {
          getSuspendManager().resumeThread(suspendContext, myThread);
        }
      }
    }
  }

  private class FreezeThreadCommand extends DebuggerCommandImpl {
    private final ThreadReferenceProxyImpl myThread;

    FreezeThreadCommand(ThreadReferenceProxyImpl thread) {
      myThread = thread;
    }

    @Override
    protected void action() {
      SuspendManager suspendManager = getSuspendManager();
      if (!suspendManager.isFrozen(myThread)) {
        suspendManager.freezeThread(myThread);
        SuspendContextImpl suspendContext = mySuspendManager.pushSuspendContext(EventRequest.SUSPEND_EVENT_THREAD, 0);
        suspendContext.setThread(myThread.getThreadReference());
        myDebugProcessDispatcher.getMulticaster().paused(suspendContext);
      }
    }
  }

  private class PopFrameCommand extends DebuggerContextCommandImpl {
    private final StackFrameProxyImpl myStackFrame;

    PopFrameCommand(DebuggerContextImpl context, StackFrameProxyImpl frameProxy) {
      super(context, frameProxy.threadProxy());
      myStackFrame = frameProxy;
    }

    @Override
    public void threadAction(@NotNull SuspendContextImpl suspendContext) {
      final ThreadReferenceProxyImpl thread = myStackFrame.threadProxy();
      try {
        if (!getSuspendManager().isSuspended(thread)) {
          notifyCancelled();
          return;
        }
      }
      catch (ObjectCollectedException ignored) {
        notifyCancelled();
        return;
      }

      if (!suspendContext.suspends(thread)) {
        suspendContext.postponeCommand(this);
        return;
      }

      if (myStackFrame.isBottom()) {
        DebuggerInvocationUtil.swingInvokeLater(myProject, () -> Messages.showMessageDialog(myProject, JavaDebuggerBundle
          .message("error.pop.bottom.stackframe"), ActionsBundle.actionText(PopFrameAction.ACTION_NAME), Messages.getErrorIcon()));
        return;
      }

      try {
        thread.popFrames(myStackFrame);
        getSuspendManager().popFrame(suspendContext);
      }
      catch (final EvaluateException e) {
        DebuggerInvocationUtil.swingInvokeLater(myProject,
                                                () -> Messages.showMessageDialog(myProject, JavaDebuggerBundle
                                                  .message("error.pop.stackframe", e.getLocalizedMessage()), ActionsBundle.actionText(
                                                  PopFrameAction.ACTION_NAME), Messages.getErrorIcon()));
        LOG.info(e);
      }
    }
  }

  @Override
  @NotNull
  public GlobalSearchScope getSearchScope() {
    LOG.assertTrue(mySession != null, "Accessing debug session before its initialization");
    return mySession.getSearchScope();
  }

  public void reattach(final DebugEnvironment environment) {
    reattach(environment, false, () -> {});
  }

  public void reattach(final DebugEnvironment environment, boolean keepCurrentVM, Runnable vmReadyCallback) {
    reattach(environment, () -> {
      if (keepCurrentVM) {
        detachProcess(false, true, vmData -> {
          myStashedVirtualMachines.addFirst(vmData);
          myDebuggerManagerThread = new DebuggerManagerThreadImpl(myDisposable, myProject);
        });
      } else {
        closeProcess(false);
      }
    }, vmReadyCallback);
  }

  private void unstashAndReattach() {
    VirtualMachineData vmData = myStashedVirtualMachines.pollFirst();
    if (vmData != null && vmData.vm != null) {
      myDebuggerManagerThread = vmData.debuggerManagerThread;
      reattach(vmData.connection, () -> {}, () -> {
        afterProcessStarted(() -> getManagerThread().schedule(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            try {
              commitVM(vmData.vm.getVirtualMachine());
            }
            catch (VMDisconnectedException e) {
              fail();
            }
          }
        }));
      });
    }
  }

  private void reattach(DebugEnvironment environment, Runnable detachVm, Runnable vmReadyCallback) {
    reattach(environment.getRemoteConnection(), detachVm, () -> {
      createVirtualMachine(environment);
      vmReadyCallback.run();
    });
  }

  private void reattach(RemoteConnection connection, Runnable detachVm, Runnable attachVm) {
    if (!myIsStopped.get()) {
      getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() {
          detachVm.run();
          getManagerThread().processRemaining();
          doReattach();
        }

        @Override
        protected void commandCancelled() {
          doReattach(); // if the original process is already finished
        }

        private void doReattach() {
          DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
            ((XDebugSessionImpl)getXdebugProcess().getSession()).reset();
            myState.set(State.INITIAL);
            myConnection = connection;
            getManagerThread().restartIfNeeded();
            attachVm.run();
          });
        }
      });
    }
  }

  @Nullable
  public ExecutionResult attachVirtualMachine(final DebugEnvironment environment,
                                              final DebuggerSession session) throws ExecutionException {
    mySession = session;
    myWaitFor.down();

    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(isInInitialState());

    myConnection = environment.getRemoteConnection();

    // in client mode start target process before the debugger to reduce polling
    if (!(myConnection instanceof RemoteConnectionStub) &&
        !(myConnection instanceof DelayedRemoteConnection) &&
        myConnection.isServerMode()) {
      createVirtualMachine(environment);
      if (myIsFailed.get()) {
        myExecutionResult = null;
        return null;
      }
    }

    ExecutionResult executionResult;
    try {
      synchronized (myProcessListeners) {
        executionResult = environment.createExecutionResult();
        myExecutionResult = executionResult;
        if (executionResult == null) {
          fail();
          return null;
        }
        for (ProcessListener processListener : myProcessListeners) {
          executionResult.getProcessHandler().addProcessListener(processListener);
        }
        myProcessListeners.clear();
        if (myTextBeforeStart.length() > 0) {
          printToConsoleImpl(myTextBeforeStart.toString());
          myTextBeforeStart.setLength(0);
        }
      }
    }
    catch (ExecutionException e) {
      fail();
      throw e;
    }

    if (myConnection instanceof DelayedRemoteConnection) {
      ((DelayedRemoteConnection)myConnection).setAttachRunnable(() -> createVirtualMachine(environment));
    } else if (!(myConnection instanceof RemoteConnectionStub) && !myConnection.isServerMode()) {
      createVirtualMachine(environment);
      if (myIsFailed.get()) {
        myExecutionResult = null;
        return null;
      }
    }

    return executionResult;
  }

  private void fail() {
    // need this in order to prevent calling stop() twice
    if (myIsFailed.compareAndSet(false, true)) {
      stop(false);
    }
  }

  private void createVirtualMachine(final DebugEnvironment environment) {
    final String sessionName = environment.getSessionName();
    final long pollTimeout = environment.getPollTimeout();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final AtomicBoolean connectorIsReady = new AtomicBoolean(false);
    myDebugProcessDispatcher.addListener(new DebugProcessListener() {
      @Override
      public void connectorIsReady() {
        connectorIsReady.set(true);
        semaphore.up();
        myDebugProcessDispatcher.removeListener(this);
      }
    });

    getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        VirtualMachine vm = null;

        try {
          final long time = System.currentTimeMillis();
          do {
            try {
              vm = createVirtualMachineInt();
              break;
            }
            catch (final ExecutionException e) {
              if (pollTimeout > 0 && !myConnection.isServerMode() && e.getCause() instanceof IOException) {
                synchronized (this) {
                  try {
                    wait(500);
                  }
                  catch (InterruptedException ignored) {
                    break;
                  }
                }
              }
              else {
                ProcessHandler processHandler = getProcessHandler();
                boolean terminated =
                  processHandler != null && (processHandler.isProcessTerminating() || processHandler.isProcessTerminated());

                fail();
                DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
                  // propagate exception only in case we succeeded to obtain execution result,
                  // otherwise if the error is induced by the fact that there is nothing to debug, and there is no need to show
                  // this problem to the user
                  if (((myExecutionResult != null && !terminated) || !connectorIsReady.get()) &&
                      !ApplicationManager.getApplication().isHeadlessEnvironment()) {
                    ExecutionUtil.handleExecutionError(myProject, ToolWindowId.DEBUG, sessionName, e);
                  }
                });
                break;
              }
            }
          }
          while (System.currentTimeMillis() - time < pollTimeout);
        }
        finally {
          semaphore.up();
        }

        if (vm != null) {
          final VirtualMachine vm1 = vm;
          afterProcessStarted(() -> getManagerThread().schedule(new DebuggerCommandImpl() {
            @Override
            protected void action() {
              try {
                commitVM(vm1);
              } catch (VMDisconnectedException e) {
                fail();
              }
            }
          }));
        }
        else {
          fail();
        }
      }

      @Override
      protected void commandCancelled() {
        try {
          super.commandCancelled();
        }
        finally {
          semaphore.up();
        }
      }
    });

    semaphore.waitFor();
  }

  private void afterProcessStarted(final Runnable run) {
    class MyProcessAdapter extends ProcessAdapter {
      private boolean alreadyRun = false;

      public synchronized void run() {
        if(!alreadyRun) {
          alreadyRun = true;
          run.run();
        }
        removeProcessListener(this);
      }

      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        run();
      }
    }
    MyProcessAdapter processListener = new MyProcessAdapter();
    addProcessListener(processListener);
    if (myExecutionResult != null) {
      if (myExecutionResult.getProcessHandler().isStartNotified()) {
        processListener.run();
      }
    }
  }

  public boolean isPausePressed() {
    final VirtualMachineProxyImpl vm = myVirtualMachineProxy;
    return vm != null && vm.isPausePressed();
  }

  @NotNull
  public DebuggerCommandImpl createPauseCommand() {
    return new PauseCommand();
  }

  @NotNull
  public ResumeCommand createResumeCommand(SuspendContextImpl suspendContext) {
    return createResumeCommand(suspendContext, PrioritizedTask.Priority.HIGH);
  }

  @NotNull
  public ResumeCommand createResumeCommand(SuspendContextImpl suspendContext, final PrioritizedTask.Priority priority) {
    final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager();
    return new ResumeCommand(suspendContext) {
      @Override
      public void contextAction() {
        breakpointManager.applyThreadFilter(DebugProcessImpl.this, null); // clear the filter on resume
        if (myReturnValueWatcher != null) {
          myReturnValueWatcher.clear();
        }
        super.contextAction();
      }

      @Override
      public Priority getPriority() {
        return priority;
      }
    };
  }

  @NotNull
  public ResumeCommand createStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
    return createStepOverCommand(suspendContext, ignoreBreakpoints, StepRequest.STEP_LINE);
  }

  @NotNull
  public ResumeCommand createStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints, int stepSize) {
    return createStepOverCommand(suspendContext, ignoreBreakpoints, null, StepRequest.STEP_LINE);
  }

  @NotNull
  public ResumeCommand createStepOverCommand(SuspendContextImpl suspendContext,
                                             boolean ignoreBreakpoints,
                                             @Nullable MethodFilter methodFilter,
                                             int stepSize) {
    return new StepOverCommand(suspendContext, ignoreBreakpoints, methodFilter, stepSize);
  }

  @NotNull
  public ResumeCommand createStepOutCommand(SuspendContextImpl suspendContext) {
    return createStepOutCommand(suspendContext, StepRequest.STEP_LINE);
  }

  @NotNull
  public ResumeCommand createStepOutCommand(SuspendContextImpl suspendContext, int stepSize) {
    return new StepOutCommand(suspendContext, stepSize);
  }

  @NotNull
  public ResumeCommand createStepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, final MethodFilter smartStepFilter) {
    return createStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, StepRequest.STEP_LINE);
  }

  @NotNull
  public ResumeCommand createStepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, final MethodFilter smartStepFilter,
                                             int stepSize) {
    return new StepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, stepSize);
  }

  @NotNull
  public ResumeCommand createRunToCursorCommand(SuspendContextImpl suspendContext,
                                                @NotNull XSourcePosition position,
                                                boolean ignoreBreakpoints)
    throws EvaluateException {
    RunToCursorCommand runToCursorCommand = new RunToCursorCommand(suspendContext, position, ignoreBreakpoints);
    if (runToCursorCommand.myRunToCursorBreakpoint == null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(position.getFile());
      throw new EvaluateException(
        JavaDebuggerBundle.message("error.running.to.cursor.no.executable.code", psiFile != null ? psiFile.getName() : "<No File>",
                                   position.getLine()), null);
    }
    return runToCursorCommand;
  }

  @NotNull
  public DebuggerCommandImpl createFreezeThreadCommand(ThreadReferenceProxyImpl thread) {
    return new FreezeThreadCommand(thread);
  }

  @NotNull
  public SuspendContextCommandImpl createResumeThreadCommand(SuspendContextImpl suspendContext, @NotNull ThreadReferenceProxyImpl thread) {
    return new ResumeThreadCommand(suspendContext, thread);
  }

  @NotNull
  public SuspendContextCommandImpl createPopFrameCommand(DebuggerContextImpl context, StackFrameProxyImpl stackFrame) {
    return new PopFrameCommand(context, stackFrame);
  }

  //public void setBreakpointsMuted(final boolean muted) {
  //  XDebugSession session = mySession.getXDebugSession();
  //  if (isAttached()) {
  //    getManagerThread().schedule(new DebuggerCommandImpl() {
  //      @Override
  //      protected void action() {
  //        // set the flag before enabling/disabling cause it affects if breakpoints will create requests
  //        if (myBreakpointsMuted.getAndSet(muted) != muted) {
  //          final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
  //          if (muted) {
  //            breakpointManager.disableBreakpoints(DebugProcessImpl.this);
  //          }
  //          else {
  //            breakpointManager.enableBreakpoints(DebugProcessImpl.this);
  //          }
  //        }
  //      }
  //    });
  //  }
  //  else {
  //    session.setBreakpointMuted(muted);
  //  }
  //}

  @NotNull
  public DebuggerContextImpl getDebuggerContext() {
    return mySession.getContextManager().getContext();
  }

  public void setXDebugProcess(JavaDebugProcess XDebugProcess) {
    myXDebugProcess = XDebugProcess;
  }

  @Nullable
  public JavaDebugProcess getXdebugProcess() {
    return myXDebugProcess;
  }

  public boolean areBreakpointsMuted() {
    XDebugSession session = mySession.getXDebugSession();
    return session != null && session.areBreakpointsMuted();
  }

  public DebuggerSession getSession() {
    return mySession;
  }

  static boolean isResumeOnlyCurrentThread() {
    return DebuggerSettings.getInstance().RESUME_ONLY_CURRENT_THREAD;
  }

  public boolean isEvaluationPossible() {
    return getSuspendManager().getPausedContext() != null;
  }

  public boolean isEvaluationPossible(SuspendContextImpl suspendContext) {
    return mySuspendManager.hasPausedContext(suspendContext);
  }

  public void startWatchingMethodReturn(ThreadReferenceProxyImpl thread) {
    if (myReturnValueWatcher != null) {
      myReturnValueWatcher.enable(thread.getThreadReference());
    }
  }

  void stopWatchingMethodReturn() {
    if (myReturnValueWatcher != null) {
      myReturnValueWatcher.disable();
    }
  }

  private static class VirtualMachineData {
    public final VirtualMachineProxyImpl vm;
    public final RemoteConnection connection;
    public final DebuggerManagerThreadImpl debuggerManagerThread;

    private VirtualMachineData(VirtualMachineProxyImpl vm, RemoteConnection connection, DebuggerManagerThreadImpl debuggerManagerThread) {
      this.vm = vm;
      this.connection = connection;
      this.debuggerManagerThread = debuggerManagerThread;
    }
  }
}
