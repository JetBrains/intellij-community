// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginGraph

private const val ALIAS_NODE_PREFIX = "__alias__:"

/** Synthetic plugin node name used for product-bundled alias targets. */
fun aliasNodeName(alias: PluginId): TargetName = TargetName("$ALIAS_NODE_PREFIX${alias.value}")
