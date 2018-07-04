// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import gnu.trove.TObjectIntHashMap;
import org.jetbrains.jps.javac.ast.api.JavacTypeCast;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import java.util.Collection;

public class JavaBackwardReferenceRegistrar implements JavacFileReferencesRegistrar {
  private volatile JavaBackwardReferenceIndexWriter myWriter;

  @Override
  public void initialize() {
    myWriter = JavaBackwardReferenceIndexWriter.getInstance();
  }

  @Override
  public boolean isEnabled() {
    return JavaBackwardReferenceIndexWriter.isEnabled() && JavaBackwardReferenceIndexWriter.getInstance() != null;
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
