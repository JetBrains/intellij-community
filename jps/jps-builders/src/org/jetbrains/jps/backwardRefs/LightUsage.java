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
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.dependencyView.RW;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static com.sun.tools.javac.code.Flags.PRIVATE;

public abstract class LightUsage implements RW.Savable {
  private final static byte CLASS_MARKER = 0x0;
  private final static byte METHOD_MARKER = 0x1;
  private final static byte FIELD_MARKER = 0x2;
  private final static byte FUN_EXPR_MARKER = 0x3;

  static final Tree.Kind LAMBDA_EXPRESSION;
  static final Tree.Kind MEMBER_REFERENCE;

  static {
    Tree.Kind lambdaExpression = null;
    Tree.Kind memberReference = null;
    try {
      lambdaExpression = Tree.Kind.valueOf("LAMBDA_EXPRESSION");
      memberReference = Tree.Kind.valueOf("MEMBER_REFERENCE");
    }
    catch (IllegalArgumentException ignored) {
    }
    LAMBDA_EXPRESSION = lambdaExpression;
    MEMBER_REFERENCE = memberReference;
  }

  public abstract int getName();

  @NotNull
  public abstract LightUsage override(LightUsage overriderClass);

  @NotNull
  public abstract LightUsage getOwner();

  public static class LightMethodUsage extends LightUsage {
    private final int myOwner;
    private final int myName;
    private final int myParameterCount;

    public LightMethodUsage(int owner, int name, int parameterCount) {
      myOwner = owner;
      myName = name;
      myParameterCount = parameterCount;
    }

    @Override
    public int getName() {
      return myName;
    }

    public int getParameterCount() {
      return myParameterCount;
    }

    @Override
    @NotNull
    public LightUsage getOwner() {
      return new LightClassUsage(myOwner);
    }

    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(METHOD_MARKER);
        DataInputOutputUtil.writeINT(out, myOwner);
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
    public LightMethodUsage override(LightUsage overriderClass) {
      return new LightMethodUsage(overriderClass.getName(), getName(), getParameterCount());
    }
  }

  public static class LightFieldUsage extends LightUsage {
    private final int myOwner;
    private final int myName;

    public LightFieldUsage(int owner, int name) {
      myOwner = owner;
      myName = name;
    }

    @Override
    public int getName() {
      return myName;
    }

    @NotNull
    @Override
    public LightUsage getOwner() {
      return new LightClassUsage(myOwner);
    }

    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(FIELD_MARKER);
        DataInputOutputUtil.writeINT(out, myOwner);
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
    public LightFieldUsage override(LightUsage overriderClass) {
      return new LightFieldUsage(overriderClass.getName(), getName());
    }

  }

  public static class LightClassUsage extends LightUsage {
    private final int myName;

    public LightClassUsage(int name) {
      myName = name;
    }

    @Override
    public int getName() {
      return myName;
    }

    @NotNull
    @Override
    public LightClassUsage override(LightUsage overriderClass) {
      return (LightClassUsage)overriderClass;
    }

    @NotNull
    @Override
    public LightUsage getOwner() {
      return this;
    }


    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(CLASS_MARKER);
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

      LightClassUsage usage = (LightClassUsage)o;

      if (myName != usage.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName;
    }
  }

  public static class LightFunExprUsage extends LightUsage {
    private final int myName;

    public LightFunExprUsage(int name) {
      myName = name;
    }

    @NotNull
    @Override
    public LightFunExprUsage override(LightUsage overriderClass) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public LightUsage getOwner() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getName() {
      return myName;
    }

    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(FUN_EXPR_MARKER);
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

      LightFunExprUsage usage = (LightFunExprUsage)o;

      if (myName != usage.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName;
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
          case FUN_EXPR_MARKER:
            return new LightFunExprUsage(DataInputOutputUtil.readINT(in));
        }
        throw new AssertionError();
      }
    };
  }

  static byte[] bytes(Symbol symbol) {
    return symbol.flatName().toUtf();
  }

  @Nullable
  static LightUsage fromSymbol(JavacRefSymbol refSymbol, ByteArrayEnumerator byteArrayEnumerator) {
    Symbol symbol = refSymbol.getSymbol();
    final Tree.Kind kind = refSymbol.getPlaceKind();
    if (symbol instanceof Symbol.ClassSymbol) {
      if (kind == LAMBDA_EXPRESSION || kind == MEMBER_REFERENCE) {
        return new LightFunExprUsage(id(symbol, byteArrayEnumerator));
      } else if (!isPrivate(symbol) && !isAnonymous(symbol)) {
        return new LightClassUsage(id(symbol, byteArrayEnumerator));
      }
    }
    else {
      Symbol owner = symbol.owner;
      if (isPrivate(symbol)) {
        return null;
      }
      if (symbol instanceof Symbol.VarSymbol) {
        return new LightFieldUsage(id(owner, byteArrayEnumerator), id(symbol, byteArrayEnumerator));
      }
      else if (symbol instanceof Symbol.MethodSymbol) {
        int paramCount = ((Symbol.MethodSymbol)symbol).type.getParameterTypes().size();
        return new LightMethodUsage(id(owner, byteArrayEnumerator), id(symbol, byteArrayEnumerator), paramCount);
      }
      else {
        throw new AssertionError("unexpected symbol: " + symbol + " class: " + symbol.getClass() + " kind: " + kind);
      }
    }
    return null;
  }

  // JDK-6 has no Symbol.isPrivate() method
  private static boolean isPrivate(Symbol symbol) {
    return (symbol.flags() & Flags.AccessFlags) == PRIVATE;
  }

  // JDK-6 has no Symbol.isAnonymous() method
  private static boolean isAnonymous(Symbol symbol) {
    return symbol.name.isEmpty();
  }

  private static int id(Symbol symbol, ByteArrayEnumerator byteArrayEnumerator) {
    return byteArrayEnumerator.enumerate(bytes(symbol));
  }
}
