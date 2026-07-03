// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.hotswap

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.ApiStatus


internal object HotSwapIncompatibilityReasons {
  fun methodAdded(className: String, methodId: HotSwapMethodId, methodShape: HotSwapMethodShape): @NlsSafe String =
    reasonWithDetail(JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.added.detail"),
                     methodId.present(className, methodShape))

  fun methodRemoved(className: String, methodId: HotSwapMethodId, methodShape: HotSwapMethodShape): @NlsSafe String =
    reasonWithDetail(JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.removed.detail"),
                     methodId.present(className, methodShape))

  fun fieldAdded(className: String, name: String, field: HotSwapFieldShape): @NlsSafe String =
    reasonWithDetail(JavaDebuggerBundle.message("hotswap.incompatibility.reason.field.added.detail"), presentField(className, name, field))

  fun fieldRemoved(className: String, name: String, field: HotSwapFieldShape): @NlsSafe String =
    reasonWithDetail(JavaDebuggerBundle.message("hotswap.incompatibility.reason.field.removed.detail"),
                     presentField(className, name, field))

  fun fieldTypeChanged(className: String, name: String, oldField: HotSwapFieldShape, currentField: HotSwapFieldShape): @NlsSafe String =
    reasonChangedFromTo(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.field.type.changed.detail"),
      oldValue = code(oldField.type),
      currentValue = code(currentField.type),
      subject = detail(presentFieldName(className, name)),
    )

  fun fieldModifiersChanged(
    className: String,
    name: String,
    oldField: HotSwapFieldShape,
    currentField: HotSwapFieldShape,
  ): @NlsSafe String =
    reasonWithSubjectAndDetail(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.field.modifiers.changed.detail"),
      detail(presentFieldName(className, name)),
      presentModifierChanges(oldField.modifiers, currentField.modifiers),
    )

  fun methodSignatureChanged(
    className: String,
    oldMethodId: HotSwapMethodId,
    oldMethodShape: HotSwapMethodShape,
    currentMethodId: HotSwapMethodId,
    currentMethodShape: HotSwapMethodShape,
  ): @NlsSafe String =
    reasonChangedFromTo(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.signature.changed.detail"),
      oldValue = oldMethodId.present(className, oldMethodShape),
      currentValue = currentMethodId.present(className, currentMethodShape),
    )

  fun methodReturnTypeChanged(
    className: String,
    methodId: HotSwapMethodId,
    oldMethod: HotSwapMethodShape,
    currentMethod: HotSwapMethodShape,
  ): @NlsSafe String =
    reasonChangedFromTo(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.return.type.changed.detail"),
      oldValue = code(oldMethod.returnType.orEmpty()),
      currentValue = code(currentMethod.returnType.orEmpty()),
      subject = detail(code(methodId.presentName(className), shortenTypeNames = false)),
    )

  fun classKindChanged(className: String, oldKind: String, currentKind: String): @NlsSafe String =
    reasonChangedFromTo(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.class.kind.changed.detail"),
      oldValue = code(oldKind, shortenTypeNames = false),
      currentValue = code(currentKind, shortenTypeNames = false),
      subject = detail(code(presentClassName(className), shortenTypeNames = false)),
    )

  fun classSupertypesChanged(className: String, oldSupers: Set<String>, currentSupers: Set<String>): @NlsSafe String =
    reasonChangedFromTo(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.class.supertypes.changed.detail"),
      oldValue = presentSet(oldSupers),
      currentValue = presentSet(currentSupers),
      subject = detail(code(presentClassName(className), shortenTypeNames = false)),
    )

  fun classInnerClassesChanged(
    className: String,
    oldInnerClasses: Set<String>,
    currentInnerClasses: Set<String>,
  ): @NlsSafe String =
    reasonWithSubjectAndDetail(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.class.inner.classes.changed.detail"),
      detail(code(presentClassName(className), shortenTypeNames = false)),
      presentClassNameChanges(oldInnerClasses, currentInnerClasses),
    )

