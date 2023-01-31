// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions


import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.lang.documentation.DocumentationResult
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.tag
import com.intellij.openapi.util.text.HtmlChunk.text
import org.jetbrains.annotations.Nls
import javax.swing.Icon

data class VMOption(
  val optionName: @NlsSafe String,
  val type: @NlsSafe String?,
  val defaultValue: @NlsSafe String?,
  val kind: VMOptionKind,
  val doc: @NlsSafe String?,
  val variant: VMOptionVariant
) : Symbol, Pointer<VMOption>, DocumentationTarget {
  override fun dereference(): VMOption = this

  override fun createPointer(): Pointer<out VMOption> = this

  override fun presentation(): TargetPresentation {
    return TargetPresentation.builder(variant.prefix() + optionName).presentation()
  }

  override fun computeDocumentation(): DocumentationResult {
    val rows = linkedMapOf<@Nls String, HtmlChunk>()
    rows[JavaBundle.message("vm.option.description.option")] = text(variant.prefix() + optionName)
    val requiresSuffix = if (kind.unlockOption() == null) ""
    else (JavaBundle.message("vm.option.description.requires", kind.unlockOption()))
    rows[JavaBundle.message("vm.option.description.category")] = text(kind.presentation() + requiresSuffix)
    if (type != null) {
      rows[JavaBundle.message("vm.option.description.type")] = text(type)
    }
    if (defaultValue != null) {
      rows[JavaBundle.message("vm.option.description.default.value")] = text(defaultValue)
    }
    if (doc != null) {
      rows[JavaBundle.message("vm.option.description.description")] = text(doc)
    }
    val table = tag("table")
      .children(rows.map { (title, content) ->
        val titleCell = text("$title ").bold().wrapWith(tag("td").attr("align", "right").attr("valign", "top"))
        val contentCell = content.wrapWith("td")
        tag("tr").children(titleCell, contentCell)
      })
    return DocumentationResult.Companion.documentation(table.toString())
  }
}

enum class VMOptionVariant {
  DASH,
  DASH_DASH,
  XX,
  X;

  fun prefix(): @NlsSafe String = when (this) {
    DASH -> "-"
    DASH_DASH -> "--"
    X -> "-X"
    XX -> "-XX:"
  }
}

enum class VMOptionKind {
  Product,
  Diagnostic,
  Experimental;

  fun presentation(): @Nls String = when (this) {
    Product -> JavaBundle.message("vm.option.description.product")
    Diagnostic -> JavaBundle.message("vm.option.description.diagnostic")
    Experimental -> JavaBundle.message("vm.option.description.experimental")
  }

  fun unlockOption(): @NlsSafe String? = when (this) {
    Product -> null
    Diagnostic -> "-XX:+UnlockDiagnosticVMOptions"
    Experimental -> "-XX:+UnlockExperimentalVMOptions"
  }

  fun icon(): Icon = when (this) {
    Product -> AllIcons.Actions.ArrowExpand
    Diagnostic -> AllIcons.General.ShowInfos
    Experimental -> AllIcons.General.Warning
  }
}