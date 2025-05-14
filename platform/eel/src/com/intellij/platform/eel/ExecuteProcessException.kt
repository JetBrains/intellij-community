// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import java.io.IOException

class ExecuteProcessException(val errno: Int, override val message: String) : EelError, IOException()