package org.jetbrains.jps.builders.javacApi

import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
class Java16ApiCompilerRunner {
  private static boolean notAvailable

  static boolean compile(ModuleChunk chunk, ProjectBuilder projectBuilder, ModuleBuildState state, String sourceLevel, String targetLevel, String customArgs) {
    if (notAvailable) {
      return false
    }

    try {
      Java16ApiCompiler compiler = Java16ApiCompiler.getInstance()
      compiler.compile(chunk, projectBuilder, state, sourceLevel, targetLevel, customArgs)
      return true
    }
    catch (NoClassDefFoundError error) {
      chunk.project.warning("Java 1.6 API compiler is not available")
      notAvailable = true
    }
    catch (Exception e) {
      e.printStackTrace()
      chunk.project.warning("Compilation failed with exception for '${chunk.name}'")
      throw e
    }
    return false
  }
}
