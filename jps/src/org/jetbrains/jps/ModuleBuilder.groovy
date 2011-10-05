package org.jetbrains.jps

/**
 * @author max
 */
interface ModuleBuilder {
  def processModule(ModuleBuildState state, ModuleChunk moduleChunk, ProjectBuilder projectBuilder);
}
