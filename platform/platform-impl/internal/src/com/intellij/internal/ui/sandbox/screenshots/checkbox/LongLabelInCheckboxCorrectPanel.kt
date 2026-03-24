// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots.checkbox

/**
 * @author Konstantin Bulenkov
 */
internal class LongLabelInCheckboxCorrectPanel : OneSelectedCheckboxPanel(true, """<html>Insert selected suggestion by pressing<br/>space, dot, or other context-dependent keys</html>""") {
  override val screenshotSize = 756 x 384
  override val sreenshotRelativePath = "images/ui/checkbox/checkbox_label_long_correct.png"
}