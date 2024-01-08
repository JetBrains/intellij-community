// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import org.jetbrains.annotations.NotNull;

/**
 * Manipulator to support reference injection. Will fail with out of it.
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineElementManipulator extends AbstractElementManipulator<CommandLineElement> {

  @Override
  public CommandLineElement handleContentChange(@NotNull final CommandLineElement element,
                                                @NotNull final TextRange range,
                                                final String newContent) {
    return null;
  }
}
