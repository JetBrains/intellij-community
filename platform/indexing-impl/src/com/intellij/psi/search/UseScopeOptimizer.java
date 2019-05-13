package com.intellij.psi.search;

/**
 * Excludes scope from use scope of PSI element. The extension should be used only for optimization, i.e. it should throw off scopes
 * which can't contain references to provided PSI element.
 *
 * @author Konstantin.Ulitin
 *
 * @deprecated use {@link ScopeOptimizer}
 */
@Deprecated
public abstract class UseScopeOptimizer implements ScopeOptimizer {}
