// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

@ApiStatus.Experimental
public final class AttributeOutputStreamImpl extends AttributeOutputStream {
  private final @NotNull DataEnumerator<String> stringsEnumerator;

  @ApiStatus.Internal
  public <T extends OutputStream & RepresentableAsByteArraySequence> AttributeOutputStreamImpl(@NotNull T underlyingStream,
                                                                                               @NotNull DataEnumerator<String> stringEnumerator) {
    super(underlyingStream);
    stringsEnumerator = stringEnumerator;
  }

  /** @inheritDocs */
  @Override
  public void writeEnumeratedString(String str) throws IOException {
    DataInputOutputUtil.writeINT(this, stringsEnumerator.enumerate(str));
  }
}
