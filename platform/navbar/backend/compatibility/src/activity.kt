// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend.compatibility

import com.intellij.ide.ui.VirtualFileAppearanceListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import kotlinx.coroutines.channels.ProducerScope

fun ProducerScope<Unit>.fireOnFileChanges(project: Project) {
  // Just a Unit-returning shortcut
  fun fire() {
    trySend(Unit)
  }

  val connection = project.messageBus.connect(this)
  connection.subscribe(FileStatusListener.TOPIC, object : FileStatusListener {
    override fun fileStatusesChanged() = fire()
    override fun fileStatusChanged(virtualFile: VirtualFile) = fire()
  })
  connection.subscribe(ProblemListener.TOPIC, object : ProblemListener {
    override fun problemsAppeared(file: VirtualFile) = fire()
    override fun problemsDisappeared(file: VirtualFile) = fire()
  })
  connection.subscribe(VirtualFileAppearanceListener.TOPIC, VirtualFileAppearanceListener {
    fire()
  })
}
