// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.utils

import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.ScreenUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.rider.util.lifetime.Lifetime
import com.jetbrains.rider.util.reactive.IPropertyView
import com.jetbrains.rider.util.reactive.ISource
import com.jetbrains.rider.util.reactive.Property
import com.jetbrains.rider.util.reactive.map
import java.awt.GraphicsDevice
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.event.AncestorEvent


fun <T, K> IPropertyView<T>.switchMap(f: (T) -> IPropertyView<K>) = object: IPropertyView<K> {

  val deduplicator = Property(value)

  override val change: ISource<K> = object : ISource<K> {
    override fun advise(lifetime: Lifetime, handler: (K) -> Unit) {
      //todo make it optimal way via MultiplexingProperty
      this@switchMap.view(lifetime) { innerLt, v ->
        f(v).advise(innerLt) { deduplicator.value = it }
      }
      deduplicator.advise(lifetime, handler)
    }

  }
  override val value: K
    get() = f(this@switchMap.value).value
}

fun <T> proxyProperty(valueProducer: () -> T, onAdvise: (lifetime: Lifetime, apply: () -> Unit) -> Unit) = object: IPropertyView<T> {
  override val change: ISource<T> = object : ISource<T> {
    override fun advise(lifetime: Lifetime, handler: (T) -> Unit) = onAdvise(lifetime) { handler(value) }
  }

  override val value: T
    get() = valueProducer()
}

fun JComponent.window(): IPropertyView<Window?> = proxyProperty({UIUtil.getWindow(this@window)},
                                                                {lifetime, apply ->
      val listener = object : AncestorListenerAdapter() {
        override fun ancestorAdded(event: AncestorEvent) {
          apply()
        }

        override fun ancestorRemoved(event: AncestorEvent) {
          apply()
        }
      }

      lifetime.bracket(
        {this@window.addAncestorListener(listener)},
        {this@window.removeAncestorListener(listener)}
      )
    }
)

fun Window.currentScreen(): IPropertyView<GraphicsDevice?> = proxyProperty(
  {if(!this@currentScreen.isShowing || !this@currentScreen.isDisplayable) null else ScreenUtil.getScreenDevice(this@currentScreen.bounds)},
  {lifetime, apply ->
        val lt = lifetime.createNestedDef()

        val windowCloseListener = object : WindowAdapter() {
          override fun windowClosed(e: WindowEvent) {
            lt.terminate()
          }
        }

        val screenListener = object : ComponentAdapter() {
          override fun componentMoved(e: ComponentEvent) {
            apply()
          }

          override fun componentResized(e: ComponentEvent) {
            apply()
          }
        }

        this@currentScreen.addWindowListener(windowCloseListener)
        this@currentScreen.addComponentListener(screenListener)

        lt.lifetime.add {
          this@currentScreen.removeWindowListener(windowCloseListener)
          this@currentScreen.removeComponentListener(screenListener)
        }
      }
)

fun Window.screenInfo() = currentScreen()
  .map {it?.defaultConfiguration?.let { conf -> ScreenInfo(conf.bounds, ScreenUtil.getScreenRectangle(conf))}}

fun JComponent.screenInfo() = window()
  .switchMap { it?.screenInfo() ?: Property(null) }



