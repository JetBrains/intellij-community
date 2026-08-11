class JSpecifyPlainTypeVariableReturnOptionOff<T> {
  T returnsNull() {
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }
}
