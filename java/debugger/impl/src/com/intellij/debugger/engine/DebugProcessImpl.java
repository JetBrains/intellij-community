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
package com.intellij.debugger.engine;

import com.intellij.Patches;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.apiAdapters.ConnectionServiceWrapper;
import com.intellij.debugger.apiAdapters.TransportServiceWrapper;
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
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.ExceptionFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.DebuggerClassFilterProvider;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.concurrency.Semaphore;
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

public abstract class DebugProcessImpl implements DebugProcess {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebugProcessImpl");

  static final @NonNls String  SOCKET_ATTACHING_CONNECTOR_NAME = "com.sun.jdi.SocketAttach";
  static final @NonNls String SHMEM_ATTACHING_CONNECTOR_NAME = "com.sun.jdi.SharedMemoryAttach";
  static final @NonNls String SOCKET_LISTENING_CONNECTOR_NAME = "com.sun.jdi.SocketListen";
  static final @NonNls String SHMEM_LISTENING_CONNECTOR_NAME = "com.sun.jdi.SharedMemoryListen";

  private final Project myProject;
  private final RequestManagerImpl myRequestManager;

  private VirtualMachineProxyImpl myVirtualMachineProxy = null;
  protected EventDispatcher<DebugProcessListener> myDebugProcessDispatcher = EventDispatcher.create(DebugProcessListener.class);
  protected EventDispatcher<EvaluationListener> myEvaluationDispatcher = EventDispatcher.create(EvaluationListener.class);

  private final List<ProcessListener> myProcessListeners = new ArrayList<ProcessListener>();

  protected static final int STATE_INITIAL   = 0;
  protected static final int STATE_ATTACHED  = 1;
  protected static final int STATE_DETACHING = 2;
  protected static final int STATE_DETACHED  = 3;
  protected final AtomicInteger myState = new AtomicInteger(STATE_INITIAL);

  private ExecutionResult  myExecutionResult;
  private RemoteConnection myConnection;

  private ConnectionServiceWrapper myConnectionService;
  private Map<String, Connector.Argument> myArguments;

  private final List<NodeRenderer> myRenderers = new ArrayList<NodeRenderer>();
  private final Map<Type, NodeRenderer>  myNodeRederersMap = new com.intellij.util.containers.HashMap<Type, NodeRenderer>();
  private final NodeRendererSettingsListener  mySettingsListener = new NodeRendererSettingsListener() {
      public void renderersChanged() {
        myNodeRederersMap.clear();
        myRenderers.clear();
        loadRenderers();
      }
    };

  private final SuspendManagerImpl mySuspendManager = new SuspendManagerImpl(this);
  protected CompoundPositionManager myPositionManager = null;
  private volatile DebuggerManagerThreadImpl myDebuggerManagerThread;
  private final HashMap myUserData = new HashMap();
  private static final int LOCAL_START_TIMEOUT = 30000;

  private final Semaphore myWaitFor = new Semaphore();
  private final AtomicBoolean myBreakpointsMuted = new AtomicBoolean(false);
  private boolean myIsFailed = false;
  protected DebuggerSession mySession;
  protected @Nullable MethodReturnValueWatcher myReturnValueWatcher;
  private final Alarm myStatusUpdateAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  protected DebugProcessImpl(Project project) {
    myProject = project;
    myRequestManager = new RequestManagerImpl(this);
    NodeRendererSettings.getInstance().addListener(mySettingsListener);
    loadRenderers();
  }

  private void loadRenderers() {
    getManagerThread().invoke(new DebuggerCommandImpl() {
      protected void action() throws Exception {
        final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();
        for (final NodeRenderer renderer : rendererSettings.getAllRenderers()) {
          if (renderer.isEnabled()) {
            myRenderers.add(renderer);
          }
        }
      }
    });
  }

  @Nullable
  public Pair<Method, Value> getLastExecutedMethod() {
    if (myReturnValueWatcher == null) {
      return null;
    }
    final Method method = myReturnValueWatcher.getLastExecutedMethod();
    if (method == null) {
      return null;
    }
    return new Pair<Method, Value>(method, myReturnValueWatcher.getLastMethodReturnValue());
  }

  public void setWatchMethodReturnValuesEnabled(boolean enabled) {
    if (myReturnValueWatcher != null) {
      myReturnValueWatcher.setFeatureEnabled(enabled);
    }
  }

