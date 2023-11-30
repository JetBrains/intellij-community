
abstract class BoxingBoxedValue {
  abstract void method(Integer i);

  void anotherMethod() {
    Integer value = 1;

    method(Integer.valueOf(<warning descr="Boxing of already boxed 'value'">value</warning>));
  }
}