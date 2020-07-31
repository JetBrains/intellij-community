// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.psi.PsiElement;

/**
 * Elements which support external references should implement this interface.
 * <p>
 * There are two kinds of element references: own references and external references.<br/>
 * Own references are references the element knows about, they are usually used by language support.
 * Element doesn't know about external references since, for example, they might be contributed by plugins.
 * External references are used for navigation/Find Usages/etc as well as own references.
 * <p>
 * The element must implement this interface to support hosting external references,
 * so this mechanism is effectively opt-in.
 *
 * @see PsiElement#getOwnReferences
 * @see UrlReferenceHost
 */
public interface PsiExternalReferenceHost extends PsiElement {
}
