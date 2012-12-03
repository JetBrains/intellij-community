package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.incremental.instrumentation.NotNullInstrumentingBuilder;
import org.jetbrains.jps.incremental.instrumentation.RmiStubsGenerator;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.service.SharedThreadPool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class JavaBuilderService extends BuilderService {
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    final ArrayList<BuildTargetType<?>> types = new ArrayList<BuildTargetType<?>>();
    types.addAll(JavaModuleBuildTargetType.ALL_TYPES);
    types.addAll(ResourcesTargetType.ALL_TYPES);
    return types;
  }

  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(new JavaBuilder(SharedThreadPool.getInstance()), new NotNullInstrumentingBuilder(), new RmiStubsGenerator());
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Arrays.asList(new ResourcesBuilder());
  }
}
