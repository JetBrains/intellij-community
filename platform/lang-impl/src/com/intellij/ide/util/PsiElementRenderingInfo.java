// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.util.TextWithIcon;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;

public interface PsiElementRenderingInfo<T extends PsiElement> {

  @RequiresReadLock
  @RequiresBackgroundThread
  default @Nullable Icon getIcon(@NotNull T element) {
    return element.getIcon(0);
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  @NlsSafe @NotNull String getPresentableText(@NotNull T element);

  @RequiresReadLock
  @RequiresBackgroundThread
  default @NlsSafe @Nullable String getContainerText(@NotNull T element) {
    return null;
  }

  static <T extends @NotNull PsiElement>
  @NotNull Comparator<T> getComparator(@NotNull PsiElementRenderingInfo<? super T> renderingInfo) {
    return Comparator.comparing(element -> ReadAction.compute(() -> {
      String elementText = renderingInfo.getPresentableText(element);
      String containerText = renderingInfo.getContainerText(element);
      TextWithIcon moduleTextWithIcon = PsiElementListCellRenderer.getModuleTextWithIcon(element);
      return (containerText == null ? elementText : elementText + " " + containerText) +
             (moduleTextWithIcon != null ? moduleTextWithIcon.getText() : "");
    }));
  }

  static <T extends @NotNull PsiElement>
  @NotNull TargetPresentation targetPresentation(T element, @NotNull PsiElementRenderingInfo<? super T> renderingInfo) {
    return PsiElementListCellRenderer.targetPresentation(element, renderingInfo);
  }
}
