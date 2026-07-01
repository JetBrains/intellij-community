// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.JavaDebuggerBundle
import org.jetbrains.annotations.ApiStatus


internal object HotSwapIncompatibilityReasons {
  fun methodAdded(className: String, methodId: HotSwapMethodId, methodShape: HotSwapMethodShape): String =
    JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.added.detail", methodId.present(className, methodShape))

  fun methodRemoved(className: String, methodId: HotSwapMethodId, methodShape: HotSwapMethodShape): String =
    JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.removed.detail", methodId.present(className, methodShape))

  fun fieldAdded(className: String, name: String, field: HotSwapFieldShape): String =
    JavaDebuggerBundle.message("hotswap.incompatibility.reason.field.added.detail", presentField(className, name, field))

  fun fieldRemoved(className: String, name: String, field: HotSwapFieldShape): String =
    JavaDebuggerBundle.message("hotswap.incompatibility.reason.field.removed.detail", presentField(className, name, field))

  fun fieldTypeChanged(className: String, name: String, oldField: HotSwapFieldShape, currentField: HotSwapFieldShape): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.field.type.changed.detail",
      presentFieldName(className, name),
      oldField.type,
      currentField.type,
    )

  fun fieldModifiersChanged(className: String, name: String, oldField: HotSwapFieldShape, currentField: HotSwapFieldShape): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.field.modifiers.changed.detail",
      presentFieldName(className, name),
      presentModifierChanges(oldField.modifiers, currentField.modifiers),
    )

  fun methodSignatureChanged(
    className: String,
    oldMethodId: HotSwapMethodId,
    oldMethodShape: HotSwapMethodShape,
    currentMethodId: HotSwapMethodId,
    currentMethodShape: HotSwapMethodShape,
  ): String = JavaDebuggerBundle.message(
    "hotswap.incompatibility.reason.method.signature.changed.detail",
    oldMethodId.present(className, oldMethodShape),
    currentMethodId.present(className, currentMethodShape),
  )

  fun methodReturnTypeChanged(
    className: String,
    methodId: HotSwapMethodId,
    oldMethod: HotSwapMethodShape,
    currentMethod: HotSwapMethodShape,
  ): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.method.return.type.changed.detail",
      methodId.presentName(className),
      oldMethod.returnType.orEmpty(),
      currentMethod.returnType.orEmpty(),
    )

  fun classKindChanged(className: String, oldKind: String, currentKind: String): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.class.kind.changed.detail",
      presentClassName(className),
      oldKind,
      currentKind,
    )

  fun classSupertypesChanged(className: String, oldSupers: Set<String>, currentSupers: Set<String>): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.class.supertypes.changed.detail",
      presentClassName(className),
      presentSet(oldSupers),
      presentSet(currentSupers),
    )

  fun classInnerClassesChanged(
    className: String,
    oldInnerClasses: Set<String>,
    currentInnerClasses: Set<String>,
  ): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.class.inner.classes.changed.detail",
      presentClassName(className),
      presentClassNameChanges(oldInnerClasses, currentInnerClasses),
    )

  fun compilationProblems(fileName: String, line: Int): String =
    JavaDebuggerBundle.message("hotswap.incompatibility.reason.compilation.problems.detail", fileName, line + 1)

  fun classModifiersChanged(className: String, oldModifiers: Set<String>, currentModifiers: Set<String>): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.class.modifiers.changed.detail",
      presentClassName(className),
      presentModifierChanges(oldModifiers, currentModifiers),
    )

  fun methodModifiersChanged(
    className: String,
    methodId: HotSwapMethodId,
    oldMethod: HotSwapMethodShape,
    currentMethod: HotSwapMethodShape,
  ): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.method.modifiers.changed.detail",
      methodId.presentName(className),
      presentModifierChanges(oldMethod.modifiers, currentMethod.modifiers),
    )

  private fun presentField(className: String, name: String, field: HotSwapFieldShape): String =
    JavaDebuggerBundle.message(
      "hotswap.incompatibility.reason.field.presentation",
      presentFieldName(className, name),
      field.type,
    )

  private fun presentFieldName(className: String, name: String): String = "${presentClassName(className)}.$name"

  private fun HotSwapMethodId.present(className: String, methodShape: HotSwapMethodShape): String {
    val returnType = methodShape.returnType
    val signature = "${presentName(className)}(${parameters.joinToString()})"
    return if (returnType == null) signature else {
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.presentation", signature, returnType)
    }
  }

  private fun HotSwapMethodId.presentName(className: String): String {
    val simpleClassName = presentClassName(className)
    return if (isConstructor) simpleClassName else "$simpleClassName.$name"
  }

  private fun presentClassName(className: String): String = className.substringAfterLast('.')

  private fun presentModifierChanges(oldModifiers: Set<String>, currentModifiers: Set<String>): String {
    val removed = (oldModifiers - currentModifiers).sorted()
      .map { JavaDebuggerBundle.message("hotswap.incompatibility.reason.modifier.removed", it) }
    val added = (currentModifiers - oldModifiers).sorted()
      .map { JavaDebuggerBundle.message("hotswap.incompatibility.reason.modifier.added", it) }
    val changes = removed + added
    return if (changes.isEmpty()) JavaDebuggerBundle.message("hotswap.incompatibility.reason.none") else changes.joinToString()
  }

  private fun presentSet(values: Set<String>): String =
    if (values.isEmpty()) JavaDebuggerBundle.message("hotswap.incompatibility.reason.none") else values.sorted().joinToString()

  private fun presentClassNameChanges(oldNames: Set<String>, currentNames: Set<String>): String {
    val removed = (oldNames - currentNames).map { presentClassName(it) }.sorted()
      .map { JavaDebuggerBundle.message("hotswap.incompatibility.reason.inner.class.removed", it) }
    val added = (currentNames - oldNames).map { presentClassName(it) }.sorted()
      .map { JavaDebuggerBundle.message("hotswap.incompatibility.reason.inner.class.added", it) }
    val changes = removed + added
    return if (changes.isEmpty()) JavaDebuggerBundle.message("hotswap.incompatibility.reason.none") else changes.joinToString()
  }
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
