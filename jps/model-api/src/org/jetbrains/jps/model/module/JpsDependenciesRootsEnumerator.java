package org.jetbrains.jps.model.module;

import java.util.Collection;

/**
 * @author nik
 */
public interface JpsDependenciesRootsEnumerator {
  Collection<String> getUrls();
}
