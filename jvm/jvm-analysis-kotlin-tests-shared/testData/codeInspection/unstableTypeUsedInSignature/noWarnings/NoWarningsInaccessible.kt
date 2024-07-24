@file:Suppress("UNUSED_PARAMETER")

package test;

class NoWarningsInaccessible {

  private val privateField: ExperimentalClass? = null

  private fun privateMethodWithParam(param: ExperimentalClass) {}

  private fun privateMethodWithReturnType(): ExperimentalClass? = null

  fun anonymousClassNoWarnings() {
    object {
      var anonymousField: ExperimentalClass? = null

      fun anonymousMethodWithParamType(param: ExperimentalClass) {
      }

      fun anonymousMethodWithReturnType(): ExperimentalClass? = null
    }
  }
}

private class PrivateKotlinClass<T : ExperimentalClass> {
}