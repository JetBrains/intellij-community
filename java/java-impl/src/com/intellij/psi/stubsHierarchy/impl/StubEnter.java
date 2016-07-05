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

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;

import static com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import static com.intellij.psi.stubsHierarchy.impl.Symbol.PackageSymbol;

public class StubEnter {
  final NameEnvironment myNameEnvironment;
  private final Symbols mySymbols;
  private final StubHierarchyConnector myStubHierarchyConnector;

  private ArrayList<ClassSymbol> uncompleted = new ArrayList<ClassSymbol>();

  StubEnter(Symbols symbols) {
    myNameEnvironment = symbols.myNameEnvironment;
    mySymbols = symbols;
    myStubHierarchyConnector = new StubHierarchyConnector(myNameEnvironment, symbols);
  }

  PackageSymbol enterPackage(DataInput in) throws IOException {
    return mySymbols.enterPackage(myNameEnvironment.readQualifiedName(in));
  }

  ClassSymbol classEnter(UnitInfo info,
                         Symbol owner,
                         int stubId,
                         int mods,
                         @ShortName int name,
                         @QNameId int[] superNames,
                         @QNameId int qname, int fileId) throws IOException {
    int flags = checkFlags(mods, owner, info.getType() == IndexTree.BYTECODE);
    @CompactArray(QualifiedName.class) Object supers = internSupers(mods, superNames);

    ClassSymbol classSymbol = mySymbols.enterClass(fileId, stubId, flags, name, owner, info, supers, qname);
    if (uncompleted != null) {
      uncompleted.add(classSymbol);
    }
    return classSymbol;
  }

  @Nullable
  @CompactArray(QualifiedName.class)
  Object internSupers(int flags, int[] superNames) {
    if (BitUtil.isSet(flags, IndexTree.ANNOTATION)) {
      return myNameEnvironment.java_lang_annotation_Annotation;
    }

    boolean isEnum = BitUtil.isSet(flags, IndexTree.ENUM);
    if (superNames.length == 0) {
      return isEnum ? myNameEnvironment.java_lang_Enum : null;
    }
    if (superNames.length == 1 && !isEnum) {
      return new QualifiedName(superNames[0]);
    }

    QualifiedName[] array = new QualifiedName[superNames.length + (isEnum ? 1 : 0)];
    for (int i = 0; i < superNames.length; i++) {
      array[i] = new QualifiedName(superNames[i]);
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
