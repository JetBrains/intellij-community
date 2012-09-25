package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public interface BuildTargetIndex {
  @NotNull
  <T extends BuildTarget<?>>
  Collection<T> getAllTargets(@NotNull BuildTargetType<T> type);
}
