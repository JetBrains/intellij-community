// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.configurationStore.OLD_NAME_CONVERTER
import com.intellij.icons.AllIcons
import com.intellij.lang.impl.modcommand.ModCommandExecutorImpl
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.FutureVirtualFile
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.modcommand.Presentation
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.profile.codeInspection.PROFILE_DIR
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<DisableInspectionToolModCommandAction>()

private const val PROFILE_TAG = "profile"
private const val INSPECTION_TOOL_TAG = "inspection_tool"
private const val CLASS_ATTRIBUTE = "class"

/**
 * Disables an inspection in the current inspection profile, for the file of the action context.
 *
 * Unlike [DisableInspectionToolAction], this action does not change the profile in memory.
 * It produces a [ModCommand] that changes the profile file. The command changes only the `inspection_tool` element of the
 * inspection, and creates the file if it is absent. The profile manager must reload the profile from the changed file.
 *
 * The inspection is disabled the same way as [ToolsImpl.disableTool] does it: in the first scope that contains the file,
 * or in the default state otherwise. Without scopes, the whole inspection is disabled.
 *
 * @param profileFileLocator finds the file of the current profile, or gives `null` when the action is not available
 */
@ApiStatus.Internal
class DisableInspectionToolModCommandAction(
  private val toolId: String,
  private val profileFileLocator: (Project, InspectionProfileImpl) -> ProfileFile?,
) : ModCommandAction, DumbAware {
  /**
   * Creates the action for the project profile of a directory-based project.
   * The action is not available for an application profile.
   */
  constructor(toolId: String) : this(toolId, ::findProjectProfileFile)

  constructor(key: HighlightDisplayKey) : this(key.shortName)

  /**
   * The file that stores an inspection profile.
   *
   * @param path the path of the file; the file and its parent directories can be absent
   * @param profileName the profile name to write into the file when the action creates it
   */
  data class ProfileFile(val path: Path, val profileName: String)

  override fun getFamilyName(): String = DisableInspectionToolAction.getNameText()

  override fun getPresentation(context: ActionContext): Presentation? {
    val project = context.project()
    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    val profileFile = profileFileLocator(project, profile) ?: return null
    if (findExistingAncestor(profileFile.path) == null) return null
    val toolWrapper = profile.getInspectionTool(toolId, project)
    if (toolWrapper != null && toolWrapper.defaultLevel.isNonSwitchable) return null
    val tools = profile.getToolsOrNull(toolId, project)
    if (tools != null && !tools.isEnabled(context.file())) return null
    return Presentation.of(familyName).withIcon(AllIcons.Actions.Cancel)
  }

  override fun perform(context: ActionContext): ModCommand {
    val project = context.project()
    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    val profileFile = profileFileLocator(project, profile) ?: return ModCommand.nop()
    val tools = profile.getToolsOrNull(toolId, project) ?: return ModCommand.nop()
    if (!tools.isEnabled(context.file())) return ModCommand.nop()
    val disabledTools = tools.copy(project)
    disabledTools.disableTool(context.file())
    val toolElement = Element(INSPECTION_TOOL_TAG).setAttribute(CLASS_ATTRIBUTE, toolId)
    disabledTools.writeExternal(toolElement)

    val path = profileFile.path
    if (Files.isRegularFile(path)) {
      val file = VirtualFileManager.getInstance().findFileByNioPath(path)
      val oldText = readText(file, path)
                    ?: return ModCommand.error(InspectionsBundle.message("disable.inspection.cannot.read.profile", path))
      val newText = replaceToolElement(oldText, toolElement)
                    ?: return ModCommand.error(InspectionsBundle.message("disable.inspection.cannot.read.profile", path))
      // The profile in memory can be older than the file, and the file can already disable the inspection
      if (newText == oldText) return ModCommand.nop()
      // The virtual file system can miss a file that exists, such as a file which is not in the index of a language server
      val target = file ?: findExistingAncestor(path)?.let { (ancestorPath, ancestor) -> futureFile(ancestor, ancestorPath, path) }
                   ?: return ModCommand.nop()
      return ModUpdateFileText(target, oldText, newText, ModCommandExecutorImpl.calculateFragments(oldText, newText))
    }
    val profileElement = Element(PROFILE_TAG).setAttribute("version", "1.0")
    profileElement.addContent(Element("option").setAttribute("name", "myName").setAttribute("value", profileFile.profileName))
    profileElement.addContent(toolElement)
    val root = Element("component").setAttribute("name", "InspectionProjectProfileManager").addContent(profileElement)
    return createFile(profileFile.path, JDOMUtil.write(root, "\n"))
  }

  /**
   * The text of the profile file with [toolElement] in place of the element of the same inspection.
   * Gives [text] when the file already has an equal element, and `null` when the text is not a profile.
   */
  private fun replaceToolElement(text: String, toolElement: Element): String? {
    val root = try {
      JDOMUtil.load(text)
    }
    catch (e: JDOMException) {
      LOG.info("Cannot parse the inspection profile", e)
      return null
    }
    val profileElement = if (root.name == PROFILE_TAG) root else root.getChild(PROFILE_TAG) ?: return null
    val toolElements = profileElement.getChildren(INSPECTION_TOOL_TAG)
    val oldElement = toolElements.firstOrNull { it.getAttributeValue(CLASS_ATTRIBUTE) == toolId }
    if (oldElement != null) {
      if (JDOMUtil.areElementsEqual(oldElement, toolElement)) return text
      profileElement.setContent(profileElement.indexOf(oldElement), toolElement)
    }
    else {
      // The profile manager writes the inspections sorted by name
      val nextElement = toolElements.firstOrNull { (it.getAttributeValue(CLASS_ATTRIBUTE) ?: "") > toolId }
      if (nextElement != null) {
        profileElement.addContent(profileElement.indexOf(nextElement), toolElement)
      }
      else {
        profileElement.addContent(toolElement)
      }
    }
    return JDOMUtil.write(root, "\n")
  }

  /** The text of the profile file, with LF line separators, from its document or from the disk. */
  private fun readText(file: VirtualFile?, path: Path): String? {
    if (file != null) {
      return FileDocumentManager.getInstance().getDocument(file)?.text ?: StringUtil.convertLineSeparators(VfsUtilCore.loadText(file))
    }
    return try {
      StringUtil.convertLineSeparators(EelFiles.readString(path))
    }
    catch (e: IOException) {
      LOG.info("Cannot read the inspection profile $path", e)
      null
    }
  }

  /** The commands that create the file at [path] with [text], and its absent parent directories. */
  private fun createFile(path: Path, text: String): ModCommand {
    val (ancestorPath, ancestor) = findExistingAncestor(path) ?: return ModCommand.nop()
    var command = ModCommand.nop()
    var parent = ancestor
    var parentPath = ancestorPath
    for (directory in ancestorPath.relativize(path.parent)) {
      parentPath = parentPath.resolve(directory)
      val futureDirectory = FutureVirtualFile(parent, directory.toString(), null)
      if (!Files.isDirectory(parentPath)) {
        command = command.andThen(ModCreateFile(futureDirectory, ModCreateFile.Directory()))
      }
      parent = futureDirectory
    }
    val fileName = path.fileName.toString()
    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
    return command.andThen(ModCreateFile(FutureVirtualFile(parent, fileName, fileType), ModCreateFile.Text(text)))
  }

  /** The virtual file for [path] under the [ancestor] directory at [ancestorPath], which the virtual file system misses. */
  private fun futureFile(ancestor: VirtualFile, ancestorPath: Path, path: Path): VirtualFile {
    var file = ancestor
    val names = ancestorPath.relativize(path).toList()
    for ((index, name) in names.withIndex()) {
      val fileType = if (index == names.lastIndex) FileTypeManager.getInstance().getFileTypeByFileName(name.toString()) else null
      file = FutureVirtualFile(file, name.toString(), fileType)
    }
    return file
  }

  /** The nearest parent directory of [path] that the virtual file system has, as a path and as a virtual file. */
  private fun findExistingAncestor(path: Path): Pair<Path, VirtualFile>? {
    var directory = path.parent
    while (directory != null) {
      val file = VirtualFileManager.getInstance().findFileByNioPath(directory)
      if (file != null) return directory to file
      directory = directory.parent
    }
    return null
  }
}

private fun findProjectProfileFile(project: Project, profile: InspectionProfileImpl): DisableInspectionToolModCommandAction.ProfileFile? {
  if (!profile.isProjectLevel || !project.isDirectoryBased) return null
  val configDir = project.stateStore.directoryStorePath ?: return null
  val path = configDir.resolve(PROFILE_DIR).resolve(OLD_NAME_CONVERTER(profile.name) + ".xml")
  return DisableInspectionToolModCommandAction.ProfileFile(path, profile.name)
}
