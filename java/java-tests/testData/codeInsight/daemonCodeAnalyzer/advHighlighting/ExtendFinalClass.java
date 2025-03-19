final class X {
  X(int i) {}
}
class A extends <error descr="Cannot inherit from final class 'X'">X</error> {
}