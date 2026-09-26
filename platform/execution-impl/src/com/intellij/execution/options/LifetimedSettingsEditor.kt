package com.intellij.execution.options

import com.intellij.openapi.options.SettingsEditor
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.SequentialLifetimes
import javax.swing.JComponent

abstract class LifetimedSettingsEditor<Settings> : SettingsEditor<Settings>() {
  private val lifetimeDefinition = Lifetime.Eternal.createNested()
  protected val editorLifetime = SequentialLifetimes(lifetimeDefinition.lifetime)

  override fun disposeEditor() {
    lifetimeDefinition.terminate()
    super.disposeEditor()
  }

  override fun createEditor(): JComponent {
    val lifetime = editorLifetime.next()
    return createEditor(lifetime)
  }

  abstract fun createEditor(lifetime: Lifetime): JComponent
}