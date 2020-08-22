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
package com.intellij.diagnostic.hprof.visitors

import com.intellij.diagnostic.hprof.parser.HProfVisitor
import com.intellij.diagnostic.hprof.parser.RecordType
import it.unimi.dsi.fastutil.longs.Long2ObjectMap

internal class CollectStringValuesVisitor(val output: Long2ObjectMap<String>) : HProfVisitor() {
  override fun preVisit() {
    disableAll()
    enable(RecordType.StringInUTF8)
  }

  override fun visitStringInUTF8(id: Long, s: String) {
    assert(output.get(id) == null)
    output.put(id, s)
  }
}