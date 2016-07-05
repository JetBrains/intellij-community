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
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;

public class Translator {
  private static final Key<Import[]> DEFAULT_JAVA_IMPORTS_KEY = Key.create("java_imports");
  private static final Key<Import[]> DEFAULT_GROOVY_IMPORTS_KEY = Key.create("groovy_imports");

  private static Import[] getDefaultJavaImports(NameEnvironment nameEnvironment) {
    Import[] imports = nameEnvironment.getUserData(DEFAULT_JAVA_IMPORTS_KEY);
    if (imports == null) {
      imports = createDefaultJavaImports(nameEnvironment);
      nameEnvironment.putUserData(DEFAULT_JAVA_IMPORTS_KEY, imports);
    }
    return imports;
  }

  private static Import[] createDefaultJavaImports(NameEnvironment nameEnvironment) {
    return new Import[]{
      importPackage(nameEnvironment, "java.lang")
    };
  }

  private static Import[] getDefaultGroovyImports(NameEnvironment nameEnvironment) {
    Import[] imports = nameEnvironment.getUserData(DEFAULT_GROOVY_IMPORTS_KEY);
    if (imports == null) {
      imports = createDefaultGroovyImports(nameEnvironment);
      nameEnvironment.putUserData(DEFAULT_GROOVY_IMPORTS_KEY, imports);
    }
    return imports;
  }

  private static Import[] createDefaultGroovyImports(NameEnvironment nameEnvironment) {
    return new Import[] {
      importPackage(nameEnvironment, "java.lang"),
      importPackage(nameEnvironment, "java.util"),
      importPackage(nameEnvironment, "java.io"),
      importPackage(nameEnvironment, "java.net"),
      importPackage(nameEnvironment, "groovy.lang"),
      importPackage(nameEnvironment, "groovy.util"),
      new Import(nameEnvironment.fromString("java.math"), IndexTree.hashIdentifier("BigInteger"), false),
      new Import(nameEnvironment.fromString("java.math"), IndexTree.hashIdentifier("BigDecimal"), false),
    };
  }

  private static Import importPackage(NameEnvironment nameEnvironment, String qname) {
    return new Import(nameEnvironment.fromString(qname), 0, false);
  }

  public static Import[] getDefaultImports(byte type, NameEnvironment nameEnvironment) {
    if (type == IndexTree.JAVA)
      return getDefaultJavaImports(nameEnvironment);
    if (type == IndexTree.GROOVY)
      return getDefaultGroovyImports(nameEnvironment);
    return Imports.EMPTY_ARRAY;
  }

}
