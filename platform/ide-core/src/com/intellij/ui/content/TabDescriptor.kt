// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.TabTitle
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier
import javax.swing.JComponent

class TabDescriptor(val component: JComponent, private val displayNamePointer: Supplier<@TabTitle String>) : Disposable {
  @get:TabTitle
  val displayName: @TabTitle String
    get() = displayNamePointer.get()

  constructor(component: JComponent, @TabTitle displayName: String) : this(component, Supplier { displayName })

  constructor(component: JComponent, displayNamePointer: Supplier<@TabTitle String>, disposable: Disposable?) :
    this(component, displayNamePointer) {
    if (disposable != null) {
      Disposer.register(this, disposable)
    }
  }

  override fun dispose() {}

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TabDescriptor

    return component == other.component
  }

  override fun hashCode(): Int {
    return component.hashCode()
  }
}

class TabGroupId @JvmOverloads constructor(@NonNls val id: String,
                                           private val displayNamePointer: Supplier<@TabTitle String>,
                                           val splitByDefault: Boolean = false) {
  @get:TabTitle
  val displayName: @TabTitle String
    get() = displayNamePointer.get()

  constructor(@NonNls id: String, @TabTitle displayName: String) : this(id, Supplier { displayName })

  @TabTitle
  fun getDisplayName(tab: TabDescriptor): String {
    if (tab.displayName.isBlank()) return displayName
    return displayName + ": " + tab.displayName
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TabGroupId

    return id == other.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}