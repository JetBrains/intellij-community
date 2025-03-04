// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser.elements

import com.intellij.openapi.extensions.ExtensionDescriptor

enum class OS {
  MAC, LINUX, WINDOWS, UNIX, FREEBSD;

  companion object {
    // todo temporary migration util
    fun OS.convert(): ExtensionDescriptor.Os = when (this) {
      MAC -> ExtensionDescriptor.Os.mac
      LINUX -> ExtensionDescriptor.Os.linux
      WINDOWS -> ExtensionDescriptor.Os.windows
      UNIX -> ExtensionDescriptor.Os.unix
      FREEBSD -> ExtensionDescriptor.Os.freebsd
    }
  }
}
