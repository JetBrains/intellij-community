package org.jetbrains.jps.builders;

import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public interface BuildOutputConsumer {
  void registerOutputFile(String outputFilePath, Collection<String> sourceFiles) throws IOException;
}
