// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.nameGenerator

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class NumericNameGenerator private constructor(
  private val name: String,
) {

  fun generate(): Iterable<String> {
    return Iterable {
      object : Iterator<String> {

        private var current: Int = 0

        override fun hasNext(): Boolean {
          return current < MAX_NUMBER_SEQ
        }

        override fun next(): String {
          current++
          return "$name~$current"
        }
      }
    }
  }

  companion object {
    private const val MAX_NUMBER_SEQ = 5

    @JvmStatic
    fun generate(name: String): Iterable<String> {
      return NumericNameGenerator(name).generate()
    }
  }
}