// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.suggested.SuggestedRefactoringState.ErrorLevel
import com.intellij.refactoring.suggested.SuggestedRefactoringState.ParameterMarker
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

/**
 * A service transforming a sequence of declaration states into [SuggestedRefactoringState].
 */
abstract class SuggestedRefactoringStateChanges(protected val refactoringSupport: SuggestedRefactoringSupport) {
  /**
   * Extracts information from declaration and stores it in an instance of [Signature] class.
   *
   * For performance reasons, don't use any resolve in this method. More accurate information about changes can be obtained later
   * with use of [SuggestedRefactoringAvailability.refineSignaturesWithResolve].
   * @param anchor declaration or call-site in its current state.
   * Only PsiElement's that are classified as anchors by [SuggestedRefactoringSupport.isAnchor] may be passed to this parameter.
   * @param prevState previous state of accumulated signature changes, or *null* if the user is just about to start editing the signature.
   * @return An instance of [Signature] class, representing the current state of the declaration,
   * or *null* if the declaration is in an incorrect state and no signature can be created.
   */
  abstract fun signature(anchor: PsiElement, prevState: SuggestedRefactoringState?): Signature?

  /**
   * Provides "marker ranges" for parameters in the declaration.
   *
   * Marker ranges are used to keep track of parameter identity when its name changes.
   * A good marker range must have high chances of staying the same while editing the signature (with help of a [RangeMarker], of course).
   * If the language has a fixed separator between parameter name and type such as ':'  - use it as a marker.
   * A whitespace between the type and the name is not so reliable because it may change its length or temporarily disappear.
   * Parameter type range is also a good marker because it's unlikely to change at the same time as the name changes.
   * @param anchor declaration or call-site in its current state.
   * Only PsiElement's that are classified as anchors by [SuggestedRefactoringSupport.isAnchor] may be passed to this parameter.
   * @return a list containing a marker range for each parameter, or *null* if no marker can be provided for this parameter
   */
  abstract fun parameterMarkerRanges(anchor: PsiElement): List<TextRange?>

  open fun createInitialState(anchor: PsiElement): SuggestedRefactoringState? {
    val signature = signature(anchor, null) ?: return null
    val signatureRange = refactoringSupport.signatureRange(anchor) ?: return null
    val psiDocumentManager = PsiDocumentManager.getInstance(anchor.project)
    val file = anchor.containingFile
    val document = psiDocumentManager.getDocument(file)!!
    require(psiDocumentManager.isCommitted(document))
    return SuggestedRefactoringState(
      anchor,
      refactoringSupport,
      errorLevel = ErrorLevel.NO_ERRORS,
      oldDeclarationText = document.getText(signatureRange),
      oldImportsText = refactoringSupport.importsRange(file)
        ?.extendWithWhitespace(document.charsSequence)
        ?.let { document.getText(it) },
      oldSignature = signature,
      newSignature = signature,
      parameterMarkers = parameterMarkers(anchor, signature)
    )
  }

  /**
   * Returns a declaration for a given anchor. Returns anchor itself if it's already a declaration,
   * or a declaration if anchor is a use-site.
   *
   * @param anchor declaration or call-site in its current state.
   * Only PsiElement's that are classified as anchors by [SuggestedRefactoringSupport.isAnchor] may be passed to this parameter.
   * @return found declaration. Could be null if anchor is a call-site that does not properly resolve.
   */
  open fun findDeclaration(anchor: PsiElement): PsiElement? = anchor

