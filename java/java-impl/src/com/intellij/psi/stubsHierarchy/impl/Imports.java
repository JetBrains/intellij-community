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
import com.intellij.util.BitUtil;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

class Imports {
  public final static Import[] EMPTY_ARRAY = new Import[0];
  private final TIntObjectHashMap<Import> myNonStaticPackageImports = new TIntObjectHashMap<>();

  private final static int IS_STATIC = 1;
  private final static int IS_ON_DEMAND = 2;
  private final static int HAS_ALIAS = 4;

  static void writeImports(@NotNull DataOutput out, IndexTree.Unit value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.imports.length);
    for (IndexTree.Import anImport : value.imports) {
      writeImport(out, anImport);
    }
  }

  Import[] readImports(UnitInputStream in) throws IOException {
    int importCount = DataInputOutputUtil.readINT(in);
    Import[] imports = importCount == 0 ? EMPTY_ARRAY : new Import[importCount];
    for (int i = 0; i < importCount; i++) {
      imports[i] = readImport(in);
    }
    return imports;
  }

  private static void writeImport(@NotNull DataOutput out, IndexTree.Import anImport) throws IOException {
    out.writeInt(NameEnvironment.fromString(anImport.myQualifier));
    boolean hasAlias = anImport.myAlias != null;
    int flags = 0;
    flags = BitUtil.set(flags, IS_STATIC, anImport.myStaticImport);
    flags = BitUtil.set(flags, IS_ON_DEMAND, anImport.myOnDemand);
    flags = BitUtil.set(flags, HAS_ALIAS, hasAlias);
    out.writeByte(flags);
    if (anImport.myImportedName != null) {
      out.writeInt(NameEnvironment.hashIdentifier(anImport.myImportedName));
    }
    if (hasAlias) {
      out.writeInt(NameEnvironment.hashIdentifier(anImport.myAlias));
    }
  }

  private Import readImport(UnitInputStream in) throws IOException {
    int qualifier = in.readInt();
    int flags = in.readByte();
    int shortName = BitUtil.isSet(flags, IS_ON_DEMAND) ? 0 : in.readInt();
    int alias = BitUtil.isSet(flags, HAS_ALIAS) ? in.readInt() : 0;

    return obtainImport(qualifier, shortName, alias, BitUtil.isSet(flags, IS_STATIC));
  }

  private Import obtainImport(int qualifier, int shortName, int alias, boolean isStatic) {
    boolean shouldIntern = shortName == 0 && alias == 0 && !isStatic;
    if (shouldIntern) {
      Import existing = myNonStaticPackageImports.get(qualifier);
      if (existing != null) {
        return existing;
      }
    }

    Import anImport = alias != 0 ? new AliasedImport(qualifier, shortName, isStatic, alias) : new Import(qualifier, shortName, isStatic);
    if (shouldIntern) {
      myNonStaticPackageImports.put(qualifier, anImport);
    }
    return anImport;
  }
}

class Import {
  @QNameHash final int qualifier;
  @ShortName final int importedName; // 0 for on-demand
  final boolean isStatic;

  Import(@QNameHash int qualifier, @ShortName int importedName, boolean isStatic) {
    this.qualifier = qualifier;
    this.importedName = importedName;
    this.isStatic = isStatic;
  }

  boolean isOnDemand() {
    return importedName == 0;
  }

  @ShortName int getAlias() {
    return 0;
  }

}

class AliasedImport extends Import {
  private final @ShortName int alias;

  AliasedImport(@QNameHash int qualifier,
                @ShortName int importedName,
                boolean isStatic,
                @ShortName int alias) {
    super(qualifier, importedName, isStatic);
    this.alias = alias;
  }

  @Override
  int getAlias() {
    return alias;
  }
}