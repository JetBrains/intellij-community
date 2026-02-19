// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots

import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.LafManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.ui.sandbox.SandboxTreeLeaf
import com.intellij.internal.ui.sandbox.UISandboxDialog
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogWrapperDialog
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure
import com.intellij.util.PathUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode

/**
 * @author Konstantin Bulenkov
 */

//UX Designers don't place screenshots exactly at the center, but a bit higher than the middle horizontal line
private const val MAGIC_VERTICAL_OFFSET = 7

internal class CaptureScreenshotsPanel: UISandboxScreenshotPanel() {
  override val title = "Capture screenshots"
  override val screenshotSize = null
  override val sreenshotRelativePath = null

  private var pathToSDKDocsProject: String
    get() = PathUtil.toSystemDependentName(PropertiesComponent.getInstance().getValue("pathToSDKDocsProject", ""))
    set(value) {
      PropertiesComponent.getInstance().setValue("pathToSDKDocsProject", PathUtil.toSystemIndependentName(value.trim()))
    }

  private lateinit var textField:Cell<TextFieldWithBrowseButton>

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row { text("Instructions:") }
      row { text("1. Checkout <a href='https://github.com/JetBrains/intellij-sdk-docs.git'>https://github.com/JetBrains/intellij-sdk-docs.git</a>") }
      row { text("2. Provide the path to the repository root below") }
      row { text("3. Press 'Capture screenshots' button to update screenshots in the repository") }
      row {
        textField = textFieldWithBrowseButton(FileChooserDescriptorFactory.singleDir())
          .applyToComponent { text = pathToSDKDocsProject }
          .onChanged { pathToSDKDocsProject = it.text }
          .align(AlignX.FILL)
      }
      row {
        button("Capture Screenshots") {
          captureScreenshots()
        }.align(AlignX.CENTER)
      }
    }
  }

  private fun captureScreenshots() {
    val savedTheme = LafManager.getInstance().currentUIThemeLookAndFeel
    val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    if (activeWindow is DialogWrapperDialog) {
      val sandboxDialog = activeWindow.dialogWrapper as UISandboxDialog
      val lafManager = LafManager.getInstance()
      val lightLaf = lafManager.defaultLightLaf
      val darkLaf = lafManager.defaultDarkLaf
      lafManager.setCurrentLookAndFeel(lightLaf!!, false)
      lafManager.updateUI()
      lafManager.repaintUI()
      SwingUtilities.invokeLater {
        captureScreenshots(sandboxDialog) {
          lafManager.setCurrentLookAndFeel(darkLaf!!, false)
          lafManager.updateUI()
          SwingUtilities.invokeLater {
            captureScreenshots(sandboxDialog) {
              QuickChangeLookAndFeel.switchLafAndUpdateUI(LafManager.getInstance(), savedTheme, false)
            }
          }
        }
      }
    }
  }
  private fun captureScreenshots(sandboxDialog: UISandboxDialog ,onDone: Runnable) {
    val tree = sandboxDialog.tree
    var parent: SimpleNode? = null
    val nodes = mutableListOf<SimpleNode>()
    val root = tree.model.root as DefaultMutableTreeNode

    root.children().iterator().forEach {
      if (it.toString() == "For Screenshots") {
        parent = (it as DefaultMutableTreeNode).userObject as SimpleNode
        return@forEach
      }
    }
    traverseTree(root = parent!!,
                   getChildren = { node -> node.children },
                   onVisit = { node, _ -> if (node.children.isEmpty()) nodes.add(node) else tree.expandPath(tree.getPathFor(node)) })
      val iterator = nodes.iterator()
      if (iterator.hasNext()) {
        val onDoneRunnable = object : Runnable {
          override fun run() {
            if (!iterator.hasNext()) {
              onDone.run()
              return
            }
            val next = iterator.next()
            val panel: UISandboxPanel = ((next as FilteringTreeStructure.FilteringNode).delegate as SandboxTreeLeaf).sandboxPanel
            val screenshotPath = if (panel is UISandboxScreenshotPanel && panel.sreenshotRelativePath != null)
              Paths.get(pathToSDKDocsProject, panel.sreenshotRelativePath)
            else null

            val screenshotSize = if (panel is UISandboxScreenshotPanel) panel.screenshotSize else null
            SwingUtilities.invokeLater {
              makeScreenshot(tree, next, { sandboxDialog.placeholder.component as JComponent }, screenshotPath, screenshotSize, this)
            }
          }
        }
        SwingUtilities.invokeLater(onDoneRunnable)
      }
  }

  private fun makeScreenshot(
    tree: SimpleTree,
    node: SimpleNode,
    source: () -> JComponent,
    screenshotPath: Path?,
    screenshotSize: Dimension?,
    onDone: Runnable,
  ) {
    val path = tree.getPathFor(node)
    TreeUtil.selectPath(tree, path).doWhenDone {
      SwingUtilities.invokeLater {
        if (screenshotPath != null && screenshotSize != null) {
          val component = source()
          val screenshot = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
          val graphics = screenshot.createGraphics()
          component.printAll(graphics)
          graphics.dispose()
          val subimage = screenshot.getSubimage((screenshot.width - screenshotSize.width) / 2,
                                                (screenshot.height - screenshotSize.height) / 2 - MAGIC_VERTICAL_OFFSET,
                                                screenshotSize.width,
                                                screenshotSize.height)
          val result = ImageUtil.createRoundedImage(subimage, 28.0)
          val path = if (!JBColor.isBright()) {
            val pathStr = screenshotPath.toString()
            val lastDot = pathStr.lastIndexOf('.')
            if (lastDot > 0) {
              Paths.get(pathStr.substring(0, lastDot) + "_dark" + pathStr.substring(lastDot))
            } else {
              screenshotPath
            }
          } else { screenshotPath }
          ImageIO.write(result, "png", path.toFile())
        }
        SwingUtilities.invokeLater { onDone.run() }
      }
    }
  }
}

internal fun <T> traverseTree(
  root: T,
  getChildren: (T) -> Array<T>,
  onVisit: (node: T, depth: Int) -> Unit,
) {
    fun traverse(node: T, depth: Int) {
        onVisit(node, depth) // Perform an action on the current node
        getChildren(node).forEach { child ->
            traverse(child, depth + 1) // Recursively traverse each child
        }
    }
    traverse(root, 0)
}
