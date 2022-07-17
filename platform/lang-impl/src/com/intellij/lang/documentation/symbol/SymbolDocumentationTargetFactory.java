// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.symbol;

import com.intellij.lang.documentation.DocumentationTarget;
import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * To provide a {@link DocumentationTarget} by a Symbol, either:
 * <ul>
 * <li>implement {@link SymbolDocumentationTargetFactory} and register as {@code com.intellij.lang.symbolDocumentation} extension</li>
 * <li>implement {@link DocumentationSymbol} in a Symbol to provide the documentation target for the symbol</li>
 * <li>implement {@link DocumentationTarget} directly in a Symbol</li>
 * </ul>
 *
 * @see com.intellij.lang.documentation.psi.PsiDocumentationTargetFactory
 */
@Experimental
public interface SymbolDocumentationTargetFactory {

  @Internal
  ExtensionPointName<SymbolDocumentationTargetFactory> EP_NAME = ExtensionPointName.create("com.intellij.lang.symbolDocumentation");

  /**
   * @return target to handle documentation actions which are invoked on the given {@code symbol},
   * or {@code null} if this factory is not aware of the given symbol
   */
  @Nullable DocumentationTarget documentationTarget(@NotNull Project project, @NotNull Symbol symbol);
}
