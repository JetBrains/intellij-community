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
package com.intellij.diagnostic.hprof.navigator

import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RootReason
private constructor(val description: String, val javaFrame: Boolean = false) {

  companion object {
    fun createConstantReferenceReason(classDefinition: ClassDefinition, constantNumber: Int): RootReason {
      return RootReason("Class constant: ${classDefinition.name}.#$constantNumber")
    }

    fun createStaticFieldReferenceReason(classDefinition: ClassDefinition, staticFieldName: String): RootReason {
      return RootReason("Static field: ${classDefinition.name}.$staticFieldName")
    }

    fun createClassDefinitionReason(classDefinition: ClassDefinition): RootReason {
      return RootReason("Class definition: ${classDefinition.name}")
    }

    fun createJavaFrameReason(frameDescription: String): RootReason {
      return RootReason("Java Frame: $frameDescription", true)
    }

    val rootUnknown: RootReason = RootReason(
      "Unknown")
    val rootGlobalJNI: RootReason = RootReason(
      "Global JNI")
    val rootLocalJNI: RootReason = RootReason(
      "Local JNI")
    val rootNativeStack: RootReason = RootReason(
      "Native stack")
    val rootStickyClass: RootReason = RootReason(
      "Sticky class")
    val rootThreadBlock: RootReason = RootReason(
      "Thread block")
    val rootThreadObject: RootReason = RootReason(
      "Thread object")
    val rootMonitorUsed: RootReason = RootReason(
      "Monitor used")
  }
}


