// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

public final class IndexAccessValidator {
  private final ThreadLocal<ID<?, ?>> ourAlreadyProcessingIndices = new ThreadLocal<>();

  private void checkAccessingIndexDuringOtherIndexProcessing(@NotNull ID<?, ?> indexKey) {
    final ID<?, ?> alreadyProcessingIndex = ourAlreadyProcessingIndices.get();
    if (alreadyProcessingIndex != null && alreadyProcessingIndex != indexKey) {
      final String message = MessageFormat.format("Accessing ''{0}'' during processing ''{1}''. Nested different indices processing may cause deadlock",
                indexKey.getName(),
                alreadyProcessingIndex.getName());
      if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
      Logger.getInstance(FileBasedIndexImpl.class).error(message); // RuntimeException to skip rebuild
    }
  }

  public <T,E extends Throwable> T validate(@NotNull ID<?, ?> indexKey, @NotNull ThrowableComputable<T, E> runnable) throws E {
    checkAccessingIndexDuringOtherIndexProcessing(indexKey);
    ourAlreadyProcessingIndices.set(indexKey);
    try {
      return runnable.compute();
    }
    finally {
      ourAlreadyProcessingIndices.set(null);
    }
  }
}
