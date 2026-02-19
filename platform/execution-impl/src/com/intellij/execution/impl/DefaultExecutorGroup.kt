// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.executors.RunExecutorSettings

abstract class DefaultExecutorGroup<Settings : RunExecutorSettings> : ExecutorGroup<Settings>() {

}