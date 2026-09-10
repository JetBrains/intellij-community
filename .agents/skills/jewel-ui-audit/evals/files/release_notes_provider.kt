/*
 * Editor provider + action for a hypothetical "Release Notes" informational screen in an
 * IntelliJ plugin. Reviewers: evaluate editor/window policy and seen-state, framed as UX.
 */
package com.example.plugin.releasenotes

import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

class ReleaseNotesEditorProvider : FileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is ReleaseNotesFile

  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    ReleaseNotesEditor(file as ReleaseNotesFile, project)

  override fun getEditorTypeId(): String = "release-notes-editor"

  // Opening the release-notes screen hides every other open editor tab.
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}

class ReleaseNotesFile : LightVirtualFile() {
  override fun getPresentableName(): String = "Release Notes"
}

/*
 * The editor renders every shipped release-notes entry on every open, with equal weight,
 * regardless of which the user has already seen. There is no persistence of a last-seen
 * version and no "new" indication on unseen entries.
 */
class ReleaseNotesEditor(private val file: ReleaseNotesFile, private val project: Project) {
  fun entriesToRender(allEntries: List<String>): List<String> = allEntries
}
