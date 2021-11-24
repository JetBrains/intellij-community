// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.javac.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.javac.ast.api.JavacTypeCast;

import java.util.Collection;

public final class JavaBackwardReferenceRegistrar implements JavacFileReferencesRegistrar {
  private volatile JavaBackwardReferenceIndexWriter myWriter;

  @Override
  public void initialize() {
    myWriter = JavaBackwardReferenceIndexWriter.getInstance();
  }

  @Override
  public boolean isEnabled() {
    return JavaBackwardReferenceIndexWriter.isEnabled() && JavaBackwardReferenceIndexWriter.getInstance() != null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void registerFile(CompileContext context, String filePath,
                           Iterable<Object2IntMap.Entry<? extends JavacRef>> refs,
                           Collection<? extends JavacDef> defs,
                           Collection<? extends JavacTypeCast> casts,
                           Collection<? extends JavacRef> implicitToString) {
    BackwardReferenceIndexUtil.registerFile(filePath, refs, defs, casts, implicitToString, myWriter);
  }
}
