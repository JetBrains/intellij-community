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
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Computable;

import javax.swing.*;


public abstract class ActionRunner {
  public static  void runInsideWriteAction(final InterruptibleRunnable runnable) throws Exception {
    final Exception[] exception = new Exception[1];
    Runnable swingRunnable = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              runnable.run();
            }
            catch (Exception e) {
              exception[0] = e;
            }
          }
        });
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      swingRunnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(swingRunnable, ModalityState.NON_MODAL);
    }
    Exception e = exception[0];
    if (e != null) {
      if (e instanceof RuntimeException) throw (RuntimeException)e;
      throw new Exception(e);
    }
  }
  //public static <E extends Throwable> void runInsideWriteAction(final InterruptibleRunnable<E> runnable) throws E {
  //  runInsideWriteAction(new InterruptibleRunnableWithResult<E,Object>(){
  //    public Object run() throws E {
  //      runnable.run();
  //      return null;
  //    }
  //  });
  //}
  public static <T> T runInsideWriteAction(final InterruptibleRunnableWithResult<T> runnable) throws Exception {
    final Throwable[] exception = new Throwable[]{null};
    final T[] result = (T[])new Object[1];
    Runnable swingRunnable = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              result[0] = runnable.run();
            }
            catch (Exception e) {
              exception[0] = e;
            }
          }
        });
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      swingRunnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(swingRunnable, ModalityState.NON_MODAL);
    }
    Throwable e = exception[0];
    if (e != null) {
      if (e instanceof Exception) throw (Exception)e;
      throw new Exception(e);
    }
    return result[0];
  }

  public static void runInsideReadAction(final InterruptibleRunnable runnable) throws Exception {
    Throwable exception = ApplicationManager.getApplication().runReadAction(new Computable<Throwable>() {
      @Override
      public Throwable compute() {
        try {
          runnable.run();
          return null;
        }
        catch (Throwable e) {
          return e;
        }
      }
    });
    if (exception != null) {
      if (exception instanceof RuntimeException) {
        throw (RuntimeException)exception;
      }
      throw new Exception(exception);
    }
  }

  public static interface InterruptibleRunnable {
    void run() throws Exception;
  }
  public static interface InterruptibleRunnableWithResult <T> {
    T run() throws Exception;
  }
}