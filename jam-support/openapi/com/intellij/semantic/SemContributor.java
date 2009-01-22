/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.semantic;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author peter
 */
public abstract class SemContributor {
  public static final ExtensionPointName<SemContributor> EP_NAME = ExtensionPointName.create("com.intellij.semContributor");

  public abstract void registerSemProviders(SemRegistrar registrar);

}
