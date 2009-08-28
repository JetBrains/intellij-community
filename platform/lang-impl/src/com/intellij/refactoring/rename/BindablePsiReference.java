package com.intellij.refactoring.rename;

import com.intellij.psi.PsiReference;

/**
 * Hacky marker interface to fix refactoring.
 * Means that bindToElement() works properly.
 * In fact, all references should be bindable.
 *
 * @author Dmitry Avdeev
 */
public interface BindablePsiReference extends PsiReference {
}
