package test;

import pkg.AnnotatedClass;

@SuppressWarnings({"SameParameterValue", "unused", "UnusedReturnValue", "ResultOfMethodCallIgnored", "MethodMayBeStatic", "ObjectToString"})
class NoWarningsMembersOfTheSameFile {

  private <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> field;

  private static <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> staticField;

  private <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> returnType() {
    return null;
  }

  private void paramType(<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> param) {
  }

  private static <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> staticReturnType() {
    return null;
  }

  private static void staticParamType(<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> a) {
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