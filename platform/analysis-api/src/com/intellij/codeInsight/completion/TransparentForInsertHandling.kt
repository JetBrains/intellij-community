// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import org.jetbrains.annotations.ApiStatus

/**
 * Mark a [com.intellij.codeInsight.lookup.LookupElementDecorator] as transparent for insert handling.
 * In this case, remote-development machinery will be able to properly pick up the insert handler of the decorated element.
 * Otherwise,
 */
//TODO IJPL-207762 mark experimental
@ApiStatus.Internal
interface TransparentForInsertHandling