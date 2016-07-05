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
package com.intellij.psi.impl.java.stubs.hierarchy;


import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNameHelper;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class IndexTree {
  public static final boolean STUB_HIERARCHY_ENABLED = Registry.is("java.hierarchy.service");

  @SuppressWarnings("PointlessBitwiseExpression")
  public final static int PACKAGE = 1 << 0;
  public final static int CLASS = 1 << 1;
  public static final int STATIC       = 1 << 3;
  public static final int INTERFACE    = 1 << 4;
  public static final int ANNOTATION   = 1 << 5;
  public static final int ENUM         = 1 << 6;
  public static final int COMPILED     = 1 << 7;
  public static final int MEMBER       = 1 << 8;
  public static final int SUPERS_UNRESOLVED = 1 << 9;

  public static final byte BYTECODE = 0;
  public static final byte JAVA = 1;
  public static final byte GROOVY = 2;

  public static int hashIdentifier(@Nullable String s) {
    if (s == null) return 0;

    // not using String.hashCode because this way there's less collisions for short package names like 'com'
    int hash = 0;
    for (int i = 0; i < s.length(); i++) {
      hash = hash * 239 + s.charAt(i);
    }
    return hash;
  }

  public static int[] hashQualifiedName(@NotNull String qName) {
    qName = PsiNameHelper.getQualifiedClassName(qName, true);
    if (qName.isEmpty()) return ArrayUtil.EMPTY_INT_ARRAY;

    List<String> components = StringUtil.split(qName, ".");
    int[] result = new int[components.size()];
    for (int i = 0; i < components.size(); i++) {
      result[i] = hashIdentifier(components.get(i));
    }
    return result;
  }

  private static int[][] hashQualifiedNameArray(String[] supers) {
    int[][] superHashes = new int[supers.length][];
    for (int i = 0; i < supers.length; i++) {
      superHashes[i] = hashQualifiedName(supers[i]);
    }
    return superHashes;
  }

  public static class Unit {
    @NotNull public final int[] myPackageName;
    public final byte myUnitType;
    public final Import[] imports;
    public final ClassDecl[] myDecls;

    public Unit(@Nullable String packageName, byte unitType, Import[] imports, ClassDecl[] decls) {
      this(hashQualifiedName(StringUtil.notNullize(packageName)), unitType, imports, decls);
    }

    public Unit(@NotNull int[] packageName, byte unitType, Import[] imports, ClassDecl[] decls) {
      myPackageName = packageName;
      myUnitType = unitType;
      this.imports = imports;
      myDecls = decls;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Unit unit = (Unit)o;

      if (myUnitType != unit.myUnitType) return false;
      if (!Arrays.equals(myPackageName, unit.myPackageName)) return false;
      if (!Arrays.equals(imports, unit.imports)) return false;
      if (!Arrays.equals(myDecls, unit.myDecls)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int hash = myUnitType * 31 + Arrays.hashCode(myPackageName);
      for (ClassDecl decl : myDecls) {
        int name = decl.myName;
        if (name != 0) {
          return hash * 31 + name;
        }
      }
      return hash;
    }
  }

  public static class Import {
    public static final Import[] EMPTY_ARRAY = new Import[0];
    public final int[] myFullname;
    public final boolean myStaticImport;
    public final boolean myOnDemand;
    public final int myAlias;

    public Import(String fullname, boolean staticImport, boolean onDemand, @Nullable String alias) {
      this(hashQualifiedName(fullname), staticImport, onDemand, hashIdentifier(alias));
    }

    public Import(int[] fullname, boolean staticImport, boolean onDemand, int alias) {
      myFullname = fullname;
      myStaticImport = staticImport;
      myOnDemand = onDemand;
      myAlias = alias;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Import anImport = (Import)o;

      if (myStaticImport != anImport.myStaticImport) return false;
      if (myOnDemand != anImport.myOnDemand) return false;
      if (!Arrays.equals(myFullname, anImport.myFullname)) return false;
      if (myAlias != anImport.myAlias) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(myFullname);
      result = 31 * result + (myStaticImport ? 1 : 0);
      result = 31 * result + (myOnDemand ? 1 : 0);
      result = 31 * result + myAlias;
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
    public final int myName;
    public final int[][] mySupers;

    public ClassDecl(int stubId, int mods, @Nullable String name, String[] supers, Decl[] decls) {
      this(stubId, mods, hashIdentifier(name), hashQualifiedNameArray(supers), decls);
    }

    public ClassDecl(int stubId, int mods, int name, int[][] supers, Decl[] decls) {
      super(decls);
      assert stubId > 0;
      myStubId = stubId;
      myMods = mods;
      myName = name;
      mySupers = supers;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ClassDecl classDecl = (ClassDecl)o;
      if (myStubId != classDecl.myStubId) return false;
      if (myMods != classDecl.myMods) return false;
      if (myName != classDecl.myName) return false;
      if (!Arrays.deepEquals(mySupers, classDecl.mySupers)) return false;
      if (!Arrays.equals(myDecls, classDecl.myDecls)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = myStubId;
      result = 31 * result + myMods;
      result = 31 * result + myName;
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
