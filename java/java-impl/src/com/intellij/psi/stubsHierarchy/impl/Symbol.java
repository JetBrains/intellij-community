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

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

/**
 * Java symbols needed for hierarchy building. Mostly classes ({@link ClassSymbol}) or packages ({@link PackageSymbol}),
 * but other {@link MemberSymbol}s are also sometimes needed for working with anonymous or local classes.
 */
public abstract class Symbol {
  public int myFlags;
  public int myShortName;
  public final Symbol myOwner;

  public Symbol(int flags, Symbol owner, int name) {
    this.myFlags = flags;
    this.myOwner = owner;
    this.myShortName = name;
  }

  @Override
  public int hashCode() {
    return myShortName;
  }

  ClassSymbol[] getMembers() {
    return ClassSymbol.EMPTY_ARRAY;
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
    final QualifiedName myQualifiedName;

    public PackageSymbol(Symbol owner, QualifiedName fullname, int name) {
      super(IndexTree.PACKAGE, owner, name);
      myQualifiedName = fullname;
    }
  }

  /** A class for class symbols
   */
  public static class ClassSymbol extends MemberSymbol {
    private static final int HIERARCHY_INCOMPLETE = 1 << 20;
    private static final int CONNECT_STARTED = 1 << 21;
    public static final ClassSymbol[] EMPTY_ARRAY = new ClassSymbol[0];

    final StubClassAnchor myClassAnchor;

    /**
     * null for empty 'supers' list
     * ClassSymbol/QualifiedName for a single resolved/unresolved super
     * ClassSymbol[]/QualifiedName[] for multiple resolved/unresolved supers
     */
    Object mySuperClasses;
    UnitInfo myUnitInfo;

    ClassSymbol(StubClassAnchor classAnchor,
                int flags,
                Symbol owner,
                int name,
                UnitInfo unitInfo,
                QualifiedName[] supers) {
      super(flags | IndexTree.CLASS, owner, name);
      this.myClassAnchor = classAnchor;
      this.mySuperClasses = supers.length == 0 ? null : supers.length == 1 ? supers[0] : supers;
      this.myUnitInfo = unitInfo;
    }

    @Override
    public String toString() {
      return myClassAnchor.toString();
    }

    void connect(StubHierarchyConnector connector) {
      if (!isConnectStarted()) {
        myFlags = BitUtil.set(myFlags, CONNECT_STARTED, true);
        connector.connect(this);
      }
    }

    private boolean isConnectStarted() {
      return BitUtil.isSet(myFlags, CONNECT_STARTED);
    }

    @NotNull
    ClassSymbol[] getSuperClasses(StubHierarchyConnector connector) throws IncompleteHierarchyException {
      connect(connector);
      if (isHierarchyIncomplete()) {
        throw IncompleteHierarchyException.INSTANCE;
      }
      return rawSuperClasses();
    }

    @NotNull
    ClassSymbol[] rawSuperClasses() {
      assert isConnectStarted();
      return mySuperClasses instanceof ClassSymbol ? new ClassSymbol[]{(ClassSymbol)mySuperClasses} :
             mySuperClasses instanceof ClassSymbol[] ? (ClassSymbol[])mySuperClasses :
             EMPTY_ARRAY;
    }

    boolean isCompiled() {
      return BitUtil.isSet(myFlags, IndexTree.COMPILED);
    }

    void markHierarchyIncomplete() {
      setSupers(Collections.emptySet());
      myFlags = BitUtil.set(myFlags, HIERARCHY_INCOMPLETE, true);
    }

    void setSupers(Set<ClassSymbol> supers) {
      mySuperClasses = supers.isEmpty() ? null :
                       supers.size() == 1 ? supers.iterator().next() :
                       supers.toArray(new ClassSymbol[supers.size()]);
      myUnitInfo = null;
    }

    boolean isHierarchyIncomplete() {
      return BitUtil.isSet(myFlags, HIERARCHY_INCOMPLETE);
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
    /**
     * null when no members, or a single ClassSymbol, or ClassSymbol[]
     */
    private Object myMembers = null;

    MemberSymbol(Symbol owner) {
      super(IndexTree.MEMBER, owner, NamesEnumerator.NO_NAME);
    }

    MemberSymbol(int flags, Symbol owner, int name) {
      super(flags, owner, name);
    }

    ClassSymbol[] getMembers() {
      return myMembers == null ? ClassSymbol.EMPTY_ARRAY :
             myMembers instanceof ClassSymbol ? new ClassSymbol[]{(ClassSymbol)myMembers} :
             (ClassSymbol[])myMembers;
    }

    void setMembers(ClassSymbol[] members) {
      myMembers = members.length == 0 ? null : members.length == 1 ? members[0] : members;
    }
  }

  public static final Comparator<ClassSymbol> CLASS_SYMBOL_BY_NAME_COMPARATOR = (s1, s2) -> {
    int name1 = s1.myShortName;
    int name2 = s2.myShortName;
    return (name1 < name2) ? -1 : ((name1 == name2) ? 0 : 1);
  };
}
