// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.library;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsOrderRootType;

@ApiStatus.Internal
public final class JpsLibraryRootTypeSerializer implements Comparable<JpsLibraryRootTypeSerializer> {
  private final String myTypeId;
  private final JpsOrderRootType myType;
  private final boolean myWriteIfEmpty;

  public JpsLibraryRootTypeSerializer(@NotNull String typeId, @NotNull JpsOrderRootType type, boolean writeIfEmpty) {
    myTypeId = typeId;
    myType = type;
    myWriteIfEmpty = writeIfEmpty;
  }

  public boolean isWriteIfEmpty() {
    return myWriteIfEmpty;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public JpsOrderRootType getType() {
    return myType;
  }

  @Override
  public int compareTo(JpsLibraryRootTypeSerializer o) {
    return myTypeId.compareTo(o.myTypeId);
  }
}
