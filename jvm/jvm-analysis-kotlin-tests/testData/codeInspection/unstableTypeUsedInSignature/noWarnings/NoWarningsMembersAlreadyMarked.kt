package test;

import org.jetbrains.annotations.ApiStatus;

class NoWarningsMembersAlreadyMarked {

  @ApiStatus.Experimental
  var field: ExperimentalClass? = null;

  @ApiStatus.Experimental
  fun methodWithExperimentalParam(param: ExperimentalClass) {
  }

  @ApiStatus.Experimental
  fun methodWithExperimentalReturnType(): ExperimentalClass? = null
}