import org.jetbrains.annotations.NotNull;

class Test {
  Object o;

  void field() {
    o.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>();
  }

  void parameter(Object o) {
    o.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>();
  }

  void callUnknownMethod() {
    unknownObject().<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>();
  }

  void callNotNullMethod() {
    knownObject().hashCode();
  }

  native Object unknownObject();

  @NotNull
  native Object knownObject();
}

enum DemoEnum {
  TEST, DEMO, DEMO_1;

  public static DemoEnum testMethod(String value){
    for (DemoEnum demoEnum : DemoEnum.values()) {
    }
    return null;
  }
}