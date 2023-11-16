// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation.finder

import com.intellij.navigation.NavigationKeyPrefix

class SelectionFinder(
    private val selectionKey: NavigationKeyPrefix = NavigationKeyPrefix.SELECTION
) {
  sealed interface FindResult {
    class Success(val selections: List<Pair<LocationInFile, LocationInFile>>) : FindResult

    class Error(val message: String) : FindResult
  }

  fun find(parameters: Map<String, String?>): FindResult {
    val selections =
        parameters
            .filterKeys { it.startsWith(selectionKey.prefix) }
            .values
            .filterNotNull()
            .mapNotNull {
              val split = it.split('-')
              if (split.size != 2) return@mapNotNull null

              val startLocation = parseLocationInFile(split[0])
              val endLocation = parseLocationInFile(split[1])

              if (startLocation != null && endLocation != null) {
                return@mapNotNull Pair(startLocation, startLocation)
              }
              return@mapNotNull null
            }

    return FindResult.Success(selections)
  }

  private fun parseLocationInFile(range: String): LocationInFile? {
    val position = range.split(':')
    return if (position.size != 2) null
    else
        try {
          LocationInFile(position[0].toInt(), position[1].toInt())
        } catch (e: Exception) {
          null
        }
  }
}
