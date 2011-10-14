package org.jetbrains.jps.incremental;

import org.jetbrains.jps.Module;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/8/11
 */
public class FSSnapshotBuilder implements FileProcessor{
  private final FSSnapshot mySnapshot;

  public FSSnapshotBuilder(Module module) {
    mySnapshot = new FSSnapshot(module);
  }

  public boolean apply(Module module, File file, String sourceRoot) throws Exception {

    return true;
  }

  public FSSnapshot getResult() {
    return mySnapshot;
  }
}
