class JSpecifyPlainTypeVariableReturnOptionOn<T> {

  // With REPORT_UNSPECIFIED_PARAMETRIC_RETURNS ON this is reported.
  T returnsNull() {
    return <warning descr="'null' is returned from a method whose type-variable return type may be instantiated as non-null">null</warning>;
  }
}
