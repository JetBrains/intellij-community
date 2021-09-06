// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.ui.dsl.builder.Cell

interface NewProjectWizardSdkData {

  val sdkComboBox: Cell<JdkComboBox>

  val sdkProperty: GraphProperty<Sdk?>

  val sdk: Sdk?
}