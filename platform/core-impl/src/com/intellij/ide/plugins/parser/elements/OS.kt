// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser.elements

import com.intellij.openapi.extensions.ExtensionDescriptor

enum class OS {
  MAC, LINUX, WINDOWS, UNIX, FREEBSD
}

// todo temporary migration util
fun OS.asExtensionOS(): ExtensionDescriptor.Os = when (this) {
  OS.MAC -> ExtensionDescriptor.Os.mac
  OS.LINUX -> ExtensionDescriptor.Os.linux
  OS.WINDOWS -> ExtensionDescriptor.Os.windows
  OS.UNIX -> ExtensionDescriptor.Os.unix
  OS.FREEBSD -> ExtensionDescriptor.Os.freebsd
}