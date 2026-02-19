/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.intellij.diagnostic.hprof.util

import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import com.intellij.diagnostic.hprof.classstore.ClassStore
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object RefIndexUtil {

  fun getFieldDescription(refIndex: Int,
                          classDefinition: ClassDefinition?,
                          classStore: ClassStore): String? {
    return when (refIndex) {
      ROOT -> "(root)"
      SOFT_REFERENCE -> "(soft)"
      WEAK_REFERENCE -> "(weak)"
      ARRAY_ELEMENT -> "[]"
      DISPOSER_CHILD -> "(disposer-tree)"
      FIELD_OMITTED -> null
      else -> classDefinition?.getRefField(classStore, refIndex - 1)?.name ?: "(field_$refIndex)"
    }
  }

  const val FIELD_OMITTED: Int = 0
  const val MAX_FIELD_INDEX: Int = 250

  const val ROOT: Int = 251
  const val SOFT_REFERENCE: Int = 252
  const val WEAK_REFERENCE: Int = 253
  const val ARRAY_ELEMENT: Int = 254
  const val DISPOSER_CHILD: Int = 255
}