// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated V2 implementation doesn't allow customization of the tool window in plugins.
 */
@Deprecated(forRemoval = true)
public interface DocToolWindowManager {

  DocToolWindowLanguageManager LANGUAGE_MANAGER = new DocToolWindowLanguageManager();

  @NotNull
  ToolWindow createToolWindow(@NotNull PsiElement element, PsiElement originalElement, @NotNull DocumentationManager documentationManager);

  void setToolWindowDefaultState(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager);

  void prepareForShowDocumentation(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager);

  void installToolWindowActions(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager);

  @Nullable
  Content getDocumentationContent(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager);

  @Nullable
  DocumentationComponent getDocumentationComponent(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager);

  void updateToolWindowDocumentationTabName(@NotNull ToolWindow toolWindow,
                                            @NotNull PsiElement element,
                                            @NotNull DocumentationManager documentationManager);

  void disposeToolWindow(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager);

  boolean isAutoUpdateAvailable();

  final class DocToolWindowLanguageManager extends LanguageExtension<DocToolWindowManager> {
    public static final ExtensionPointName<KeyedLazyInstance<DocToolWindowManager>> EP_NAME =
      new ExtensionPointName<>("com.intellij.lang.documentationToolWindowManager");

    DocToolWindowLanguageManager() {
      super(EP_NAME);
    }
  }
}
