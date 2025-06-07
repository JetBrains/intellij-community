// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.ExternalizableGraphElement;
import org.jetbrains.jps.dependency.FactoredExternalizableGraphElement;
import org.jetbrains.jps.dependency.GraphDataOutput;

import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphDataOutputImpl implements GraphDataOutput {

  private final DataOutput myDelegate;

  public GraphDataOutputImpl(DataOutput delegate) {
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
    if (0 > v || v >= 192) {
      myDelegate.writeByte(192 + (v & 0x3F));
      v >>>= 6;
      while (v >= 128) {
        myDelegate.writeByte((v & 0x7F) | 0x80);
        v >>>= 7;
      }
    }
    myDelegate.writeByte(v);
  }

  @Override
  public void writeLong(long v) throws IOException {
    if (0 > v || v >= 192) {
      myDelegate.writeByte(192 + (int)(v & 0x3F));
      v >>>= 6;
      while (v >= 128) {
        myDelegate.writeByte((int)(v & 0x7F) | 0x80);
        v >>>= 7;
      }
    }
    myDelegate.writeByte((int) v);
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
    RW.writeUTF(myDelegate, s);
  }

  @Override
  public <T extends ExternalizableGraphElement> void writeGraphElement(@NotNull T elem) throws IOException {
    writeUTF(elem.getClass().getName());
    if (elem instanceof FactoredExternalizableGraphElement) {
      writeGraphElement(((FactoredExternalizableGraphElement<?>)elem).getFactorData());
    }
    elem.write(this);
  }

  @Override
  public <T extends ExternalizableGraphElement> void writeGraphElementCollection(Class<? extends T> elemType, @NotNull Iterable<T> col) throws IOException {
    writeUTF(elemType.getName());
    if (FactoredExternalizableGraphElement.class.isAssignableFrom(elemType)) {
      Map<ExternalizableGraphElement, List<FactoredExternalizableGraphElement<?>>> elemGroups = new HashMap<>();
      for (T e : col) {
        FactoredExternalizableGraphElement<?> fe = (FactoredExternalizableGraphElement<?>)e;
        elemGroups.computeIfAbsent(fe.getFactorData(), k -> new ArrayList<>()).add(fe);
      }
      writeInt(elemGroups.size());
      for (Map.Entry<ExternalizableGraphElement, List<FactoredExternalizableGraphElement<?>>> entry : elemGroups.entrySet()) {
        RW.writeCollection(this, entry.getValue(), new RW.Writer<>() {
          private boolean commonPartWritten;
          @Override
          public void write(FactoredExternalizableGraphElement<?> elem) throws IOException {
            if (!commonPartWritten) {
              commonPartWritten = true;
              writeGraphElement(entry.getKey());
            }
            elem.write(GraphDataOutputImpl.this);
          }
        });
      }
    }
    else {
      RW.writeCollection(this, col, elem -> elem.write(this));
    }
  }

  public static GraphDataOutput wrap(DataOutput out) {
    return out instanceof GraphDataOutput? (GraphDataOutput)out : new GraphDataOutputImpl(out);
  }
  
  public interface StringEnumerator {
    int toNumber(String str) throws IOException;
  }

  public static GraphDataOutput wrap(DataOutput out, @Nullable StringEnumerator enumerator) {
    return enumerator == null? wrap(out) : new GraphDataOutputImpl(out) {
      @Override
      public void writeUTF(@NotNull String s) throws IOException {
        writeInt(enumerator.toNumber(s));
      }
    };
  }
}
