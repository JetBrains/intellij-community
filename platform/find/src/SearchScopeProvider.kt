// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.psi.search.SearchScope

//TODO("Not yet implemented")
object SearchScopeProvider {
  private val scopes = mapOf<String, Int>()

  fun getScopeId(scopeName: String): Int? = scopes[scopeName]
  fun getScopeById(id: Int): SearchScope? {
    TODO("Not yet implemented")
  }
}