/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.application;

public abstract class BaseActionRunnable<T> {

  private boolean mySilentExecution;

  public boolean isSilentExecution() {
    return mySilentExecution;
  }

  protected abstract void run(Result<T> result) throws Throwable;

  public abstract RunResult<T> execute();

  protected boolean canWriteNow() {
    return getApplication().isWriteAccessAllowed();
  }

  protected boolean canReadNow() {
    return getApplication().isReadAccessAllowed();
  }

  protected Application getApplication() {
    return ApplicationManager.getApplication();
  }

  /** Same as execute() but do not log error if exception occurred. */
  public final RunResult<T> executeSilently() {
    mySilentExecution = true;
    return execute();
  }

}