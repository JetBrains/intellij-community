package org.jetbrains.jps.resolvers

import org.jetbrains.jps.ClasspathItem
import org.jetbrains.jps.Project

/**
 * @author max
 */
class ModuleResolver implements Resolver {
  Project project;

  def ClasspathItem resolve(String classpathItem) {
    if (classpathItem.startsWith("module:")) classpathItem = classpathItem.substring("module:".length())

    return project.modules[classpathItem]
  }

}
