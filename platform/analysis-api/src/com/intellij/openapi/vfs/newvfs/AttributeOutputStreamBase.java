// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.RepresentableAsByteArraySequence;
import com.intellij.util.io.SimpleStringPersistentEnumerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Experimental
public final class AttributeOutputStreamBase extends AttributeOutputStream {
  private final @NotNull SimpleStringPersistentEnumerator myStringEnumerator;

  @ApiStatus.Internal
  public <T extends DataOutputStream & RepresentableAsByteArraySequence> AttributeOutputStreamBase(@NotNull T out,
                                                                                                   @NotNull SimpleStringPersistentEnumerator stringEnumerator) {
    super(out);
    myStringEnumerator = stringEnumerator;
  }

  /**
   * Enumerate & write string to file's attribute. Might be used if one need to write many duplicated strings to attributes.
   */
  @Override
  public void writeEnumeratedString(String str) throws IOException {
    DataInputOutputUtil.writeINT(this, myStringEnumerator.enumerate(str));
  }
}
