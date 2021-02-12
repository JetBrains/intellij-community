/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.chains;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface DiffRequestChain extends UserDataHolder {
  @NotNull
  @RequiresEdt
  List<? extends DiffRequestProducer> getRequests();

  @RequiresEdt
  int getIndex();

  /**
   * @see com.intellij.diff.impl.CacheDiffRequestChainProcessor#setCurrentRequest
   * @deprecated This method will not change selected position if chain was already shown.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @RequiresEdt
  void setIndex(int index);
}
