// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.util.IntellijInternalApi;
import org.jetbrains.annotations.Nullable;

public class SearchId {
  private final @Nullable String myDeserializedName;
  private final int myId;

  SearchId(@Nullable String deserializedName, int id) {
    myDeserializedName = deserializedName;
    myId = id;
  }

  @IntellijInternalApi
  public SearchId(@Nullable String deserializedName) {
    this(deserializedName, -1);
  }

  SearchId(int id) {
    this(null, id);
  }

  public @Nullable String getDeserializedName() {
    return myDeserializedName;
  }

  public int getId() {
    return myId;
  }
}
