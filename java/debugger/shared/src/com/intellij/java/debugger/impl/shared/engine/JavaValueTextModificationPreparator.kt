// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.engine

import com.intellij.java.debugger.impl.shared.SharedDebuggerUtils
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueTextModificationPreparator
import com.intellij.xdebugger.frame.XValueTextModificationPreparatorProvider
import com.intellij.xdebugger.impl.ui.XValueTextProvider

/**
 * Transforms `the "world"` into `"the \"world\""`.
 */
fun convertToJavaStringLiteral(text: String): String {
  return StringUtil.wrapWithDoubleQuote(SharedDebuggerUtils.translateStringValue(text))
}

private class JavaValueTextModificationPreparatorProvider : XValueTextModificationPreparatorProvider {
  override fun getTextValuePreparator(value: XValue): XValueTextModificationPreparator? {
    if (value.xValueDescriptorAsync?.getNow(null)?.kind == JAVA_VALUE_KIND &&
        (value as? XValueTextProvider)?.shouldShowTextValue() == true &&
        value.modifier != null) {

      return object : XValueTextModificationPreparator {
        override fun convertToStringLiteral(text: String) = convertToJavaStringLiteral(text)
      }
    }
    return null
  }
}
