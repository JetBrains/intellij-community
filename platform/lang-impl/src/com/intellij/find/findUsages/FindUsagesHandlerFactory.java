// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extend this class and register the implementation as {@code com.intellij.findUsagesHandlerFactory} extension in plugin.xml
 * to provide a factory of {@link FindUsagesHandler find usage handlers}.
 *
 * @see com.intellij.find.usages.symbol.SymbolSearchTargetFactory
 */
public abstract class FindUsagesHandlerFactory implements PluginAware {

  public static final ExtensionPointName<FindUsagesHandlerFactory> EP_NAME = new ExtensionPointName<>(
    "com.intellij.findUsagesHandlerFactory"
  );

  PluginDescriptor pluginDescriptor;

  /**
   * {@code true} if this factory can provide a {@code FindUsagesHandler} which is able to find usages of {@code element},
   * otherwise {@code false}
   */
  public abstract boolean canFindUsages(@NotNull PsiElement element);


  /**
   * @param element            target element, for which usages are searched
   * @param forHighlightUsages {@code true} is equivalent to {@link OperationMode#HIGHLIGHT_USAGES}
   * @return a handler, which is used for Find Usages action (and related actions),
   * or {@code null} if this factory cannot provide a handler
   */
  public abstract @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, final boolean forHighlightUsages);

  public enum OperationMode {
    /**
     * The handler will be used to highlight usages in the currently opened file.
     * The handler is not expected to show dialogs.
     *
     * @see com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass
     */
    HIGHLIGHT_USAGES,
    /**
     * The handler will be used in Show Usages popup.
     * The handler is not expected to show dialogs.
     */
    USAGES_WITH_DEFAULT_OPTIONS,
    /**
     * The handler will be used in Find Usages action.
     */
    DEFAULT
  }

  /**
   * @param element target element, for which usages are searched
   * @return a handler, which is used for Find Usages action (and related actions),
   * or {@code null} if this factory cannot provide a handler
   */
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, @NotNull OperationMode operationMode) {
    return createFindUsagesHandler(element, operationMode == OperationMode.HIGHLIGHT_USAGES);
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
