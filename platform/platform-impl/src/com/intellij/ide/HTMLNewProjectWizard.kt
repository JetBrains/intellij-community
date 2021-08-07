// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.util.PlatformUtils

class HTMLNewProjectWizard : NewProjectWizard<HTMLSettings> {
  override val language: String = "HTML"
  override var settingsFactory = { HTMLSettings() }

  override fun enabled() = PlatformUtils.isCommunityEdition()
}

class HTMLSettings