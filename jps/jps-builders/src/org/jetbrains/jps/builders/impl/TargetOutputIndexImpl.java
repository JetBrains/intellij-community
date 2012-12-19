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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class TargetOutputIndexImpl implements TargetOutputIndex {
  private final Map<File, List<BuildTarget<?>>> myOutputToTargets;

  public TargetOutputIndexImpl(Collection<? extends BuildTarget<?>> allTargets, CompileContext context) {
    myOutputToTargets = new THashMap<File, List<BuildTarget<?>>>(FileUtil.FILE_HASHING_STRATEGY);
    for (BuildTarget<?> target : allTargets) {
      Collection<File> roots = target.getOutputRoots(context);
      for (File root : roots) {
        List<BuildTarget<?>> targets = myOutputToTargets.get(root);
        if (targets == null) {
          targets = new SmartList<BuildTarget<?>>();
          myOutputToTargets.put(root, targets);
        }
        targets.add(target);
      }
    }
  }

  @Override
  public Collection<BuildTarget<?>> getTargetsByOutputFile(@NotNull File file) {
    File current = file;
    Collection<BuildTarget<?>> result = null;
    while (current != null) {
      List<BuildTarget<?>> targets = myOutputToTargets.get(current);
      if (targets != null) {
        if (result == null) {
          result = targets;
        }
        else {
          result = new ArrayList<BuildTarget<?>>(result);
          result.addAll(targets);
        }
      }
      current = FileUtilRt.getParentFile(current);
    }
    return result != null ? result : Collections.<BuildTarget<?>>emptyList();
  }
}
