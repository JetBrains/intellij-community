// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

/**
 * A service determining available refactoring for a given [SuggestedRefactoringState].
 */
abstract class SuggestedRefactoringAvailability(protected val refactoringSupport: SuggestedRefactoringSupport) {
  /**
   * Detects if we should suppress refactoring suggestion for this declaration.
   *
   * This method is supposed to use [SuggestedRefactoringState.restoredDeclarationCopy] in order to analyze the original declaration.
   * It's allowed to use reference resolve in this method.
   * If resolve is not needed then it's recommended to override [SuggestedRefactoringStateChanges.createInitialState] and do the checks there.
   * This method should not be called if [SuggestedRefactoringState.errorLevel] == [SuggestedRefactoringState.ErrorLevel.INCONSISTENT].
   * @return true, if the refactoring suggestion should be permanently disabled for this declaration
   * and all further changes in the signature ignored.
   */
  open fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean = false

  /**
   * Refines the old and the new signatures with use of resolve.
   *
   * Resolve may be useful, for example, to filter out annotation changes that are not supposed to be copied across method hierarchy.
   * This method should be called only when [SuggestedRefactoringState.errorLevel] == [SuggestedRefactoringState.ErrorLevel.NO_ERRORS].
   */
  open fun refineSignaturesWithResolve(state: SuggestedRefactoringState): SuggestedRefactoringState = state

  /**
   * Amends state with additional information by performing potentially slow computations such as usage search.
   *
   * Normally, the state is amended by modifying [SuggestedRefactoringState.additionalData]
   * (see [SuggestedRefactoringState.withAdditionalData]). The additional data can be retrieved later and used by
   * implementations of [detectAvailableRefactoring] and [shouldSuppressRefactoringForDeclaration].
   * This method should not be called if [SuggestedRefactoringState.errorLevel] == [SuggestedRefactoringState.ErrorLevel.INCONSISTENT].
   * @return lazy iterator over sequence of amended states
   */
  open fun amendStateInBackground(state: SuggestedRefactoringState): Iterator<SuggestedRefactoringState> = iterator { }

  /**
   * Determines refactoring availability for a given state and returns instance of [SuggestedRefactoringData],
   * providing information for presentation and execution of the refactoring.
   *
   * It's supposed that this method is called only when *state.oldSignature != state.newSignature* and *state.syntaxError == false*.
   * @param state current state of accumulated changes
   * @return An instance of [SuggestedRefactoringData] with information about available refactoring,
   * or *null* if no refactoring is available.
   */
  abstract fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData?

  /**
   * Use this implementation of [SuggestedRefactoringAvailability], if only Rename refactoring is supported for the language.
   */
  open class RenameOnly(refactoringSupport: SuggestedRefactoringSupport) : SuggestedRefactoringAvailability(refactoringSupport) {
    override fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData? {
      val namedElement = state.anchor as? PsiNamedElement ?: return null
      return SuggestedRenameData(namedElement, state.oldSignature.name)
    }
  }

  protected fun hasParameterAddedRemovedOrReordered(oldSignature: Signature, newSignature: Signature): Boolean {
    if (oldSignature.parameters.size != newSignature.parameters.size) return true
    return oldSignature.parameters.zip(newSignature.parameters).any { (oldParam, newParam) ->
      oldParam.id != newParam.id
    }
  }

  protected open fun hasTypeChanges(oldSignature: Signature, newSignature: Signature): Boolean {
    require(oldSignature.parameters.size == newSignature.parameters.size)
    if (oldSignature.type != newSignature.type) return true

    return oldSignature.parameters.zip(newSignature.parameters).any { (oldParam, newParam) ->
      hasParameterTypeChanges(oldParam, newParam)
    }
  }

  protected open fun hasParameterTypeChanges(oldParam: Parameter, newParam: Parameter): Boolean {
    return oldParam.type != newParam.type
  }

  protected fun nameChanges(
    oldSignature: Signature,
    newSignature: Signature,
    declaration: PsiElement,
    parameters: List<PsiNamedElement>
  ): Pair<Int, SuggestedRenameData?> {
    require(parameters.size == newSignature.parameters.size)
    require(oldSignature.parameters.size == newSignature.parameters.size)

    var nameChanges = 0
    var renameData: SuggestedRenameData? = null

    if (declaration is PsiNamedElement && oldSignature.name != newSignature.name) {
      nameChanges++
      renameData = SuggestedRenameData(declaration, oldSignature.name)
    }

    for (i in oldSignature.parameters.indices) {
      val oldParam = oldSignature.parameters[i]
      val newParam = newSignature.parameters[i]
      if (oldParam.name != newParam.name) {
        nameChanges++
        renameData = SuggestedRenameData(parameters[i], oldParam.name)
      }
    }

    return nameChanges to renameData?.takeIf { nameChanges == 1 }
  }

  companion object {
    @Deprecated("Use RefactoringBundle.message(\"suggested.refactoring.usages\") explicitly")
    @JvmField val USAGES = RefactoringBundle.message("suggested.refactoring.usages")
    @Deprecated("Use RefactoringBundle.message(\"suggested.refactoring.overrides\") explicitly")
    @JvmField val OVERRIDES = RefactoringBundle.message("suggested.refactoring.overrides")
    @Deprecated("Use RefactoringBundle.message(\"suggested.refactoring.implementations\") explicitly")
    @JvmField val IMPLEMENTATIONS = RefactoringBundle.message("suggested.refactoring.implementations")
  }
}