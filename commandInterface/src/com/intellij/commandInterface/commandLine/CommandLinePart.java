// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.psi.PsiElement;
import com.intellij.commandInterface.command.Command;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import org.jetbrains.annotations.Nullable;

/**
 * Any part of commandline from file till any element
 *
 * @author Ilya.Kazakevich
 */
public interface CommandLinePart extends PsiElement {
  /**
   * @return command associated with this command line (if any)
   */
  @Nullable
  Command findRealCommand();

  /**
   * @return command line file where this part sits
   */
  @SuppressWarnings("ClassReferencesSubclass") // Although referencing child is bad idea, this hierarchy is coupled tightly and considered to be solid part
  @Nullable
  CommandLineFile getCommandLineFile();
}
