public sealed class B permits A1, A2, A3 {
  void foo(A3 param) {}
}