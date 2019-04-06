// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.parsers

import com.intellij.debugger.memory.agent.ReferringObjectsInfo
import com.intellij.debugger.memory.agent.UnexpectedValueFormatException
import com.sun.jdi.ArrayReference
import com.sun.jdi.Value

object GcRootsPathsParser : ResultParser<ReferringObjectsInfo> {
  override fun parse(value: Value): ReferringObjectsInfo {
    if (value is ArrayReference) {
      if (value.length() != 2) throw UnexpectedValueFormatException("Array must represent 2 values: objects and backward references")
      val values = ObjectReferencesParser.parse(value.getValue(0))
      val backwardReferences = BackwardReferencesParser.parse(value.getValue(1))
      value.fillWithNulls()
      return ReferringObjectsInfo(values, backwardReferences)
    }
    throw UnexpectedValueFormatException("Array of arrays is expected")
  }

  private fun ArrayReference.fillWithNulls() {
    for (i in 0 until length()) {
      this.setValue(i, null)
    }
  }
}