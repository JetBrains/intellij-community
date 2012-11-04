package org.jetbrains.jps.builders.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.Utils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author nik
 */
public abstract class DirtyFilesHolderBase<R extends BuildRootDescriptor, T extends BuildTarget<R>> implements DirtyFilesHolder<R, T> {
  protected final CompileContext myContext;

  public DirtyFilesHolderBase(CompileContext context) {
    myContext = context;
  }

  @Override
  public boolean hasRemovedFiles() {
    Map<BuildTarget<?>, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(myContext);
    return map != null && !map.isEmpty();
  }

  @NotNull
  @Override
  public Collection<String> getRemovedFiles(@NotNull T target) {
    Map<BuildTarget<?>, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(myContext);
    if (map != null) {
      Collection<String> paths = map.get(target);
      if (paths != null) {
        return paths;
      }
    }
    return Collections.emptyList();
  }
}
