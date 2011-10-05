package org.jetbrains.jps.listeners

import org.jetbrains.jps.ModuleBuilder
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
interface JpsBuildListener {
  
  def onBuildStarted(ProjectBuilder projectBuilder)
  def onBuildFinished(ProjectBuilder projectBuilder)

  def onCompilationStarted(ModuleChunk moduleChunk)
  def onCompilationFinished(ModuleChunk moduleChunk)

  def onModuleBuilderStarted(ModuleBuilder builder, ModuleChunk chunk)
  def onModuleBuilderFinished(ModuleBuilder builder, ModuleChunk chunk)

  def onJavaFilesCompiled(ModuleChunk moduleChunk, int filesCount)
}
