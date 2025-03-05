// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual

@Actual("capitalizeWithCurrentLocale")
fun String.capitalizeWithCurrentLocaleWasm(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Actual("lowercaseWithCurrentLocale")
fun String.lowercaseWithCurrentLocaleWasm(): String = lowercase()

@Actual("uppercaseWithCurrentLocale")
fun String.uppercaseWithCurrentLocaleWasm(): String = uppercase()
