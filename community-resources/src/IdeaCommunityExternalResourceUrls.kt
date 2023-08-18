// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.community.customization

import com.intellij.platform.ide.impl.customization.BaseJetBrainsExternalProductResourceUrls

class IdeaCommunityExternalResourceUrls : BaseJetBrainsExternalProductResourceUrls() {
  override val basePatchDownloadUrl: String
    get() = "https://download.jetbrains.com/idea/"

  override val youtrackProjectId: String
    get() = "IDEA"

  override val shortProductNameUsedInForms: String
    get() = "IDEA"
}