@file:[
Suppress("UNUSED_PARAMETER")
org.jetbrains.annotations.ApiStatus.Experimental
]

// No warnings because the file itself is marked experimental.

package noWarnings

import test.ExperimentalClass;

fun topLevelMethodReturnType(): ExperimentalClass? = null
fun topLevelParameterType(param: ExperimentalClass) {}
val topLevelProperty: ExperimentalClass? = null

class NoWarnings<T : ExperimentalClass> {
  fun methodReturnType(): ExperimentalClass? = null
  fun parameterType(param: ExperimentalClass) {}
  val property: ExperimentalClass? = null
}