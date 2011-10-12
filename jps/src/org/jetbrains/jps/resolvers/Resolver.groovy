package org.jetbrains.jps.resolvers

import org.jetbrains.jps.ClasspathItem

/**
 * @author max
 */
interface Resolver {
  def ClasspathItem resolve(String classpathItem);
}
