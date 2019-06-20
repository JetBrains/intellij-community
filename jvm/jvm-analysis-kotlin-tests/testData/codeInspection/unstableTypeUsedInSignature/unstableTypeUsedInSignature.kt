@file:Suppress("UNUSED_PARAMETER")

package test

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class ExperimentalClass

class Warnings {

  var <warning descr="Field must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its type references unstable type 'test.ExperimentalClass'">field</warning>: ExperimentalClass? = null

  var <warning descr="Field must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its type references unstable type 'test.ExperimentalClass'">arrayField</warning>: Array<ExperimentalClass>? = null

  var <warning descr="Field must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its type references unstable type 'test.ExperimentalClass'">listField</warning>: List<ExperimentalClass>? = null

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalParam</warning>(param: ExperimentalClass) {}

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalParamArray</warning>(param: Array<ExperimentalClass>) {}

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalParamArray</warning>(param: List<ExperimentalClass>) {}

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalReturnType</warning>(): ExperimentalClass? {
    return null
  }

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalReturnTypeArray</warning>(): Array<ExperimentalClass>? {
    return null
  }

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithExperimentalReturnTypeList</warning>(): List<ExperimentalClass>? {
    return null
  }

  fun <T : ExperimentalClass> <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithUnstableTypeParameter</warning>(): T? {
    return null
  }

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithUnstableTypeParameterExtendsWildcard</warning>(list: List<ExperimentalClass>) {}

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'test.ExperimentalClass'">methodWithUnstableTypeParameterSuperWildcard</warning>(list: List<ExperimentalClass>) {}
}

class <warning descr="Class must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its declaration references unstable type 'test.ExperimentalClass'">WarningTypeParameter</warning><T : ExperimentalClass>

// No warnings should be produced because the declaring class is experimental itself.

@ApiStatus.Experimental
class NoWarningsClassLevel {

  var field: ExperimentalClass? = null

  fun methodWithExperimentalParam(param: ExperimentalClass) {}

  fun methodWithExperimentalReturnType(): ExperimentalClass? {
    return null
  }
}

// No warnings should be produced because methods and fields are already marked with @ApiStatus.Experimental annotation.

class NoWarnings {

  @ApiStatus.Experimental
  var field: ExperimentalClass? = null

  @ApiStatus.Experimental
  fun methodWithExperimentalParam(param: ExperimentalClass) {
  }

  @ApiStatus.Experimental
  fun methodWithExperimentalReturnType(): ExperimentalClass? {
    return null
  }
}