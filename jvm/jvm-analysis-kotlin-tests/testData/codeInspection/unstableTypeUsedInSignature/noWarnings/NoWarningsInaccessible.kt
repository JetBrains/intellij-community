@file:Suppress("UNUSED_PARAMETER")

package test;

import org.jetbrains.annotations.ApiStatus;

class NoWarningsInaccessible {

  private val privateField: ExperimentalClass? = null

  private fun privateMethodWithParam(param: ExperimentalClass) {}

  private fun privateMethodWithReturnType(): ExperimentalClass? = null
}

private class PrivateKotlinClass {
}