// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.RW;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.DataOutput;
import java.io.IOException;

public interface CompilerRef extends RW.Savable {
  CompilerRef[] EMPTY_ARRAY = new CompilerRef[0];

  byte CLASS_MARKER = 0x0;
  byte METHOD_MARKER = 0x1;
  byte FIELD_MARKER = 0x2;
  byte FUN_EXPR_MARKER = 0x3;
  byte ANONYMOUS_CLASS_MARKER = 0x4;

  CompilerRef override(int newOwner);

  interface NamedCompilerRef extends CompilerRef {
    NamedCompilerRef[] EMPTY_ARRAY = new NamedCompilerRef[0];

    int getName();
  }

  interface CompilerClassHierarchyElementDef extends NamedCompilerRef {
    CompilerClassHierarchyElementDef[] EMPTY_ARRAY = new CompilerClassHierarchyElementDef[0];

  }

  interface CompilerAnonymousClassDef extends CompilerClassHierarchyElementDef {
  }

  interface CompilerFunExprDef extends CompilerRef {
    int getId();
  }

  interface CompilerMember extends NamedCompilerRef {
    @NotNull
    CompilerClassHierarchyElementDef getOwner();
  }

  class JavaCompilerMethodRef implements CompilerMember {
    private final int myOwner;
    private final int myName;
    private final int myParameterCount;

    public JavaCompilerMethodRef(int owner, int name, int parameterCount) {
      myOwner = owner;
      myName = name;
      myParameterCount = parameterCount;
    }

    public int getName() {
      return myName;
    }

    @Override
    public CompilerRef override(int newOwner) {
      return new JavaCompilerMethodRef(newOwner, myName, myParameterCount);
    }

    public int getParameterCount() {
      return myParameterCount;
    }

    @Override
    @NotNull
    public CompilerClassHierarchyElementDef getOwner() {
      return new JavaCompilerClassRef(myOwner);
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

      JavaCompilerMethodRef methodRef = (JavaCompilerMethodRef)o;

      if (myOwner != methodRef.myOwner) return false;
      if (myName != methodRef.myName) return false;
      if (myParameterCount != methodRef.myParameterCount) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName;
      result = 31 * result + myParameterCount;
      result = 31 * result + myOwner;
      return result;
    }
  }

  class JavaCompilerFieldRef implements CompilerMember {
    private final int myOwner;
    private final int myName;

    public JavaCompilerFieldRef(int owner, int name) {
      myOwner = owner;
      myName = name;
    }

    @Override
    public int getName() {
      return myName;
    }

    @Override
    public CompilerRef override(int newOwner) {
      return new JavaCompilerFieldRef(newOwner, myName);
    }

    @NotNull
    @Override
    public CompilerClassHierarchyElementDef getOwner() {
      return new JavaCompilerClassRef(myOwner);
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

      JavaCompilerFieldRef fieldRef = (JavaCompilerFieldRef)o;

      if (myOwner != fieldRef.myOwner) return false;
      if (myName != fieldRef.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName + 31 * myOwner;
    }
  }

  class JavaCompilerAnonymousClassRef implements CompilerAnonymousClassDef {
    private final int myName;

    public JavaCompilerAnonymousClassRef(int name) {myName = name;}

    @Override
    public CompilerRef override(int newOwner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getName() {
      return myName;
    }

    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(ANONYMOUS_CLASS_MARKER);
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

      JavaCompilerAnonymousClassRef ref = (JavaCompilerAnonymousClassRef)o;

      if (myName != ref.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName;
    }
  }

  class JavaCompilerClassRef implements CompilerClassHierarchyElementDef {
    private final int myName;

    public JavaCompilerClassRef(int name) {
      myName = name;
    }

    @Override
    public int getName() {
      return myName;
    }

    @Override
    public CompilerRef override(int newOwner) {
      return new JavaCompilerClassRef(newOwner);
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

      JavaCompilerClassRef classRef = (JavaCompilerClassRef)o;

      if (myName != classRef.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName;
    }
  }

  class JavaCompilerFunExprDef implements CompilerFunExprDef {
    private final int myId;

    public JavaCompilerFunExprDef(int id) {
      myId = id;
    }

    @Override
    public int getId() {
      return myId;
    }

    @Override
    public CompilerRef override(int newOwner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void save(DataOutput out) {
      try {
        out.writeByte(FUN_EXPR_MARKER);
        DataInputOutputUtil.writeINT(out, getId());
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      JavaCompilerFunExprDef funExprDef = (JavaCompilerFunExprDef)o;
      return myId == funExprDef.myId;
    }

    @Override
    public int hashCode() {
      return myId;
    }

  }
}
