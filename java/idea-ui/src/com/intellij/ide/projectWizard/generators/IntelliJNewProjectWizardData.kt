// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.openapi.projectRoots.Sdk

interface IntelliJNewProjectWizardData {
  var sdk: Sdk?
  var moduleName: String
  var contentRoot: String
  var moduleFileLocation: String
  var addSampleCode: Boolean
}