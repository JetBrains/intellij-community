package org.jetbrains.jps.builders

import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
public abstract class ModuleBuilderService {
  public abstract def registerBuilders(ProjectBuilder builder)
}
