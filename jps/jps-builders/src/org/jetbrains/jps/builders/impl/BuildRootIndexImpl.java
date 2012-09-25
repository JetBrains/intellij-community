package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class BuildRootIndexImpl implements BuildRootIndex {
  private static final Key<Map<File, BuildRootDescriptor>> ROOT_DESCRIPTOR_MAP = Key.create("_root_to_descriptor_map");
  private static final Key<Map<BuildTarget<?>, List<? extends BuildRootDescriptor>>> TEMP_TARGET_ROOTS_MAP = Key.create("_module_to_root_map");
  private HashMap<BuildTarget<?>, List<? extends BuildRootDescriptor>> myRootsByTarget;
  private THashMap<File,List<BuildRootDescriptor>> myRootToDescriptor;
  
  public BuildRootIndexImpl(BuildTargetIndex targetIndex, JpsModel model, ModuleRootsIndex index, File dataStorageRoot) {
    myRootsByTarget = new HashMap<BuildTarget<?>, List<? extends BuildRootDescriptor>>();
    myRootToDescriptor = new THashMap<File, List<BuildRootDescriptor>>(FileUtil.FILE_HASHING_STRATEGY);
    final Iterable<AdditionalRootsProviderService> rootsProviders = JpsServiceManager.getInstance().getExtensions(AdditionalRootsProviderService.class);
    for (BuildTargetType<?> targetType : BuilderRegistry.getInstance().getTargetTypes()) {
      for (BuildTarget<?> target : targetIndex.getAllTargets(targetType)) {
        addRoots(dataStorageRoot, rootsProviders, targetType, target, model, index);
      }
    }
  }

  private <R extends BuildRootDescriptor> void addRoots(File dataStorageRoot, Iterable<AdditionalRootsProviderService> rootsProviders, BuildTargetType<?> targetType,
                                                        BuildTarget<R> target, JpsModel model, ModuleRootsIndex index) {
    List<R> descriptors = target.computeRootDescriptors(model, index);
    for (AdditionalRootsProviderService<?> provider : rootsProviders) {
      if (provider.getTargetTypes().contains(targetType)) {
        //noinspection unchecked
        AdditionalRootsProviderService<R> providerService = (AdditionalRootsProviderService<R>)provider;
        final List<R> additionalRoots = providerService.getAdditionalRoots(target, dataStorageRoot);
        if (!additionalRoots.isEmpty()) {
          descriptors = new ArrayList<R>(descriptors);
          descriptors.addAll(additionalRoots);
        }
      }
    }
    for (BuildRootDescriptor descriptor : descriptors) {
      List<BuildRootDescriptor> list = myRootToDescriptor.get(descriptor.getRootFile());
      if (list == null) {
        list = new SmartList<BuildRootDescriptor>();
        myRootToDescriptor.put(descriptor.getRootFile(), list);
      }
      list.add(descriptor);
    }
    myRootsByTarget.put(target, descriptors);
  }

  @NotNull
  @Override
  public <R extends BuildRootDescriptor> List<R> getRootDescriptors(@NotNull File root,
                                                                    @NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                                    @Nullable CompileContext context) {
    List<BuildRootDescriptor> descriptors = myRootToDescriptor.get(root);
    List<R> result = new SmartList<R>();
    if (descriptors != null) {
      for (BuildRootDescriptor descriptor : descriptors) {
        if (types.contains(descriptor.getTarget().getTargetType())) {
          //noinspection unchecked
          result.add((R)descriptor);
        }
      }
    }
    if (context != null) {
      final Map<File, BuildRootDescriptor> contextMap = ROOT_DESCRIPTOR_MAP.get(context);
      if (contextMap != null) {
        BuildRootDescriptor descriptor = contextMap.get(root);
        if (descriptor != null && types.contains(descriptor.getTarget().getTargetType())) {
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
    return contextMap != null ? (List<R>)contextMap.get(target) : Collections.<R>emptyList();
  }

  @Override
  public <R extends BuildRootDescriptor> void associateTempRoot(@NotNull CompileContext context,
                                                                @NotNull BuildTarget<R> target,
                                                                @NotNull R root) {
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
      current = FileUtil.getParentFile(current);
    }
    return null;
  }

  @Override
  @NotNull
  public <R extends BuildRootDescriptor> Collection<R> findAllParentDescriptors(@NotNull File file,
                                                                                @NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
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
      current = FileUtil.getParentFile(current);
    }
    return result != null ? result : Collections.<R>emptyList();
  }

  @Override
  @NotNull
  public Collection<? extends BuildRootDescriptor> clearTempRoots(@NotNull CompileContext context) {
    try {
      final Map<File, BuildRootDescriptor> map = ROOT_DESCRIPTOR_MAP.get(context);
      return map != null? map.values() : Collections.<RootDescriptor>emptyList();
    }
    finally {
      TEMP_TARGET_ROOTS_MAP.set(context, null);
      ROOT_DESCRIPTOR_MAP.set(context, null);
    }
  }

  @Override
  @Nullable
  public RootDescriptor getModuleAndRoot(@Nullable CompileContext context, File file) {
    return findParentDescriptor(file, JavaModuleBuildTargetType.ALL_TYPES, context);
  }
}
