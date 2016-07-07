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

final class UnitInfo {
  private static final UnitInfo[] EMPTY_INFOS = new UnitInfo[]{
    new UnitInfo(IndexTree.BYTECODE, Imports.EMPTY_ARRAY),
    new UnitInfo(IndexTree.JAVA, Imports.EMPTY_ARRAY),
    new UnitInfo(IndexTree.GROOVY, Imports.EMPTY_ARRAY)
  };

  /* IndexTree.BYTECODE, IndexTree.JAVA, IndexTree.GROOVY */
  final byte type;
  final Import[] imports;

  private UnitInfo(byte type, Import[] imports) {
    this.type = type;
    this.imports = imports;
  }

  static UnitInfo mkUnitInfo(byte unitType, Import[] imports) {
    return imports.length == 0 ? EMPTY_INFOS[unitType] : new UnitInfo(unitType, imports);
  }

  boolean isCompiled() {
    return type == IndexTree.BYTECODE;
  }

}
