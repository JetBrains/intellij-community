package com.intellij.codeInspection.reference;

/**
 * Additional entry points can be declared via {@link com.intellij.ExtensionPoints#INSPECTION_ENRTY_POINT}
 */
public interface EntryPoint {
  /**
   * @param refElement to be examined
   * @return true if refElement is entry point
   *         false otherwise
   */
  boolean accept(RefElement refElement);
}