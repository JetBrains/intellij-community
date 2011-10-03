package org.jetbrains.jps.builders

import org.jetbrains.jps.Module

/**
 * @author nik
 */
public interface ModuleBuildTask {
  def perform(Module module, String outputFolder)
}
