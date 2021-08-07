package testapi

import org.jetbrains.annotations.VisibleForTesting

object VisibleForTestingTestApi {
  var foo = 0
    @VisibleForTesting get() = field

  @VisibleForTesting
  fun bar() {
  }
}