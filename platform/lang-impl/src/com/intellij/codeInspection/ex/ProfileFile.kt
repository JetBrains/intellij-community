// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.lang.impl.modcommand.ModCommandExecutorImpl
import com.intellij.modcommand.FutureVirtualFile
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val LOG = logger<ProfileFile>()

private const val PROFILE_TAG = "profile"
private const val INSPECTION_TOOL_TAG = "inspection_tool"
private const val CLASS_ATTRIBUTE = "class"

/**
 * The file that stores an inspection profile. It contains [ModCommand]-related APIs to manipulate with profile content.
 *
 * @param path the path of the file; the file and its parent directories can be absent
 * @param profileName the profile name to write into the file when a command creates it
 */
@ApiStatus.Internal
data class ProfileFile(val path: Path, val profileName: String) {
  /**
   * The command that writes the settings of [tools] into this profile.
   *
   * The command changes only the `inspection_tool` elements of [tools]. It keeps the other text of the file.
   * It creates the file and its parent directories if they are absent.
   * The command does nothing when the file already has equal elements.
   * It shows an error when the file is not an inspection profile.
   */
  fun writeTools(tools: Collection<ToolsImpl>): ModCommand {
    if (tools.isEmpty()) return ModCommand.nop()
    val toolElements = tools.map { toolElement(it) }
    val path = path
    // The virtual file system supplies the current snapshot, including unsaved profile files
    val file = VirtualFileManager.getInstance().findFileByNioPath(path)
    if (file?.isDirectory == true) return ModCommand.nop()
    if (file != null) {
      val oldText = readText(file)
                    ?: return ModCommand.error(InspectionsBundle.message("disable.inspection.cannot.read.profile", path))
      val newText = replaceToolElements(oldText, toolElements)
                    ?: return ModCommand.error(InspectionsBundle.message("disable.inspection.cannot.read.profile", path))
      // The profile in memory can be older than the file, and the file can already have the settings
      if (newText == oldText) return ModCommand.nop()
      return ModUpdateFileText(file, oldText, newText, ModCommandExecutorImpl.calculateFragments(oldText, newText))
    }
    val profileElement = Element(PROFILE_TAG).setAttribute("version", "1.0")
    profileElement.addContent(Element("option").setAttribute("name", "myName").setAttribute("value", profileName))
    toolElements.sortedBy { it.getAttributeValue(CLASS_ATTRIBUTE) }.forEach { profileElement.addContent(it) }
    val root = Element("component").setAttribute("name", "InspectionProjectProfileManager").addContent(profileElement)
    return createFile(path, JDOMUtil.write(root, "\n"))
  }

  /** `true` when [this@canWrite] or one of its parent directories is in the virtual file system, so a command can address the file. */
  fun canWrite(): Boolean = findExistingAncestor(path) != null


  private fun toolElement(tools: ToolsImpl): Element {
    val element = Element(INSPECTION_TOOL_TAG).setAttribute(CLASS_ATTRIBUTE, tools.shortName)
    tools.writeExternal(element)
    return element
  }

  /**
   * The text of the profile file with [toolElements] in place of the elements of the same inspections.
   * Gives [text] when the file already has equal elements, and `null` when the text is not a profile.
   */
  private fun replaceToolElements(text: String, toolElements: List<Element>): String? {
    val root = try {
      JDOMUtil.load(text)
    }
    catch (e: JDOMException) {
      LOG.info("Cannot parse the inspection profile", e)
      return null
    }
    val profileElement = if (root.name == PROFILE_TAG) root else root.getChild(PROFILE_TAG) ?: return null
    var changed = false
    for (toolElement in toolElements) {
      val toolId = toolElement.getAttributeValue(CLASS_ATTRIBUTE)
      val oldToolElements = profileElement.getChildren(INSPECTION_TOOL_TAG)
      val oldElement = oldToolElements.firstOrNull { it.getAttributeValue(CLASS_ATTRIBUTE) == toolId }
      if (oldElement != null) {
        if (JDOMUtil.areElementsEqual(oldElement, toolElement)) continue
        profileElement.setContent(profileElement.indexOf(oldElement), toolElement)
      }
      else {
        // The profile manager writes the inspections sorted by name
        val nextElement = oldToolElements.firstOrNull { (it.getAttributeValue(CLASS_ATTRIBUTE) ?: "") > toolId }
        if (nextElement != null) {
          profileElement.addContent(profileElement.indexOf(nextElement), toolElement)
        }
        else {
          profileElement.addContent(toolElement)
        }
      }
      changed = true
    }
    return if (changed) JDOMUtil.write(root, "\n") else text
  }

  /** The text of the profile file, with LF line separators, from its document. */
  private fun readText(file: VirtualFile): String? = FileDocumentManager.getInstance().getDocument(file)?.text

  /** The commands that create the file at [path] with [text], and its absent parent directories. */
  private fun createFile(path: Path, text: String): ModCommand {
    val [ancestorPath, ancestor] = findExistingAncestor(path) ?: return ModCommand.nop()
    var command = ModCommand.nop()
    var parent = ancestor
    for (directory in ancestorPath.relativize(path).toList().dropLast(1)) {
      val futureDirectory = FutureVirtualFile(parent, directory.toString(), null)
      command = command.andThen(ModCreateFile(futureDirectory, ModCreateFile.Directory()))
      parent = futureDirectory
    }
    return command.andThen(ModCreateFile(futureFile(parent, path), ModCreateFile.Text(text)))
  }

  /** The virtual file for [path] under the [parent] directory, before the file exists in the virtual file system. */
  private fun futureFile(parent: VirtualFile, path: Path): FutureVirtualFile {
    val name = path.fileName.toString()
    return FutureVirtualFile(parent, name, FileTypeManager.getInstance().getFileTypeByFileName(name))
  }

  /** The nearest parent directory of [path] that the virtual file system has, as a path and as a virtual file. */
  private fun findExistingAncestor(path: Path): Pair<Path, VirtualFile>? {
    var directory = path.parent
    while (directory != null) {
      val file = VirtualFileManager.getInstance().findFileByNioPath(directory)
      if (file != null) return if (file.isDirectory) directory to file else null
      directory = directory.parent
    }
    return null
  }
}
