public class Client {
  void f(IfaceImpl impl) {
    impl = impl.foo();
  }
}