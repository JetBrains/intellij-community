package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

/**
 * @author nik
 */
public interface TargetOutputIndex {
  Collection<BuildTarget<?>> getTargetsByOutputFile(@NotNull File file);
}
