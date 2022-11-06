// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.openapi.util.NlsSafe
import javax.swing.Action

fun Action.getName(): @NlsSafe String = (getValue(Action.NAME) as? String).orEmpty()