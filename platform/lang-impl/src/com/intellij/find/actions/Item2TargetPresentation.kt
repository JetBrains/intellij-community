// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.codeInsight.navigation.getColoredAttributes
import com.intellij.codeInsight.navigation.getLocationText
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.TargetPopupPresentation
import com.intellij.openapi.editor.markup.TextAttributes
import javax.swing.Icon

internal class Item2TargetPresentation(private val itemPresentation: ItemPresentation) : TargetPopupPresentation {

  override val icon: Icon? get() = itemPresentation.getIcon(false)

  override val presentableText: String get() = itemPresentation.presentableText ?: ""

  override val presentableAttributes: TextAttributes? get() = itemPresentation.getColoredAttributes()

  override val locationText: String? get() = itemPresentation.getLocationText()
}
