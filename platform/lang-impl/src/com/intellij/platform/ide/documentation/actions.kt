// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.documentation

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.backend.documentation.DocumentationTarget
import org.jetbrains.annotations.ApiStatus.Experimental

@JvmField
val DOCUMENTATION_TARGETS: DataKey<List<DocumentationTarget>> = DataKey.create("documentation.targets")

@Experimental
@JvmField
val DOCUMENTATION_BROWSER: DataKey<DocumentationBrowserFacade> = DataKey.create("documentation.browser")
