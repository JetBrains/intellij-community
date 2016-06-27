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
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.util.BitUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static com.intellij.psi.impl.java.stubs.index.JavaUnitDescriptor.ImportFlags.*;

public class JavaUnitDescriptor implements DataExternalizer<IndexTree.Unit> {
  public static final JavaUnitDescriptor INSTANCE = new JavaUnitDescriptor();

  private static void writeIntArray(DataOutput out, int[] array) throws IOException {
    DataInputOutputUtil.writeINT(out, array.length);
    for (int i : array) {
      out.writeInt(i);
    }
  }
  private static int[] readIntArray(DataInput in) throws IOException {
    int length = DataInputOutputUtil.readINT(in);
    int[] result = new int[length];
    for (int i = 0; i < length; i++) {
      result[i] = in.readInt();
    }
    return result;
  }

  @Override
  public void save(@NotNull DataOutput out, IndexTree.Unit value) throws IOException {
    writeIntArray(out, value.myPackageName);
    out.writeByte(value.myUnitType);
    if (value.myUnitType != IndexTree.BYTECODE) {
      DataInputOutputUtil.writeINT(out, value.imports.length);
      for (IndexTree.Import anImport : value.imports) {
        writeImport(out, anImport);
      }
    }
    // class Declaration
    DataInputOutputUtil.writeINT(out, value.myDecls.length);
    for (IndexTree.ClassDecl def : value.myDecls) {
      saveClassDecl(out, def);
    }
  }

  private void saveClassDecl(@NotNull DataOutput out, IndexTree.ClassDecl value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.myStubId);
    DataInputOutputUtil.writeINT(out, value.myMods);
    out.writeInt(value.myName);
    DataInputOutputUtil.writeINT(out, value.mySupers.length);
    for (int[] aSuper : value.mySupers) {
      writeIntArray(out, aSuper);
    }
    DataInputOutputUtil.writeINT(out, value.myDecls.length);
    for (IndexTree.Decl def : value.myDecls) {
      saveDecl(out, def);
    }
  }

  private void saveDecl(@NotNull DataOutput out, IndexTree.Decl value) throws IOException {
    if (value instanceof IndexTree.ClassDecl) {
      out.writeBoolean(true);
      saveClassDecl(out, (IndexTree.ClassDecl)value);
    } else if (value instanceof IndexTree.MemberDecl) {
      out.writeBoolean(false);
      IndexTree.MemberDecl memberDecl = (IndexTree.MemberDecl)value;
      DataInputOutputUtil.writeINT(out, memberDecl.myDecls.length);
      for (IndexTree.Decl def : memberDecl.myDecls) {
        saveDecl(out, def);
      }
    }
  }

  @Override
  public IndexTree.Unit read(@NotNull DataInput in) throws IOException {
    int[] pid = readIntArray(in);
    byte type = in.readByte();
    IndexTree.Import[] imports = IndexTree.Import.EMPTY_ARRAY;
    if (type != IndexTree.BYTECODE) {
      imports = new IndexTree.Import[DataInputOutputUtil.readINT(in)];
      for (int i = 0; i < imports.length; i++) {
        imports[i] = readImport(in);
      }
    }
    IndexTree.ClassDecl[] classes = new IndexTree.ClassDecl[DataInputOutputUtil.readINT(in)];
    for (int i = 0; i < classes.length; i++) {
      classes[i] = readClassDecl(in);
    }
    return new IndexTree.Unit(pid, type, imports, classes);
  }

  private IndexTree.ClassDecl readClassDecl(DataInput in) throws IOException {
    int stubId = DataInputOutputUtil.readINT(in);
    int mods = DataInputOutputUtil.readINT(in);
    int name = in.readInt();
    int[][] supers = new int[DataInputOutputUtil.readINT(in)][];
    for (int i = 0; i < supers.length; i++) {
      supers[i] = readIntArray(in);
    }
    IndexTree.Decl[] decls = new IndexTree.Decl[DataInputOutputUtil.readINT(in)];
    for (int i = 0; i < decls.length; i++) {
      decls[i] = readDecl(in);
    }
    return new IndexTree.ClassDecl(stubId, mods, name, supers, decls);
  }

  private IndexTree.Decl readDecl(DataInput in) throws IOException {
    boolean isClassDecl = in.readBoolean();
    if (isClassDecl) {
     return readClassDecl(in);
    }
    else {
      IndexTree.Decl[] decls = new IndexTree.Decl[DataInputOutputUtil.readINT(in)];
      for (int i = 0; i < decls.length; i++) {
        decls[i] = readDecl(in);
      }
      return new IndexTree.MemberDecl(decls);
    }
  }

  interface ImportFlags {
    int IS_STATIC = 1;
    int IS_ON_DEMAND = 2;
    int HAS_ALIAS = 4;
  }

  private static void writeImport(@NotNull DataOutput out, IndexTree.Import anImport) throws IOException {
    writeIntArray(out, anImport.myFullname);
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

  @NotNull
  private static IndexTree.Import readImport(@NotNull DataInput in) throws IOException {
    int[] fullname = readIntArray(in);
    int flags = in.readByte();
    return new IndexTree.Import(fullname,
                                BitUtil.isSet(flags, IS_STATIC), BitUtil.isSet(flags, IS_ON_DEMAND),
                                BitUtil.isSet(flags, HAS_ALIAS) ? in.readInt() : 0);
  }

}
