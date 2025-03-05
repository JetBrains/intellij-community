// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims


import fleet.util.multiplatform.Actual
import java.util.concurrent.ConcurrentHashMap as JavaConcurrentHashMap


@Actual("ConcurrentHashSet")
internal fun <K> ConcurrentHashSetJvm(): MutableSet<K> = JavaConcurrentHashMap.newKeySet()