// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation

import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.util.text.nullize

enum class NavigationKeyPrefix(val prefix: String) {
  PROJECT("project"),
  ORIGIN("origin"),
  FQN("fqn"),
  PATH("path"),
  FRAGMENT(JBProtocolCommand.FRAGMENT_PARAM_NAME),
  SELECTION("selection"),
  REVISION("revision"),
  PATH_LEFT("path_left"),
  PATH_RIGHT("path_right"),
  REVISION_LEFT("revision_left"),
  REVISION_RIGHT("revision_right");

  override fun toString(): String = prefix
}

fun Map<String, String?>.getNavigationKeyValue(key: NavigationKeyPrefix): String? {
  return get(key.prefix).nullize(nullizeSpaces = true)
}
