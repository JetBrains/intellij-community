package org.jetbrains.jps.gwt

import org.jetbrains.jps.builders.ModuleBuilderService
import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
class GwtModuleBuilderService extends ModuleBuilderService {
  @Override
  registerBuilders(ProjectBuilder builder) {
    builder.weavingBuilders << new GwtModuleBuilder()
  }

}
