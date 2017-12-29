// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.index.CompilerIndexDescriptor;

public interface CompilerReferenceReaderFactory<Reader extends CompilerReferenceReader<?>> {
  /**
   *  Must be consistent with {@link Reader} type.
   */
  @NotNull
  CompilerIndexDescriptor<?> getReaderIndexDescriptor();
 
  Reader create(Project project);
}
