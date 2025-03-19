// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated V2 implementation doesn't allow customization of the tool window in plugins.
 */
@Deprecated(forRemoval = true)
public abstract class AbstractDocToolWindowManager implements DocToolWindowManager {

  @Override
  public void setToolWindowDefaultState(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager) {
    documentationManager.setToolwindowDefaultState(toolWindow);
  }

  @Override
  public void prepareForShowDocumentation(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager) {}

  @Override
  public void installToolWindowActions(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager) {
    DocumentationComponent component = getDocumentationComponent(toolWindow, documentationManager);
    if (component == null) return;
    documentationManager.installComponentActions(toolWindow, component);
  }

  @Override
  public boolean isAutoUpdateAvailable() {
    return true;
  }

  @Override
  public @Nullable Content getDocumentationContent(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager) {
    return toolWindow.getContentManager().getSelectedContent();
  }

  @Override
  public @Nullable DocumentationComponent getDocumentationComponent(@NotNull ToolWindow toolWindow,
                                                                    @NotNull DocumentationManager documentationManager) {
    Content content = getDocumentationContent(toolWindow, documentationManager);
    if (content == null) return null;
    return (DocumentationComponent)content.getComponent();
  }

  @Override
  public void updateToolWindowDocumentationTabName(@NotNull ToolWindow toolWindow,
                                                   @NotNull PsiElement element,
                                                   @NotNull DocumentationManager documentationManager) {
    Content content = getDocumentationContent(toolWindow, documentationManager);
    if (content != null) content.setDisplayName(documentationManager.getTitle(element));
  }

  @Override
  public void disposeToolWindow(@NotNull ToolWindow toolWindow, @NotNull DocumentationManager documentationManager) {
    toolWindow.remove();
    Disposer.dispose(toolWindow.getContentManager());
  }
}
