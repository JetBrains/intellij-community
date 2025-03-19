// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation;

import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.createMaybeSingletonList;

/**
 * To provide a {@link DocumentationTarget} by a Symbol, either:
 * <ul>
 * <li>implement {@link SymbolDocumentationTargetProvider} and register as {@code com.intellij.platform.backend.documentation.symbolTargetProvider} extension</li>
 * <li>implement {@link DocumentationSymbol} in a Symbol to provide the documentation target for the symbol</li>
 * <li>implement {@link DocumentationTarget} directly in a Symbol</li>
 * </ul>
 *
 * @see DocumentationTargetProvider
 * @see PsiDocumentationTargetProvider
 */
@Experimental
@OverrideOnly
public interface SymbolDocumentationTargetProvider {

  @Internal
  ExtensionPointName<SymbolDocumentationTargetProvider> EP_NAME = ExtensionPointName.create(
    "com.intellij.platform.backend.documentation.symbolTargetProvider"
  );

  /**
   * @return target to handle documentation actions which are invoked on the given {@code symbol},
   * or {@code null} if this provider is not aware of the given symbol
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  default @Nullable DocumentationTarget documentationTarget(@NotNull Project project, @NotNull Symbol symbol) {
    throw new IllegalStateException("Override this or documentationTargets(Project, Symbol)");
  }

  /**
   * @return targets to handle documentation actions which are invoked on the given {@code symbol}, or an empty list if this provider is
   * unaware of the given symbol.
   * @apiNote if multiple targets are returned, then their order is maintained and will be reflected in the tool window and any popups.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  default @Unmodifiable @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(@NotNull Project project, @NotNull Symbol symbol) {
    return createMaybeSingletonList(documentationTarget(project, symbol));
  }
}
