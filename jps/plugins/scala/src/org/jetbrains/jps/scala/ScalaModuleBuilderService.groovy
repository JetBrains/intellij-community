package org.jetbrains.jps.scala

import org.jetbrains.jps.builders.ModuleBuilderService
import org.jetbrains.jps.ProjectBuilder

class ScalaModuleBuilderService extends ModuleBuilderService {
  @Override
  registerBuilders(ProjectBuilder builder) {
    builder.sourceModifyingBuilders << new ScalaModuleBuilder();
  }
}
