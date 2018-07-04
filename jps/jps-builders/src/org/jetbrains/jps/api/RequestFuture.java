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
package org.jetbrains.jps.api;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
* @author Eugene Zhuravlev
*/
public class RequestFuture<T> extends BasicFuture<T> {
  private final T myHandler;
  private final UUID myRequestID;
  @Nullable private final CancelAction<T> myCancelAction;

  public interface CancelAction<T> {
    void cancel(RequestFuture<T> future) throws Exception;
  }

  public RequestFuture(T handler, UUID requestID, @Nullable CancelAction<T> cancelAction) {
    super();
    myCancelAction = cancelAction;
    myHandler = handler;
    myRequestID = requestID;
  }

  public UUID getRequestID() {
    return myRequestID;
  }

  public T getMessageHandler() {
    return myHandler;
  }

  @Override
  protected void performCancel() throws Exception {
    if (myCancelAction != null) {
      myCancelAction.cancel(this);
    }
  }
}
