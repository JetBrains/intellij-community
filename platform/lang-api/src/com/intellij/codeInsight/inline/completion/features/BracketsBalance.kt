// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.features

import com.intellij.codeInsight.inline.completion.features.CommonBracketType.entries
import org.jetbrains.annotations.ApiStatus
import java.util.Stack


internal sealed interface TokenCategory {
  sealed interface CommonBracket : TokenCategory {
    val type: CommonBracketType
  }

  data class OpeningOrClosingBracket(override val type: CommonBracketType) : CommonBracket
  data class OpeningBracket(override val type: CommonBracketType) : CommonBracket
  data class ClosingBracket(override val type: CommonBracketType) : CommonBracket
  data object Other : TokenCategory
}

@ApiStatus.Internal
enum class CommonBracketType(val opening: Char, val closing: Char, val fusName: String) {
  BRACKET('[', ']', "bracket"),
  PARENTHESIS('(', ')', "parenthesis"),
  BRACE('{', '}', "brace"),
  ANGLE_BRACKET('<', '>', "angle_bracket"),
  DOUBLE_QUOTE('"', '"', "double_quote"),
  SINGLE_QUOTE('\'', '\'', "single_quote"),
  ;
}

internal fun String.tokenCategory(): TokenCategory {
  val openingBracketType = entries.find { it.opening.toString() == this }
  val closingBracketType = entries.find { it.closing.toString() == this }

  return when {
    openingBracketType != null && closingBracketType != null -> TokenCategory.OpeningOrClosingBracket(type = openingBracketType)
    openingBracketType != null -> TokenCategory.OpeningBracket(type = openingBracketType)
    closingBracketType != null -> TokenCategory.ClosingBracket(type = closingBracketType)
    else -> TokenCategory.Other
  }
}

@ApiStatus.Internal
data class OpeningBracketsBalance(
  val balance: Map<CommonBracketType, Int>,
  val missingOpeningBrackets: Map<CommonBracketType, Int>,
)

@ApiStatus.Internal
fun getOpenedBrackets(tokens: List<String>): OpeningBracketsBalance {
  fun <T> Stack<T>.popOrNull(): T? {
    return if (isEmpty()) null else pop()
  }

  fun <T> Stack<T>.peekOrNull(): T? {
    return if (isEmpty()) null else peek()
  }

  val opened = Stack<CommonBracketType>()
  val missingOpeningBrackets = mutableMapOf<CommonBracketType, Int>().withDefault { 0 }

  for (t in tokens) {
    when (val currentToken = t.tokenCategory()) {
      is TokenCategory.OpeningOrClosingBracket -> {
        val lastOpened = opened.peekOrNull()
        when (lastOpened) {
          currentToken.type -> opened.pop()
          else -> opened.push(currentToken.type) // Assuming it's not closing something from the previous line, which can't happen because of how getSameLineTextsOnTheLeft works
        }
      }
      is TokenCategory.OpeningBracket -> opened.push(currentToken.type)
      is TokenCategory.ClosingBracket -> when (opened.popOrNull()) {
        currentToken.type -> continue
        else -> missingOpeningBrackets.merge(currentToken.type, 1) { old, new -> old + new }
      }
      TokenCategory.Other -> continue
    }
  }

  val encounteredBracketTypes = tokens
    .map { it.tokenCategory() }
    .filterIsInstance<TokenCategory.CommonBracket>()
    .map { it.type }
    .toSet()

  val counts = opened.groupingBy { it }.eachCount()

  return OpeningBracketsBalance(
    balance = encounteredBracketTypes.associateWith {
      (counts[it] ?: 0) - missingOpeningBrackets.getValue(it)
    },
    missingOpeningBrackets = encounteredBracketTypes.associateWith {
      missingOpeningBrackets[it] ?: 0
    }
  )
}
