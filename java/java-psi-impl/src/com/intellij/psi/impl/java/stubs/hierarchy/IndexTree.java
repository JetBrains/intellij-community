/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.hierarchy;


import java.util.Arrays;

public class IndexTree {
  public final static int PACKAGE = 1 << 0;
  public final static int CLASS = 1 << 1;
  public static final int STATIC       = 1 << 3;
  public static final int INTERFACE    = 1 << 4;
  public static final int ANNOTATION   = 1 << 5;
  public static final int ENUM         = 1 << 6;
  public static final int COMPILED     = 1 << 7;
  public static final int MEMBER       = 1 << 8;

  public static byte BYTECODE = 0;
  public static byte JAVA = 1;
  public static byte GROOVY = 2;

  public static class Unit {
    public final int myFileId;
    public final String myPackageId;
    public final byte myUnitType;
    public final Import[] imports;
    public final ClassDecl[] myDecls;

    public Unit(int fileId, String packageId, byte unitType, Import[] imports, ClassDecl[] decls) {
      this.myFileId = fileId;
      this.myPackageId = packageId;
      this.myUnitType = unitType;
      this.imports = imports;
      this.myDecls = decls;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Unit unit = (Unit)o;

      if (myFileId != unit.myFileId) return false;
      if (myPackageId != null ? !myPackageId.equals(unit.myPackageId) : unit.myPackageId != null) return false;
      if (!Arrays.equals(imports, unit.imports)) return false;
      if (!Arrays.equals(myDecls, unit.myDecls)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFileId;
      result = 31 * result + (myPackageId != null ? myPackageId.hashCode() : 0);
      result = 31 * result + Arrays.hashCode(imports);
      result = 31 * result + Arrays.hashCode(myDecls);
      return result;
    }
  }

  public static class Import {
    public static final Import[] EMPTY_ARRAY = new Import[0];
    public final String myFullname;
    public final boolean myStaticImport;
    public final boolean myOnDemand;
    public final String myAlias;

    public Import(String fullname, boolean staticImport, boolean onDemand, String alias) {
      this.myFullname = fullname;
      this.myStaticImport = staticImport;
      this.myOnDemand = onDemand;
      this.myAlias = alias;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Import anImport = (Import)o;

      if (myStaticImport != anImport.myStaticImport) return false;
      if (myOnDemand != anImport.myOnDemand) return false;
      if (!myFullname.equals(anImport.myFullname)) return false;
      if (myAlias != null ? !myAlias.equals(anImport.myAlias) : anImport.myAlias != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFullname.hashCode();
      result = 31 * result + (myStaticImport ? 1 : 0);
      result = 31 * result + (myOnDemand ? 1 : 0);
      result = 31 * result + (myAlias != null ? myAlias.hashCode() : 0);
      return result;
    }
  }

  public static abstract class Decl {
    public static final Decl[] EMPTY_ARRAY = new Decl[0];
    public final Decl[] myDecls;

    protected Decl(Decl[] decls) {
      this.myDecls = decls;
    }
  }

  public static class ClassDecl extends Decl {
    public static final ClassDecl[] EMPTY_ARRAY = new ClassDecl[0];
    public final int myStubId;
    public final int myMods;
    public final String myName;
    public final String[] mySupers;

    public ClassDecl(int stubId, int mods, String name, String[] supers, Decl[] decls) {
      super(decls);
      this.myStubId = stubId;
      this.myMods = mods;
      this.myName = name;
      this.mySupers = supers;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ClassDecl classDecl = (ClassDecl)o;
      if (myStubId != classDecl.myStubId) return false;
      if (myMods != classDecl.myMods) return false;
      if (myName != null ? !myName.equals(classDecl.myName) : classDecl.myName != null) return false;
      if (!Arrays.equals(mySupers, classDecl.mySupers)) return false;
      if (!Arrays.equals(myDecls, classDecl.myDecls)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = myStubId;
      result = 31 * result + myMods;
      result = 31 * result + (myName != null ? myName.hashCode() : 0);
      result = 31 * result + Arrays.hashCode(mySupers);
      result = 31 * result + Arrays.hashCode(myDecls);
      return result;
    }
  }

  public static class MemberDecl extends Decl {

    public MemberDecl(Decl[] decls) {
      super(decls);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MemberDecl that = (MemberDecl)o;
      if (!Arrays.equals(myDecls, that.myDecls)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myDecls);
    }
  }

}
