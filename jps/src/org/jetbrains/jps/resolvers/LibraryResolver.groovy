package org.jetbrains.jps.resolvers

import org.jetbrains.jps.ClasspathItem
import org.jetbrains.jps.Project

/**
 * @author max
 */

class LibraryResolver implements Resolver {
  Project project;

  def ClasspathItem resolve(String classpathItem) {
    if (classpathItem.startsWith("lib:")) classpathItem = classpathItem.substring("lib:".length())

    return project.libraries[classpathItem]
  }
}
