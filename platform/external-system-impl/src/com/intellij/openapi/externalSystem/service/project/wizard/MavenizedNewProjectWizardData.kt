// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.wizard

import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.observable.properties.GraphProperty

interface MavenizedNewProjectWizardData<Data : Any> {

  val parentProperty: GraphProperty<DataView<Data>>

  var parent: DataView<Data>

  var parentData: Data?

  val groupIdProperty: GraphProperty<String>

  var groupId: String

  val artifactIdProperty: GraphProperty<String>

  var artifactId: String

  val versionProperty: GraphProperty<String>

  var version: String
}