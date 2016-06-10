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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.stubs.*;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Translator {
  private static final Key<long[]> DEFAULT_JAVA_IMPORTS_KEY = Key.create("java_imports");
  private static final Key<long[]> DEFAULT_GROOVY_IMPORTS_KEY = Key.create("groovy_imports");

  private static long[] getDefaultJavaImports(NameEnvironment nameEnvironment) {
    long[] imports = nameEnvironment.getUserData(DEFAULT_JAVA_IMPORTS_KEY);
    if (imports == null) {
      imports = createDefaultJavaImports(nameEnvironment);
      nameEnvironment.putUserData(DEFAULT_JAVA_IMPORTS_KEY, imports);
    }
    return imports;
  }

  private static long[] createDefaultJavaImports(NameEnvironment nameEnvironment) {
    return new long[]{
      Import.mkImport(nameEnvironment.fromString("java.lang", true), false, true, 0)
    };
  }

  private static long[] getDefaultGroovyImports(NameEnvironment nameEnvironment) {
    long[] imports = nameEnvironment.getUserData(DEFAULT_GROOVY_IMPORTS_KEY);
    if (imports == null) {
      imports = createDefaultGroovyImports(nameEnvironment);
      nameEnvironment.putUserData(DEFAULT_GROOVY_IMPORTS_KEY, imports);
    }
    return imports;
  }

  private static long[] createDefaultGroovyImports(NameEnvironment nameEnvironment) {
    return new long[] {
      Import.mkImport(nameEnvironment.fromString("java.lang", true), false, true, 0),
      Import.mkImport(nameEnvironment.fromString("java.util", true), false, true, 0),
      Import.mkImport(nameEnvironment.fromString("java.io", true), false, true, 0),
      Import.mkImport(nameEnvironment.fromString("java.net", true), false, true, 0),
      Import.mkImport(nameEnvironment.fromString("groovy.lang", true), false, true, 0),
      Import.mkImport(nameEnvironment.fromString("groovy.util", true), false, true, 0),
      Import.mkImport(nameEnvironment.fromString("java.math.BigInteger", true), false, true, 0),
      Import.mkImport(nameEnvironment.fromString("java.math.BigDecimal", true), false, true, 0),
    };
  }

  public static long[] getDefaultImports(byte type, NameEnvironment nameEnvironment) {
    if (type == IndexTree.JAVA)
      return getDefaultJavaImports(nameEnvironment);
    if (type == IndexTree.GROOVY)
      return getDefaultGroovyImports(nameEnvironment);
    return Import.EMPTY_ARRAY;
  }

  @NotNull
  public static Unit internNames(NameEnvironment nameEnvironment, IndexTree.Unit unit, int fileId, QualifiedName pkg) {
    ClassDeclaration[] classes = new ClassDeclaration[unit.myDecls.length];
    for (int i = 0; i < unit.myDecls.length; i++) {
      classes[i] = processClassDecl(nameEnvironment, fileId, unit.myDecls[i]);
    }

    long[] imports = unit.imports.length == 0 ? Import.EMPTY_ARRAY : new long[unit.imports.length];
    for (int i = 0; i < unit.imports.length; i++) {
      imports[i] = processImport(nameEnvironment, unit.imports[i]);
    }

    return new Unit(pkg, imports, classes, unit.myUnitType);
  }

  private static ClassDeclaration processClassDecl(NameEnvironment nameEnvironment, int fileId, IndexTree.ClassDecl def) {
    String stubName = def.myName;
    int name = stubName == null ? 0 : nameEnvironment.simpleName(stubName, true);
    ArrayList<QualifiedName> superList = new ArrayList<QualifiedName>();
    for (String aSuper : def.mySupers) {
      superList.add(id(nameEnvironment, aSuper));
    }
    if (BitUtil.isSet(def.myMods, IndexTree.ENUM)) {
      superList.add(nameEnvironment.java_lang_Enum);
    }
    ArrayList<Declaration> innerDefList = new ArrayList<Declaration>();
    for (IndexTree.Decl decl : def.myDecls) {
      Declaration hTree = processMember(nameEnvironment, fileId, decl);
      if (hTree != null) {
        innerDefList.add(hTree);
      }
    }

    ClassAnchor anchor = new ClassAnchor(fileId, def.myStubId);
    QualifiedName[] supers = superList.isEmpty() ? QualifiedName.EMPTY_ARRAY : superList.toArray(new QualifiedName[superList.size()]);
    Declaration[] innerDefs = innerDefList.isEmpty() ? Declaration.EMPTY_ARRAY : innerDefList.toArray(new Declaration[innerDefList.size()]);
    return new ClassDeclaration(anchor, def.myMods, name, supers, innerDefs);
  }

  private static Declaration processMember(NameEnvironment nameEnvironment, int fileId, IndexTree.Decl decl) {
    if (decl instanceof IndexTree.ClassDecl) {
      return processClassDecl(nameEnvironment, fileId, (IndexTree.ClassDecl)decl);
    }
    ArrayList<Declaration> defList = new ArrayList<Declaration>();
    for (IndexTree.Decl def : ((IndexTree.MemberDecl)decl).myDecls) {
      Declaration hTree = processMember(nameEnvironment, fileId, def);
      if (hTree != null) {
        defList.add(hTree);
      }
    }
    Declaration[] defs = defList.toArray(new Declaration[defList.size()]);
    return new MemberDeclaration(defs);
  }

  private static long processImport(NameEnvironment nameEnvironment, IndexTree.Import anImport) {
    QualifiedName fullname = nameEnvironment.fromString(anImport.myFullname, true);
    int aliasName = anImport.myAlias == null ? 0 : nameEnvironment.simpleName(anImport.myAlias, true);
    return Import.mkImport(fullname, anImport.myStaticImport, anImport.myOnDemand, aliasName);
  }

  private static QualifiedName id(NameEnvironment nameEnvironment, String s) {
    s = PsiNameHelper.getQualifiedClassName(s, true);
    List<String> ids = StringUtil.split(s, ".");
    int[] comps = new int[ids.size()];
    int i = 0;
    for (String id : ids) {
      int name = nameEnvironment.simpleName(id, true);
      comps[i] = name;
      i++;
    }
    return nameEnvironment.concat(comps, true);
  }
}
