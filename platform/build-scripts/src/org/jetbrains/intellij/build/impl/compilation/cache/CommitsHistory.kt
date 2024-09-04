// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation.cache

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CommitsHistory(private val commitsPerRemote: Map<String, Set<String>>) {
  companion object {
    internal const val JSON_FILE = "commit_history.json"
  }

  constructor(json: String) : this(Json.decodeFromString<Map<String, Set<String>>>(json))

  fun toJson(): String {
    return Json.encodeToString(commitsPerRemote)
  }

  fun commitsForRemote(remote: String): Collection<String> {
    return commitsPerRemote[remote] ?: emptyList()
  }

  operator fun plus(other: CommitsHistory): CommitsHistory {
    return CommitsHistory(union(other.commitsPerRemote))
  }

  operator fun minus(other: CommitsHistory): CommitsHistory {
    return CommitsHistory(subtract(other.commitsPerRemote))
  }

  private fun union(map: Map<String, Set<String>>): Map<String, Set<String>> {
    if (map.isEmpty()) return commitsPerRemote
    if (commitsPerRemote.isEmpty()) return map
    val union = HashMap<String, Set<String>>(commitsPerRemote)
    map.forEach { (remote, commits) ->
      union.compute(remote) { _, old -> if (old != null) old + commits else commits }
    }
    return union
  }

  private fun subtract(map: Map<String, Set<String>>): Map<String, Set<String>> {
    if (map.isEmpty()) return commitsPerRemote
    if (commitsPerRemote.isEmpty()) return commitsPerRemote
    return commitsPerRemote.mapValues { (remote, commits) ->
      val remove = map[remote]
      if (remove != null) (commits - remove) else commits
    }
  }
}
