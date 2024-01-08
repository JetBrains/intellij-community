// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.commandInterface.command.Command;
import com.intellij.commandInterface.command.Help;
import com.intellij.commandInterface.command.Option;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.commandInterface.commandLine.psi.CommandLineArgument;
import com.intellij.commandInterface.commandLine.psi.CommandLineCommand;
import com.intellij.commandInterface.commandLine.psi.CommandLineOption;
import com.intellij.commandInterface.commandLine.psi.CommandLineVisitor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Provides quick help for arguments
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineDocumentationProvider implements DocumentationProvider {
  @Nullable
  @Override
  public @Nls String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
    final Help help = findHelp(element);
    if (help == null) {
      return null;
    }

    final String helpText = help.getHelpString();
    // For some reason we can't return empty sting (leads to "fetching doc" string)
    return (StringUtil.isEmptyOrSpaces(helpText) ? null : helpText);
  }

  @Override
  public List<String> getUrlFor(final PsiElement element, final PsiElement originalElement) {
    final Help help = findHelp(element);
    if (help == null) {
      return null;
    }
    final String externalHelpUrl = help.getExternalHelpUrl();
    if (externalHelpUrl != null) {
      return Collections.singletonList(externalHelpUrl);
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull final Editor editor,
                                                  @NotNull final PsiFile file,
                                                  @Nullable final PsiElement contextElement,
                                                  int targetOffset) {

    // First we try to find required parent for context element. Then, for element to the left of caret to support case "command<caret>"
    for (final PsiElement element : Arrays.asList(contextElement, file.findElementAt(targetOffset - 1))) {
      final CommandLineElement commandLineElement = PsiTreeUtil.getParentOfType(element, CommandLineElement.class);
      if (commandLineElement != null) {
        return commandLineElement;
      }
    }
    return null;
  }

  /**
   * Searches for help text for certain element
   *
   * @param element element to search help for
   * @return help or
   */
  @Nullable
  private static Help findHelp(@NotNull final PsiElement element) {
    if (!(element instanceof CommandLinePart commandLinePart)) {
      return null;
    }

    final Command realCommand = commandLinePart.findRealCommand();
    if (realCommand == null) {
      return null;
    }

    final CommandLineElement commandLineElement = ObjectUtils.tryCast(element, CommandLineElement.class);
    if (commandLineElement == null) {
      return null;
    }


    final MyCommandHelpObtainer helpObtainer = new MyCommandHelpObtainer();
    commandLineElement.accept(helpObtainer);

    return helpObtainer.myResultHelp;
  }

  /**
   * Fetches text from command line part as visitor
   */
  private static final class MyCommandHelpObtainer extends CommandLineVisitor {
    private Help myResultHelp;

    @Override
    public void visitArgument(@NotNull final CommandLineArgument o) {
      super.visitArgument(o);
      myResultHelp = o.findBestHelp();
    }

    @Override
    public void visitCommand(@NotNull CommandLineCommand o) {
      super.visitCommand(o);
      final Command realCommand = o.findRealCommand();
      if (realCommand != null) {
        myResultHelp = realCommand.getHelp(false); // Arguments are ok to display here;
      }
    }

    @Override
    public void visitOption(@NotNull final CommandLineOption o) {
      super.visitOption(o);
      final Option option = o.findRealOption();
      if (option == null) {
        return;
      }
      myResultHelp = option.getHelp();
    }
  }
}