// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base class for build targets which produce *.class files and copy resources from Java modules.
 * It isn't supposed to be used from plugins.</strong>
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public abstract class JVMModuleBuildTarget<R extends BuildRootDescriptor> extends ModuleBasedTarget<R> {
  public JVMModuleBuildTarget(@NotNull ModuleBasedBuildTargetType<? extends JVMModuleBuildTarget<R>> targetType, JpsModule module) {
    super(targetType, module);
  }

  @Override
  public @NotNull String getId() {
    return getModule().getName();
  }

  protected final @NotNull Set<Path> computeRootExcludes(Path root, ModuleExcludeIndex index) {
    Collection<Path> moduleExcludes = index.getModuleExcludes(getModule());
    if (moduleExcludes.isEmpty()) {
      return Collections.emptySet();
    }

    Set<Path> excludes = FileCollectionFactory.createCanonicalPathSet();
    for (Path excluded : moduleExcludes) {
      if (FileUtil.isAncestor(root.toString(), excluded.toString(), true)) {
        excludes.add(excluded);
      }
    }
    return excludes.isEmpty() ? Collections.emptySet() : excludes;
  }

  @Override
  public R findRootDescriptor(@NotNull String rootId, @NotNull BuildRootIndex rootIndex) {
    List<R> descriptors = rootIndex.getRootDescriptors(new File(rootId), List.of(getTargetType()), null);
    return descriptors.isEmpty() ? null : descriptors.get(0);
  }
}
