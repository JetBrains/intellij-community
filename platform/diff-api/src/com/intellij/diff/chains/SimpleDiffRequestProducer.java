// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.chains;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class SimpleDiffRequestProducer {
  private static final Logger LOG = Logger.getInstance(SimpleDiffRequestProducer.class);

  @NotNull
  public static DiffRequestProducer create(@NotNull FilePath filePath,
                                           @NotNull ThrowableComputable<DiffRequest, Throwable> producer) {
    return create(filePath.getPath(), producer);
  }

  @NotNull
  public static DiffRequestProducer create(@NotNull @Nls String name,
                                           @NotNull ThrowableComputable<DiffRequest, Throwable> producer) {
    return new MyDiffRequestProducer(name, producer);
  }

  private static class MyDiffRequestProducer implements DiffRequestProducer {
    @NotNull private final @Nls String myName;
    @NotNull private final ThrowableComputable<DiffRequest, Throwable> myProducer;

    private MyDiffRequestProducer(@NotNull @Nls String name,
                                  @NotNull ThrowableComputable<DiffRequest, Throwable> producer) {
      myName = name;
      myProducer = producer;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
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
