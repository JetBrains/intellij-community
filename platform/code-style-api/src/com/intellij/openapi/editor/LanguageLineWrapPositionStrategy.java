// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Exposes {@link LineWrapPositionStrategy} implementations to their clients.
 * <p>
 * Using this extension, you can specify the line wrapping strategy only for those editors
 * that are bound to some PSI file with the language.
 * If you need to specify the strategy for the editor that is not bound to the PSI file,
 * please use {@link com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager#setLineWrapPositionStrategy(LineWrapPositionStrategy) SoftWrapApplianceManager#setLineWrapPositionStrategy}
 * </p>
 */
public final class LanguageLineWrapPositionStrategy extends LanguageExtension<LineWrapPositionStrategy> {

  public static final ExtensionPointName<? extends KeyedLazyInstance<LineWrapPositionStrategy>> EP_NAME =
    new ExtensionPointName<>("com.intellij.lang.lineWrapStrategy");
  public static final LanguageLineWrapPositionStrategy INSTANCE = new LanguageLineWrapPositionStrategy();

  private LanguageLineWrapPositionStrategy() {
    super(EP_NAME, new DefaultLineWrapPositionStrategy());
  }

  /**
   * Asks to get wrap position strategy to use for the document managed by the given editor.
   * The strategy is determined based on the editor's PSI file language.
   *
   * @param editor    editor that manages document which text should be processed by wrap position strategy
   * @return          line wrap position strategy to use for the lines from the document managed by the given editor
   */
  public @NotNull LineWrapPositionStrategy forEditor(@NotNull Editor editor) {
    LineWrapPositionStrategy result = getDefaultImplementation();
    Project project = editor.getProject();
    if (project != null && !project.isDisposed()) {
      try (AccessToken ignore = SlowOperations.knownIssue("IJPL-162826")) {
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile != null) {
          result = INSTANCE.forLanguage(psiFile.getLanguage());
        }
      }
    }
    return result;
  }

  @Override
  public @NotNull LineWrapPositionStrategy getDefaultImplementation() {
    return Objects.requireNonNull(super.getDefaultImplementation(), "com.intellij.openapi.editor.DefaultLineWrapPositionStrategy must be registered");
  }
}
