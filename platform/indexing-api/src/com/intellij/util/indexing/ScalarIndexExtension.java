// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A specialization of FileBasedIndexExtension allowing to create a mapping {@code [DataObject -> List of files containing this object]}.
 */
@ApiStatus.OverrideOnly
public abstract class ScalarIndexExtension<K> extends FileBasedIndexExtension<K, Void> {

  /**
   * To remove in IDEA 2018.1.
   *
   * @deprecated use {@link VoidDataExternalizer#INSTANCE}
   */
  @Deprecated(forRemoval = true)
  public static final DataExternalizer<Void> VOID_DATA_EXTERNALIZER = VoidDataExternalizer.INSTANCE;

  @NotNull
  @Override
  public final DataExternalizer<Void> getValueExternalizer() {
    return VoidDataExternalizer.INSTANCE;
  }
}