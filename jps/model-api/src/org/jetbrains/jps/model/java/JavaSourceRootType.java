package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author nik
 */
public class JavaSourceRootType extends JpsModuleSourceRootType<JavaSourceRootProperties> implements JpsElementTypeWithDefaultProperties<JavaSourceRootProperties> {
  public static final JavaSourceRootType SOURCE = new JavaSourceRootType();
  public static final JavaSourceRootType TEST_SOURCE = new JavaSourceRootType();

  @NotNull
  @Override
  public JavaSourceRootProperties createDefaultProperties() {
    return new JavaSourceRootProperties();
  }

  @Override
  public JavaSourceRootProperties createCopy(JavaSourceRootProperties properties) {
    return new JavaSourceRootProperties(properties.getPackagePrefix());
  }
}
