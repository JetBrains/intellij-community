// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.util.*;

@ApiStatus.Internal
public final class TargetOutputIndexImpl implements TargetOutputIndex {
  private final Map<File, List<BuildTarget<?>>> myOutputToTargets;

  public TargetOutputIndexImpl(Collection<? extends BuildTarget<?>> allTargets, CompileContext context) {
    myOutputToTargets = FileCollectionFactory.createCanonicalFileMap();
    for (BuildTarget<?> target : allTargets) {
      Collection<File> roots = target.getOutputRoots(context);
      for (File root : roots) {
        List<BuildTarget<?>> targets = myOutputToTargets.get(root);
        if (targets == null) {
          targets = new SmartList<>();
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
          result = new ArrayList<>(result);
          result.addAll(targets);
        }
      }
      current = FileUtilRt.getParentFile(current);
    }
    return result != null ? result : Collections.emptyList();
  }
}
