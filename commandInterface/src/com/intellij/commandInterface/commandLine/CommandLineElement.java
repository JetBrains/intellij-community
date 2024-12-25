// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.command.Command;
import com.intellij.commandInterface.commandLine.psi.CommandLineFile;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parent of all command line elements (enables reference injection, see {@link #getReferences()})
 *
 * @author Ilya.Kazakevich
 */
public class CommandLineElement extends ASTWrapperPsiElement implements CommandLinePart {
  protected CommandLineElement(final @NotNull ASTNode node) {
    super(node);
  }


  @Override
  public final @Nullable CommandLineFile getCommandLineFile() {
    return PsiTreeUtil.getParentOfType(this, CommandLineFile.class);
  }

  @Override
  public final @Nullable Command findRealCommand() {
    final CommandLineFile commandLineFile = getCommandLineFile();
    if (commandLineFile != null) {
      return commandLineFile.findRealCommand();
    }
    return null;
  }

  @Override
  public final PsiReference @NotNull [] getReferences() {
    // We need it to enable reference injection
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }
}
