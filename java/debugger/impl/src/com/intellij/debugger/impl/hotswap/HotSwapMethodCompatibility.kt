// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.JavaDebuggerBundle
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object HotSwapIncompatibilityReasons {
  fun methodAdded(): String = JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.added")

  fun methodRemoved(): String = JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.removed")

  fun signatureModified(): String = JavaDebuggerBundle.message("hotswap.incompatibility.reason.signature.modified")

  fun structureModified(): String = JavaDebuggerBundle.message("hotswap.incompatibility.reason.structure.modified")

  fun compilationProblems(): String = JavaDebuggerBundle.message("hotswap.incompatibility.reason.compilation.problems")

  fun classModifiersChanged(): String = JavaDebuggerBundle.message("hotswap.incompatibility.reason.class.modifiers.changed")

  fun methodModifiersChanged(): String = JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.modifiers.changed")
}


@ApiStatus.Internal
data class HotSwapClassShape(
  val name: String,
  val kind: String,
  val modifiers: Set<String>,
  val supers: Set<String>,
  val innerClasses: Set<String>,
  val fields: Map<String, HotSwapFieldShape>,
  val methods: Map<HotSwapMethodId, HotSwapMethodShape>,
)

@ApiStatus.Internal
data class HotSwapFieldShape(val type: String, val modifiers: Set<String>)


@ApiStatus.Internal
data class HotSwapMethodId(val name: String, val isConstructor: Boolean, val parameters: List<String>)

@ApiStatus.Internal
data class HotSwapMethodShape(val returnType: String?, val modifiers: Set<String>)
