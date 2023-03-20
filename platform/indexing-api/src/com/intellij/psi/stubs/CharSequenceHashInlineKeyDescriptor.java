// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.util.io.InlineKeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;

final class CharSequenceHashInlineKeyDescriptor extends InlineKeyDescriptor<CharSequence> {

  final static CharSequenceHashInlineKeyDescriptor INSTANCE = new CharSequenceHashInlineKeyDescriptor();

  @Override
  public CharSequence fromInt(int n) {
    return new HashWrapper(n);
  }

  @Override
  public int toInt(CharSequence s) {
    return s.hashCode();
  }

  private static class HashWrapper implements CharSequence {
    final int hashCode;

    HashWrapper(int hashCode) {
      this.hashCode = hashCode;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int length() {
      throw new UnsupportedOperationException();
    }

    @Override
    public char charAt(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull CharSequence subSequence(int start, int end) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull IntStream chars() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull IntStream codePoints() {
      throw new UnsupportedOperationException();
    }
  }
}
