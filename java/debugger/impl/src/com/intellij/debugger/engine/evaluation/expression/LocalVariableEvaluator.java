// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class LocalVariableEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.SimpleStackFrameContext;
import com.intellij.debugger.jdi.*;
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

class LocalVariableEvaluator implements ModifiableEvaluator {
  private static final Logger LOG = Logger.getInstance(LocalVariableEvaluator.class);

  private final String myLocalVariableName;
  private final boolean myCanScanFrames;

  // TODO remove non-final fields, see IDEA-366793
  @Deprecated
  private EvaluationContextImpl myContext;
  @Deprecated
  private LocalVariableProxyImpl myEvaluatedVariable;
  @Deprecated
  private DecompiledLocalVariable myEvaluatedDecompiledVariable;

  LocalVariableEvaluator(String localVariableName, boolean canScanFrames) {
    myLocalVariableName = localVariableName;
    myCanScanFrames = canScanFrames;
  }

  @Override
  public @NotNull ModifiableValue evaluateModifiable(EvaluationContextImpl context) throws EvaluateException {
    StackFrameProxyImpl frameProxy = context.getFrameProxy();
    if (frameProxy == null) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.no.stackframe"));
    }

    try {
      ThreadReferenceProxyImpl threadProxy = null;
      int lastFrameIndex = -1;
      PsiVariable variable = null;
      DebugProcessImpl process = context.getDebugProcess();

      boolean topFrame = true;

      while (true) {
        try {
          LocalVariableProxyImpl local = frameProxy.visibleVariableByName(myLocalVariableName);
          if (local != null) {
            if (topFrame ||
                variable.equals(resolveVariable(frameProxy, myLocalVariableName, context.getProject(), process))) {
              myEvaluatedVariable = local;
              myContext = context;
              return new ModifiableValue(frameProxy.getValue(local), new MyModifier(context, local, null));
            }
          }
        }
        catch (EvaluateException e) {
          if (!(e.getCause() instanceof AbsentInformationException)) {
            throw e;
          }

          // try to look in slots
          try {
            Map<DecompiledLocalVariable, Value> vars = LocalVariablesUtil.fetchValues(frameProxy, process, true);
            for (Map.Entry<DecompiledLocalVariable, Value> entry : vars.entrySet()) {
              DecompiledLocalVariable var = entry.getKey();
              if (var.getMatchedNames().contains(myLocalVariableName) || var.getDefaultName().equals(myLocalVariableName)) {
                myEvaluatedDecompiledVariable = var;
                myContext = context;
                return new ModifiableValue(entry.getValue(), new MyModifier(context, null, var));
              }
            }
          }
          catch (Exception e1) {
            LOG.info(e1);
          }
        }

        if (myCanScanFrames) {
          if (topFrame) {
            variable = resolveVariable(frameProxy, myLocalVariableName, context.getProject(), process);
            if (variable == null) break;
          }
          if (threadProxy == null /* initialize it lazily */) {
            threadProxy = frameProxy.threadProxy();
            lastFrameIndex = threadProxy.frameCount() - 1;
          }
          int currentFrameIndex = frameProxy.getFrameIndex();
          if (currentFrameIndex < lastFrameIndex) {
            frameProxy = threadProxy.frame(currentFrameIndex + 1);
            if (frameProxy != null) {
              topFrame = false;
              continue;
            }
          }
        }

        break;
      }
      throw EvaluateExceptionUtil.createEvaluateException(
        JavaDebuggerBundle.message("evaluation.error.local.variable.missing", myLocalVariableName));
    }
    catch (EvaluateException e) {
      myEvaluatedVariable = null;
      myContext = null;
      throw e;
    }
  }

  @Override
  public Modifier getModifier() {
    if ((myEvaluatedVariable != null || myEvaluatedDecompiledVariable != null) && myContext != null) {
      return new MyModifier(myContext, myEvaluatedVariable, myEvaluatedDecompiledVariable);
    }
    return null;
  }

  private static @Nullable PsiVariable resolveVariable(final StackFrameProxy frame,
                                                       final String name,
                                                       final Project project,
                                                       final DebugProcess process) {
    PsiElement place = ContextUtil.getContextElement(new SimpleStackFrameContext(frame, process));
    if (place == null) {
      return null;
    }
    return ReadAction.compute(() ->
      JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(name, place));
  }

  @Override
  public String toString() {
    return myLocalVariableName;
  }

  private static class MyModifier implements Modifier {
    private final EvaluationContextImpl myContext;
    private final LocalVariableProxyImpl myEvaluatedVariable;
    private final DecompiledLocalVariable myEvaluatedDecompiledVariable;

    private MyModifier(EvaluationContextImpl context,
                       LocalVariableProxyImpl evaluatedVariable,
                       DecompiledLocalVariable evaluatedDecompiledVariable) {
      this.myContext = context;
      this.myEvaluatedVariable = evaluatedVariable;
      this.myEvaluatedDecompiledVariable = evaluatedDecompiledVariable;
    }

    @Override
    public boolean canInspect() {
      return true;
    }

    @Override
    public boolean canSetValue() {
      return true;
    }

    @Override
    public void setValue(Value value) throws ClassNotLoadedException, InvalidTypeException {
      StackFrameProxyImpl frameProxy = myContext.getFrameProxy();
      try {
        assert frameProxy != null;
        if (myEvaluatedVariable != null) {
          frameProxy.setValue(myEvaluatedVariable, value);
        }
        else { // no debug info
          LocalVariablesUtil.setValue(frameProxy.getStackFrame(), myEvaluatedDecompiledVariable, value);
        }
      }
      catch (EvaluateException e) {
        LOG.error(e);
      }
    }

    @Override
    public Type getExpectedType() throws ClassNotLoadedException {
      try {
        return myEvaluatedVariable.getType();
      }
      catch (EvaluateException e) {
        LOG.error(e);
        return null;
      }
    }

    @Override
    public NodeDescriptorImpl getInspectItem(Project project) {
      return new LocalVariableDescriptorImpl(project, myEvaluatedVariable);
    }
  }
}
