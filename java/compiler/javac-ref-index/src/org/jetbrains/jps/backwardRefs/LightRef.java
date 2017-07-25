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

import com.intellij.util.Function;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

public interface LightRef {
  LightRef[] EMPTY_ARRAY = new LightRef[0];

  byte CLASS_MARKER = 0x0;
  byte METHOD_MARKER = 0x1;
  byte FIELD_MARKER = 0x2;
  byte FUN_EXPR_MARKER = 0x3;
  byte ANONYMOUS_CLASS_MARKER = 0x4;

  LightRef override(int newOwner);

  void save(DataOutput out, Function<IOException, ? extends RuntimeException> exceptionGenerator);

  interface NamedLightRef extends LightRef {
    NamedLightRef[] EMPTY_ARRAY = new NamedLightRef[0];

    int getName();
  }

  interface LightClassHierarchyElementDef extends NamedLightRef {
    LightClassHierarchyElementDef[] EMPTY_ARRAY = new LightClassHierarchyElementDef[0];

  }

  interface LightAnonymousClassDef extends LightClassHierarchyElementDef {
  }

  interface LightFunExprDef extends LightRef {
    int getId();
  }

  interface LightMember extends NamedLightRef {
    @NotNull
    LightClassHierarchyElementDef getOwner();
  }

  class JavaLightMethodRef implements LightMember {
    private final int myOwner;
    private final int myName;
    private final int myParameterCount;

    public JavaLightMethodRef(int owner, int name, int parameterCount) {
      myOwner = owner;
      myName = name;
      myParameterCount = parameterCount;
    }

    public int getName() {
      return myName;
    }

    @Override
    public LightRef override(int newOwner) {
      return new JavaLightMethodRef(newOwner, myName, myParameterCount);
    }

    @Override
    public void save(DataOutput out, Function<IOException, ? extends RuntimeException> exceptionGenerator) {
      try {
        out.writeByte(METHOD_MARKER);
        DataInputOutputUtil.writeINT(out, myOwner);
        DataInputOutputUtil.writeINT(out, getName());
        DataInputOutputUtil.writeINT(out, getParameterCount());
      }
      catch (IOException e) {
        throw exceptionGenerator.fun(e);
      }
    }

    public int getParameterCount() {
      return myParameterCount;
    }

    @Override
    @NotNull
    public LightClassHierarchyElementDef getOwner() {
      return new JavaLightClassRef(myOwner);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      JavaLightMethodRef usage = (JavaLightMethodRef)o;

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
  }

  class JavaLightFieldRef implements LightMember {
    private final int myOwner;
    private final int myName;

    public JavaLightFieldRef(int owner, int name) {
      myOwner = owner;
      myName = name;
    }

    @Override
    public int getName() {
      return myName;
    }

    @Override
    public LightRef override(int newOwner) {
      return new JavaLightFieldRef(newOwner, myName);
    }

    @Override
    public void save(DataOutput out, Function<IOException, ? extends RuntimeException> exceptionGenerator) {
      try {
        out.writeByte(FIELD_MARKER);
        DataInputOutputUtil.writeINT(out, myOwner);
        DataInputOutputUtil.writeINT(out, getName());
      }
      catch (IOException e) {
        throw exceptionGenerator.fun(e);
      }
    }

    @NotNull
    @Override
    public LightClassHierarchyElementDef getOwner() {
      return new JavaLightClassRef(myOwner);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      JavaLightFieldRef usage = (JavaLightFieldRef)o;

      if (myOwner != usage.myOwner) return false;
      if (myName != usage.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName + 31 * myOwner;
    }
  }

  class JavaLightAnonymousClassRef implements LightAnonymousClassDef {
    private final int myName;

    public JavaLightAnonymousClassRef(int name) {myName = name;}

    @Override
    public LightRef override(int newOwner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void save(DataOutput out, Function<IOException, ? extends RuntimeException> exceptionGenerator) {
      try {
        out.writeByte(ANONYMOUS_CLASS_MARKER);
        DataInputOutputUtil.writeINT(out, getName());
      }
      catch (IOException e) {
        throw exceptionGenerator.fun(e);
      }
    }

    @Override
    public int getName() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      JavaLightAnonymousClassRef ref = (JavaLightAnonymousClassRef)o;

      if (myName != ref.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName;
    }
  }

  class JavaLightClassRef implements LightClassHierarchyElementDef {
    private final int myName;

    public JavaLightClassRef(int name) {
      myName = name;
    }

    @Override
    public int getName() {
      return myName;
    }

    @Override
    public LightRef override(int newOwner) {
      return new JavaLightClassRef(newOwner);
    }

    @Override
    public void save(DataOutput out, Function<IOException, ? extends RuntimeException> exceptionGenerator) {
      try {
        out.writeByte(CLASS_MARKER);
        DataInputOutputUtil.writeINT(out, getName());
      }
      catch (IOException e) {
        throw exceptionGenerator.fun(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      JavaLightClassRef usage = (JavaLightClassRef)o;

      if (myName != usage.myName) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName;
    }
  }

  class JavaLightFunExprDef implements LightFunExprDef {
    private final int myId;

    public JavaLightFunExprDef(int id) {
      myId = id;
    }

    @Override
    public int getId() {
      return myId;
    }

    @Override
    public LightRef override(int newOwner) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void save(DataOutput out, Function<IOException, ? extends RuntimeException> exceptionGenerator) {
      try {
        out.writeByte(FUN_EXPR_MARKER);
        DataInputOutputUtil.writeINT(out, getId());
      }
      catch (IOException e) {
        throw exceptionGenerator.fun(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      JavaLightFunExprDef usage = (JavaLightFunExprDef)o;
      return myId == usage.myId;
    }

    @Override
    public int hashCode() {
      return myId;
    }

  }
}
