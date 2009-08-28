package com.intellij.ide;

import com.intellij.openapi.ide.CutElementMarker;

/**
 * @author yole
 */
public class PsiCutElementMarker implements CutElementMarker {
  public boolean isCutElement(final Object element) {
    return PsiCopyPasteManager.getInstance().isCutElement(element);
  }
}