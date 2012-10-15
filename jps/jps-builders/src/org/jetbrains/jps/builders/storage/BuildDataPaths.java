package org.jetbrains.jps.builders.storage;

import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.File;

/**
 * @author nik
 */
public interface BuildDataPaths {
  File getDataStorageRoot();

  File getTargetsDataRoot();

  File getTargetTypeDataRoot(BuildTargetType<?> targetType);

  File getTargetDataRoot(BuildTarget<?> target);
}
