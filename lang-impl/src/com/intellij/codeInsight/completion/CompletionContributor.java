/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author peter
 */
public abstract class CompletionContributor implements Disposable {
  public static final ExtensionPointName<CompletionContributor> EP_NAME = ExtensionPointName.create("com.intellij.completion.contributor");

  public abstract void registerCompletionProviders(CompletionRegistrar registrar);

  public void dispose() {
  }
}
