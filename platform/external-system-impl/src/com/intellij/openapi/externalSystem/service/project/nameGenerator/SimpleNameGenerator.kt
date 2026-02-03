// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.nameGenerator

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SimpleNameGenerator private constructor(
  private val group: String?,
  private val name: String,
  private val delimiter: String,
) {

  fun generate(): Iterable<String> {
    val names = ArrayList<String>()
    names.add(name)
    if (group != null && !name.startsWith(group)) {
      names.add(group + delimiter + name)
    }
    return names
  }

  companion object {

    @JvmStatic
    fun generate(group: String?, name: String, delimiter: String): Iterable<String> {
      return SimpleNameGenerator(group, name, delimiter).generate()
    }
  }
}