// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import java.util.function.Function
import javax.swing.Icon

class DefaultPresentationIconUpdater : PresentationIconUpdater {
  override fun performUpdate(presentation: Presentation, iconTransformer: Function<Icon, Icon>) {
    updateAndSubscribe(presentation, iconTransformer, "icon", { it.icon }, { pst, icn -> pst.icon = icn })
    updateAndSubscribe(presentation, iconTransformer, "selectedIcon", { it.selectedIcon }, { pst, icn -> pst.selectedIcon = icn })
    updateAndSubscribe(presentation, iconTransformer, "hoveredIcon", { it.hoveredIcon }, { pst, icn -> pst.hoveredIcon = icn })
    updateAndSubscribe(presentation, iconTransformer, "disabledIcon", { it.disabledIcon }, { pst, icn -> pst.disabledIcon = icn })
  }

  private fun updateAndSubscribe(presentation: Presentation, iconTransformer: Function<Icon, Icon>, propName: String, getter: (Presentation) -> Icon?, setter: (Presentation, Icon) -> Unit) {
    getter(presentation)
      ?.let { transformWithEqualityCheck(it, iconTransformer) }
      ?.let { setter(presentation, it) }

    presentation.addPropertyChangeListener { event ->
      if (event.propertyName == propName) {
        (event.newValue as? Icon)
          ?.let { iconTransformer.apply(it) }
          ?.let { setter(presentation, it) }
      }
    }
  }

  private fun transformWithEqualityCheck(oldValue: Icon, iconTransformer: Function<Icon, Icon>): Icon? {
    val newValue = iconTransformer.apply(oldValue)
    return if (newValue != oldValue) newValue else null
  }
}
