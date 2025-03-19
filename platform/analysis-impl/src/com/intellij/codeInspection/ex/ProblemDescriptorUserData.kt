// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.openapi.util.Key

@JvmField
val FINGERPRINT_DATA = Key.create<Map<String, String>>("problem.fingerprint")
