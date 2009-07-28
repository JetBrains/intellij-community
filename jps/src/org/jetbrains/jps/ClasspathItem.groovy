package org.jetbrains.jps

/**
 * @author max
 */
interface ClasspathItem {
  List<String> getClasspathRoots(boolean test);
}
