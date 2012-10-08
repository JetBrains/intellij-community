package org.jetbrains.jps.builders.impl;

import com.intellij.util.PathUtilRt;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;

import java.io.File;

/**
 * @author nik
 */
public class BuildDataPathsImpl implements BuildDataPaths {
  private final File myDataStorageRoot;

  public BuildDataPathsImpl(File dataStorageRoot) {
    myDataStorageRoot = dataStorageRoot;
  }

  @Override
  public File getDataStorageRoot() {
    return myDataStorageRoot;
  }

  @Override
  public File getTargetsDataRoot() {
    return new File(myDataStorageRoot, "targets");
  }

  @Override
  public File getTargetTypeDataRoot(BuildTargetType<?> targetType) {
    return new File(getTargetsDataRoot(), targetType.getTypeId());
  }

  @Override
  public File getTargetDataRoot(BuildTarget<?> target) {
    return new File(getTargetTypeDataRoot(target.getTargetType()), PathUtilRt.suggestFileName(target.getId(), true, true));
  }
}
