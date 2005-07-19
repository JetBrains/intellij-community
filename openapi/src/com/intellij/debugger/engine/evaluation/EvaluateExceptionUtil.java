/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.debugger.engine.evaluation;

import com.sun.jdi.*;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 8, 2004
 * Time: 4:38:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class EvaluateExceptionUtil {
  public static final EvaluateException INCONSISTEND_DEBUG_INFO = createEvaluateException("Debug information is inconsistent");
  public static final EvaluateException BOOLEAN_EXPECTED = createEvaluateException("'boolean' value expected in condition");
  public static final EvaluateException PROCESS_EXITED = createEvaluateException("Cannot evaluate: process exited");
  public static final EvaluateException NULL_STACK_FRAME = createEvaluateException("Stack frame unavailable");
  public static final EvaluateException NESTED_EVALUATION_ERROR = createEvaluateException("Evaluation is not supported during another method's evaluation");
  public static final EvaluateException INVALID_DEBUG_INFO = createEvaluateException("Sources do not correspond executed code");
  public static final EvaluateException CANNOT_FIND_SOURCE_CLASS = createEvaluateException("Cannot find source class for current stack frame ");
  public static final EvaluateException INVALID_DESCENTANTS_EXPRESSION = createEvaluateException("Descendants expression should be of reference type");
  public static final EvaluateException OBJECT_WAS_COLLECTED = createEvaluateException("Object was collected");
  public static final EvaluateException ARRAY_WAS_COLLECTED = createEvaluateException("Array was collected");
  public static final EvaluateException THREAD_WAS_RESUMED = createEvaluateException("Thread was resumed");
  public static final EvaluateException DEBUG_INFO_UNAVAILABLE = createEvaluateException("Debug info unavailable");

  public static final EvaluateException INVALID_EXPRESSION(String expression) {
    return createEvaluateException("Invalid expression : " + expression);
  }

  public static final EvaluateException UNKNOWN_TYPE(String expression) {
    return createEvaluateException("Expression type unknown for : " + expression);
  }

  public static EvaluateException createEvaluateException(Throwable th) {
    return new EvaluateException(reason(th), th instanceof EvaluateException ? th.getCause() : th);
  }

  public static EvaluateException createEvaluateException(String msg, Throwable th) {
    return new EvaluateException(msg + ": " + reason(th), th instanceof EvaluateException ? th.getCause() : th);
  }

  public static EvaluateException createEvaluateException(String reason) {
    return new EvaluateException(reason, null);
  }

  private static String reason(Throwable th) {
    if(th instanceof InvalidTypeException) {
      return "Type mistmatch";
    }
    else if(th instanceof AbsentInformationException) {
      return "Debug info unavailable";
    }
    else if(th instanceof ClassNotLoadedException) {
      return "Class '" + ((ClassNotLoadedException)th).className() + "' is not loaded";
    }
    else if(th instanceof ClassNotPreparedException) {
      return th.getMessage();
    }
    else if(th instanceof IncompatibleThreadStateException) {
      return "Cannot evaluate: thread is not paused at breakpoint";
    }
    else if(th instanceof InconsistentDebugInfoException) {
      return "Debug info inconsistent";      
    }
    else if(th instanceof ObjectCollectedException) {
      return "Object was collected";
    }
    else if(th instanceof InvocationException){
      InvocationException invocationException = (InvocationException) th;
      return "Method threw '" + invocationException.exception().referenceType().name() + "' exception.";
    }
    else if(th instanceof EvaluateException) {
      return th.getMessage();
    }
    else {
      return th.getClass().getName() + " : " + (th.getMessage() != null ? th.getMessage() : "");
    }
  }
}
