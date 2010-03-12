package org.jetbrains.jps.builders

import org.jetbrains.jps.Module
import org.jetbrains.jps.ModuleBuildState

/**
 * @author nik
 */
public interface ModuleBuildTask {
  def perform(Module module, String outputFolder)
}
