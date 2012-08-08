package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

/**
 * @author nik
 */
public class JavaSourceRootType extends JpsModuleSourceRootType<JpsSimpleElement<JavaSourceRootProperties>> implements JpsElementTypeWithDefaultProperties<JpsSimpleElement<JavaSourceRootProperties>> {
  public static final JavaSourceRootType SOURCE = new JavaSourceRootType();
  public static final JavaSourceRootType TEST_SOURCE = new JavaSourceRootType();

  private JavaSourceRootType() {
  }

  @NotNull
  @Override
  public JpsSimpleElement<JavaSourceRootProperties> createDefaultProperties() {
    return JpsElementFactory.getInstance().createSimpleElement(new JavaSourceRootProperties());
  }
}
