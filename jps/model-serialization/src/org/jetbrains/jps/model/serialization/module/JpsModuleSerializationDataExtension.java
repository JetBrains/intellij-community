package org.jetbrains.jps.model.serialization.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;

import java.io.File;

/**
 * @author nik
 */
public interface JpsModuleSerializationDataExtension extends JpsElement {
  @NotNull
  File getBaseDirectory();
}
