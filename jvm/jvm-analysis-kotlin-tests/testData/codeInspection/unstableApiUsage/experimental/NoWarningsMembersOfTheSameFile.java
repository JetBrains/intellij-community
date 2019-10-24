package test;

import pkg.AnnotatedClass;

@SuppressWarnings({"SameParameterValue", "unused", "UnusedReturnValue", "ResultOfMethodCallIgnored", "MethodMayBeStatic", "ObjectToString"})
class NoWarningsMembersOfTheSameFile {

  private <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> field;

  private static <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> staticField;

  private <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> returnType() {
    return null;
  }

  private void paramType(<warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> param) {
  }

  private static <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> staticReturnType() {
    return null;
  }

  private static void staticParamType(<warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> a) {
  }

  void testNoWarningsProducedForMembersOfTheSameClass() {
    field.toString();
    staticField.toString();
    returnType();
    paramType(null);
    staticReturnType();
    staticParamType(null);
  }

  private class InnerClass {
    void testNoWarningsProducedForMembersEnclosingClass() {
      field.toString();
      staticField.toString();
      returnType();
      paramType(null);
      staticReturnType();
      staticParamType(null);
    }
  }
}