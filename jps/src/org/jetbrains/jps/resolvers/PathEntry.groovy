package org.jetbrains.jps.resolvers

import org.jetbrains.jps.ClasspathItem
import org.jetbrains.jps.ClasspathKind

/**
 * @author max
 */
class PathEntry implements ClasspathItem {
  def String path;

  def List<String> getClasspathRoots(ClasspathKind kind) {
    return [path]
  }

}
