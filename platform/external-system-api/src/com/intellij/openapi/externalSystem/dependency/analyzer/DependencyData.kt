// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

interface DependencyData {

  interface Module : DependencyData {

    val moduleName: String
  }

  interface Artifact : DependencyData {

    val scope: String

    val coordinate: Coordinates

    interface Coordinates {

      val groupId: String

      val artifactId: String

      val version: String
    }
  }
}