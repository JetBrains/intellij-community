// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk

interface IntelliJNewProjectWizardData {

  val sdkProperty: ObservableMutableProperty<Sdk?>
  val moduleNameProperty: ObservableMutableProperty<String>
  val contentRootProperty: ObservableMutableProperty<String>
  val moduleFileLocationProperty: ObservableMutableProperty<String>
  val addSampleCodeProperty: ObservableMutableProperty<Boolean>

  var sdk: Sdk?
  var moduleName: String
  var contentRoot: String
  var moduleFileLocation: String
  var addSampleCode: Boolean
  val generateOnboardingTips: Boolean
}