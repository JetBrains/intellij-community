// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.Nls

interface DependencyAnalyzerDependency : UserDataHolder {

  val data: Data

  val scope: Scope

  val parent: DependencyAnalyzerDependency?

  val status: List<Status>

  sealed interface Data : UserDataHolder {

    interface Module : Data {

      val name: @Nls String
    }

    interface Artifact : Data {

      val groupId: @Nls String

      val artifactId: @Nls String

      val version: @Nls String
    }
  }

  interface Scope : UserDataHolder {

    val name: @Nls String

    val title: @Nls(capitalization = Nls.Capitalization.Title) String
  }

  sealed interface Status : UserDataHolder {

    interface Omitted : Status

    interface Warning : Status {

      val message: @Nls String
    }
  }
}