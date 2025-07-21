// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.psi.*


public object JavaTypeHintsFactory {
  private const val startFoldingFromLevel: Int = 2

  private const val CAPTURE_OF = "capture of "
  private const val UNNAMED_MARK = "<unnamed>"
  private const val ANONYMOUS_MARK = "anonymous"


  public fun typeHint(type: PsiType, treeBuilder: PresentationTreeBuilder) {
    treeBuilder.typeHint(this.startFoldingFromLevel, type)
  }

  private fun PresentationTreeBuilder.typeHint(level: Int, type: PsiType) {
    when (type) {
      is PsiArrayType -> {
        typeHint(level + 1, type.componentType)
        text("[]")
      }
      is PsiClassType -> classTypeHint(level, type)
      is PsiCapturedWildcardType -> {
        text(CAPTURE_OF)
        typeHint(level, type.wildcard)
      }
      is PsiWildcardType -> wildcardHint(level, type)
      is PsiDisjunctionType -> {
        join(
          type.disjunctions,
          op = {
               typeHint(level, it)
          },
          separator = {
            text(" | ")
          }
        )
      }
      is PsiIntersectionType -> {
        join(
          type.conjuncts,
          op = {
            typeHint(level, it)
          },
          separator = {
            text(" & ")
          }
        )
      }
      else -> {
        text(type.presentableText)
      }
    }
  }

  private fun PresentationTreeBuilder.wildcardHint(level: Int, type: PsiWildcardType) {
    when {
      type.isExtends -> {
        text("extends ")
        typeHint(level, type.extendsBound)
      }
      type.isSuper -> {
        text("super ")
        typeHint(level, type.superBound)
      }
      else -> {
        text("?")
      }
    }
  }

  private fun PresentationTreeBuilder.classTypeHint(level: Int, classType: PsiClassType) {
    val aClass = classType.resolve()

    val className = classType.className ?: ANONYMOUS_MARK // TODO here it may be not exactly true, the class might be unresolved
    text(className, aClass?.qualifiedName?.let { InlayActionData(StringInlayActionPayload(it), JavaFqnDeclarativeInlayActionHandler.HANDLER_NAME) })
    if (classType.parameterCount == 0) return
    collapsibleList(expandedState = {
      toggleButton {
        text("<")
      }
      join(
        classType.parameters,
        op = {
          typeHint(level + 1, it)
        },
        separator = {
          text(", ")
        }
      )
      toggleButton {
        text(">")
      }
    }, collapsedState = {
      toggleButton {
        text("<...>")
      }
    })
  }

  private fun <T> PresentationTreeBuilder.join(elements: Array<T>,
                                               op: PresentationTreeBuilder.(T) -> Unit,
                                               separator: PresentationTreeBuilder.() -> Unit) {
    var isFirst = true
    for (element in elements) {
      if (isFirst) {
        isFirst = false
      }
      else {
        separator()
      }
      op(this, element)
    }
  }

  private fun <T> PresentationTreeBuilder.join(elements: List<T>,
                                               op: PresentationTreeBuilder.(T) -> Unit,
                                               separator: PresentationTreeBuilder.() -> Unit) {
    var isFirst = true
    for (element in elements) {
      if (isFirst) {
        isFirst = false
      }
      else {
        separator()
      }
      op(this, element)
    }
  }
}