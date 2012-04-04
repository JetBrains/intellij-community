package org.jetbrains.ether.dependencyView;

import groovyjarjarasm.asm.Opcodes;
import org.jetbrains.ether.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.03.11
 * Time: 17:57
 * To change this template use File | Settings | File Templates.
 */
class Proto implements RW.Savable {
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
      access = in.readInt();
      signature = in.readInt();
      name = in.readInt();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void save(final DataOutput out) {
    try {
      out.writeInt(access);
      out.writeInt(signature);
      out.writeInt(name);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
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
        return ((past.access & Opcodes.ACC_PRIVATE) != 0 ||
                (past.access & Opcodes.ACC_PUBLIC) != 0 ||
                (past.access & Opcodes.ACC_PROTECTED) != 0)

               &&

               ((access & Opcodes.ACC_PRIVATE) == 0 && (access & Opcodes.ACC_PROTECTED) == 0 && (access & Opcodes.ACC_PUBLIC) == 0);
      }

      @Override
      public boolean hadValue() {
        return false;
      }
    };
  }
}
