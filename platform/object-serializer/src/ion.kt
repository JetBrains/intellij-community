// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonReader
import com.amazon.ion.IonType
import com.amazon.ion.IonWriter

internal inline fun IonWriter.list(writer: () -> Unit) {
  stepIn(IonType.LIST)
  writer()
  stepOut()
}

internal inline fun <T> IonReader.list(reader: () -> T): T {
  stepIn()
  val result = reader()
  stepOut()
  return result
}