  open fun updateState(state: SuggestedRefactoringState, anchor: PsiElement): SuggestedRefactoringState {
    val newSignature = signature(anchor, state)
                       ?: return state.withErrorLevel(ErrorLevel.SYNTAX_ERROR)

    val idsPresent = newSignature.parameters.map { it.id }.toSet()
    val disappearedParameters = state.disappearedParameters.entries
      .filter { (_, id) -> id !in idsPresent }
      .associate { it.key to it.value }
      .toMutableMap()
    for ((id, name) in state.newSignature.parameters) {
      if (id !in idsPresent && state.oldSignature.parameterById(id) != null) {
        disappearedParameters[name] = id // one more parameter disappeared
      }
    }

    val parameterMarkers = parameterMarkers(anchor, newSignature).toMutableList()
    val syntaxError = refactoringSupport.hasSyntaxError(anchor)
    if (syntaxError) {
      // when there is a syntax error inside the signature, there can be parameters which are temporarily not parsed as parameters
      // we must keep their markers in order to match them later
      for (marker in state.parameterMarkers) {
        if (marker.rangeMarker.isValid && newSignature.parameterById(marker.parameterId) == null) {
          parameterMarkers += marker
        }
      }
    }

    return state
      .withAnchor(anchor)
      .withNewSignature(newSignature)
      .withErrorLevel(if (syntaxError) ErrorLevel.SYNTAX_ERROR else ErrorLevel.NO_ERRORS)
      .withParameterMarkers(parameterMarkers)
      .withDisappearedParameters(disappearedParameters)
  }

  protected fun matchParametersWithPrevState(
    signature: Signature,
    newDeclaration: PsiElement,
    prevState: SuggestedRefactoringState
  ): Signature {
    // first match all parameters by names (in prevState or in the history of changes)
    val ids = signature.parameters.map { guessParameterIdByName(it, prevState) }.toMutableList()

    // now match those that we could not match by name via marker ranges
    val markerRanges = parameterMarkerRanges(newDeclaration)
    for (index in signature.parameters.indices) {
      val markerRange = markerRanges[index]
      if (ids[index] == null && markerRange != null) {
        val id = guessParameterIdByMarkers(markerRange, prevState)
        if (id != null && id !in ids) {
          ids[index] = id
        }
      }
    }

    val newParameters = signature.parameters.zip(ids) { parameter, id ->
      parameter.copy(id = id ?: Any()/*new id*/)
    }
    return Signature.create(signature.name, signature.type, newParameters, signature.additionalData)!!
  }

  protected fun guessParameterIdByName(parameter: SuggestedRefactoringSupport.Parameter, prevState: SuggestedRefactoringState): Any? {
    prevState.newSignature.parameterByName(parameter.name)
      ?.let { return it.id }

    prevState.disappearedParameters[parameter.name]
      ?.let { return it }

    return null
  }

  protected open fun guessParameterIdByMarkers(markerRange: TextRange, prevState: SuggestedRefactoringState): Any? {
    return prevState.parameterMarkers.firstOrNull { it.rangeMarker.range == markerRange }?.parameterId
  }

  /**
   * Use this implementation of [SuggestedRefactoringStateChanges], if only Rename refactoring is supported for the language.
   */
  class RenameOnly(refactoringSupport: SuggestedRefactoringSupport) : SuggestedRefactoringStateChanges(refactoringSupport) {
    override fun signature(anchor: PsiElement, prevState: SuggestedRefactoringState?): Signature? {
      val name = (anchor as? PsiNamedElement)?.name ?: return null
      return Signature.create(name, null, emptyList(), null)!!
    }

    override fun parameterMarkerRanges(anchor: PsiElement): List<TextRange?> {
      return emptyList()
    }
  }
}

fun SuggestedRefactoringStateChanges.parameterMarkers(declaration: PsiElement, signature: Signature): List<ParameterMarker> {
  val document = PsiDocumentManager.getInstance(declaration.project).getDocument(declaration.containingFile)!!
  val markerRanges = parameterMarkerRanges(declaration)
  require(markerRanges.size == signature.parameters.size)
  return markerRanges.zip(signature.parameters)
    .mapNotNull { (range, parameter) ->
      range?.let { ParameterMarker(document.createRangeMarker(it), parameter.id) }
    }
}
