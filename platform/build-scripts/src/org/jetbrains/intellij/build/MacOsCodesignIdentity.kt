// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

/**
 * Full name of a keychain identity (Applications > Utilities > Keychain Access).
 * More info in the `SIGNING IDENTITIES` section of the `man codesign` Terminal command output.
 */
data class MacOsCodesignIdentity(val value: String)
