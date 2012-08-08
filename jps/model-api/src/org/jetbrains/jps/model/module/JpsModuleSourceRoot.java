package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsModuleSourceRoot extends JpsElement {
  @NotNull
  JpsModuleSourceRootType<?> getRootType();

  @Nullable
  <P extends JpsElement> P getProperties(@NotNull JpsModuleSourceRootType<P> type);

  @NotNull
  JpsElement getProperties();

  @NotNull
  String getUrl();
}
