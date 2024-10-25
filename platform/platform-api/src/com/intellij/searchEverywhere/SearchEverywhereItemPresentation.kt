// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere

import javax.swing.Icon

sealed interface SearchEverywhereItemPresentation {}

data class ActionItemPresentation(
  val icon: Icon? = null,
  val name: String,
  val location: String? = null,
  val switcherState: Boolean? = null,
  val isEnabled: Boolean = true,
  val shortcut: String? = null,
): SearchEverywhereItemPresentation

data class OptionItemPresentation(
  val icon: Icon? = null,
  val name: String,
  val location: String? = null,
  val switcherState: Boolean? = null,
  val isEnabled: Boolean = true
): SearchEverywhereItemPresentation
