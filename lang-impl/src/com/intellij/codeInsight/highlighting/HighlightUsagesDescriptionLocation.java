package com.intellij.codeInsight.highlighting;

import com.intellij.psi.ElementDescriptionLocation;

/**
 * @author yole
 */
public class HighlightUsagesDescriptionLocation extends ElementDescriptionLocation {
  private HighlightUsagesDescriptionLocation() {
  }

  public static HighlightUsagesDescriptionLocation INSTANCE = new HighlightUsagesDescriptionLocation();

}
