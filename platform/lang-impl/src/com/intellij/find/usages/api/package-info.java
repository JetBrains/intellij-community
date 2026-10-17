// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * <h3>Overview</h3>
 * This package is the Find Usages side of the Symbol API.
 * The entry point is a {@link com.intellij.find.usages.api.SearchTarget search target}.
 * The platform obtains a target from a {@link com.intellij.model.Symbol symbol} in one of three ways,
 * see {@link com.intellij.find.usages.symbol.SymbolSearchTargetFactory}.
 * <p>
 * The platform searches for {@link com.intellij.find.usages.api.Usage usages} of the target.
 * A usage is a declaration or a reference.
 * A {@link com.intellij.find.usages.api.PsiUsage} is a usage in a file.
 * The default search finds references and declarations through
 * {@link com.intellij.model.psi.PsiSymbolReferenceProvider#getSearchRequests search requests}.
 * A {@link com.intellij.find.usages.api.UsageSearcher} adds a custom query to the default search.
 * </p>
 *
 * <h3>Lifecycle</h3>
 * A target and a usage are valid inside a single read action.
 * Each of them has a {@code createPointer()} method.
 * The platform calls it to restore the instance in a later read action.
 *
 * <h3>Related APIs</h3>
 * <ul>
 *   <li>{@link com.intellij.model.search} finds references to a symbol</li>
 *   <li>{@link com.intellij.refactoring.rename.api} renames a target and follows the same structure</li>
 * </ul>
 */
package com.intellij.find.usages.api;
