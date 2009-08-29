package com.intellij.openapi.roots;

/**
 * @author yole
 */
public class JavadocOrderRootType extends PersistentOrderRootType {
  private JavadocOrderRootType() {
    super("JAVADOC", "javadocPath", "javadoc-paths", "javadocPathEntry");
  }

  /**
   * JavaDoc paths.
   */
  public static OrderRootType getInstance() {
    return getOrderRootType(JavadocOrderRootType.class);
  }

  public boolean collectFromDependentModules() {
    return true;
  }
}
