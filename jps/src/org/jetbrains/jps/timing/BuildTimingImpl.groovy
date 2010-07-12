package org.jetbrains.jps.timing

import org.jetbrains.jps.Project
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ModuleBuilder

/**
 * @author nik
 */
class BuildTimingImpl implements BuildTiming {
  private final Map<String, Long> elapsedTime = [:]
  private int compiledChunks
  private long moduleBuilderStartTime
  private  long buildStartTime

  def onBuildStarted(Project project) {
    buildStartTime = System.currentTimeMillis()
    compiledChunks = 0
  }

  def onBuildFinished(Project project) {
    long delta = System.currentTimeMillis() - buildStartTime
    project.stage("Total compilation time: ${formatTime(delta)}, ${compiledChunks} chunks compiled")
    elapsedTime.each {key, time ->
      project.stage(" $key: ${formatTime(time)}")
    }
  }

  def onCompilationStarted(ModuleChunk moduleChunk) {
  }

  def onCompilationFinished(ModuleChunk moduleChunk) {
    compiledChunks++
  }

  def onModuleBuilderStarted(ModuleBuilder builder, ModuleChunk chunk) {
    moduleBuilderStartTime = System.currentTimeMillis()
  }

  def onModuleBuilderFinished(ModuleBuilder builder, ModuleChunk chunk) {
    long delta = System.currentTimeMillis() - moduleBuilderStartTime
    def key = builder.getClass().getSimpleName()
    def oldValue = elapsedTime.get(key)
    elapsedTime.put(key, (oldValue != null ? oldValue.intValue() : 0) + delta)
  }

  private static def formatTime(long time) {
    long minutes = time / 60000
    long millis = time % 60000
    return "${minutes} minutes ${millis / 1000.0} seconds"
  }
}
