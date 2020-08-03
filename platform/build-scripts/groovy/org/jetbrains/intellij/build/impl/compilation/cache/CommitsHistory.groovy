// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import groovy.transform.CompileStatic

import java.lang.reflect.Type

@CompileStatic
class CommitsHistory {
  static final String JSON_FILE = 'commit_history.json'
  private static final Type JSON_TYPE = new TypeToken<Map<String, Set<String>>>() {}.getType()
  private final Map<String, Set<String>> commitsPerRemote

  CommitsHistory(Map<String, Set<String>> commitsPerRemote) {
    this.commitsPerRemote = commitsPerRemote
  }

  CommitsHistory(String json) {
    this(new Gson().fromJson(json, JSON_TYPE) as Map<String, Set<String>>)
  }

  String toJson() {
    new Gson().toJson(commitsPerRemote)
  }

  Collection<String> commitsForRemote(String remote) {
    commitsPerRemote[remote] ?: []
  }

  CommitsHistory plus(CommitsHistory other) {
    new CommitsHistory(union(other.commitsPerRemote))
  }

  CommitsHistory minus(CommitsHistory other) {
    new CommitsHistory(subtract(other.commitsPerRemote))
  }

  private Map<String, Set<String>> union(Map<String, Set<String>> map) {
    if (map.isEmpty()) return commitsPerRemote
    if (commitsPerRemote.isEmpty()) return map
    Map<String, Set<String>> union = [:]
    [commitsPerRemote, map].each {
      it.forEach { String remote, Set<String> commits ->
        def commitSet = (union[remote] ?: []) as Set<String>
        commitSet += commits
        union[remote] = commitSet
      }
    }
    return union
  }

  private Map<String, Set<String>> subtract(Map<String, Set<String>> map) {
    if (map.isEmpty()) return commitsPerRemote
    if (commitsPerRemote.isEmpty()) return commitsPerRemote
    Map<String, Set<String>> result = [:]
    commitsPerRemote.forEach { String remote, Set<String> commits ->
      result[remote] = (commits - map[remote] ?: []) as Set<String>
    }
    return result
  }
}
