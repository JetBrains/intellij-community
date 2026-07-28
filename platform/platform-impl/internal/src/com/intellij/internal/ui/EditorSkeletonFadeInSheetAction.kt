// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorSkeleton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Container
import java.awt.Font
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import kotlin.io.path.outputStream
import kotlin.math.ceil
import kotlin.math.sqrt

internal class EditorSkeletonFadeInSheetAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val descriptor = FileSaverDescriptor("Dump Editor Skeleton Fade-In", "Select file to save the frame sheet to:", "png")
    val target = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project).save(SHEET_FILE_NAME) ?: return
    @Suppress("IO_FILE_USAGE") val path = target.file.toPath()

    project.service<EditorSkeletonSheetScopeHolder>().scope.launch {
      val file = withBackgroundProgress(project, "Dumping editor skeleton fade-in") {
        reportSequentialProgress { reporter ->
          val frames = reporter.nextStep(endFraction = 80, text = "Rendering fade-in frames") {
            renderFrames(project, this)
          }
          val sheet = reporter.nextStep(endFraction = 90, text = "Composing the sheet") {
            contactSheet(frames)
          }
          reporter.nextStep(endFraction = 100, text = "Writing ${path.fileName}") {
            withContext(Dispatchers.IO) {
              path.outputStream().use { ImageIO.write(sheet, "png", it) }
              VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
            }
          }
        }
      } ?: return@launch

      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).openFile(file, true)
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class EditorSkeletonSheetScopeHolder(@JvmField val scope: CoroutineScope)

private suspend fun renderFrames(project: Project, parentScope: CoroutineScope): List<Pair<Long, BufferedImage>> {
  val clock = AtomicLong(0)
  val skeletonScope = parentScope.childScope("Editor Skeleton fade-in sheet")
  val skeleton = withContext(Dispatchers.EDT) {
    val skeleton = EditorSkeleton(
      cs = skeletonScope,
      project = project,
      skeletonDelayMs = FADE_IN_MS,
      nowMs = clock::get,
    )
    skeletonScope.cancel()

    skeleton.size = skeleton.preferredSize
    layoutTree(skeleton)

    render(skeleton)
    skeleton.tickFadeIn()
    skeleton
  }

  val timeline = ((0..FADE_IN_MS step FRAME_STEP_MS) + FADE_IN_MS).distinct()
  return reportSequentialProgress(timeline.size) { reporter ->
    timeline.map { elapsedMs ->
      reporter.itemStep("$elapsedMs ms of $FADE_IN_MS ms")
      withContext(Dispatchers.EDT) {
        clock.set(elapsedMs)
        skeleton.tickFadeIn()
        elapsedMs to render(skeleton)
      }
    }
  }
}

private fun render(skeleton: EditorSkeleton): BufferedImage {
  val image = ImageUtil.createImage(skeleton.width, minOf(skeleton.height, JBUI.scale(VIEWPORT_HEIGHT)), BufferedImage.TYPE_INT_RGB)
  val g = image.createGraphics()
  try {
    g.color = EditorSkeleton.EDITOR_BACKGROUND_COLOR
    g.fillRect(0, 0, image.width, image.height)
    skeleton.paint(g)
  }
  finally {
    g.dispose()
  }
  return image
}

private fun contactSheet(frames: List<Pair<Long, BufferedImage>>): BufferedImage {
  val frameWidth = frames.maxOf { it.second.width }
  val labelHeight = JBUI.scale(LABEL_HEIGHT)
  val gap = JBUI.scale(CELL_GAP)
  val cellHeight = frames.maxOf { it.second.height } + labelHeight
  val columns = ceil(sqrt(frames.size.toDouble() * cellHeight / frameWidth)).toInt().coerceAtLeast(1)
  val rows = ceil(frames.size.toDouble() / columns).toInt()

  val background = EditorSkeleton.EDITOR_BACKGROUND_COLOR
  val foreground = if (ColorUtil.isDark(background)) Gray._255 else Gray._0
  val sheet = ImageUtil.createImage(
    columns * frameWidth + (columns + 1) * gap,
    rows * cellHeight + (rows + 1) * gap,
    BufferedImage.TYPE_INT_RGB,
  )
  val g = sheet.createGraphics()
  try {
    GraphicsUtil.setupAAPainting(g)
    g.color = ColorUtil.mix(background, foreground, 0.15)
    g.fillRect(0, 0, sheet.width, sheet.height)
    g.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(LABEL_FONT_SIZE))
    for ((index, frame) in frames.withIndex()) {
      val (elapsedMs, image) = frame
      val x = gap + (index % columns) * (frameWidth + gap)
      val y = gap + (index / columns) * (cellHeight + gap)
      g.color = foreground
      g.drawString("$elapsedMs ms", x, y + g.fontMetrics.ascent)
      g.drawImage(image, x, y + labelHeight, null)
    }
  }
  finally {
    g.dispose()
  }
  return sheet
}

private fun layoutTree(component: Component) {
  if (component !is Container) return
  component.doLayout()
  for (child in component.components) {
    layoutTree(child)
  }
}

private const val SHEET_FILE_NAME = "editor-skeleton-fade-in.png"
private const val FADE_IN_MS = 500L
private const val FRAME_STEP_MS = 8L
private const val CELL_GAP = 10
private const val LABEL_HEIGHT = 22
private const val LABEL_FONT_SIZE = 14

private const val VIEWPORT_HEIGHT = 700
