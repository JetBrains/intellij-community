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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.stubs.UnitInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * Java symbols needed for hierarchy building. Mostly classes ({@link ClassSymbol}) or packages ({@link PackageSymbol}),
 * but other {@link MemberSymbol}s are also sometimes needed for working with anonymous or local classes.
 */
public abstract class Symbol {
  public int myFlags;
  public int myShortName;
  public final QualifiedName myQualifiedName;
  public final Symbol myOwner;

  public Symbol(int flags, Symbol owner, QualifiedName qualifiedName, int name) {
    this.myFlags = flags;
    this.myOwner = owner;
    this.myQualifiedName = qualifiedName;
    this.myShortName = name;
  }

  public ClassSymbol[] members() {
    return ClassSymbol.EMPTY_ARRAY;
  }

  public void setMembers(ClassSymbol[] members) {
  }

  public boolean isStatic() {
    return (myFlags & IndexTree.STATIC) != 0;
  }

  public boolean isPackage() {
    return (myFlags & IndexTree.PACKAGE) != 0;}

  public boolean isClass() {
    return (myFlags & IndexTree.CLASS) != 0;}

  public boolean isMember() {
    return (myFlags & IndexTree.MEMBER) != 0;}

  public PackageSymbol pkg() {
    Symbol sym = this;
    while (!sym.isPackage()) {
      sym = sym.myOwner;
    }
    return (PackageSymbol) sym;
  }

  public static class PackageSymbol extends Symbol {
    public PackageSymbol(Symbol owner, QualifiedName fullname, int name) {
      super(IndexTree.PACKAGE, owner, fullname, name);
      setMembers(ClassSymbol.EMPTY_ARRAY);
    }
  }

  /** A class for class symbols
   */
  public static class ClassSymbol extends Symbol {
    public static final ClassSymbol[] EMPTY_ARRAY = new ClassSymbol[0];
    public final SmartClassAnchor myClassAnchor;
    public ClassSymbol[] mySuperClasses;
    public UnitInfo myUnitInfo;
    public QualifiedName[] mySuperNames;
    private ClassSymbol[] myMembers;
    private HierarchyConnector myConnector;

    public ClassSymbol(SmartClassAnchor classAnchor,
                       int flags,
                       Symbol owner,
                       QualifiedName fullname,
                       int name,
                       UnitInfo unitInfo,
                       QualifiedName[] supers,
                       HierarchyConnector connector) {
      super(flags | IndexTree.CLASS, owner, fullname, name);
      this.myClassAnchor = classAnchor;
      this.mySuperNames = supers;
      this.myUnitInfo = unitInfo;
      this.myConnector = connector;
    }

    public void connect() {
      if (myConnector != null) {
        HierarchyConnector c = myConnector;
        myConnector = null;
        c.connect(this);
      }
    }

    @NotNull
    public ClassSymbol[] getSuperClasses() {
      connect();
      if (mySuperClasses == null) {
        return EMPTY_ARRAY;
      }
      return mySuperClasses;
    }

    public boolean isCompiled() {
      return (myFlags & IndexTree.COMPILED) != 0;
    }

    public ClassSymbol[] members() {
      return myMembers;
    }

    public void setMembers(ClassSymbol[] members) {
      this.myMembers = members;
    }
  }

  /**
   * Represents methods, fields and other constructs that may contain anonymous or local classes.
   */
  public static class MemberSymbol extends Symbol {
    private ClassSymbol[] myMembers;
    public MemberSymbol(Symbol owner) {
      super(IndexTree.MEMBER, owner, null, NamesEnumerator.NO_NAME);
    }
    public ClassSymbol[] members() {
      return myMembers;
    }
    public void setMembers(ClassSymbol[] members) {
      this.myMembers = members;
    }
  }

  public static final Comparator<ClassSymbol> CLASS_SYMBOL_BY_NAME_COMPARATOR = new Comparator<ClassSymbol>() {
    @Override
    public int compare(ClassSymbol s1, ClassSymbol s2) {
      int name1 = s1.myShortName;
      int name2 = s2.myShortName;
      return (name1 < name2) ? -1 : ((name1 == name2) ? 0 : 1);
    }
  };
}
