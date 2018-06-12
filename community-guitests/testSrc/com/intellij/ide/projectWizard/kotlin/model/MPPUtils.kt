// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

enum class MppProjectStructure(private val title: String){
  RootEmptyModule("Root empty module with common & platform children"),
  RootCommonModule("Root common module with children platform modules")
  ;

  override fun toString() = title
}