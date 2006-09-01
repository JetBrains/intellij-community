/*
 * Class Breakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.LocatableEvent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;

public abstract class Breakpoint extends FilteredRequestor implements ClassPrepareRequestor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.Breakpoint");

  public boolean ENABLED = true;
  public String  SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;
  public boolean LOG_ENABLED = false;
  public boolean LOG_EXPRESSION_ENABLED = false;
  private TextWithImports  myLogMessage; // an expression to be evaluated and printed
  private static final @NonNls String LOG_MESSAGE_OPTION_NAME = "LOG_MESSAGE";
  public static final Breakpoint[] EMPTY_ARRAY = new Breakpoint[0];

  protected Breakpoint(Project project) {
    super(project);
    myLogMessage = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "");
  }

  public abstract PsiClass getPsiClass();
  /**
   * Request for creating all needed JPDA requests in the specified VM
   * @param debuggerProcess the requesting process
   */
  public abstract void createRequest(DebugProcessImpl debuggerProcess);

  /**
   * Request for creating all needed JPDA requests in the specified VM
   * @param debuggerProcess the requesting process
   */
  public abstract void processClassPrepare(DebugProcess debuggerProcess, final ReferenceType referenceType);

  public abstract String getDisplayName ();

  public @Nullable String getClassName() {
    return null;
  }

  public @Nullable String getShortClassName() {
    final String className = getClassName();
    if (className != null) {
      final int dotIndex = className.lastIndexOf('.');
      return dotIndex >= 0 && dotIndex + 1 < className.length()? className.substring(dotIndex + 1) : className;
    }
    return className;
  }

  public @Nullable String getPackageName() {
    return null;
  }

  public abstract Icon getIcon();

  public abstract void reload();

  /**
   * returns UI representation
   */
  public abstract String getEventMessage(LocatableEvent event);

  public abstract boolean isValid();

  public abstract String getCategory();

  /**
   * Associates breakpoint with class.
   *    Create requests for loaded class and registers callback for loading classes
   * @param debugProcess the requesting process
   */
  protected void createOrWaitPrepare(DebugProcessImpl debugProcess, String classToBeLoaded) {
    debugProcess.getRequestsManager().callbackOnPrepareClasses(this, classToBeLoaded);

    List list = debugProcess.getVirtualMachineProxy().classesByName(classToBeLoaded);
    for (Iterator it = list.iterator(); it.hasNext();) {
      ReferenceType refType = (ReferenceType)it.next();
      if (refType.isPrepared()) {
        processClassPrepare(debugProcess, refType);
      }
    }
  }

  protected void createOrWaitPrepare(final DebugProcessImpl debugProcess, final SourcePosition classPosition) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        debugProcess.getRequestsManager().callbackOnPrepareClasses(Breakpoint.this, classPosition);

        List list = debugProcess.getPositionManager().getAllClasses(classPosition);
        for (Iterator it = list.iterator(); it.hasNext();) {
          ReferenceType refType = (ReferenceType)it.next();
          if (refType.isPrepared()) {
            processClassPrepare(debugProcess, refType);
          }
        }
      }
    });
  }

  protected ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) throws EvaluateException {
    ThreadReferenceProxyImpl thread = context.getThread();
    if(thread != null) {
      StackFrameProxyImpl stackFrameProxy = thread.frame(0);
      if(stackFrameProxy != null) {
        return stackFrameProxy.thisObject();
      }
    }
    return null;
  }

  // returns whether should resume
  public boolean processLocatableEvent(final SuspendContextCommandImpl action, final LocatableEvent event) {
    final SuspendContextImpl context = action.getSuspendContext();
    if(!isValid()) {
      context.getDebugProcess().getRequestsManager().deleteRequest(this);
      return true;
    }

    final String[] title = new String[] {DebuggerBundle.message("title.error.evaluating.breakpoint.condition") };

    try {
      final StackFrameProxyImpl frameProxy = context.getThread().frame(0);
      if (frameProxy == null) {
        // might be if the thread has been collected
        return true;
      }

      final EvaluationContextImpl evaluationContext = new EvaluationContextImpl(
        action.getSuspendContext(),
        frameProxy,
        getThisObject(context, event)
      );

      if(!evaluateCondition(evaluationContext, event)) {
        return true;
      }

      title[0] = DebuggerBundle.message("title.error.evaluating.breakpoint.action");
      runAction(evaluationContext, event);
    }
    catch (final EvaluateException ex) {
      if(ApplicationManager.getApplication().isUnitTestMode()) {
        System.out.println(ex.getMessage());
        return true;
      }

      final boolean[] shouldResume = new boolean[]{true};
      DebuggerInvocationUtil.invokeAndWait(getProject(), new Runnable() {
          public void run() {
            DebuggerSession session = DebuggerManagerEx.getInstanceEx(getProject()).getSession(context.getDebugProcess());
            DebuggerPanelsManager.getInstance(getProject()).toFront(session);
            final String text = DebuggerBundle.message("error.evaluating.breakpoint.condition.or.action", getDisplayName(), ex.getMessage());
            if (LOG.isDebugEnabled()) {
              LOG.debug(text);
            }
            shouldResume[0] = Messages.showYesNoDialog(getProject(), text, title[0], Messages.getQuestionIcon()) != 0;
          }
        }, ModalityState.NON_MMODAL);

      return shouldResume[0];
    } 

    return false;
  }

  private void runAction(final EvaluationContextImpl context, LocatableEvent event) {
    if (LOG_ENABLED || LOG_EXPRESSION_ENABLED) {
      final StringBuilder buf = new StringBuilder(128);
      if (LOG_ENABLED) {
        buf.append(getEventMessage(event));
        buf.append("\n");
      }
      final DebugProcessImpl debugProcess = context.getDebugProcess();
      if (LOG_EXPRESSION_ENABLED && getLogMessage() != null && !"".equals(getLogMessage())) {
        if(!debugProcess.isAttached()) {
          return;
        }

        String result;
        try {
          ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(getProject(), new com.intellij.debugger.EvaluatingComputable<ExpressionEvaluator>() {
            public ExpressionEvaluator compute() throws EvaluateException {
              return EvaluatorBuilderImpl.getInstance().build(getLogMessage(), ContextUtil.getContextElement(context));
            }
          });
          result = DebuggerUtilsEx.getValueAsString(context, evaluator.evaluate(context));
          buf.append(getLogMessage());
          buf.append(" = ");
          buf.append(result);
        }
        catch (EvaluateException e) {
          buf.append(DebuggerBundle.message("error.unable.to.evaluate.expression"));
          buf.append("\"");
          buf.append(getLogMessage());
          buf.append("\"");
          buf.append(" : ");
          buf.append(e.getMessage());
        }
        buf.append("\n");
      }
      if (buf.length() > 0) {
        debugProcess.printToConsole(buf.toString());
      }
    }
  }

  public final void updateUI() {
    updateUI(EmptyRunnable.getInstance());
  }

  public void updateUI(final Runnable afterUpdate) {
  }

  public void delete() {
    RequestManagerImpl.deleteRequests(this);
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    super.readExternal(parentNode);
    String logMessage = JDOMExternalizerUtil.readField(parentNode, LOG_MESSAGE_OPTION_NAME);
    if (logMessage != null) {
      setLogMessage(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, logMessage));
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    super.writeExternal(parentNode);
    JDOMExternalizerUtil.writeField(parentNode, LOG_MESSAGE_OPTION_NAME, getLogMessage().toString());
  }

  public TextWithImports getLogMessage() {
    return myLogMessage;
  }

  public void setLogMessage(TextWithImports logMessage) {
    myLogMessage = logMessage;
  }
}
