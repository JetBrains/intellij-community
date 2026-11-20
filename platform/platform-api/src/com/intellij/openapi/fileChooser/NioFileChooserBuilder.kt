// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ThrowableConsumer
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.nio.file.Path

/**
 * Builds and shows a file or folder selection dialogue that works with [Path] instead of [com.intellij.openapi.vfs.VirtualFile].
 * Internally creates `UniversalFileChooser` instead of legacy `FileChooserDialogImpl`.
 *
 * The no-argument constructor starts with the options of [FileChooserDescriptorFactory.singleFile].
 * The constructor with a [FileChooserDescriptor] starts with a copy of the options of that descriptor.
 * Change the options only with the `with*` methods.
 * These methods mirror the methods of [FileChooserDescriptor] that do not use [com.intellij.openapi.vfs.VirtualFile].
 *
 * Example:
 * ```kotlin
 * val path = NioFileChooserBuilder()
 *   .withChooseFiles(false)
 *   .withChooseFolders(true)
 *   .withTitle(title)
 *   .chooseFile(project, toSelect)
 * ```
 */
@Suppress("unused")
@ApiStatus.Experimental
class NioFileChooserBuilder {
  private var descriptor: FileChooserDescriptor
  private var pathValidator: ThrowableConsumer<List<Path>, Exception>? = null

  /** Creates a builder with the options of [FileChooserDescriptorFactory.singleFile]. */
  constructor() {
    descriptor = FileChooserDescriptorFactory.singleFile()
  }

  /**
   * Creates a builder with a copy of the options of [descriptor].
   * The builder does not change [descriptor].
   * The copy keeps the overridden methods of [descriptor], for example [FileChooserDescriptor.validateSelectedFiles].
   */
  constructor(descriptor: FileChooserDescriptor) {
    this.descriptor = FileChooserDescriptor(descriptor)
  }

  /** @see FileChooserDescriptor.isChooseFiles */
  fun withChooseFiles(chooseFiles: Boolean): NioFileChooserBuilder = withKind(chooseFiles = chooseFiles)

  /** @see FileChooserDescriptor.isChooseFolders */
  fun withChooseFolders(chooseFolders: Boolean): NioFileChooserBuilder = withKind(chooseFolders = chooseFolders)

  /** @see FileChooserDescriptor.isChooseJarContents */
  fun withChooseJarContents(chooseJarContents: Boolean): NioFileChooserBuilder = withKind(chooseJarContents = chooseJarContents)

  /** @see FileChooserDescriptor.isChooseMultiple */
  fun withChooseMultiple(chooseMultiple: Boolean): NioFileChooserBuilder = withKind(chooseMultiple = chooseMultiple)

  /** @see FileChooserDescriptor.withTitle */
  fun withTitle(@NlsContexts.DialogTitle title: String?): NioFileChooserBuilder = apply { descriptor.withTitle(title) }

  /** @see FileChooserDescriptor.withDescription */
  fun withDescription(@NlsContexts.Label description: String?): NioFileChooserBuilder = apply { descriptor.withDescription(description) }

  /** @see FileChooserDescriptor.withHideIgnored */
  fun withHideIgnored(hideIgnored: Boolean): NioFileChooserBuilder = apply { descriptor.withHideIgnored(hideIgnored) }

  /** @see FileChooserDescriptor.withPathRoots */
  fun withPathRoots(vararg roots: Path): NioFileChooserBuilder = apply { descriptor.withPathRoots(*roots) }

  /** @see FileChooserDescriptor.withPathRoots */
  fun withPathRoots(roots: List<Path>): NioFileChooserBuilder = apply { descriptor.withPathRoots(roots) }

  /** @see FileChooserDescriptor.withShowFileSystemRoots */
  fun withShowFileSystemRoots(showFileSystemRoots: Boolean): NioFileChooserBuilder = apply {
    descriptor.withShowFileSystemRoots(showFileSystemRoots)
  }

  /** @see FileChooserDescriptor.withTreeRootVisible */
  fun withTreeRootVisible(isTreeRootVisible: Boolean): NioFileChooserBuilder = apply { descriptor.withTreeRootVisible(isTreeRootVisible) }

  /** @see FileChooserDescriptor.withShowHiddenFiles */
  fun withShowHiddenFiles(showHiddenFiles: Boolean): NioFileChooserBuilder = apply { descriptor.withShowHiddenFiles(showHiddenFiles) }

  /** @see FileChooserDescriptor.withPathFilter */
  fun withPathFilter(filter: Condition<in Path>?): NioFileChooserBuilder = apply { descriptor.withPathFilter(filter) }

