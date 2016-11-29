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
package org.jetbrains.jps.javac.ast.api;

import com.sun.tools.javac.code.Symbol;
import org.jetbrains.annotations.NotNull;

public interface JavacRef {
  JavacRef[] EMPTY_ARRAY = new JavacRef[0];

  @NotNull
  byte[] getName();

  long getFlags();

  @NotNull
  byte[] getOwnerName();

  interface JavacClass extends JavacRef {
    boolean isAnonymous();
  }

  interface JavacMethod extends JavacRef {
    byte getParamCount();
  }

  interface JavacField extends JavacRef {
  }

  abstract class JavacRefBase implements JavacRef {
    private final byte[] myName;
    private final long myFlags;

    protected JavacRefBase(byte[] name, long flags) {
      myName = name;
      myFlags = flags;
    }

    @NotNull
    @Override
    public final byte[] getName() {
      return myName;
    }

    @Override
    public final long getFlags() {
      return myFlags;
    }
  }

  class JavacClassImpl extends JavacRefBase implements JavacClass {
    private boolean myAnonymous;

    public JavacClassImpl(byte[] name, long flags, boolean anonymous) {
      super(name, flags);
      myAnonymous = anonymous;
    }

    @NotNull
    @Override
    public byte[] getOwnerName() {
      throw new UnsupportedOperationException();
    }

    public boolean isAnonymous() {
      return myAnonymous;
    }
  }

  class JavacMethodImpl extends JavacRefBase implements JavacMethod {
    private final byte[] myOwnerName;
    private final byte myParamCount;

    public JavacMethodImpl(byte[] name, byte[] ownerName, byte paramCount, long flags) {
      super(name, flags);
      myOwnerName = ownerName;
      myParamCount = paramCount;
    }

    public byte getParamCount() {
      return myParamCount;
    }

    @NotNull
    @Override
    public byte[] getOwnerName() {
      return myOwnerName;
    }
  }

  class JavacFieldImpl extends JavacRefBase implements JavacField {
    private final byte[] myOwnerName;

    public JavacFieldImpl(byte[] name, byte[] ownerName, long flags) {
      super(name, flags);
      myOwnerName = ownerName;
    }

    @NotNull
    @Override
    public byte[] getOwnerName() {
      return myOwnerName;
    }
  }

  abstract class JavacSymbolRefBase implements JavacRef {
    protected final @NotNull Symbol myOriginalElement;

    protected JavacSymbolRefBase(@NotNull Symbol element) {myOriginalElement = element;}

    @NotNull
    public Symbol getOriginalElement() {
      return myOriginalElement;
    }

    @NotNull
    @Override
    public byte[] getName() {
      return myOriginalElement.flatName().toUtf();
    }

    @Override
    public long getFlags() {
      return myOriginalElement.flags();
    }

    @NotNull
    @Override
    public byte[] getOwnerName() {
      return myOriginalElement.owner.flatName().toUtf();
    }

    public static JavacRef.JavacSymbolRefBase fromSymbol(Symbol symbol) {
      if (symbol instanceof Symbol.ClassSymbol) {
        return new JavacRef.JavacSymbolClassImpl(symbol);
      }
      else if (symbol instanceof Symbol.VarSymbol) {
        return new JavacRef.JavacSymbolFieldImpl(symbol);
      }
      else if (symbol instanceof Symbol.MethodSymbol) {
        return new JavacRef.JavacSymbolMethodImpl(symbol);
      }
      throw new AssertionError("unexpected symbol: " + symbol + " class: " + symbol.getClass());
    }
  }

  class JavacSymbolClassImpl extends JavacSymbolRefBase implements JavacClass {
   public JavacSymbolClassImpl(@NotNull Symbol element) {
      super(element);
    }

    @Override
    public boolean isAnonymous() {
      return myOriginalElement.name.isEmpty();
    }
  }

  class JavacSymbolMethodImpl extends JavacSymbolRefBase implements JavacMethod {
    public JavacSymbolMethodImpl(@NotNull Symbol element) {
      super(element);
    }

    @Override
    public byte getParamCount() {
      return (byte)((Symbol.MethodSymbol)myOriginalElement).type.getParameterTypes().size();
    }
  }

  class JavacSymbolFieldImpl extends JavacSymbolRefBase implements JavacField {
    public JavacSymbolFieldImpl(@NotNull Symbol element) {
      super(element);
    }
  }
}
