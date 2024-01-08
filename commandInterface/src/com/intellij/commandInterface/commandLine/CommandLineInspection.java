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

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new MyVisitor(holder);
  }

  private static final class MyVisitor extends CommandLineVisitor {
    @NotNull
    private final ProblemsHolder myHolder;

    private MyVisitor(@NotNull final ProblemsHolder holder) {
      myHolder = holder;
    }

    @Nullable
    private static CommandLineFile getFile(@NotNull final PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, CommandLineFile.class);
    }

    @Nullable
    private static ValidationResult getValidationResult(@NotNull final PsiElement element) {
      final CommandLineFile file = getFile(element);
      if (file == null) {
        return null;
      }
      return file.getValidationResult();
    }

    @Override
    public void visitCommand(@NotNull final CommandLineCommand o) {
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
    public void visitOption(@NotNull final CommandLineOption o) {
      super.visitOption(o);
      final ValidationResult validationResult = getValidationResult(o);
      if (validationResult != null && validationResult.isBadValue(o)) {
        myHolder.registerProblem(o, CommandInterfaceBundle.message("commandLine.inspection.badOption"));
      }
    }


    @Override
    public void visitArgument(@NotNull final CommandLineArgument o) {
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
