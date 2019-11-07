/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.diagnostic.hprof.parser

import java.util.*

enum class Type(val typeId: Int, val size: Int, private val arrayName: String) {
  // Pointer sizes are dependent on the hprof file, so set it to 0 for now.
  OBJECT(2, 0, ""),
  BOOLEAN(4, 1, "[Z"),
  CHAR(5, 2, "[C"),
  FLOAT(6, 4, "[F"),
  DOUBLE(7, 8, "[D"),
  BYTE(8, 1, "[B"),
  SHORT(9, 2, "[S"),
  INT(10, 4, "[I"),
  LONG(11, 8, "[J");

  fun getClassNameOfPrimitiveArray(): String {
    if (this == OBJECT) {
      throw IllegalArgumentException("OBJECT type is not a primitive type")
    }
    return arrayName
  }

  companion object {
    private val sIdTypeMap = HashMap<Int, Type>()
    private val sNameTypeMap = HashMap<String, Type>()

    init {
      for (type in values()) {
        sIdTypeMap[type.typeId] = type
        sNameTypeMap[type.arrayName] = type
      }
    }

    fun getType(id: Int): Type {
      return sIdTypeMap[id]!!
    }

    fun getType(name: String): Type {
      return sNameTypeMap[name]!!
    }
  }
}

