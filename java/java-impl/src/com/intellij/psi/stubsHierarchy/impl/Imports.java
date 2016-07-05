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
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

class Imports {
  public final static long[] EMPTY_ARRAY = ArrayUtil.EMPTY_LONG_ARRAY;

  public static final int onDemandMask = 1 << 29;
  public static final int staticMask = 1 << 30;

  public static int mask = ~(onDemandMask | staticMask);

  public static int getAlias(long importMask) {
    return (int)(importMask >>> 32);
  }

  static int getFullNameId(long importMask) {
    int fullNameId = (int)importMask;
    fullNameId &= mask;
    return fullNameId;
  }

  public static boolean isOnDemand(long importMask) {
    return BitUtil.isSet(importMask, onDemandMask);
  }

  public static boolean isStatic(long importMask) {
    return BitUtil.isSet(importMask, staticMask);
  }

  public static long mkImport(@QNameId int fullname, boolean importStatic, boolean onDemand, int alias) {
    long lower = fullname;
    if (importStatic) lower |= staticMask;
    if (onDemand) lower |= onDemandMask;
    return (((long)alias) << 32) | lower;
  }

  private final static int IS_STATIC = 1;
  private final static int IS_ON_DEMAND = 2;
  private final static int HAS_ALIAS = 4;

  static void writeImports(@NotNull DataOutput out, IndexTree.Unit value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.imports.length);
    for (IndexTree.Import anImport : value.imports) {
      writeImport(out, anImport);
    }
  }

  static long[] readImports(UnitInputStream in) throws IOException {
    int importCount = DataInputOutputUtil.readINT(in);
    long[] imports = importCount == 0 ? EMPTY_ARRAY : new long[importCount];
    for (int i = 0; i < importCount; i++) {
      imports[i] = readImport(in);
    }
    return imports;
  }

  private static void writeImport(@NotNull DataOutput out, IndexTree.Import anImport) throws IOException {
    SerializedUnit.writeQualifiedName(out, anImport.myFullname);
    boolean hasAlias = anImport.myAlias != 0;
    int flags = 0;
    flags = BitUtil.set(flags, IS_STATIC, anImport.myStaticImport);
    flags = BitUtil.set(flags, IS_ON_DEMAND, anImport.myOnDemand);
    flags = BitUtil.set(flags, HAS_ALIAS, hasAlias);
    out.writeByte(flags);
    if (hasAlias) {
      out.writeInt(anImport.myAlias);
    }
  }

  private static long readImport(UnitInputStream in) throws IOException {
    int fullname = in.names.readQualifiedName(in);
    int flags = in.readByte();
    return mkImport(fullname,
                    BitUtil.isSet(flags, IS_STATIC), BitUtil.isSet(flags, IS_ON_DEMAND),
                    BitUtil.isSet(flags, HAS_ALIAS) ? in.readInt() : 0);
  }

}
