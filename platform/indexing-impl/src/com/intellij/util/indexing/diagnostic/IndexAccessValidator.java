// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

public final class IndexAccessValidator {
  private final ThreadLocal<ID<?, ?>> ourAlreadyProcessingIndices = new ThreadLocal<>();

  private void checkAccessingIndexDuringOtherIndexProcessing(@NotNull ID<?, ?> indexKey) {
    ID<?, ?> alreadyProcessingIndex = ourAlreadyProcessingIndices.get();
    if (alreadyProcessingIndex == null || alreadyProcessingIndex == indexKey) {
      return;
    }

    String message = MessageFormat.format("Accessing ''{0}'' during processing ''{1}''. Nested different indices processing may cause deadlock",
              indexKey.getName(),
              alreadyProcessingIndex.getName());

    PluginId alreadyProcessingIndexOwner = alreadyProcessingIndex.getPluginId();
    PluginException exception = new PluginException(message, alreadyProcessingIndexOwner);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw exception;
    }

    ((FileBasedIndexEx)FileBasedIndex.getInstance()).getLogger().error(exception); // RuntimeException to skip rebuild
  }

  public <T, E extends Throwable> T validate(@NotNull ID<?, ?> indexKey, @NotNull ThrowableComputable<T, E> runnable) throws E {
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
