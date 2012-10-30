package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl;
import org.jetbrains.jps.model.serialization.impl.JpsProjectSerializationDataExtensionImpl;
import org.jetbrains.jps.model.serialization.module.JpsModuleSerializationDataExtension;

import java.io.File;

/**
 * @author nik
 */
public class JpsModelSerializationDataService {

  @Nullable
  public static JpsProjectSerializationDataExtension getProjectExtension(@NotNull JpsProject project) {
    return project.getContainer().getChild(JpsProjectSerializationDataExtensionImpl.ROLE);
  }

  @Nullable
  public static File getBaseDirectory(@NotNull JpsProject project) {
    JpsProjectSerializationDataExtension extension = getProjectExtension(project);
    return extension != null ? extension.getBaseDirectory() : null;
  }

  @Nullable
  public static JpsModuleSerializationDataExtension getModuleExtension(@NotNull JpsModule project) {
    return project.getContainer().getChild(JpsModuleSerializationDataExtensionImpl.ROLE);
  }

  @Nullable
  public static File getBaseDirectory(@NotNull JpsModule module) {
    JpsModuleSerializationDataExtension extension = getModuleExtension(module);
    return extension != null ? extension.getBaseDirectory() : null;
  }
}
