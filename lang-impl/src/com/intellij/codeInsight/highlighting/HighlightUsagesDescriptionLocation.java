package com.intellij.codeInsight.highlighting;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;

/**
 * @author yole
 */
public class HighlightUsagesDescriptionLocation implements ElementDescriptionLocation {
  private HighlightUsagesDescriptionLocation() {
  }

  public static HighlightUsagesDescriptionLocation INSTANCE = new HighlightUsagesDescriptionLocation();

  public ElementDescriptionProvider getDefaultProvider() {
    return null;
  }
}
