// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.*;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValueModifier;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/*
 * Class SetValueAction
 * @author Jeka
 */
public abstract class JavaValueModifier extends XValueModifier {
  private final JavaValue myJavaValue;

  public JavaValueModifier(JavaValue javaValue) {
    myJavaValue = javaValue;
  }

  @Override
  public void calculateInitialValueEditorText(final XInitialValueCallback callback) {
    final Value value = myJavaValue.getDescriptor().getValue();
    if (value == null || value instanceof PrimitiveValue) {
      String valueString = myJavaValue.getValueString();
      int pos = valueString.lastIndexOf('('); //skip hex presentation if any
      if (pos > 1) {
        valueString = valueString.substring(0, pos).trim();
      }
      callback.setValue(valueString);
    }
    else if (value instanceof StringReference) {
      final EvaluationContextImpl evaluationContext = myJavaValue.getEvaluationContext();
      evaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(evaluationContext.getSuspendContext()) {
        @Override
        public Priority getPriority() {
          return Priority.NORMAL;
        }

        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
          callback.setValue(
            StringUtil.wrapWithDoubleQuote(DebuggerUtils.translateStringValue(DebuggerUtils.getValueAsString(evaluationContext, value))));
        }
      });
    }
    else {
      callback.setValue(null);
    }
  }

  protected static void update(final DebuggerContextImpl context) {
    DebuggerInvocationUtil.swingInvokeLater(context.getProject(), () -> {
      final DebuggerSession session = context.getDebuggerSession();
      if (session != null) {
        session.refresh(false);
      }
    });
    //node.setState(context);
  }

  protected abstract void setValueImpl(@NotNull XExpression expression, @NotNull XModificationCallback callback);

  @Override
  public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
    final ValueDescriptorImpl descriptor = myJavaValue.getDescriptor();
    if(!descriptor.canSetValue()) {
      return;
    }

    if (myJavaValue.getEvaluationContext().getSuspendContext().isResumed()) {
      callback.errorOccurred(DebuggerBundle.message("error.context.has.changed"));
      return;
    }

    setValueImpl(expression, callback);
  }

  protected static Value preprocessValue(EvaluationContextImpl context, Value value, @NotNull Type varType) throws EvaluateException {
    if (value != null && JAVA_LANG_STRING.equals(varType.name()) && !(value instanceof StringReference)) {
      String v = DebuggerUtils.getValueAsString(context, value);
      if (v != null) {
        value = DebuggerUtilsEx.mirrorOfString(v, context.getDebugProcess().getVirtualMachineProxy(), context);
      }
    }
    if (value instanceof DoubleValue) {
      double dValue = ((DoubleValue) value).doubleValue();
      if(varType instanceof FloatType && Float.MIN_VALUE <= dValue && dValue <= Float.MAX_VALUE){
        value = context.getDebugProcess().getVirtualMachineProxy().mirrorOf((float)dValue);
      }
    }
    if (value != null) {
      if (varType instanceof PrimitiveType) {
        if (!(value instanceof PrimitiveValue)) {
          value = (Value)UnBoxingEvaluator.unbox(value, context);
        }
      }
      else if (varType instanceof ReferenceType) {
        if (value instanceof PrimitiveValue) {
          value = (Value)new BoxingEvaluator(new IdentityEvaluator(value)).evaluate(context);
        }
      }
    }
    return value;
  }

  protected interface SetValueRunnable {
    void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                          InvalidTypeException,
                                                                                          EvaluateException,
                                                                                          IncompatibleThreadStateException;

    default ClassLoaderReference getClassLoader(EvaluationContextImpl evaluationContext) throws EvaluateException {
      return evaluationContext.getClassLoader();
    }

    @Nullable
    Type getLType() throws ClassNotLoadedException, EvaluateException;
  }

  @Nullable
  private static ExpressionEvaluator tryDirectAssignment(@NotNull XExpression expression,
                                                         @Nullable Type varType,
                                                         @NotNull EvaluationContextImpl evaluationContext) {
    if (varType instanceof LongType) {
      try {
        return new ExpressionEvaluatorImpl(new IdentityEvaluator(
          evaluationContext.getDebugProcess().getVirtualMachineProxy().mirrorOf(Long.decode(expression.getExpression()))));
      }
      catch (NumberFormatException ignored) {
      }
    }
    return null;
  }

  private static void setValue(ExpressionEvaluator evaluator, EvaluationContextImpl evaluationContext, SetValueRunnable setValueRunnable) throws EvaluateException {
    Value value;
    try {
      value = evaluator.evaluate(evaluationContext);

      setValueRunnable.setValue(evaluationContext, value);
    }
    catch (IllegalArgumentException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(ex.getMessage());
    }
    catch (InvalidTypeException ex) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.type.mismatch"));
    }
    catch (IncompatibleThreadStateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (ClassNotLoadedException ex) {
      if (!evaluationContext.isAutoLoadClasses()) {
        throw EvaluateExceptionUtil.createEvaluateException(ex);
      }
      final ReferenceType refType;
      try {
        refType = evaluationContext.getDebugProcess().loadClass(evaluationContext,
                                                                ex.className(),
                                                                setValueRunnable.getClassLoader(evaluationContext));
        if (refType != null) {
          //try again
          setValue(evaluator, evaluationContext, setValueRunnable);
        }
      }
      catch (InvocationException | InvalidTypeException | IncompatibleThreadStateException | ClassNotLoadedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (ObjectCollectedException e) {
        throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
      }
    }
  }

  protected void set(@NotNull final XExpression expression,
                     final XModificationCallback callback,
                     final DebuggerContextImpl debuggerContext,
                     final SetValueRunnable setValueRunnable) {
    final ProgressWindow progressWindow = new ProgressWindow(true, debuggerContext.getProject());
    final EvaluationContextImpl evaluationContext = myJavaValue.getEvaluationContext();

    SuspendContextCommandImpl askSetAction = new DebuggerContextCommandImpl(debuggerContext) {
      public Priority getPriority() {
        return Priority.HIGH;
      }

      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        ExpressionEvaluator evaluator;
        try {
          evaluator = tryDirectAssignment(expression, setValueRunnable.getLType(), evaluationContext);

          if (evaluator == null) {
            Project project = evaluationContext.getProject();
            SourcePosition position = ContextUtil.getSourcePosition(evaluationContext);
            PsiElement context = ContextUtil.getContextElement(evaluationContext, position);
            evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable<ExpressionEvaluator>() {
              public ExpressionEvaluator compute() throws EvaluateException {
                return EvaluatorBuilderImpl
                  .build(TextWithImportsImpl.fromXExpression(expression), context, position, project);
              }
            });
          }

          setValue(evaluator, evaluationContext, new SetValueRunnable() {
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                                 InvalidTypeException,
                                                                                                 EvaluateException,
                                                                                                 IncompatibleThreadStateException {
              if (!progressWindow.isCanceled()) {
                setValueRunnable.setValue(evaluationContext, newValue);
                //node.calcValue();
              }
            }

            @Nullable
            @Override
            public Type getLType() throws EvaluateException, ClassNotLoadedException {
              return setValueRunnable.getLType();
            }
          });
          callback.valueModified();
        }
        catch (EvaluateException | ClassNotLoadedException e) {
          callback.errorOccurred(e.getMessage());
        }
      }
    };

    progressWindow.setTitle(DebuggerBundle.message("title.evaluating"));
    evaluationContext.getDebugProcess().getManagerThread().startProgress(askSetAction, progressWindow);
  }
}
