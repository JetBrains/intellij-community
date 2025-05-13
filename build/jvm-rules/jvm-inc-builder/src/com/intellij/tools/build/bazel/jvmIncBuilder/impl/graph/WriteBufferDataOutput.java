// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import org.h2.mvstore.WriteBuffer;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

public class WriteBufferDataOutput implements DataOutput {
  private final WriteBuffer myBuf;

  public WriteBufferDataOutput(WriteBuffer buf) {
    myBuf = buf;
  }

  @Override
  public void write(int b) throws IOException {
    myBuf.putInt(b);
  }

  @Override
  public void write(@NotNull byte[] b) throws IOException {
    myBuf.put(b);
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) throws IOException {
    myBuf.put(b, off, len);
  }

  @Override
  public void writeBoolean(boolean v) throws IOException {
    writeByte(v? 1 : 0);
  }

  @Override
  public void writeByte(int v) throws IOException {
    myBuf.put(((byte)(v & 0xFF)));
  }

  @Override
  public void writeShort(int v) throws IOException {
    myBuf.putShort((short)(v & 0xFFFF));
  }

  @Override
  public void writeChar(int v) throws IOException {
    myBuf.putChar((char)(v & 0xFFFF));
  }

  @Override
  public void writeInt(int v) throws IOException {
    myBuf.putInt(v);
  }

  @Override
  public void writeLong(long v) throws IOException {
    myBuf.putLong(v);
  }

  @Override
  public void writeFloat(float v) throws IOException {
    myBuf.putFloat(v);
  }

  @Override
  public void writeDouble(double v) throws IOException {
    myBuf.putDouble(v);
  }

  @Override
  public void writeBytes(@NotNull String s) throws IOException {
    int length = s.length();
    for (int idx = 0; idx < length; idx++) {
      writeByte(s.charAt(idx));
    }
  }

  @Override
  public void writeChars(@NotNull String s) throws IOException {
    int length = s.length();
    for (int idx = 0; idx < length; idx++) {
      myBuf.putChar(s.charAt(idx));
    }
  }

  @Override
  public void writeUTF(@NotNull String s) throws IOException {
    int length = s.length();
    myBuf.putVarInt(length).putStringData(s, length);
  }
}
