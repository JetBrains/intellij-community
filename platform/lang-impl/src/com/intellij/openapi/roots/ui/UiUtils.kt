// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UiUtils")

package com.intellij.openapi.roots.ui

import com.intellij.openapi.ui.ComboBox
import org.jetbrains.annotations.ApiStatus
import java.awt.event.*
import javax.swing.text.JTextComponent
import com.intellij.openapi.observable.util.whenItemSelected as whenItemSelectedImpl
import com.intellij.openapi.observable.util.whenTextChanged as whenTextModifiedImpl

@Deprecated("Use function from platform API", ReplaceWith("whenItemSelected(listener)", "com.intellij.openapi.observable.util.whenItemSelected"))
@ApiStatus.ScheduledForRemoval
fun <E> ComboBox<E>.whenItemSelected(listener: (E) -> Unit) = whenItemSelectedImpl(listener = listener)
@Deprecated("Use function from platform API", ReplaceWith("whenTextChanged { listener() }", "com.intellij.openapi.observable.util.whenTextChanged"))
@ApiStatus.ScheduledForRemoval
fun JTextComponent.whenTextModified(listener: () -> Unit) = whenTextModifiedImpl { listener() }
