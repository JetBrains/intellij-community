package org.jetbrains.jps.builders;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public interface BuildOutputConsumer {
  void registerOutputFile(File outputFile, Collection<String> sourcePaths) throws IOException;
}
