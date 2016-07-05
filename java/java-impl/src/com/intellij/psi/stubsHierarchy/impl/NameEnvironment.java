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

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TLongIntHashMap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

class NameEnvironment extends UserDataHolderBase {
  public static final int OBJECT_NAME = IndexTree.hashIdentifier("Object");
  public static final int NO_NAME = 0;
  @QNameId public final int java_lang;
  public final QualifiedName java_lang_Enum;
  public final QualifiedName java_lang_annotation_Annotation;

  private final TLongIntHashMap myConcatenations = new TLongIntHashMap();

  NameEnvironment() {
    java_lang = fromString("java.lang");
    java_lang_Enum = new QualifiedName.Interned(fromString(CommonClassNames.JAVA_LANG_ENUM));
    java_lang_annotation_Annotation = new QualifiedName.Interned(fromString(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION));
  }

  @QNameId
  int fromString(String s) {
    return internQualifiedName(IndexTree.hashQualifiedName(s));
  }

  @QNameId int findExistingName(@QNameId int stemId, @ShortName int suffix) {
    int existing = myConcatenations.get(pack(stemId, suffix));
    return existing > 0 ? existing : -1;
  }

  @QNameId int internQualifiedName(@ShortName int[] qname) {
    int id = 0;
    for (int shortName : qname) {
      id = qualifiedName(id, shortName);
    }
    return id;
  }

  /**
   * @see SerializedUnit#writeQualifiedName(DataOutput, int[])
   */
  @QNameId int readQualifiedName(DataInput in) throws IOException {
    int id = 0;
    int len = DataInputOutputUtil.readINT(in);
    for (int i = 0; i < len; i++) {
      id = qualifiedName(id, in.readInt());
    }
    return id;
  }

  int memberQualifiedName(@QNameId int ownerName, @ShortName int name) {
    return name == NO_NAME || ownerName < 0 ? -1 : qualifiedName(ownerName, name);
  }

  @QNameId int qualifiedName(@QNameId int prefix, @ShortName int shortName) {
    int existing = findExistingName(prefix, shortName);
    return existing >= 0 ? existing : addName(prefix, shortName);
  }

  private int addName(@QNameId int stemId, @ShortName int suffix) {
    int newId = myConcatenations.size();
    myConcatenations.put(pack(stemId, suffix), newId);
    return newId;
  }

  private static long pack(@QNameId int stemId, @ShortName int suffix) {
    return ((long)suffix << 32) + stemId;
  }
}
