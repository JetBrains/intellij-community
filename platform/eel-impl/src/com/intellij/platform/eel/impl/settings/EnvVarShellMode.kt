// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.settings

import com.intellij.platform.eel.impl.EelImplBundle

enum class EnvVarShellMode {
  LOGIN_INTERACTIVE {
    override fun toString(): String {
      return EelImplBundle.message("advanced.setting.container.environments.env.var.shell.mode.login.interactive")
    }
  },
  LOGIN_NON_INTERACTIVE {
    override fun toString(): String {
      return EelImplBundle.message("advanced.setting.container.environments.env.var.shell.mode.login.non.interactive")
    }
  },
}
