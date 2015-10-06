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
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class JavaUnitKeyDescriptor implements KeyDescriptor<IndexTree.Unit> {
  public static JavaUnitKeyDescriptor INSTANCE = new JavaUnitKeyDescriptor();

  @Override
  public void save(@NotNull DataOutput out, IndexTree.Unit value) throws IOException {
    out.writeInt(value.myFileId);
    String pidToWrite = value.myPackageId == null ? "" : value.myPackageId;
    out.writeUTF(pidToWrite);
    out.writeByte(value.myUnitType);
    if (value.myUnitType != IndexTree.BYTECODE) {
      DataInputOutputUtil.writeINT(out, value.imports.length);
      for (IndexTree.Import anImport : value.imports) {
        out.writeUTF(anImport.myFullname);
        out.writeBoolean(anImport.myStaticImport);
        out.writeBoolean(anImport.myOnDemand);
      }
    }
    // class Declaration
    DataInputOutputUtil.writeINT(out, value.myDecls.length);
    for (IndexTree.ClassDecl def : value.myDecls) {
      saveClassDecl(out, def);
    }
  }

  private void saveClassDecl(@NotNull DataOutput out, IndexTree.ClassDecl value) throws IOException {
    out.writeInt(value.myStubId);
    DataInputOutputUtil.writeINT(out, value.myMods);
    out.writeUTF(value.myName == null ? "" : value.myName);
    DataInputOutputUtil.writeINT(out, value.mySupers.length);
    for (String aSuper : value.mySupers) {
      out.writeUTF(aSuper);
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
    int fileId = in.readInt();
    String pid = in.readUTF();
    if (pid.isEmpty()) {
      pid = null;
    }
    byte type = in.readByte();
    IndexTree.Import[] imports = IndexTree.Import.EMPTY_ARRAY;
    if (type != IndexTree.BYTECODE) {
      imports = new IndexTree.Import[DataInputOutputUtil.readINT(in)];
      for (int i = 0; i < imports.length; i++) {
        imports[i] = new IndexTree.Import(in.readUTF(), in.readBoolean(), in.readBoolean(), null);
      }
    }
    IndexTree.ClassDecl[] classes = new IndexTree.ClassDecl[DataInputOutputUtil.readINT(in)];
    for (int i = 0; i < classes.length; i++) {
      classes[i] = readClassDecl(in);
    }
    return new IndexTree.Unit(fileId, pid, type, imports, classes);
  }

  private IndexTree.ClassDecl readClassDecl(DataInput in) throws IOException {
    int stubId = in.readInt();
    int mods = DataInputOutputUtil.readINT(in);
    String name = in.readUTF();
    if (name.isEmpty()) {
      name = null;
    }
    String[] supers = new String[DataInputOutputUtil.readINT(in)];
    for (int i = 0; i < supers.length; i++) {
      supers[i] = in.readUTF();
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

  @Override
  public int getHashCode(IndexTree.Unit value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(IndexTree.Unit val1, IndexTree.Unit val2) {
    return val1.equals(val2);
  }
}
