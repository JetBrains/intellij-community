package com.intellij.openapi.roots;

/**
 * @author yole
 */
public class JavadocOrderRootType extends OrderRootType {
  /**
   * JavaDoc paths.
   */
  public static final OrderRootType INSTANCE = new JavadocOrderRootType();

  private JavadocOrderRootType() {
    super("JAVADOC", "javadocPath", true);
  }
}
