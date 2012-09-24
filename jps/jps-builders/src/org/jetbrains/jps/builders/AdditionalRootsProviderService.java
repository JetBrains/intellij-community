package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class AdditionalRootsProviderService<R extends BuildRootDescriptor> {
  private Collection<? extends BuildTargetType> myTargetTypes;

  protected AdditionalRootsProviderService(Collection<? extends BuildTargetType> targetTypes) {
    myTargetTypes = targetTypes;
  }

  public Collection<? extends BuildTargetType> getTargetTypes() {
    return myTargetTypes;
  }

  @NotNull
  public List<R> getAdditionalRoots(@NotNull BuildTarget<R> target, File dataStorageRoot) {
    return Collections.emptyList();
  }
}
