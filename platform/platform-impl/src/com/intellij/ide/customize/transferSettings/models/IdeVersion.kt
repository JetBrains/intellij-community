// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.ide.customize.transferSettings.providers.TransferSettingsProvider
import java.util.*
import javax.swing.Icon

class IdeVersion(id: String,
                 icon: Icon,
                 name: String,
                 subName: String? = null,
                 val settings: Settings,
                 val lastUsed: Date? = null,
                 val provider: TransferSettingsProvider) : BaseIdeVersion(id, icon, name, subName)