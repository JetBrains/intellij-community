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
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

import static com.intellij.psi.stubsHierarchy.impl.Symbol.*;

public class StubEnter {
  private final NameEnvironment myNameEnvironment;
  private final Symbols mySymbols;
  private final StubHierarchyConnector myStubHierarchyConnector;

  private ArrayList<ClassSymbol> uncompleted = new ArrayList<ClassSymbol>();

  StubEnter(Symbols symbols) {
    myNameEnvironment = symbols.myNameEnvironment;
    mySymbols = symbols;
    myStubHierarchyConnector = new StubHierarchyConnector(myNameEnvironment, symbols);
  }

  void unitEnter(IndexTree.Unit unit, int fileId) {
    @QNameId int pkgName = unit.myPackageName.length > 0 ? myNameEnvironment.internQualifiedName(unit.myPackageName) : 0;
    enter(unit.myDecls, UnitInfo.mkUnitInfo(unit.myUnitType, internImports(unit)), mySymbols.enterPackage(pkgName), pkgName, fileId);
  }

  private long[] internImports(IndexTree.Unit unit) {
    long[] imports = unit.imports.length == 0 ? Imports.EMPTY_ARRAY : new long[unit.imports.length];
    for (int i = 0; i < unit.imports.length; i++) {
      imports[i] = processImport(unit.imports[i]);
    }
    return imports;
  }

  private long processImport(IndexTree.Import anImport) {
    int fullname = myNameEnvironment.internQualifiedName(anImport.myFullname);
    return Imports.mkImport(fullname, anImport.myStaticImport, anImport.myOnDemand, anImport.myAlias);
  }

  private void enter(IndexTree.ClassDecl[] trees, UnitInfo info, Symbol owner, @QNameId int ownerName, int fileId) {
    for (IndexTree.ClassDecl tree : trees) {
      enter(tree, info, owner, ownerName, fileId);
    }
  }

  private ClassSymbol[] enter(IndexTree.Decl[] trees, UnitInfo info, Symbol owner, @QNameId int ownerName, int fileId) {
    ClassSymbol[] members = new ClassSymbol[trees.length];
    int i = 0;
    for (IndexTree.Decl tree : trees) {
      ClassSymbol member = enter(tree, info, owner, ownerName, fileId);
      if (member != null && member.myShortName != 0) {
        members[i++] = member;
      }
    }
    if (i == 0) return ClassSymbol.EMPTY_ARRAY;

    if (i < members.length) {
      members = Arrays.copyOf(members, i);
    }
    Arrays.sort(members, CLASS_SYMBOL_BY_NAME_COMPARATOR);
    return members;
  }

  private ClassSymbol enter(IndexTree.Decl tree, UnitInfo info, Symbol owner, @QNameId int ownerName, int fileId) {
    if (tree instanceof IndexTree.ClassDecl) {
      return classEnter((IndexTree.ClassDecl)tree, info, owner, ownerName, fileId);
    }
    if (tree instanceof IndexTree.MemberDecl) {
      memberEnter((IndexTree.MemberDecl)tree, info, owner, ownerName, fileId);
      return null;
    }
    return null;
  }

  private void memberEnter(IndexTree.MemberDecl tree, UnitInfo info, Symbol owner, @QNameId int ownerName, int fileId) {
    MemberSymbol mc = new MemberSymbol(owner);
    mc.setMembers(enter(tree.myDecls, info, mc, ownerName, fileId));
  }

  private ClassSymbol classEnter(IndexTree.ClassDecl tree, UnitInfo info, Symbol owner, @QNameId int ownerName, int fileId) {
    int flags = checkFlags(tree.myMods, owner, info.getType() == IndexTree.BYTECODE);

    int name = tree.myName;
    @QNameId int qname = name == NameEnvironment.NO_NAME || ownerName < 0 ? -1
                                                                          : myNameEnvironment.qualifiedName(ownerName, name);
    @CompactArray(QualifiedName.class) Object supers = internSupers(tree.myMods, tree.mySupers);
    ClassSymbol classSymbol = mySymbols.enterClass(fileId, tree.myStubId, flags, name, owner, info, supers, qname);

    if (uncompleted != null) {
      uncompleted.add(classSymbol);
    }
    if (tree.myDecls.length > 0) {
      classSymbol.setMembers(enter(tree.myDecls, info, classSymbol, qname, fileId));
    }
    return classSymbol;
  }

  @Nullable
  @CompactArray(QualifiedName.class)
  private Object internSupers(int flags, int[][] superNames) {
    if (BitUtil.isSet(flags, IndexTree.ANNOTATION)) {
      return myNameEnvironment.java_lang_annotation_Annotation;
    }

    boolean isEnum = BitUtil.isSet(flags, IndexTree.ENUM);
    if (superNames.length == 0) {
      return isEnum ? myNameEnvironment.java_lang_Enum : null;
    }
    if (superNames.length == 1 && !isEnum) {
      return new QualifiedName(myNameEnvironment.internQualifiedName(superNames[0]));
    }

    QualifiedName[] array = new QualifiedName[superNames.length + (isEnum ? 1 : 0)];
    for (int i = 0; i < superNames.length; i++) {
      array[i] = new QualifiedName(myNameEnvironment.internQualifiedName(superNames[i]));
    }
    if (isEnum) {
      array[array.length - 1] = myNameEnvironment.java_lang_Enum;
    }
    return array;
  }

  public void connect1() {
    for (ClassSymbol classSymbol : uncompleted) {
      classSymbol.connect(myStubHierarchyConnector);
    }
    uncompleted = new ArrayList<ClassSymbol>();
  }

  public void connect2() {
    for (ClassSymbol classSymbol : uncompleted) {
      classSymbol.connect(myStubHierarchyConnector);
    }
    uncompleted = null;
  }

  private static int checkFlags(long flags, Symbol owner, boolean compiled) {
    int mask = 0;
    if (owner.isClass() && (owner.myOwner.isPackage() || owner.isStatic())) {
      if ((flags & (IndexTree.INTERFACE | IndexTree.ENUM | IndexTree.STATIC)) != 0) {
        mask |= IndexTree.STATIC;
      }
    }
    if ((flags & IndexTree.SUPERS_UNRESOLVED) != 0) {
      mask |= IndexTree.SUPERS_UNRESOLVED;
    }
    if (compiled) {
      mask |= IndexTree.COMPILED;
    }
    return mask;
  }
}
