package test;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
class NoWarningsAlreadyMarked {

  public ExperimentalClass field;

  public void methodWithExperimentalParam(ExperimentalClass param) {
  }

  public ExperimentalClass methodWithExperimentalReturnType() {
    return null;
  }
}