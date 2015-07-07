/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.Patches;
import com.intellij.debugger.*;
import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.apiAdapters.ConnectionServiceWrapper;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.engine.requests.MethodReturnValueWatcher;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.RunToCursorBreakpoint;
import com.intellij.debugger.ui.breakpoints.StepIntoBreakpoint;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
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
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class DebugProcessImpl extends UserDataHolderBase implements DebugProcess {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebugProcessImpl");

  @NonNls private static final String SOCKET_ATTACHING_CONNECTOR_NAME = "com.sun.jdi.SocketAttach";
  @NonNls private static final String SHMEM_ATTACHING_CONNECTOR_NAME = "com.sun.jdi.SharedMemoryAttach";
  @NonNls private static final String SOCKET_LISTENING_CONNECTOR_NAME = "com.sun.jdi.SocketListen";
  @NonNls private static final String SHMEM_LISTENING_CONNECTOR_NAME = "com.sun.jdi.SharedMemoryListen";

  private final Project myProject;
  private final RequestManagerImpl myRequestManager;

  private volatile VirtualMachineProxyImpl myVirtualMachineProxy = null;
  protected EventDispatcher<DebugProcessListener> myDebugProcessDispatcher = EventDispatcher.create(DebugProcessListener.class);
  protected EventDispatcher<EvaluationListener> myEvaluationDispatcher = EventDispatcher.create(EvaluationListener.class);

  private final List<ProcessListener> myProcessListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  protected static final int STATE_INITIAL   = 0;
  protected static final int STATE_ATTACHED  = 1;
  protected static final int STATE_DETACHING = 2;
  protected static final int STATE_DETACHED  = 3;
  protected final AtomicInteger myState = new AtomicInteger(STATE_INITIAL);

  private volatile ExecutionResult myExecutionResult;
  private RemoteConnection myConnection;
  private JavaDebugProcess myXDebugProcess;

  private ConnectionServiceWrapper myConnectionService;
  private Map<String, Connector.Argument> myArguments;

  private final List<NodeRenderer> myRenderers = new ArrayList<NodeRenderer>();

  // we use null key here
  private final Map<Type, NodeRenderer> myNodeRenderersMap = new HashMap<Type, NodeRenderer>();

  private final NodeRendererSettingsListener mySettingsListener = new NodeRendererSettingsListener() {
    @Override
    public void renderersChanged() {
      myNodeRenderersMap.clear();
      myRenderers.clear();
      loadRenderers();
    }
  };

  private final SuspendManagerImpl mySuspendManager = new SuspendManagerImpl(this);
  protected CompoundPositionManager myPositionManager = null;
  private final DebuggerManagerThreadImpl myDebuggerManagerThread;
  private static final int LOCAL_START_TIMEOUT = 30000;

  private final Semaphore myWaitFor = new Semaphore();
  private final AtomicBoolean myIsFailed = new AtomicBoolean(false);
  protected DebuggerSession mySession;
  @Nullable protected MethodReturnValueWatcher myReturnValueWatcher;
  private final Disposable myDisposable = Disposer.newDisposable();
  private final Alarm myStatusUpdateAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, myDisposable);

  protected DebugProcessImpl(Project project) {
    myProject = project;
    myDebuggerManagerThread = new DebuggerManagerThreadImpl(myDisposable, myProject);
    myRequestManager = new RequestManagerImpl(this);
    NodeRendererSettings.getInstance().addListener(mySettingsListener);
    loadRenderers();
  }

  private void loadRenderers() {
    getManagerThread().invoke(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        try {
          final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();
          for (final NodeRenderer renderer : rendererSettings.getAllRenderers()) {
            if (renderer.isEnabled()) {
              myRenderers.add(renderer);
            }
          }
        }
        finally {
          DebuggerInvocationUtil.swingInvokeLater(myProject, new Runnable() {
            @Override
            public void run() {
              final DebuggerSession session = mySession;
              if (session != null && session.isAttached()) {
                session.refresh(true);
                DebuggerAction.refreshViews(mySession.getXDebugSession());
              }
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
      watcher.setFeatureEnabled(enabled);
    }
  }

  public boolean isWatchMethodReturnValuesEnabled() {
    final MethodReturnValueWatcher watcher = myReturnValueWatcher;
    return watcher != null && watcher.isFeatureEnabled();
  }

  public boolean canGetMethodReturnValue() {
    return myReturnValueWatcher != null;
  }

  public NodeRenderer getAutoRenderer(ValueDescriptor descriptor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final Value value = descriptor.getValue();
    Type type = value != null ? value.type() : null;

    // in case evaluation is not possible, force default renderer
    if (!DebuggerManagerEx.getInstanceEx(getProject()).getContext().isEvaluationPossible()) {
      return getDefaultRenderer(type);
    }

    NodeRenderer renderer = myNodeRenderersMap.get(type);
    if(renderer == null) {
      for (final NodeRenderer nodeRenderer : myRenderers) {
        if (nodeRenderer.isApplicable(type)) {
          renderer = nodeRenderer;
          break;
        }
      }
      if (renderer == null) {
        renderer = getDefaultRenderer(type);
      }
      myNodeRenderersMap.put(type, renderer);
    }

    return renderer;
  }

  public static NodeRenderer getDefaultRenderer(Value value) {
    return getDefaultRenderer(value != null ? value.type() : null);
  }

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

  private static final String ourTrace = System.getProperty("idea.debugger.trace");

  @SuppressWarnings({"HardCodedStringLiteral"})
  protected void commitVM(VirtualMachine vm) {
    if (!isInInitialState()) {
      LOG.error("State is invalid " + myState.get());
    }
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager = createPositionManager();
    if (LOG.isDebugEnabled()) {
      LOG.debug("*******************VM attached******************");
    }
    checkVirtualMachineVersion(vm);

    myVirtualMachineProxy = new VirtualMachineProxyImpl(this, vm);

    if (!StringUtil.isEmpty(ourTrace)) {
      int mask = 0;
      StringTokenizer tokenizer = new StringTokenizer(ourTrace);
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

      vm.setDebugTraceMode(mask);
    }
  }

  private void stopConnecting() {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    Map<String, Connector.Argument> arguments = myArguments;
    try {
      if (arguments == null) {
        return;
      }
      if(myConnection.isServerMode()) {
        ListeningConnector connector = (ListeningConnector)findConnector(SOCKET_LISTENING_CONNECTOR_NAME);
        if (connector == null) {
          LOG.error("Cannot find connector: " + SOCKET_LISTENING_CONNECTOR_NAME);
        }
        else {
          connector.stopListening(arguments);
        }
      }
      else {
        if(myConnectionService != null) {
          myConnectionService.close();
        }
      }
    }
    catch (IOException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
    }
    catch (IllegalConnectorArgumentsException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    finally {
      closeProcess(true);
    }
  }

  protected CompoundPositionManager createPositionManager() {
    return new CompoundPositionManager(new PositionManagerImpl(this));
  }

  @Override
  public void printToConsole(final String text) {
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
      if (!(hint != null && hint.isIgnoreFilters()) /*&& depth == StepRequest.STEP_INTO*/) {
        List<ClassFilter> activeFilters = getActiveFilters();

        if (!activeFilters.isEmpty()) {
          final String currentClassName = getCurrentClassName(stepThread);
          if (currentClassName == null || !DebuggerUtilsEx.isFiltered(currentClassName, activeFilters)) {
            // add class filters
            for (ClassFilter filter : activeFilters) {
              stepRequest.addClassExclusionFilter(filter.getPattern());
            }
          }
        }
      }

      // suspend policy to match the suspend policy of the context:
      // if all threads were suspended, then during stepping all the threads must be suspended
      // if only event thread were suspended, then only this particular thread must be suspended during stepping
      stepRequest.setSuspendPolicy(Registry.is("debugger.step.resumes.one.thread") ? EventRequest.SUSPEND_EVENT_THREAD
                                                                                   : suspendContext.getSuspendPolicy());

      if (hint != null) {
        //noinspection HardCodedStringLiteral
        stepRequest.putProperty("hint", hint);
      }
      stepRequest.enable();
    }
    catch (ObjectCollectedException ignored) {

    }
  }

  @NotNull
  static List<ClassFilter> getActiveFilters() {
    List<ClassFilter> activeFilters = new ArrayList<ClassFilter>();
    DebuggerSettings settings = DebuggerSettings.getInstance();
    if (settings.TRACING_FILTERS_ENABLED) {
      for (ClassFilter filter : settings.getSteppingFilters()) {
        if (filter.isEnabled()) {
          activeFilters.add(filter);
        }
      }
    }
    for (DebuggerClassFilterProvider provider : Extensions.getExtensions(DebuggerClassFilterProvider.EP_NAME)) {
      for (ClassFilter filter : provider.getFilters()) {
        if (filter.isEnabled()) {
          activeFilters.add(filter);
        }
      }
    }
    return activeFilters;
  }

  void deleteStepRequests(@Nullable final ThreadReference stepThread) {
    EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
    List<StepRequest> stepRequests = requestManager.stepRequests();
    if (!stepRequests.isEmpty()) {
      final List<StepRequest> toDelete = new ArrayList<StepRequest>(stepRequests.size());
      for (final StepRequest request : stepRequests) {
        ThreadReference threadReference = request.thread();
        // [jeka] on attempt to delete a request assigned to a thread with unknown status, a JDWP error occurs
        try {
          if (threadReference.status() != ThreadReference.THREAD_STATUS_UNKNOWN && (stepThread == null || stepThread.equals(threadReference))) {
            toDelete.add(request);
          }
        }
        catch (IllegalThreadStateException e) {
          LOG.info(e); // undocumented by JDI: may be thrown when querying thread status
        }
        catch (ObjectCollectedException ignored) {
        }
      }
      requestManager.deleteEventRequests(toDelete);
    }
  }

  @Nullable
  static String getCurrentClassName(ThreadReferenceProxyImpl thread) {
    try {
      if (thread != null && thread.frameCount() > 0) {
        StackFrameProxyImpl stackFrame = thread.frame(0);
        if (stackFrame != null) {
          Location location = stackFrame.location();
          ReferenceType referenceType = location == null ? null : location.declaringType();
          if (referenceType != null) {
            return referenceType.name();
          }
        }
      }
    }
    catch (EvaluateException ignored) {
    }
    return null;
  }

  private VirtualMachine createVirtualMachineInt() throws ExecutionException {
    try {
      if (myArguments != null) {
        throw new IOException(DebuggerBundle.message("error.debugger.already.listening"));
      }

      final String address = myConnection.getAddress();
      if (myConnection.isServerMode()) {
        ListeningConnector connector = (ListeningConnector)findConnector(
          myConnection.isUseSockets() ? SOCKET_LISTENING_CONNECTOR_NAME : SHMEM_LISTENING_CONNECTOR_NAME);
        if (connector == null) {
          throw new CantRunException(DebuggerBundle.message("error.debug.connector.not.found", DebuggerBundle.getTransportName(myConnection)));
        }
        myArguments = connector.defaultArguments();
        if (myArguments == null) {
          throw new CantRunException(DebuggerBundle.message("error.no.debug.listen.port"));
        }

        if (address == null) {
          throw new CantRunException(DebuggerBundle.message("error.no.debug.listen.port"));
        }
        // negative port number means the caller leaves to debugger to decide at which port to listen
        //noinspection HardCodedStringLiteral
        final Connector.Argument portArg = myConnection.isUseSockets() ? myArguments.get("port") : myArguments.get("name");
        if (portArg != null) {
          portArg.setValue(address);
        }
        //noinspection HardCodedStringLiteral
        final Connector.Argument timeoutArg = myArguments.get("timeout");
        if (timeoutArg != null) {
          timeoutArg.setValue("0"); // wait forever
        }
        connector.startListening(myArguments);
        myDebugProcessDispatcher.getMulticaster().connectorIsReady();
        try {
          return connector.accept(myArguments);
        }
        finally {
          if(myArguments != null) {
            try {
              connector.stopListening(myArguments);
            }
            catch (IllegalArgumentException ignored) {
              // ignored
            }
            catch (IllegalConnectorArgumentsException ignored) {
              // ignored
            }
          }
        }
      }
      else { // is client mode, should attach to already running process
        AttachingConnector connector = (AttachingConnector)findConnector(
          myConnection.isUseSockets() ? SOCKET_ATTACHING_CONNECTOR_NAME : SHMEM_ATTACHING_CONNECTOR_NAME
        );

        if (connector == null) {
          throw new CantRunException( DebuggerBundle.message("error.debug.connector.not.found", DebuggerBundle.getTransportName(myConnection)));
        }
        myArguments = connector.defaultArguments();
        if (myConnection.isUseSockets()) {
          //noinspection HardCodedStringLiteral
          final Connector.Argument hostnameArg = myArguments.get("hostname");
          if (hostnameArg != null && myConnection.getHostName() != null) {
            hostnameArg.setValue(myConnection.getHostName());
          }
          if (address == null) {
            throw new CantRunException(DebuggerBundle.message("error.no.debug.attach.port"));
          }
          //noinspection HardCodedStringLiteral
          final Connector.Argument portArg = myArguments.get("port");
          if (portArg != null) {
            portArg.setValue(address);
          }
        }
        else {
          if (address == null) {
            throw new CantRunException(DebuggerBundle.message("error.no.shmem.address"));
          }
          //noinspection HardCodedStringLiteral
          final Connector.Argument nameArg = myArguments.get("name");
          if (nameArg != null) {
            nameArg.setValue(address);
          }
        }
        //noinspection HardCodedStringLiteral
        final Connector.Argument timeoutArg = myArguments.get("timeout");
        if (timeoutArg != null) {
          timeoutArg.setValue("0"); // wait forever
        }

        myDebugProcessDispatcher.getMulticaster().connectorIsReady();
        try {
          return connector.attach(myArguments);
        }
        catch (IllegalArgumentException e) {
          throw new CantRunException(e.getLocalizedMessage());
        }
      }
    }
    catch (IOException e) {
      throw new ExecutionException(processIOException(e, DebuggerBundle.getAddressDisplayName(myConnection)), e);
    }
    catch (IllegalConnectorArgumentsException e) {
      throw new ExecutionException(processError(e), e);
    }
    finally {
      myArguments = null;
      myConnectionService = null;
    }
  }

  public void showStatusText(final String text) {
    if (!myStatusUpdateAlarm.isDisposed()) {
      myStatusUpdateAlarm.cancelAllRequests();
      myStatusUpdateAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          StatusBarUtil.setStatusBarInfo(myProject, text);
        }
      }, 50);
    }
  }

  static Connector findConnector(String connectorName) throws ExecutionException {
    VirtualMachineManager virtualMachineManager;
    try {
      virtualMachineManager = Bootstrap.virtualMachineManager();
    }
    catch (Error e) {
      final String error = e.getClass().getName() + " : " + e.getLocalizedMessage();
      throw new ExecutionException(DebuggerBundle.message("debugger.jdi.bootstrap.error", error));
    }
    List connectors;
    if (SOCKET_ATTACHING_CONNECTOR_NAME.equals(connectorName) || SHMEM_ATTACHING_CONNECTOR_NAME.equals(connectorName)) {
      connectors = virtualMachineManager.attachingConnectors();
    }
    else if (SOCKET_LISTENING_CONNECTOR_NAME.equals(connectorName) || SHMEM_LISTENING_CONNECTOR_NAME.equals(connectorName)) {
      connectors = virtualMachineManager.listeningConnectors();
    }
    else {
      return null;
    }
    for (Object connector1 : connectors) {
      Connector connector = (Connector)connector1;
      if (connectorName.equals(connector.name())) {
        return connector;
      }
    }
    return null;
  }

  private void checkVirtualMachineVersion(VirtualMachine vm) {
    final String version = vm.version();
    if ("1.4.0".equals(version)) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showMessageDialog(
            getProject(),
            DebuggerBundle.message("warning.jdk140.unstable"), DebuggerBundle.message("title.jdk140.unstable"), Messages.getWarningIcon()
          );
        }
      });
    }
  }

  /*Event dispatching*/
  public void addEvaluationListener(EvaluationListener evaluationListener) {
    myEvaluationDispatcher.addListener(evaluationListener);
  }

  public void removeEvaluationListener(EvaluationListener evaluationListener) {
    myEvaluationDispatcher.removeListener(evaluationListener);
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
    return myState.get() == STATE_INITIAL;
  }

  @Override
  public boolean isAttached() {
    return myState.get() == STATE_ATTACHED;
  }

  @Override
  public boolean isDetached() {
    return myState.get() == STATE_DETACHED;
  }

  @Override
  public boolean isDetaching() {
    return myState.get() == STATE_DETACHING;
  }

  @Override
  public RequestManagerImpl getRequestsManager() {
    return myRequestManager;
  }

  @Override
  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final VirtualMachineProxyImpl vm = myVirtualMachineProxy;
    if (vm == null) {
      throw new VMDisconnectedException();
    }
    return vm;
  }

  @Override
  public void appendPositionManager(final PositionManager positionManager) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager.appendPositionManager(positionManager);
  }

  private volatile RunToCursorBreakpoint myRunToCursorBreakpoint;

  public void cancelRunToCursorBreakpoint() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final RunToCursorBreakpoint runToCursorBreakpoint = myRunToCursorBreakpoint;
    if (runToCursorBreakpoint != null) {
      myRunToCursorBreakpoint = null;
      getRequestsManager().deleteRequest(runToCursorBreakpoint);
      if (runToCursorBreakpoint.isRestoreBreakpoints()) {
        final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager();
        breakpointManager.enableBreakpoints(this);
      }
    }
  }

  protected void closeProcess(boolean closedByUser) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if (myState.compareAndSet(STATE_INITIAL, STATE_DETACHING) || myState.compareAndSet(STATE_ATTACHED, STATE_DETACHING)) {
      try {
        getManagerThread().close();
      }
      finally {
        final VirtualMachineProxyImpl vm = myVirtualMachineProxy;
        myVirtualMachineProxy = null;
        myPositionManager = null;
        myReturnValueWatcher = null;
        myNodeRenderersMap.clear();
        myRenderers.clear();
        DebuggerUtils.cleanupAfterProcessFinish(this);
        myState.set(STATE_DETACHED);
        try {
          myDebugProcessDispatcher.getMulticaster().processDetached(this, closedByUser);
        }
        finally {
          //if (DebuggerSettings.getInstance().UNMUTE_ON_STOP) {
          //  XDebugSession session = mySession.getXDebugSession();
          //  if (session != null) {
          //    session.setBreakpointMuted(false);
          //  }
          //}
          if (vm != null) {
            try {
              vm.dispose(); // to be on the safe side ensure that VM mirror, if present, is disposed and invalidated
            }
            catch (Throwable ignored) {
            }
          }
          myWaitFor.up();
        }
      }

    }
  }

  private static String formatMessage(String message) {
    final int lineLength = 90;
    StringBuilder buf = new StringBuilder(message.length());
    int index = 0;
    while (index < message.length()) {
      buf.append(message.substring(index, Math.min(index + lineLength, message.length()))).append('\n');
      index += lineLength;
    }
    return buf.toString();
  }

  public static String processError(Exception e) {
    String message;

    if (e instanceof VMStartException) {
      VMStartException e1 = (VMStartException)e;
      message = e1.getLocalizedMessage();
    }
    else if (e instanceof IllegalConnectorArgumentsException) {
      IllegalConnectorArgumentsException e1 = (IllegalConnectorArgumentsException)e;
      final List<String> invalidArgumentNames = e1.argumentNames();
      message = formatMessage(DebuggerBundle.message("error.invalid.argument", invalidArgumentNames.size()) + ": "+ e1.getLocalizedMessage()) + invalidArgumentNames;
      if (LOG.isDebugEnabled()) {
        LOG.debug(e1);
      }
    }
    else if (e instanceof CantRunException) {
      message = e.getLocalizedMessage();
    }
    else if (e instanceof VMDisconnectedException) {
      message = DebuggerBundle.message("error.vm.disconnected");
    }
    else if (e instanceof IOException) {
      message = processIOException((IOException)e, null);
    }
    else if (e instanceof ExecutionException) {
      message = e.getLocalizedMessage();
    }
    else  {
      message = DebuggerBundle.message("error.exception.while.connecting", e.getClass().getName(), e.getLocalizedMessage());
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
    }
    return message;
  }

  @NotNull
  public static String processIOException(@NotNull IOException e, @Nullable String address) {
    if (e instanceof UnknownHostException) {
      return DebuggerBundle.message("error.unknown.host") + (address != null ? " (" + address + ")" : "") + ":\n" + e.getLocalizedMessage();
    }

    String message;
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append(DebuggerBundle.message("error.cannot.open.debugger.port"));
      if (address != null) {
        buf.append(" (").append(address).append(")");
      }
      buf.append(": ");
      buf.append(e.getClass().getName()).append(" ");
      final String localizedMessage = e.getLocalizedMessage();
      if (!StringUtil.isEmpty(localizedMessage)) {
        buf.append('"');
        buf.append(localizedMessage);
        buf.append('"');
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      message = buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
    return message;
  }

  public void dispose() {
    NodeRendererSettings.getInstance().removeListener(mySettingsListener);
    Disposer.dispose(myDisposable);
    myRequestManager.setFilterThread(null);
  }

  @Override
  public DebuggerManagerThreadImpl getManagerThread() {
    return myDebuggerManagerThread;
  }

  private static int getInvokePolicy(SuspendContext suspendContext) {
    //return ThreadReference.INVOKE_SINGLE_THREADED;
    return suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD ? ObjectReference.INVOKE_SINGLE_THREADED : 0;
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
    private final List myArgs;

    protected InvokeCommand(@NotNull Method method, @NotNull List args) {
      myMethod = method;
      if (!args.isEmpty()) {
        myArgs = new ArrayList(args);
      }
      else {
        myArgs = args;
      }
    }

    public String toString() {
      return "INVOKE: " + super.toString();
    }

    protected abstract E invokeMethod(int invokePolicy, Method method, final List args) throws InvocationException,
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
      catch (InvocationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InvalidTypeException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (ObjectCollectedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (UnsupportedOperationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InternalException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      finally {
        suspendContext.setIsEvaluating(null);
        if (resumeData != null) {
          SuspendManagerUtil.restoreAfterResume(suspendContext, resumeData);
        }
        for (SuspendContextImpl suspendingContext : mySuspendManager.getEventContexts()) {
          if (suspendingContexts.contains(suspendingContext) && !suspendingContext.isEvaluating() && !suspendingContext.suspends(invokeThread)) {
            mySuspendManager.suspendThread(suspendingContext, invokeThread);
          }
        }

        if (LOG.isDebugEnabled()) {
          LOG.debug("getVirtualMachine().clearCaches()");
        }
        getVirtualMachineProxy().clearCaches();
        afterMethodInvocation(suspendContext, internalEvaluate);

        myEvaluationDispatcher.getMulticaster().evaluationFinished(suspendContext);
      }
    }

    private E invokeMethodAndFork(final SuspendContextImpl context) throws InvocationException,
                                                                           ClassNotLoadedException,
                                                                           IncompatibleThreadStateException,
                                                                           InvalidTypeException {
      final int invokePolicy = getInvokePolicy(context);
      final Exception[] exception = new Exception[1];
      final Value[] result = new Value[1];
      getManagerThread().startLongProcessAndFork(new Runnable() {
        @Override
        public void run() {
          ThreadReferenceProxyImpl thread = context.getThread();
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
                int lastIndex = myArgs.size() - 1;
                if (lastIndex >= 0) {
                  final Object lastArg = myArgs.get(lastIndex);
                  if (lastArg instanceof ArrayReference) {
                    final ArrayReference arrayRef = (ArrayReference)lastArg;
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

              if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
                // ensure args are not collected
                for (Object arg : myArgs) {
                  if (arg instanceof ObjectReference) {
                    ((ObjectReference)arg).disableCollection();
                  }
                }
              }

              // workaround for jdi hang in trace mode
              if (!StringUtil.isEmpty(ourTrace)) {
                for (Object arg : myArgs) {
                  //noinspection ResultOfMethodCallIgnored
                  arg.toString();
                }
              }

              result[0] = invokeMethod(invokePolicy, myMethod, myArgs);
            }
            finally {
              //  assertThreadSuspended(thread, context);
              if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
                // ensure args are not collected
                for (Object arg : myArgs) {
                  if (arg instanceof ObjectReference) {
                    ((ObjectReference)arg).enableCollection();
                  }
                }
              }
            }
          }
          catch (Exception e) {
            exception[0] = e;
          }
        }
      });

      if (exception[0] != null) {
        if (exception[0] instanceof InvocationException) {
          throw (InvocationException)exception[0];
        }
        else if (exception[0] instanceof ClassNotLoadedException) {
          throw (ClassNotLoadedException)exception[0];
        }
        else if (exception[0] instanceof IncompatibleThreadStateException) {
          throw (IncompatibleThreadStateException)exception[0];
        }
        else if (exception[0] instanceof InvalidTypeException) {
          throw (InvalidTypeException)exception[0];
        }
        else if (exception[0] instanceof RuntimeException) {
          throw (RuntimeException)exception[0];
        }
        else {
          LOG.error("Unexpected exception", new Throwable().initCause(exception[0]));
        }
      }

      return (E)result[0];
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
  public Value invokeMethod(@NotNull EvaluationContext evaluationContext, @NotNull ObjectReference objRef, @NotNull Method method, final List args) throws EvaluateException {
    return invokeInstanceMethod(evaluationContext, objRef, method, args, 0);
  }

  @Override
  public Value invokeInstanceMethod(@NotNull EvaluationContext evaluationContext,
                                    @NotNull final ObjectReference objRef,
                                    @NotNull Method method,
                                    @NotNull List args,
                                    final int invocationOptions) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    return new InvokeCommand<Value>(method, args) {
      @Override
      protected Value invokeMethod(int invokePolicy, Method method, final List args) throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoke " + method.name());
        }
        return objRef.invokeMethod(thread, method, args, invokePolicy | invocationOptions);
      }
    }.start((EvaluationContextImpl)evaluationContext, false);
  }

  private static ThreadReference getEvaluationThread(final EvaluationContext evaluationContext) throws EvaluateException {
    ThreadReferenceProxy evaluationThread = evaluationContext.getSuspendContext().getThread();
    if(evaluationThread == null) {
      throw EvaluateExceptionUtil.NULL_STACK_FRAME;
    }
    return evaluationThread.getThreadReference();
  }

  @Override
  public Value invokeMethod(final EvaluationContext evaluationContext, final ClassType classType,
                            final Method method,
                            final List args) throws EvaluateException {
    return invokeMethod(evaluationContext, classType, method, args, false);
  }

  public Value invokeMethod(@NotNull EvaluationContext evaluationContext,
                            @NotNull final ClassType classType,
                            @NotNull Method method,
                            @NotNull List args,
                            boolean internalEvaluate) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    return new InvokeCommand<Value>(method, args) {
      @Override
      protected Value invokeMethod(int invokePolicy, Method method, List args) throws InvocationException,
                                                                             ClassNotLoadedException,
                                                                             IncompatibleThreadStateException,
                                                                             InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoke " + method.name());
        }
        return classType.invokeMethod(thread, method, args, invokePolicy);
      }
    }.start((EvaluationContextImpl)evaluationContext, internalEvaluate);
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
                                     @NotNull List args) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    InvokeCommand<ObjectReference> invokeCommand = new InvokeCommand<ObjectReference>(method, args) {
      @Override
      protected ObjectReference invokeMethod(int invokePolicy, Method method, List args) throws InvocationException,
                                                                                       ClassNotLoadedException,
                                                                                       IncompatibleThreadStateException,
                                                                                       InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("New instance " + method.name());
        }
        return classType.newInstance(thread, method, args, invokePolicy);
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, false);
  }

  public void clearCashes(int suspendPolicy) {
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
        showStatusText(DebuggerBundle.message("progress.evaluating", DebuggerUtilsEx.methodName(method)));
      }
      else {
        showStatusText(DebuggerBundle.message("title.evaluating"));
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
  public ReferenceType findClass(EvaluationContext evaluationContext, String className,
                                 ClassLoaderReference classLoader) throws EvaluateException {
    try {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      final VirtualMachineProxyImpl vmProxy = getVirtualMachineProxy();
      if (vmProxy == null) {
        throw new VMDisconnectedException();
      }
      ReferenceType result = null;
      for (final ReferenceType refType : vmProxy.classesByName(className)) {
        if (refType.isPrepared() && isVisibleFromClassLoader(classLoader, refType)) {
          result = refType;
          break;
        }
      }
      final EvaluationContextImpl evalContext = (EvaluationContextImpl)evaluationContext;
      if (result == null && evalContext.isAutoLoadClasses()) {
        return loadClass(evalContext, className, classLoader);
      }
      return result;
    }
    catch (InvocationException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (ClassNotLoadedException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (IncompatibleThreadStateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (InvalidTypeException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
  }

  private static boolean isVisibleFromClassLoader(final ClassLoaderReference fromLoader, final ReferenceType refType) {
    // IMPORTANT! Even if the refType is already loaded by some parent or bootstrap loader, it may not be visible from the given loader.
    // For example because there were no accesses yet from this loader to this class. So the loader is not in the list of "initialing" loaders
    // for this refType and the refType is not visible to the loader.
    // Attempt to evaluate method with this refType will yield ClassNotLoadedException.
    // The only way to say for sure whether the class is _visible_ to the given loader, is to use the following API call
    return fromLoader == null || fromLoader.equals(refType.classLoader()) || fromLoader.visibleClasses().contains(refType);
  }

  private static String reformatArrayName(String className) {
    if (className.indexOf('[') == -1) return className;

    int dims = 0;
    while (className.endsWith("[]")) {
      className = className.substring(0, className.length() - 2);
      dims++;
    }

    StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      for (int i = 0; i < dims; i++) {
        buffer.append('[');
      }
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
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral", "SpellCheckingInspection"})
  public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String qName, ClassLoaderReference classLoader)
    throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException, EvaluateException {

    DebuggerManagerThreadImpl.assertIsManagerThread();
    qName = reformatArrayName(qName);
    ReferenceType refType = null;
    VirtualMachineProxyImpl virtualMachine = getVirtualMachineProxy();
    final List classClasses = virtualMachine.classesByName("java.lang.Class");
    if (!classClasses.isEmpty()) {
      ClassType classClassType = (ClassType)classClasses.get(0);
      final Method forNameMethod;
      if (classLoader != null) {
        //forNameMethod = classClassType.concreteMethodByName("forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
        forNameMethod = DebuggerUtils.findMethod(classClassType, "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
      }
      else {
        //forNameMethod = classClassType.concreteMethodByName("forName", "(Ljava/lang/String;)Ljava/lang/Class;");
        forNameMethod = DebuggerUtils.findMethod(classClassType, "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
      }
      final List<Mirror> args = new ArrayList<Mirror>(); // do not use unmodifiable lists because the list is modified by JPDA
      final StringReference qNameMirror = virtualMachine.mirrorOf(qName);
      args.add(qNameMirror);
      if (classLoader != null) {
        args.add(virtualMachine.mirrorOf(true));
        args.add(classLoader);
      }
      final Value value = invokeMethod(evaluationContext, classClassType, forNameMethod, args);
      if (value instanceof ClassObjectReference) {
        refType = ((ClassObjectReference)value).reflectedType();
      }
    }
    return refType;
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

  public SuspendManager getSuspendManager() {
    return mySuspendManager;
  }

  @Override
  public CompoundPositionManager getPositionManager() {
    return myPositionManager;
  }
  //ManagerCommands

  @Override
  public void stop(boolean forceTerminate) {
    getManagerThread().terminateAndInvoke(createStopCommand(forceTerminate), DebuggerManagerThreadImpl.COMMAND_TIMEOUT);
  }

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
    protected void action() throws Exception {
      if (isAttached()) {
        final VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachineProxy();
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
        stopConnecting();
      }
    }
  }

  private class StepOutCommand extends StepCommand {
    private final int myStepSize;

    public StepOutCommand(SuspendContextImpl suspendContext, int stepSize) {
      super(suspendContext);
      myStepSize = stepSize;
    }

    @Override
    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.step.out"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl thread = getContextThread();
      RequestHint hint = new RequestHint(thread, suspendContext, StepRequest.STEP_OUT);
      hint.setIgnoreFilters(mySession.shouldIgnoreSteppingFilters());
      applyThreadFilter(thread);
      final MethodReturnValueWatcher rvWatcher = myReturnValueWatcher;
      if (rvWatcher != null) {
        rvWatcher.enable(thread.getThreadReference());
      }
      doStep(suspendContext, thread, myStepSize, StepRequest.STEP_OUT, hint);
      super.contextAction();
    }
  }

  private class StepIntoCommand extends StepCommand {
    private final boolean myForcedIgnoreFilters;
    private final MethodFilter mySmartStepFilter;
    @Nullable
    private final StepIntoBreakpoint myBreakpoint;
    private final int myStepSize;

    public StepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, @Nullable final MethodFilter methodFilter,
                           int stepSize) {
      super(suspendContext);
      myForcedIgnoreFilters = ignoreFilters || methodFilter != null;
      mySmartStepFilter = methodFilter;
      myBreakpoint = methodFilter instanceof BreakpointStepMethodFilter ?
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addStepIntoBreakpoint(((BreakpointStepMethodFilter)methodFilter)) :
        null;
      myStepSize = stepSize;
    }

    @Override
    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.step.into"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl stepThread = getContextThread();
      final RequestHint hint = mySmartStepFilter != null?
                               new RequestHint(stepThread, suspendContext, mySmartStepFilter) :
                               new RequestHint(stepThread, suspendContext, StepRequest.STEP_INTO);
      hint.setResetIgnoreFilters(mySmartStepFilter != null && !mySession.shouldIgnoreSteppingFilters());
      if (myForcedIgnoreFilters) {
        try {
          mySession.setIgnoreStepFiltersFlag(stepThread.frameCount());
        }
        catch (EvaluateException e) {
          LOG.info(e);
        }
      }
      hint.setIgnoreFilters(myForcedIgnoreFilters || mySession.shouldIgnoreSteppingFilters());
      applyThreadFilter(stepThread);
      if (myBreakpoint != null) {
        myBreakpoint.setSuspendPolicy(suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD? DebuggerSettings.SUSPEND_THREAD : DebuggerSettings.SUSPEND_ALL);
        myBreakpoint.createRequest(suspendContext.getDebugProcess());
        myRunToCursorBreakpoint = myBreakpoint;
      }
      doStep(suspendContext, stepThread, myStepSize, StepRequest.STEP_INTO, hint);
      super.contextAction();
    }
  }

  private class StepOverCommand extends StepCommand {
    private final boolean myIsIgnoreBreakpoints;
    private final int myStepSize;

    public StepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints, int stepSize) {
      super(suspendContext);
      myIsIgnoreBreakpoints = ignoreBreakpoints;
      myStepSize = stepSize;
    }

    @Override
    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.step.over"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl stepThread = getContextThread();
      // need this hint while stepping over for JSR45 support:
      // several lines of generated java code may correspond to a single line in the source file,
      // from which the java code was generated
      RequestHint hint = new RequestHint(stepThread, suspendContext, StepRequest.STEP_OVER);
      hint.setRestoreBreakpoints(myIsIgnoreBreakpoints);
      hint.setIgnoreFilters(myIsIgnoreBreakpoints || mySession.shouldIgnoreSteppingFilters());

      applyThreadFilter(stepThread);

      final MethodReturnValueWatcher rvWatcher = myReturnValueWatcher;
      if (rvWatcher != null) {
        rvWatcher.enable(stepThread.getThreadReference());
      }

      doStep(suspendContext, stepThread, myStepSize, StepRequest.STEP_OVER, hint);

      if (myIsIgnoreBreakpoints) {
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().disableBreakpoints(DebugProcessImpl.this);
      }
      super.contextAction();
    }
  }

  private class RunToCursorCommand extends StepCommand {
    private final RunToCursorBreakpoint myRunToCursorBreakpoint;
    private final boolean myIgnoreBreakpoints;

    private RunToCursorCommand(SuspendContextImpl suspendContext, @NotNull XSourcePosition position, final boolean ignoreBreakpoints) {
      super(suspendContext);
      myIgnoreBreakpoints = ignoreBreakpoints;
      BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
      myRunToCursorBreakpoint = breakpointManager.addRunToCursorBreakpoint(position, ignoreBreakpoints);
    }

    @Override
    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.run.to.cursor"));
      cancelRunToCursorBreakpoint();
      if (myRunToCursorBreakpoint == null) {
        return;
      }
      if (myIgnoreBreakpoints) {
        final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
        breakpointManager.disableBreakpoints(DebugProcessImpl.this);
      }
      applyThreadFilter(getContextThread());
      final SuspendContextImpl context = getSuspendContext();
      myRunToCursorBreakpoint.setSuspendPolicy(context.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD? DebuggerSettings.SUSPEND_THREAD : DebuggerSettings.SUSPEND_ALL);
      DebugProcessImpl debugProcess = context.getDebugProcess();
      myRunToCursorBreakpoint.createRequest(debugProcess);
      DebugProcessImpl.this.myRunToCursorBreakpoint = myRunToCursorBreakpoint;

      if (debugProcess.getRequestsManager().getWarning(myRunToCursorBreakpoint) == null) {
        super.contextAction();
      }
      else {
        myDebugProcessDispatcher.getMulticaster().resumed(getSuspendContext());
        DebuggerInvocationUtil.swingInvokeLater(myProject, new Runnable() {
          @Override
          public void run() {
            SourcePosition position = myRunToCursorBreakpoint.getSourcePosition();
            String name = position != null ? position.getFile().getName() : "<No File>";
            Messages.showErrorDialog(
              DebuggerBundle.message("error.running.to.cursor.no.executable.code", name, myRunToCursorBreakpoint.getLineIndex()+1),
              UIUtil.removeMnemonic(ActionsBundle.actionText(XDebuggerActions.RUN_TO_CURSOR)));
          }
        });
      }
    }
  }

  private abstract class StepCommand extends ResumeCommand {
    public StepCommand(SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    @Override
    protected void resumeAction() {
      SuspendContextImpl context = getSuspendContext();
      if (context != null
          && Registry.is("debugger.step.resumes.one.thread")
          && context.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
        getSuspendManager().resumeThread(context, myContextThread);
      }
      else {
        super.resumeAction();
      }
    }
  }

  public abstract class ResumeCommand extends SuspendContextCommandImpl {
    protected final ThreadReferenceProxyImpl myContextThread;

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
      showStatusText(DebuggerBundle.message("status.process.resumed"));
      resumeAction();
      myDebugProcessDispatcher.getMulticaster().resumed(getSuspendContext());
    }

    protected void resumeAction() {
      getSuspendManager().resume(getSuspendContext());
    }

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
    public PauseCommand() {
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

    public ResumeThreadCommand(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl thread) {
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
        if (suspendContext.getThread() == myThread) {
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

    public FreezeThreadCommand(ThreadReferenceProxyImpl thread) {
      myThread = thread;
    }

    @Override
    protected void action() throws Exception {
      SuspendManager suspendManager = getSuspendManager();
      if (!suspendManager.isFrozen(myThread)) {
        suspendManager.freezeThread(myThread);
        SuspendContextImpl suspendContext = mySuspendManager.pushSuspendContext(EventRequest.SUSPEND_EVENT_THREAD, 0);
        suspendContext.setThread(myThread.getThreadReference());
        mySuspendManager.notifyPaused(suspendContext);
      }
    }
  }

  private class PopFrameCommand extends SuspendContextCommandImpl {
    private final StackFrameProxyImpl myStackFrame;

    public PopFrameCommand(SuspendContextImpl context, StackFrameProxyImpl frameProxy) {
      super(context);
      myStackFrame = frameProxy;
    }

    @Override
    public void contextAction() {
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

      final SuspendContextImpl suspendContext = getSuspendContext();
      if (!suspendContext.suspends(thread)) {
        suspendContext.postponeCommand(this);
        return;
      }

      if (myStackFrame.isBottom()) {
        DebuggerInvocationUtil.swingInvokeLater(myProject, new Runnable() {
          @Override
          public void run() {
            Messages.showMessageDialog(myProject, DebuggerBundle.message("error.pop.bottom.stackframe"), ActionsBundle.actionText(DebuggerActions.POP_FRAME), Messages.getErrorIcon());
          }
        });
        return;
      }

      try {
        thread.popFrames(myStackFrame);
      }
      catch (final EvaluateException e) {
        DebuggerInvocationUtil.swingInvokeLater(myProject, new Runnable() {
          @Override
          public void run() {
            Messages.showMessageDialog(myProject, DebuggerBundle.message("error.pop.stackframe", e.getLocalizedMessage()), ActionsBundle.actionText(DebuggerActions.POP_FRAME), Messages.getErrorIcon());
          }
        });
        LOG.info(e);
      }
      finally {
        getSuspendManager().popFrame(suspendContext);
      }
    }
  }

  @Override
  @NotNull
  public GlobalSearchScope getSearchScope() {
    LOG.assertTrue(mySession != null, "Accessing debug session before its initialization");
    return mySession.getSearchScope();
  }

  public void reattach(final DebugEnvironment environment) throws ExecutionException {
    ApplicationManager.getApplication().assertIsDispatchThread(); //TODO: remove this requirement
    ((XDebugSessionImpl)getXdebugProcess().getSession()).reset();
    myState.set(STATE_INITIAL);
    getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        myRequestManager.processDetached(DebugProcessImpl.this, false);
      }
    });
    myConnection = environment.getRemoteConnection();
    getManagerThread().restartIfNeeded();
    createVirtualMachine(environment.getSessionName(), environment.isPollConnection());
  }

  @Nullable
  public ExecutionResult attachVirtualMachine(final DebugEnvironment environment,
                                              final DebuggerSession session) throws ExecutionException {
    mySession = session;
    myWaitFor.down();

    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(isInInitialState());

    myConnection = environment.getRemoteConnection();

    createVirtualMachine(environment.getSessionName(), environment.isPollConnection());

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
      }
    }
    catch (ExecutionException e) {
      fail();
      throw e;
    }

    // writing to volatile field ensures the other threads will see the right values in non-volatile fields

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return executionResult;
    }

    /*
    final Alarm debugPortTimeout = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    myExecutionResult.getProcessHandler().addProcessListener(new ProcessAdapter() {
      public void processTerminated(ProcessEvent event) {
        debugPortTimeout.cancelAllRequests();
      }

      public void startNotified(ProcessEvent event) {
        debugPortTimeout.addRequest(new Runnable() {
          public void run() {
            if(isInInitialState()) {
              ApplicationManager.getApplication().schedule(new Runnable() {
                public void run() {
                  String message = DebuggerBundle.message("status.connect.failed", DebuggerBundle.getAddressDisplayName(remoteConnection), DebuggerBundle.getTransportName(remoteConnection));
                  Messages.showErrorDialog(myProject, message, DebuggerBundle.message("title.generic.debug.dialog"));
                }
              });
            }
          }
        }, LOCAL_START_TIMEOUT);
      }
    });
    */

    return executionResult;
  }

  private void fail() {
    // need this in order to prevent calling stop() twice
    if (myIsFailed.compareAndSet(false, true)) {
      stop(false);
    }
  }

  private void createVirtualMachine(final String sessionName, final boolean pollConnection) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final AtomicBoolean connectorIsReady = new AtomicBoolean(false);
    myDebugProcessDispatcher.addListener(new DebugProcessAdapter() {
      @Override
      public void connectorIsReady() {
        connectorIsReady.set(true);
        semaphore.up();
        myDebugProcessDispatcher.removeListener(this);
      }
    });

    // reload to make sure that source positions are initialized
    DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().reloadBreakpoints();

    getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        VirtualMachine vm = null;

        try {
          final long time = System.currentTimeMillis();
          while (System.currentTimeMillis() - time < LOCAL_START_TIMEOUT) {
            try {
              vm = createVirtualMachineInt();
              break;
            }
            catch (final ExecutionException e) {
              if (pollConnection && !myConnection.isServerMode() && e.getCause() instanceof IOException) {
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
                fail();
                DebuggerInvocationUtil.swingInvokeLater(myProject, new Runnable() {
                  @Override
                  public void run() {
                    // propagate exception only in case we succeeded to obtain execution result,
                    // otherwise if the error is induced by the fact that there is nothing to debug, and there is no need to show
                    // this problem to the user
                    if (myExecutionResult != null || !connectorIsReady.get()) {
                      ExecutionUtil.handleExecutionError(myProject, ToolWindowId.DEBUG, sessionName, e);
                    }
                  }
                });
                break;
              }
            }
          }
        }
        finally {
          semaphore.up();
        }

        if (vm != null) {
          final VirtualMachine vm1 = vm;
          afterProcessStarted(new Runnable() {
            @Override
            public void run() {
              getManagerThread().schedule(new DebuggerCommandImpl() {
                @Override
                protected void action() throws Exception {
                  commitVM(vm1);
                }
              });
            }
          });
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
      public void startNotified(ProcessEvent event) {
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

  public DebuggerCommandImpl createPauseCommand() {
    return new PauseCommand();
  }

  public ResumeCommand createResumeCommand(SuspendContextImpl suspendContext) {
    return createResumeCommand(suspendContext, PrioritizedTask.Priority.HIGH);
  }

  public ResumeCommand createResumeCommand(SuspendContextImpl suspendContext, final PrioritizedTask.Priority priority) {
    final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager();
    return new ResumeCommand(suspendContext) {
      @Override
      public void contextAction() {
        breakpointManager.applyThreadFilter(DebugProcessImpl.this, null); // clear the filter on resume
        super.contextAction();
      }

      @Override
      public Priority getPriority() {
        return priority;
      }
    };
  }

  public ResumeCommand createStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
    return createStepOverCommand(suspendContext, ignoreBreakpoints, StepRequest.STEP_LINE);
  }

  public ResumeCommand createStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints, int stepSize) {
    return new StepOverCommand(suspendContext, ignoreBreakpoints, stepSize);
  }

  public ResumeCommand createStepOutCommand(SuspendContextImpl suspendContext) {
    return createStepOutCommand(suspendContext, StepRequest.STEP_LINE);
  }

  public ResumeCommand createStepOutCommand(SuspendContextImpl suspendContext, int stepSize) {
    return new StepOutCommand(suspendContext, stepSize);
  }

  public ResumeCommand createStepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, final MethodFilter smartStepFilter) {
    return createStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, StepRequest.STEP_LINE);
  }

  public ResumeCommand createStepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, final MethodFilter smartStepFilter,
                                             int stepSize) {
    return new StepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, stepSize);
  }

  public ResumeCommand createRunToCursorCommand(SuspendContextImpl suspendContext,
                                                @NotNull XSourcePosition position,
                                                boolean ignoreBreakpoints)
    throws EvaluateException {
    RunToCursorCommand runToCursorCommand = new RunToCursorCommand(suspendContext, position, ignoreBreakpoints);
    if (runToCursorCommand.myRunToCursorBreakpoint == null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(position.getFile());
      throw new EvaluateException(DebuggerBundle.message("error.running.to.cursor.no.executable.code", psiFile != null? psiFile.getName() : "<No File>",
                                                         position.getLine()), null);
    }
    return runToCursorCommand;
  }

  public DebuggerCommandImpl createFreezeThreadCommand(ThreadReferenceProxyImpl thread) {
    return new FreezeThreadCommand(thread);
  }

  public SuspendContextCommandImpl createResumeThreadCommand(SuspendContextImpl suspendContext, ThreadReferenceProxyImpl thread) {
    return new ResumeThreadCommand(suspendContext, thread);
  }

  public SuspendContextCommandImpl createPopFrameCommand(DebuggerContextImpl context, StackFrameProxyImpl stackFrame) {
    final SuspendContextImpl contextByThread =
      SuspendManagerUtil.findContextByThread(context.getDebugProcess().getSuspendManager(), stackFrame.threadProxy());
    return new PopFrameCommand(contextByThread, stackFrame);
  }

  //public void setBreakpointsMuted(final boolean muted) {
  //  XDebugSession session = mySession.getXDebugSession();
  //  if (isAttached()) {
  //    getManagerThread().schedule(new DebuggerCommandImpl() {
  //      @Override
  //      protected void action() throws Exception {
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
}
