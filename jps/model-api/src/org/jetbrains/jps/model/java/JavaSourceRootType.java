package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author nik
 */
public class JavaSourceRootType extends JpsModuleSourceRootType<JavaSourceRootProperties> {
  public static final JavaSourceRootType SOURCE = new JavaSourceRootType();
  public static final JavaSourceRootType TEST_SOURCE = new JavaSourceRootType();

  @Override
  public JavaSourceRootProperties createDefaultProperties() {
    return new JavaSourceRootProperties();
  }

  @Override
  public JavaSourceRootProperties createCopy(JavaSourceRootProperties properties) {
    return new JavaSourceRootProperties(properties.getPackagePrefix());
  }
}
