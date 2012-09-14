package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class AdditionalRootsProviderService {
  @NotNull
  public List<String> getAdditionalSourceRoots(@NotNull JpsModule module, File dataStorageRoot) {
    return Collections.emptyList();
  }
}
