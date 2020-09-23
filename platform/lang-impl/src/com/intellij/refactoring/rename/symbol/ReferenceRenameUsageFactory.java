// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.symbol;

import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.refactoring.rename.api.RenameTarget;
import com.intellij.refactoring.rename.api.RenameUsage;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * If {@link RenameTarget} is {@linkplain SymbolRenameTarget based on a Symbol},
 * and the {@link RenameUsage} search is delegated to the symbol reference search,
 * then to provide a {@link RenameUsage} by a {@link PsiSymbolReference}, either:
 * <ul>
 * <li>implement {@link ReferenceRenameUsageFactory} and register as {@code com.intellij.rename.referenceRenameUsageFactory} extension</li>
 * <li>implement {@link RenameableReference} in a {@link PsiSymbolReference} to provide rename target for the reference</li>
 * <li>implement {@link RenameUsage} in a {@link PsiSymbolReference}</li>
 * </ul>
 */
public interface ReferenceRenameUsageFactory {

  @Internal ExtensionPointName<ReferenceRenameUsageFactory> EP_NAME = ExtensionPointName.create(
    "com.intellij.rename.referenceRenameUsageFactory"
  );

  @Nullable RenameUsage renameUsage(@NotNull PsiSymbolReference reference);
}
