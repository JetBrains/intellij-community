// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import java.util.concurrent.ConcurrentHashMap as JavaConcurrentHashMap

@Deprecated("Use [java.util.concurrent.ConcurrentHashMap] instead", replaceWith = ReplaceWith("ConcurrentHashMap<K, V>.newKeySet()",
                                                                                              "java.util.concurrent.ConcurrentHashMap"))
internal fun <K> ConcurrentHashSet(): MutableSet<K> = JavaConcurrentHashMap.newKeySet()
