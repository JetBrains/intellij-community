// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Command line element type to be used in parser
 *
 * @author Ilya.Kazakevich
 */
final class CommandLineElementType extends IElementType {
  CommandLineElementType(@NotNull @NonNls final String debugName) {
    super(debugName, CommandLineLanguage.INSTANCE);
  }
}
