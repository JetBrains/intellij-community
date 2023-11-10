// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;

public class GraphDataInput implements DataInput {

  private final DataInput myDelegate;

  public GraphDataInput(DataInput delegate) {
    myDelegate = delegate;
  }

  @Override
  public void readFully(byte[] b) throws IOException {
    myDelegate.readFully(b);
  }

  @Override
  public void readFully(byte[] b, int off, int len) throws IOException {
    myDelegate.readFully(b, off, len);
  }

  @Override
  public int skipBytes(int n) throws IOException {
    return myDelegate.skipBytes(n);
  }

  @Override
  public boolean readBoolean() throws IOException {
    return myDelegate.readBoolean();
  }

  @Override
  public byte readByte() throws IOException {
    return myDelegate.readByte();
  }

  @Override
  public int readUnsignedByte() throws IOException {
    return myDelegate.readUnsignedByte();
  }

  @Override
  public short readShort() throws IOException {
    return myDelegate.readShort();
  }

  @Override
  public int readUnsignedShort() throws IOException {
    return myDelegate.readUnsignedShort();
  }

  @Override
  public char readChar() throws IOException {
    return myDelegate.readChar();
  }

  @Override
  public int readInt() throws IOException {
    return DataInputOutputUtil.readINT(myDelegate);
  }

  @Override
  public long readLong() throws IOException {
    return DataInputOutputUtil.readLONG(myDelegate);
  }

  @Override
  public float readFloat() throws IOException {
    return myDelegate.readFloat();
  }

  @Override
  public double readDouble() throws IOException {
    return myDelegate.readDouble();
  }

  @Override
  public String readLine() throws IOException {
    return myDelegate.readLine();
  }

  @NotNull
  @Override
  public String readUTF() throws IOException {
    return IOUtil.readUTF(myDelegate);
  }

  public static DataInput wrap(DataInput in) {
    return new GraphDataInput(in);
  }
}
