package com.intellij.psi;

import com.intellij.openapi.extensions.Extensions;

/**
 * @author yole
 */
public class ElementDescriptionUtil {
  private ElementDescriptionUtil() {
  }

  public static String getElementDescription(PsiElement element, ElementDescriptionLocation location) {
    for(ElementDescriptionProvider provider: Extensions.getExtensions(ElementDescriptionProvider.EP_NAME)) {
      String result = provider.getElementDescription(element, location);
      if (result != null) return result;
    }

    ElementDescriptionProvider defaultProvider = location.getDefaultProvider();
    if (defaultProvider != null) {
      String result = defaultProvider.getElementDescription(element, location);
      if (result != null) return result;
    }

    for(ElementDescriptionProvider provider: Extensions.getExtensions(ElementDescriptionProvider.EP_NAME)) {
      String result = provider.getElementDescription(element, null);
      if (result != null) return result;
    }

    return element.toString();
  }
}
