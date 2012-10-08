package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class BuilderService {
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends TargetBuilder<?,?>> createBuilders() {
    return Collections.emptyList();
  }
}
