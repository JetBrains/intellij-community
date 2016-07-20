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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IndexTree {
  public static final boolean STUB_HIERARCHY_ENABLED = Registry.is("java.hierarchy.service");

  public final static int PACKAGE = 1;
  public final static int CLASS = 1 << 1;
  public static final int ANNOTATION   = 1 << 5;
  public static final int ENUM         = 1 << 6;
  public static final int COMPILED     = 1 << 7;
  public static final int MEMBER       = 1 << 8;
  public static final int SUPERS_UNRESOLVED = 1 << 9;

  public static final byte BYTECODE = 0;
  public static final byte JAVA = 1;
  public static final byte GROOVY = 2;

  public static class Unit {
    @NotNull public final String myPackageName;
    public final byte myUnitType;
    public final Import[] imports;
    public final ClassDecl[] myDecls;

    public Unit(@Nullable String packageName, byte unitType, Import[] imports, ClassDecl[] decls) {
      myPackageName = StringUtil.notNullize(packageName);
      myUnitType = unitType;
      this.imports = imports;
      myDecls = decls;
    }

  }

  public static class Import {
    public static final Import[] EMPTY_ARRAY = new Import[0];
    @NotNull public final String myQualifier;
    @Nullable public final String myImportedName;
    public final boolean myStaticImport;
    public final boolean myOnDemand;
    @Nullable public final String myAlias;

    public Import(String fullname, boolean staticImport, boolean onDemand, @Nullable String alias) {
      myQualifier = onDemand ? fullname : StringUtil.getPackageName(fullname);
      myImportedName = onDemand ? null : StringUtil.getShortName(fullname);
      myStaticImport = staticImport;
      myOnDemand = onDemand;
      myAlias = alias;
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

    public ClassDecl(int stubId, int mods, @Nullable String name, String[] supers, Decl[] decls) {
      super(decls);
      assert stubId > 0;
      myStubId = stubId;
      myMods = mods;
      myName = name;
      mySupers = supers;
    }

  }

  public static class MemberDecl extends Decl {

    public MemberDecl(Decl[] decls) {
      super(decls);
    }

  }

}
