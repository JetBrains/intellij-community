// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi;


/**
 * Implement this interface in PsiElement to inject {@link com.intellij.openapi.paths.UrlReference URL reference}
 * into the element if its text contains a URL or a predefined URL pattern.
 */
public interface UrlReferenceHost extends PsiExternalReferenceHost {
}
