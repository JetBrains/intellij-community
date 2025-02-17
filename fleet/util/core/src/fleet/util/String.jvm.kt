// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import java.util.Locale

@Actual("capitalizeWithCurrentLocale")
fun String.capitalizeWithCurrentLocaleJvm(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

@Actual("lowercaseWithCurrentLocale")
fun String.lowercaseWithCurrentLocaleJvm(): String = lowercase(Locale.getDefault())

@Actual("uppercaseWithCurrentLocale")
fun String.uppercaseWithCurrentLocaleJvm(): String = uppercase(Locale.getDefault())
