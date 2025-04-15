// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask

interface IntelliJNewProjectWizardData {

  val sdkProperty: ObservableMutableProperty<Sdk?>

  var sdk: Sdk?

  val jdkIntentProperty: ObservableMutableProperty<ProjectWizardJdkIntent?>

  var jdkIntent: ProjectWizardJdkIntent?

  val sdkDownloadTaskProperty: ObservableMutableProperty<SdkDownloadTask?>

  var sdkDownloadTask: SdkDownloadTask?

  val moduleNameProperty: ObservableMutableProperty<String>

  var moduleName: String

  val contentRootProperty: ObservableMutableProperty<String>

  var contentRoot: String

  val moduleFileLocationProperty: ObservableMutableProperty<String>

  var moduleFileLocation: String

  val addSampleCodeProperty: ObservableMutableProperty<Boolean>

  var addSampleCode: Boolean

  @Deprecated("Use addSampleCodeProperty instead")
  val generateOnboardingTipsProperty: ObservableMutableProperty<Boolean>
    get() = addSampleCodeProperty

  @Deprecated("Use addSampleCode instead")
  val generateOnboardingTips: Boolean
    get() = addSampleCode
}