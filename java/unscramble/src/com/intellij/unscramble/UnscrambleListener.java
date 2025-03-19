// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble;

import com.intellij.openapi.application.ClipboardAnalyzeListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
class UnscrambleListener extends ClipboardAnalyzeListener {

  private static final Pattern STACKTRACE_LINE =
    Pattern.compile(
      "[\t]*at [[_a-zA-Z0-9/]+\\.]+[_a-zA-Z$0-9/]+\\.[a-zA-Z0-9_/]+\\([A-Za-z0-9_/]+\\.(java|kt):[\\d]+\\)+[ [~]*\\[[a-zA-Z0-9\\.\\:/]\\]]*");

  @Override
  public void applicationActivated(@NotNull IdeFrame ideFrame) {
    if (!Registry.is("analyze.exceptions.on.the.fly")) return;

    super.applicationActivated(ideFrame);
  }

  @Override
  public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
    if (!Registry.is("analyze.exceptions.on.the.fly")) return;
    super.applicationDeactivated(ideFrame);
  }

  @Override
  public boolean canHandle(@NotNull String value) {
    return UnscrambleUtils.isStackTrace(value);
  }

  @Override
  protected void handle(@NotNull Project project, @NotNull String value) {
    final UnscrambleDialog dialog = new UnscrambleDialog(project);
    dialog.createNormalizeTextAction().actionPerformed(null);
    if (!DumbService.isDumb(project)) {
      dialog.doOKAction();
    }
  }
}
