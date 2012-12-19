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
package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author nik
 */
public class BuildRootIndexImpl implements BuildRootIndex {
  private static final Key<Map<File, BuildRootDescriptor>> ROOT_DESCRIPTOR_MAP = Key.create("_root_to_descriptor_map");
  private static final Key<Map<BuildTarget<?>, List<? extends BuildRootDescriptor>>> TEMP_TARGET_ROOTS_MAP = Key.create("_module_to_root_map");
  private HashMap<BuildTarget<?>, List<? extends BuildRootDescriptor>> myRootsByTarget;
  private THashMap<File,List<BuildRootDescriptor>> myRootToDescriptors;
  private ConcurrentMap<BuildRootDescriptor, FileFilter> myFileFilters;
  
  public BuildRootIndexImpl(BuildTargetIndex targetIndex, JpsModel model, ModuleExcludeIndex index,
                            BuildDataPaths dataPaths, final IgnoredFileIndex ignoredFileIndex) {
    myRootsByTarget = new HashMap<BuildTarget<?>, List<? extends BuildRootDescriptor>>();
    myRootToDescriptors = new THashMap<File, List<BuildRootDescriptor>>(FileUtil.FILE_HASHING_STRATEGY);
    myFileFilters = new ConcurrentHashMap<BuildRootDescriptor, FileFilter>();
    final Iterable<AdditionalRootsProviderService> rootsProviders = JpsServiceManager.getInstance().getExtensions(AdditionalRootsProviderService.class);
    for (BuildTargetType<?> targetType : BuilderRegistry.getInstance().getTargetTypes()) {
      for (BuildTarget<?> target : targetIndex.getAllTargets(targetType)) {
        addRoots(dataPaths, rootsProviders, target, model, index, ignoredFileIndex);
      }
    }
  }

  private <R extends BuildRootDescriptor> void addRoots(BuildDataPaths dataPaths, Iterable<AdditionalRootsProviderService> rootsProviders,
                                                        BuildTarget<R> target,
                                                        JpsModel model,
                                                        ModuleExcludeIndex index,
                                                        IgnoredFileIndex ignoredFileIndex) {
    List<R> descriptors = target.computeRootDescriptors(model, index, ignoredFileIndex, dataPaths);
    for (AdditionalRootsProviderService<?> provider : rootsProviders) {
      if (provider.getTargetTypes().contains(target.getTargetType())) {
        //noinspection unchecked
        AdditionalRootsProviderService<R> providerService = (AdditionalRootsProviderService<R>)provider;
        final List<R> additionalRoots = providerService.getAdditionalRoots(target, dataPaths);
        if (!additionalRoots.isEmpty()) {
          descriptors = new ArrayList<R>(descriptors);
          descriptors.addAll(additionalRoots);
        }
      }
    }
    for (BuildRootDescriptor descriptor : descriptors) {
      registerDescriptor(descriptor);
    }
    myRootsByTarget.put(target, descriptors);
  }

  private void registerDescriptor(BuildRootDescriptor descriptor) {
    List<BuildRootDescriptor> list = myRootToDescriptors.get(descriptor.getRootFile());
    if (list == null) {
      list = new SmartList<BuildRootDescriptor>();
      myRootToDescriptors.put(descriptor.getRootFile(), list);
    }
    list.add(descriptor);
  }

