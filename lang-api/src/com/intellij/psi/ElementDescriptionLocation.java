package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface ElementDescriptionLocation {
  @Nullable
  ElementDescriptionProvider getDefaultProvider();
}
