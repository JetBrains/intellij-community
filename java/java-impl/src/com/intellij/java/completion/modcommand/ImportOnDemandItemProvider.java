// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.annotations.NotNullByDefault;

/**
 * Completes the {@code *} of an import on demand in an import reference, like {@code import java.util.*}.
 */
@NotNullByDefault
final class ImportOnDemandItemProvider extends JavaModCompletionItemProvider implements DumbAware {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (context.getPosition().getParent() instanceof PsiJavaCodeReferenceElement ref &&
        ref.getParent() instanceof PsiImportStatementBase) {
      sink.accept(new CommonCompletionItem("*"));
    }
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
