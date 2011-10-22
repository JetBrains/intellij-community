package org.jetbrains.jps.incremental;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public abstract class Builder {

  public static boolean isFileDirty(File file, CompileContext context, TimestampStorage tsStorage) throws Exception {
    return !context.isMake() || tsStorage.getStamp(file) != file.lastModified();
  }

  public static enum ExitCode {
    OK, ABORT, ADDITIONAL_PASS_REQUIRED
  }

  public abstract String getName();

  public abstract ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException;

  public abstract String getDescription();

  public void cleanupResources() {

  }
}
