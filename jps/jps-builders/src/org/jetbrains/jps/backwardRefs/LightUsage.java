/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.sun.tools.javac.code.Symbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.RW;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import javax.lang.model.element.ElementKind;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class LightUsage implements RW.Savable {
  private final static byte CLASS_MARKER = 0x0;
  private final static byte METHOD_MARKER = 0x1;
  private final static byte FIELD_MARKER = 0x2;

  public final int myOwner;

  protected LightUsage(int owner) {
    myOwner = owner;
  }

  public int getOwner() {
    return myOwner;
  }

  @NotNull
  public abstract LightUsage override(int ownerOverrider);

  public static class LightMethodUsage extends LightUsage {
    private final int myName;
    private final int myParameterCount;

    LightMethodUsage(int owner, int name, int parameterCount) {
      super(owner);
      myName = name;
      myParameterCount = parameterCount;
    }

    public int getName() {
      return myName;
    }

    public int getParameterCount() {
      return myParameterCount;
    }

    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(METHOD_MARKER);
        DataInputOutputUtil.writeINT(out, getOwner());
        DataInputOutputUtil.writeINT(out, getName());
        DataInputOutputUtil.writeINT(out, getParameterCount());
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LightMethodUsage usage = (LightMethodUsage)o;

      if (myOwner != usage.myOwner) return false;
      if (myName != usage.myName) return false;
      if (myParameterCount != usage.myParameterCount) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName;
      result = 31 * result + myParameterCount;
      result = 31 * result + myOwner;
      return result;
    }

    @NotNull
    @Override
    public LightMethodUsage override(int ownerOverrider) {
      return new LightMethodUsage(ownerOverrider, getName(), getParameterCount());
    }
  }

  public static class LightFieldUsage extends LightUsage {
    private final int myName;

    LightFieldUsage(int owner, int name) {
      super(owner);
      myName = name;
    }

    public int getName() {
      return myName;
    }

    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(FIELD_MARKER);
        DataInputOutputUtil.writeINT(out, getOwner());
        DataInputOutputUtil.writeINT(out, getName());
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LightFieldUsage usage = (LightFieldUsage)o;

      if (myOwner != usage.myOwner) return false;
      if (myName != usage.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName + 31 * myOwner;
    }

    @NotNull
    @Override
    public LightFieldUsage override(int ownerOverrider) {
      return new LightFieldUsage(ownerOverrider, getName());
    }

  }

  public static class LightClassUsage extends LightUsage {
    LightClassUsage(int owner) {
      super(owner);
    }

    @NotNull
    @Override
    public LightClassUsage override(int ownerOverrider) {
      return new LightClassUsage(ownerOverrider);
    }

    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(CLASS_MARKER);
        DataInputOutputUtil.writeINT(out, getOwner());
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LightClassUsage usage = (LightClassUsage)o;

      if (myOwner != usage.myOwner) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myOwner;
    }
  }

  static KeyDescriptor<LightUsage> createDescriptor() {
    return new KeyDescriptor<LightUsage>() {
      @Override
      public int getHashCode(LightUsage value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(LightUsage val1, LightUsage val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, LightUsage value) throws IOException {
        value.save(out);
      }

      @Override
      public LightUsage read(@NotNull DataInput in) throws IOException {
        final byte type = in.readByte();
        switch (type) {
          case CLASS_MARKER:
            return new LightClassUsage(DataInputOutputUtil.readINT(in));
          case METHOD_MARKER:
            return new LightMethodUsage(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
          case FIELD_MARKER:
            return new LightFieldUsage(DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in));
        }
        throw new AssertionError();
      }
    };
  }

  static byte[] bytes(Symbol symbol) {
    return symbol.getQualifiedName().toUtf();
  }

  static LightUsage fromSymbol(Symbol symbol, ByteArrayEnumerator byteArrayEnumerator) {
    final ElementKind kind = symbol.getKind();
    if (symbol instanceof Symbol.ClassSymbol) {
      return new LightClassUsage(id(symbol, byteArrayEnumerator));
    }
    else if (symbol instanceof Symbol.VarSymbol) {
      return new LightFieldUsage(id(symbol.owner, byteArrayEnumerator), id(symbol, byteArrayEnumerator));
    }
    else if (symbol instanceof Symbol.MethodSymbol) {
      int paramCount = ((Symbol.MethodSymbol)symbol).type.getParameterTypes().size();
      return new LightMethodUsage(id(symbol.owner, byteArrayEnumerator), id(symbol, byteArrayEnumerator), paramCount);
    }
    else {
      throw new AssertionError("unexpected symbol: " + symbol + " class: " + symbol.getClass() + " kind: " + kind);
    }
  }

  private static int id(Symbol symbol, ByteArrayEnumerator byteArrayEnumerator) {
    return byteArrayEnumerator.enumerate(bytes(symbol));
  }
}
