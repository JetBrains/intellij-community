// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual

@Actual("capitalizeWithCurrentLocale")
fun String.capitalizeWithCurrentLocaleWasmJs(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Actual("lowercaseWithCurrentLocale")
fun String.lowercaseWithCurrentLocaleWasmJs(): String = lowercase()

@Actual("uppercaseWithCurrentLocale")
fun String.uppercaseWithCurrentLocaleWasmJs(): String = uppercase()
