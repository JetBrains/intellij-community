// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.compiler.CompilableFileTypesProvider;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public final class JavaCompilableFileTypesProvider implements CompilableFileTypesProvider {

  @Override
  public @NotNull Set<FileType> getCompilableFileTypes() {
    return Collections.singleton(JavaFileType.INSTANCE);
  }
}
