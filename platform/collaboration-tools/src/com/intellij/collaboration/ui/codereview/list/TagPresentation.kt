// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import org.jetbrains.annotations.Nls
import java.awt.Color

interface TagPresentation {
  val name: @Nls String
  val color: Color?

  data class Simple(override val name: String, override val color: Color?) : TagPresentation
}
