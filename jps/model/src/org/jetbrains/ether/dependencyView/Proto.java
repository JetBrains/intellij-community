package org.jetbrains.ether.dependencyView;

import org.jetbrains.asm4.Opcodes;
import org.jetbrains.ether.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.03.11
 * Time: 17:57
 * To change this template use File | Settings | File Templates.
 */
class Proto implements RW.Savable, Streamable {
  public final int myAccess;
  public final int mySignature;
  public final int myName;

  protected Proto(final int access, final int signature, final int name) {
    this.myAccess = access;
    this.mySignature = signature;
    this.myName = name;
  }

  protected Proto(final DataInput in) {
    try {
      myAccess = in.readInt();
      mySignature = in.readInt();
      myName = in.readInt();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void save(final DataOutput out) {
    try {
      out.writeInt(myAccess);
      out.writeInt(mySignature);
      out.writeInt(myName);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Difference difference(final Proto past) {
    int diff = Difference.NONE;

    if (past.myAccess != myAccess) {
      diff |= Difference.ACCESS;
    }

    if (past.mySignature != mySignature) {
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
        return ~past.myAccess & myAccess;
      }

      @Override
      public int removedModifiers() {
        return ~myAccess & past.myAccess;
      }

      @Override
      public boolean packageLocalOn() {
        return ((past.myAccess & Opcodes.ACC_PRIVATE) != 0 ||
                (past.myAccess & Opcodes.ACC_PUBLIC) != 0 ||
                (past.myAccess & Opcodes.ACC_PROTECTED) != 0) &&
               Difference.isPackageLocal(myAccess);
      }

      @Override
      public boolean hadValue() {
        return false;
      }

      @Override
      public boolean weakedAccess() {
        return Difference.weakerAccess(past.myAccess, myAccess);
      }
    };
  }

  public void toStream(final DependencyContext context, final PrintStream stream) {
    final String d = this instanceof ClassRepr ? "      " : "          ";

    if (this instanceof ClassRepr) {
      stream.print("    Class ");
      stream.println(context.getValue(myName));
    }

    if (this instanceof MethodRepr) {
      stream.print("        Method ");
      stream.println(context.getValue(myName));
    }

    if (this instanceof FieldRepr) {
      stream.print("        Field ");
      stream.println(context.getValue(myName));
    }

    stream.print(d);
    stream.print("Access     : ");
    stream.println(myAccess);

    stream.print(d);
    stream.print("Signature  : ");
    stream.println(context.getValue(mySignature));
  }
}
