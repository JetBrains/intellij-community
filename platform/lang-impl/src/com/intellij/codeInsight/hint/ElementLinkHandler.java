// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ide.actions.QualifiedNameProviderUtil.qualifiedNameToElement;

/**
 * Handles tooltip links in format {@code #element/qualified.name}.
 * On a click opens specified element in an editor and positions caret to the corresponding offset.
 */
@ApiStatus.Internal
public final class ElementLinkHandler extends TooltipLinkHandler {
  @Override
  public boolean handleLink(@NotNull String name, @NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) {
      return false;
    }
    // Resolving the qualified name may hit indexes (a slow operation), so it must not run on the EDT.
    ReadAction.nonBlocking(() -> qualifiedNameToElement(name, project))
      .expireWith(project)
      .finishOnUiThread(ModalityState.defaultModalityState(), element -> {
        if (element instanceof Navigatable navigatable && navigatable.canNavigate()) {
          navigatable.navigate(true);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
    return true;
  }
}
