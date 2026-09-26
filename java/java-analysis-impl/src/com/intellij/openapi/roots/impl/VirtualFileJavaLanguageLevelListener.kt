// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.messages.Topic

public interface VirtualFileJavaLanguageLevelListener {
  public companion object {
    @JvmField
    @Topic.ProjectLevel
    public val TOPIC: Topic<VirtualFileJavaLanguageLevelListener> = Topic(VirtualFileJavaLanguageLevelListener::class.java)
  }

  public fun levelChanged(virtualFile: VirtualFile, newLevel: LanguageLevel?)
}