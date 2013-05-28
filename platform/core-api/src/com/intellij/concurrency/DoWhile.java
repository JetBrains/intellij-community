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
package com.intellij.concurrency;

import org.jetbrains.annotations.NotNull;

/**
 * Author: dmitrylomov
 */
public abstract class DoWhile  {
  private AsyncFutureResult<Boolean> myResult;
  private SameThreadExecutorWithTrampoline myExecutor;

  public DoWhile() {
  }

  @NotNull
  public AsyncFutureResult<Boolean> getResult() {
    if (myResult == null) {
      myExecutor = new SameThreadExecutorWithTrampoline();
      myResult = AsyncFutureFactory.getInstance().createAsyncFutureResult();
      body().addConsumer(myExecutor, new MyConsumer());
    }
    return myResult;
  }

  @NotNull
  protected abstract AsyncFuture<Boolean> body();
  protected abstract boolean condition();

  private class MyConsumer extends DefaultResultConsumer<Boolean> {
    public MyConsumer() {
      super(DoWhile.this.myResult);
    }

    @Override
    public void onSuccess(Boolean value) {
      if (!value.booleanValue()) {
        myResult.set(false);
      }
      else {
        if(!condition()) {
          myResult.set(true);
        }
        else {
          body().addConsumer(myExecutor, this);
        }
      }
    }
  }


}
