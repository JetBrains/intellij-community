package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementProperties;

/**
 * @author nik
 */
public interface JpsModuleSourceRoot extends JpsElement {
  @NotNull
  JpsModuleSourceRootType<?> getRootType();

  @Nullable
  <P extends JpsElementProperties> P getProperties(@NotNull JpsModuleSourceRootType<P> type);

  @NotNull
  JpsElementProperties getProperties();

  <P extends JpsElementProperties>
  void setProperties(JpsModuleSourceRootType<P> type, P properties);

  @NotNull
  String getUrl();
}
