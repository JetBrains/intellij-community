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
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;

import static com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import static com.intellij.psi.stubsHierarchy.impl.Symbol.PackageSymbol;

public class StubEnter {
  final Imports imports = new Imports();
  private final Symbols mySymbols;
  private final StubHierarchyConnector myStubHierarchyConnector;

  private ArrayList<ClassSymbol> uncompleted = new ArrayList<>();

  StubEnter(Symbols symbols) {
    mySymbols = symbols;
    myStubHierarchyConnector = new StubHierarchyConnector(symbols);
  }

  PackageSymbol readPackageName(DataInput in) throws IOException {
    PackageSymbol pkg = mySymbols.myRootPackage;
    int qname = 0;
    int len = DataInputOutputUtil.readINT(in);
    for (int i = 0; i < len; i++) {
      int shortName = in.readInt();
      qname = NameEnvironment.qualifiedName(qname, shortName);
      pkg = mySymbols.enterPackage(qname, shortName, pkg);
    }
    return pkg;
  }

  ClassSymbol classEnter(UnitInfo info,
                         Symbol owner,
                         int stubId,
                         int mods,
                         @ShortName int name,
                         @CompactArray(QualifiedName.class) Object superNames,
                         @QNameHash int qname, int fileId) throws IOException {
    int flags = checkFlags(mods, info.isCompiled());
    @CompactArray(QualifiedName.class) Object supers = handleSpecialSupers(mods, superNames);

    ClassSymbol classSymbol = mySymbols.enterClass(fileId, stubId, flags, name, owner, info, supers, qname);
    if (uncompleted != null) {
      uncompleted.add(classSymbol);
    }
    return classSymbol;
  }

  @Nullable
  @CompactArray(QualifiedName.class)
  Object handleSpecialSupers(int flags, @CompactArray(QualifiedName.class) Object superNames) {
    if (BitUtil.isSet(flags, IndexTree.ANNOTATION)) {
      return NameEnvironment.java_lang_annotation_Annotation;
    }

    if (BitUtil.isSet(flags, IndexTree.ENUM)) {
      if (superNames == null) return NameEnvironment.java_lang_Enum;
      if (superNames instanceof QualifiedName) return new QualifiedName[]{(QualifiedName)superNames, NameEnvironment.java_lang_Enum};
      return ArrayUtil.append((QualifiedName[])superNames, NameEnvironment.java_lang_Enum);
    }

    return superNames;
  }

  public void connect1() {
    for (ClassSymbol classSymbol : uncompleted) {
      classSymbol.connect(myStubHierarchyConnector);
    }
    uncompleted = new ArrayList<>();
  }

  public void connect2() {
    for (ClassSymbol classSymbol : uncompleted) {
      classSymbol.connect(myStubHierarchyConnector);
    }
    uncompleted = null;
  }

  private static int checkFlags(long flags, boolean compiled) {
    int mask = 0;
    if ((flags & IndexTree.SUPERS_UNRESOLVED) != 0) {
      mask |= IndexTree.SUPERS_UNRESOLVED;
    }
    if (compiled) {
      mask |= IndexTree.COMPILED;
    }
    return mask;
  }
}
