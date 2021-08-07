// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide;

import com.intellij.openapi.ide.CutElementMarker;


public class PsiCutElementMarker implements CutElementMarker {
  @Override
  public boolean isCutElement(final Object element) {
    return PsiCopyPasteManager.getInstance().isCutElement(element);
  }
}