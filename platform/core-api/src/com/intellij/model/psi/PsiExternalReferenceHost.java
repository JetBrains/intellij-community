// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi;

import com.intellij.psi.PsiElement;

/**
 * A marker interface for elements that support external references.
 * <p>
 * There are two kinds of element references:
 * <ol>
 *   <li>Own references - known by the element, part of the language.</li>
 *   <li><b>External references</b> - unknown by the element, contributed by plugins.</li>
 * </ol>
 * Own and external references are used for navigation, finding usages, and so on.
 * <p>
 * The element must implement this interface to support hosting external references,
 * so this mechanism is effectively opt-in.
 *
 * @see PsiSymbolReferenceProvider
 * @see PsiElement#getOwnReferences
 * @see UrlReferenceHost
 */
public interface PsiExternalReferenceHost extends PsiElement {
}
