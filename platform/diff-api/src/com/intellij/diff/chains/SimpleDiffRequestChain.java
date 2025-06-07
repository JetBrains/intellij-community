// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.chains;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SimpleDiffRequestChain extends UserDataHolderBase implements DiffRequestSelectionChain {
  private final @NotNull ListSelection<? extends DiffRequestProducer> myRequests;

  public SimpleDiffRequestChain(@NotNull DiffRequest request) {
    this(Collections.singletonList(request));
  }

  public SimpleDiffRequestChain(@NotNull List<? extends DiffRequest> requests) {
    this(requests, 0);
  }

  public SimpleDiffRequestChain(@NotNull List<? extends DiffRequest> requests, int selectedIndex) {
    myRequests = ListSelection.createAt(requests, selectedIndex).map(request -> new DiffRequestProducerWrapper(request));
  }

  private SimpleDiffRequestChain(@NotNull ListSelection<? extends DiffRequestProducer> requests, @Nullable Object constructorFlag) {
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
    return fromProducers(ListSelection.createAt(producers, selectedIndex));
  }

  public static SimpleDiffRequestChain fromProducers(@NotNull ListSelection<? extends DiffRequestProducer> producers) {
    return new SimpleDiffRequestChain(producers, null);
  }

  @Override
  public @NotNull ListSelection<? extends DiffRequestProducer> getListSelection() {
    return myRequests;
  }

  public static class DiffRequestProducerWrapper implements DiffRequestProducer {
    private final @NotNull DiffRequest myRequest;

    public DiffRequestProducerWrapper(@NotNull DiffRequest request) {
      myRequest = request;
    }

    public @NotNull DiffRequest getRequest() {
      return myRequest;
    }

    @Override
    public @NotNull String getName() {
      String title = myRequest.getTitle();
      if (title != null) return title;
      return DiffBundle.message("diff.files.generic.request.title");
    }

    @Override
    public @Nullable FileType getContentType() {
      ContentDiffRequest contentRequest = ObjectUtils.tryCast(myRequest, ContentDiffRequest.class);
      if (contentRequest != null) {
        return JBIterable.from(contentRequest.getContents())
          .map(DiffContent::getContentType)
          .filter(fileType -> fileType != null && fileType != UnknownFileType.INSTANCE)
          .first();
      }
      return null;
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
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
