/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private static class Holder {
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
        List<String> extensions = builder.getCompilableFileExtensions();
        if (extensions == null) {
          LOG.info(builder.getClass().getName() + " builder returns 'null' from 'getCompilableFileExtensions' method so files for module-level builders won't be filtered");
          compilableFileExtensions = null;
        }
        else if (compilableFileExtensions != null) {
          compilableFileExtensions.addAll(extensions);
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
