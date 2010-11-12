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
package com.intellij.debugger.engine.evaluation;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.InvocationException;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.Nullable;

public class EvaluateException extends Exception {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.EvaluateException");
  private ObjectReference myTargetException;

  public EvaluateException(final String message) {
    super(message);
    if (LOG.isDebugEnabled()) {
      LOG.debug(message);
    }
  }

  public EvaluateException(String msg, Throwable th) {
    super(msg, th);
    if (th instanceof EvaluateException) {
      myTargetException = ((EvaluateException)th).getExceptionFromTargetVM();
    }
    else if(th instanceof InvocationException){
      InvocationException invocationException = (InvocationException) th;
      myTargetException = invocationException.exception();
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(msg);
    }
  }
  
  @Nullable
  public ObjectReference getExceptionFromTargetVM() {
    return myTargetException;
  }

  public void setTargetException(final ObjectReference targetException) {
    myTargetException = targetException;
  }

  public String getMessage() {
    final String errorMessage = super.getMessage();
    if (errorMessage != null) {
      return errorMessage;
    }
    final Throwable cause = getCause();
    final String causeMessage = cause != null? cause.getMessage() : null;
    if (causeMessage != null) {
      return causeMessage;
    }
    return "unknown error";
  }
}