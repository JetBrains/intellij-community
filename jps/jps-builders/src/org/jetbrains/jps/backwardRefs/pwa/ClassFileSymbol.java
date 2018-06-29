// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.pwa;

import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

public abstract class ClassFileSymbol {
  public final int name;

  protected ClassFileSymbol(int name) { this.name = name; }

  abstract static class FieldOrMethod extends ClassFileSymbol {
    public final int containingClass;

    protected FieldOrMethod(int name, int containingClass) {
      super(name);
      this.containingClass = containingClass;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FieldOrMethod)) return false;
      if (!super.equals(o)) return false;
      FieldOrMethod method = (FieldOrMethod)o;
      return containingClass == method.containingClass;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), containingClass);
    }
  }

  //TODO
  public static class Lambda extends ClassFileSymbol {
    protected Lambda(int name) {
      super(name);
    }

    @Override
    public int hashCode() {
      return name;
    }
  }
  public static class Clazz extends ClassFileSymbol {
    public Clazz(int name) {
      super(name);
    }

    @Override
    public int hashCode() {
      return name;
    }
  }
  public static class Method extends FieldOrMethod {
    public final int parameterCount;

    public Method(int name, int containingClass, int parameterCount) {
      super(name, containingClass);
      this.parameterCount = parameterCount;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Method)) return false;
      if (!super.equals(o)) return false;
      Method method = (Method)o;
      return parameterCount == method.parameterCount;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), parameterCount);
    }
  }
  public static class Field extends FieldOrMethod {
    public Field(int name, int containingClass) {
      super(name, containingClass);
    }

    @Override
    public boolean equals(Object o) {
      return super.equals(o);
    }

    @Override
    public int hashCode() {
      return super.hashCode();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassFileSymbol)) return false;
    ClassFileSymbol symbol = (ClassFileSymbol)o;
    return name == symbol.name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  static KeyDescriptor<ClassFileSymbol> EXTERNALIZER = createExternalizer();

  private static final byte LAMBDA_TYPE = 1;
  private static final byte CLASS_TYPE = 2;
  private static final byte FIELD_TYPE = 3;
  private static final byte METHOD_TYPE = 4;
  private static KeyDescriptor<ClassFileSymbol> createExternalizer() {
    return new KeyDescriptor<ClassFileSymbol>() {
      @Override
      public int getHashCode(ClassFileSymbol value) {
        return value.hashCode();
      }

      @Override
      public boolean isEqual(ClassFileSymbol val1, ClassFileSymbol val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, ClassFileSymbol symbol) throws IOException {
        out.writeInt(symbol.name);
        if (symbol instanceof Clazz) {
          out.writeByte(CLASS_TYPE);
        }
        else if (symbol instanceof Method) {
          out.writeByte(METHOD_TYPE);
          out.writeInt(((Method)symbol).containingClass);
          out.writeInt(((Method)symbol).parameterCount);
        }
        else if (symbol instanceof Field) {
          out.writeByte(FIELD_TYPE);
          out.writeInt(((Field)symbol).containingClass);
        } else if (symbol instanceof Lambda) {
          throw new RuntimeException();
        }
      }

      @Override
      public ClassFileSymbol read(@NotNull DataInput in) throws IOException {
        int name = in.readInt();
        byte type = in.readByte();
        if (type == CLASS_TYPE) {
          return new Clazz(name);
        }
        else if (type == METHOD_TYPE) {
          return new Method(name, in.readInt(), in.readInt());
        }
        else if (type == FIELD_TYPE) {
          return new Field(name, in.readInt());
        }
        else if (type == LAMBDA_TYPE) {
          return new Lambda(name);
        }
        return null;
      }

      private byte getType(ClassFileSymbol symbol) {
        if (symbol instanceof Clazz) {
          return CLASS_TYPE;
        }
        else if (symbol instanceof Method) {
          return METHOD_TYPE;
        }
        else if (symbol instanceof Field) {
          return FIELD_TYPE;
        }
        assert symbol instanceof Lambda;
        return LAMBDA_TYPE;
      }
    };
  }
}
