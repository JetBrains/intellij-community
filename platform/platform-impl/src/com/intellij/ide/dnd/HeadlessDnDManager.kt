// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.dnd

import com.intellij.openapi.Disposable
import java.awt.Component
import javax.swing.JComponent

internal class HeadlessDnDManager : DnDManager() {
  override fun unregisterSource(source: DnDSource, component: JComponent) {
  }

  override fun unregisterSource(source: AdvancedDnDSource) {
  }

  override fun registerTarget(target: DnDTarget?, component: JComponent?) {
  }

  override fun registerTarget(target: DnDTarget, component: JComponent, parentDisposable: Disposable) {
  }

  override fun unregisterTarget(target: DnDTarget?, component: JComponent?) {
  }

  override fun registerSource(source: DnDSource, component: JComponent) {
  }

  override fun registerSource(source: DnDSource, component: JComponent, parentDisposable: Disposable) {
  }

  override fun registerSource(source: AdvancedDnDSource) {
  }

  override fun getLastDropHandler(): Component? = null
}