  public boolean isWatchMethodReturnValuesEnabled() {
    return myReturnValueWatcher != null && myReturnValueWatcher.isFeatureEnabled();
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

    NodeRenderer renderer = myNodeRederersMap.get(type);
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
      myNodeRederersMap.put(type, renderer);
    }

    return renderer;
  }

  public final NodeRenderer getDefaultRenderer(Value value) {
    return getDefaultRenderer((value != null) ? value.type() : (Type)null);
  }

  public final NodeRenderer getDefaultRenderer(Type type) {
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

    String trace = System.getProperty("idea.debugger.trace");
    if (trace != null) {
      int mask = 0;
      StringTokenizer tokenizer = new StringTokenizer(trace);
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

    Map arguments = myArguments;
    try {
      if (arguments == null) {
        return;
      }
      if(myConnection.isServerMode()) {
        ListeningConnector connector = (ListeningConnector)findConnector(SOCKET_LISTENING_CONNECTOR_NAME);
        if (connector == null) {
          LOG.error("Cannot find connector: " + SOCKET_LISTENING_CONNECTOR_NAME);
        }
        connector.stopListening(arguments);
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

  public void printToConsole(final String text) {
    myExecutionResult.getProcessHandler().notifyTextAvailable(text, ProcessOutputTypes.SYSTEM);
  }

  /**
   *
   * @param suspendContext
   * @param stepThread
   * @param depth
   * @param hint may be null
   */
  protected void doStep(final SuspendContextImpl suspendContext, final ThreadReferenceProxyImpl stepThread, int depth, RequestHint hint) {
    if (stepThread == null) {
      return;
    }
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("DO_STEP: creating step request for " + stepThread.getThreadReference());
      }
      deleteStepRequests();
      EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
      StepRequest stepRequest = requestManager.createStepRequest(stepThread.getThreadReference(), StepRequest.STEP_LINE, depth);
      DebuggerSettings settings = DebuggerSettings.getInstance();
      if (!(hint != null && hint.isIgnoreFilters()) /*&& depth == StepRequest.STEP_INTO*/) {
        final List<ClassFilter> activeFilters = new ArrayList<ClassFilter>();
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
      stepRequest.setSuspendPolicy(suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD? EventRequest.SUSPEND_EVENT_THREAD : EventRequest.SUSPEND_ALL);

      if (hint != null) {
        //noinspection HardCodedStringLiteral
        stepRequest.putProperty("hint", hint);
      }
      stepRequest.enable();
    }
    catch (ObjectCollectedException ignored) {

    }
  }

  void deleteStepRequests() {
    EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
    List<StepRequest> stepRequests = requestManager.stepRequests();
    if (stepRequests.size() > 0) {
      final List<StepRequest> toDelete = new ArrayList<StepRequest>(stepRequests.size());
      for (final StepRequest request : stepRequests) {
        ThreadReference threadReference = request.thread();
        // [jeka] on attempt to delete a request assigned to a thread with unknown status, a JDWP error occures
        if (threadReference.status() != ThreadReference.THREAD_STATUS_UNKNOWN) {
          toDelete.add(request);
        }
      }
      requestManager.deleteEventRequests(toDelete);
    }
  }

  @Nullable
  private static String getCurrentClassName(ThreadReferenceProxyImpl thread) {
    try {
      if (thread != null && thread.frameCount() > 0) {
        StackFrameProxyImpl stackFrame = thread.frame(0);
        Location location = stackFrame.location();
        ReferenceType referenceType = location.declaringType();
        if (referenceType != null) {
          return referenceType.name();
        }
      }
    }
    catch (EvaluateException e) {
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
        // negative port number means the caller leaves to debugger to decide at which hport to listen
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
            catch (IllegalArgumentException e) {
              // ignored
            }
            catch (IllegalConnectorArgumentsException e) {
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
          final Connector.Argument hostnameArg = (Connector.Argument)myArguments.get("hostname");
          if (hostnameArg != null && myConnection.getHostName() != null) {
            hostnameArg.setValue(myConnection.getHostName());
          }
          if (address == null) {
            throw new CantRunException(DebuggerBundle.message("error.no.debug.attach.port"));
          }
          //noinspection HardCodedStringLiteral
          final Connector.Argument portArg = (Connector.Argument)myArguments.get("port");
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
          if(SOCKET_ATTACHING_CONNECTOR_NAME.equals(connector.name()) && Patches.SUN_BUG_338675) {
            String portString = myConnection.getAddress();
            String hostString = myConnection.getHostName();

            if (hostString == null || hostString.length() == 0) {
              //noinspection HardCodedStringLiteral
              hostString = "localhost";
            }
            hostString = hostString + ":";

            final TransportServiceWrapper transportServiceWrapper = TransportServiceWrapper.getTransportService(connector.transport());
            myConnectionService = transportServiceWrapper.attach(hostString + portString);
            return myConnectionService.createVirtualMachine();
          }
          else {
            return connector.attach(myArguments);
          }
        }
        catch (IllegalArgumentException e) {
          throw new CantRunException(e.getLocalizedMessage());
        }
      }
    }
    catch (IOException e) {
      throw new ExecutionException(processError(e), e);
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
    myStatusUpdateAlarm.cancelAllRequests();
    myStatusUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        final WindowManager wm = WindowManager.getInstance();
        if (wm != null) {
          wm.getStatusBar(myProject).setInfo(text);
        }
      }
    }, 50);
  }

  static Connector findConnector(String connectorName) throws ExecutionException {
    VirtualMachineManager virtualMachineManager = null;
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
    for (Iterator it = connectors.iterator(); it.hasNext();) {
      Connector connector = (Connector)it.next();
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

  public void addDebugProcessListener(DebugProcessListener listener) {
    myDebugProcessDispatcher.addListener(listener);
  }

  public void removeDebugProcessListener(DebugProcessListener listener) {
    myDebugProcessDispatcher.removeListener(listener);
  }

  public void addProcessListener(ProcessListener processListener) {
    synchronized(myProcessListeners) {
      if(getExecutionResult() != null) {
        getExecutionResult().getProcessHandler().addProcessListener(processListener);
      }
      else {
        myProcessListeners.add(processListener);
      }
    }
  }

  public void removeProcessListener(ProcessListener processListener) {
    synchronized (myProcessListeners) {
      if(getExecutionResult() != null) {
        getExecutionResult().getProcessHandler().removeProcessListener(processListener);
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

  public ExecutionResult getExecutionResult() {
    return myExecutionResult;
  }

  public <T> T getUserData(Key<T> key) {
    return (T)myUserData.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    myUserData.put(key, value);
  }

  public Project getProject() {
    return myProject;
  }

  public boolean canRedefineClasses() {
    return myVirtualMachineProxy != null && myVirtualMachineProxy.canRedefineClasses();
  }

  public boolean canWatchFieldModification() {
    return myVirtualMachineProxy != null && myVirtualMachineProxy.canWatchFieldModification();
  }

  public boolean isInInitialState() {
    return myState.get() == STATE_INITIAL;
  }

  public boolean isAttached() {
    return myState.get() == STATE_ATTACHED;
  }

  public boolean isDetached() {
    return myState.get() == STATE_DETACHED;
  }

  public boolean isDetaching() {
    return myState.get() == STATE_DETACHING;
  }

  public RequestManagerImpl getRequestsManager() {
    return myRequestManager;
  }

  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myVirtualMachineProxy == null) {
      throw new VMDisconnectedException();
    }
    return myVirtualMachineProxy;
  }

  public void appendPositionManager(final PositionManager positionManager) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager.appendPositionManager(positionManager);
    DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().updateBreakpoints(this);
  }

  private RunToCursorBreakpoint myRunToCursorBreakpoint;

  public void cancelRunToCursorBreakpoint() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myRunToCursorBreakpoint != null) {
      getRequestsManager().deleteRequest(myRunToCursorBreakpoint);
      myRunToCursorBreakpoint.delete();
      if (myRunToCursorBreakpoint.isRestoreBreakpoints()) {
        final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager();
        breakpointManager.enableBreakpoints(this);
      }
      myRunToCursorBreakpoint = null;
    }
  }

  protected void closeProcess(boolean closedByUser) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    
    if (myState.compareAndSet(STATE_INITIAL, STATE_DETACHING) || myState.compareAndSet(STATE_ATTACHED, STATE_DETACHING)) {
      try {
        getManagerThread().close();
      }
      finally {
        myVirtualMachineProxy = null;
        myPositionManager = null;
        myState.set(STATE_DETACHED);
        try {
          myDebugProcessDispatcher.getMulticaster().processDetached(this, closedByUser);
        }
        finally {
          setBreakpointsMuted(false);
          myWaitFor.up();
        }
      }

    }
  }

  private static String formatMessage(String message) {
    final int lineLength = 90;
    StringBuffer buf = new StringBuffer(message.length());
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
    else if (e instanceof UnknownHostException) {
      message = DebuggerBundle.message("error.unknown.host") + ":\n" + e.getLocalizedMessage();
    }
    else if (e instanceof IOException) {
      IOException e1 = (IOException)e;
      final StringBuilder buf = StringBuilderSpinAllocator.alloc();
      try {
        buf.append(DebuggerBundle.message("error.cannot.open.debugger.port")).append(" : ");
        buf.append(e1.getClass().getName() + " ");
        final String localizedMessage = e1.getLocalizedMessage();
        if (localizedMessage != null && localizedMessage.length() > 0) {
          buf.append('"');
          buf.append(localizedMessage);
          buf.append('"');
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug(e1);
        }
        message = buf.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(buf);
      }
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

  public void dispose() {
    NodeRendererSettings.getInstance().removeListener(mySettingsListener);
    myStatusUpdateAlarm.dispose();
  }

  public DebuggerManagerThreadImpl getManagerThread() {
    if (myDebuggerManagerThread == null) {
      synchronized (this) {
        if (myDebuggerManagerThread == null) {
          myDebuggerManagerThread = new DebuggerManagerThreadImpl();
        }
      }
    }
    return myDebuggerManagerThread;
  }

  private static int getInvokePolicy(SuspendContext suspendContext) {
    //return ThreadReference.INVOKE_SINGLE_THREADED;
    return suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD ? ThreadReference.INVOKE_SINGLE_THREADED : 0;
  }

  public void waitFor() {
    LOG.assertTrue(!DebuggerManagerThreadImpl.isManagerThread());
    myWaitFor.waitFor();
  }

  public void waitFor(long timeout) {
    LOG.assertTrue(!DebuggerManagerThreadImpl.isManagerThread());
    myWaitFor.waitFor(timeout);
  }

  private abstract class InvokeCommand <E extends Value> {
    private final List myArgs;

    protected InvokeCommand(List args) {
      if (args.size() > 0) {
        myArgs = new ArrayList(args);
      }
      else {
        myArgs = args;
      }
    }

    public String toString() {
      return "INVOKE: " + super.toString();
    }

    protected abstract E invokeMethod(int invokePolicy, final List args) throws InvocationException,
                                                                                ClassNotLoadedException,
                                                                                IncompatibleThreadStateException,
                                                                                InvalidTypeException;

    public E start(EvaluationContextImpl evaluationContext, Method method) throws EvaluateException {
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
      beforeMethodInvocation(suspendContext, method);
      
      Object resumeData = null;
      try {
        for (final SuspendContextImpl suspendingContext : suspendingContexts) {
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

        while (true) {
          try {
            return invokeMethodAndFork(suspendContext);
            }
          catch (ClassNotLoadedException e) {
            ReferenceType loadedClass = null;
            try {
              loadedClass = evaluationContext.isAutoLoadClasses()? loadClass(evaluationContext, e.className(), evaluationContext.getClassLoader()) : null;
            }
            catch (EvaluateException ignored) {
              loadedClass = null;
            }
            if (loadedClass == null) {
              throw EvaluateExceptionUtil.createEvaluateException(e);
            }
          }
        }
      }
      catch (ClassNotLoadedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
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
        afterMethodInvocation(suspendContext);

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
              if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
                // ensure args are not collected
                for (Object arg : myArgs) {
                  if (arg instanceof ObjectReference) {
                    ((ObjectReference)arg).disableCollection();
                  }
                }
              }
              result[0] = invokeMethod(invokePolicy, myArgs);
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
          LOG.assertTrue(false);
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

  public Value invokeMethod(final EvaluationContext evaluationContext, final ObjectReference objRef, final Method method, final List args) throws EvaluateException {
    return invokeInstanceMethod(evaluationContext, objRef, method, args, 0);
  }

  public Value invokeInstanceMethod(final EvaluationContext evaluationContext, final ObjectReference objRef, final Method method,
                                     final List args, final int invocationOptions) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    InvokeCommand<Value> invokeCommand = new InvokeCommand<Value>(args) {
      protected Value invokeMethod(int invokePolicy, final List args) throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoke " + method.name());
        }
        //try {
        //  if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        //    // ensure target object wil not be collected
        //    objRef.disableCollection();
        //  }
          return objRef.invokeMethod(thread, method, args, invokePolicy | invocationOptions);
        //}
        //finally {
        //  if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        //    objRef.enableCollection();
        //  }
        //}
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, method);
  }

  private static ThreadReference getEvaluationThread(final EvaluationContext evaluationContext) throws EvaluateException {
    ThreadReferenceProxy evaluationThread = evaluationContext.getSuspendContext().getThread();
    if(evaluationThread == null) {
      throw EvaluateExceptionUtil.NULL_STACK_FRAME;
    }
    return evaluationThread.getThreadReference();
  }

  public Value invokeMethod(final EvaluationContext evaluationContext, final ClassType classType,
                            final Method method,
                            final List args) throws EvaluateException {

    final ThreadReference thread = getEvaluationThread(evaluationContext);
    InvokeCommand<Value> invokeCommand = new InvokeCommand<Value>(args) {
      protected Value invokeMethod(int invokePolicy, final List args) throws InvocationException,
                                                                             ClassNotLoadedException,
                                                                             IncompatibleThreadStateException,
                                                                             InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invoke " + method.name());
        }
        return classType.invokeMethod(thread, method, args, invokePolicy);
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, method);
  }

  public ArrayReference newInstance(final ArrayType arrayType,
                                    final int dimension)
    throws EvaluateException {
    return arrayType.newInstance(dimension);
  }

  public ObjectReference newInstance(final EvaluationContext evaluationContext, final ClassType classType,
                                     final Method method,
                                     final List args) throws EvaluateException {
    final ThreadReference thread = getEvaluationThread(evaluationContext);
    InvokeCommand<ObjectReference> invokeCommand = new InvokeCommand<ObjectReference>(args) {
      protected ObjectReference invokeMethod(int invokePolicy, final List args) throws InvocationException,
                                                                                       ClassNotLoadedException,
                                                                                       IncompatibleThreadStateException,
                                                                                       InvalidTypeException {
        if (LOG.isDebugEnabled()) {
          LOG.debug("New instance " + method.name());
        }
        return classType.newInstance(thread, method, args, invokePolicy);
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, method);
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

  private void beforeMethodInvocation(SuspendContextImpl suspendContext, Method method) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "before invocation in  thread " + suspendContext.getThread().name() + " method " + (method == null ? "null" : method.name()));
    }

    if (method != null) {
      showStatusText(DebuggerBundle.message("progress.evaluating", DebuggerUtilsEx.methodName(method)));
    }
    else {
      showStatusText(DebuggerBundle.message("title.evaluating"));
    }
  }

  private void afterMethodInvocation(SuspendContextImpl suspendContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("after invocation in  thread " + suspendContext.getThread().name());
    }
    showStatusText("");
  }

  public ReferenceType findClass(EvaluationContext evaluationContext, String className,
                                 ClassLoaderReference classLoader) throws EvaluateException {
    try {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      ReferenceType result = null;
      final VirtualMachineProxyImpl vmProxy = getVirtualMachineProxy();
      if (vmProxy == null) {
        throw new VMDisconnectedException();
      }
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
    final ClassLoaderReference typeLoader = refType.classLoader();
    if (typeLoader == null) {
      return true; // optimization: if class is loaded by a bootstrap loader, it is visible from every other loader
    }
    for (ClassLoaderReference checkLoader = fromLoader; checkLoader != null; checkLoader = getParentLoader(checkLoader)) {
      if (Comparing.equal(typeLoader, checkLoader)) {
        return true;
      }
    }
    return fromLoader != null? fromLoader.visibleClasses().contains(refType) : false;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static ClassLoaderReference getParentLoader(final ClassLoaderReference fromLoader) {
    final ReferenceType refType = fromLoader.referenceType();
    Field field = refType.fieldByName("parent");
    if (field == null) {
      final List allFields = refType.allFields();
      for (Iterator it = allFields.iterator(); it.hasNext();) {
        final Field candidateField = (Field)it.next();
        try {
          final Type checkedType = candidateField.type();
          if (checkedType instanceof ReferenceType && DebuggerUtilsEx.isAssignableFrom("java.lang.ClassLoader", (ReferenceType)checkedType)) {
            field = candidateField;
            break;
          }
        }
        catch (ClassNotLoadedException e) {
          // ignore this and continue,
          // java.lang.ClassLoader must be loaded at the moment of check, so if this happens, the field's type is definitely not java.lang.ClassLoader
        }
      }
    }
    return field != null? (ClassLoaderReference)fromLoader.getValue(field) : null;
  }

  private String reformatArrayName(String className) {
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String qName, ClassLoaderReference classLoader)
    throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException, EvaluateException {

    DebuggerManagerThreadImpl.assertIsManagerThread();
    qName = reformatArrayName(qName);
    ReferenceType refType = null;
    VirtualMachineProxyImpl virtualMachine = getVirtualMachineProxy();
    final List classClasses = virtualMachine.classesByName("java.lang.Class");
    if (classClasses.size() > 0) {
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

  public CompoundPositionManager getPositionManager() {
    return myPositionManager;
  }
  //ManagerCommands

  public void stop(boolean forceTerminate) {
    this.getManagerThread().terminateAndInvoke(createStopCommand(forceTerminate), DebuggerManagerThreadImpl.COMMAND_TIMEOUT);
  }

  public StopCommand createStopCommand(boolean forceTerminate) {
    return new StopCommand(forceTerminate);
  }

  protected class StopCommand extends DebuggerCommandImpl {
    private final boolean myIsTerminateTargetVM;

    public StopCommand(boolean isTerminateTargetVM) {
      myIsTerminateTargetVM = isTerminateTargetVM;
    }

    public Priority getPriority() {
      return Priority.HIGH;
    }

    protected void action() throws Exception {
      if (isAttached()) {
        final VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachineProxy();
        if (myIsTerminateTargetVM) {
          virtualMachineProxy.exit(-1);
        }
        else {
          // some VM's (like IBM VM 1.4.2 bundled with WebSpere) does not
          // resume threads on dispose() like it should
          virtualMachineProxy.resume();
          virtualMachineProxy.dispose();
        }
      }
      else {
        stopConnecting();
      }
    }
  }

  private class StepOutCommand extends ResumeCommand {
    public StepOutCommand(SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.step.out"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl thread = suspendContext.getThread();
      RequestHint hint = new RequestHint(thread, suspendContext, StepRequest.STEP_OUT);
      hint.setIgnoreFilters(mySession.shouldIgnoreSteppingFilters());
      if (myReturnValueWatcher != null) {
        myReturnValueWatcher.setTrackingEnabled(true);
      }
      doStep(suspendContext, thread, StepRequest.STEP_OUT, hint);
      super.contextAction();
    }
  }

  private class StepIntoCommand extends ResumeCommand {
    private final boolean myForcedIgnoreFilters;
    private final RequestHint.SmartStepFilter mySmartStepFilter;

    public StepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, final @Nullable RequestHint.SmartStepFilter smartStepFilter) {
      super(suspendContext);
      myForcedIgnoreFilters = ignoreFilters || smartStepFilter != null;
      mySmartStepFilter = smartStepFilter;
    }

    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.step.into"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl stepThread = suspendContext.getThread();
      final RequestHint hint = mySmartStepFilter != null?
                               new RequestHint(stepThread, suspendContext, mySmartStepFilter) :
                               new RequestHint(stepThread, suspendContext, StepRequest.STEP_INTO);
      if (myForcedIgnoreFilters) {
        try {
          mySession.setIgnoreStepFiltersFlag(stepThread.frameCount());
        }
        catch (EvaluateException e) {
          LOG.info(e);
        }
      }
      hint.setIgnoreFilters(myForcedIgnoreFilters || mySession.shouldIgnoreSteppingFilters());
      doStep(suspendContext, stepThread, StepRequest.STEP_INTO, hint);
      super.contextAction();
    }
  }

  private class StepOverCommand extends ResumeCommand {
    private final boolean myIsIgnoreBreakpoints;

    public StepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
      super(suspendContext);
      myIsIgnoreBreakpoints = ignoreBreakpoints;
    }

    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.step.over"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl steppingThread = suspendContext.getThread();
      // need this hint whil stepping over for JSR45 support:
      // several lines of generated java code may correspond to a single line in the source file,
      // from which the java code was generated
      RequestHint hint = new RequestHint(steppingThread, suspendContext, StepRequest.STEP_OVER);
      hint.setRestoreBreakpoints(myIsIgnoreBreakpoints);
      hint.setIgnoreFilters(myIsIgnoreBreakpoints || mySession.shouldIgnoreSteppingFilters());

      if (myReturnValueWatcher != null) {
        myReturnValueWatcher.setTrackingEnabled(true);
      }
      doStep(suspendContext, steppingThread, StepRequest.STEP_OVER, hint);

      if (myIsIgnoreBreakpoints) {
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().disableBreakpoints(DebugProcessImpl.this);
      }
      super.contextAction();
    }
  }

  private class RunToCursorCommand extends ResumeCommand {
    private final RunToCursorBreakpoint myRunToCursorBreakpoint;
    private final boolean myIgnoreBreakpoints;

    private RunToCursorCommand(SuspendContextImpl suspendContext, Document document, int lineIndex, final boolean ignoreBreakpoints) {
      super(suspendContext);
      myIgnoreBreakpoints = ignoreBreakpoints;
      final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
      myRunToCursorBreakpoint = breakpointManager.addRunToCursorBreakpoint(document, lineIndex, ignoreBreakpoints);
    }

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
      myRunToCursorBreakpoint.SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;
      myRunToCursorBreakpoint.LOG_ENABLED = false;
      myRunToCursorBreakpoint.createRequest(getSuspendContext().getDebugProcess());
      DebugProcessImpl.this.myRunToCursorBreakpoint = myRunToCursorBreakpoint;
      super.contextAction();
    }
  }

  public abstract class ResumeCommand extends SuspendContextCommandImpl {

    public ResumeCommand(SuspendContextImpl suspendContext) {
      super(suspendContext);
    }

    public Priority getPriority() {
      return Priority.HIGH;
    }

    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.process.resumed"));
      getSuspendManager().resume(getSuspendContext());
      myDebugProcessDispatcher.getMulticaster().resumed(getSuspendContext());
    }
  }

  private class PauseCommand extends DebuggerCommandImpl {
    public PauseCommand() {
    }

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

    public void contextAction() {
      if (getSuspendManager().isFrozen(myThread)) {
        getSuspendManager().unfreezeThread(myThread);
        return;
      }

      final Set<SuspendContextImpl> suspendingContexts = SuspendManagerUtil.getSuspendingContexts(getSuspendManager(), myThread);
      for (Iterator<SuspendContextImpl> iterator = suspendingContexts.iterator(); iterator.hasNext();) {
        SuspendContextImpl suspendContext = iterator.next();
        if (suspendContext.getThread() == myThread) {
          DebugProcessImpl.this.getManagerThread().invoke(createResumeCommand(suspendContext));
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

    protected void action() throws Exception {
      SuspendManager suspendManager = getSuspendManager();
      if (!suspendManager.isFrozen(myThread)) {
        suspendManager.freezeThread(myThread);
      }
    }
  }

  private class PopFrameCommand extends SuspendContextCommandImpl {
    private final StackFrameProxyImpl myStackFrame;

    public PopFrameCommand(SuspendContextImpl context, StackFrameProxyImpl frameProxy) {
      super(context);
      myStackFrame = frameProxy;
    }

    public void contextAction() {
      final ThreadReferenceProxyImpl thread = myStackFrame.threadProxy();
      try {
        if (!getSuspendManager().isSuspended(thread)) {
          notifyCancelled();
          return;
        }
      }
      catch (ObjectCollectedException e) {
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

  @NotNull
  public GlobalSearchScope getSearchScope() {
    LOG.assertTrue(mySession != null, "Accessing debug session before its initialization");
    return mySession.getSearchScope();
  }

  public @Nullable ExecutionResult attachVirtualMachine(final Executor executor,
                                                        final ProgramRunner runner,
                                                        final DebuggerSession session,
                                                        final RunProfileState state,
                                                        final RemoteConnection remoteConnection,
                                                        boolean pollConnection) throws ExecutionException {
    mySession = session;
    myWaitFor.down();

    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(isInInitialState());

    myConnection = remoteConnection;

    createVirtualMachine(state, pollConnection);

    try {
      synchronized (myProcessListeners) {
        if (state instanceof CommandLineState) {
          final TextConsoleBuilder consoleBuilder = ((CommandLineState)state).getConsoleBuilder();
          if (consoleBuilder != null) {
            consoleBuilder.addFilter(new ExceptionFilter(session.getSearchScope()));
          }
        }
        myExecutionResult = state.execute(executor, runner);
        if (myExecutionResult == null) {
          fail();
          return null;
        }
        for (ProcessListener processListener : myProcessListeners) {
          myExecutionResult.getProcessHandler().addProcessListener(processListener);
        }
        myProcessListeners.clear();
      }
    }
    catch (ExecutionException e) {
      fail();
      throw e;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myExecutionResult;
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

    return myExecutionResult;
  }

  private void fail() {
    synchronized (this) {
      if (myIsFailed) {
        // need this in order to prevent calling stop() twice
        return;
      }
      myIsFailed = true;
    }
    stop(false);
  }

  private void createVirtualMachine(final RunProfileState state, final boolean pollConnection) {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final Ref<Boolean> connectorIsReady = Ref.create(false);
    myDebugProcessDispatcher.addListener(new DebugProcessAdapter() {
      public void connectorIsReady() {
        connectorIsReady.set(true);
        semaphore.up();
        myDebugProcessDispatcher.removeListener(this);
      }
    });


    this.getManagerThread().schedule(new DebuggerCommandImpl() {
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
                  catch (InterruptedException ie) {
                    break;
                  }
                }
              }
              else {
                fail();
                if (myExecutionResult != null || !connectorIsReady.get()) {
                  // propagate exception only in case we succeded to obtain execution result,
                  // otherwise it the error is induced by the fact that there is nothing to debug, and there is no need to show
                  // this problem to the user
                  SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                      ExecutionUtil.handleExecutionError(myProject, state.getRunnerSettings().getRunProfile(), e);
                    }
                  });
                }
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
            public void run() {
              getManagerThread().schedule(new DebuggerCommandImpl() {
                protected void action() throws Exception {
                  commitVM(vm1);
                }
              });
            }
          });
        }
      }

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

      public void startNotified(ProcessEvent event) {
        run();
      }
    }
    MyProcessAdapter processListener = new MyProcessAdapter();
    addProcessListener(processListener);
    if(myExecutionResult != null) {
      if(myExecutionResult.getProcessHandler().isStartNotified()) {
        processListener.run();
      }
    }
  }

  public boolean isPausePressed() {
    return myVirtualMachineProxy != null && myVirtualMachineProxy.isPausePressed();
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
      public void contextAction() {
        breakpointManager.applyThreadFilter(DebugProcessImpl.this, null); // clear the filter on resume
        super.contextAction();
      }

      public Priority getPriority() {
        return priority;
      }
    };
  }

  public ResumeCommand createStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
    return new StepOverCommand(suspendContext, ignoreBreakpoints);
  }

  public ResumeCommand createStepOutCommand(SuspendContextImpl suspendContext) {
    return new StepOutCommand(suspendContext);
  }

  public ResumeCommand createStepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, final RequestHint.SmartStepFilter smartStepFilter) {
    return new StepIntoCommand(suspendContext, ignoreFilters, smartStepFilter);
  }

  public ResumeCommand createRunToCursorCommand(SuspendContextImpl suspendContext, Document document, int lineIndex,
                                                            final boolean ignoreBreakpoints)
    throws EvaluateException {
    RunToCursorCommand runToCursorCommand = new RunToCursorCommand(suspendContext, document, lineIndex, ignoreBreakpoints);
    if(runToCursorCommand.myRunToCursorBreakpoint == null) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
      throw new EvaluateException(DebuggerBundle.message("error.running.to.cursor.no.executable.code", psiFile != null? psiFile.getName() : "<No File>", lineIndex), null);
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

  public void setBreakpointsMuted(final boolean muted) {
    if (isAttached()) {
      getManagerThread().schedule(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          // set the flag before enabling/disabling cause it affects if breakpoints will create requests
          if (myBreakpointsMuted.getAndSet(muted) != muted) {
            final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
            if (muted) {
              breakpointManager.disableBreakpoints(DebugProcessImpl.this);
            }
            else {
              breakpointManager.enableBreakpoints(DebugProcessImpl.this);
            }
          }
        }
      });
    }
    else {
      myBreakpointsMuted.set(muted);
    }
  }


  public boolean areBreakpointsMuted() {
    return myBreakpointsMuted.get();
  }
}

