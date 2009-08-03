package org.jetbrains.jps

/**
 * @author max
 */
interface ModuleBuilder {
  def processModule(ModuleChunk module, ModuleBuildState state);
}
