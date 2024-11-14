// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.ExceptionBreakpoint;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionTestCase;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import com.sun.jdi.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Runs the IDE with the debugger.
 * <p>
 * To set a breakpoint in the source code,
 * place a 'Breakpoint!' comment in the line directly above,
 * see {@link #createBreakpoints} for details.
 */
public abstract class ExecutionWithDebuggerToolsTestCase extends ExecutionTestCase {
  private @Nullable BreakpointProvider myBreakpointProvider;
  protected static final int RATHER_LATER_INVOKES_N = 10;
  public DebugProcessImpl myDebugProcess;
  private final List<Throwable> myException = new SmartList<>();
  protected boolean myWasUsedOnlyDefaultSuspendPolicy = true;

  private static class InvokeRatherLaterRequest {
    private final DebuggerCommandImpl myDebuggerCommand;
    private final DebugProcessImpl myDebugProcess;
    int invokesN;

    InvokeRatherLaterRequest(DebuggerCommandImpl debuggerCommand, DebugProcessImpl debugProcess) {
      myDebuggerCommand = debuggerCommand;
      myDebugProcess = debugProcess;
    }
  }

  public final List<InvokeRatherLaterRequest> myRatherLaterRequests = new ArrayList<>();

  protected DebugProcessImpl getDebugProcess() {
    return myDebugProcess;
  }

  protected void resume(SuspendContextImpl context) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    context.getManagerThread().schedule(debugProcess.createResumeCommand(context, PrioritizedTask.Priority.LOWEST));
  }

  protected void stepInto(SuspendContextImpl context) {
    stepInto(context, false);
  }

  protected void stepInto(SuspendContextImpl context, boolean ignoreFilters) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    context.getManagerThread().schedule(debugProcess.createStepIntoCommand(context, ignoreFilters, null));
  }

  protected void stepOver(SuspendContextImpl context) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    context.getManagerThread().schedule(debugProcess.createStepOverCommand(context, false));
  }

  protected void stepOut(SuspendContextImpl context) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    context.getManagerThread().schedule(debugProcess.createStepOutCommand(context));
  }

  @Override
  protected void tearDown() throws Exception {
    ThreadTracker.awaitJDIThreadsTermination(100, TimeUnit.SECONDS);
    try {
      myDebugProcess = null;
      myBreakpointProvider = null;
      myRatherLaterRequests.clear();
      myWasUsedOnlyDefaultSuspendPolicy = true;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
      throwExceptionsIfAny();
    }
  }

  protected void throwExceptionsIfAny() {
    synchronized (myException) {
      CompoundRuntimeException.throwIfNotEmpty(myException);
      myException.clear();
    }
  }

  private @NotNull BreakpointProvider getBreakpointProvider() {
    if (myBreakpointProvider == null) {
      DebugProcessImpl debugProcess = getDebugProcess();
      assertNotNull("Debug process was not started", debugProcess);

      myBreakpointProvider = new BreakpointProvider(myDebugProcess);
      DebugProcessListener processListener = new DelayedEventsProcessListener(myBreakpointProvider);
      debugProcess.addDebugProcessListener(processListener, getTestRootDisposable());
    }
    return myBreakpointProvider;
  }

  /**
   * Queues an action to be run a single time.
   * <p>
   * Whenever the VM stops, no matter whether due to a breakpoint
   * or because an action like {@link #stepInto(SuspendContextImpl)}
   * or {@link #stepOver(SuspendContextImpl)} finished,
   * a single action is polled from the queue and then run.
   */
  protected void onBreakpoint(SuspendContextRunnable runnable) {
    getBreakpointProvider().onBreakpoint(runnable);
  }

  /**
   * Runs an action every time the VM stops, no matter whether due to a breakpoint
   * or because an action like {@link #stepInto(SuspendContextImpl)}
   * or {@link #stepOver(SuspendContextImpl)} finished.
   * <p>
   * The actions added here are run after the one-time action from {@link #onBreakpoint(SuspendContextRunnable)}.
   */
  protected void onEveryBreakpoint(SuspendContextRunnable runnable) {
    getBreakpointProvider().onEveryBreakpoint(runnable);
  }

  /**
   * Queues two actions to be run a single time, one after another.
   *
   * @see #onBreakpoint(SuspendContextRunnable)
   */
  protected void onStop(SuspendContextRunnable runnable, SuspendContextRunnable then) {
    onBreakpoint(new SuspendContextRunnable() {
      @Override
      public void run(SuspendContextImpl suspendContext) throws Exception {
        try {
          runnable.run(suspendContext);
        }
        finally {
          then.run(suspendContext);
        }
      }
    });
  }

  /**
   * Queues an action to be run a single time, see {@link #onBreakpoint(SuspendContextRunnable)}.
   * <p>
   * After the action is run, execution resumes.
   *
   * @see #onStop(SuspendContextRunnable, SuspendContextRunnable)
   */
  protected void doWhenPausedThenResume(SuspendContextRunnable runnable) {
    onStop(runnable, this::resume);
  }

  protected void printFrameProxy(StackFrameProxyImpl frameProxy) throws EvaluateException {
    int frameIndex = frameProxy.getFrameIndex();
    Method method = frameProxy.location().method();

    systemPrintln("frameProxy(" + frameIndex + ") = " + method);
  }

  protected static String toDisplayableString(SourcePosition sourcePosition) {
    int line = sourcePosition.getLine();
    if (line >= 0) {
      line++;
    }
    return sourcePosition.getFile().getVirtualFile().getName() + ":" + line;
  }

  /** Prints the location of the given context, in the format "file.ext:12345". */
  protected void printContext(StackFrameContext context) {
    ApplicationManager.getApplication().runReadAction(() -> {
      if (context.getFrameProxy() != null) {
        systemPrintln(toDisplayableString(Objects.requireNonNull(PositionUtil.getSourcePosition(context))));
      }
      else {
        systemPrintln("Context thread is null");
      }
    });
  }

  protected void printContextWithText(StackFrameContext context) {
    ApplicationManager.getApplication().runReadAction(() -> {
      if (context.getFrameProxy() != null) {
        SourcePosition sourcePosition = PositionUtil.getSourcePosition(context);
        int offset = sourcePosition.getOffset();
        Document document = PsiDocumentManager.getInstance(myProject).getDocument(sourcePosition.getFile());
        CharSequence text = Objects.requireNonNull(document).getImmutableCharSequence();
        String positionText = "";
        if (offset > -1) {
          CharSequence before = text.subSequence(Math.max(0, offset - 20), offset);
          CharSequence after = text.subSequence(offset, Math.min(offset + 20, text.length()));
          positionText = StringUtil.escapeLineBreak(" [" + before + "<*>" + after + "]");
        }

        systemPrintln(toDisplayableString(sourcePosition) + positionText);
        printHighlightingRange((SuspendContextImpl)context);
      }
      else {
        systemPrintln("Context thread is null");
      }
    });
  }

  protected void printHighlightingRange(SuspendContextImpl context) {
    SourcePosition position = context.getDebugProcess().getPositionManager().getSourcePosition(context.getLocation());
    JavaSourcePositionHighlighter highlighter = new JavaSourcePositionHighlighter();
    TextRange range = ReadAction.compute(() -> highlighter.getHighlightRange(position));
    String actualText = range == null ? null : range.substring(position.getFile().getText());
    if (actualText != null) {
      systemPrintln("Highlight code range: '" + StringUtil.escapeLineBreak(actualText) + "'");
    } else {
      systemPrintln("Highlight whole line");
    }
  }

  protected void invokeRatherLater(@NotNull SuspendContextImpl context, @NotNull Runnable runnable) {
    invokeRatherLater(context.getDebugProcess(), new SuspendContextCommandImpl(context) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        DebuggerInvocationUtil.invokeLater(myProject, runnable);
      }
    });
  }

  protected void pumpSwingThread() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    InvokeRatherLaterRequest request = myRatherLaterRequests.get(0);
    request.invokesN++;

    if (request.invokesN == RATHER_LATER_INVOKES_N) {
      myRatherLaterRequests.remove(0);
      if (!myRatherLaterRequests.isEmpty()) pumpSwingThread();
    }

    if (request.myDebuggerCommand instanceof SuspendContextCommandImpl suspendContextCommand) {
      SuspendContextImpl suspendContext = suspendContextCommand.getSuspendContext();
      Objects.requireNonNull(suspendContext).getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          pumpDebuggerThread(request);
        }

        @Override
        protected void commandCancelled() {
          pumpDebuggerThread(request);
        }
      });
    }
    else {
      request.myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() {
          pumpDebuggerThread(request);
        }

        @Override
        protected void commandCancelled() {
          pumpDebuggerThread(request);
        }
      });
    }
  }

  private void pumpDebuggerThread(InvokeRatherLaterRequest request) {
    if (request.invokesN == RATHER_LATER_INVOKES_N) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        var commands = request.myDebugProcess.getManagerThread().getUnfinishedCommands();
        while (!commands.isEmpty()) {
          TimeoutUtil.sleep(1);
        }
        request.myDebugProcess.getManagerThread().schedule(request.myDebuggerCommand);
      });
    }
    else {
      if (!SwingUtilities.isEventDispatchThread()) {
        try {
          EdtInvocationManager.getInstance().invokeAndWait(() -> pumpSwingThread());
        }
        catch (InvocationTargetException | InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      else {
        SwingUtilities.invokeLater(() -> pumpSwingThread());
      }
    }
  }

  protected void invokeRatherLater(DebuggerCommandImpl command) {
    invokeRatherLater(getDebugProcess(), command);
  }

  protected void invokeRatherLater(@NotNull DebugProcessImpl debugProcess, DebuggerCommandImpl command) {
    UIUtil.invokeLaterIfNeeded(() -> {
      InvokeRatherLaterRequest request = new InvokeRatherLaterRequest(command, debugProcess);
      myRatherLaterRequests.add(request);

      if (myRatherLaterRequests.size() == 1) pumpSwingThread();
    });
  }

  protected void addException(@NotNull Throwable e) {
    synchronized (myException) {
      myException.add(e);
    }
  }

  protected void error(@NotNull Throwable th) {
    fail(StringUtil.getThrowableText(th));
  }

  /**
   * Create breakpoints as specified by 'Breakpoint!' comments in the source code.
   * <p>
   * A breakpoint comment has the form &#x201C;[<i>kind</i>] Breakpoint! [<i>property</i>...]&#x201D;.
   * <p>
   * Breakpoint kinds (none defaults to a line breakpoint):
   * <ul>
   * <li>Method
   * <li>Field(fieldName)
   * <li>Exception(java.lang.RuntimeException)
   * </ul>
   * Properties for all breakpoints:
   * <ul>
   * <li>Class filters(-MethodClassFilter$A)
   * <li>Condition(i >= 10 || (i % 5 == 0))
   * <li>LogExpression("i = " + i)
   * <li>Pass count(13)
   * <li>suspendPolicy(SuspendAll | SuspendThread | SuspendNone)
   * </ul>
   * Properties for method breakpoints:
   * <ul>
   * <li>Emulated(true)
   * <li>OnEntry(true)
   * <li>OnExit(true)
   * </ul>
   * Properties for exception breakpoints:
   * <ul>
   * <li>Catch class filters(-ExceptionTest,-com.intellij.rt.*)
   * </ul>
   */
  public void createBreakpoints(PsiFile file) {
    Runnable runnable = () -> {
      BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      assert document != null;
      String text = document.getText();

      for (int offset = -1; (offset = text.indexOf("Breakpoint!", offset + 1)) != -1; ) {
        int commentLine = document.getLineNumber(offset);
        String fileName = file.getVirtualFile().getName();
        String breakpointLocation = fileName + ":" + (commentLine + 1 + 1);

        String commentText = text.substring(document.getLineStartOffset(commentLine), document.getLineEndOffset(commentLine));
        BreakpointComment comment = BreakpointComment.parse(commentText, file.getVirtualFile().getPresentableUrl(), commentLine);

        Breakpoint breakpoint;

        String kind = comment.readKind();
        switch (kind) {
          case "Method" -> {
            breakpoint = breakpointManager.addMethodBreakpoint(document, commentLine + 1);
            if (breakpoint != null) {
              systemPrintln("MethodBreakpoint created at " + breakpointLocation);

              Boolean emulated = comment.readBooleanValue("Emulated");
              if (emulated != null) {
                ((JavaMethodBreakpointProperties)breakpoint.getXBreakpoint().getProperties()).EMULATED = emulated;
                systemPrintln("Emulated = " + emulated);
              }

              Boolean entry = comment.readBooleanValue("OnEntry");
              if (entry != null) {
                ((JavaMethodBreakpointProperties)breakpoint.getXBreakpoint().getProperties()).WATCH_ENTRY = entry;
                systemPrintln("On Entry = " + entry);
              }

              Boolean exit = comment.readBooleanValue("OnExit");
              if (exit != null) {
                ((JavaMethodBreakpointProperties)breakpoint.getXBreakpoint().getProperties()).WATCH_EXIT = exit;
                systemPrintln("On Exit = " + exit);
              }
            }
          }
          case "Field" -> {
            breakpoint = breakpointManager.addFieldBreakpoint(document, commentLine + 1, comment.readKindValue());
            if (breakpoint != null) {
              systemPrintln("FieldBreakpoint created at " + breakpointLocation);
            }
          }
          case "Exception" -> {
            String exceptionClassName = Objects.requireNonNull(comment.readKindValue());
            breakpoint = breakpointManager.addExceptionBreakpoint(exceptionClassName);
            if (breakpoint == null) break;
            systemPrintln("ExceptionBreakpoint created at " + breakpointLocation);
            String catchClassFiltersStr = comment.readValue("Catch class filters");
            if (catchClassFiltersStr != null) {
              Pair<ClassFilter[], ClassFilter[]> filters = BreakpointComment.parseClassFilters(catchClassFiltersStr);
              ExceptionBreakpoint exceptionBreakpoint = (ExceptionBreakpoint)breakpoint;
              exceptionBreakpoint.setCatchFiltersEnabled(true);
              exceptionBreakpoint.setCatchClassFilters(filters.first);
              exceptionBreakpoint.setCatchClassExclusionFilters(filters.second);
              systemPrintln("Catch class filters = " + catchClassFiltersStr);
            }
          }
          case "ConditionalReturn" -> {
            breakpoint = breakpointManager.addLineBreakpoint(document, commentLine + 1, p -> {
              // Note that we don't support `return` inside of lambda in unit tests.
              p.setEncodedInlinePosition(JavaLineBreakpointProperties.encodeInlinePosition(JavaLineBreakpointProperties.NO_LAMBDA, true));
            });
            if (breakpoint != null) {
              systemPrintln("ConditionalReturnBreakpoint created at " + breakpointLocation);
            }
          }
          case "Line" -> {
            breakpoint = breakpointManager.addLineBreakpoint(document, commentLine + 1);
            if (breakpoint != null) {
              systemPrintln("LineBreakpoint created at " + breakpointLocation);
            }
          }
          default -> throw new IllegalArgumentException("Invalid kind '" + kind + "' at " + fileName + ":" + (commentLine + 1));
        }

        if (breakpoint == null) {
          LOG.error("Unable to set a breakpoint at line " + (commentLine + 1));
          continue;
        }

        String enabled = comment.readValue("Enabled");
        if (enabled != null) {
          breakpoint.getXBreakpoint().setEnabled(Boolean.parseBoolean(enabled));
          systemPrintln("Enabled = " + enabled);
        }

        String suspendPolicy = comment.readValue("suspendPolicy");
        if (suspendPolicy != null) {
          //breakpoint.setSuspend(!DebuggerSettings.SUSPEND_NONE.equals(suspendPolicy));
          breakpoint.setSuspendPolicy(suspendPolicy);
          systemPrintln("SUSPEND_POLICY = " + suspendPolicy);
          myWasUsedOnlyDefaultSuspendPolicy = false;
        }

        String condition = comment.readValue("Condition");
        if (condition != null) {
          //breakpoint.CONDITION_ENABLED = true;
          breakpoint.setCondition(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, condition));
          systemPrintln("Condition = " + condition);
        }

        String logExpression = comment.readValue("LogExpression");
        if (logExpression != null) {
          breakpoint.getXBreakpoint().setLogExpression(logExpression);
          systemPrintln("LogExpression = " + logExpression);
        }

        Integer passCount = comment.readIntValue("Pass count");
        if (passCount != null) {
          breakpoint.setCountFilterEnabled(true);
          breakpoint.setCountFilter(passCount);
          systemPrintln("Pass count = " + passCount);
        }

        String classFiltersStr = comment.readValue("Class filters");
        if (classFiltersStr != null) {
          breakpoint.setClassFiltersEnabled(true);
          Pair<ClassFilter[], ClassFilter[]> filters = BreakpointComment.parseClassFilters(classFiltersStr);
          breakpoint.setClassFilters(filters.first);
          breakpoint.setClassExclusionFilters(filters.second);
          systemPrintln("Class filters = " + classFiltersStr);
        }

        comment.done();
      }
    };
    if (!SwingUtilities.isEventDispatchThread()) {
      DebuggerInvocationUtil.invokeAndWait(myProject, runnable, ModalityState.defaultModalityState());
    }
    else {
      runnable.run();
    }
  }

  protected static class DelayedEventsProcessListener implements DebugProcessListener {
    private final DebugProcessAdapterImpl myTarget;

    public DelayedEventsProcessListener(DebugProcessAdapterImpl target) {
      myTarget = target;
    }

    @Override
    public void paused(@NotNull SuspendContext suspendContext) {
      pauseExecution();
      myTarget.paused(suspendContext);
    }

    @Override
    public void resumed(SuspendContext suspendContext) {
      pauseExecution();
      myTarget.resumed(suspendContext);
    }

    @Override
    public void processDetached(@NotNull DebugProcess process, boolean closedByUser) {
      myTarget.processDetached(process, closedByUser);
    }

    @Override
    public void processAttached(@NotNull DebugProcess process) {
      myTarget.processAttached(process);
    }

    @Override
    public void connectorIsReady() {
      myTarget.connectorIsReady();
    }

    @Override
    public void attachException(RunProfileState state, ExecutionException exception, RemoteConnection remoteConnection) {
      myTarget.attachException(state, exception, remoteConnection);
    }

    private static void pauseExecution() {
      TimeoutUtil.sleep(10);
    }
  }

  protected class BreakpointProvider extends DebugProcessAdapterImpl {
    private final DebugProcessImpl myDebugProcess;
    private final List<SuspendContextRunnable> myRepeatingRunnables = new ArrayList<>();
    private final Queue<SuspendContextRunnable> myScriptRunnables = new ArrayDeque<>();

    public BreakpointProvider(DebugProcessImpl debugProcess) {
      myDebugProcess = debugProcess;
    }

    public void onBreakpoint(SuspendContextRunnable runnable) {
      myScriptRunnables.add(runnable);
    }

    public void onEveryBreakpoint(SuspendContextRunnable runnable) {
      myRepeatingRunnables.add(runnable);
    }

    @Override
    public void paused(SuspendContextImpl suspendContext) {
      // Need to add SuspendContextCommandImpl because the stepping pause is not now in SuspendContextCommandImpl
      DebuggerManagerThreadImpl debuggerManagerThread = suspendContext.getManagerThread();
      debuggerManagerThread.invoke(new SuspendContextCommandImpl(suspendContext) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          pausedImpl(suspendContext);
        }
      });
    }

    private void pausedImpl(SuspendContextImpl suspendContext) {
      try {
        if (myScriptRunnables.isEmpty() && myRepeatingRunnables.isEmpty()) {
          print("resuming ", ProcessOutputTypes.SYSTEM);
          printContext(suspendContext);
          resume(suspendContext);
          return;
        }
        SuspendContextRunnable suspendContextRunnable = myScriptRunnables.poll();
        if (suspendContextRunnable != null) {
          suspendContextRunnable.run(suspendContext);
        }
        for (SuspendContextRunnable it : myRepeatingRunnables) {
          it.run(suspendContext);
        }
      }
      catch (Exception e) {
        addException(e);
        error(e);
      }
      catch (AssertionError e) {
        addException(e);
        resume(suspendContext);
      }
    }

    //executed in manager thread
    @Override
    public void resumed(SuspendContextImpl suspendContext) {
      SuspendContextImpl pausedContext = myDebugProcess.getSuspendManager().getPausedContext();
      if (pausedContext != null) {
        suspendContext.getManagerThread().schedule(new SuspendContextCommandImpl(pausedContext) {
          @Override
          public void contextAction(@NotNull SuspendContextImpl suspendContext) {
            paused(pausedContext);
          }
        });
      }
    }
  }
}
