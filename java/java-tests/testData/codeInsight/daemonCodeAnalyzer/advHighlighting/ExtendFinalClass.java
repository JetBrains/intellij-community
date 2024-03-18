final class X {
  X(int i) {}
}
class A extends <error descr="Cannot inherit from final 'X'">X</error> {
}