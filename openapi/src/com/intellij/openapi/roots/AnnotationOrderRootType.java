package com.intellij.openapi.roots;

/**
 * @author yole
 */
public class AnnotationOrderRootType extends OrderRootType {
  /**
   * External annotations path
   */
  public static final OrderRootType INSTANCE = new AnnotationOrderRootType();

  private AnnotationOrderRootType() {
    super("ANNOTATIONS", "annotationsPath", true);
  }
}
