// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.parsers

import com.intellij.debugger.memory.agent.ReferringObjectsInfo
import com.intellij.debugger.memory.agent.UnexpectedValueFormatException
import com.sun.jdi.ArrayReference
import com.sun.jdi.Value

object GcRootsPathsParser : ResultParser<ReferringObjectsInfo> {
  override fun parse(value: Value): ReferringObjectsInfo {
    if (value is ArrayReference) {
      val referringObjectsInfo = ObjectsReferencesInfoParser.parse(value)
      value.fillWithNulls()
      return referringObjectsInfo
    }
    throw UnexpectedValueFormatException("Array of arrays is expected")
  }

  private fun ArrayReference.fillWithNulls() {
    for (i in 0 until length()) {
      this.setValue(i, null)
    }
  }
}