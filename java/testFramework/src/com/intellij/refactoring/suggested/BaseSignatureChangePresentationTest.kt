// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.refactoring.suggested.SignatureChangePresentationModel.Effect
import com.intellij.refactoring.suggested.SignatureChangePresentationModel.TextFragment
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import junit.framework.TestCase
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions

abstract class BaseSignatureChangePresentationTest : TestCase() {
  protected abstract val refactoringSupport: SuggestedRefactoringSupport

  protected fun doTest(
    oldSignature: Signature,
    newSignature: Signature,
    expectedDump: String
  ) {
    refactoringSupport.checkSignatureChangePresentation(oldSignature, newSignature, expectedDump)
  }
}

/**
 * Builds the presentation of the change from [oldSignature] to [newSignature] and compares its [dump] with [expectedDump].
 * It is `doTest` of [BaseSignatureChangePresentationTest] for a JUnit 6 test.
 */
@TestOnly
fun SuggestedRefactoringSupport.checkSignatureChangePresentation(
  oldSignature: Signature,
  newSignature: Signature,
  expectedDump: String,
) {
  val model = ui.buildSignatureChangePresentation(oldSignature, newSignature)
  Assertions.assertEquals(expectedDump, model.dump().trim())
}

fun SignatureChangePresentationModel.dump(): String {
  return buildString {
    append("Old:\n")
    append(oldSignature, ident = "  ")
    append("New:\n")
    append(newSignature, ident = "  ")
  }
}

private fun StringBuilder.append(fragments: List<TextFragment>, ident: String) {
  for (fragment in fragments) {
    append(ident)

    val properties = mutableListOf<String>()

    if (fragment.connectionId != null) {
      properties.add("id = ${fragment.connectionId}")
    }

    when (fragment.effect) {
      Effect.None -> {}
      Effect.Added -> properties.add("added")
      Effect.Removed -> properties.add("removed")
      Effect.Modified -> properties.add("modified")
      Effect.Moved -> properties.add("moved")
    }

    when (fragment) {
      is TextFragment.Leaf -> append("\'${fragment.text}\'")
      is TextFragment.Group -> append("Group")
      is TextFragment.LineBreak -> append("LineBreak(\'${fragment.spaceInHorizontalMode}\', ${fragment.indentAfter})")
    }

    if (properties.isNotEmpty()) {
      append(" (")
      properties.joinTo(this, separator = ", ")
      append(")")
    }

    if (fragment is TextFragment.Group) {
      append(":")
    }

    append("\n")

    if (fragment is TextFragment.Group) {
      append(fragment.children, "$ident  ")
    }
  }
}
