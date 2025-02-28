// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.FileFilter;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class BuilderRegistry {
  private static final Logger LOG = Logger.getInstance(BuilderRegistry.class);
  private static final class Holder {
    static final BuilderRegistry ourInstance = new BuilderRegistry();
  }
  private final Map<BuilderCategory, List<ModuleLevelBuilder>> moduleLevelBuilders = new HashMap<>();
  private final List<JvmClassFileInstrumenter> myClassFileInstrumenters = new ArrayList<>();
  private final Object2LongMap<BuildTargetType<?>> expectedBuildTime = new Object2LongOpenHashMap<>();
  private final List<TargetBuilder<?,?>> targetBuilders = new ArrayList<>();
  private final FileFilter moduleBuilderFileFilter;

  public static @NotNull BuilderRegistry getInstance() {
    return Holder.ourInstance;
  }

  private BuilderRegistry() {
    for (BuilderCategory category : BuilderCategory.values()) {
      moduleLevelBuilders.put(category, new ArrayList<>());
    }

    Set<String> compilableFileExtensions = CollectionFactory.createFilePathSet();
    for (BuilderService service : JpsServiceManager.getInstance().getExtensions(BuilderService.class)) {
      targetBuilders.addAll(service.createBuilders());
      for (ModuleLevelBuilder builder : service.createModuleLevelBuilders()) {
        try {
          List<String> extensions = builder.getCompilableFileExtensions();
          if (compilableFileExtensions != null) {
            compilableFileExtensions.addAll(extensions);
          }
        }
        catch (AbstractMethodError e) {
          LOG.info(builder.getClass().getName() + " builder doesn't implement 'getCompilableFileExtensions' method so ModuleBuildTarget will process all files under source roots.");
          compilableFileExtensions = null;
        }
        moduleLevelBuilders.get(builder.getCategory()).add(builder);
        if (builder instanceof JvmClassFileInstrumenter) {
          myClassFileInstrumenters.add((JvmClassFileInstrumenter)builder);
        }
      }
    }
    Collections.sort(myClassFileInstrumenters, Comparator.comparing(JvmClassFileInstrumenter::getId));
    
    if (compilableFileExtensions == null) {
      moduleBuilderFileFilter = FileFilters.EVERYTHING;
    }
    else {
      Set<String> finalCompilableFileExtensions = compilableFileExtensions;
      moduleBuilderFileFilter = file -> finalCompilableFileExtensions.contains(FileUtilRt.getExtension(file.getName()));
    }

    long moduleTargetBuildTime = 0;
    for (ModuleLevelBuilder builder : getModuleLevelBuilders()) {
      moduleTargetBuildTime += builder.getExpectedBuildTime();
    }
    expectedBuildTime.put(JavaModuleBuildTargetType.PRODUCTION, moduleTargetBuildTime);
    expectedBuildTime.put(JavaModuleBuildTargetType.TEST, moduleTargetBuildTime);

    for (TargetBuilder<?, ?> targetBuilder : targetBuilders) {
      long buildTime = targetBuilder.getExpectedBuildTime();
      for (BuildTargetType<?> type : targetBuilder.getTargetTypes()) {
        long total = expectedBuildTime.getLong(type);
        expectedBuildTime.put(type, total + buildTime);
      }
    }
  }

  public @NotNull FileFilter getModuleBuilderFileFilter() {
    return moduleBuilderFileFilter;
  }

  public int getModuleLevelBuilderCount() {
    int count = 0;
    for (BuilderCategory category : BuilderCategory.values()) {
      count += getBuilders(category).size();
    }
    return count;
  }

  /**
   * Returns the list of all available class-file instrumenters in the sorted order. 
   */
  @ApiStatus.Internal
  public @NotNull List<JvmClassFileInstrumenter> getClassFileInstrumenters() {
    return myClassFileInstrumenters;
  }

  public @NotNull @Unmodifiable List<BuildTask> getBeforeTasks(){
    return List.of(); // todo
  }

  public @NotNull @Unmodifiable List<BuildTask> getAfterTasks(){
    return List.of(); // todo
  }

  public @NotNull @Unmodifiable List<ModuleLevelBuilder> getBuilders(BuilderCategory category){
    return Collections.unmodifiableList(moduleLevelBuilders.get(category));
  }

  public @NotNull @Unmodifiable List<ModuleLevelBuilder> getModuleLevelBuilders() {
    List<ModuleLevelBuilder> result = new ArrayList<>();
    for (BuilderCategory category : BuilderCategory.values()) {
      result.addAll(getBuilders(category));
    }
    return result;
  }

  public @NotNull @Unmodifiable List<TargetBuilder<?,?>> getTargetBuilders() {
    return targetBuilders;
  }

  /**
   * Returns default expected build time for targets of the given {@code targetType}.
   * @see Builder#getExpectedBuildTime()
   */
  public long getExpectedBuildTimeForTarget(BuildTargetType<?> targetType) {
    // it may happen that there are no builders registered for a given type, so it won't be built at all
    return expectedBuildTime.getLong(targetType);
  }
}
