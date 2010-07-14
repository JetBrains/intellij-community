package org.jetbrains.jps.listeners

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.Project
import org.jetbrains.jps.ModuleBuilder

/**
 * @author nik
 */
interface JpsBuildListener {
  
  def onBuildStarted(Project project)
  def onBuildFinished(Project project)

  def onCompilationStarted(ModuleChunk moduleChunk)
  def onCompilationFinished(ModuleChunk moduleChunk)

  def onModuleBuilderStarted(ModuleBuilder builder, ModuleChunk chunk)
  def onModuleBuilderFinished(ModuleBuilder builder, ModuleChunk chunk)

  def onJavaFilesCompiled(ModuleChunk moduleChunk, int filesCount)
}
