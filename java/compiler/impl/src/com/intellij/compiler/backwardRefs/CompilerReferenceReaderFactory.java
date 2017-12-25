// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.project.Project;

/**
 * Created by sugakandrey.
 */
public interface CompilerReferenceReaderFactory<Reader extends CompilerReferenceReader<?>> {
  Reader create(Project project);
}
