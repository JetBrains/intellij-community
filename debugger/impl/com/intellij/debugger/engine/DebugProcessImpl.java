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
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.DebuggerSmoothManager;
import com.intellij.debugger.ui.DescriptorHistoryManagerImpl;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.debugger.ui.impl.watch.DescriptorHistoryManager;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.RunStrategy;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.Semaphore;
import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;

public abstract class DebugProcessImpl implements DebugProcess {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebugProcessImpl");

  static final @NonNls String  SOCKET_ATTACHING_CONNECTOR_NAME = "com.sun.jdi.SocketAttach";
  static final @NonNls String SHMEM_ATTACHING_CONNECTOR_NAME = "com.sun.jdi.SharedMemoryAttach";
  static final @NonNls String SOCKET_LISTENING_CONNECTOR_NAME = "com.sun.jdi.SocketListen";
  static final @NonNls String SHMEM_LISTENING_CONNECTOR_NAME = "com.sun.jdi.SharedMemoryListen";

  public static final @NonNls String JAVA_STRATUM = "Java";

  private final Project myProject;
  private final RequestManagerImpl myRequestManager;

  private VirtualMachineProxyImpl myVirtualMachineProxy = null;
  protected EventDispatcher<DebugProcessListener> myDebugProcessDispatcher = EventDispatcher.create(DebugProcessListener.class);
  protected EventDispatcher<EvaluationListener> myEvaluationDispatcher = EventDispatcher.create(EvaluationListener.class);

  private final List<ProcessListener> myProcessListeners = new ArrayList<ProcessListener>();

  private static final int STATE_INITIAL   = 0;
  private static final int STATE_ATTACHED  = 1;
  private static final int STATE_DETACHING = 2;
  private static final int STATE_DETACHED  = 3;
  private int myState = STATE_INITIAL;

  private boolean myCanRedefineClasses;
  private boolean myCanWatchFieldModification;

  private ExecutionResult  myExecutionResult;
  private RemoteConnection myConnection;

  private ConnectionServiceWrapper myConnectionService;
  private Map<String, Connector.Argument> myArguments;

  private LinkedList<String> myStatusStack = new LinkedList<String>();
  private String myStatusText;
  private int mySuspendPolicy = DebuggerSettings.getInstance().isSuspendAllThreads()
                                ? EventRequest.SUSPEND_ALL
                                : EventRequest.SUSPEND_EVENT_THREAD;

  private final DescriptorHistoryManager myDescriptorHistoryManager;

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
  DebuggerManagerThreadImpl myDebuggerManagerThread;
  private HashMap myUserData = new HashMap();
  private static int LOCAL_START_TIMEOUT = 15000;

  private final Semaphore myWaitFor = new Semaphore();
  private boolean myBreakpointsMuted = false;
  private boolean myIsFailed = false;
  private DebuggerSession mySession;


