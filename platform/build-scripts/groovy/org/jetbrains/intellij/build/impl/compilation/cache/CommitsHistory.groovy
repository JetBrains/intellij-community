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

  private Map<String, Set<String>> union(Map<String, Set<String>> map) {
    Map<String, Set<String>> union = [:]
    [commitsPerRemote, map].each {
      it.entrySet().each {
        def commitSet = (union[it.key] ?: []) as Set<String>
        commitSet += it.value as Set<String>
        union[it.key as String] = commitSet
      }
    }
    union
  }
}
