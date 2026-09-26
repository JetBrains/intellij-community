// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.graph;

import org.h2.mvstore.DataUtils;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class ByteBufferDataInput implements DataInput {
  private final ByteBuffer myBuf;

  public ByteBufferDataInput(ByteBuffer buf) {
    myBuf = buf;
  }

  @Override
  public void readFully(@NotNull byte[] b) throws IOException {
    try {
      myBuf.get(b, 0, b.length);
    }
    catch (BufferUnderflowException e) {
      throw new EOFException(e.getMessage());
    }
  }

  @Override
  public void readFully(@NotNull byte[] b, int off, int len) throws IOException {
    try {
      myBuf.get(b, off, len);
    }
    catch (BufferUnderflowException e) {
      throw new EOFException(e.getMessage());
    }
  }

  @Override
  public int skipBytes(int n) throws IOException {
    int skip = Math.min(n, myBuf.remaining());
    myBuf.position(myBuf.position() + skip);
    return skip;
  }

  @Override
  public boolean readBoolean() throws IOException {
    return myBuf.get() != 0;
  }

  @Override
  public byte readByte() throws IOException {
    return myBuf.get();
  }

  @Override
  public int readUnsignedByte() throws IOException {
    return ((int)readByte()) & 0xFF;
  }

  @Override
  public short readShort() throws IOException {
    return myBuf.getShort();
  }

  @Override
  public int readUnsignedShort() throws IOException {
    return ((int)readShort()) & 0xFF;
  }

  @Override
  public char readChar() throws IOException {
    return myBuf.getChar();
  }

  @Override
  public int readInt() throws IOException {
    return myBuf.getInt();
  }

  @Override
  public long readLong() throws IOException {
    return myBuf.getLong();
  }

  @Override
  public float readFloat() throws IOException {
    return myBuf.getFloat();
  }

  @Override
  public double readDouble() throws IOException {
    return myBuf.getDouble();
  }

  @Override
  public String readLine() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull String readUTF() throws IOException {
    return DataUtils.readString(myBuf);
  }
}
