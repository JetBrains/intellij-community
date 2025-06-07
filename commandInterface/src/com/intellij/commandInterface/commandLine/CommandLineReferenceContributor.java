// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.commandLine.psi.CommandLineArgument;
import com.intellij.commandInterface.commandLine.psi.CommandLineCommand;
import com.intellij.commandInterface.commandLine.psi.CommandLineOption;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Injects references to command-line parts
 *
 * @author Ilya.Kazakevich
 */
final class CommandLineReferenceContributor extends PsiReferenceContributor {
  private static final ReferenceProvider REFERENCE_PROVIDER = new ReferenceProvider();

  @Override
  public void registerReferenceProviders(final @NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(CommandLineElement.class), REFERENCE_PROVIDER);
  }

  private static final class ReferenceProvider extends PsiReferenceProvider {
    @Override
    public PsiReference @NotNull [] getReferencesByElement(final @NotNull PsiElement element, final @NotNull ProcessingContext context) {
      if (element instanceof CommandLineCommand) {
        return new PsiReference[]{new CommandLineCommandReference((CommandLineCommand)element)};
      }
      if (element instanceof CommandLineArgument) {
        return new PsiReference[]{new CommandLineArgumentReference((CommandLineArgument)element)};
      }
      if (element instanceof CommandLineOption) {
        return new PsiReference[]{new CommandLineOptionReference((CommandLineOption)element)};
      }
      return PsiReference.EMPTY_ARRAY;
    }
  }
}

