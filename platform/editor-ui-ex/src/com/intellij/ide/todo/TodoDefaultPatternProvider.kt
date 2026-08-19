// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoPattern
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TodoDefaultPatternProvider {

  companion object {
    @JvmStatic
    fun getInstance(): TodoDefaultPatternProvider = service()
  }

  fun getDefaultPatterns(): Array<TodoPattern>
  fun tryGetExternalTodoPatterns(project: Project): Array<out TodoPattern>?
}

internal class DefaultTodoDefaultPatternProvider : TodoDefaultPatternProvider {
  override fun getDefaultPatterns(): Array<TodoPattern> {
    return arrayOf(
      TodoPattern("\\btodo\\b.*", TodoAttributesUtil.createDefault(), false),
      TodoPattern("\\bfixme\\b.*", TodoAttributesUtil.createDefault(), false),
    )
  }

  override fun tryGetExternalTodoPatterns(project: Project): Array<out TodoPattern>? {
    return null
  }
}
