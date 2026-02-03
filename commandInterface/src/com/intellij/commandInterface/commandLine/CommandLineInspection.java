// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.commandInterface.CommandInterfaceBundle;
import com.intellij.commandInterface.command.Command;
import com.intellij.commandInterface.commandLine.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Inspection that ensures command line options and arguments are correct.
 * It works only if list of available commands is provided to command file
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                                 final boolean isOnTheFly,
                                                 final @NotNull LocalInspectionToolSession session) {
    return new MyVisitor(holder);
  }

  private static final class MyVisitor extends CommandLineVisitor {
    private final @NotNull ProblemsHolder myHolder;

    private MyVisitor(final @NotNull ProblemsHolder holder) {
      myHolder = holder;
    }

    private static @Nullable CommandLineFile getFile(final @NotNull PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, CommandLineFile.class);
    }

    private static @Nullable ValidationResult getValidationResult(final @NotNull PsiElement element) {
      final CommandLineFile file = getFile(element);
      if (file == null) {
        return null;
      }
      return file.getValidationResult();
    }

    @Override
    public void visitCommand(final @NotNull CommandLineCommand o) {
      super.visitCommand(o);
      final CommandLineFile file = getFile(o);
      if (file == null) {
        return;
      }
      final List<Command> commands = file.getCommands();
      if (commands == null) {
        return;
      }
      for (final Command command : commands) {
        if (o.getText().equals(command.getName())) {
          return;
        }
      }
      myHolder.registerProblem(o, CommandInterfaceBundle.message("commandLine.inspection.badCommand"));
    }

    @Override
    public void visitOption(final @NotNull CommandLineOption o) {
      super.visitOption(o);
      final ValidationResult validationResult = getValidationResult(o);
      if (validationResult != null && validationResult.isBadValue(o)) {
        myHolder.registerProblem(o, CommandInterfaceBundle.message("commandLine.inspection.badOption"));
      }
    }


    @Override
    public void visitArgument(final @NotNull CommandLineArgument o) {
      super.visitArgument(o);
      final ValidationResult validationResult = getValidationResult(o);
      if (validationResult != null) {
        if (validationResult.isBadValue(o)) {
          myHolder.registerProblem(o, CommandInterfaceBundle.message("commandLine.inspection.badArgument"));
        }
        else if (validationResult.isExcessArgument(o)) {
          myHolder.registerProblem(o, CommandInterfaceBundle.message("commandLine.inspection.excessArgument"));
        }
      }
    }
  }
}
