// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.detekt.rules

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtPureElement

private val jewelRootFqName = FqName("org.jetbrains.jewel")

internal fun KtPureElement.isJewelSymbol(): Boolean {
    val file = containingKtFile
    val packageFqName = file.packageFqName

    // Allow files in the exact Jewel package or its subpackages
    if (packageFqName == jewelRootFqName || packageFqName.isSubpackageOf(jewelRootFqName)) {
        return true
    }

    // Allow files with no package declaration (for test code)
    if (packageFqName.isRoot) {
        return true
    }

    return false
}
