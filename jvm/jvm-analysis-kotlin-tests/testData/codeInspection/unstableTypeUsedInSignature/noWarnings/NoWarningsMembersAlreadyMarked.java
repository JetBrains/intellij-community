package test;

import org.jetbrains.annotations.ApiStatus;

public class NoWarningsMembersAlreadyMarked {

  @ApiStatus.Experimental
  public ExperimentalClass field;

  @ApiStatus.Experimental
  public void methodWithExperimentalParam(ExperimentalClass param) {
  }

  @ApiStatus.Experimental
  public ExperimentalClass methodWithExperimentalReturnType() {
    return null;
  }
}