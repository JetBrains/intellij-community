// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.parsers

import com.sun.jdi.PrimitiveValue
import com.sun.jdi.Value

class LongValueParser : ResultParser<Long> {
  override fun parse(value: Value): Long {
    if (value is PrimitiveValue) {
      return value.longValue()
    }

    throw IllegalArgumentException("Unexpected argument. Primitive value is expected")
  }
}