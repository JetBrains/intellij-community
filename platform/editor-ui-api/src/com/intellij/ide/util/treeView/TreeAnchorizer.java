// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

/**
 * Used by UI trees to get a more memory-efficient representation of their user objects.
 * For example, instead of holding PsiElement's they can hold PsiAnchor's which don't hold AST, document text, etc.
 * This service is used to perform object<->anchor conversion automatically so that all 100500 tree nodes don't have to do this themselves.
 */
public class TreeAnchorizer {
  private static TreeAnchorizer ourInstance;

  public static TreeAnchorizer getService() {
    TreeAnchorizer result = ourInstance;
    if (result == null) {
      result = ApplicationManager.getApplication().getService(TreeAnchorizer.class);
      ourInstance = result == null ? new TreeAnchorizer() : result;
    }
    return result;
  }

  public @NotNull Object createAnchor(@NotNull Object element) {
    return element;
  }

  public @Nullable Object retrieveElement(@NotNull Object anchor) {
    return anchor;
  }

  public void freeAnchor(Object element) { }

  public static @Unmodifiable @NotNull List<Object> anchorizeList(@NotNull Collection<Object> elements) {
    return ContainerUtil.map(elements, getService()::createAnchor);
  }

  public static @Unmodifiable @NotNull List<Object> retrieveList(Collection<Object> anchors) {
    return ContainerUtil.mapNotNull(anchors, getService()::retrieveElement);
  }

  @ApiStatus.Internal
  protected TreeAnchorizer() {
  }
}
