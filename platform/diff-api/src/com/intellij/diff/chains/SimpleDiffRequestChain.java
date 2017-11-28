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

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SimpleDiffRequestChain extends UserDataHolderBase implements DiffRequestChain {
  @NotNull private final List<DiffRequestProducerWrapper> myRequests;
  private int myIndex = 0;

  public SimpleDiffRequestChain(@NotNull DiffRequest request) {
    this(Collections.singletonList(request));
  }

  public SimpleDiffRequestChain(@NotNull List<? extends DiffRequest> requests) {
    myRequests = ContainerUtil.map(requests, request -> new DiffRequestProducerWrapper(request));
  }

  @Override
  @NotNull
  public List<DiffRequestProducerWrapper> getRequests() {
    return myRequests;
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Override
  public void setIndex(int index) {
    assert index >= 0 && index < myRequests.size();
    myIndex = index;
  }

  public static class DiffRequestProducerWrapper implements DiffRequestProducer {
    @NotNull private final DiffRequest myRequest;

    public DiffRequestProducerWrapper(@NotNull DiffRequest request) {
      myRequest = request;
    }

    @NotNull
    public DiffRequest getRequest() {
      return myRequest;
    }

    @NotNull
    @Override
    public String getName() {
      return StringUtil.notNullize(myRequest.getTitle(), "Change");
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws ProcessCanceledException {
      return myRequest;
    }
  }
}
