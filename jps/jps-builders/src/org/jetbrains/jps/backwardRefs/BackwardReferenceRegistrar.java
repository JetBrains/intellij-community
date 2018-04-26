// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import gnu.trove.TObjectIntHashMap;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.javac.ast.api.JavacTypeCast;

import java.util.Collection;

public class BackwardReferenceRegistrar implements JavacFileReferencesRegistrar {
  private volatile BackwardReferenceIndexWriter myWriter;

  @Override
  public void initialize() {
    myWriter = BackwardReferenceIndexWriter.getInstance();
  }

  @Override
  public boolean isEnabled() {
    return BackwardReferenceIndexWriter.isEnabled() && BackwardReferenceIndexWriter.getInstance() != null;
  }

  @Override
  public boolean onlyImports() {
    return false;
  }

  @Override
  public void registerFile(String filePath,
                           TObjectIntHashMap<JavacRef> refs,
                           Collection<JavacDef> defs,
                           Collection<JavacTypeCast> casts,
                           Collection<JavacRef> implicitToString) {
    BackwardReferenceIndexUtil.registerFile(filePath, refs, defs, casts, implicitToString, myWriter);
  }
}
