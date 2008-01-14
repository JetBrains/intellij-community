package com.intellij.openapi.roots;

/**
 * @author yole
 */
public class JavadocOrderRootType extends OrderRootType {
  private JavadocOrderRootType() {
    super("JAVADOC", "javadocPath", "javadoc-paths", true);
  }

  /**
   * JavaDoc paths.
   */
  public static OrderRootType getInstance() {
    return getOrderRootType(JavadocOrderRootType.class);
  }
}
