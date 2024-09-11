// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

@ApiStatus.Experimental
public final class AttributeInputStream extends DataInputStream {
  private final @NotNull DataEnumerator<String> stringsEnumerator;

  @ApiStatus.Internal
  public AttributeInputStream(@NotNull InputStream in,
                              @NotNull DataEnumerator<String> stringsEnumerator) {
    super(in);
    this.stringsEnumerator = stringsEnumerator;
  }

  /**
   * Read enumerated string from file's attribute. Might be used if one need to write many duplicated strings to attributes.
   */
  public String readEnumeratedString() throws IOException {
    return stringsEnumerator.valueOf(DataInputOutputUtil.readINT(this));
  }
}
