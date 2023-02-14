// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base class for build targets which produce *.class files and copies resources from Java modules. <strong>It isn't supposed to be used from
 * plugins.</strong>
 * @author Eugene Zhuravlev
 */
public abstract class JVMModuleBuildTarget<R extends BuildRootDescriptor> extends ModuleBasedTarget<R> {
  public JVMModuleBuildTarget(@NotNull ModuleBasedBuildTargetType<? extends JVMModuleBuildTarget<R>> targetType, JpsModule module) {
    super(targetType, module);
  }

  @Override
  public String getId() {
    return getModule().getName();
  }

  @NotNull
  protected Set<File> computeRootExcludes(File root, ModuleExcludeIndex index) {
    final Collection<File> moduleExcludes = index.getModuleExcludes(getModule());
    if (moduleExcludes.isEmpty()) {
      return Collections.emptySet();
    }
    final Set<File> excludes = FileCollectionFactory.createCanonicalFileSet();
    for (File excluded : moduleExcludes) {
      if (FileUtil.isAncestor(root, excluded, true)) {
        excludes.add(excluded);
      }
    }
    return excludes;
  }

  @Override
  public R findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    final List<R> descriptors = rootIndex.getRootDescriptors(
      new File(rootId), Collections.singletonList((BuildTargetType<? extends JVMModuleBuildTarget<R>>)getTargetType()), null
    );
    return ContainerUtil.getFirstItem(descriptors);
  }
}
