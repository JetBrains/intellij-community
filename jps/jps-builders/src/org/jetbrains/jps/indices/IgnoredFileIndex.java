package org.jetbrains.jps.indices;

/**
 * @author nik
 */
public interface IgnoredFileIndex {
  boolean isIgnored(String fileName);
}
