package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.module.JpsModuleType;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author nik
 */
public abstract class JpsElementFactory {
  public static JpsElementFactory getInstance() {
    return JpsServiceManager.getInstance().getService(JpsElementFactory.class);
  }

  public abstract JpsModule createModule(String name, JpsModuleType<?> type);

  public abstract JpsLibrary createLibrary(@NotNull String name, @NotNull JpsLibraryType<?> type);

  @NotNull
  public abstract JpsModuleReference createModuleReference(@NotNull String moduleName);

  @NotNull
  public abstract JpsLibraryReference createLibraryReference(@NotNull String libraryName,
                                                             @NotNull JpsElementReference<? extends JpsCompositeElement> parentReference);

  @NotNull
  public abstract JpsElementReference<JpsProject> createProjectReference();

  @NotNull
  public abstract JpsElementReference<JpsGlobal> createGlobalReference();
}
