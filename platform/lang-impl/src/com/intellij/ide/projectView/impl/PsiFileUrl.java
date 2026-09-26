// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class PsiFileUrl extends AbstractUrl {
  private static final @NonNls String ELEMENT_TYPE = TYPE_PSI_FILE;

  public PsiFileUrl(final String url) {
    super(url, null, ELEMENT_TYPE);
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new PsiFileUrl(url);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof PsiFileUrl) {
     return StringUtil.equals(url, ((PsiFileUrl)o).url);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return url.hashCode();
  }
}
