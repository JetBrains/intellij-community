// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.system.IonReaderBuilder
import com.amazon.ion.system.IonTextWriterBuilder
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.inputStream
import com.intellij.util.io.outputStream
import java.nio.file.Paths

class TestApp {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val inputFile = Paths.get(args[0])
      val outFile = inputFile.parent.resolve(FileUtilRt.getNameWithoutExtension(inputFile.fileName.toString()) + "-text.ion")
      IonReaderBuilder.standard().build(inputFile.inputStream().buffered()).use { reader ->
        IonTextWriterBuilder.pretty().build(outFile.outputStream().buffered()).use { writer ->
          reader.next()
          writer.writeValue(reader)
        }
      }
    }
  }
}