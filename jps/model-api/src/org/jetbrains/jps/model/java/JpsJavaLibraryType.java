package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.library.JpsLibraryType;

/**
 * @author nik
 */
public class JpsJavaLibraryType extends JpsLibraryType<DummyJpsElementProperties> implements JpsElementTypeWithDefaultProperties<DummyJpsElementProperties> {
  public static final JpsJavaLibraryType INSTANCE = new JpsJavaLibraryType();

  @NotNull
  @Override
  public DummyJpsElementProperties createDefaultProperties() {
    return DummyJpsElementProperties.INSTANCE;
  }

  @Override
  public DummyJpsElementProperties createCopy(DummyJpsElementProperties properties) {
    return DummyJpsElementProperties.INSTANCE;
  }
}