  fun compilationProblems(fileName: String, line: Int): @NlsSafe String =
    htmlString {
      append(text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.compilation.problems.detail")))
      append(space())
      append(text(fileName))
      append(text(":"))
      append(text(line + 1))
    }

  fun classModifiersChanged(className: String, oldModifiers: Set<String>, currentModifiers: Set<String>): @NlsSafe String =
    reasonWithSubjectAndDetail(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.class.modifiers.changed.detail"),
      detail(code(presentClassName(className), shortenTypeNames = false)),
      presentModifierChanges(oldModifiers, currentModifiers),
    )

  fun methodModifiersChanged(
    className: String,
    methodId: HotSwapMethodId,
    oldMethod: HotSwapMethodShape,
    currentMethod: HotSwapMethodShape,
  ): @NlsSafe String =
    reasonWithSubjectAndDetail(
      JavaDebuggerBundle.message("hotswap.incompatibility.reason.method.modifiers.changed.detail"),
      detail(code(methodId.presentName(className), shortenTypeNames = false)),
      presentModifierChanges(oldMethod.modifiers, currentMethod.modifiers),
    )

  private fun presentField(className: String, name: String, field: HotSwapFieldShape): HtmlChunk = html {
    append(presentFieldName(className, name))
    append(text(": "))
    append(code(field.type))
  }

  private fun presentFieldName(className: String, name: String): HtmlChunk =
    code("${presentClassName(className)}.$name", shortenTypeNames = false)

  private fun HotSwapMethodId.present(className: String, methodShape: HotSwapMethodShape): HtmlChunk {
    val signature = presentSignature(className)
    val returnType = methodShape.returnType ?: return signature
    return html {
      append(signature)
      append(text(": "))
      append(code(returnType))
    }
  }

  private fun HotSwapMethodId.presentSignature(className: String): HtmlChunk {
    val signature: @NlsSafe String = "${presentName(className)}(${parameters.joinToString { shortenType(it) }})"
    return code(signature, shortenTypeNames = false)
  }

  private fun HotSwapMethodId.presentName(className: String): String {
    val simpleClassName = presentClassName(className)
    return if (isConstructor) simpleClassName else "$simpleClassName.$name"
  }

  private fun presentClassName(className: String): String = className.substringAfterLast('.')

  private fun presentModifierChanges(oldModifiers: Set<String>, currentModifiers: Set<String>): HtmlChunk {
    val removed = (oldModifiers - currentModifiers).sorted().map {
      html {
        append(code(it, shortenTypeNames = false))
        append(space())
        append(text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.removed")))
      }
    }
    val added = (currentModifiers - oldModifiers).sorted().map {
      html {
        append(code(it, shortenTypeNames = false))
        append(space())
        append(text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.added")))
      }
    }
    return joinTexts(removed + added)
  }

  private fun presentSet(values: Set<String>): HtmlChunk =
    if (values.isEmpty()) text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.none"))
    else joinTexts(values.sorted().map(::code))

  private fun presentClassNameChanges(oldNames: Set<String>, currentNames: Set<String>): HtmlChunk {
    val removed = (oldNames - currentNames).map { presentClassName(it) }.sorted().map {
      html {
        append(code(it, shortenTypeNames = false))
        append(space())
        append(text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.removed")))
      }
    }
    val added = (currentNames - oldNames).map { presentClassName(it) }.sorted().map {
      html {
        append(code(it, shortenTypeNames = false))
        append(space())
        append(text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.added")))
      }
    }
    return joinTexts(removed + added)
  }

  private fun joinTexts(values: List<HtmlChunk>): HtmlChunk =
    if (values.isEmpty()) text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.none"))
    else html {
      appendWithSeparators(text(", "), values)
    }

  private fun reasonWithDetail(label: String, detail: HtmlChunk): @NlsSafe String = htmlString {
    append(text(label))
    append(space())
    append(detail(detail))
  }

  private fun reasonChangedFromTo(
    label: String,
    oldValue: HtmlChunk,
    currentValue: HtmlChunk,
    subject: HtmlChunk? = null,
  ): @NlsSafe String = htmlString {
    append(text(label))
    if (subject != null) {
      append(space())
      append(subject)
    }
    append(space())
    append(text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.change.from")))
    append(space())
    append(oldValue)
    append(space())
    append(text(JavaDebuggerBundle.message("hotswap.incompatibility.reason.change.to")))
    append(space())
    append(currentValue)
  }

  private fun reasonWithSubjectAndDetail(label: String, subject: HtmlChunk, detail: HtmlChunk): @NlsSafe String = htmlString {
    append(text(label))
    append(space())
    append(subject)
    append(text(": "))
    append(detail)
  }

  private fun detail(value: HtmlChunk): HtmlChunk = html {
    br()
    append(value)
  }

  private fun text(value: @NlsSafe String): HtmlChunk = HtmlChunk.text(value)

  private fun text(value: Int): HtmlChunk = text(value.toString())

  private fun space(): HtmlChunk = text(" ")

  private fun html(builder: HtmlBuilder.() -> Unit): HtmlChunk =
    HtmlBuilder().apply(builder).toFragment()

  private fun htmlString(builder: HtmlBuilder.() -> Unit): @NlsSafe String =
    HtmlBuilder().apply(builder).toString()

  private fun code(value: @NlsSafe String, shortenTypeNames: Boolean = true): HtmlChunk {
    val text: @NlsSafe String = if (shortenTypeNames) shortenType(value) else value
    return HtmlChunk.text(text).wrapWith("code")
  }

  private fun shortenType(type: @NlsSafe String): @NlsSafe String =
    type.replace(qualifiedTypeRegex) { matchResult -> matchResult.value.substringAfterLast('.') }
}

private val qualifiedTypeRegex = Regex("\\b(?:[a-z_]\\w*\\.)+[A-Z]\\w*")


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
