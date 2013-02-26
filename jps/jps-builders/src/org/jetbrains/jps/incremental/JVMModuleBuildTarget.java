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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
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
 * @author Eugene Zhuravlev
 *         Date: 11/12/12
 */
public abstract class JVMModuleBuildTarget<R extends BuildRootDescriptor> extends ModuleBasedTarget<R> {

  public JVMModuleBuildTarget(ModuleBasedBuildTargetType<? extends JVMModuleBuildTarget<R>> targetType, JpsModule module) {
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
    final Set<File> excludes = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
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
