package com.intellij.ide.actions.searcheverywhere

class PsiItemWithSimilarity<T : Any>(val value: T, var similarityScore: Double? = null) : MergeableElement {
  val isPureSemantic: Boolean = similarityScore != null

  override fun mergeWith(other: MergeableElement?): MergeableElement {
    if (other is PsiItemWithSimilarity<*> && other.shouldBeMergedIntoAnother()) {
      similarityScore = other.similarityScore
    }
    return this
  }

  override fun shouldBeMergedIntoAnother(): Boolean {
    return similarityScore != null
  }
}