// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.openapi.Disposable
import com.intellij.util.messages.Topic

interface ProjectExtraDataPusher {
  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<ProjectExtraDataPusher> = Topic.create("LangLevelInfoProvider", ProjectExtraDataPusher::class.java)
  }

  fun syncStarted(accessor: ProjectExtraDataAccessor, parentDisposable: Disposable)

  fun syncFinished()

  interface ProjectExtraDataAccessor {
    fun getExtraData(key: String): String?

    fun setExtraData(key: String, value: String?)

    fun isDisposed(): Boolean
  }
}