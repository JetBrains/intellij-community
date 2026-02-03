// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.ide.CutElementMarker;

final class PsiCutElementMarker implements CutElementMarker {
  @Override
  public boolean isCutElement(final Object element) {
    return PsiCopyPasteManager.getInstance().isCutElement(element);
  }
}