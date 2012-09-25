package org.jetbrains.jps.builders.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.model.JpsModel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class BuildTargetIndexImpl implements BuildTargetIndex {
  private Map<BuildTargetType<?>, Collection<? extends BuildTarget<?>>> myTargets;

  public BuildTargetIndexImpl(@NotNull JpsModel model) {
    myTargets = new HashMap<BuildTargetType<?>, Collection<? extends BuildTarget<?>>>();
    for (BuildTargetType<?> type : BuilderRegistry.getInstance().getTargetTypes()) {
      myTargets.put(type, type.computeAllTargets(model));
    }
  }

  @NotNull
  @Override
  public <T extends BuildTarget<?>> Collection<T> getAllTargets(@NotNull BuildTargetType<T> type) {
    //noinspection unchecked
    return (Collection<T>)myTargets.get(type);
  }
}
