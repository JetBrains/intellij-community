// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public enum SkipOverwriteChoice {
  OVERWRITE("copy.overwrite.button"),
  SKIP("copy.skip.button"),
  OVERWRITE_ALL("copy.overwrite.for.all.button"),
  SKIP_ALL("copy.skip.for.all.button");

  private final String myKey;

  SkipOverwriteChoice(String key) {
    myKey = key;
  }

  private static String[] getOptions(boolean full) {
    if (full) {
      return Arrays.stream(values()).map(choice -> getMessage(choice)).toArray(String[]::new);
    }
    return new String[]{
      getMessage(OVERWRITE),
      getMessage(SKIP)
    };
  }

  private static @NotNull @Nls String getMessage(SkipOverwriteChoice choice) {
    return RefactoringBundle.message(choice.myKey);
  }

  /**
   * Shows dialog with overwrite/skip choices
   */
  public static @NotNull SkipOverwriteChoice askUser(@NotNull PsiDirectory targetDirectory,
                                            @NotNull @NlsSafe String fileName, 
                                            @NlsContexts.Command String title,
                                            boolean includeAllCases) {
    String message =
      RefactoringBundle.message("dialog.message.file.already.exists.in.directory", fileName, targetDirectory.getVirtualFile().getPresentableUrl());
    int selection = Messages.showDialog(targetDirectory.getProject(), message, title,
                                        getOptions(includeAllCases), 0, Messages.getQuestionIcon());
    if (selection < 0) return SKIP;
    return values()[selection];
  }
}
