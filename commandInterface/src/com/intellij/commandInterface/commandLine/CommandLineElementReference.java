// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.command.Command;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parent of all  references injected to command line elements
 *
 * @author Ilya.Kazakevich
 */
abstract class CommandLineElementReference<T extends PsiElement> extends PsiReferenceBase<T> {
  protected CommandLineElementReference(final @NotNull T element) {
    super(element);
  }

  /**
   * @return command line file this element sits in (if any)
   */
  protected final @Nullable CommandLineFile getCommandLineFile() {
    return PsiTreeUtil.getParentOfType(getElement(), CommandLineFile.class);
  }

  /**
   * @return command line validation result (if any)
   */
  protected final @Nullable ValidationResult getValidationResult() {
    final CommandLineFile file = getCommandLineFile();
    if (file == null) {
      return null;
    }
    return file.getValidationResult();
  }

  /**
   * @return real command used in parent file (if any)
   */
  protected final @Nullable Command getCommand() {
    final CommandLineFile file = getCommandLineFile();
    if (file == null) {
      return null;
    }
    return file.findRealCommand();
  }
}
