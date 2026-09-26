// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

interface UserPresentation {
  val username: @NlsSafe String
  val fullName: @NlsSafe String?
  val avatarIcon: Icon

  fun getPresentableName(): @NlsSafe String = fullName ?: username

  data class Simple(override val username: String,
                    override val fullName: String?,
                    override val avatarIcon: Icon) : UserPresentation
}
