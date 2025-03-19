// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlayHintsProviderSwitch : InlayHintsSwitch {
  override fun isEnabled(project: Project): Boolean {
    return InlayHintsSettings.instance().hintsEnabledGlobally()
  }

  override fun setEnabled(project: Project, value: Boolean) {
    InlayHintsSettings.instance().setEnabledGlobally(value)
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
    DeclarativeInlayHintsPassFactory.resetModificationStamp()
    InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
  }
}