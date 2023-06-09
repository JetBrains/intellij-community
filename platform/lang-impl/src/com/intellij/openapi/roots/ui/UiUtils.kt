// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UiUtils")

package com.intellij.openapi.roots.ui

import com.intellij.openapi.ui.ComboBox
import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.observable.util.whenItemSelected as whenItemSelectedImpl

@Deprecated("Use function from platform API", ReplaceWith("whenItemSelected(listener)", "com.intellij.openapi.observable.util.whenItemSelected"))
@ApiStatus.ScheduledForRemoval
fun <E> ComboBox<E>.whenItemSelected(listener: (E) -> Unit): Unit = whenItemSelectedImpl(listener = listener)
