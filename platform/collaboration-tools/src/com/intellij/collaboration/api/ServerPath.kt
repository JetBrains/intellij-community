// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.api

import com.intellij.openapi.util.NlsSafe
import java.net.URI

interface ServerPath {

  fun toURI(): URI

  @NlsSafe
  override fun toString(): String
}