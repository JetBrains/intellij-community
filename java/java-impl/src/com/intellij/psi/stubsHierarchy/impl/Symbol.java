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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.stubs.UnitInfo;
import com.intellij.util.BitUtil;
import gnu.trove.TIntHashSet;
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

  @Override
  public int hashCode() {
    return myShortName;
  }

  public ClassSymbol[] members() {
    return ClassSymbol.EMPTY_ARRAY;
  }

  public void setMembers(ClassSymbol[] members) {
  }

  public boolean isStatic() {
    return BitUtil.isSet(myFlags, IndexTree.STATIC);
  }

  public boolean isPackage() {
    return BitUtil.isSet(myFlags, IndexTree.PACKAGE);
  }

  public boolean isClass() {
    return BitUtil.isSet(myFlags, IndexTree.CLASS);
  }

  public boolean isMember() {
    return BitUtil.isSet(myFlags, IndexTree.MEMBER);
  }

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
    public final StubClassAnchor myClassAnchor;
    public ClassSymbol[] mySuperClasses;
    public UnitInfo myUnitInfo;
    public QualifiedName[] mySuperNames;
    private ClassSymbol[] myMembers;
    private StubHierarchyConnector myConnector;
    private boolean myHierarchyIncomplete;

    public ClassSymbol(StubClassAnchor classAnchor,
                       int flags,
                       Symbol owner,
                       QualifiedName fullname,
                       int name,
                       UnitInfo unitInfo,
                       QualifiedName[] supers,
                       StubHierarchyConnector connector) {
      super(flags | IndexTree.CLASS, owner, fullname, name);
      this.myClassAnchor = classAnchor;
      this.mySuperNames = supers;
      this.myUnitInfo = unitInfo;
      this.myConnector = connector;
    }

    @Override
    public String toString() {
      return myClassAnchor.toString();
    }

    public void connect() {
      if (myConnector != null) {
        StubHierarchyConnector c = myConnector;
        myConnector = null;
        c.connect(this);
      }
    }

    @NotNull
    public ClassSymbol[] getSuperClasses() throws IncompleteHierarchyException {
      connect();
      if (myHierarchyIncomplete) {
        throw IncompleteHierarchyException.INSTANCE;
      }
      return rawSuperClasses();
    }

    @NotNull
    ClassSymbol[] rawSuperClasses() {
      assert myConnector == null;
      return mySuperClasses == null ? EMPTY_ARRAY : mySuperClasses;
    }

    public boolean isCompiled() {
      return BitUtil.isSet(myFlags, IndexTree.COMPILED);
    }

    public ClassSymbol[] members() {
      return myMembers;
    }

    public void setMembers(ClassSymbol[] members) {
      this.myMembers = members;
    }

    void markHierarchyIncomplete() {
      mySuperClasses = EMPTY_ARRAY;
      mySuperNames = null;
      myUnitInfo = null;
      myHierarchyIncomplete = true;
    }

    boolean isHierarchyIncomplete() {
      return myHierarchyIncomplete;
    }

    boolean hasAmbiguousSupers() {
      ClassSymbol[] superClasses = rawSuperClasses();
      if (superClasses.length < 2) return false;

      TIntHashSet superNames = new TIntHashSet();
      for (ClassSymbol symbol : superClasses) {
        if (!superNames.add(symbol.myShortName)) {
          return true;
        }
      }

      return false;
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

  public static final Comparator<ClassSymbol> CLASS_SYMBOL_BY_NAME_COMPARATOR = (s1, s2) -> {
    int name1 = s1.myShortName;
    int name2 = s2.myShortName;
    return (name1 < name2) ? -1 : ((name1 == name2) ? 0 : 1);
  };
}
