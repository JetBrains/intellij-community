// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.ContributedConfigurationBase.Companion.getTypeImpl
import com.intellij.execution.target.LanguageRuntimeType.Companion.EXTENSION_NAME

/**
 * Base class for configuration instances contributed by the ["com.intellij.executionTargetLanguageRuntimeType"][EXTENSION_NAME] extension point.
 *
 * Since different language configurations do not share any common bits, this is effectively a marker.
 */
abstract class LanguageRuntimeConfiguration(typeId: String) : ContributedConfigurationBase(typeId, EXTENSION_NAME)

fun <C : LanguageRuntimeConfiguration, T : LanguageRuntimeType<C>> C.getRuntimeType(): T = this.getTypeImpl()