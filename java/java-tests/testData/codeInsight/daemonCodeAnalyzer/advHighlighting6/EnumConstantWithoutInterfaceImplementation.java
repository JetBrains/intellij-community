
interface A {
  void m();
}

enum E implements A {
  <error descr="Class 'Anonymous class derived from E' must implement abstract method 'm()' in 'A'">F</error>() {};
}