package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * @author nik
 */
public abstract class BuilderService {
  @NotNull
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders(ExecutorService executorService) {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends ProjectLevelBuilder> createProjectLevelBuilders() {
    return Collections.emptyList();
  }
}
