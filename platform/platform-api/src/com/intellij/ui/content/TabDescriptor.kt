// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import javax.swing.JComponent

class TabDescriptor(val component: JComponent, private val displayNamePointer: Supplier<@Nls String>) : Disposable {
  val displayName: @Nls String
    get() = displayNamePointer.get()

  constructor(component: JComponent, @Nls displayName: String) : this(component, Supplier { displayName })

  constructor(component: JComponent, displayNamePointer: Supplier<@Nls String>, disposable: Disposable?) :
    this(component, displayNamePointer) {
    if (disposable != null) {
      Disposer.register(this, disposable)
    }
  }

  override fun dispose() = Unit
  
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TabDescriptor

    if (component != other.component) return false

    return true
  }

  override fun hashCode(): Int {
    return component.hashCode()
  }
}

class TabGroupId(@NonNls val id: String, private val displayNamePointer: Supplier<@Nls String>) {
  val displayName: @Nls String
    get() = displayNamePointer.get()

  constructor(@NonNls id: String, @Nls displayName: String) : this(id, Supplier { displayName })

  @Nls
  fun getDisplayName(tab: TabDescriptor): String {
    if (tab.displayName.isBlank()) return displayName
    return displayName + ": " + tab.displayName
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TabGroupId

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}