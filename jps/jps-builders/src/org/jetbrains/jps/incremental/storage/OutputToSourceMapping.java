package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/7/11
 */
public class OutputToSourceMapping extends AbstractStateStorage<String, String> {

  public OutputToSourceMapping(File storePath) throws IOException {
    super(storePath, new EnumeratorStringDescriptor(), new EnumeratorStringDescriptor());
  }

  @Override
  public void update(@NotNull String outputPath, @Nullable String sourcePath) throws IOException {
    super.update(FileUtil.toSystemIndependentName(outputPath), sourcePath != null? FileUtil.toSystemIndependentName(sourcePath) : null);
  }

  @Override
  public void remove(@NotNull String outputPath) throws IOException {
    super.remove(FileUtil.toSystemIndependentName(outputPath));
  }

  @Override
  public String getState(@NotNull String outputPath) throws IOException {
    return super.getState(FileUtil.toSystemIndependentName(outputPath));
  }
}
