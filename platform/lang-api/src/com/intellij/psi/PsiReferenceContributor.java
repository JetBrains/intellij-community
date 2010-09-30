/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * Via implementing this extension it's possible to provide references ({@link com.intellij.psi.PsiReference}) to
 * PSI elements which support that. Such known elements include: XML tags and attribute values, Java/Python/Javascript
 * literal expressions, comments etc. The reference contributors are run once per project and are able to
 * register reference providers for specific locations. See {@link com.intellij.psi.PsiReferenceRegistrar} for more details.
 *
 * The contributed references may then be obtained via
 * {@link com.intellij.psi.PsiReferenceService#getReferences(PsiElement, com.intellij.psi.PsiReferenceService.Hints)},
 * which is the preferred way.
 * Some elements return them from {@link PsiElement#getReferences()} directly though, but one should not rely on that
 * behavior since it may be changed in the future.
 *
 * @author peter
 */
public abstract class PsiReferenceContributor implements Disposable {
  public static final ExtensionPointName<PsiReferenceContributor> EP_NAME = ExtensionPointName.create("com.intellij.psi.referenceContributor");

  public abstract void registerReferenceProviders(PsiReferenceRegistrar registrar);

  public void dispose() {
  }
}
