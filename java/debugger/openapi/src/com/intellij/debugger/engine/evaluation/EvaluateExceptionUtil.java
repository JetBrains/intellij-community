// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.*;

/**
 * @author lex
 */
public final class EvaluateExceptionUtil {
  public static final EvaluateException INCONSISTEND_DEBUG_INFO = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.inconsistent.debug.info"));
  public static final EvaluateException BOOLEAN_EXPECTED = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.boolean.value.expected.in.condition"));
  public static final EvaluateException PROCESS_EXITED = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.process.exited"));
  public static final EvaluateException NULL_STACK_FRAME = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.stack.frame.unavailable"));
  public static final EvaluateException NESTED_EVALUATION_ERROR = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.nested.evaluation"));
  public static final EvaluateException INVALID_DEBUG_INFO = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.sources.out.of.sync"));
  public static final EvaluateException CANNOT_FIND_SOURCE_CLASS = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.cannot.find.stackframe.source"));
  public static final EvaluateException OBJECT_WAS_COLLECTED = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.object.collected"));
  public static final EvaluateException ARRAY_WAS_COLLECTED = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.array.collected"));
  public static final EvaluateException THREAD_WAS_RESUMED = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.thread.resumed"));
  public static final EvaluateException DEBUG_INFO_UNAVAILABLE = createEvaluateException(
    JavaDebuggerBundle.message("evaluation.error.debug.info.unavailable"));

  private EvaluateExceptionUtil() {
  }

  public static EvaluateException createEvaluateException(Throwable th) {
    return createEvaluateException(null, th);
  }

  public static EvaluateException createEvaluateException(String msg, Throwable th) {
    String message = msg != null ? msg + ": " + reason(th) : reason(th);
    if (th instanceof EvaluateException) {
      th = th.getCause();
    }
    if (th instanceof AbsentInformationException) {
      return new AbsentInformationEvaluateException(message, th);
    }
    return new EvaluateException(message, th);
  }

  public static EvaluateException createEvaluateException(String reason) {
    return new EvaluateException(reason, null);
  }

  private static String reason(Throwable th) {
    if(th instanceof InvalidTypeException) {
      final String originalReason = th.getMessage();
      return JavaDebuggerBundle.message("evaluation.error.type.mismatch") + (originalReason != null ? " " + originalReason : "");
    }
    else if(th instanceof AbsentInformationException) {
      return JavaDebuggerBundle.message("evaluation.error.debug.info.unavailable");
    }
    else if(th instanceof ClassNotLoadedException) {
      return JavaDebuggerBundle.message("evaluation.error.class.not.loaded", ((ClassNotLoadedException)th).className());
    }
    else if(th instanceof ClassNotPreparedException) {
      return th.getMessage();
    }
    else if(th instanceof IncompatibleThreadStateException) {
      return JavaDebuggerBundle.message("evaluation.error.thread.not.at.breakpoint");
    }
    else if(th instanceof InconsistentDebugInfoException) {
      return JavaDebuggerBundle.message("evaluation.error.inconsistent.debug.info");
    }
    else if(th instanceof ObjectCollectedException) {
      return JavaDebuggerBundle.message("evaluation.error.object.collected");
    }
    else if(th instanceof InvocationException){
      InvocationException invocationException = (InvocationException) th;
      return JavaDebuggerBundle.message("evaluation.error.method.exception", invocationException.exception().referenceType().name());
    }
    else if(th instanceof EvaluateException) {
      return th.getMessage();
    }
    else {
      StringBuilder res = new StringBuilder(th.getClass().getName());
      String message = th.getMessage();
      if (!StringUtil.isEmpty(message)) {
        res.append(" : ").append(message);
      }
      return res.toString();
    }
  }
}
