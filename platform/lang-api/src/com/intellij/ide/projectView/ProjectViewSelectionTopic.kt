// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView

import com.intellij.util.messages.Topic

@JvmField val PROJECT_VIEW_SELECTION_TOPIC: Topic<ProjectViewSelectionListener> = Topic(ProjectViewSelectionListener::class.java)
interface ProjectViewSelectionListener {
  fun onChanged()
}