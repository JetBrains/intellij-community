package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class AdditionalRootsProviderService<R extends BuildRootDescriptor> {
  private Collection<? extends BuildTargetType<? extends BuildTarget<R>>> myTargetTypes;

  protected AdditionalRootsProviderService(Collection<? extends BuildTargetType<? extends BuildTarget<R>>> targetTypes) {
    myTargetTypes = targetTypes;
  }

  public Collection<? extends BuildTargetType<? extends BuildTarget<R>>> getTargetTypes() {
    return myTargetTypes;
  }

  @NotNull
  public List<R> getAdditionalRoots(@NotNull BuildTarget<R> target, BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }
}
