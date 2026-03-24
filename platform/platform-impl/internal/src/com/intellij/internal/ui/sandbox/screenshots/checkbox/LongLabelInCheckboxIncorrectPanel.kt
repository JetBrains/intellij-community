// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots.checkbox

/**
 * @author Konstantin Bulenkov
 */
internal class LongLabelInCheckboxIncorrectPanel : OneSelectedCheckboxPanel(false, """<html>Insert selected suggestion by pressing<br/>space, dot, or other context-dependent<br/>keys. Suggestions will appear as you type<br/>and can help you complete words and<br/>phrases more quickly</html>""") {
  override val screenshotSize = 756 x 384
  override val sreenshotRelativePath = "images/ui/checkbox/checkbox_label_long_incorrect.png"
}