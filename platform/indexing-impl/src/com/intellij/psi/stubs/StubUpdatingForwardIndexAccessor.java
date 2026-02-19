// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.forward.SingleEntryIndexForwardIndexAccessor;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

@Internal
final class StubUpdatingForwardIndexAccessor extends SingleEntryIndexForwardIndexAccessor<SerializedStubTree> {
  StubUpdatingForwardIndexAccessor(@NotNull FileBasedIndexExtension<Integer, SerializedStubTree> extension) {
    super((SingleEntryFileBasedIndexExtension<SerializedStubTree>)extension);
  }

  @Override
  public @NotNull InputDataDiffBuilder<Integer, SerializedStubTree> createDiffBuilderByMap(int inputId,
                                                                                           @Nullable Map<Integer, SerializedStubTree> data)
    throws IOException {
    SerializedStubTree tree = ContainerUtil.isEmpty(data) ? null : ContainerUtil.getFirstItem(data.values());
    if (tree != null) {
      tree.restoreIndexedStubs();
    }
    return new StubCumulativeInputDiffBuilder(inputId, tree);
  }
}
