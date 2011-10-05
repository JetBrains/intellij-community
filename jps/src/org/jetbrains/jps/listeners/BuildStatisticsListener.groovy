package org.jetbrains.jps.listeners

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ModuleBuilder
import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
class BuildStatisticsListener implements JpsBuildListener {
  private final Map<String, Long> elapsedTime = [:]
  private int compiledChunks
  private int compiledJavaFiles
  private long moduleBuilderStartTime
  private long buildStartTime

  def onBuildStarted(ProjectBuilder projectBuilder) {
    buildStartTime = System.currentTimeMillis()
    compiledChunks = 0
    compiledJavaFiles = 0
  }

  def onBuildFinished(ProjectBuilder projectBuilder) {
    long delta = System.currentTimeMillis() - buildStartTime
    projectBuilder.info("Total compilation time: ${formatTime(delta)}, ${compiledJavaFiles} java files in ${compiledChunks} chunks compiled")
    elapsedTime.each {key, time ->
      projectBuilder.info(" $key: ${formatTime(time)}")
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

  def onJavaFilesCompiled(ModuleChunk moduleChunk, int filesCount) {
    compiledJavaFiles += filesCount
  }


  private static def formatTime(long time) {
    long minutes = time / 60000
    long millis = time % 60000
    return "${minutes} minutes ${millis / 1000.0} seconds"
  }
}
