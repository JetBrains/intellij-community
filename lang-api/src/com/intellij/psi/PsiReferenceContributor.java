/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author peter
 */
public abstract class PsiReferenceContributor implements Disposable {
  public static final ExtensionPointName<PsiReferenceContributor> EP_NAME = ExtensionPointName.create("com.intellij.psi.referenceContributor");

  public abstract void registerReferenceProviders(PsiReferenceRegistrar registrar);

  public void dispose() {
  }
}
