// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

@ApiStatus.Experimental
public final class AttributeOutputStream extends DataOutputStream {
  private final @NotNull SimpleStringPersistentEnumerator myStringEnumerator;

  @ApiStatus.Internal
  public AttributeOutputStream(@NotNull OutputStream out, @NotNull SimpleStringPersistentEnumerator stringEnumerator) {
    super(out);
    myStringEnumerator = stringEnumerator;
  }

  /**
   * Enumerate & write string to file's attribute. Might be used if one need to write many duplicated strings to attributes.
   */
  public void writeEnumeratedString(String str) throws IOException {
    DataInputOutputUtil.writeINT(this, myStringEnumerator.enumerate(str));
  }
}
