// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.symbol;

import com.intellij.find.usages.symbol.SymbolSearchTargetFactory;
import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.rename.api.RenameTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.annotations.ApiStatus.Internal;

/**
 * To provide a {@link RenameTarget} by a Symbol, either:
 * <ul>
 * <li>implement {@link SymbolRenameTargetFactory} and register as {@code com.intellij.rename.symbolRenameTargetFactory} extension</li>
 * <li>implement {@link RenameableSymbol} in a Symbol to provide rename target for the symbol</li>
 * <li>implement {@link RenameTarget} in a Symbol</li>
 * </ul>
 *
 * @see SymbolSearchTargetFactory
 */
public interface SymbolRenameTargetFactory {

  @Internal ExtensionPointName<SymbolRenameTargetFactory> EP_NAME = ExtensionPointName.create(
    "com.intellij.rename.symbolRenameTargetFactory"
  );

  /**
   * @return target to be renamed when rename is invoked on a given {@code symbol}
   * @see SymbolSearchTargetFactory#createTarget
   */
  @Nullable RenameTarget renameTarget(@NotNull Project project, @NotNull Symbol symbol);
}
