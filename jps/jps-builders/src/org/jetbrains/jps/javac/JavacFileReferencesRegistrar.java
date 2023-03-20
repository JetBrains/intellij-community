// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.javac.ast.api.JavacTypeCast;

import java.util.Collection;
import java.util.Map;

public interface JavacFileReferencesRegistrar {
  void initialize();

  boolean isEnabled();

  void registerFile(CompileContext context,
                    String filePath,
                    Iterable<Map.Entry<? extends JavacRef, Integer>> refs,
                    Collection<? extends JavacDef> defs,
                    Collection<? extends JavacTypeCast> casts,
                    Collection<? extends JavacRef> implicitToString);
}
