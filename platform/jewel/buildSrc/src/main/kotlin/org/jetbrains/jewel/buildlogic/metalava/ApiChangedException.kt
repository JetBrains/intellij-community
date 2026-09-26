// Forked from https://github.com/autonomousapps/dependency-analysis-gradle-plugin
// Copyright (c) 2025. Tony Robalik.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.jewel.buildlogic.metalava

import org.gradle.api.GradleException

class ApiChangedException(msg: String, cause: Throwable? = null) : GradleException(msg, cause)
