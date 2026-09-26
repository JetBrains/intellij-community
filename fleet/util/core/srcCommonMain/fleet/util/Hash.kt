// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

fun hash(vararg values: Any?): Int = values.fold(1) { acc, value -> 31 * acc + (value?.hashCode() ?: 0) }
