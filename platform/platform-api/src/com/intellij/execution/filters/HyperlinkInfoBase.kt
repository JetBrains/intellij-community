/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.filters

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.MouseEvent

abstract class HyperlinkInfoBase : HyperlinkInfo {
  abstract fun navigate(project: Project, hyperlinkLocationPoint: RelativePoint?)

  override fun navigate(project: Project) {
    navigate(project, null)
  }
}

/**
 * A helper to call the correct `navigate` overload of HyperlinkInfo.
 * 
 * If this [HyperlinkInfo] is a [HyperlinkInfoBase] and both [editor] and [logicalPosition] are not `null`,
 * then [HyperlinkInfoBase]'s `navigate` is called, otherwise, the base [HyperlinkInfo.navigate] is called.
 */
@ApiStatus.Experimental
fun HyperlinkInfo.navigate(
  project: Project,
  editor: Editor?,
  logicalPosition: LogicalPosition?,
) {
  if (this is HyperlinkInfoBase && editor != null && logicalPosition != null) {
    val point: Point = editor.logicalPositionToXY(logicalPosition)
    val event = MouseEvent(editor.getContentComponent(), 0, 0, 0, point.x, point.y, 1, false)
    navigate(project, RelativePoint(event))
  }
  else {
    navigate(project)
  }
}
