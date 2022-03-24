// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.hasErrorElementInRange
import com.intellij.psi.util.parents

/**
 * Language extension to implement to support suggested Rename and/or Change Signature refactorings.
 */
interface SuggestedRefactoringSupport {
  companion object : LanguageExtension<SuggestedRefactoringSupport>("com.intellij.suggestedRefactoringSupport") {
    @Suppress("RedundantOverride")
    override fun forLanguage(l: Language): SuggestedRefactoringSupport? {
      return super.forLanguage(l)
    }
  }

  /**
   * Returns true, if given PsiElement is a declaration which may be subject of suggested refactoring.
   *
   * Note, that if Change Signature is supported then individual parameters must not be considered as declarations
   * for they are part of a bigger declaration.
   */
  @Deprecated(message = "Use isAnchor instead", replaceWith = ReplaceWith("isAnchor(psiElement)"))
  @JvmDefault
  fun isDeclaration(psiElement: PsiElement): Boolean = throw NotImplementedError("Will be removed")

  /**
   * Returns true, if given PsiElement is an anchor element where suggested refactoring can start.
   *
   * It could be either a declaration or a call-site if suggested refactoring from the call-site is supported.
   *
   * Note, that if Change Signature is supported then individual parameters must not be considered as anchors
   * for they are part of a bigger anchor (method or function).
   */
  @Suppress("DEPRECATION")
  @JvmDefault
  fun isAnchor(psiElement: PsiElement): Boolean = isDeclaration(psiElement)

  /**
   * Returns "signature range" for a given anchor.
   *
   * Signature range is a range that contains all properties taken into account by Change Signature refactoring.
   * If only Rename refactoring is supported for the given declaration, the name identifier range must be returned.
   *
   * Only PsiElement's that are classified as anchor by [isAnchor] method must be passed to this method.
   * @return signature range for the anchor, or *null* if anchor is considered unsuitable for refactoring
   * (usually when it has syntax error).
   */
  fun signatureRange(anchor: PsiElement): TextRange?

  /**
   * Returns range in the given file taken by imports (if supported by the language).
   *
   * If no imports exist yet in the given file, it must return an empty range within whitespace where the imports are to be inserted.
   * @return range taken by imports, or *null* if imports are not supported by the language.
   */
  fun importsRange(psiFile: PsiFile): TextRange?

  /**
   * Returns name range for a given anchor.
   *
   * Only PsiElement's that are classified as anchors by [isAnchor] method must be passed to this method.
   * @return name range for the anchor, or *null* if anchor does not have a name.
   */
  fun nameRange(anchor: PsiElement): TextRange?

  /**
   * Returns true if there's a syntax error within given anchor that prevents
   * suggested refactoring from successful completion
   */
  @JvmDefault
  fun hasSyntaxError(anchor: PsiElement): Boolean {
    val signatureRange = signatureRange(anchor) ?: return true
    return anchor.containingFile.hasErrorElementInRange(signatureRange)
  }

  /**
   * Determines if the character can start an identifier in the language.
   *
   * This method is used for suppression of refactoring for a declaration which has been just typed by the user.
   */
  fun isIdentifierStart(c: Char): Boolean

  /**
   * Determines if an identifier can contain the character.
   *
   * This method is used for suppression of refactoring for a declaration which has been just typed by the user.
   */
  fun isIdentifierPart(c: Char): Boolean

  /**
   * A service transforming a sequence of declaration states into [SuggestedRefactoringState].
   *
   * Use [SuggestedRefactoringStateChanges.RenameOnly] if only Rename refactoring is supported.
   */
  val stateChanges: SuggestedRefactoringStateChanges

  /**
   * A service determining available refactoring for a given [SuggestedRefactoringState].
   *
   * Use [SuggestedRefactoringAvailability.RenameOnly] if only Rename refactoring is supported.
   */
  val availability: SuggestedRefactoringAvailability

  /**
   * A service providing information required for building user-interface of Change Signature refactoring.
   *
   * Use [SuggestedRefactoringUI.RenameOnly] if only Rename refactoring is supported.
   */
  val ui: SuggestedRefactoringUI

  /**
   * A service performing actual changes in the code when user executes suggested refactoring.
   *
   * Use [SuggestedRefactoringExecution.RenameOnly] if only Rename refactoring is supported.
   */
  val execution: SuggestedRefactoringExecution

  /**
   * A class with data representing declaration signature.
   *
   * This data is used mainly for signature change presentation and for detection of refactoring availability.
   * No PSI-related objects must be referenced by instances of this class and all PSI-related information
   * that is required to perform the refactoring must be extracted from the declaration itself prior to perform the refactoring.
   */
  class Signature private constructor(
    val name: String,
    val type: String?,
    val parameters: List<Parameter>,
    val additionalData: SignatureAdditionalData?,
    private val nameToParameter: Map<String, Parameter>
  ) {
    private val idToParameter: Map<Any, Parameter> = mutableMapOf<Any, Parameter>().apply {
      for (parameter in parameters) {
        val prev = this.put(parameter.id, parameter)
        require(prev == null) { "Duplicate parameter id: ${parameter.id}" }
      }
    }

    private val parameterToIndex = parameters.withIndex().associate { (index, parameter) -> parameter to index }

    fun parameterById(id: Any): Parameter? = idToParameter[id]

    fun parameterByName(name: String): Parameter? = nameToParameter[name]

    fun parameterIndex(parameter: Parameter): Int = parameterToIndex[parameter]!!

    override fun equals(other: Any?): Boolean {
      return other is Signature &&
             name == other.name &&
             type == other.type &&
             additionalData == other.additionalData &&
             parameters == other.parameters
    }

    override fun hashCode(): Int {
      return 0
    }

    companion object {
      /**
       * Factory method, used to create instances of [Signature] class.
       *
       * @return create instance of [Signature], or *null* if it cannot be created due to duplicated parameter names
       */
      @JvmStatic
      fun create(
        name: String,
        type: String?,
        parameters: List<Parameter>,
        additionalData: SignatureAdditionalData?
      ): Signature? {
        val nameToParameter = mutableMapOf<String, Parameter>()
        for (parameter in parameters) {
          val key = parameter.name
          if (nameToParameter.containsKey(key)) return null
          nameToParameter[key] = parameter
        }
        return Signature(name, type, parameters, additionalData, nameToParameter)
      }
    }
  }

  /**
   * A class representing a parameter in [Signature].
   *
   * Parameters with the same [id] represent the same parameter in the old and new signatures.
   * All parameters in the same [Signature] must have unique [id].
   */
  data class Parameter(
    val id: Any,
    @NlsSafe val name: String,
    val type: String,
    val additionalData: ParameterAdditionalData? = null
  )

  /**
   * Language-specific information to be stored in [Signature].
   *
   * Don't put any PSI-related objects here.
   */
  interface SignatureAdditionalData {
    override fun equals(other: Any?): Boolean
  }

  /**
   * Language-specific information to be stored in [Parameter].
   *
   * Don't put any PSI-related objects here.
   */
  interface ParameterAdditionalData {
    override fun equals(other: Any?): Boolean
  }
}

fun SuggestedRefactoringSupport.anchorByOffset(psiFile: PsiFile, offset: Int): PsiElement? {
  return psiFile.findElementAt(offset)
    ?.parents(true)
    ?.firstOrNull { isAnchor(it) }
}
