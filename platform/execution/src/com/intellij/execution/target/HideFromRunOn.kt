// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import org.jetbrains.annotations.ApiStatus

/**
 * Allows to hide the particular [TargetEnvironmentType] from the choices in "Run on" combobox of Run Configurations.
 */
@ApiStatus.Experimental
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class HideFromRunOn