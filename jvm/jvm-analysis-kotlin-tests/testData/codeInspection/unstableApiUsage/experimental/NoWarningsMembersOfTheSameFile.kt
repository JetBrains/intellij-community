@file:Suppress("UNUSED_PARAMETER")

package test;

import pkg.AnnotatedClass;

class NoWarningsMembersOfTheSameFile {

  companion object {

    private var staticField: <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>? = null;

    private fun staticReturnType(): <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>? {
      return null;
    }

    private fun staticParamType(param: <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>?) {
    }
  }

  private var field: <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>? = null;

  private fun returnType(): <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>? {
    return null;
  }

  private fun paramType(param: <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>?) {
  }

  fun testNoWarningsProducedForMembersOfTheSameClass() {
    field?.toString();
    staticField?.toString();
    returnType();
    paramType(null);
    staticReturnType();
    staticParamType(null)
  }

  private inner class InnerClass {
    fun testNoWarningsProducedForMembersEnclosingClass() {
      field.toString();
      staticField.toString();
      returnType();
      paramType(null);
      staticReturnType();
      staticParamType(null)
    }
  }
}