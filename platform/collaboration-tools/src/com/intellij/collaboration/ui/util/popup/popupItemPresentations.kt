// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util.popup

import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface PopupItemPresentation {
  val shortText: @Nls String
  val icon: Icon?
  val fullText: @Nls String?

  class Simple(override val shortText: String,
               override val icon: Icon? = null,
               override val fullText: String? = null)
    : PopupItemPresentation

  class ToString(value: Any) : PopupItemPresentation {
    override val shortText: String = value.toString()
    override val icon: Icon? = null
    override val fullText: String? = null
  }
}

interface SelectablePopupItemPresentation {
  val icon: Icon?
  val shortText: @Nls String
  val fullText: @Nls String?
  val isSelected: Boolean

  data class Simple(override val shortText: String,
                    override val icon: Icon? = null,
                    override val fullText: String? = null,
                    override val isSelected: Boolean = false) : SelectablePopupItemPresentation
}