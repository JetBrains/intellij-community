// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.RepresentableAsByteArraySequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

@ApiStatus.Experimental
public abstract class AttributeOutputStream extends DataOutputStream implements RepresentableAsByteArraySequence {
  public <T extends OutputStream & RepresentableAsByteArraySequence> AttributeOutputStream(@NotNull T underlyingStream) {
    super(underlyingStream);
  }

  /**
   * Enumerate & write string to file's attribute -- i.e. write int instead of string's UTF8.
   * Might be useful if one needs to write many duplicated strings to attributes.
   */
  public abstract void writeEnumeratedString(String str) throws IOException;

  @Override
  public @NotNull ByteArraySequence asByteArraySequence() {
    return ((RepresentableAsByteArraySequence)out).asByteArraySequence();
  }
}