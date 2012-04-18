package org.jetbrains.jps.idea;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class AdditionalRootsProviderService {
  @NotNull
  public List<String> getAdditionalSourceRoots(@NotNull Module module) {
    return Collections.emptyList();
  }
}
