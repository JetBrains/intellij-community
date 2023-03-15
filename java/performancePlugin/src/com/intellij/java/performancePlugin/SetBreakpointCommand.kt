// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin

import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand
import org.jetbrains.annotations.NonNls
import java.io.IOException

class SetBreakpointCommand(text: String, line: Int) : AbstractCallbackBasedCommand(text, line, true) {
  override fun execute(callback: ActionCallback, context: PlaybackContext) {
    val lineNumber = extractCommandArgument(PREFIX).toInt() - 1
    val project = context.project
    val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
    if (selectedEditor == null) {
      callback.reject("No opened editor")
    }
    val filePath = "file://" + selectedEditor!!.file.path
    val breakpointType = XBreakpointType.EXTENSION_POINT_NAME.findExtension(JavaLineBreakpointType::class.java)!!
    WriteAction.runAndWait<IOException> {
      val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
      VirtualFileManager.getInstance().refreshAndFindFileByUrl(filePath)
      val breakpoint = breakpointManager.addLineBreakpoint(breakpointType, filePath, lineNumber,
                                                           breakpointType.createBreakpointProperties(selectedEditor.file, lineNumber))
      breakpointManager.updateBreakpointPresentation(breakpoint, null, null)
    }
    callback.setDone()
  }

  companion object {
    private val LOG = Logger.getInstance(SetBreakpointCommand::class.java)
    const val PREFIX: @NonNls String = CMD_PREFIX + "setBreakpoint"
  }
}