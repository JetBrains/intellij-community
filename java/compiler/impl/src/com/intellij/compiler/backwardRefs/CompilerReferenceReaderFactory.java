// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.index.CompilerIndexDescriptor;

public interface CompilerReferenceReaderFactory<Reader extends CompilerReferenceReader<?>> {
  /**
   *  Must be consistent with {@link Reader} type, i.e. must specify 
   *  the same {@link org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex} that {@link Reader} has access to.
   */
  @NotNull
  CompilerIndexDescriptor<?> getIndexDescriptor();
 
  Reader create(Project project);
}
