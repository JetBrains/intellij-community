// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

abstract class BaseIdeVersion(
  val id: String,
  val icon: Icon,
  @NlsSafe val name: String,
  @NlsSafe val subName: String? = null
)