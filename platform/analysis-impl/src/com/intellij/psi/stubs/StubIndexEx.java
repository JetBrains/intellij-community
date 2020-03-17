// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

public abstract class StubIndexEx extends StubIndex {
  static void initExtensions() {
    // initialize stub index keys
    for (StubIndexExtension<?, ?> extension : StubIndexExtension.EP_NAME.getExtensionList()) {
      extension.getKey();
    }
  }

  abstract <K> void serializeIndexValue(@NotNull DataOutput out, @NotNull StubIndexKey<K, ?> stubIndexKey,
                                        @NotNull Map<K, StubIdList> map) throws IOException;

  @NotNull
  abstract <K> Map<K, StubIdList> deserializeIndexValue(@NotNull DataInput in, @NotNull StubIndexKey<K, ?> stubIndexKey,
                                                        @Nullable K requestedKey) throws IOException;

  void skipIndexValue(@NotNull DataInput in) throws IOException {
    int bufferSize = DataInputOutputUtil.readINT(in);
    in.skipBytes(bufferSize);
  }

  @NotNull
  abstract <K> TObjectHashingStrategy<K> getKeyHashingStrategy(StubIndexKey<K, ?> stubIndexKey);

  @ApiStatus.Internal
  abstract void ensureLoaded();
}
