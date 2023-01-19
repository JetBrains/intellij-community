// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.openapi.actionSystem.DependentTransientComponent
import com.intellij.openapi.ui.Queryable
import com.intellij.ui.components.JBList
import java.awt.Component

/**
 * A [JBList] which delegates its [data][com.intellij.openapi.actionSystem.DataProvider] and [Queryable] requests to [contextComponent].
 */
internal class ContextJBList<E>(
  private val contextComponent: Component,
) : JBList<E>(),
    DependentTransientComponent,
    Queryable {

  override fun getPermanentComponent(): Component {
    return contextComponent
  }

  override fun putInfo(info: MutableMap<in String, in String>) {
    if (contextComponent is Queryable) {
      contextComponent.putInfo(info)
    }
  }
}
