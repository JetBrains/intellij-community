// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelPathResultImpl")
package com.intellij.platform.eel.path

internal data class Ok<P : EelPath>(override val path: P) : EelPathResult.Ok<P>
internal data class Err<P : EelPath>(override val raw: String, override val reason: String) : EelPathResult.Err<P>