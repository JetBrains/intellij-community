// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UpdateSession
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
import java.util.regex.Matcher
import java.util.regex.Pattern

class GroupMapping @JvmOverloads constructor(private val myShowNonPopupGroups: Boolean = false) : Comparable<GroupMapping?> {
  private val myPaths: MutableList<List<ActionGroup>> = ArrayList<List<ActionGroup>>()

  private var myBestGroupName: @ActionText String? = null
  private var myBestNameComputed = false

  fun addPath(path: List<ActionGroup>) {
    myPaths.add(path)
  }

  override fun compareTo(o: GroupMapping?): Int {
    return Comparing.compare<String?>(this.firstGroupName, o?.firstGroupName)
  }

  val bestGroupName: @ActionText String?
    get() {
      if (myBestNameComputed) return myBestGroupName
      return this.firstGroupName
    }

  val firstGroup: List<ActionGroup>?
    get() = myPaths.firstOrNull()

  private val firstGroupName: @Nls String?
    get() {
      val path = this.firstGroup
      return path?.let { getPathName(it) }
      return if (path != null) getPathName(path) else null
    }

  suspend fun updateBeforeShowSuspend(presentationProvider: suspend (AnAction) -> Presentation) {
    if (myBestNameComputed) return
    myBestNameComputed = true

    for (path in myPaths) {
      val name = getActualPathNameSuspend(path, presentationProvider)
      if (name != null) {
        myBestGroupName = name
        return
      }
    }
  }

  fun updateBeforeShow(session: UpdateSession) {
    if (myBestNameComputed) return
    myBestNameComputed = true

    for (path in myPaths) {
      val name = getActualPathName(path, session)
      if (name != null) {
        myBestGroupName = name
        return
      }
    }
  }

  val allGroupNames: List<String?>
    get() = myPaths.map { path: List<ActionGroup> -> getPathName(path) }

  private fun getPathName(path: List<ActionGroup>): @Nls String? {
    var name = ""
    for (group in path) {
      name = appendGroupName(name, group, group.getTemplatePresentation())
    }
    return StringUtil.nullize(name)
  }

  private suspend fun getActualPathNameSuspend(path: List<ActionGroup>, presentationProvider: suspend (AnAction) -> Presentation): @Nls String? {
    var name = ""
    for (group in path) {
      val presentation = presentationProvider(group)
      if (!presentation.isVisible()) return null
      name = appendGroupName(name, group, presentation)
    }
    return StringUtil.nullize(name)
  }

  private fun getActualPathName(path: List<ActionGroup>, session: UpdateSession): @Nls String? {
    var name = ""
    for (group in path) {
      val presentation = session.presentation(group)
      if (!presentation.isVisible()) return null
      name = appendGroupName(name, group, presentation)
    }
    return StringUtil.nullize(name)
  }

  private fun appendGroupName(
    prefix: @Nls String,
    group: ActionGroup,
    presentation: Presentation
  ): @Nls String {
    if (group.isPopup() || myShowNonPopupGroups) {
      val groupName: String? = getActionGroupName(presentation)
      if (!groupName.isNullOrBlank()) {
        return (if (prefix.isEmpty())
          groupName
        else
          prefix + " | " + groupName)
      }
    }
    return prefix
  }

  companion object {
    private val INNER_GROUP_WITH_IDS: Pattern = Pattern.compile("(.*) \\(\\d+\\)")

    @JvmStatic
    fun createFromText(text: @ActionText String?, showGroupText: Boolean): GroupMapping {
      val mapping = GroupMapping(showGroupText)
      mapping.addPath(listOf<ActionGroup>(DefaultActionGroup(text, false)))
      return mapping
    }

    private fun getActionGroupName(presentation: Presentation): @ActionText String? {
      val text = presentation.getText()
      if (text == null) return null

      val matcher: Matcher = INNER_GROUP_WITH_IDS.matcher(text)
      if (matcher.matches()) return matcher.group(1)

      return text
    }
  }
}
