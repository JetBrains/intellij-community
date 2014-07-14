/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;

/**
 * @author: db
 * Date: 01.03.11
 */
class Proto implements RW.Savable, Streamable {
  public final int access;
  public final int signature;
  public final int name;

  protected Proto(final int access, final int signature, final int name) {
    this.access = access;
    this.signature = signature;
    this.name = name;
  }

  protected Proto(final DataInput in) {
    try {
      access = DataInputOutputUtil.readINT(in);
      signature = DataInputOutputUtil.readINT(in);
      name = DataInputOutputUtil.readINT(in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void save(final DataOutput out) {
    try {
      DataInputOutputUtil.writeINT(out, access);
      DataInputOutputUtil.writeINT(out, signature);
      DataInputOutputUtil.writeINT(out, name);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public final boolean isPublic() {
    return (Opcodes.ACC_PUBLIC & access) != 0;
  }

  public final boolean isProtected() {
    return (Opcodes.ACC_PROTECTED & access) != 0;
  }

  public final boolean isPackageLocal() {
    return (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) == 0;
  }

  public final boolean isPrivate() {
    return (Opcodes.ACC_PRIVATE & access) != 0;
  }

  public final boolean isAbstract() {
    return (Opcodes.ACC_ABSTRACT & access) != 0;
  }

  public final boolean isBridge() {
    return (Opcodes.ACC_BRIDGE & access) != 0;
  }

  public final boolean isSynthetic() {
    return (Opcodes.ACC_SYNTHETIC & access) != 0;
  }

  public final boolean isAnnotation() {
    return (Opcodes.ACC_ANNOTATION & access) != 0;
  }

  public final boolean isFinal() {
    return (Opcodes.ACC_FINAL & access) != 0;
  }

  public final boolean isStatic() {
    return (Opcodes.ACC_STATIC & access) != 0;
  }

  /**
   * tests if the accessibility of this Proto is less restricted than the accessibility of the given Proto
   * @return true means this Proto is less restricted than the proto passed as parameter <br>
   *         false means this Proto has more restricted access than the parameter Proto or they have equal accessibility
   */
  public final boolean isMoreAccessibleThan(Proto anotherProto) {
    if (anotherProto.isPrivate()) {
      return this.isPackageLocal() || this.isProtected() || this.isPublic();
    }
    if (anotherProto.isPackageLocal()) {
      return this.isProtected() || this.isPublic();
    }
    if (anotherProto.isProtected()) {
      return this.isPublic();
    }
    return false;
  }

  public Difference difference(final Proto past) {
    int diff = Difference.NONE;

    if (past.access != access) {
      diff |= Difference.ACCESS;
    }

    if (past.signature != signature) {
      diff |= Difference.SIGNATURE;
    }

    final int base = diff;

    return new Difference() {
      @Override
      public int base() {
        return base;
      }

      @Override
      public boolean no() {
        return base == NONE;
      }

      @Override
      public int addedModifiers() {
        return ~past.access & access;
      }

      @Override
      public int removedModifiers() {
        return ~access & past.access;
      }

      @Override
      public boolean packageLocalOn() {
        return (past.isPrivate() || past.isPublic() || past.isProtected()) && Proto.this.isPackageLocal();
      }

      @Override
      public boolean hadValue() {
        return false;
      }

      @Override
      public boolean weakedAccess() {
        return Difference.weakerAccess(past.access, access);
      }
    };
  }

  public void toStream(final DependencyContext context, final PrintStream stream) {
    final String d = this instanceof ClassRepr ? "      " : "          ";

    if (this instanceof ClassRepr) {
      stream.print("    Class ");
      stream.println(context.getValue(name));
    }

    if (this instanceof MethodRepr) {
      stream.print("        Method ");
      stream.println(context.getValue(name));
    }

    if (this instanceof FieldRepr) {
      stream.print("        Field ");
      stream.println(context.getValue(name));
    }

    stream.print(d);
    stream.print("Access     : ");
    stream.println(access);

    stream.print(d);
    stream.print("Signature  : ");
    stream.println(context.getValue(signature));
  }
}
