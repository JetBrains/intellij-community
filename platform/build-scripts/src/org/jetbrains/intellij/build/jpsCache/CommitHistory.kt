// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jpsCache

import kotlinx.serialization.json.Json

internal const val COMMIT_HISTORY_JSON_FILE = "commit_history.json"

class CommitHistory(private val commitsPerRemote: Map<String, Set<String>>) {
  constructor(json: String) : this(Json.decodeFromString<Map<String, Set<String>>>(json))

  fun toJson(): String {
    return Json.encodeToString(commitsPerRemote)
  }

  fun commitsForRemote(remote: String): Collection<String> {
    return commitsPerRemote[remote] ?: emptyList()
  }

  operator fun plus(other: CommitHistory): CommitHistory {
    return CommitHistory(union(other.commitsPerRemote))
  }

  operator fun minus(other: CommitHistory): CommitHistory {
    return CommitHistory(subtract(other.commitsPerRemote))
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
