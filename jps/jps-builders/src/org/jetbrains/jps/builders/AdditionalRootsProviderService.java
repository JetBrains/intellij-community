package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class AdditionalRootsProviderService {
  @NotNull
  public List<String> getAdditionalSourceRoots(@NotNull JpsModule module, BuildDataManager dataManager) {
    return Collections.emptyList();
  }
}
