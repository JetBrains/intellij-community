package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public interface BuildTargetIndex {
  @NotNull
  Collection<BuildTarget<?>> getAllTargets(@NotNull BuildTargetType type);
}
