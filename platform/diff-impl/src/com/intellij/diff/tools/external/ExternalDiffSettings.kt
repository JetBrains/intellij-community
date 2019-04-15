// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.OptionTag

@State(name = "ExternalDiffSettings", storages = [Storage(DiffUtil.DIFF_CONFIG)])
class ExternalDiffSettings : BaseState(), PersistentStateComponent<ExternalDiffSettings> {
  override fun getState(): ExternalDiffSettings = this
  override fun loadState(state: ExternalDiffSettings) {
    copyFrom(state)
  }

  @get:OptionTag("DIFF_ENABLED")
  var isDiffEnabled: Boolean by property(false)

  @get:OptionTag("DIFF_DEFAULT")
  var isDiffDefault: Boolean by property(false)

  @get:OptionTag("DIFF_EXE_PATH")
  var diffExePath: String by nonNullString()

  @get:OptionTag("DIFF_PARAMETERS")
  var diffParameters: String by nonNullString("%1 %2 %3")

  @get:OptionTag("MERGE_ENABLED")
  var isMergeEnabled: Boolean by property(false)

  @get:OptionTag("MERGE_EXE_PATH")
  var mergeExePath: String by nonNullString()

  @get:OptionTag("MERGE_PARAMETERS")
  var mergeParameters: String by nonNullString("%1 %2 %3 %4")

  @get:OptionTag("MERGE_TRUST_EXIT_CODE")
  var isMergeTrustExitCode: Boolean by property(false)

  private fun nonNullString(initialValue: String = "") = property(initialValue, { it == initialValue })

  companion object {
    @JvmStatic
    val instance: ExternalDiffSettings
      get() = service()
  }
}
