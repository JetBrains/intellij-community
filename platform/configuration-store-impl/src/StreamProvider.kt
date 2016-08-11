/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import org.jetbrains.annotations.TestOnly
import java.io.InputStream

interface StreamProvider {
  val enabled: Boolean
    get() = true

  fun isApplicable(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT) = true

  /**
   * @param fileSpec
   * @param content bytes of content, size of array is not actual size of data, you must use `size`
   * @param size actual size of data
   */
  fun write(fileSpec: String, content: ByteArray, size: Int = content.size, roamingType: RoamingType = RoamingType.DEFAULT)

  fun read(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT): InputStream?

  fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean)

  /**
   * Delete file or directory
   */
  fun delete(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT)
}

@TestOnly
fun StreamProvider.write(path: String, content: String) {
  write(path, content.toByteArray())
}

fun StreamProvider.write(path: String, content: BufferExposingByteArrayOutputStream, roamingType: RoamingType = RoamingType.DEFAULT) {
  write(path, content.internalBuffer, content.size(), roamingType)
}