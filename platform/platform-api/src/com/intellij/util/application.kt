// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager

val application: Application
  get() = ApplicationManager.getApplication()