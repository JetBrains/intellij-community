package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public interface BuildTargetIndex extends BuildTargetRegistry {

  @NotNull
  Collection<BuildTarget<?>> getDependencies(@NotNull BuildTarget<?> target);

  List<BuildTargetChunk> getSortedTargetChunks();

  Set<BuildTarget<?>> getDependenciesRecursively(BuildTarget<?> target);
}
