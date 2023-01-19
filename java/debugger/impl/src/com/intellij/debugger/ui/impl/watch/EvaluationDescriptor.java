// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.JavaValueModifier;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.engine.evaluation.expression.UnsupportedExpressionException;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValueModifier;
import com.sun.jdi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class EvaluationDescriptor extends ValueDescriptorImpl {
  private Modifier myModifier;
  protected TextWithImports myText;

  protected EvaluationDescriptor(TextWithImports text, Project project, Value value) {
    super(project, value);
    myText = text;
  }

  protected EvaluationDescriptor(TextWithImports text, Project project) {
    super(project);
    setLvalue(false);
    myText = text;
  }

  protected abstract EvaluationContextImpl getEvaluationContext(EvaluationContextImpl evaluationContext);

  protected abstract PsiCodeFragment getEvaluationCode(StackFrameContext context) throws EvaluateException;

  public PsiCodeFragment createCodeFragment(PsiElement context) {
    TextWithImports text = getEvaluationText();
    return DebuggerUtilsEx.findAppropriateCodeFragmentFactory(text, context).createCodeFragment(text, context, myProject);
  }

  @ApiStatus.Experimental
  protected @NotNull ExpressionEvaluator getEvaluator(EvaluationContextImpl evaluationContext) throws EvaluateException {
    SourcePosition position = ContextUtil.getSourcePosition(evaluationContext);
    return ReadAction.compute(() -> {
      PsiCodeFragment code = getEvaluationCode(evaluationContext);
      PsiElement psiContext = ContextUtil.getContextElement(evaluationContext, position);
      try {
        return DebuggerUtilsEx.findAppropriateCodeFragmentFactory(getEvaluationText(), psiContext).getEvaluatorBuilder().build(code, position);
      }
      catch (UnsupportedExpressionException ex) {
        ExpressionEvaluator eval = CompilingEvaluatorImpl.create(myProject, code.getContext(), element -> code);
        if (eval != null) {
          return eval;
        }
        throw ex;
      }
    });
  }

  @Override
  public final Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    try {
      PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(() -> {});

      EvaluationContextImpl thisEvaluationContext = getEvaluationContext(evaluationContext);

      ExpressionEvaluator evaluator = getEvaluator(thisEvaluationContext);

      if (!thisEvaluationContext.getDebugProcess().isAttached()) {
        throw EvaluateExceptionUtil.PROCESS_EXITED;
      }
      StackFrameProxyImpl frameProxy = thisEvaluationContext.getFrameProxy();
      if (frameProxy == null) {
        throw EvaluateExceptionUtil.NULL_STACK_FRAME;
      }

      Value value = evaluator.evaluate(thisEvaluationContext);
      thisEvaluationContext.keep(value);

      myModifier = evaluator.getModifier();
      setLvalue(myModifier != null);

      return value;
    }
    catch (IndexNotReadyException ex) {
      throw new EvaluateException(JavaDebuggerBundle.message("evaluation.error.during.indexing"), ex);
    }
    catch (final EvaluateException ex) {
      throw new EvaluateException(ex.getLocalizedMessage(), ex);
    }
    catch (ObjectCollectedException ex) {
      throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
    }
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElement evaluationCode = getEvaluationCode(context);
    if (evaluationCode instanceof PsiExpressionCodeFragment) {
      return ((PsiExpressionCodeFragment)evaluationCode).getExpression();
    }
    else {
      throw new EvaluateException(JavaDebuggerBundle.message("error.cannot.create.expression.from.code.fragment"), null);
    }
  }

  @Override
  protected boolean isPrintExceptionToConsole() {
    return false;
  }

  @Nullable
  public Modifier getModifier() {
    return myModifier;
  }

  @Override
  public boolean canSetValue() {
    return super.canSetValue() && myModifier != null && myModifier.canSetValue();
  }

  public TextWithImports getEvaluationText() {
    return myText;
  }

  @Override
  public XValueModifier getModifier(JavaValue value) {
    return new JavaValueModifier(value) {
      @Override
      protected void setValueImpl(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
        final EvaluationDescriptor evaluationDescriptor = EvaluationDescriptor.this;
        if (evaluationDescriptor.canSetValue()) {
          final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
          set(expression, callback, debuggerContext, new SetValueRunnable() {
            @Override
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue)
              throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
              //noinspection ConstantConditions
              evaluationDescriptor.getModifier().setValue(preprocessValue(evaluationContext, newValue, getLType()));
              update(debuggerContext);
            }

            @NotNull
            @Override
            public Type getLType() throws EvaluateException, ClassNotLoadedException {
              //noinspection ConstantConditions
              return evaluationDescriptor.getModifier().getExpectedType();
            }
          });
        }
      }
    };
  }
}