// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.indexing.CustomInputsIndexFileBasedIndexExtension;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class IdIndexImpl extends IdIndex implements CustomInputsIndexFileBasedIndexExtension<IdIndexEntry> {
  @Override
  public @NotNull DataExternalizer<Collection<IdIndexEntry>> createExternalizer() {
    return new IdIndexEntriesExternalizer();
  }
}
