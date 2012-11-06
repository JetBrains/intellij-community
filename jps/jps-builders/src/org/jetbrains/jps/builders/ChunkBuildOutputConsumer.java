package org.jetbrains.jps.builders;

import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public interface ChunkBuildOutputConsumer {
  void registerOutputFile(BuildTarget<?> target, String outputFilePath, Collection<String> sourceFiles) throws IOException;
}