  protected DebugProcessImpl(Project project) {
    myProject = project;
    myRequestManager = new RequestManagerImpl(this);
    myDescriptorHistoryManager = new DescriptorHistoryManagerImpl(project);
    setSuspendPolicy(DebuggerSettings.getInstance().isSuspendAllThreads());
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

  public NodeRenderer getAutoRenderer(ValueDescriptor descriptor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final Value value = descriptor.getValue();
    Type type = value != null ? value.type() : null;

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
      LOG.assertTrue(false, "State is invalid " + myState);
    }
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager = createPositionManager();
    if (LOG.isDebugEnabled()) {
      LOG.debug("*******************VM attached******************");
    }
    checkVirtualMachineVersion(vm);

    myVirtualMachineProxy = new VirtualMachineProxyImpl(this, vm);
    myCanRedefineClasses = myVirtualMachineProxy.canRedefineClasses();
    myCanWatchFieldModification = myVirtualMachineProxy.canWatchFieldModification();

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
   * @param stepThread
   * @param depth
   * @param hint may be null
   */
  protected void doStep(final ThreadReferenceProxyImpl stepThread, int depth, RequestHint hint) {
    /*
    if (stepThread == null || !stepThread.isSuspended()) {
      return false;
    }
    */
    if (LOG.isDebugEnabled()) {
      LOG.debug("DO_STEP: creating step request for " + stepThread.getThreadReference());
    }
    deleteStepRequests(stepThread);
    EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
    StepRequest stepRequest = requestManager.createStepRequest(stepThread.getThreadReference(), StepRequest.STEP_LINE, depth);
    DebuggerSettings settings = DebuggerSettings.getInstance();
    if (!(hint != null && hint.isIgnoreFilters()) && depth == StepRequest.STEP_INTO) {
      if (settings.TRACING_FILTERS_ENABLED) {
        String currentClassName = getCurrentClassName(stepThread);
        if (currentClassName == null || !settings.isNameFiltered(currentClassName)) {
          // add class filters
          ClassFilter[] filters = settings.getSteppingFilters();
          for (ClassFilter filter : filters) {
            if (filter.isEnabled()) {
              stepRequest.addClassExclusionFilter(filter.getPattern());
            }
          }
        }
      }
    }

    stepRequest.setSuspendPolicy(getSuspendPolicy());

    if (hint != null) {
      //noinspection HardCodedStringLiteral
      stepRequest.putProperty("hint", hint);
    }
    stepRequest.enable();
  }

  void deleteStepRequests(ThreadReferenceProxy requestsInThread) {
    EventRequestManager requestManager = getVirtualMachineProxy().eventRequestManager();
    List stepRequests = requestManager.stepRequests();
    if (stepRequests.size() > 0) {
      List toDelete = new ArrayList();
      for (Iterator iterator = stepRequests.iterator(); iterator.hasNext();) {
        final StepRequest request = (StepRequest)iterator.next();
        ThreadReference threadReference = request.thread();

        if (threadReference.status() == ThreadReference.THREAD_STATUS_UNKNOWN) {
          // [jeka] on attempt to delete a request assigned to a thread with unknown status, a JDWP error occures
          continue;
        }
        else /*if(threadReference.equals(requestsInThread.getThreadReference())) */{
          toDelete.add(request);
        }
      }
      for (Iterator iterator = toDelete.iterator(); iterator.hasNext();) {
        StepRequest request = (StepRequest)iterator.next();
        requestManager.deleteEventRequest(request);
      }
    }
  }

  private String getCurrentClassName(ThreadReferenceProxyImpl thread) {
    try {
      final ThreadReferenceProxyImpl currentThreadProxy = thread;
      if (currentThreadProxy != null) {
        if (currentThreadProxy.frameCount() > 0) {
          StackFrameProxyImpl stackFrame = currentThreadProxy.frame(0);
          Location location = stackFrame.location();
          ReferenceType referenceType = location.declaringType();
          if (referenceType != null) {
            return referenceType.name();
          }
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
        Connector.Argument portArg = myConnection.isUseSockets()
                                     ? (Connector.Argument)myArguments.get("port")
                                     : (Connector.Argument)myArguments.get("name");
        if (portArg != null) {
          portArg.setValue(address);
        }
        //noinspection HardCodedStringLiteral
        final Connector.Argument timeoutArg = (Connector.Argument)myArguments.get("timeout");
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
          final Connector.Argument nameArg = (Connector.Argument)myArguments.get("name");
          if (nameArg != null) {
            nameArg.setValue(address);
          }
        }
        //noinspection HardCodedStringLiteral
        final Connector.Argument timeoutArg = (Connector.Argument)myArguments.get("timeout");
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

  private void pushStatisText(String text) {
    if (myStatusText == null) {
      myStatusText = ((StatusBarEx)WindowManager.getInstance().getStatusBar(getProject())).getInfo();
    }

    myStatusStack.addLast(myStatusText);
    showStatusText(text);
  }

  private void popStatisText() {
    if (!myStatusStack.isEmpty()) {
      showStatusText(myStatusStack.removeFirst());
    }
  }

  public void showStatusText(final String text) {
    myStatusText = text;
    DebuggerSmoothManager.getInstanceEx().action("DebugProcessImpl.showStatusText", new Runnable() {
      public void run() {
        if (ProjectManagerEx.getInstanceEx().isProjectOpened(getProject())) {
          WindowManager.getInstance().getStatusBar(getProject()).setInfo(text);
          myStatusText = null;
        }
      }
    });
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
    return myCanRedefineClasses;
  }

  public boolean canWatchFieldModification() {
    return myCanWatchFieldModification;
  }

  public boolean isInInitialState() {
    return myState == STATE_INITIAL;
  }

  public boolean isAttached() {
    return myState == STATE_ATTACHED;
  }

  public boolean isDetached() {
    return myState == STATE_DETACHED;
  }

  public boolean isDetaching() {
    return myState == STATE_DETACHING;
  }

  protected void setIsAttached() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myState = STATE_ATTACHED;
  }

  protected void setIsDetaching() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myState = STATE_DETACHING;
  }

  protected void setIsDetached() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myState = STATE_DETACHED;
  }

  public RequestManagerImpl getRequestsManager() {
    return myRequestManager;
  }

  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myVirtualMachineProxy == null) throw new VMDisconnectedException();
    return myVirtualMachineProxy;
  }

  public void appendPositionManager(final PositionManager positionManager) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myPositionManager.appendPositionManager(positionManager);
    DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().updateBreakpoints(this);
  }

  private LineBreakpoint myRunToCursorBreakpoint;

  public void cancelRunToCursorBreakpoint() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myRunToCursorBreakpoint != null) {
      getRequestsManager().deleteRequest(myRunToCursorBreakpoint);
      myRunToCursorBreakpoint.delete();
      myRunToCursorBreakpoint = null;
    }
  }

  protected void closeProcess(boolean closedByUser) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if (isDetached() || isDetaching()) {
      return;
    }

    setIsDetaching();
    myVirtualMachineProxy = null;
    myPositionManager = null;

    DebugProcessImpl.this.getManagerThread().close();

    setIsDetached();
    myDebugProcessDispatcher.getMulticaster().processDetached(DebugProcessImpl.this, closedByUser);
    setBreakpointsMuted(false);
    myWaitFor.up();
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
      StringBuffer buf = new StringBuffer();
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

  public DescriptorHistoryManager getDescriptorHistoryManager() {
    return myDescriptorHistoryManager;
  }

  public void dispose() {
    NodeRendererSettings.getInstance().removeListener(mySettingsListener);
    myDescriptorHistoryManager.dispose();
  }

  public DebuggerManagerThreadImpl getManagerThread() {
    synchronized (this) {
      if (myDebuggerManagerThread == null) {
        myDebuggerManagerThread = new DebuggerManagerThreadImpl();
      }
      return myDebuggerManagerThread;
    }
  }

  private static int getInvokePolicy(SuspendContext suspendContext) {
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
      myArgs = args.size() > 0? new ArrayList(args.size()) : args;
      for (Iterator it = args.iterator(); it.hasNext();) {
        final Object arg = (Object)it.next();
        if (arg instanceof ObjectReference) {
          myArgs.add((ObjectReference)arg);
        }
        else {
          myArgs.add(arg);
        }
      }
    }

    protected abstract E invokeMethod(int invokePolicy, final List args) throws InvocationException,
                                                                                ClassNotLoadedException,
                                                                                IncompatibleThreadStateException,
                                                                                InvalidTypeException;

    public E start(EvaluationContextImpl evaluationContext, Method method) throws EvaluateException {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      SuspendContextImpl suspendContext = evaluationContext.getSuspendContext();
      SuspendManagerUtil.assertSuspendContext(suspendContext);

      myEvaluationDispatcher.getMulticaster().evaluationStarted(suspendContext);

      beforeMethodInvocation(suspendContext, method);
      ThreadReferenceProxyImpl invokeThread = suspendContext.getThread();

      if (SuspendManagerUtil.isEvaluating(getSuspendManager(), invokeThread)) {
        throw EvaluateExceptionUtil.NESTED_EVALUATION_ERROR;
      }

      Set<SuspendContextImpl> suspendingContexts = SuspendManagerUtil.getSuspendingContexts(getSuspendManager(), invokeThread);
      final ThreadReference invokeThreadRef = invokeThread.getThreadReference();
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

      Object resumeData = SuspendManagerUtil.prepareForResume(suspendContext);
      suspendContext.setIsEvaluating(evaluationContext);

      getVirtualMachineProxy().clearCaches();

      try {
        while (true) {
          try {
            return invokeMethodAndFork(suspendContext);
          }
          catch (ClassNotLoadedException e) {
            ReferenceType loadedClass = loadClass(evaluationContext, e.className(), evaluationContext.getClassLoader());
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
      finally {
        // important need this to ensure that no requesst have been left.
        // situation; eveluate some method in breakpoint inside it
        // after the breakpoint has been hit, do stepOut: the step-out request will be added as a result
        // the problem is that VM will pause at the end of method evaluation _before_ stepOut event occurs and
        // the next evaluation (map.size() or toString()) will cause the request to generate the event.
        // As a result the user will find himself paused in completely different method 
        deleteStepRequests(invokeThread);
        suspendContext.setIsEvaluating(null);
        SuspendManagerUtil.restoreAfterResume(suspendContext, resumeData);
        for (Iterator<SuspendContextImpl> iterator = getSuspendManager().getEventContexts().iterator(); iterator.hasNext();) {
          SuspendContextImpl suspendingContext = iterator.next();
          if (suspendingContexts.contains(suspendingContext) && !suspendingContext.isEvaluating() && !suspendingContext.suspends(invokeThread)) {
            getSuspendManager().suspendThread(suspendingContext, invokeThread);
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
      DebugProcessImpl.this.getManagerThread().startLongProcessAndFork(new Runnable() {
        public void run() {
          ThreadReferenceProxyImpl thread = context.getThread();
          try {
            try {
              if (LOG.isDebugEnabled()) {
                final VirtualMachineProxyImpl virtualMachineProxy = getVirtualMachineProxy();
                virtualMachineProxy.logThreads();
                LOG.debug("Invoke in " + thread.name());
                LOG.assertTrue(thread.isSuspended(), thread.toString());
                LOG.assertTrue(context.isEvaluating());
              }
              result[0] = invokeMethod(invokePolicy, myArgs);
            }
            finally {
              if (!thread.isSuspended()) {
                LOG.assertTrue(false, thread.toString());
              }
              LOG.assertTrue(context.isEvaluating());
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
  }

  public Value invokeMethod(final EvaluationContext evaluationContext, final ObjectReference objRef,
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
        return objRef.invokeMethod(thread, method, args, invokePolicy);
      }
    };
    return invokeCommand.start((EvaluationContextImpl)evaluationContext, method);
  }

  private ThreadReference getEvaluationThread(final EvaluationContext evaluationContext) throws EvaluateException {
    ThreadReferenceProxy evaluationThread = evaluationContext.getSuspendContext().getThread();
    if(evaluationThread == null) throw EvaluateExceptionUtil.NULL_STACK_FRAME;
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
      pushStatisText(DebuggerBundle.message("progress.evaluating", DebuggerUtilsEx.methodName(method)));
    }
    else {
      pushStatisText(DebuggerBundle.message("title.evaluating"));
    }
  }

  private void afterMethodInvocation(SuspendContextImpl suspendContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("after invocation in  thread " + suspendContext.getThread().name());
    }
    popStatisText();
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
      final List list = vmProxy.classesByName(className);
      for (Iterator it = list.iterator(); it.hasNext();) {
        final ReferenceType refType = (ReferenceType)it.next();
        if (refType.isPrepared() && isVisibleFromClassLoader(classLoader, refType)) {
          result = refType;
          break;
        }
      }
      if (result == null) {
        return loadClass((EvaluationContextImpl)evaluationContext, className, classLoader);
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

  private boolean isVisibleFromClassLoader(final ClassLoaderReference fromLoader, final ReferenceType refType) {
    final ClassLoaderReference typeLoader = refType.classLoader();
    if (typeLoader == null) {
      return true; // optimization: if class is loaded by a bootstrap loader, it is visible from every other loader
    }
    for (ClassLoaderReference checkLoader = fromLoader; checkLoader != null; checkLoader = getParentLoader(checkLoader)) {
      if (Comparing.equal(typeLoader, checkLoader)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private ClassLoaderReference getParentLoader(final ClassLoaderReference fromLoader) {
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

    StringBuffer buffer = new StringBuffer();
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
        forNameMethod = classClassType.concreteMethodByName("forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
      }
      else {
        forNameMethod = classClassType.concreteMethodByName("forName", "(Ljava/lang/String;)Ljava/lang/Class;");
      }
      final List args = new ArrayList(); // do not use unmodifiable lists because the list is modified by JPDA
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

  public int getSuspendPolicy() {
    return mySuspendPolicy;
  }

  public void setSuspendPolicy(boolean suspendAll) {
    mySuspendPolicy = suspendAll ? EventRequest.SUSPEND_ALL : EventRequest.SUSPEND_EVENT_THREAD;
    DebuggerSettings.getInstance().setSuspendPolicy(suspendAll);
  }

  public void setSuspendPolicy(int policy) {
    mySuspendPolicy = policy;
    DebuggerSettings.getInstance().setSuspendPolicy(policy == EventRequest.SUSPEND_ALL);
  }

  public void logThreads() {
    if (LOG.isDebugEnabled()) {
      try {
        Collection<ThreadReferenceProxyImpl> allThreads = getVirtualMachineProxy().allThreads();
        for (Iterator<ThreadReferenceProxyImpl> iterator = allThreads.iterator(); iterator.hasNext();) {
          ThreadReferenceProxyImpl threadReferenceProxy = iterator.next();
          LOG.debug("Thread name=" + threadReferenceProxy.name() + " suspendCount()=" + threadReferenceProxy.suspendCount());
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
      doStep(thread, StepRequest.STEP_OUT, hint);
      super.contextAction();
    }
  }

  private class StepIntoCommand extends ResumeCommand {
    private final boolean myIgnoreFilters;

    public StepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters) {
      super(suspendContext);
      myIgnoreFilters = ignoreFilters;
    }

    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.step.into"));
      final SuspendContextImpl suspendContext = getSuspendContext();
      final ThreadReferenceProxyImpl stepThread = suspendContext.getThread();
      RequestHint hint = new RequestHint(stepThread, suspendContext, StepRequest.STEP_INTO);
      hint.setIgnoreFilters(myIgnoreFilters);
      doStep(stepThread, StepRequest.STEP_INTO, hint);
      super.contextAction();
    }
  }

  private class StepOverCommand extends ResumeCommand {
    private boolean myIsIgnoreBreakpoints;

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

      doStep(steppingThread, StepRequest.STEP_OVER, hint);

      if (myIsIgnoreBreakpoints) {
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().disableBreakpoints(DebugProcessImpl.this);
      }
      super.contextAction();
    }
  }

  private class RunToCursorCommand extends ResumeCommand {
    private final LineBreakpoint myRunToCursorBreakpoint;

    private RunToCursorCommand(SuspendContextImpl suspendContext, Document document, int lineIndex) {
      super(suspendContext);
      myRunToCursorBreakpoint = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addRunToCursorBreakpoint(document, lineIndex);
    }

    public void contextAction() {
      showStatusText(DebuggerBundle.message("status.run.to.cursor"));
      cancelRunToCursorBreakpoint();
      if (myRunToCursorBreakpoint == null) {
        return;
      }
      myRunToCursorBreakpoint.SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;
      myRunToCursorBreakpoint.LOG_ENABLED = false;
      myRunToCursorBreakpoint.createRequest(getSuspendContext().getDebugProcess());
      DebugProcessImpl.this.myRunToCursorBreakpoint = myRunToCursorBreakpoint;
      super.contextAction();
    }
  }

  private class ResumeCommand extends SuspendContextCommandImpl {

    public ResumeCommand(SuspendContextImpl suspendContext) {
      super(suspendContext);
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
      if (!getSuspendManager().isSuspended(thread)) {
        notifyCancelled();
        return;
      }

      final SuspendContextImpl suspendContext = getSuspendContext();
      if (!suspendContext.suspends(thread)) {
        SuspendManagerUtil.postponeCommand(suspendContext, this);
        return;
      }

      if (myStackFrame.isBottom()) {
        DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
          public void run() {
            Messages.showMessageDialog(myProject, DebuggerBundle.message("error.pop.bottom.stackframe"), ActionsBundle.actionText(DebuggerActions.POP_FRAME), Messages.getErrorIcon());
          }
        });
        return;
      }

      try {
        thread.popFrames(myStackFrame);
      }
      catch (EvaluateException e) {
        LOG.error(e);
      }
      finally {
        getSuspendManager().popFrame(suspendContext);
      }
    }
  }

  public DebuggerSession getSession() {
    return mySession;
  }

  public @Nullable ExecutionResult attachVirtualMachine(final DebuggerSession session,
                                                        final RunProfileState state,
                                                        final RemoteConnection remoteConnection,
                                                        boolean pollConnection) throws ExecutionException {
    mySession = session;
    myWaitFor.down();

    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(isInInitialState());

    myConnection = remoteConnection;

    createVirtualMachine(state, pollConnection);

    try {
      synchronized (myProcessListeners) {
        myExecutionResult = state.execute();
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

    final Alarm debugPortTimeout = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    myExecutionResult.getProcessHandler().addProcessListener(new ProcessAdapter() {
      public void processTerminated(ProcessEvent event) {
        debugPortTimeout.cancelAllRequests();
      }

      public void startNotified(ProcessEvent event) {
        debugPortTimeout.addRequest(new Runnable() {
          public void run() {
            if(isInInitialState()) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
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

    myDebugProcessDispatcher.addListener(new DebugProcessAdapter() {
      public void connectorIsReady() {
        semaphore.up();
        myDebugProcessDispatcher.removeListener(this);
      }
    });

    final long time = System.currentTimeMillis();

    this.getManagerThread().invokeLater(new DebuggerCommandImpl() {
      protected void action() {
        VirtualMachine vm = null;

        try {
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
                if (myExecutionResult != null) {
                  // propagate exception only in case we succeded to obtain execution result,
                  // otherwise it the error is induced by the fact that there is nothing to debug, and there is no need to show
                  // this problem to the user
                  SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                      RunStrategy.handleExecutionError(myProject, state.getRunnerSettings().getRunProfile(), e);
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

        if(vm != null) {
          final VirtualMachine vm1 = vm;
          afterProcessStarted(new Runnable() {
            public void run() {
              getManagerThread().invokeLater(new DebuggerCommandImpl() {
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
    return (myVirtualMachineProxy != null)? myVirtualMachineProxy.isPausePressed() : false;
  }

  public DebuggerCommandImpl createPauseCommand() {
    return new PauseCommand();
  }

  public SuspendContextCommandImpl createResumeCommand(SuspendContextImpl suspendContext) {
    return new ResumeCommand(suspendContext);
  }

  public SuspendContextCommandImpl createStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints) {
    return new StepOverCommand(suspendContext, ignoreBreakpoints);
  }

  public SuspendContextCommandImpl createStepOutCommand(SuspendContextImpl suspendContext) {
    return new StepOutCommand(suspendContext);
  }

  public SuspendContextCommandImpl createStepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters) {
    return new StepIntoCommand(suspendContext, ignoreFilters);
  }

  public SuspendContextCommandImpl createRunToCursorCommand(SuspendContextImpl suspendContext, Document document, int lineIndex)
    throws EvaluateException {
    RunToCursorCommand runToCursorCommand = new RunToCursorCommand(suspendContext, document, lineIndex);
    if(runToCursorCommand.myRunToCursorBreakpoint == null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
      throw new EvaluateException(DebuggerBundle.message("error.running.to.cursor.no.executable.code", psiFile.getName(), lineIndex), null);
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
      getManagerThread().invokeLater(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          // set the flag before enabling/disabling cause it affects if breakpoints will create requests
          synchronized (DebugProcessImpl.this) {
            if (myBreakpointsMuted == muted) {
              return;
            }
            myBreakpointsMuted = muted;
          }
          final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
          if (muted) {
            breakpointManager.disableBreakpoints(DebugProcessImpl.this);
          }
          else {
            breakpointManager.enableBreakpoints(DebugProcessImpl.this);
          }
        }
      });
    }
    else {
      synchronized (this) {
        myBreakpointsMuted = muted;
      }
    }
  }


  public synchronized boolean areBreakpointsMuted() {
    return myBreakpointsMuted;
  }
}

