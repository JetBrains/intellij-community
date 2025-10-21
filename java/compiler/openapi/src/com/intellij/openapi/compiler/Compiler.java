// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.compiler;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface for a custom compiler which participates in the build process and should be executed inside the IDE process.
 * @deprecated since IDEA 15 compilers need to be executed inside a separate (external) build process, see
 * <a href="https://plugins.jetbrains.com/docs/intellij/external-builder-api.html">this guide</a>
 * for details. If you need to run some code inside the IDE process before the external build process starts or after it finishes, use
 * {@link CompileTask} extension point instead. Implementations of this class aren't used by the IDE (except those which implement
 * {@link Validator} or {@link SourceInstrumentingCompiler}).
 */
@Deprecated
public interface Compiler {
  ProjectExtensionPointName<Compiler> EP_NAME = new ProjectExtensionPointName<>("com.intellij.compiler");

  /**
   * Returns the description of the compiler. All registered compilers should have unique description.
   *
   * @return the description string.
   */
  @NotNull
  @Nls
  String getDescription();

  /**
   * @deprecated isn't called by the compilation engine anymore; if the configuration of a specific compiled is invalid, it should report an 
   * error via {@link CompileContext#addMessage}.
   */
  @Deprecated(forRemoval = true)
  default boolean validateConfiguration(CompileScope scope) {
    return true;
  }
}
