package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/27/12
 */
public interface BuildTargetRegistry {
  @NotNull
  <T extends BuildTarget<?>>
  List<T> getAllTargets(@NotNull BuildTargetType<T> type);

  List<? extends BuildTarget<?>> getAllTargets();

  enum ModuleTargetSelector {
    PRODUCTION, TEST, ALL
  }
  Collection<ModuleBasedTarget<?>> getModuleBasedTargets(@Nullable JpsModule module, @NotNull ModuleTargetSelector selector);
}
