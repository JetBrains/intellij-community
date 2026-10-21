// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.ide.ui.search.OptionDescription
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Unmodifiable
import java.util.function.Function

@ApiStatus.Experimental
fun interface ConfigurableWithOptionDescriptors {
  fun getOptionDescriptors(
    configurableId: String,
    nameConverter: Function<in @Nls String, @Nls String>,
  ): @Unmodifiable List<OptionDescription>
}
