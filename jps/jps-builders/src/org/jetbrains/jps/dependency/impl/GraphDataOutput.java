// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

public class GraphDataOutput implements DataOutput {

  private final DataOutput myDelegate;

  public GraphDataOutput(DataOutput delegate) {
    myDelegate = delegate;
  }

  @Override
  public void write(int b) throws IOException {
    myDelegate.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    myDelegate.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    myDelegate.write(b, off, len);
  }

  @Override
  public void writeBoolean(boolean v) throws IOException {
    myDelegate.writeBoolean(v);
  }

  @Override
  public void writeByte(int v) throws IOException {
    myDelegate.writeByte(v);
  }

  @Override
  public void writeShort(int v) throws IOException {
    myDelegate.writeShort(v);
  }

  @Override
  public void writeChar(int v) throws IOException {
    myDelegate.writeChar(v);
  }

  @Override
  public void writeInt(int v) throws IOException {
    DataInputOutputUtil.writeINT(myDelegate, v);
  }

  @Override
  public void writeLong(long v) throws IOException {
    DataInputOutputUtil.writeLONG(myDelegate, v);
  }

  @Override
  public void writeFloat(float v) throws IOException {
    myDelegate.writeFloat(v);
  }

  @Override
  public void writeDouble(double v) throws IOException {
    myDelegate.writeDouble(v);
  }

  @Override
  public void writeBytes(@NotNull String s) throws IOException {
    myDelegate.writeBytes(s);
  }

  @Override
  public void writeChars(@NotNull String s) throws IOException {
    myDelegate.writeChars(s);
  }

  @Override
  public void writeUTF(@NotNull String s) throws IOException {
    IOUtil.writeUTF(myDelegate, s);
  }

  public static DataOutput wrap(DataOutput out) {
    return new GraphDataOutput(out);
  }
  
  public interface StringEnumerator {
    int toNumber(String str) throws IOException;
  }

  public static DataOutput wrap(DataOutput out, StringEnumerator enumerator) {
    return new GraphDataOutput(out) {
      @Override
      public void writeUTF(@NotNull String s) throws IOException {
        writeInt(enumerator.toNumber(s));
      }
    };
  }
}
