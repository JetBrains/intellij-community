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
package com.intellij.psi.stubsHierarchy.stubs;

import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;

public abstract class UnitInfo {
  private static final UnitInfo[] EMPTY_INFOS = new UnitInfo[]{
    new LongUnitInfo(IndexTree.BYTECODE, Import.EMPTY_ARRAY),
    new LongUnitInfo(IndexTree.JAVA, Import.EMPTY_ARRAY),
    new LongUnitInfo(IndexTree.GROOVY, Import.EMPTY_ARRAY)
  };

  static UnitInfo mkUnitInfo(byte unitType, long[] imports) {
    if (imports.length == 0) {
      return EMPTY_INFOS[unitType];
    }
    for (long anImport : imports) {
      if (Import.getAlias(anImport) != 0) {
        return new LongUnitInfo(unitType, imports);
      }
    }
    return new IntUnitInfo(unitType, imports);
  }

  /* IndexTree.BYTECODE, IndexTree.JAVA, IndexTree.GROOVY */
  public abstract byte getType();

  public abstract long[] getImports();

  static class LongUnitInfo extends UnitInfo {
    public final byte type;

    @Override
    public byte getType() {
      return type;
    }

    public long[] getImports() {
      return imports;
    }
    private long[] imports;
    public LongUnitInfo(byte type, long[] imports) {
      this.type = type;
      this.imports = imports;
    }
  }

  static class IntUnitInfo extends UnitInfo {
    public final byte type;
    private int[] imports;

    @Override
    public byte getType() {
      return type;
    }
    public long[] getImports() {
      return convert(imports);
    }
    public IntUnitInfo(byte type, long[] imports) {
      this.type = type;
      this.imports = convert(imports);
    }

    private static int[] convert(long[] ar) {
      int[] result = new int[ar.length];
      for (int i = 0; i < ar.length; i++) {
        result[i] = (int)ar[i];
      }
      return result;
    }

    private static long[] convert(int[] ar) {
      long[] result = new long[ar.length];
      for (int i = 0; i < ar.length; i++) {
        result[i] = ar[i];
      }
      return result;
    }
  }
}