  /** @see FileChooserDescriptor.withExtensionFilter */
  fun withExtensionFilter(type: FileType): NioFileChooserBuilder = apply { descriptor.withExtensionFilter(type) }

  /** @see FileChooserDescriptor.withExtensionFilter */
  fun withExtensionFilter(@NlsContexts.Label label: String, vararg types: FileType): NioFileChooserBuilder = apply {
    descriptor.withExtensionFilter(label, *types)
  }

  /** @see FileChooserDescriptor.withExtensionFilter */
  fun withExtensionFilter(extension: String): NioFileChooserBuilder = apply { descriptor.withExtensionFilter(extension) }

  /** @see FileChooserDescriptor.withExtensionFilter */
  fun withExtensionFilter(@NlsContexts.Label label: String, vararg extensions: String): NioFileChooserBuilder = apply {
    descriptor.withExtensionFilter(label, *extensions)
  }

  /** @see FileChooserDescriptor.withoutExtensionFilter */
  fun withoutExtensionFilter(): NioFileChooserBuilder = apply { descriptor.withoutExtensionFilter() }

  /** @see FileChooserDescriptor.withEnvironmentRestricted */
  fun withEnvironmentRestricted(environmentRestricted: Boolean): NioFileChooserBuilder = apply {
    descriptor.withEnvironmentRestricted(environmentRestricted)
  }

  /** @see FileChooserDescriptor.withLocalFileSystem */
  fun withLocalFileSystem(): NioFileChooserBuilder = apply { descriptor.withLocalFileSystem() }

  /** @see FileChooserDescriptor.withLocalFileSystem */
  fun withLocalFileSystem(localFileSystem: Boolean): NioFileChooserBuilder = apply { descriptor.withLocalFileSystem(localFileSystem) }

  /**
   * Adds a check of the chosen paths. The file chooser calls [validator] when the user clicks OK.
   * When [validator] throws an exception, the file chooser shows its message and stays open.
   * The check runs after the checks that the builder already has.
   *
   * To reject the paths, [validator] can throw [PathValidationException] with a localised message.
   * The file chooser also accepts other exceptions, but it logs them as unexpected.
   *
   * @see PathValidationException
   * @see NioFileChooserDialog.setPathValidator
   */
  fun withPathValidator(validator: ThrowableConsumer<List<Path>, Exception>): NioFileChooserBuilder = apply {
    val previous = pathValidator
    pathValidator = if (previous == null) validator
    else ThrowableConsumer { paths ->
      previous.consume(paths)
      validator.consume(paths)
    }
  }

  /**
   * Shows the dialogue and lets the user choose one or more files.
   *
   * @param project the project of the dialogue.
   * @param toSelect the path to select when the dialogue opens.
   * @param parent the parent component of the dialogue.
   * @return the paths that the user chose, or an empty list if the user cancelled the dialogue.
   */
  @JvmOverloads
  fun chooseFiles(project: Project?, toSelect: Path?, parent: Component? = null): List<Path> {
    val chooser = FileChooserFactory.getInstance().createNioFileChooser(descriptor, project, parent)
    pathValidator?.let { chooser.setPathValidator(it) }
    return if (toSelect != null) chooser.choose(toSelect) else chooser.choose()
  }

  /**
   * Shows the dialogue and lets the user choose one file.
   * Do not call [withChooseMultiple] with `true` before this method.
   *
   * @param project the project of the dialogue.
   * @param toSelect the path to select when the dialogue opens.
   * @param parent the parent component of the dialogue. When `null`, the active window is the parent.
   * @return the path that the user chose, or `null` if the user cancelled the dialogue.
   */
  @JvmOverloads
  fun chooseFile(project: Project?, toSelect: Path?, parent: Component? = null): Path? {
    val parentComponent = parent ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    LOG.assertTrue(!descriptor.isChooseMultiple)
    return chooseFiles(project, toSelect, parentComponent).firstOrNull()
  }

  private fun withKind(
    chooseFiles: Boolean = descriptor.isChooseFiles,
    chooseFolders: Boolean = descriptor.isChooseFolders,
    chooseJarContents: Boolean = descriptor.isChooseJarContents,
    chooseMultiple: Boolean = descriptor.isChooseMultiple,
  ): NioFileChooserBuilder = apply {
    descriptor = FileChooserDescriptor(descriptor, chooseFiles, chooseFolders, chooseJarContents, chooseMultiple)
  }

  private companion object {
    private val LOG = logger<NioFileChooserBuilder>()
  }
}
