package org.jetbrains.jps.resolvers

import org.jetbrains.jps.ClasspathItem

/**
 * @author max
 */
class PathEntry implements ClasspathItem {
  def String path;

  def List<String> getClasspathRoots(boolean test) {
    return Collections.singletonList(path)
  }

}
