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
package com.intellij.debugger.impl;

/**
 * @author lex
 */
public abstract class InvokeAndWaitThread<E extends DebuggerTask> extends InvokeThread<E> {

  public InvokeAndWaitThread() {
    super();
  }

  /**
   * !!! Do not remove this code !!!
   * Otherwise it will be impossible to override schedule method
   */
  public void schedule(E e) {
    super.schedule(e);
  }

  public void pushBack(E e) {
    super.pushBack(e);
  }

  public void invokeAndWait(final E runnable) {
    runnable.hold();
    schedule(runnable);
    runnable.waitFor();
  }
}

