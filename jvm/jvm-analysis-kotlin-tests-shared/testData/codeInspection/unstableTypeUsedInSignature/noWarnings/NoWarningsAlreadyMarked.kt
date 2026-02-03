@file:Suppress("UNUSED_PARAMETER")

package test;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
class NoWarningsAlreadyMarked {

  var field: ExperimentalClass? = null

  fun methodWithExperimentalParam(param: ExperimentalClass) {}

  fun methodWithExperimentalReturnType(): ExperimentalClass? {
    return null
  }
}