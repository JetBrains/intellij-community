// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import gnu.trove.THashSet;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.FileFilter;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class BuilderRegistry {
  private static final Logger LOG = Logger.getInstance(BuilderRegistry.class);
  private static final class Holder {
    static final BuilderRegistry ourInstance = new BuilderRegistry();
  }
  private final Map<BuilderCategory, List<ModuleLevelBuilder>> myModuleLevelBuilders = new HashMap<>();
  private final TObjectLongHashMap<BuildTargetType<?>> myExpectedBuildTime = new TObjectLongHashMap<>();
  private final List<TargetBuilder<?,?>> myTargetBuilders = new ArrayList<>();
  private final FileFilter myModuleBuilderFileFilter;

  public static BuilderRegistry getInstance() {
    return Holder.ourInstance;
  }

  private BuilderRegistry() {
    for (BuilderCategory category : BuilderCategory.values()) {
      myModuleLevelBuilders.put(category, new ArrayList<>());
    }

    Set<String> compilableFileExtensions = new THashSet<>(FileUtil.PATH_HASHING_STRATEGY);
    for (BuilderService service : JpsServiceManager.getInstance().getExtensions(BuilderService.class)) {
      myTargetBuilders.addAll(service.createBuilders());
      final List<? extends ModuleLevelBuilder> moduleLevelBuilders = service.createModuleLevelBuilders();
      for (ModuleLevelBuilder builder : moduleLevelBuilders) {
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
        myModuleLevelBuilders.get(builder.getCategory()).add(builder);
      }
    }
    if (compilableFileExtensions == null) {
      myModuleBuilderFileFilter = FileUtilRt.ALL_FILES;
    }
    else {
      final Set<String> finalCompilableFileExtensions = compilableFileExtensions;
      myModuleBuilderFileFilter = file -> finalCompilableFileExtensions.contains(FileUtilRt.getExtension(file.getName()));
    }

    long moduleTargetBuildTime = 0;
    for (ModuleLevelBuilder builder : getModuleLevelBuilders()) {
      moduleTargetBuildTime += builder.getExpectedBuildTime();
    }
    myExpectedBuildTime.put(JavaModuleBuildTargetType.PRODUCTION, moduleTargetBuildTime);
    myExpectedBuildTime.put(JavaModuleBuildTargetType.TEST, moduleTargetBuildTime);

    for (TargetBuilder<?, ?> targetBuilder : myTargetBuilders) {
      long buildTime = targetBuilder.getExpectedBuildTime();
      for (BuildTargetType<?> type : targetBuilder.getTargetTypes()) {
        if (!myExpectedBuildTime.adjustValue(type, buildTime)) {
          myExpectedBuildTime.put(type, buildTime);
        }
      }
    }
  }

  @NotNull
  public FileFilter getModuleBuilderFileFilter() {
    return myModuleBuilderFileFilter;
  }

  public int getModuleLevelBuilderCount() {
    int count = 0;
    for (BuilderCategory category : BuilderCategory.values()) {
      count += getBuilders(category).size();
    }
    return count;
  }

  public List<BuildTask> getBeforeTasks(){
    return Collections.emptyList(); // todo
  }

  public List<BuildTask> getAfterTasks(){
    return Collections.emptyList(); // todo
  }

  public List<ModuleLevelBuilder> getBuilders(BuilderCategory category){
    return Collections.unmodifiableList(myModuleLevelBuilders.get(category));
  }

  public List<ModuleLevelBuilder> getModuleLevelBuilders() {
    List<ModuleLevelBuilder> result = new ArrayList<>();
    for (BuilderCategory category : BuilderCategory.values()) {
      result.addAll(getBuilders(category));
    }
    return result;
  }

  public List<TargetBuilder<?,?>> getTargetBuilders() {
    return myTargetBuilders;
  }

  /**
   * Returns default expected build time for targets of the given {@code targetType}.
   * @see Builder#getExpectedBuildTime()
   */
  public long getExpectedBuildTimeForTarget(BuildTargetType<?> targetType) {
    long time = myExpectedBuildTime.get(targetType);
    if (time == -1) {
      //it may happen that there is no builders registered for a given type, so it won't be built at all.
      return 0;
    }
    return time;
  }
}
