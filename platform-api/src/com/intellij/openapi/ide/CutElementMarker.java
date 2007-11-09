package com.intellij.openapi.ide;

import com.intellij.openapi.extensions.ExtensionPointName;

public interface CutElementMarker {
  ExtensionPointName<CutElementMarker> EP_NAME = ExtensionPointName.create("com.intellij.cutElementMarker");

  boolean isCutElement(Object element);
}