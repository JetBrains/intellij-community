// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class BreakpointManager
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.BreakpointStepMethodFilter;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.LightOrRealThreadInfo;
import com.intellij.debugger.engine.RealThreadInfo;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerSupport;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.jetbrains.jdi.EventRequestManagerImpl;
import com.sun.jdi.InternalException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.InvalidRequestStateException;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class BreakpointManager {
  private static final Logger LOG = Logger.getInstance(BreakpointManager.class);

  @NonNls private static final String MASTER_BREAKPOINT_TAG_NAME = "master_breakpoint";
  @NonNls private static final String SLAVE_BREAKPOINT_TAG_NAME = "slave_breakpoint";
  @NonNls private static final String DEFAULT_SUSPEND_POLICY_ATTRIBUTE_NAME = "default_suspend_policy";
  @NonNls private static final String DEFAULT_CONDITION_STATE_ATTRIBUTE_NAME = "default_condition_enabled";

  @NonNls private static final String RULES_GROUP_NAME = "breakpoint_rules";
  private static final String CONVERTED_PARAM = "converted";

  private final Project myProject;
  private final Map<String, String> myUIProperties = new LinkedHashMap<>();

  public BreakpointManager(@NotNull Project project, @NotNull DebuggerManagerImpl debuggerManager) {
    myProject = project;
    debuggerManager.getContextManager().addListener(new DebuggerContextListener() {
      private DebuggerSession myPreviousSession;

      @Override
      public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
        if (event == DebuggerSession.Event.ATTACHED) {
          for (XBreakpoint breakpoint : getXBreakpointManager().getAllBreakpoints()) {
            if (checkAndNotifyPossiblySlowBreakpoint(breakpoint)) break;
          }
        }
        if (newContext.getDebuggerSession() != myPreviousSession || event == DebuggerSession.Event.DETACHED) {
          updateBreakpointsUI();
          myPreviousSession = newContext.getDebuggerSession();
        }
      }
    });
  }

  private static boolean checkAndNotifyPossiblySlowBreakpoint(XBreakpoint breakpoint) {
    XBreakpointProperties properties = breakpoint.getProperties();
    if (breakpoint.isEnabled() && properties instanceof JavaMethodBreakpointProperties javaProperties && !javaProperties.EMULATED) {
      XDebuggerManagerImpl.getNotificationGroup()
        .createNotification(JavaDebuggerBundle.message("method.breakpoints.slowness.warning"), MessageType.WARNING)
        .notify(((XBreakpointBase<?, ?, ?>)breakpoint).getProject());
      return true;
    }
    return false;
  }

  public void addListeners(@NotNull MessageBusConnection busConnection) {
    busConnection.subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
      @Override
      public void breakpointAdded(@NotNull XBreakpoint<?> xBreakpoint) {
        Breakpoint<?> breakpoint = getJavaBreakpoint(xBreakpoint);
        if (breakpoint != null) {
          addBreakpoint(breakpoint);
          JavaBreakpointsUsageCollector.reportNewBreakpoint(breakpoint, xBreakpoint.getType());
        }
      }

      @Override
      public void breakpointChanged(@NotNull XBreakpoint xBreakpoint) {
        Breakpoint<?> breakpoint = getJavaBreakpoint(xBreakpoint);
        if (breakpoint != null) {
          //maybe readaction
          ReadAction.run(() -> {
            breakpoint.scheduleReload();
            breakpoint.updateUI();
          });
        }
      }
    });
  }

  private XBreakpointManager getXBreakpointManager() {
    return XDebuggerManager.getInstance(myProject).getBreakpointManager();
  }

  public void editBreakpoint(final Breakpoint breakpoint, final Editor editor) {
    DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
      XBreakpoint xBreakpoint = breakpoint.myXBreakpoint;
      if (xBreakpoint instanceof XLineBreakpointImpl) {
        RangeHighlighter highlighter = ((XLineBreakpointImpl<?>)xBreakpoint).getHighlighter();
        if (highlighter != null) {
          GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
          if (renderer != null) {
            DebuggerSupport.getDebuggerSupport(XDebuggerSupport.class).getEditBreakpointAction().editBreakpoint(
              myProject, editor, breakpoint.myXBreakpoint, renderer
            );
          }
        }
      }
    });
  }

  public void setBreakpointDefaults(Key<? extends Breakpoint> category, BreakpointDefaults defaults) {
    Class<? extends XBreakpointType> typeCls = null;
    if (LineBreakpoint.CATEGORY.toString().equals(category.toString())) {
      typeCls = JavaLineBreakpointType.class;
    }
    else if (MethodBreakpoint.CATEGORY.toString().equals(category.toString())) {
      typeCls = JavaMethodBreakpointType.class;
    }
    else if (FieldBreakpoint.CATEGORY.toString().equals(category.toString())) {
      typeCls = JavaFieldBreakpointType.class;
    }
    else if (ExceptionBreakpoint.CATEGORY.toString().equals(category.toString())) {
      typeCls = JavaExceptionBreakpointType.class;
    }
    else if (CollectionBreakpoint.CATEGORY.toString().equals(category.toString())) {
      typeCls = JavaCollectionBreakpointType.class;
    }
    if (typeCls != null) {
      XBreakpointType type = XDebuggerUtil.getInstance().findBreakpointType(typeCls);
      ((XBreakpointManagerImpl)getXBreakpointManager()).getBreakpointDefaults(type).setSuspendPolicy(Breakpoint.transformSuspendPolicy(defaults.getSuspendPolicy()));
    }
  }

  @Nullable
  public RunToCursorBreakpoint addRunToCursorBreakpoint(@NotNull XSourcePosition position, boolean ignoreBreakpoints) {
    return RunToCursorBreakpoint.create(myProject, position, ignoreBreakpoints);
  }

  @Nullable
  public StepIntoBreakpoint addStepIntoBreakpoint(@NotNull BreakpointStepMethodFilter filter) {
    return StepIntoBreakpoint.create(myProject, filter);
  }

  @Nullable
  public LineBreakpoint<?> addLineBreakpoint(Document document, int lineIndex, Consumer<JavaLineBreakpointProperties> setupAction) {
    ThreadingAssertions.assertEventDispatchThread();
    if (!LineBreakpoint.canAddLineBreakpoint(myProject, document, lineIndex)) {
      return null;
    }
    var xLineBreakpoint = addXLineBreakpoint(JavaLineBreakpointType.class, document, lineIndex,
                                             p -> setupAction.accept(((JavaLineBreakpointProperties)p)));
    var breakpoint = getJavaBreakpoint(xLineBreakpoint);
    if (breakpoint instanceof LineBreakpoint<?> lineBreakpoint) {
      addBreakpoint(breakpoint);
      return lineBreakpoint;
    }
    return null;
  }

  @Nullable
  public LineBreakpoint<?> addLineBreakpoint(Document document, int lineIndex) {
    return addLineBreakpoint(document, lineIndex, p -> {});
  }

  @Nullable
  public FieldBreakpoint addFieldBreakpoint(@NotNull Document document, int offset) {
    PsiField field = FieldBreakpoint.findField(myProject, document, offset);
    if (field == null) {
      return null;
    }

    int line = document.getLineNumber(offset);

    if (document.getLineNumber(field.getNameIdentifier().getTextOffset()) < line) {
      return null;
    }

    return addFieldBreakpoint(document, line, field.getName());
  }

  @Nullable
  public FieldBreakpoint addFieldBreakpoint(Document document, int lineIndex, String fieldName) {
    ThreadingAssertions.assertEventDispatchThread();
    XLineBreakpoint xBreakpoint = addXLineBreakpoint(JavaFieldBreakpointType.class, document, lineIndex);
    Breakpoint javaBreakpoint = getJavaBreakpoint(xBreakpoint);
    if (javaBreakpoint instanceof FieldBreakpoint fieldBreakpoint) {
      fieldBreakpoint.setFieldName(fieldName);
      addBreakpoint(javaBreakpoint);
      return fieldBreakpoint;
    }
    return null;
  }

  @Nullable
  public ExceptionBreakpoint addExceptionBreakpoint(@NotNull final String exceptionClassName, final String packageName) {
    ThreadingAssertions.assertEventDispatchThread();
    final JavaExceptionBreakpointType type = XDebuggerUtil.getInstance().findBreakpointType(JavaExceptionBreakpointType.class);
    return WriteAction.compute(() -> {
      XBreakpoint<JavaExceptionBreakpointProperties> xBreakpoint = XDebuggerManager.getInstance(myProject).getBreakpointManager()
        .addBreakpoint(type, new JavaExceptionBreakpointProperties(exceptionClassName, packageName));
      if (getJavaBreakpoint(xBreakpoint) instanceof ExceptionBreakpoint exceptionBreakpoint) {
        exceptionBreakpoint.setQualifiedName(exceptionClassName);
        exceptionBreakpoint.setPackageName(packageName);
        addBreakpoint(exceptionBreakpoint);
        LOG.debug("ExceptionBreakpoint Added");
        return exceptionBreakpoint;
      }
      return null;
    });
  }

  @Nullable
  public MethodBreakpoint addMethodBreakpoint(Document document, int lineIndex) {
    ThreadingAssertions.assertEventDispatchThread();

    XLineBreakpoint xBreakpoint = addXLineBreakpoint(JavaMethodBreakpointType.class, document, lineIndex);
    if (getJavaBreakpoint(xBreakpoint) instanceof MethodBreakpoint methodBreakpoint) {
      addBreakpoint(methodBreakpoint);
      return methodBreakpoint;
    }
    return null;
  }

  private <B extends XBreakpoint<?>> XLineBreakpoint addXLineBreakpoint(Class<? extends XBreakpointType<B, ?>> typeCls, Document document, final int lineIndex, Consumer<XBreakpointProperties> propertiesSetup) {
    final XBreakpointType<B, ?> type = XDebuggerUtil.getInstance().findBreakpointType(typeCls);
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return WriteAction.compute(() -> {
      var properties = ((XLineBreakpointType<?>)type).createBreakpointProperties(file, lineIndex);
      propertiesSetup.accept(properties);
      return XDebuggerManager.getInstance(myProject).getBreakpointManager()
        .addLineBreakpoint((XLineBreakpointType)type, file.getUrl(), lineIndex, properties);
    });
  }

  private <B extends XBreakpoint<?>> XLineBreakpoint addXLineBreakpoint(Class<? extends XBreakpointType<B, ?>> typeCls, Document document, final int lineIndex) {
    return addXLineBreakpoint(typeCls, document, lineIndex, p -> {});
  }

  /**
   * @param category breakpoint category, null if the category does not matter
   */
  @Nullable
  public <T extends BreakpointWithHighlighter> T findBreakpoint(final Document document, final int offset, @Nullable final Key<T> category) {
    for (final Breakpoint breakpoint : getBreakpoints()) {
      if (breakpoint instanceof BreakpointWithHighlighter && ((BreakpointWithHighlighter<?>)breakpoint).isAt(document, offset)) {
        if (category == null || category.equals(breakpoint.getCategory())) {
          // noinspection unchecked
          return (T)breakpoint;
        }
      }
    }
    return null;
  }

  private final Map<String, Element> myOriginalBreakpointsNodes = new LinkedHashMap<>();

  public void readExternal(@NotNull final Element parentNode) {
    myOriginalBreakpointsNodes.clear();
    // save old breakpoints
    for (Element element : parentNode.getChildren()) {
      myOriginalBreakpointsNodes.put(element.getName(), JDOMUtil.internElement(element));
    }
    if (!myProject.isDefault()) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> doRead(parentNode));
    }
  }

  private void doRead(@NotNull final Element parentNode) {
    ApplicationManager.getApplication().runReadAction(() -> {
      final Map<String, Breakpoint> nameToBreakpointMap = new HashMap<>();
      try {
        final List groups = parentNode.getChildren();
        for (final Object group1 : groups) {
          final Element group = (Element)group1;
          if (group.getName().equals(RULES_GROUP_NAME)) {
            continue;
          }
          // skip already converted
          if (group.getAttribute(CONVERTED_PARAM) != null) {
            continue;
          }
          final String categoryName = group.getName();
          final Key<Breakpoint> breakpointCategory = BreakpointCategory.lookup(categoryName);
          final String defaultPolicy = group.getAttributeValue(DEFAULT_SUSPEND_POLICY_ATTRIBUTE_NAME);
          final boolean conditionEnabled = Boolean.parseBoolean(group.getAttributeValue(DEFAULT_CONDITION_STATE_ATTRIBUTE_NAME, "true"));
          setBreakpointDefaults(breakpointCategory, new BreakpointDefaults(defaultPolicy, conditionEnabled));
          Element anyExceptionBreakpointGroup;
          if (!AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT.equals(breakpointCategory)) {
            // for compatibility with previous format
            anyExceptionBreakpointGroup = group.getChild(AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT.toString());
            //final BreakpointFactory factory = BreakpointFactory.getInstance(breakpointCategory);
            //if (factory != null) {
              for (Element breakpointNode : group.getChildren("breakpoint")) {
                //Breakpoint breakpoint = factory.createBreakpoint(myProject, breakpointNode);
                Breakpoint breakpoint = createBreakpoint(categoryName, breakpointNode);
                breakpoint.readExternal(breakpointNode);
                nameToBreakpointMap.put(breakpoint.getDisplayName(), breakpoint);
              }
            //}
          }
          else {
            anyExceptionBreakpointGroup = group;
          }

          if (anyExceptionBreakpointGroup != null) {
            final Element breakpointElement = group.getChild("breakpoint");
            if (breakpointElement != null) {
              XBreakpointManager manager = XDebuggerManager.getInstance(myProject).getBreakpointManager();
              JavaExceptionBreakpointType type = XDebuggerUtil.getInstance().findBreakpointType(JavaExceptionBreakpointType.class);
              for (XBreakpoint<JavaExceptionBreakpointProperties> defaultBreakpoint : manager.getDefaultBreakpoints(type)) {
                Breakpoint breakpoint = getJavaBreakpoint(defaultBreakpoint);
                if (breakpoint != null) {
                  breakpoint.readExternal(breakpointElement);
                  addBreakpoint(breakpoint);
                }
              }
            }
          }
        }
      }
      catch (InvalidDataException ignored) {
      }

      final Element rulesGroup = parentNode.getChild(RULES_GROUP_NAME);
      if (rulesGroup != null) {
        final List<Element> rules = rulesGroup.getChildren("rule");
        for (Element rule : rules) {
          // skip already converted
          if (rule.getAttribute(CONVERTED_PARAM) != null) {
            continue;
          }
          final Element master = rule.getChild(MASTER_BREAKPOINT_TAG_NAME);
          if (master == null) {
            continue;
          }
          final Element slave = rule.getChild(SLAVE_BREAKPOINT_TAG_NAME);
          if (slave == null) {
            continue;
          }
          final Breakpoint masterBreakpoint = nameToBreakpointMap.get(master.getAttributeValue("name"));
          if (masterBreakpoint == null) {
            continue;
          }
          final Breakpoint slaveBreakpoint = nameToBreakpointMap.get(slave.getAttributeValue("name"));
          if (slaveBreakpoint == null) {
            continue;
          }

          boolean leaveEnabled = Boolean.parseBoolean(rule.getAttributeValue("leaveEnabled"));
          XDependentBreakpointManager dependentBreakpointManager = ((XBreakpointManagerImpl)getXBreakpointManager()).getDependentBreakpointManager();
          dependentBreakpointManager.setMasterBreakpoint(slaveBreakpoint.myXBreakpoint, masterBreakpoint.myXBreakpoint, leaveEnabled);
          //addBreakpointRule(new EnableBreakpointRule(BreakpointManager.this, masterBreakpoint, slaveBreakpoint, leaveEnabled));
        }
      }

      updateBreakpointsUI();
    });

    myUIProperties.clear();
    final Element props = parentNode.getChild("ui_properties");
    if (props != null) {
      final List children = props.getChildren("property");
      for (Object child : children) {
        Element property = (Element)child;
        final String name = property.getAttributeValue("name");
        final String value = property.getAttributeValue("value");
        if (name != null && value != null) {
          myUIProperties.put(name, value);
        }
      }
    }
  }

  private Breakpoint createBreakpoint(String category, Element breakpointNode) throws InvalidDataException {
    XBreakpoint xBreakpoint = null;
    if (category.equals(LineBreakpoint.CATEGORY.toString())) {
      xBreakpoint = createXLineBreakpoint(JavaLineBreakpointType.class, breakpointNode);
    }
    else if (category.equals(MethodBreakpoint.CATEGORY.toString())) {
      if (breakpointNode.getAttribute("url") != null) {
        xBreakpoint = createXLineBreakpoint(JavaMethodBreakpointType.class, breakpointNode);
      }
      else {
        xBreakpoint = createXBreakpoint(JavaWildcardMethodBreakpointType.class);
      }
    }
    else if (category.equals(FieldBreakpoint.CATEGORY.toString())) {
      xBreakpoint = createXLineBreakpoint(JavaFieldBreakpointType.class, breakpointNode);
    }
    else if (category.equals(ExceptionBreakpoint.CATEGORY.toString())) {
      xBreakpoint = createXBreakpoint(JavaExceptionBreakpointType.class);
    }
    else if (category.equals(CollectionBreakpoint.CATEGORY.toString())) {
      xBreakpoint = createXBreakpoint(JavaCollectionBreakpointType.class);
    }
    if (xBreakpoint == null) {
      throw new IllegalStateException("Unknown breakpoint category " + category);
    }
    return getJavaBreakpoint(xBreakpoint);
  }

  private <B extends XBreakpoint<?>> XBreakpoint createXBreakpoint(Class<? extends XBreakpointType<B, ?>> typeCls) {
    final XBreakpointType<B, ?> type = XDebuggerUtil.getInstance().findBreakpointType(typeCls);
    return XDebuggerManager.getInstance(myProject).getBreakpointManager().addBreakpoint((XBreakpointType)type, type.createProperties());
  }

  private <B extends XBreakpoint<?>> XLineBreakpoint createXLineBreakpoint(Class<? extends XBreakpointType<B, ?>> typeCls,
                                                                           Element breakpointNode) throws InvalidDataException {
    final String url = breakpointNode.getAttributeValue("url");
    VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
    if (vFile == null) {
      throw new InvalidDataException(JavaDebuggerBundle.message("error.breakpoint.file.not.found", url));
    }
    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    if (doc == null) {
      throw new InvalidDataException(JavaDebuggerBundle.message("error.cannot.load.breakpoint.file", url));
    }

    final int line;
    try {
      line = Integer.parseInt(breakpointNode.getAttributeValue("line"));
    }
    catch (Exception e) {
      throw new InvalidDataException("Line number is invalid for breakpoint");
    }
    return addXLineBreakpoint(typeCls, doc, line);
  }

  public static void addBreakpoint(@NotNull Breakpoint breakpoint) {
    assert breakpoint.myXBreakpoint.getUserData(Breakpoint.DATA_KEY) == breakpoint;
    breakpoint.updateUI();
    checkAndNotifyPossiblySlowBreakpoint(breakpoint.myXBreakpoint);
  }

  public void removeBreakpoint(@Nullable final Breakpoint breakpoint) {
    if (breakpoint == null) {
      return;
    }
    getXBreakpointManager().removeBreakpoint(breakpoint.myXBreakpoint);
  }

  public void writeExternal(@NotNull final Element parentNode) {
    // restore old breakpoints
    for (Element group : myOriginalBreakpointsNodes.values()) {
      Element clone = group.clone();
      if (clone.getAttribute(CONVERTED_PARAM) == null) {
        clone.setAttribute(CONVERTED_PARAM, "true");
      }
      parentNode.addContent(clone);
    }
  }

  @NotNull
  public List<Breakpoint> getBreakpoints() {
    return ReadAction.compute(() ->
      ContainerUtil.mapNotNull(getXBreakpointManager().getAllBreakpoints(), BreakpointManager::getJavaBreakpoint));
  }

  @Nullable
  public static Breakpoint<?> getJavaBreakpoint(@Nullable final XBreakpoint<?> xBreakpoint) {
    if (xBreakpoint == null) {
      return null;
    }

    Breakpoint<?> existingBreakpoint = xBreakpoint.getUserData(Breakpoint.DATA_KEY);
    if (existingBreakpoint != null) {
      return existingBreakpoint;
    }

    if (!(xBreakpoint.getType() instanceof JavaBreakpointType)) {
      return null;
    }

    XBreakpointBase<?, ?, ?> xBreakpointBase = (XBreakpointBase<?, ?, ?>)xBreakpoint;
    Project project = xBreakpointBase.getProject();
    Breakpoint<?> breakpoint;
    try {
      //noinspection unchecked,rawtypes
      breakpoint = ((JavaBreakpointType)xBreakpoint.getType()).createJavaBreakpoint(project, xBreakpoint);
    }
    catch (Throwable e) {
      DebuggerUtilsImpl.logError(e);
      return null;
    }

    // Note that a newly created breakpoint might be thrown out if another thread set its own Java breakpoint concurrently.
    return xBreakpointBase.putUserDataIfAbsent(Breakpoint.DATA_KEY, breakpoint);
  }

  //interaction with RequestManagerImpl
  public void disableBreakpoints(@NotNull final DebugProcessImpl debugProcess) {
    final List<Breakpoint> breakpoints = getBreakpoints();
    if (!breakpoints.isEmpty()) {
      final RequestManagerImpl requestManager = debugProcess.getRequestsManager();
      for (Breakpoint breakpoint : breakpoints) {
        breakpoint.markVerified(requestManager.isVerified(breakpoint));
        requestManager.deleteRequest(breakpoint);
      }
      updateBreakpointsUI();
    }
  }

  public void enableBreakpoints(final DebugProcessImpl debugProcess) {
    final List<Breakpoint> breakpoints = getBreakpoints();
    if (!breakpoints.isEmpty()) {
      for (Breakpoint breakpoint : breakpoints) {
        breakpoint.markVerified(false); // clean cached state
        breakpoint.createRequest(debugProcess);
      }
      updateBreakpointsUI();
    }
  }

  /** @deprecated Use removeThreadFilter or version with LightOrRealThreadInfo parameter */
  @Deprecated
  public void applyThreadFilter(@NotNull final DebugProcessImpl debugProcess, @Nullable ThreadReference newFilterThread) {
    if (newFilterThread != null) {
      applyThreadFilter(debugProcess, new RealThreadInfo(newFilterThread));
    }
    else {
      removeThreadFilter(debugProcess);
    }
  }

  public void removeThreadFilter(@NotNull final DebugProcessImpl debugProcess) {
    applyThreadFilter(debugProcess, (LightOrRealThreadInfo)null);
  }

  public void applyThreadFilter(@NotNull final DebugProcessImpl debugProcess, @Nullable LightOrRealThreadInfo filter) {
    final RequestManagerImpl requestManager = debugProcess.getRequestsManager();
    if (Comparing.equal(filter, requestManager.getFilterThread())) {
      // the filter already added
      return;
    }

    final ThreadReference oldFilterThread = requestManager.getFilterRealThread();
    final ThreadReference newFilterThread = filter == null ? null : filter.getRealThread();

    requestManager.setThreadFilter(filter);

    if (!DebuggerSession.filterBreakpointsDuringSteppingUsingDebuggerEngine()) {
      return;
    }

    EventRequestManager eventRequestManager = requestManager.getVMRequestManager();
    if (DebuggerUtilsAsync.isAsyncEnabled() && eventRequestManager instanceof EventRequestManagerImpl) {
      Stream<EventRequestManagerImpl.ThreadVisibleEventRequestImpl> requests =
        StreamEx.<EventRequest>of(eventRequestManager.breakpointRequests())
          .append(eventRequestManager.methodEntryRequests())
          .append(eventRequestManager.methodExitRequests())
          .select(EventRequestManagerImpl.ThreadVisibleEventRequestImpl.class);
      try {
        Stream<CompletableFuture> futures = requests
          .map(r -> newFilterThread != null ? r.addThreadFilterAsync(newFilterThread) : r.removeThreadFilterAsync(oldFilterThread));
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      }
      catch (Exception e) {
        Throwable cause = DebuggerUtilsAsync.unwrap(e);
        if (cause instanceof VMDisconnectedException) {
          throw (VMDisconnectedException)cause;
        }
        LOG.error(new Exception(e));
      }
    }
    else if (newFilterThread == null || oldFilterThread != null) {
      final List<Breakpoint> breakpoints = getBreakpoints();
      for (Breakpoint breakpoint : breakpoints) {
        if (LineBreakpoint.CATEGORY.equals(breakpoint.getCategory()) || MethodBreakpoint.CATEGORY.equals(breakpoint.getCategory())) {
          requestManager.deleteRequest(breakpoint);
          breakpoint.createRequest(debugProcess);
        }
      }
    }
    else {
      // important! need to add filter to _existing_ requests, otherwise Requestor->Request mapping will be lost
      // and debugger trees will not be restored to original state
      if (eventRequestManager != null) {
        applyFilter(eventRequestManager.breakpointRequests(), request -> request.addThreadFilter(newFilterThread));
        applyFilter(eventRequestManager.methodEntryRequests(), request -> request.addThreadFilter(newFilterThread));
        applyFilter(eventRequestManager.methodExitRequests(), request -> request.addThreadFilter(newFilterThread));
      }
    }
  }

  private static <T extends EventRequest> void applyFilter(@NotNull List<T> requests, Consumer<? super T> setter) {
    for (T request : requests) {
      try {
        // skip synthetic
        if (RequestManagerImpl.findRequestor(request) instanceof SyntheticBreakpoint) {
          continue;
        }
        boolean wasEnabled = request.isEnabled();
        if (wasEnabled) {
          request.disable();
        }
        setter.accept(request);
        if (wasEnabled) {
          request.enable();
        }
      }
      catch (InternalException | InvalidRequestStateException e) {
        LOG.info(e);
      }
    }
  }

  public void updateBreakpointsUI() {
    ReadAction.nonBlocking(this::getBreakpoints)
      .coalesceBy(this)
      .expireWhen(myProject::isDisposed)
      .submit(AppExecutorUtil.getAppExecutorService())
      .onSuccess(b -> b.forEach(Breakpoint::updateUI));
  }

  @Nullable
  public Breakpoint findMasterBreakpoint(@NotNull Breakpoint dependentBreakpoint) {
    XDependentBreakpointManager dependentBreakpointManager = ((XBreakpointManagerImpl)getXBreakpointManager()).getDependentBreakpointManager();
    return getJavaBreakpoint(dependentBreakpointManager.getMasterBreakpoint(dependentBreakpoint.myXBreakpoint));
  }

  public String getProperty(String name) {
    return myUIProperties.get(name);
  }

  public String setProperty(String name, String value) {
    return myUIProperties.put(name, value);
  }
}
