import org.jetbrains.annotations.NotNull;

class Test {
  Object o;

  void field() {
    <warning descr="Method invocation 'o.hashCode()' may produce 'java.lang.NullPointerException'">o.hashCode()</warning>;
  }

  void parameter(Object o) {
    <warning descr="Method invocation 'o.hashCode()' may produce 'java.lang.NullPointerException'">o.hashCode()</warning>;
  }

  void callUnknownMethod() {
    <warning descr="Method invocation 'unknownObject().hashCode()' may produce 'java.lang.NullPointerException'">unknownObject().hashCode()</warning>;
  }

  void callNotNullMethod() {
    knownObject().hashCode();
  }

  native Object unknownObject();

  @NotNull
  native Object knownObject();
}