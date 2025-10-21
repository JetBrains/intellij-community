package com.intellij.jvm.analysis.testFramework

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

abstract class JvmReferenceContributorTestBase : LightJvmCodeInsightFixtureTestCase() {
  protected fun JavaCodeInsightTestFixture.assertResolvableReference(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (PsiReference, PsiElement) -> Unit = { _, _, -> }
  ) {
    configureByText("$fileName${lang.ext}", before)
    val offset = getCaretOffset()
    val reference = myFixture.file.findReferenceAt(offset) ?: error("Could not find reference at caret offset")
    val resolved = reference.resolve()
    assertNotNull(resolved)
    assertion(reference, resolved!!)
  }

  protected fun JavaCodeInsightTestFixture.assertMultiresolveReference(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (PsiReference, Array<ResolveResult>) -> Unit = { _, _ -> }
  ) {
    configureByText("$fileName${lang.ext}", before)
    val offset = getCaretOffset()
    val reference = myFixture.file.findReferenceAt(offset) ?: error("Could not find reference at caret offset")
    assertTrue(reference is PsiPolyVariantReference)
    if (reference !is PsiPolyVariantReference) return

    assertion(reference, reference.multiResolve(false))
  }

  protected fun JavaCodeInsightTestFixture.assertUnResolvableReference(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (PsiReference) -> Unit = {_ -> }
  ) {
    configureByText("$fileName${lang.ext}", before)
    val offset = getCaretOffset()
    val reference = myFixture.file.findReferenceAt(offset)  ?: error("Could not find reference at caret offset")
    val resolved = reference.resolve()
    assertNull(resolved)
    assertion(reference)
  }

  protected fun PsiReference.lookupStringVariants() = variants.mapNotNull { if (it is LookupElement) it.lookupString else null }
}