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
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SimpleDiffRequestChain extends DiffRequestChainBase {
  @NotNull private final List<? extends DiffRequestProducer> myRequests;

  public SimpleDiffRequestChain(@NotNull DiffRequest request) {
    this(Collections.singletonList(request));
  }

  public SimpleDiffRequestChain(@NotNull List<? extends DiffRequest> requests) {
    myRequests = ContainerUtil.map(requests, request -> new DiffRequestProducerWrapper(request));
  }

  private SimpleDiffRequestChain(@NotNull List<? extends DiffRequestProducer> requests, @Nullable Object constructorFlag) {
    assert constructorFlag == null;
    myRequests = requests;
  }

  public static SimpleDiffRequestChain fromProducer(@NotNull DiffRequestProducer producer) {
    return fromProducers(Collections.singletonList(producer));
  }

  public static SimpleDiffRequestChain fromProducers(@NotNull List<? extends DiffRequestProducer> producers) {
    return fromProducers(producers, -1);
  }

  public static SimpleDiffRequestChain fromProducers(@NotNull List<? extends DiffRequestProducer> producers, int selectedIndex) {
    SimpleDiffRequestChain chain = new SimpleDiffRequestChain(producers, null);
    if (selectedIndex > 0) chain.setIndex(selectedIndex);
    return chain;
  }

  @Override
  @NotNull
  public List<? extends DiffRequestProducer> getRequests() {
    return myRequests;
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
      String title = myRequest.getTitle();
      if (title != null) return title;
      return DiffBundle.message("diff.files.generic.request.title");
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws ProcessCanceledException {
      return myRequest;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DiffRequestProducerWrapper wrapper = (DiffRequestProducerWrapper)o;
      return myRequest == wrapper.myRequest;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRequest);
    }
  }
}
