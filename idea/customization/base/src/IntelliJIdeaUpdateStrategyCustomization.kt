package com.intellij.idea.customization.base

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization

class IntelliJIdeaUpdateStrategyCustomization : UpdateStrategyCustomization() {
  override val showWhatIsNewPageAfterUpdate: Boolean
    get() = true
}