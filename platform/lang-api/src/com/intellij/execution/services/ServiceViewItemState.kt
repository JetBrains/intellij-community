// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services

data class ServiceViewItemState(val selected: Boolean,
                                val expanded: Boolean,
                                val leaf: Boolean,
                                val hasFocus: Boolean)