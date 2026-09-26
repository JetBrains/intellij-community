// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.chains;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SimpleDiffRequestProducer {
  private static final Logger LOG = Logger.getInstance(SimpleDiffRequestProducer.class);

  public static @NotNull DiffRequestProducer create(@NotNull FilePath filePath,
                                                    @NotNull ThrowableComputable<? extends DiffRequest, Throwable> producer) {
    return new MyDiffRequestProducer(filePath.getPath(), filePath.getFileType(), producer);
  }

  public static @NotNull DiffRequestProducer create(@NotNull @Nls String name,
                                                    @NotNull ThrowableComputable<? extends DiffRequest, Throwable> producer) {
    return new MyDiffRequestProducer(name, null, producer);
  }

  private static class MyDiffRequestProducer implements DiffRequestProducer {
    private final @NotNull @Nls String myName;
    private final @Nullable FileType myFileType;
    private final @NotNull ThrowableComputable<? extends DiffRequest, Throwable> myProducer;

    private MyDiffRequestProducer(@NotNull @Nls String name,
                                  @Nullable FileType fileType,
                                  @NotNull ThrowableComputable<? extends DiffRequest, Throwable> producer) {
      myName = name;
      myFileType = fileType;
      myProducer = producer;
    }

    @Override
    public @Nls @NotNull String getName() {
      return myName;
    }

    @Override
    public @Nullable FileType getContentType() {
      return myFileType;
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      try {
        return myProducer.compute();
      }
      catch (Throwable e) {
        LOG.warn(e);
        throw new DiffRequestProducerException(e);
      }
    }
  }
}
