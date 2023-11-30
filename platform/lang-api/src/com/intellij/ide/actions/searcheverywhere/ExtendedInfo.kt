// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnAction

open class ExtendedInfo {
  var leftText: (Any) -> String?
  var rightAction: (Any) -> AnAction?

  constructor(leftText: (Any) -> String?, rightAction: (Any) -> AnAction?) {
    this.leftText = leftText
    this.rightAction = rightAction
  }

  constructor() {
    leftText = fun(_: Any) = null
    rightAction = fun(_: Any?): AnAction? = null
  }
}