// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * <h3>Overview</h3>
 * This package is the Rename side of the Symbol API.
 * The entry point is a {@link com.intellij.refactoring.rename.api.RenameTarget rename target}.
 * The platform obtains a target from a {@link com.intellij.model.Symbol symbol} in one of three ways,
 * see {@link com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory}.
 * <p>
 * The platform searches for {@link com.intellij.refactoring.rename.api.RenameUsage usages} of the target.
 * A usage is a declaration or a reference.
 * A {@link com.intellij.refactoring.rename.api.PsiRenameUsage} is a usage in a file.
 * A {@link com.intellij.refactoring.rename.api.ModifiableRenameUsage} tells the platform how to update the usage.
 * A {@link com.intellij.refactoring.rename.api.RenameUsageSearcher} adds a custom query to the default search.
 * </p>
 *
 * <h3>Validation and conflicts</h3>
 * A {@link com.intellij.refactoring.rename.api.RenameValidator} checks the new name on each keystroke.
 * A usage reports {@link com.intellij.refactoring.rename.api.RenameConflict conflicts} for the new name.
 * The platform shows the conflicts before it applies the refactoring.
 *
 * <h3>Lifecycle</h3>
 * A target and a usage are valid inside a single read action.
 * Each of them has a {@code createPointer()} method.
 * The platform calls it to restore the instance in a later read action.
 *
 * @see com.intellij.find.usages.api
 */
package com.intellij.refactoring.rename.api;
