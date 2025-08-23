// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.configuration

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
class CommandCompletionConfigurableProvider : ConfigurableProvider() {

  override fun createConfigurable(): Configurable? {
    //now, only for intellij
    if (!PlatformUtils.isIntelliJ()) return null
    return CommandCompletionConfigurable()
  }

  class CommandCompletionConfigurable : UiDslUnnamedConfigurable.Simple(), Configurable {
    override fun getDisplayName(): String {
      return CodeInsightBundle.message("options.command.completion.display.name")
    }

    override fun Panel.createContent() {
      val settings = ApplicationCommandCompletionService.getInstance()

      group(CodeInsightBundle.message("options.command.completion.display.name")) {
        row {
          checkBox(CodeInsightBundle.message("options.command.completion.enabled"))
            .bindSelected({ settings.state.isEnabled() },
                          { r -> settings.state.setEnabled(r) })
            .gap(RightGap.SMALL)
          contextHelp(CodeInsightBundle.message("options.command.completion.display.comment"))
            .gap(RightGap.SMALL)
          icon(AllIcons.General.Beta)
        }
        if (GroupedCompletionContributor.isGroupEnabledInApp()) {
          indent {
            row {
              checkBox(CodeInsightBundle.message("options.command.completion.show.group"))
                .bindSelected({ settings.state.useGroup },
                              { r -> settings.state.useGroup = r })
                .gap(RightGap.SMALL)
            }
          }
        }
      }
    }
  }
}