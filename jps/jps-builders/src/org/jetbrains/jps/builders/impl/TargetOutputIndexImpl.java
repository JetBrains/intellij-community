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