  private static Map<String, List<ModuleBuildTarget>> buildModuleNameToTargetsMap(BuildTargetIndex targetIndex) {
    final Map<String, List<ModuleBuildTarget>> moduleNameToTargetsMap = new HashMap<String, List<ModuleBuildTarget>>();
    for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
      for (ModuleBuildTarget target : targetIndex.getAllTargets(type)) {
        final String moduleName = target.getModule().getName();
        List<ModuleBuildTarget> targets = moduleNameToTargetsMap.get(moduleName);
        if (targets == null) {
          targets = new ArrayList<ModuleBuildTarget>();
          moduleNameToTargetsMap.put(moduleName, targets);
        }
        targets.add(target);
      }
    }
    return moduleNameToTargetsMap;
  }

  @NotNull
  @Override
  public <R extends BuildRootDescriptor> List<R> getRootDescriptors(@NotNull File root,
                                                                    @Nullable Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                                    @Nullable CompileContext context) {
    List<BuildRootDescriptor> descriptors = myRootToDescriptors.get(root);
    List<R> result = new SmartList<R>();
    if (descriptors != null) {
      for (BuildRootDescriptor descriptor : descriptors) {
        if (types == null || types.contains(descriptor.getTarget().getTargetType())) {
          //noinspection unchecked
          result.add((R)descriptor);
        }
      }
    }
    if (context != null) {
      final Map<File, BuildRootDescriptor> contextMap = ROOT_DESCRIPTOR_MAP.get(context);
      if (contextMap != null) {
        BuildRootDescriptor descriptor = contextMap.get(root);
        if (descriptor != null && (types == null || types.contains(descriptor.getTarget().getTargetType()))) {
          //noinspection unchecked
          result.add((R)descriptor);
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public <R extends BuildRootDescriptor> List<R> getTargetRoots(@NotNull BuildTarget<R> target, CompileContext context) {
    //noinspection unchecked
    List<R> roots = (List<R>)myRootsByTarget.get(target);
    if (context != null) {
      final List<R> tempDescriptors = getTempTargetRoots(target, context);
      if (!tempDescriptors.isEmpty()) {
        if (roots != null) {
          roots = new ArrayList<R>(roots);
          roots.addAll(tempDescriptors);
        }
        else {
          roots = tempDescriptors;
        }
      }
    }
    return roots != null? Collections.unmodifiableList(roots) : Collections.<R>emptyList();
  }

  @NotNull
  @Override
  public <R extends BuildRootDescriptor> List<R> getTempTargetRoots(@NotNull BuildTarget<R> target, @NotNull CompileContext context) {
    final Map<BuildTarget<?>, List<? extends BuildRootDescriptor>> contextMap = TEMP_TARGET_ROOTS_MAP.get(context);
    //noinspection unchecked
    final List<R> rootList = contextMap != null? (List<R>)contextMap.get(target) : null;
    return rootList != null ? rootList : Collections.<R>emptyList();
  }

  @Override
  public <R extends BuildRootDescriptor> void associateTempRoot(@NotNull CompileContext context, @NotNull BuildTarget<R> target, @NotNull R root) {
    Map<File, BuildRootDescriptor> rootToDescriptorMap = ROOT_DESCRIPTOR_MAP.get(context);
    if (rootToDescriptorMap == null) {
      rootToDescriptorMap = new THashMap<File, BuildRootDescriptor>(FileUtil.FILE_HASHING_STRATEGY);
      ROOT_DESCRIPTOR_MAP.set(context, rootToDescriptorMap);
    }

    Map<BuildTarget<?>, List<? extends BuildRootDescriptor>> targetToRootMap = TEMP_TARGET_ROOTS_MAP.get(context);
    if (targetToRootMap == null) {
      targetToRootMap = new HashMap<BuildTarget<?>, List<? extends BuildRootDescriptor>>();
      TEMP_TARGET_ROOTS_MAP.set(context, targetToRootMap);
    }

    final BuildRootDescriptor d = rootToDescriptorMap.get(root.getRootFile());
    if (d != null) {
      return;
    }

    //noinspection unchecked
    List<R> targetRoots = (List<R>)targetToRootMap.get(target);
    if (targetRoots == null) {
      targetRoots = new ArrayList<R>();
      targetToRootMap.put(target, targetRoots);
    }
    rootToDescriptorMap.put(root.getRootFile(), root);
    targetRoots.add(root);
  }

  @Override
  @Nullable
  public <R extends BuildRootDescriptor> R findParentDescriptor(@NotNull File file, @NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                                @Nullable CompileContext context) {
    File current = file;
    while (current != null) {
      final List<R> descriptors = getRootDescriptors(current, types, context);
      if (!descriptors.isEmpty()) {
        return descriptors.get(0);
      }
      current = FileUtilRt.getParentFile(current);
    }
    return null;
  }



  @Override
  @NotNull
  public <R extends BuildRootDescriptor> Collection<R> findAllParentDescriptors(@NotNull File file,
                                                                                @Nullable Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                                                @Nullable CompileContext context) {
    File current = file;
    Collection<R> result = null;
    while (current != null) {
      List<R> descriptors = getRootDescriptors(current, types, context);
      if (!descriptors.isEmpty()) {
        if (result == null) {
          result = descriptors;
        }
        else {
          result = new ArrayList<R>(result);
          result.addAll(descriptors);
        }
      }
      current = FileUtilRt.getParentFile(current);
    }
    return result != null ? result : Collections.<R>emptyList();
  }

  @NotNull
  @Override
  public <R extends BuildRootDescriptor> Collection<R> findAllParentDescriptors(@NotNull File file, @Nullable CompileContext context) {
    return findAllParentDescriptors(file, null, context);
  }

  @Override
  @NotNull
  public Collection<? extends BuildRootDescriptor> clearTempRoots(@NotNull CompileContext context) {
    try {
      final Map<File, BuildRootDescriptor> map = ROOT_DESCRIPTOR_MAP.get(context);
      return map != null? map.values() : Collections.<BuildRootDescriptor>emptyList();
    }
    finally {
      TEMP_TARGET_ROOTS_MAP.set(context, null);
      ROOT_DESCRIPTOR_MAP.set(context, null);
    }
  }

  @Override
  @Nullable
  public JavaSourceRootDescriptor findJavaRootDescriptor(@Nullable CompileContext context, File file) {
    return findParentDescriptor(file, JavaModuleBuildTargetType.ALL_TYPES, context);
  }

  @NotNull
  @Override
  public FileFilter getRootFilter(@NotNull BuildRootDescriptor descriptor, @NotNull ProjectDescriptor projectDescriptor) {
    FileFilter filter = myFileFilters.get(descriptor);
    if (filter == null) {
      filter = descriptor.createFileFilter(projectDescriptor);
      myFileFilters.put(descriptor, filter);
    }
    return filter;
  }
}
