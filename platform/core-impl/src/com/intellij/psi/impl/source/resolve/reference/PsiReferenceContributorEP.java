// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiReferenceContributorEP extends CustomLoadingExtensionPointBean<PsiReferenceContributor> implements KeyedLazyInstance<PsiReferenceContributor> {
  @Attribute("language")
  public String language = Language.ANY.getID();

  @Attribute("implementation")
  public String implementationClass;

  @Override
  public @NotNull String getKey() {
    return language;
  }

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }
}
