package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.service.SharedThreadPool;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class JavaBuilderService extends BuilderService {
  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(new JavaBuilder(SharedThreadPool.getInstance()), new ResourcesBuilder());
  }
}
