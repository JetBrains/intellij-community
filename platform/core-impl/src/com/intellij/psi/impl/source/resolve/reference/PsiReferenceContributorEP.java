// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

public final class PsiReferenceContributorEP extends CustomLoadingExtensionPointBean<PsiReferenceContributor> implements KeyedLazyInstance<PsiReferenceContributor> {
  @Attribute("language")
  public String language = Language.ANY.getID();

  @Attribute("implementation")
  public String implementationClass;

  @Override
  public String getKey() {
    return language;
  }

  @Nullable
  @Override
  protected String getImplementationClassName() {
    return implementationClass;
  }
}
