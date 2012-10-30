package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;

import java.io.File;

/**
 * @author nik
 */
public interface JpsProjectSerializationDataExtension extends JpsElement {
  @NotNull
  File getBaseDirectory();
}
