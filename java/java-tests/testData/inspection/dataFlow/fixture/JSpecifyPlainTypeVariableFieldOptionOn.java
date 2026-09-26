class JSpecifyPlainTypeVariableFieldOptionOn<T> {

  // With REPORT_UNSPECIFIED_PARAMETRIC_NULLNESS ON this is reported.
  T field = <warning descr="'null' is assigned to a variable whose type-variable type may be instantiated as non-null">null</warning>;
}
