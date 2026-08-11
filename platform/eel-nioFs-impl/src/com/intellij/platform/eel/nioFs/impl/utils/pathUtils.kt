// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.nioFs.impl.utils

import com.intellij.openapi.util.io.FileAttributes
import com.intellij.platform.eel.fs.EelFileInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun EelFileInfo.Type.Directory.getCaseSensitivity(): FileAttributes.CaseSensitivity {
  return when (sensitivity) {
    EelFileInfo.CaseSensitivity.SENSITIVE -> FileAttributes.CaseSensitivity.SENSITIVE
    EelFileInfo.CaseSensitivity.INSENSITIVE -> FileAttributes.CaseSensitivity.INSENSITIVE
    EelFileInfo.CaseSensitivity.UNKNOWN -> FileAttributes.CaseSensitivity.UNKNOWN
  }
}