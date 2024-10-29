// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import com.intellij.ide.ui.UISettings
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@ApiStatus.Experimental
class LookupPresentation private constructor(
  val positionStrategy: LookupPositionStrategy,
  val mostRelevantOnTop: Boolean,
  val maxVisibleItemsProperty: ReadWriteProperty<LookupPresentation, Int>
) {
  var maxVisibleItemsCount: Int by maxVisibleItemsProperty

  override fun toString(): String {
    return "LookupPresentation(positionStrategy=$positionStrategy, mostRelevantOnTop=$mostRelevantOnTop, maxVisibleItemsCount=$maxVisibleItemsCount)"
  }


  class Builder(base: LookupPresentation?) {
    private var positionStrategy = base?.positionStrategy ?: LookupPositionStrategy.PREFER_BELOW
    private var mostRelevantOnTop = base?.mostRelevantOnTop ?: true
    private var maxVisibleItemsProperty = base?.maxVisibleItemsProperty ?: DefaultMaxVisibleItemsProperty()

    constructor() : this(null)

    /**
     * Default is [LookupPositionStrategy.PREFER_BELOW]. See [LookupPositionStrategy] for more details.
     */
    fun withPositionStrategy(strategy: LookupPositionStrategy): Builder {
      positionStrategy = strategy
      return this
    }

    /**
     * If true, the first selected item most probably will be on top of the popup, otherwise - in the bottom.
     * Default value is true.
     */
    fun withMostRelevantOnTop(onTop: Boolean): Builder {
      mostRelevantOnTop = onTop
      return this
    }

    /**
     * Allows specifying the maximum number of items to be displayed in the visible part of the lookup list.
     * This value can be adjusted by resizing the popup, so it is worth to store it in some place.
     * Default value is 11.
     */
    fun withMaxVisibleItemsCount(property: ReadWriteProperty<LookupPresentation, Int>): Builder {
      maxVisibleItemsProperty = property
      return this
    }

    fun build(): LookupPresentation {
      return LookupPresentation(positionStrategy, mostRelevantOnTop, maxVisibleItemsProperty)
    }

    private class DefaultMaxVisibleItemsProperty : ReadWriteProperty<LookupPresentation, Int> {
      override fun getValue(thisRef: LookupPresentation, property: KProperty<*>): Int {
        return UISettings.getInstance().maxLookupListHeight
      }

      override fun setValue(thisRef: LookupPresentation, property: KProperty<*>, value: Int) {
        UISettings.getInstance().maxLookupListHeight = max(5, value)
      }
    }
  }
}