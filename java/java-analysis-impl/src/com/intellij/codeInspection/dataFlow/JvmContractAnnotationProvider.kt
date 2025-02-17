// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow

import com.intellij.openapi.extensions.ExtensionPointName

interface JvmContractAnnotationProvider {
  val fqn: String

  companion object {
    private val EP_NAME: ExtensionPointName<JvmContractAnnotationProvider> =
      ExtensionPointName.Companion.create<JvmContractAnnotationProvider>("com.intellij.codeInsight.contractProvider")

    @JvmStatic
    fun qualifiedNames(): List<String> {
      return EP_NAME.extensionList.map { it.fqn }
    }

    @JvmStatic
    fun isMethodContract(fqn: String): Boolean {
      return qualifiedNames().any { it == fqn }
    }
  }
}