// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.PositionTracker
import java.awt.Component
import java.awt.Point

interface LocationListener {
  fun locationChanged(component: Component)
}

class LocationTracker(private val component: Component): Disposable {
  private val subscribers = ArrayList<LocationListener>()

  private val client = object : PositionTracker.Client<Component> {
    override fun dispose() {
    }

    override fun revalidate() {
      revalidate(tracker)
    }

    override fun revalidate(tracker: PositionTracker<Component>) {
      subscribers.forEach{ listener -> listener.locationChanged(tracker.component)}
    }

  }

  private val tracker = object : PositionTracker<Component>(component) {
    override fun recalculateLocation(visibleArea: Component): RelativePoint = RelativePoint(component, Point(0, 0))
  }

  init {
    tracker.init(client)
    Disposer.register(this, tracker)
    Disposer.register(this, client)
  }

  fun subscribe(locationListener: LocationListener) {
    subscribers.add(locationListener)
  }

  fun subscribe(locationListener: () -> Unit) {
    val listener = object : LocationListener {
      override fun locationChanged(component: Component) {
        locationListener()
      }
    }
    subscribers.add(listener)
  }

  fun fireLocationEvent(){
    client.revalidate()
  }

  override fun dispose() {
  }
}