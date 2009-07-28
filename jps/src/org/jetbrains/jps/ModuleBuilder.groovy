package org.jetbrains.jps

/**
 * @author max
 */
interface ModuleBuilder {
  def processModule(Module module, ModuleBuildState state);
}
