@file:Suppress("UNUSED_PARAMETER")

package test

import experimentalPackage.ClassInExperimentalPackage

class UnstableTypeUsedInSignature {

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

  var <warning descr="Field must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its type references unstable type 'experimentalPackage.ClassInExperimentalPackage'">fieldWithTypeFromExperimentalPackage</warning>: ClassInExperimentalPackage? = null

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'experimentalPackage.ClassInExperimentalPackage'">methodWithParamTypeFromExperimentalPackage</warning>(param: ClassInExperimentalPackage) {}

  fun <warning descr="Method must be marked with '@org.jetbrains.annotations.ApiStatus.Experimental' annotation because its signature references unstable type 'experimentalPackage.ClassInExperimentalPackage'">methodWithReturnTypeFromExperimentalPackage</warning>(): ClassInExperimentalPackage? = null
}