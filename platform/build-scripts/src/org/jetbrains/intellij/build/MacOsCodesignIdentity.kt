// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

/**
 * Full name of a keychain identity (Applications > Utilities > Keychain Access).
 * More info in [SIGNING IDENTITIES](https://developer.apple.com/legacy/library/documentation/Darwin/Reference/ManPages/man1/codesign.1.html).
 */
data class MacOsCodesignIdentity(val value: String)
