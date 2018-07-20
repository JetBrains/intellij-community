// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac.ast.api;

import gnu.trove.TObjectIntHashMap;

import java.util.Collection;

public interface JavacFileReferencesRegistrar {
  void initialize();

  boolean isEnabled();

  boolean onlyImports();

  void registerFile(String filePath,
                    TObjectIntHashMap<JavacRef> refs,
                    Collection<JavacDef> defs,
                    Collection<JavacTypeCast> casts,
                    Collection<JavacRef> implicitToString);
}
