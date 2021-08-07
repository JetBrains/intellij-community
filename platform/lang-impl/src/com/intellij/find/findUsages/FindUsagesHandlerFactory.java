// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class FindUsagesHandlerFactory implements PluginAware {
  public static final ExtensionPointName<FindUsagesHandlerFactory> EP_NAME = new ExtensionPointName<>("com.intellij.findUsagesHandlerFactory");
  PluginDescriptor pluginDescriptor;

  public abstract boolean canFindUsages(@NotNull PsiElement element);

  @Nullable
  public abstract FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, final boolean forHighlightUsages);

  public enum OperationMode {
    HIGHLIGHT_USAGES,
    USAGES_WITH_DEFAULT_OPTIONS,
    DEFAULT
  }

  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, @NotNull OperationMode operationMode) {
    return createFindUsagesHandler(element, operationMode == OperationMode.HIGHLIGHT_USAGES);
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
