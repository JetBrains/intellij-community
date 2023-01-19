// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import org.jetbrains.annotations.Nls
import javax.swing.Icon

class FailedIdeVersion(id: String, icon: Icon, name: String, subName: String? = null,
                       @Nls val potentialReason: String? = null,
                       @Nls var stepsToFix: String? = null, val canBeRetried: Boolean = true,
                       val throwable: Throwable? = null) : BaseIdeVersion(id, icon, name, subName)