// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.kdbx

internal object KdbxAttributeNames {
  const val protected = "Protected"
}

internal object KdbxEntryElementNames {
  const val title = "Title"
  const val userName = "UserName"
  const val password = "Password"

  const val value = "Value"
  const val key = "Key"

  const val string = "String"
}

class IncorrectMainPasswordException(val isFileMissed: Boolean = false) : RuntimeException()

internal interface KeePassCredentials {
  val key: ByteArray
}

internal class KdbxException(message: String) : RuntimeException(message)
