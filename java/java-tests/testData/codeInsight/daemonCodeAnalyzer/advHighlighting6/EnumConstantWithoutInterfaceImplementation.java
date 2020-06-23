
interface A {
  void m();
}

enum E implements A {
  <error descr="Enum constant 'F' must implement abstract method 'm()' in 'A'">F</error>() {};
}