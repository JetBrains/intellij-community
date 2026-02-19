// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.openapi.editor.impl.zombie.Necromancer
import com.intellij.openapi.editor.impl.zombie.NecromancerAwaker
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope


internal class VisualFormattingNecromancerAwaker : NecromancerAwaker<VisualFormattingZombie> {
  override fun awake(project: Project, coroutineScope: CoroutineScope): Necromancer<VisualFormattingZombie> {
    return VisualFormattingNecromancer(project, coroutineScope)
  }
}
