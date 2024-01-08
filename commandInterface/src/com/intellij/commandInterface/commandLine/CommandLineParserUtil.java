// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.CommandInterfaceBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Tool to be used in parser generation to handle "=" for long option
 *
 * @author Ilya.Kazakevich
 */
final class CommandLineParserUtil extends GeneratedParserUtilBase {
  private CommandLineParserUtil() {
  }

  static void bound_argument(@NotNull final PsiBuilder b, final int i) {
    final IElementType tokenType = b.getTokenType();
    final IElementType leftElement = b.rawLookup(-1);
    final IElementType rightElement = b.rawLookup(1);
    if (leftElement == null || TokenType.WHITE_SPACE.equals(leftElement)) {
      return;
    }

    /*
     * At '=' position: if no whitespace to left and right, we move to argument.
     * And we report error if whitespace to the left.
     */
    if (tokenType == CommandLineElementTypes.EQ) {
      if (leftElement.equals(CommandLineElementTypes.LONG_OPTION_NAME_TOKEN)) {
        if (rightElement == null || TokenType.WHITE_SPACE.equals(rightElement)) {
          b.error(CommandInterfaceBundle.message("command.line.parser.error.message"));
        }
        b.advanceLexer();
      }
    }
  }
}
