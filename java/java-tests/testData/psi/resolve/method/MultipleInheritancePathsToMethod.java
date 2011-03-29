interface N1 {
  String getName();
}
interface N2 {
  String getName();
}
interface NN extends N1 {}

interface N3 {
    String getName();
}
interface VeryNamed extends NN, N2, N3, N1 {}

class MyClass {

  void foo(VeryNamed f) {
    f.getNa<ref>me();
  }

}
